package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.model.FeedImport
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.repository.AdministratorRepository
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.websocket.ProgressTrackingService
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class FeedImportService(
    @Qualifier("feedManagementFeedRepository")
    private val feedRepository: FeedRepository,
    private val feedImportRepository: FeedImportRepository,
    private val administratorRepository: AdministratorRepository,
    private val progressTrackingService: ProgressTrackingService,
    private val jobLauncher: JobLauncher,
    @Qualifier("feedImportJob")
    private val feedImportJob: Job,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(FeedImportService::class.java)

    @Transactional
    fun startImport(
        feedOnestopId: String,
        administratorUsername: String?,
        triggerType: ImportTriggerType,
        force: Boolean
    ): FeedImport {
        val feed = feedRepository.findById(feedOnestopId)
            .orElseThrow { IllegalArgumentException("Feed not found: $feedOnestopId") }

        val administrator = administratorUsername?.let { adminUsername ->
            administratorRepository.findByUsername(adminUsername).orElse(null)
        }

        val now = clock.instant()
        val feedImport = feedImportRepository.save(
            FeedImport().apply {
                this.feed = feed
                this.administrator = administrator
                this.triggerType = triggerType
                this.status = ImportStatus.RUNNING
                this.startedAt = now
                this.versionSha1 = if (force) feed.currentVersionSha1 else null
            }
        )

        progressTrackingService.updateProgress(
            importId = feedImport.requireIdAsString(),
            feedOnestopId = feed.feedOnestopId,
            progressPercentage = 0,
            currentStep = "Starting import",
            currentStepNumber = 0,
            totalSteps = 8,
            startedAt = now
        )

        launchImportJob(feedImport.requireId(), feed.feedOnestopId)

        return feedImport
    }

    @Transactional
    fun cancelImport(importId: UUID) {
        val feedImport = feedImportRepository.findById(importId)
            .orElseThrow { IllegalArgumentException("Import not found: $importId") }

        feedImport.status = ImportStatus.CANCELLED
        feedImport.completedAt = clock.instant()
        feedImportRepository.save(feedImport)
        progressTrackingService.markFailed(importId.toString(), "Import cancelled by user")
    }

    private fun launchImportJob(importId: UUID, feedOnestopId: String) {
        val params = JobParametersBuilder()
            .addString("feedOnestopId", feedOnestopId)
            .addString("importId", importId.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        runCatching {
            jobLauncher.run(feedImportJob, params)
        }.onFailure { throwable ->
            logger.error("Failed to launch feed import job for {}", feedOnestopId, throwable)
            failImport(importId, throwable.message ?: "Failed to start import job")
        }
    }

    @Transactional
    fun completeImport(importId: UUID, versionSha1: String?) {
        val feedImport = feedImportRepository.findById(importId)
            .orElseThrow { IllegalArgumentException("Import not found: $importId") }

        val now = clock.instant()
        feedImport.status = ImportStatus.COMPLETED
        feedImport.completedAt = now
        feedImport.versionSha1 = versionSha1 ?: feedImport.versionSha1
        feedImportRepository.save(feedImport)

        feedImport.feed?.let { feed ->
            feed.status = FeedStatus.ACTIVE
            feed.lastUpdatedAt = now
            feed.currentVersionSha1 = feedImport.versionSha1
            feedRepository.save(feed)
        }

        progressTrackingService.markCompleted(importId.toString())
    }

    @Transactional
    fun failImport(importId: UUID, message: String) {
        val feedImport = feedImportRepository.findById(importId)
            .orElseThrow { IllegalArgumentException("Import not found: $importId") }

        feedImport.status = ImportStatus.FAILED
        feedImport.completedAt = clock.instant()
        feedImport.errorMessage = message
        feedImportRepository.save(feedImport)

        progressTrackingService.markFailed(importId.toString(), message)
    }

    private fun FeedImport.requireId(): UUID =
        id ?: throw IllegalStateException("Import must have an identifier before use")

    private fun FeedImport.requireIdAsString(): String = requireId().toString()
}

package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.gtfs.ParsedGtfsData
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.repository.AdministratorRepository
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.websocket.ProgressTrackingService
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock

class FeedImportStarted(val importId: ImportId, val feedId: FeedId)

class FeedImportJobFailed(val importId: ImportId, val message: String)

@Service
class FeedImportService(
    @Qualifier("feedManagementFeedRepository")
    private val feedRepository: FeedRepository,
    private val feedImportRepository: FeedImportRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val jobLauncher: JobLauncher,
    @Qualifier("feedImportJob")
    private val feedImportJob: Job,
    @Qualifier("taskExecutor")
    private val importLaunchExecutor: TaskExecutor,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(FeedImportService::class.java)

    fun startImport(
        feedId: FeedId,
        triggerType: ImportTriggerType
    ): FeedImport {
        val feed = feedRepository.findByFeedOnestopId(feedId.value)
            .orElseThrow { IllegalArgumentException("Feed not found: $feedId") }

        val now = clock.instant()
        val feedImport = feedImportRepository.save(
            FeedImport().apply {
                this.feedId = feed.feedId
                this.administrator = null
                this.triggerType = triggerType
                this.status = ImportStatus.RUNNING
                this.startedAt = now
                this.versionSha1 = null
            }
        )

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        launchImportJob(feedImport.requireId(), FeedId(feed.feedId))
                    }
                }
            )
        } else {
            launchImportJob(feedImport.requireId(), FeedId(feed.feedId))
        }

        return feedImport
    }

    @Transactional
    fun cancelImport(importId: ImportId) {
        val feedImport = feedImportRepository.findByImportId(importId)
            .orElseThrow { IllegalArgumentException("Import not found: $importId") }

        feedImport.status = ImportStatus.CANCELLED
        feedImport.completedAt = clock.instant()
        feedImportRepository.save(feedImport)
    }

    private fun launchImportJob(importId: ImportId, feedId: FeedId) {
        val params = JobParametersBuilder()
            .addString("feedOnestopId", feedId.value)
            .addString("importId", importId.value.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        importLaunchExecutor.execute {
            runCatching {
                jobLauncher.run(feedImportJob, params)
                eventPublisher.publishEvent(FeedImportStarted(importId, feedId))
            }.onFailure { throwable ->
                logger.error("Failed to launch feed import job for {}", feedId, throwable)
                failImport(importId, throwable.message ?: "Failed to start import job")
            }
        }
    }

    @Transactional
    fun completeImport(importId: ImportId, parsedData: ParsedGtfsData) {
        val feedImport = feedImportRepository.findByImportId(importId)
            .orElseThrow { IllegalArgumentException("Import not found: $importId") }

        val now = clock.instant()
        feedImport.status = ImportStatus.COMPLETED
        feedImport.completedAt = now
        // TODO: Generate version SHA1 from parsed data or feed archive
        feedImportRepository.save(feedImport)

        if (feedImport.feedId.isNotBlank()) {
            feedRepository.findByFeedOnestopId(feedImport.feedId)
                .ifPresent { feed ->
                    feed.status = FeedStatus.ACTIVE
                    feed.lastUpdatedAt = now
                    // TODO: Set currentVersionSha1 from parsed data
                    feedRepository.save(feed)
                }
        }
    }

    @Transactional
    fun failImport(importId: ImportId, message: String) {
        val feedImport = feedImportRepository.findByImportId(importId)
            .orElseThrow { IllegalArgumentException("Import not found: $importId") }

        feedImport.status = ImportStatus.FAILED
        feedImport.completedAt = clock.instant()
        feedImport.errorMessage = message
        feedImportRepository.save(feedImport)
        eventPublisher.publishEvent(FeedImportJobFailed(importId, message))
    }

    private fun FeedImport.requireId(): ImportId = id

    private fun FeedImport.requireIdAsString(): String = requireId().value.toString()
}

package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class FeedImportService(
  @Qualifier("feedManagementFeedRepository") private val feedRepository: FeedRepository,
  private val feedImportRepository: FeedImportRepository,
  private val jobLauncher: JobLauncher,
  private val jobOperator: JobOperator,
  @Qualifier("feedImportJob") private val feedImportJob: Job,
  @Qualifier("taskExecutor") private val importLaunchExecutor: TaskExecutor,
  private val clock: Clock = Clock.systemUTC(),
) {
  private val logger = LoggerFactory.getLogger(FeedImportService::class.java)

  @Transactional
  fun import(feedId: FeedId, triggerType: ImportTriggerType): FeedImport {
    val activeImport =
      feedImportRepository
        .findAllByFeedIdAndStatusInOrderByStartedAtDesc(
          feedId.value,
          listOf(ImportStatus.PENDING, ImportStatus.RUNNING),
          PageRequest.of(0, 1),
        )
        .content
        .firstOrNull()
    if (activeImport != null) {
      // Check if import is stale (running for more than 1 hour)
      val now = clock.instant()
      val importAge = java.time.Duration.between(activeImport.startedAt, now)
      if (importAge.toHours() >= 1) {
        logger.warn(
          "Found stale import {} for feed {} (running for {} hours), marking as failed",
          activeImport.id,
          feedId,
          importAge.toHours(),
        )
        activeImport.status = ImportStatus.FAILED
        activeImport.completedAt = now
        activeImport.errorMessage = "Import timed out - stuck in running state for > 1 hour"
        feedImportRepository.save(activeImport)
        // Continue to create new import below
      } else {
        logger.info(
          "Import already running for feed {}, returning existing import {}",
          feedId,
          activeImport.id,
        )
        return activeImport
      }
    }

    val feed =
      feedRepository.findByFeedOnestopId(feedId.value).orElseThrow {
        IllegalArgumentException("Feed not found: $feedId")
      }

    val now = clock.instant()
    val feedImport =
      try {
        feedImportRepository.save(
          FeedImport().apply {
            this.feedId = feed.feedId
            this.administrator = null
            this.triggerType = triggerType
            this.status = ImportStatus.RUNNING
            this.startedAt = now
            this.versionSha1 = null
          }
        )
      } catch (e: org.springframework.dao.DataIntegrityViolationException) {
        // Database constraint prevented duplicate - fetch and return the existing import
        logger.info(
          "Import already started for feed {} (caught by database constraint), fetching existing import",
          feedId,
        )
        feedImportRepository
          .findAllByFeedIdAndStatusInOrderByStartedAtDesc(
            feedId.value,
            listOf(ImportStatus.PENDING, ImportStatus.RUNNING),
            PageRequest.of(0, 1),
          )
          .content
          .firstOrNull()
          ?: throw IllegalStateException("Failed to create or find active import for feed $feedId")
      }

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
        object : TransactionSynchronization {
          override fun afterCommit() {
            launchImportJob(feedImport.id, FeedId(feed.feedId))
          }
        }
      )
    } else {
      launchImportJob(feedImport.id, FeedId(feed.feedId))
    }

    return feedImport
  }

  @Transactional
  fun cancelImport(importId: ImportId) {
    val feedImport =
      feedImportRepository.findByImportId(importId).orElseThrow {
        IllegalArgumentException("Import not found: $importId")
      }

    feedImport.status = ImportStatus.CANCELLED
    feedImport.completedAt = clock.instant()
    feedImportRepository.save(feedImport)
  }

  private fun launchImportJob(importId: ImportId, feedId: FeedId) {
    importLaunchExecutor.execute {
      // Build parameters inside executor to ensure unique timestamp (including nanos)
      val params =
        JobParametersBuilder()
          .addString("feedOnestopId", feedId.value, true)
          .addString("importId", importId.value.toString(), true)
          .addLong("timestamp", System.nanoTime(), true) // Use nanoTime for better uniqueness
          .toJobParameters()

      runCatching { jobLauncher.run(feedImportJob, params) }
        .onFailure { throwable ->
          if (throwable is JobExecutionAlreadyRunningException) {
            logger.info("Feed import job already running for {} with import {}", feedId, importId)
            cancelRunningJobExecution(importId, feedId)
            cancelImport(importId)
            return@onFailure
          }
          logger.error("Failed to launch feed import job for {}", feedId, throwable)
          failImport(importId, throwable.message ?: "Failed to start import job")
        }
    }
  }

  @Transactional
  fun completeImport(importId: ImportId, parsedData: GTFSData) {
    val feedImport =
      feedImportRepository.findByImportId(importId).orElseThrow {
        IllegalArgumentException("Import not found: $importId")
      }

    val now = clock.instant()
    feedImport.status = ImportStatus.COMPLETED
    feedImport.completedAt = now
    // TODO: Generate version SHA1 from parsed data or feed archive
    feedImportRepository.save(feedImport)

    if (feedImport.feedId.isNotBlank()) {
      feedRepository.findByFeedOnestopId(feedImport.feedId).ifPresent { feed ->
        feed.status = FeedStatus.ACTIVE
        feed.lastUpdatedAt = now
        // TODO: Set currentVersionSha1 from parsed data
        feedRepository.save(feed)
      }
    }
  }

  @Transactional
  fun failImport(importId: ImportId, message: String) {
    val feedImport =
      feedImportRepository.findByImportId(importId).orElseThrow {
        IllegalArgumentException("Import not found: $importId")
      }

    feedImport.status = ImportStatus.FAILED
    feedImport.completedAt = clock.instant()
    feedImport.errorMessage = message
    feedImportRepository.save(feedImport)
  }

  private fun cancelRunningJobExecution(importId: ImportId, feedId: FeedId) {
    val runningExecutionIds = jobOperator.getRunningExecutions(feedImportJob.name)

    if (runningExecutionIds.isEmpty()) {
      logger.warn(
        "No running job executions found to cancel for feed {} and import {}",
        feedId,
        importId,
      )
      return
    }

    runningExecutionIds.forEach { executionId ->
      runCatching { jobOperator.stop(executionId) }
        .onSuccess {
          logger.info("Stopped running job execution {} for feed {}", executionId, feedId)
        }
        .onFailure { error ->
          logger.warn(
            "Failed to stop running job execution {} for feed {}",
            executionId,
            feedId,
            error,
          )
        }
    }
  }
}

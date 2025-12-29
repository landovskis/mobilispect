package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.events.FeedImportCompletedEvent
import com.mobilispect.backend.feed.events.FeedImportFailedEvent
import com.mobilispect.backend.feed.events.FeedImportStartedEvent
import com.mobilispect.backend.feed.events.FeedImportStepCompletedEvent
import com.mobilispect.backend.feed.events.FeedImportStepStartedEvent
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.websocket.ImportProgress
import com.mobilispect.backend.websocket.ProgressUpdate
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

/**
 * Service for tracking import progress and monitoring job execution lifecycle.
 *
 * Task T032: Create ImportProgressService for Redis-based progress tracking
 *
 * This service listens to FeedImport events, publishes WebSocket progress updates, and monitors
 * Spring Batch job execution lifecycle. It provides a clean interface to progress tracking for
 * both the feed domain and WebSocket controllers.
 *
 * Constitutional Requirement: Observability & Operational Insight
 * - Provides real-time progress visibility via WebSocket broadcasting
 * - Tracks import lifecycle for operational monitoring
 * - Maintains ephemeral state for active imports
 * - Traces job execution lifecycle with structured logging
 * - Publishes events for downstream observability systems
 */
@Service
class FeedImportProgressService(
  private val messagingTemplate: SimpMessagingTemplate,
  private val eventPublisher: ApplicationEventPublisher,
) : JobExecutionListener {
  private val logger = LoggerFactory.getLogger(FeedImportProgressService::class.java)

  // Track active imports and their progress
  private val activeImports = ConcurrentHashMap<ImportId, ImportProgress>()

  @EventListener
  fun onFeedImportStarted(event: FeedImportStartedEvent) {
    val progress =
      ImportProgress(
        importId = event.importId,
        feedId = event.feedId,
        currentStep = "Starting import",
      )

    activeImports[event.importId] = progress
    broadcastProgress(event.importId, progress)
  }

  @EventListener
  fun onFeedImportStepStarted(event: FeedImportStepStartedEvent) {

    val currentProgress = activeImports[event.importId] ?: return
    val updatedProgress = currentProgress.copy(currentStep = event.step)

    activeImports[event.importId] = updatedProgress
    broadcastProgress(event.importId, updatedProgress)
  }

  @EventListener
  fun onFeedImportStepCompleted(event: FeedImportStepCompletedEvent) {
    val currentProgress = activeImports[event.importId] ?: return
    val updatedProgress = currentProgress.copy(currentStep = "${event.step} (completed)")

    activeImports[event.importId] = updatedProgress
    broadcastProgress(event.importId, updatedProgress)
  }

  @EventListener
  fun onFeedImportCompleted(event: FeedImportCompletedEvent) {
    activeImports.remove(event.importId)
    messagingTemplate.convertAndSend(
      "/topic/import/progress/${event.importId}",
      ImportProgress(event.importId, event.feedId, "Finished import",),
    )
  }

  @EventListener
  fun onFeedImportFailed(event: FeedImportFailedEvent) {
    activeImports.remove(event.importId)

    messagingTemplate.convertAndSend(
      "/topic/import/progress/${event.importId}",
      ImportProgress(
        importId = event.importId,
        feedId = event.feedId,
        currentStep = "Failed",
        error = "${event.step}: ${event.message}",
      ),
    )
  }

  /** Get current progress for an import (for REST API or WebSocket queries) */
  fun getProgress(importId: ImportId): ImportProgress? {
    return activeImports[importId]
  }

  /** Get a list of all active import IDs */
  fun getActiveImportIds(): List<ImportId> {
    return activeImports.keys.toList()
  }

  /** Broadcast progress update to WebSocket subscribers */
  private fun broadcastProgress(importId: ImportId, progress: ImportProgress) {
    messagingTemplate.convertAndSend(
      "/topic/import/progress/$importId",
      ProgressUpdate(progress = progress),
    )
    logger.debug(
      "Broadcast progress update for import: {}, step: {}",
      importId,
      progress.currentStep,
    )
  }

  // JobExecutionListener methods

  override fun beforeJob(jobExecution: JobExecution) {
    val feedId = extractFeedId(jobExecution) ?: throw IllegalStateException("Missing feedId parameter")
    val importIdString = jobExecution.jobParameters.getString("importId") ?: throw IllegalStateException("Missing importId parameter")
    val importId = ImportId.fromString(importIdString)

    logger.info("Starting feed import job for feed: {}", feedId.value)
    eventPublisher.publishEvent(FeedImportStartedEvent(feedId, importId))
  }

  override fun afterJob(jobExecution: JobExecution) {
    val feedId = extractFeedId(jobExecution)
    val importIdString = jobExecution.jobParameters.getString("importId")
    val importId = importIdString?.let { ImportId.fromString(it) }
    val status = jobExecution.status
    val exitStatus = jobExecution.exitStatus

    logger.info(
      "Completed feed import job for feed: {} with status: {} (exit code: {})",
      feedId?.value ?: "unknown",
      status,
      exitStatus.exitCode,
    )

    if (feedId != null && importId != null) {
      when (status) {
        BatchStatus.COMPLETED -> {
          eventPublisher.publishEvent(FeedImportCompletedEvent(feedId, importId))
          logger.info("Published FeedImportCompleted event for feed: {}", feedId.value)
        }
        BatchStatus.FAILED -> {
          val errorMessage = extractErrorMessage(jobExecution)
          eventPublisher.publishEvent(FeedImportFailedEvent(feedId, "job", errorMessage, importId))
          logger.error(
            "Published FeedImportFailed event for feed: {}, error: {}",
            feedId.value,
            errorMessage,
          )
        }
        else -> {
          logger.warn("Job completed with unexpected status: {} for feed: {}", status, feedId.value)
        }
      }
    }
  }

  /** Extract FeedId from job execution parameters. */
  private fun extractFeedId(jobExecution: JobExecution): FeedId? {
    val feedOnestopId = jobExecution.jobParameters.getString("feedOnestopId")

    return when {
      feedOnestopId != null -> FeedId.from(feedOnestopId)
      else -> {
        logger.warn("Could not extract feedId from job execution")
        null
      }
    }
  }

  /**
   * Extract error message from job execution.
   *
   * Checks for:
   * 1. Exit status description
   * 2. Failed step exception messages
   * 3. Job-level exception messages
   */
  private fun extractErrorMessage(jobExecution: JobExecution): String {
    // First, check exit status description
    jobExecution.exitStatus.exitDescription?.let { description ->
      if (description.isNotBlank()) {
        return description
      }
    }

    // Second, check for failed step exceptions
    jobExecution.stepExecutions
      .filter { it.status == BatchStatus.FAILED }
      .forEach { stepExecution ->
        stepExecution.failureExceptions.firstOrNull()?.let { exception ->
          return "${stepExecution.stepName}: ${exception.message ?: exception.javaClass.simpleName}"
        }
      }

    // Third, check for job-level exceptions
    jobExecution.allFailureExceptions.firstOrNull()?.let { exception ->
      return exception.message ?: exception.javaClass.simpleName
    }

    // Fallback
    return "Job failed without detailed error message"
  }
}

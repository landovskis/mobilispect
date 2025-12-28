package com.mobilispect.backend.websocket

import com.mobilispect.backend.feed.events.FeedImportCompletedEvent
import com.mobilispect.backend.feed.events.FeedImportFailedEvent
import com.mobilispect.backend.feed.events.FeedImportStartedEvent
import com.mobilispect.backend.feed.events.FeedImportStepCompletedEvent
import com.mobilispect.backend.feed.events.FeedImportStepStartedEvent
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * Event listener that listens to FeedImport events and publishes WebSocket progress updates.
 *
 * This component replaces the imperative ProgressTrackingService by using Spring's event-driven
 * architecture. It automatically tracks import progress and broadcasts updates to WebSocket
 * subscribers when feed import events are published.
 *
 * Constitutional Requirement: Observability & Operational Insight
 * - Provides real-time progress visibility via WebSocket broadcasting
 * - Tracks import lifecycle for operational monitoring
 * - Maintains ephemeral state for active imports
 */
@Component
class FeedImportProgressEventListener(private val messagingTemplate: SimpMessagingTemplate) {
  private val logger = LoggerFactory.getLogger(FeedImportProgressEventListener::class.java)

  // Track active imports and their progress
  private val activeImports = ConcurrentHashMap<String, ImportProgress>()

  // Track step counts per feed for progress calculation
  private val stepTracking = ConcurrentHashMap<String, StepTracking>()

  @EventListener
  fun onFeedImportStarted(event: FeedImportStartedEvent) {
    val importId = event.feedId.value
    val now = Instant.now()

    logger.debug("Feed import started for feed: {}", importId)

    // Initialize step tracking
    stepTracking[importId] =
      StepTracking(totalSteps = estimateTotalSteps(), currentStep = 0, startedAt = now)

    // Initialize progress
    val progress =
      ImportProgress(
        importId = importId,
        feedOnestopId = importId,
        progressPercentage = 0,
        currentStep = "Starting import",
        currentStepNumber = 0,
        totalSteps = estimateTotalSteps(),
        startedAt = now,
        lastUpdatedAt = now,
      )

    activeImports[importId] = progress

    // Broadcast initial progress
    broadcastProgress(importId, progress)
  }

  @EventListener
  fun onFeedImportStepStarted(event: FeedImportStepStartedEvent) {
    val importId = event.feedId.value
    val tracking = stepTracking[importId] ?: return

    logger.debug("Feed import step started for feed: {}, step: {}", importId, event.step)

    val currentProgress = activeImports[importId] ?: return
    val stepNumber = tracking.currentStep + 1
    val progressPercentage = calculateProgressPercentage(stepNumber, tracking.totalSteps)

    val updatedProgress =
      currentProgress.copy(
        progressPercentage = progressPercentage,
        currentStep = event.step,
        currentStepNumber = stepNumber,
        lastUpdatedAt = Instant.now(),
        estimatedTimeRemainingSeconds =
          calculateEstimatedTimeRemaining(tracking.startedAt, progressPercentage),
      )

    activeImports[importId] = updatedProgress
    broadcastProgress(importId, updatedProgress)
  }

  @EventListener
  fun onFeedImportStepCompleted(event: FeedImportStepCompletedEvent) {
    val importId = event.feedId.value
    val tracking = stepTracking[importId] ?: return

    logger.debug("Feed import step completed for feed: {}, step: {}", importId, event.step)

    // Increment step counter
    stepTracking[importId] = tracking.copy(currentStep = tracking.currentStep + 1)

    val currentProgress = activeImports[importId] ?: return
    val stepNumber = tracking.currentStep + 1
    val progressPercentage = calculateProgressPercentage(stepNumber, tracking.totalSteps)

    val updatedProgress =
      currentProgress.copy(
        progressPercentage = progressPercentage,
        currentStep = "${event.step} (completed)",
        currentStepNumber = stepNumber,
        lastUpdatedAt = Instant.now(),
        estimatedTimeRemainingSeconds =
          calculateEstimatedTimeRemaining(tracking.startedAt, progressPercentage),
      )

    activeImports[importId] = updatedProgress
    broadcastProgress(importId, updatedProgress)
  }

  @EventListener
  fun onFeedImportCompleted(event: FeedImportCompletedEvent) {
    val importId = event.feedId.value

    logger.info("Feed import completed for feed: {}", importId)

    val progress = activeImports.remove(importId)
    val tracking = stepTracking.remove(importId)
    val finishedAt = Instant.now()
    val durationSeconds = tracking?.let { Duration.between(it.startedAt, finishedAt).seconds }

    messagingTemplate.convertAndSend(
      "/topic/import/progress/$importId",
      ProgressUpdate(completed = true, finishedAt = finishedAt, durationSeconds = durationSeconds),
    )

    logger.debug("Broadcast completion message for import: {}", importId)
  }

  @EventListener
  fun onFeedImportFailed(event: FeedImportFailedEvent) {
    val importId = event.feedId.value

    logger.error(
      "Feed import failed for feed: {}, step: {}, message: {}",
      importId,
      event.step,
      event.message,
    )

    val progress = activeImports.remove(importId)
    val tracking = stepTracking.remove(importId)
    val finishedAt = Instant.now()
    val durationSeconds = tracking?.let { Duration.between(it.startedAt, finishedAt).seconds }

    messagingTemplate.convertAndSend(
      "/topic/import/progress/$importId",
      ProgressUpdate(
        error = "${event.step}: ${event.message}",
        finishedAt = finishedAt,
        durationSeconds = durationSeconds,
      ),
    )

    logger.debug("Broadcast failure message for import: {}", importId)
  }

  /** Get current progress for an import (for REST API or WebSocket queries) */
  fun getProgress(importId: String): ImportProgress? {
    return activeImports[importId]
  }

  /** Get list of all active import IDs */
  fun getActiveImportIds(): List<String> {
    return activeImports.keys.toList()
  }

  /** Check if import is currently active */
  fun isActive(importId: String): Boolean {
    return activeImports.containsKey(importId)
  }

  /** Broadcast progress update to WebSocket subscribers */
  private fun broadcastProgress(importId: String, progress: ImportProgress) {
    messagingTemplate.convertAndSend(
      "/topic/import/progress/$importId",
      ProgressUpdate(progress = progress),
    )
    logger.debug(
      "Broadcast progress update for import: {}, progress: {}%",
      importId,
      progress.progressPercentage,
    )
  }

  /**
   * Estimate total number of steps in a feed import. This is a heuristic based on typical import
   * jobs.
   */
  private fun estimateTotalSteps(): Int {
    // Typical steps: download, agency, routes, trips, stop_times, shapes, stops, etc.
    return 8
  }

  /** Calculate progress percentage based on step number */
  private fun calculateProgressPercentage(currentStep: Int, totalSteps: Int): Int {
    if (totalSteps == 0) return 0
    return ((currentStep.toDouble() / totalSteps.toDouble()) * 100).toInt().coerceIn(0, 100)
  }

  /** Calculate estimated time remaining in seconds */
  private fun calculateEstimatedTimeRemaining(startedAt: Instant, progressPercentage: Int): Long? {
    if (progressPercentage <= 0) return null

    val elapsedSeconds = Duration.between(startedAt, Instant.now()).seconds
    val estimatedTotalSeconds = (elapsedSeconds.toDouble() / progressPercentage.toDouble()) * 100.0
    val remainingSeconds = estimatedTotalSeconds - elapsedSeconds

    return remainingSeconds.toLong().coerceAtLeast(0)
  }

  /** Internal tracking for step progress */
  private data class StepTracking(
    val totalSteps: Int,
    val currentStep: Int,
    val startedAt: Instant,
  )
}

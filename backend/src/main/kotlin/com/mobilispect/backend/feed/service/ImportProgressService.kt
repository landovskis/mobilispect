package com.mobilispect.backend.feed.service

import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Service for tracking import progress using Redis for transient data storage.
 *
 * Task T032: Create ImportProgressService for Redis-based progress tracking
 *
 * This service manages ephemeral import progress data that doesn't need to be persisted
 * to the database. Progress data is stored in Redis with TTL expiration and automatically
 * cleaned up after imports complete.
 *
 * Progress data includes:
 * - Real-time progress percentages (0-100)
 * - Current processing step information
 * - Estimated time remaining
 * - Processing rates (entities/second)
 * - Started timestamp
 *
 * This implementation delegates to the existing ProgressTrackingService in the websocket
 * package, which manages the actual progress tracking and WebSocket broadcasting.
 * This facade provides the feed domain with a clean interface to progress tracking
 * while maintaining separation of concerns.
 */
@Service
class ImportProgressService(
    private val progressTrackingService: com.mobilispect.backend.websocket.ProgressTrackingService
) {

    /**
     * Update progress for an import operation.
     */
    fun updateProgress(
        importId: String,
        feedOnestopId: String,
        progressPercentage: Int,
        currentStep: String,
        currentStepNumber: Int,
        totalSteps: Int,
        startedAt: Instant,
        estimatedTimeRemainingSeconds: Long? = null,
        processingRate: Double? = null
    ) {
        progressTrackingService.updateProgress(
            importId = importId,
            feedOnestopId = feedOnestopId,
            progressPercentage = progressPercentage,
            currentStep = currentStep,
            currentStepNumber = currentStepNumber,
            totalSteps = totalSteps,
            startedAt = startedAt,
            estimatedTimeRemainingSeconds = estimatedTimeRemainingSeconds,
            processingRate = processingRate
        )
    }

    /**
     * Mark import as completed and remove from active tracking.
     */
    fun markCompleted(importId: String) {
        progressTrackingService.markCompleted(importId)
    }

    /**
     * Mark import as failed with error message.
     */
    fun markFailed(importId: String, errorMessage: String) {
        progressTrackingService.markFailed(importId, errorMessage)
    }

    /**
     * Get current progress for an import.
     */
    fun getProgress(importId: String): com.mobilispect.backend.websocket.ImportProgress? {
        return progressTrackingService.getProgress(importId)
    }

    /**
     * Check if import is currently active.
     */
    fun isActive(importId: String): Boolean {
        return progressTrackingService.isActive(importId)
    }

    /**
     * Get list of all active import IDs.
     */
    fun getActiveImportIds(): List<String> {
        return progressTrackingService.getActiveImportIds()
    }
}

package com.mobilispect.backend.websocket

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for tracking and broadcasting import progress via WebSocket
 */
@Service
class ProgressTrackingService(
    private val messagingTemplate: SimpMessagingTemplate
) {
    // Track active imports and their progress
    private val activeImports = ConcurrentHashMap<String, ImportProgress>()

    /**
     * Update progress for an import and broadcast to subscribers
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
        val now = Instant.now()
        val progress = ImportProgress(
            importId = importId,
            feedOnestopId = feedOnestopId,
            progressPercentage = progressPercentage,
            currentStep = currentStep,
            currentStepNumber = currentStepNumber,
            totalSteps = totalSteps,
            startedAt = startedAt,
            lastUpdatedAt = now,
            estimatedTimeRemainingSeconds = estimatedTimeRemainingSeconds,
            processingRate = processingRate
        )

        activeImports[importId] = progress

        // Broadcast progress update to topic subscribers
        messagingTemplate.convertAndSend(
            "/topic/import/progress/$importId",
            ProgressUpdate(progress = progress)
        )
    }

    /**
     * Mark import as completed and remove from active tracking
     */
    fun markCompleted(importId: String) {
        val progress = activeImports.remove(importId)
        val finishedAt = Instant.now()
        val durationSeconds = progress?.let { Duration.between(it.startedAt, finishedAt).seconds }
        messagingTemplate.convertAndSend(
            "/topic/import/progress/$importId",
            ProgressUpdate(completed = true, finishedAt = finishedAt, durationSeconds = durationSeconds)
        )
    }

    /**
     * Mark import as failed with error message
     */
    fun markFailed(importId: String, errorMessage: String) {
        val progress = activeImports.remove(importId)
        val finishedAt = Instant.now()
        val durationSeconds = progress?.let { Duration.between(it.startedAt, finishedAt).seconds }
        messagingTemplate.convertAndSend(
            "/topic/import/progress/$importId",
            ProgressUpdate(error = errorMessage, finishedAt = finishedAt, durationSeconds = durationSeconds)
        )
    }

    /**
     * Get current progress for an import
     */
    fun getProgress(importId: String): ImportProgress? {
        return activeImports[importId]
    }

    /**
     * Get list of all active import IDs
     */
    fun getActiveImportIds(): List<String> {
        return activeImports.keys.toList()
    }

    /**
     * Check if import is currently active
     */
    fun isActive(importId: String): Boolean {
        return activeImports.containsKey(importId)
    }
}

package com.mobilispect.backend.feed.service

import com.mobilispect.backend.websocket.FeedImportProgressEventListener
import com.mobilispect.backend.websocket.ImportProgress
import org.springframework.stereotype.Service

/**
 * Service for tracking import progress.
 *
 * Task T032: Create ImportProgressService for Redis-based progress tracking
 *
 * This service provides the feed domain with a clean interface to progress tracking
 * while maintaining separation of concerns. Progress tracking is now handled by
 * the FeedImportProgressEventListener which listens to feed import events and
 * publishes WebSocket updates automatically.
 *
 * This facade delegates to the event listener for querying current progress state,
 * while progress updates are driven by publishing FeedImport domain events.
 */
@Service
class ImportProgressService(
    private val progressEventListener: FeedImportProgressEventListener
) {

    /**
     * Get current progress for an import.
     */
    fun getProgress(importId: String): ImportProgress? {
        return progressEventListener.getProgress(importId)
    }

    /**
     * Check if import is currently active.
     */
    fun isActive(importId: String): Boolean {
        return progressEventListener.isActive(importId)
    }

    /**
     * Get list of all active import IDs.
     */
    fun getActiveImportIds(): List<String> {
        return progressEventListener.getActiveImportIds()
    }
}

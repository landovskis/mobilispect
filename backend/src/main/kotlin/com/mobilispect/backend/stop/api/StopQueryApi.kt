package com.mobilispect.backend.stop.api

import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.stop.domain.model.ids.StopId

/**
 * Public API for querying stops.
 *
 * This is the Stop module's API contract for cross-module communication.
 * Other modules should use this API instead of accessing repositories directly.
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 */
interface StopQueryApi {
    /**
     * Find a stop by its onestop ID.
     *
     * @param stopId The unique identifier for the stop
     * @return The stop DTO if found, null otherwise
     */
    fun findStopById(stopId: StopId): StopDTO?

    /**
     * Find all stops associated with a specific feed.
     *
     * @param feedId The feed identifier
     * @return List of stops in the feed
     */
    fun findStopsByFeed(feedId: FeedId): List<StopDTO>

    /**
     * Validate that a stop exists.
     *
     * @param stopId The stop identifier to validate
     * @return true if stop exists, false otherwise
     */
    fun validateStopExists(stopId: StopId): Boolean
}

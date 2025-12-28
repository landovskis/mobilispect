package com.mobilispect.backend.feed.api

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.RegionId

/**
 * Public API for querying feeds.
 *
 * This is the Feed module's API contract for cross-module communication.
 * Other modules should use this API instead of accessing repositories directly.
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 */
interface FeedQueryApi {
    /**
     * Find a feed by its onestop ID.
     *
     * @param feedId The unique identifier for the feed
     * @return The feed DTO if found, null otherwise
     */
    fun findFeedById(feedId: FeedId): FeedDTO?

    /**
     * Find all feeds associated with a specific region.
     *
     * @param regionId The region identifier
     * @return List of feeds in the region
     */
    fun findFeedsByRegion(regionId: RegionId): List<FeedDTO>

    /**
     * Get the current version SHA1 for a feed.
     *
     * @param feedId The feed identifier
     * @return The version SHA1 if available, null otherwise
     */
    fun getFeedVersion(feedId: FeedId): String?
}

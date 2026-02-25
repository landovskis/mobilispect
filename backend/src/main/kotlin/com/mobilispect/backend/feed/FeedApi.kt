package com.mobilispect.backend.feed

import com.mobilispect.backend.feed.api.FeedDTO
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.region.RegionId
import java.util.UUID

/**
 * Public API for querying feeds.
 *
 * This is the Feed module's API contract for cross-module communication. Other modules should use
 * this API instead of accessing repositories directly.
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 */
interface FeedApi {
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
  fun findFeedsByRegion(regionId: RegionId): List<Feed>

  /**
   * Find all ACTIVE feeds associated with a specific region.
   *
   * @param regionId The region identifier
   * @return List of active feeds in the region
   */
  fun findActiveFeedsByRegion(regionId: RegionId): List<Feed>

  /**
   * Get the current version SHA1 for a feed.
   *
   * @param feedId The feed identifier
   * @return The version SHA1 if available, null otherwise
   */
  fun getFeedVersion(feedId: FeedId): String?

  /**
   * Get the current status of a feed import.
   *
   * @param importId The UUID of the feed import
   * @return The import status if found, null otherwise
   */
  fun getImportStatus(importId: UUID): ImportStatus?
}

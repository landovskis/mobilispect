package com.mobilispect.backend.gtfsrt.domain.repository

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFeedState

/**
 * Repository for GTFS-RT feed deduplication state.
 *
 * Stores per-feed state to detect unchanged content across polling cycles. Implementation uses
 * Redis with a 24-hour TTL per ADR 0011.
 */
interface GtfsRtFeedStateRepository {

  /**
   * Find the state for a feed.
   *
   * @param feedId The feed identifier
   * @return The feed state, or null if not found
   */
  fun findByFeedId(feedId: FeedId): GtfsRtFeedState?

  /**
   * Save or update the state for a feed.
   *
   * @param state The feed state to save
   * @return The saved feed state
   */
  fun save(state: GtfsRtFeedState): GtfsRtFeedState

  /**
   * Delete the state for a feed.
   *
   * @param feedId The feed identifier
   */
  fun deleteByFeedId(feedId: FeedId)
}

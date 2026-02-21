package com.mobilispect.backend.vehicle.domain.model

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import java.time.Instant

/**
 * Tracks the last known state of a GTFS-RT feed for deduplication. Stored in Redis per feed to
 * detect unchanged content across polling cycles.
 */
data class GtfsRtFeedState(
  val feedId: FeedId,

  /** SHA-256 hash of the last response body. */
  val contentHash: String?,

  /** HTTP ETag header from last response (for conditional requests). */
  val etag: String?,

  /** HTTP Last-Modified header from last response (for conditional requests). */
  val lastModified: String?,

  /** The header.timestamp from the last processed GTFS-RT FeedMessage. */
  val gtfsRtTimestamp: Long?,

  /** When the feed was last fetched (regardless of whether it was processed). */
  val lastFetchedAt: Instant,

  /** When the feed was last actually processed (new data persisted). */
  val lastProcessedAt: Instant?,
)

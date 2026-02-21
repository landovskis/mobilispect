package com.mobilispect.backend.vehicle.domain.model

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import java.time.Instant

/**
 * Result of fetching a single GTFS-RT feed. Represents one of three states: new data available,
 * data unchanged (skip processing), or fetch failure.
 */
sealed class GtfsRtFetchResult {
  abstract val feedId: FeedId
  abstract val timestamp: Instant

  /** Successfully fetched new data that should be processed. */
  data class NewData(
    override val feedId: FeedId,
    val data: ByteArray,
    val contentHash: String,
    val etag: String?,
    val lastModified: String?,
    val fetchedAt: Instant,
  ) : GtfsRtFetchResult() {
    override val timestamp: Instant = fetchedAt

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is NewData) return false
      return feedId == other.feedId && contentHash == other.contentHash
    }

    override fun hashCode(): Int {
      var result = feedId.hashCode()
      result = 31 * result + contentHash.hashCode()
      return result
    }
  }

  /** Data unchanged — skip processing. */
  data class Unchanged(
    override val feedId: FeedId,
    val reason: UnchangedReason,
    val checkedAt: Instant,
  ) : GtfsRtFetchResult() {
    override val timestamp: Instant = checkedAt
  }

  /** Fetch failed. */
  data class Failure(override val feedId: FeedId, val error: Throwable, val failedAt: Instant) :
    GtfsRtFetchResult() {
    override val timestamp: Instant = failedAt
  }
}

/** Reason why feed data was unchanged and processing was skipped. */
enum class UnchangedReason {
  /** Server returned HTTP 304 Not Modified. */
  HTTP_NOT_MODIFIED,

  /** Content hash (SHA-256) matches previous fetch. */
  CONTENT_HASH_MATCH,

  /** GTFS-RT header.timestamp is not newer than last processed. */
  TIMESTAMP_NOT_NEWER,
}

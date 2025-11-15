package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.feed.model.FeedSpecType
import java.time.LocalDate

/**
 * Represents metadata for a GTFS feed including download URL and version information.
 *
 * This data class is used by the feed import batch job to map feed IDs to their
 * download URLs and associated metadata needed for import processing.
 *
 * @property feedOnestopId The Transit.land onestop ID for the feed
 * @property name Human-readable name of the feed/operator
 * @property downloadUrl Direct URL to download the feed data
 * @property specType Type of feed specification (GTFS or GTFS-RT)
 * @property versionSha1 SHA1 hash of the current feed version
 * @property earliestCalendarDate Earliest date in the feed's calendar
 * @property latestCalendarDate Latest date in the feed's calendar
 * @property staticFeedUrl URL for static GTFS feed (if available)
 * @property realtimeFeedUrl URL for real-time feed updates (if available)
 * @property authorizationType Type of authentication required (if any)
 * @property authorizationInfoUrl URL with authorization instructions (if required)
 */
data class FeedMetadata(
    val feedOnestopId: String,
    val name: String,
    val downloadUrl: String,
    val specType: FeedSpecType,
    val versionSha1: String,
    val earliestCalendarDate: LocalDate,
    val latestCalendarDate: LocalDate,
    val staticFeedUrl: String? = null,
    val realtimeFeedUrl: String? = null,
    val authorizationType: String? = null,
    val authorizationInfoUrl: String? = null
)

/**
 * Represents a mapping of feed onestop IDs to their metadata.
 *
 * This is the output type for the FeedMetadataReader, providing a batch of
 * feeds with their associated metadata for processing.
 *
 * @property feeds Map of feed onestop ID to FeedMetadata
 */
data class FeedMetadataMap(
    val feeds: Map<String, FeedMetadata>
) {
    /**
     * Number of feeds in this map.
     */
    val size: Int
        get() = feeds.size

    /**
     * Returns true if this map contains no feeds.
     */
    fun isEmpty(): Boolean = feeds.isEmpty()

    /**
     * Returns true if this map contains at least one feed.
     */
    fun isNotEmpty(): Boolean = feeds.isNotEmpty()

    /**
     * Gets metadata for a specific feed ID.
     */
    operator fun get(feedId: String): FeedMetadata? = feeds[feedId]

    /**
     * Returns all feed IDs in this map.
     */
    fun feedIds(): Set<String> = feeds.keys

    /**
     * Filters this map to include only the specified feed IDs.
     */
    fun filterKeys(feedIds: Set<String>): FeedMetadataMap {
        return FeedMetadataMap(feeds.filterKeys { it in feedIds })
    }
}

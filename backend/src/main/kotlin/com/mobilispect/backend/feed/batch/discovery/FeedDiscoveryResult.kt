package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.feed.model.FeedSpecType
import java.time.LocalDate

/**
 * Represents a complete feed discovery result combining feed metadata with region information.
 *
 * This data class aggregates information from both the operator parsing stage (region data)
 * and TransitLandMetadataService (feed details) to provide a comprehensive view of a discovered feed.
 *
 * @property feedOnestopId The Transit.land onestop ID for the feed
 * @property name Human-readable name of the feed/operator
 * @property downloadUrl Direct URL to download the feed data
 * @property specType Type of feed specification (GTFS or GTFS-RT)
 * @property versionSha1 SHA1 hash of the current feed version
 * @property earliestCalendarDate Earliest date in the feed's calendar
 * @property latestCalendarDate Latest date in the feed's calendar
 * @property region Region metadata for this feed
 * @property staticFeedUrl URL for static GTFS feed (if available)
 * @property realtimeFeedUrl URL for real-time feed updates (if available)
 * @property authorizationType Type of authentication required (if any)
 * @property authorizationInfoUrl URL with authorization instructions (if required)
 */
data class FeedDiscoveryResult(
    val feedOnestopId: String,
    val name: String,
    val downloadUrl: String,
    val specType: FeedSpecType,
    val versionSha1: String,
    val earliestCalendarDate: LocalDate,
    val latestCalendarDate: LocalDate,
    val region: RegionMetadata,
    val staticFeedUrl: String? = null,
    val realtimeFeedUrl: String? = null,
    val authorizationType: String? = null,
    val authorizationInfoUrl: String? = null
)

/**
 * Represents a batch of feed discovery results.
 *
 * This is the output type for the FeedDiscoveryProcessor, containing all feeds
 * that were successfully matched with both metadata and region information.
 *
 * @property results List of feed discovery results
 * @property totalFeeds Total number of feeds processed
 * @property successfulMatches Number of feeds with complete data (both metadata and region)
 * @property missingMetadata Number of feeds missing metadata
 * @property missingRegion Number of feeds missing region information
 */
data class FeedDiscoveryBatch(
    val results: List<FeedDiscoveryResult>
) {
    val totalFeeds: Int
        get() = results.size

    val successfulMatches: Int
        get() = results.size

    /**
     * Groups results by region.
     */
    fun groupByRegion(): Map<String, List<FeedDiscoveryResult>> {
        return results.groupBy { it.region.regionOnestopId }
    }

    /**
     * Groups results by spec type.
     */
    fun groupBySpecType(): Map<FeedSpecType, List<FeedDiscoveryResult>> {
        return results.groupBy { it.specType }
    }

    /**
     * Gets all unique regions in this batch.
     */
    fun regions(): Set<RegionMetadata> {
        return results.map { it.region }.toSet()
    }

    /**
     * Gets all feed IDs in this batch.
     */
    fun feedIds(): List<String> {
        return results.map { it.feedOnestopId }
    }

    /**
     * Returns true if this batch contains no results.
     */
    fun isEmpty(): Boolean = results.isEmpty()

    /**
     * Returns true if this batch contains at least one result.
     */
    fun isNotEmpty(): Boolean = results.isNotEmpty()
}

/**
 * Input data for the FeedDiscoveryProcessor.
 *
 * Combines the outputs from operator parsing and TransitLandMetadataService
 * to enable joining feed metadata with region information.
 *
 * @property feedRegionMap Map of feed IDs to region metadata
 * @property feedMetadataMap Map of feed IDs to feed metadata
 */
data class FeedDiscoveryInput(
    val feedRegionMap: FeedRegionMap,
    val feedMetadataMap: FeedMetadataMap
)

package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.feed.model.FeedId

/**
 * Represents metadata about a geographic region from Transit.land.
 *
 * This data class captures location information extracted from operator data,
 * used to associate feeds with their geographic regions.
 *
 * @property regionOnestopId Computed region identifier (e.g., "san-francisco-ca-usa")
 * @property regionName Human-readable region name (e.g., "San Francisco, CA, USA")
 * @property cityName City name from place information
 * @property adm1Name Administrative division level 1 (state/province)
 * @property adm0Name Administrative division level 0 (country)
 * @property operatorName Name of the transit operator providing this feed
 */
data class RegionMetadata(
    val regionOnestopId: String,
    val regionName: String,
    val cityName: String? = null,
    val adm1Name: String? = null,
    val adm0Name: String? = null,
    val operatorName: String? = null
)

/**
 * Represents a mapping of feed onestop IDs to their region metadata.
 *
 * This is the intermediate output from processing Transit.land operators,
 * providing a map of feeds to their associated geographic regions for
 * downstream batch processing.
 *
 * @property feedToRegionMap Map of feed onestop ID to RegionMetadata
 */
data class FeedRegionMap(
    val feedToRegionMap: Map<FeedId, RegionMetadata>
) {
    /**
     * Number of feeds in this map.
     */
    val size: Int
        get() = feedToRegionMap.size

    /**
     * Returns true if this map contains no feeds.
     */
    fun isEmpty(): Boolean = feedToRegionMap.isEmpty()

    /**
     * Returns true if this map contains at least one feed.
     */
    fun isNotEmpty(): Boolean = feedToRegionMap.isNotEmpty()

    /**
     * Gets region metadata for a specific feed ID.
     */
    operator fun get(feedId: FeedId): RegionMetadata? = feedToRegionMap[feedId]

    /**
     * Returns all feed IDs in this map.
     */
    fun feedIds(): Set<FeedId> = feedToRegionMap.keys

    /**
     * Groups feeds by region, returning a map of region ID to feed IDs.
     */
    fun groupByRegion(): Map<String, Set<FeedId>> {
        return feedToRegionMap.entries
            .groupBy({ it.value.regionOnestopId }, { it.key })
            .mapValues { it.value.toSet() }
    }

    /**
     * Returns all unique regions in this map.
     */
    fun regions(): Set<RegionMetadata> = feedToRegionMap.values.toSet()

    /**
     * Filters this map to include only the specified feed IDs.
     */
    fun filterKeys(feedIds: Set<FeedId>): FeedRegionMap {
        return FeedRegionMap(feedToRegionMap.filterKeys { it in feedIds })
    }
}

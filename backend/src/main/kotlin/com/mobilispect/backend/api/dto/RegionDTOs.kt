package com.mobilispect.backend.api.dto

import java.time.Instant

/**
 * Metropolitan Region DTO
 *
 * Represents a metropolitan region with transit feeds.
 */
data class MetropolitanRegionDTO(
    val regionOnestopId: String,
    val name: String,
    val autoUpdateEnabled: Boolean,
    val feedCount: Int,
    val lastCheckAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Regions Response
 *
 * Wrapper response for listing regions.
 */
data class RegionsResponse(
    val regions: List<MetropolitanRegionDTO>,
    val total: Int
)

/**
 * Region Update Request
 *
 * Request to update region configuration.
 */
data class RegionUpdateRequest(
    val autoUpdateEnabled: Boolean?
)

/**
 * Feed DTO
 *
 * Represents a transit feed within a region.
 */
data class FeedDTO(
    val feedOnestopId: String,
    val regionOnestopId: String,
    val name: String,
    val specType: FeedSpecType,
    val downloadUrl: String,
    val currentVersionSha1: String?,
    val lastCheckedAt: Instant?,
    val lastUpdatedAt: Instant?,
    val status: FeedStatus,
    val hasAuthentication: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Feed specification types
 */
enum class FeedSpecType {
    GTFS,
    GTFS_RT
}

/**
 * Feed status values
 */
enum class FeedStatus {
    ACTIVE,
    INACTIVE,
    ERROR
}

/**
 * Feeds Response
 *
 * Wrapper response for listing feeds.
 */
data class FeedsResponse(
    val feeds: List<FeedDTO>,
    val total: Int
)

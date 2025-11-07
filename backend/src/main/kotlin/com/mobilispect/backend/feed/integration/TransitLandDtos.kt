package com.mobilispect.backend.feed.integration

import com.mobilispect.backend.feed.model.FeedSpecType
import java.time.Instant

/**
 * Simplified representation of a Transit.land feed returned by [TransitLandApiClient].
 */
data class TransitLandFeedSummary(
    val feedOnestopId: String,
    val name: String,
    val specType: FeedSpecType,
    val staticFeedUrl: String?,
    val realtimeFeedUrl: String?,
    val latestVersionSha1: String?,
    val latestVersionUrl: String?,
    val latestVersionFetchedAt: Instant?,
    val operatorName: String?,
    val authorization: TransitLandAuthorizationSummary?,
    val places: List<PlaceSummary>
)

/**
 * Authorization hints for accessing a Transit.land feed.
 */
data class TransitLandAuthorizationSummary(
    val type: String,
    val parameterName: String?,
    val infoUrl: String?
)

/**
 * Geographic location information from Transit.land.
 */
data class PlaceSummary(
    val adm0Name: String?,
    val adm1Name: String?,
    val cityName: String?
)

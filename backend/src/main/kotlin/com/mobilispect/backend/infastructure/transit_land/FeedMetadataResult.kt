package com.mobilispect.backend.infastructure.transit_land

import com.mobilispect.backend.feed.model.FeedSpecType
import java.time.LocalDate

/**
 * The result of fetching feed metadata from Transit.land API.
 *
 * Contains comprehensive feed information including version details,
 * URLs, and authorization requirements.
 */
data class FeedMetadataResult(
    val feedOnestopId: String,
    val name: String?,
    val spec: FeedSpecType,
    val downloadUrl: String,
    val versionSha1: String,
    val earliestCalendarDate: LocalDate,
    val latestCalendarDate: LocalDate,
    val staticFeedUrl: String? = null,
    val realtimeFeedUrl: String? = null,
    val authorizationType: String? = null,
    val authorizationInfoUrl: String? = null
)

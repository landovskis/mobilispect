package com.mobilispect.backend.infastructure.transit_land

import com.mobilispect.backend.AgencyResult
import com.mobilispect.backend.transit_land.PagingParameters
import com.mobilispect.backend.schedule.ScheduledFeed

/**
 * A client to access the transitland API.
 * Implementations should handle rate limiting and concurrency control.
 */
interface TransitLandAPI {
    /**
     * Retrieve the feed identified by [feedID].
     */
    fun feed(apiKey: String, feedID: String): Result<ScheduledFeed>

    /**
     * Retrieve all feeds that match the given [search] criteria.
     * Searches across feed names, operator names, and regions.
     */
    fun feeds(apiKey: String, search: String, spec: String = "gtfs"): Result<Collection<ScheduledFeed>>

    /**
     * Retrieve all feeds within a geographic area.
     * Uses latitude, longitude, and radius (in meters) to find feeds.
     */
    fun feedsByCoordinates(apiKey: String, lat: Double, lon: Double, radius: Int = 50000, spec: String = "gtfs"): Result<Collection<ScheduledFeed>>

    /**
     * Retrieve all agencies that serve a given [region] or are contained in the feed identified by [feedID].
     */
    @Suppress("ReturnCount")
    fun agencies(apiKey: String, region: String? = null, feedID: String? = null): Result<AgencyResult>

    /**
     * Retrieve the routes contained in the feed identified by [feedID].
     */
    fun routes(apiKey: String, feedID: String, paging: PagingParameters = PagingParameters()): Result<RouteResult>

    /**
     * Retrieve all stops contained in the feed identified by [feedID].
     */
    fun stop(apiKey: String, feedID: String, stopID: String): Result<StopResultItem>

    /**
     * Retrieve all operators with pagination support.
     * Returns a paginated list of operators with their feeds.
     */
    fun operators(apiKey: String, paging: PagingParameters = PagingParameters()): Result<OperatorsResult>

    /**
     * Retrieve metadata for a specific feed identified by [feedId].
     * Returns detailed feed information including version, URLs, and authorization details.
     */
    fun feedMetadata(apiKey: String, feedId: String): Result<FeedMetadataResult>
}

package com.mobilispect.backend.schedule.transit_land

import com.mobilispect.backend.AgencyResult
import com.mobilispect.backend.transit_land.PagingParameters
import com.mobilispect.backend.schedule.ScheduledFeed
import com.mobilispect.backend.schedule.transit_land.api.RouteResult
import com.mobilispect.backend.infastructure.transit_land.StopResultItem

/**
 * A client to access the transitland API.
 */
interface TransitLandAPI {
    /**
     * Retrieve the feed identified by [feedID].
     */
    fun feed(apiKey: String, feedID: String): Result<ScheduledFeed>

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
}

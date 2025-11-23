package com.mobilispect.backend.infastructure.transit_land

/**
 * Result item for a single route from Transit.land API.
 */
data class RouteResultItem(val id: String, val agencyID: String, val routeID: String)

/**
 * The combination of routes extracted and any paging parameters.
 */
class RouteResult(val routes: Collection<RouteResultItem>, val after: Int? = null)

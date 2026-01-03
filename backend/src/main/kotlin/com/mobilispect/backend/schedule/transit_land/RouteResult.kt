package com.mobilispect.backend.schedule.transit_land.api

import com.mobilispect.backend.schedule.transit_land.RouteResultItem

/** The combination of routes extracted and any paging parameters. */
class RouteResult(val routes: Collection<RouteResultItem>, val after: Int? = null)

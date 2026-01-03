package com.mobilispect.backend

import com.mobilispect.backend.schedule.route.RouteIDDataSource

/** A [RouteIDDataSource] suitable for testing. */
internal class StubRouteIDDataSource(private val pairs: Map<String, String>) : RouteIDDataSource {
  override fun routeIDs(feedID: String): Result<Map<String, String>> = Result.success(pairs)
}

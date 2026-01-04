package com.mobilispect.backend.route.batch.import

import com.mobilispect.backend.feed.api.GTFSRoute
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.route.domain.model.Route

/**
 * Input data for the RouteProcessor.
 *
 * Combines parsed GTFS route data with feed context needed for generating route onestop IDs.
 *
 * @property parsedRoute The GTFS route data
 * @property feedOnestopId The onestop ID of the feed this route belongs to
 */
data class RouteInput(val parsedRoute: GTFSRoute, val feedOnestopId: String)

/**
 * Represents a batch of route processing results.
 *
 * This is the output type for the RouteProcessor, containing routes converted from GTFS data to
 * domain models with TransitLand-compatible onestop IDs.
 *
 * @property routes List of processed routes ready for persistence
 */
data class RouteBatch(
  val routes: List<Route>,
  val routesByFeedLocalId: MutableMap<FeedLocalRouteId, Route>,
) {
  /** Total number of routes in this batch. */
  val size: Int
    get() = routes.size

  /** Returns true if this batch contains no routes. */
  fun isEmpty(): Boolean = routes.isEmpty()

  /** Returns true if this batch contains at least one route. */
  fun isNotEmpty(): Boolean = routes.isNotEmpty()

  /** Groups routes by agency ID. */
  fun groupByAgency(): Map<String, List<Route>> {
    return routes.groupBy { it.agencyId.value }
  }

  /** Groups routes by route type. */
  fun groupByType(): Map<String, List<Route>> {
    return routes.groupBy { it.routeType.value }
  }
}

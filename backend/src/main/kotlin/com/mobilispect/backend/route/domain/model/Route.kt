package com.mobilispect.backend.route.domain.model

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.api.ids.GTFSRouteId
import com.mobilispect.backend.route.domain.model.ids.RouteId
import java.time.Instant

/**
 * Named transit line operated by an agency.
 *
 * @property id Unique route identifier in Transitland Onestop ID format
 *   (r-{geohash}-{route_identifier})
 * @property agencyId Agency that operates this route
 * @property gtfsRouteId Route ID from GTFS routes.txt file
 * @property shortName Short route name (e.g., "5", "Red Line")
 * @property longName Long route name (e.g., "Downtown Express")
 * @property routeType GTFS route type (bus, rail, subway, etc.)
 * @property color Route color in hex format (e.g., "FF0000")
 * @property textColor Text color for route in hex format
 * @property active Whether this route is currently active
 * @property createdAt Record creation timestamp
 * @property updatedAt Record last update timestamp
 */
data class Route(
  val id: RouteId,
  val agencyId: AgencyId,
  val gtfsRouteId: GTFSRouteId,
  val shortName: String? = null,
  val longName: String,
  val routeType: RouteType,
  val color: String? = null,
  val textColor: String? = null,
  val active: Boolean = true,
  val createdAt: Instant = Instant.now(),
  val updatedAt: Instant = Instant.now(),
)

package com.mobilispect.backend.route.api

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.api.ids.GTFSRouteId
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.model.ids.RouteId

/**
 * Data Transfer Object for Route.
 *
 * Exposes route data across module boundaries without exposing internal entities. Part of the Route
 * module's public API.
 */
data class RouteDTO(
  val routeId: RouteId,
  val agencyId: AgencyId,
  val gtfsRouteId: GTFSRouteId,
  val shortName: String?,
  val longName: String?,
  val routeType: RouteType,
  val color: String?,
  val textColor: String?,
  val active: Boolean,
)

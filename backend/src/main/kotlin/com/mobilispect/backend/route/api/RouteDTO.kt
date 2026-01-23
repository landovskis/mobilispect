package com.mobilispect.backend.route.api

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.RouteType

/**
 * Data Transfer Object for Route.
 *
 * Exposes route data across module boundaries without exposing internal entities. Part of the Route
 * module's public API.
 */
data class RouteDTO(
  val routeId: RouteId,
  val agencyId: AgencyId,
  val gtfsRouteId: String,
  val shortName: String?,
  val longName: String?,
  val routeType: RouteType,
  val color: String?,
  val textColor: String?,
  val active: Boolean,
)

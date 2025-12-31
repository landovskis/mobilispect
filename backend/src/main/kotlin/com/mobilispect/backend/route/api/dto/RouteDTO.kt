package com.mobilispect.backend.route.api.dto

import com.mobilispect.backend.route.domain.model.RouteType

data class RouteDTO(
  val id: String,
  val agencyId: String,
  val shortName: String?,
  val longName: String,
  val routeType: RouteType,
  val active: Boolean,
  val variants: List<RouteVariantDTO>,
  val hourlyStats: List<RouteHourlyStatsDTO>,
)

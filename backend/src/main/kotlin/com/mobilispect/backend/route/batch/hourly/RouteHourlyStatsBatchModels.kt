package com.mobilispect.backend.route.batch.hourly

import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.route.domain.model.RouteHourlyStat
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ServiceDayType

data class RouteHourlyStatsInput(
  val routeId: String,
  val variantTrips: Map<RouteVariant, List<GTFSTrip>>,
  val serviceDayTypes: Map<String, Set<ServiceDayType>>,
)

data class RouteHourlyStatsBatch(val stats: List<RouteHourlyStat>)

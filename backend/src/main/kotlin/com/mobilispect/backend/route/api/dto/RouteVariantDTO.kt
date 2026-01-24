package com.mobilispect.backend.route.api.dto

import java.time.LocalTime

data class RouteVariantDTO(
  val id: String,
  val routeId: String,
  val directionId: Int?,
  val headsign: String?,
  val stopCount: Int,
  val stopPattern: String,
  val stopNames: List<String>,
  val stopSpacingsMeters: List<Double>,
  val firstStopId: String,
  val lastStopId: String,
  val firstDepartureTime: LocalTime?,
  val lastDepartureTime: LocalTime?,
  val scheduleTripCount: Int?,
)

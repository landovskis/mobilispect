package com.mobilispect.backend.route.api.dto

data class CorridorDTO(
  val id: String,
  val stopPattern: String,
  val stopCount: Int,
  val firstStopId: String,
  val lastStopId: String,
  val routes: List<CorridorRouteDTO>,
)

data class CorridorRouteDTO(
  val routeId: String,
  val shortName: String?,
  val longName: String,
)

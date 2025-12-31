package com.mobilispect.backend.route.api.dto

data class RouteHourlyStatsDTO(
  val serviceDate: String,
  val directionId: Int?,
  val dayType: String,
  val hourOfDay: Int,
  val tripCount: Int,
  val averageSpeedKph: Double?,
)

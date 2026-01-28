package com.mobilispect.backend.route.api.dto

/**
 * DTO for the longest continuous section of stops shared by ALL variants
 * in a given direction.
 */
data class RouteCommonSectionDTO(
  val id: String,
  val routeId: String,
  val directionId: Int?,
  val stopPattern: String,
  val stopNames: List<String>,
  val stopCount: Int,
  val firstStopId: String,
  val lastStopId: String,
  val variantCount: Int,
)

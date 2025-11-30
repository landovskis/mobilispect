package com.mobilispect.backend.transitanalysis.api.dto

data class RouteVariantDTO(
    val id: String,
    val routeId: String,
    val directionId: Int?,
    val headsign: String?,
    val stopCount: Int,
    val stopPattern: String,
    val firstStopId: String,
    val lastStopId: String
)

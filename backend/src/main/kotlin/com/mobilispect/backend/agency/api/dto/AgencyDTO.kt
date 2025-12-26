package com.mobilispect.backend.agency.api.dto

import com.mobilispect.backend.route.domain.model.RouteType

data class AgencyDTO(
    val id: String,
    val name: String,
    val feedOnestopId: String,
    val regionIds: Set<String>,
    val routeCount: Int,
    val activeRouteCount: Int,
    val routesByType: Map<RouteType, Int>
)

data class AgencySummaryDTO(
    val id: String,
    val name: String,
    val routeCount: Int,
    val averageHeadwayMinutes: Double?,
    val minHeadwayMinutes: Double?,
    val maxHeadwayMinutes: Double?
)

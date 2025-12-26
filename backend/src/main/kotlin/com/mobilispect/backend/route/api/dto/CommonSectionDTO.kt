package com.mobilispect.backend.route.api.dto

data class CommonSectionDTO(
    val id: String,
    val stopPattern: String,
    val stopCount: Int,
    val firstStopId: String,
    val lastStopId: String,
    val variants: List<String>
)

data class CombinedFrequencyDTO(
    val commonSectionId: String,
    val timePeriod: String,
    val averageHeadwayMinutes: Double?,
    val tripCount: Int,
    val isIrregular: Boolean,
    val contributions: List<RouteContributionDTO> = emptyList()
)

data class RouteContributionDTO(
    val routeId: String,
    val averageHeadwayMinutes: Double?,
    val tripCount: Int,
    val isIrregular: Boolean
)

package com.mobilispect.backend.transitanalysis.api.dto

import com.mobilispect.backend.transitanalysis.domain.model.RouteType

data class RouteDTO(
    val id: String,
    val agencyId: String,
    val shortName: String?,
    val longName: String,
    val routeType: RouteType,
    val active: Boolean
)

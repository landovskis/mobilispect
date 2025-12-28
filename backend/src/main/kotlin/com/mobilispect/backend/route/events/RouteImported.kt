package com.mobilispect.backend.route.events

import com.mobilispect.backend.route.domain.model.ids.RouteId

data class RouteImported(
    val routeId: RouteId,
    val gtfsRouteId: String
)

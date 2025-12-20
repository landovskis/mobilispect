package com.mobilispect.backend.transitanalysis.events

import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash

data class RouteVariantIdentified(
    val variantId: VariantHash,
    val routeId: RouteId
)

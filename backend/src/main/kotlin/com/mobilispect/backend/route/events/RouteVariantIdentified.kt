package com.mobilispect.backend.route.events

import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.model.ids.VariantHash

data class RouteVariantIdentified(val variantId: VariantHash, val routeId: RouteId)

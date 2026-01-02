package com.mobilispect.mobile.data.transit_land

import kotlinx.serialization.Serializable

/**
 * A route returned by the transit.land API.
 */
@Serializable
data class TransitLandRouteResponse(
    val routes: Collection<TransitLandRoute>,
)

package com.mobilispect.mobile.data.transit_land

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class TransitLandRoute(
    @SerialName("route_long_name")
    val longName: String,

    @SerialName("route_short_name")
    val shortName: String,
)

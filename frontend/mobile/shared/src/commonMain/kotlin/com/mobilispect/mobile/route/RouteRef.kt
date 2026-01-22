package com.mobilispect.mobile.route

/**
 * A reference to a route in OneStopID format
 *
 * Reference: https://www.transit.land/documentation/onestop-id-scheme/
 *
 */
data class RouteRef(
    private val geohash: String,
    val routeNumber: String,
) {
    val id: String = "r-$geohash-$routeNumber"

    companion object {
        fun fromString(string: String?): RouteRef? =
            string?.let {
                val split = it.split("-")
                RouteRef(geohash = split[1], routeNumber = split[2])
            }
    }
}

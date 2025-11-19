@file:Suppress("PropertyName", "ConstructorParameterNaming") // For _id and medianHeadway_min

package com.mobilispect.backend.schedule

import com.mobilispect.backend.AGENCY_ONESTOP_ID_REGEX


val ROUTE_ONESTOP_ID_REGEX = Regex("r-[a-z0-9]+-[a-z0-9~çéâêîôûàèùëïü]+")

data class Route(
    /**
     * ID that is globally unique.
     */
    val uid: String,

    /**
     * ID that is a local to feed.
     */
    val localID: String,
    val shortName: String,
    val longName: String,
    val agencyID: String,
    val versions: List<String>
) {
    init {
        require(uid.matches(ROUTE_ONESTOP_ID_REGEX)) { "Route id must be of the form r-geohash-id, Was $uid" }
        require(localID.isNotBlank()) { "Local ID must not be blank" }
        require(shortName.isNotBlank()) { "Short name must not be blank" }
        require(longName.isNotBlank()) { "Long name must not be blank" }
        require(agencyID.matches(AGENCY_ONESTOP_ID_REGEX)) { "Agency ID must be of the form o-geohash-id, Was $agencyID" }
        require(versions.isNotEmpty()) { "Versions must not be empty" }
    }
}

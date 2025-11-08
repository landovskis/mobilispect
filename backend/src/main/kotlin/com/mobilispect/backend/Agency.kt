package com.mobilispect.backend

import org.springframework.data.mongodb.core.mapping.Document

val AGENCY_ONESTOP_ID_REGEX = Regex("o(-[a-z0-9]+)?-[a-z0-9~çéâêîôûàèùëïü]+")

@Document(value = "agencies")
data class Agency(
    /**
     * ID that is unique globally (in OneStop ID format ex: o-geohash-name)
     */
    val uid: String,

    /**
     * ID that is unique to a feed.
     */
    val localID: String,
    val name: String,
    val versions: Collection<String>
) {
    init {
        require(uid.matches(AGENCY_ONESTOP_ID_REGEX)) { "Must be in OneStopID format i.e. o[-geohash]-name. Was $uid" }
        require(localID.isNotBlank()) { "Local ID must not be blank." }
        require(name.isNotBlank()) { "Name must not be blank." }
        require(versions.isNotEmpty()) { "Versions must not be empty." }
    }
}

package com.mobilispect.backend.transitanalysis.domain.model

import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.transitanalysis.domain.model.ids.StopId
import java.time.Instant

/**
 * Domain model representing a transit stop or station.
 *
 * Stops are physical locations where passengers board or alight from transit vehicles.
 * Each stop is uniquely identified by its Transitland Onestop ID (s-geohash-name format).
 *
 * Per GTFS specification, a stop can represent:
 * - location_type 0: A stop/platform where passengers board
 * - location_type 1: A station (group of stops)
 * - location_type 2: An entrance/exit to a station
 * - location_type 3: A generic node in a pathway
 * - location_type 4: A boarding area within a platform
 *
 * @property stopOnestopId Transitland Onestop ID (s-geohash-name format) - primary identifier
 * @property feedId Reference to the GTFS feed this stop belongs to
 * @property gtfsStopId Original stop_id from GTFS stops.txt
 * @property name Stop or station name
 * @property latitude WGS 84 latitude (-90 to +90)
 * @property longitude WGS 84 longitude (-180 to +180)
 * @property stopCode Short text or number for riders (e.g., stop code on signage)
 * @property stopDesc Description providing useful information
 * @property zoneId Fare zone identifier
 * @property stopUrl URL for this stop
 * @property locationType Type of location (0=stop, 1=station, 2=entrance, 3=node, 4=boarding area)
 * @property parentStation For stops with parent stations, the station_id
 * @property active Whether this stop is currently active in the feed
 * @property firstSeen When this stop was first observed in GTFS data
 * @property lastSeen When this stop was last observed in GTFS data
 * @property createdAt When this record was created in our system
 * @property updatedAt When this record was last updated
 */
data class Stop(
    val stopOnestopId: StopId,
    val feedId: FeedId,
    val gtfsStopId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val stopCode: String? = null,
    val stopDesc: String? = null,
    val zoneId: String? = null,
    val stopUrl: String? = null,
    val locationType: Int? = null,
    val parentStation: String? = null,
    val active: Boolean = true,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "Stop name cannot be blank" }
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90, got $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180, got $longitude" }
        require(locationType == null || locationType in 0..4) {
            "Location type must be null or 0-4 (0=stop, 1=station, 2=entrance, 3=node, 4=boarding area), got $locationType"
        }
    }

    /**
     * Returns whether this stop is a station (container for multiple stops).
     */
    fun isStation(): Boolean = locationType == 1

    /**
     * Returns whether this stop is a platform/stop where passengers board.
     */
    fun isPlatform(): Boolean = locationType == null || locationType == 0

    /**
     * Returns whether this stop requires a parent station reference.
     */
    fun requiresParentStation(): Boolean = locationType != null && locationType in 2..4
}

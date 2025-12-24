package com.mobilispect.backend.transitanalysis.api.dto

import com.mobilispect.backend.transitanalysis.domain.model.Stop
import java.time.Instant

/**
 * Data Transfer Object for Stop API responses.
 *
 * Represents a transit stop or station with all GTFS attributes and tracking metadata.
 */
data class StopDTO(
    val stopOnestopId: String,
    val feedId: String,
    val gtfsStopId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val stopCode: String?,
    val stopDesc: String?,
    val zoneId: String?,
    val stopUrl: String?,
    val locationType: Int?,
    val locationTypeDescription: String?,
    val parentStation: String?,
    val active: Boolean,
    val firstSeen: Instant,
    val lastSeen: Instant
) {
    companion object {
        /**
         * Convert Stop domain model to DTO.
         */
        fun fromDomain(stop: Stop): StopDTO = StopDTO(
            stopOnestopId = stop.stopOnestopId.value,
            feedId = stop.feedId.value,
            gtfsStopId = stop.gtfsStopId,
            name = stop.name,
            latitude = stop.latitude,
            longitude = stop.longitude,
            stopCode = stop.stopCode,
            stopDesc = stop.stopDesc,
            zoneId = stop.zoneId,
            stopUrl = stop.stopUrl,
            locationType = stop.locationType,
            locationTypeDescription = getLocationTypeDescription(stop.locationType),
            parentStation = stop.parentStation,
            active = stop.active,
            firstSeen = stop.firstSeen,
            lastSeen = stop.lastSeen
        )

        private fun getLocationTypeDescription(locationType: Int?): String? = when (locationType) {
            0 -> "Stop/Platform"
            1 -> "Station"
            2 -> "Entrance/Exit"
            3 -> "Generic Node"
            4 -> "Boarding Area"
            else -> null
        }
    }
}

/**
 * Simplified stop summary for list views.
 */
data class StopSummaryDTO(
    val stopOnestopId: String,
    val gtfsStopId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val locationType: Int?,
    val active: Boolean
) {
    companion object {
        fun fromDomain(stop: Stop): StopSummaryDTO = StopSummaryDTO(
            stopOnestopId = stop.stopOnestopId.value,
            gtfsStopId = stop.gtfsStopId,
            name = stop.name,
            latitude = stop.latitude,
            longitude = stop.longitude,
            locationType = stop.locationType,
            active = stop.active
        )
    }
}

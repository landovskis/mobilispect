package com.mobilispect.backend.stop.api

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.stop.domain.model.Stop
import com.mobilispect.backend.stop.domain.model.ids.StopId
import java.time.Instant

/**
 * Data Transfer Object for Stop.
 *
 * Exposes stop data across module boundaries without exposing internal entities. Part of the Stop
 * module's public API.
 */
data class StopDTO(
  val stopId: StopId,
  val feedId: FeedId,
  val gtfsStopId: String,
  val name: String,
  val latitude: Double,
  val longitude: Double,
  val locationType: Int?,
  val parentStationId: String?,
  val wheelchairBoarding: Int?,
  val platformCode: String?,
  val zoneId: String?,
  val createdAt: Instant,
  val updatedAt: Instant,
) {
  companion object {
    fun fromDomain(stop: Stop): StopDTO =
      StopDTO(
        stopId = stop.stopOnestopId,
        feedId = stop.feedId,
        gtfsStopId = stop.gtfsStopId,
        name = stop.name,
        latitude = stop.latitude,
        longitude = stop.longitude,
        locationType = stop.locationType,
        parentStationId = stop.parentStation,
        wheelchairBoarding = null, // Not in domain model
        platformCode = null, // Not in domain model
        zoneId = stop.zoneId,
        createdAt = stop.createdAt,
        updatedAt = stop.updatedAt,
      )
  }
}

/**
 * Simplified stop summary for list views.
 *
 * Provides essential stop information for paginated lists without full details.
 */
data class StopSummaryDTO(
  val stopId: StopId,
  val feedId: FeedId,
  val gtfsStopId: String,
  val name: String,
  val latitude: Double,
  val longitude: Double,
  val locationType: Int?,
) {
  companion object {
    fun fromDomain(stop: Stop): StopSummaryDTO =
      StopSummaryDTO(
        stopId = stop.stopOnestopId,
        feedId = stop.feedId,
        gtfsStopId = stop.gtfsStopId,
        name = stop.name,
        latitude = stop.latitude,
        longitude = stop.longitude,
        locationType = stop.locationType,
      )
  }
}

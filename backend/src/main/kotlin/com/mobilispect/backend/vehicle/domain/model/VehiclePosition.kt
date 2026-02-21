package com.mobilispect.backend.vehicle.domain.model

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import java.time.Instant

/**
 * Realtime vehicle position from a GTFS-RT feed.
 *
 * Represents the current location of a transit vehicle at a point in time.
 */
data class VehiclePosition(
  val feedId: FeedId,
  val vehicleId: String,
  val tripId: String?,
  val routeId: String?,
  val latitude: Double,
  val longitude: Double,
  val bearing: Float?,
  val speed: Float?,
  val currentStopSequence: Int?,
  val currentStatus: VehicleStopStatus?,
  val timestamp: Instant,
  val congestionLevel: CongestionLevel?,
  val occupancyStatus: OccupancyStatus?,
)

/** Vehicle's relationship to the current stop. */
enum class VehicleStopStatus {
  INCOMING_AT,
  STOPPED_AT,
  IN_TRANSIT_TO,
}

/** Congestion level affecting the vehicle. */
enum class CongestionLevel {
  UNKNOWN_CONGESTION_LEVEL,
  RUNNING_SMOOTHLY,
  STOP_AND_GO,
  CONGESTION,
  SEVERE_CONGESTION,
}

/** Passenger occupancy status. */
enum class OccupancyStatus {
  EMPTY,
  MANY_SEATS_AVAILABLE,
  FEW_SEATS_AVAILABLE,
  STANDING_ROOM_ONLY,
  CRUSHED_STANDING_ROOM_ONLY,
  FULL,
  NOT_ACCEPTING_PASSENGERS,
  NO_DATA_AVAILABLE,
  NOT_BOARDABLE,
}

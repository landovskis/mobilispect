package com.mobilispect.backend.vehicle.domain.model

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import java.time.Instant

/**
 * Realtime trip update from a GTFS-RT feed.
 *
 * Contains predictions for arrival/departure times at stops along a trip.
 */
data class TripUpdate(
  val feedId: FeedId,
  val tripId: String,
  val routeId: String?,
  val vehicleId: String?,
  val timestamp: Instant,
  val delay: Int?,
  val scheduleRelationship: TripScheduleRelationship?,
  val stopTimeUpdates: List<StopTimeUpdate>,
)

/** Relationship of this trip to the static schedule. */
enum class TripScheduleRelationship {
  SCHEDULED,
  ADDED,
  UNSCHEDULED,
  CANCELED,
  REPLACEMENT,
  DUPLICATED,
  DELETED,
  NEW,
}

/** Realtime update for a single stop in a trip. */
data class StopTimeUpdate(
  val stopSequence: Int?,
  val stopId: String?,
  val arrival: StopTimeEvent?,
  val departure: StopTimeEvent?,
  val scheduleRelationship: StopScheduleRelationship?,
)

/** Predicted arrival or departure time. */
data class StopTimeEvent(val delay: Int?, val time: Instant?, val uncertainty: Int?)

/** Relationship of this stop to the static schedule. */
enum class StopScheduleRelationship {
  SCHEDULED,
  SKIPPED,
  NO_DATA,
  UNSCHEDULED,
}

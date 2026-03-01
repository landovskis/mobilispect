package com.mobilispect.backend.gtfsrt.domain.model

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import java.time.Instant

/**
 * Realtime service alert from a GTFS-RT feed.
 *
 * Contains information about disruptions or changes to service.
 */
data class ServiceAlert(
  val feedId: FeedId,
  val alertId: String,
  val cause: AlertCause?,
  val effect: AlertEffect?,
  val headerText: String?,
  val descriptionText: String?,
  val url: String?,
  val activePeriods: List<TimeRange>,
  val informedEntities: List<EntitySelector>,
  val timestamp: Instant,
)

/** Time range during which an alert is active. */
data class TimeRange(val start: Instant?, val end: Instant?)

/** Selector for entities affected by an alert. */
data class EntitySelector(
  val agencyId: String?,
  val routeId: String?,
  val routeType: Int?,
  val tripId: String?,
  val stopId: String?,
)

/** Cause of a service alert. */
enum class AlertCause {
  UNKNOWN_CAUSE,
  OTHER_CAUSE,
  TECHNICAL_PROBLEM,
  STRIKE,
  DEMONSTRATION,
  ACCIDENT,
  HOLIDAY,
  WEATHER,
  MAINTENANCE,
  CONSTRUCTION,
  POLICE_ACTIVITY,
  MEDICAL_EMERGENCY,
}

/** Effect of a service alert. */
enum class AlertEffect {
  NO_SERVICE,
  REDUCED_SERVICE,
  SIGNIFICANT_DELAYS,
  DETOUR,
  ADDITIONAL_SERVICE,
  MODIFIED_SERVICE,
  OTHER_EFFECT,
  UNKNOWN_EFFECT,
  STOP_MOVED,
  NO_EFFECT,
  ACCESSIBILITY_ISSUE,
}

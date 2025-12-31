package com.mobilispect.backend.route.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Hourly trip counts and average speeds for a route.
 *
 * Tracks hourly service volume and performance metrics at the route level. Average speed is
 * calculated from trip duration and inter-stop distances.
 *
 * @property id Unique identifier (UUID)
 * @property routeId Route ID this stat applies to
 * @property directionId GTFS direction_id (0 = outbound, 1 = inbound, null = unknown)
 * @property dayType Day type classification (weekday, saturday, sunday, holiday)
 * @property serviceDate Date this stat applies to
 * @property hourOfDay Hour of day (0-23) for this stat
 * @property tripCount Number of trips starting in this hour
 * @property averageSpeedKph Average trip speed in km/h for this hour
 * @property calculatedAt Timestamp when this stat was calculated
 * @property createdAt Record creation timestamp
 */
@Entity
@Table(name = "route_hourly_stats")
class RouteHourlyStat(
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  val id: UUID? = null,
  @Column(name = "route_id", nullable = false, length = 50) val routeId: String,
  @Column(name = "direction_id") val directionId: Short? = null,
  @Enumerated(EnumType.STRING)
  @Column(name = "day_type", nullable = false)
  val dayType: ServiceDayType = ServiceDayType.WEEKDAY,
  @Column(name = "service_date", nullable = false) val serviceDate: LocalDate,
  @Column(name = "hour_of_day", nullable = false) val hourOfDay: Int,
  @Column(name = "trip_count", nullable = false) val tripCount: Int,
  @Column(name = "average_speed_kph") val averageSpeedKph: Double? = null,
  @Column(name = "calculated_at", nullable = false) val calculatedAt: Instant = Instant.now(),
  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
) {
  constructor() :
    this(
      routeId = "",
      directionId = null,
      dayType = ServiceDayType.WEEKDAY,
      serviceDate = LocalDate.now(),
      hourOfDay = 0,
      tripCount = 0,
      averageSpeedKph = null,
      createdAt = Instant.EPOCH,
      calculatedAt = Instant.EPOCH,
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RouteHourlyStat) return false
    return id != null && id == other.id
  }

  override fun hashCode(): Int = id?.hashCode() ?: 0

  override fun toString(): String =
    "RouteHourlyStat(id=$id, routeId=$routeId, serviceDate=$serviceDate, " +
      "directionId=$directionId, dayType=$dayType, hourOfDay=$hourOfDay, " +
      "tripCount=$tripCount, " +
      "averageSpeedKph=$averageSpeedKph)"
}

package com.mobilispect.backend.route.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Schedule summary for a route variant.
 *
 * Stores the earliest and latest departure times for a variant, along with trip count. This allows
 * quick display of service hours without querying individual trips.
 *
 * @property id Unique identifier (UUID)
 * @property variantId Route variant ID this schedule applies to (SHA-256 hash)
 * @property firstDepartureTime Earliest departure time from the first stop across all trips
 * @property lastDepartureTime Latest departure time from the first stop across all trips
 * @property tripCount Number of trips operating this variant
 * @property clockFaceIntervalMinutes Detected clock-face interval in minutes (10, 12, 15, 20, 30, or 60),
 *   or null if no regular pattern detected
 * @property calculatedAt Timestamp when this schedule summary was calculated
 * @property createdAt Record creation timestamp
 */
@Entity
@Table(name = "variant_schedule")
class VariantSchedule(
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  val id: UUID? = null,
  @Column(name = "variant_id", nullable = false, length = 64, unique = true) val variantId: String,
  @Column(name = "first_departure_time", nullable = false) val firstDepartureTime: LocalTime,
  @Column(name = "last_departure_time", nullable = false) val lastDepartureTime: LocalTime,
  @Column(name = "trip_count", nullable = false) val tripCount: Int,
  @Column(name = "clock_face_interval_minutes", nullable = true) val clockFaceIntervalMinutes: Int? = null,
  @Column(name = "calculated_at", nullable = false) val calculatedAt: Instant = Instant.now(),
  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
) {
  constructor() :
    this(
      variantId = "",
      firstDepartureTime = LocalTime.MIDNIGHT,
      lastDepartureTime = LocalTime.MIDNIGHT,
      tripCount = 0,
      clockFaceIntervalMinutes = null,
      calculatedAt = Instant.EPOCH,
      createdAt = Instant.EPOCH,
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is VariantSchedule) return false
    return id != null && id == other.id
  }

  override fun hashCode(): Int = id?.hashCode() ?: 0

  override fun toString(): String =
    "VariantSchedule(id=$id, variantId=$variantId, " +
      "firstDeparture=$firstDepartureTime, lastDeparture=$lastDepartureTime, tripCount=$tripCount)"
}

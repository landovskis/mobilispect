package com.mobilispect.backend.route.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Individual departure time for a route variant.
 *
 * Stores each departure time from the first stop for a variant. This enables display of complete
 * schedules showing all service times throughout the day.
 *
 * @property id Unique identifier (UUID)
 * @property variantId Route variant ID (SHA-256 hash)
 * @property departureTime Departure time from the first stop
 * @property tripId Original GTFS trip ID for reference
 * @property calculatedAt Timestamp when this departure was recorded
 * @property createdAt Record creation timestamp
 */
@Entity
@Table(
  name = "variant_departures",
  indexes =
    [
      Index(name = "idx_variant_departures_variant", columnList = "variant_id"),
      Index(name = "idx_variant_departures_time", columnList = "variant_id, departure_time"),
      Index(name = "idx_variant_departures_calculated", columnList = "calculated_at"),
    ],
)
class VariantDeparture(
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  val id: UUID? = null,
  @Column(name = "variant_id", nullable = false, length = 64) val variantId: String,
  @Column(name = "departure_time", nullable = false) val departureTime: LocalTime,
  @Column(name = "trip_id", nullable = false, length = 128) val tripId: String,
  @Column(name = "calculated_at", nullable = false) val calculatedAt: Instant = Instant.now(),
  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
) {
  constructor() :
    this(
      variantId = "",
      departureTime = LocalTime.MIDNIGHT,
      tripId = "",
      calculatedAt = Instant.EPOCH,
      createdAt = Instant.EPOCH,
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is VariantDeparture) return false
    return id != null && id == other.id
  }

  override fun hashCode(): Int = id?.hashCode() ?: 0

  override fun toString(): String =
    "VariantDeparture(id=$id, variantId=$variantId, departureTime=$departureTime, tripId=$tripId)"
}

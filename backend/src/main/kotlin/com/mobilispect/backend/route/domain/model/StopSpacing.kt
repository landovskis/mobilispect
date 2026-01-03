package com.mobilispect.backend.route.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Distance between two consecutive stops on a route variant.
 *
 * Tracks the spacing between each pair of consecutive stops along a route variant's path. This
 * allows analysis of stop spacing patterns and identification of service gaps.
 *
 * @property id Unique identifier (UUID)
 * @property variantId Route variant ID this spacing applies to
 * @property fromStopId GTFS stop ID for the origin stop
 * @property toStopId GTFS stop ID for the destination stop
 * @property stopSequence Sequence number of the from-stop in the variant's stop pattern (0-based)
 * @property distanceMeters Distance between the two stops in meters
 * @property calculatedAt Timestamp when this spacing was calculated
 * @property createdAt Record creation timestamp
 */
@Entity
@Table(name = "stop_spacing")
class StopSpacing(
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  val id: UUID? = null,
  @Column(name = "variant_id", nullable = false, length = 64) val variantId: String,
  @Column(name = "from_stop_id", nullable = false, length = 64) val fromStopId: String,
  @Column(name = "to_stop_id", nullable = false, length = 64) val toStopId: String,
  @Column(name = "stop_sequence", nullable = false) val stopSequence: Int,
  @Column(name = "distance_meters", nullable = false) val distanceMeters: Double,
  @Column(name = "calculated_at", nullable = false) val calculatedAt: Instant = Instant.now(),
  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
) {
  constructor() :
    this(
      variantId = "",
      fromStopId = "",
      toStopId = "",
      stopSequence = 0,
      distanceMeters = 0.0,
      createdAt = Instant.EPOCH,
      calculatedAt = Instant.EPOCH,
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is StopSpacing) return false
    return id != null && id == other.id
  }

  override fun hashCode(): Int = id?.hashCode() ?: 0

  override fun toString(): String =
    "StopSpacing(id=$id, variantId=$variantId, " +
      "fromStop=$fromStopId, toStop=$toStopId, sequence=$stopSequence, distance=$distanceMeters m)"
}

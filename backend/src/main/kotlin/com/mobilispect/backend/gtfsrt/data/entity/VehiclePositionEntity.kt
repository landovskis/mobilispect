package com.mobilispect.backend.gtfsrt.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** JPA entity for persisting realtime vehicle positions. */
@Entity
@Table(name = "vehicle_positions")
class VehiclePositionEntity(
  @Id
  @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
  val id: UUID = UUID.randomUUID(),
  @Column(name = "feed_id", nullable = false, length = 128)
  val feedId: String,
  @Column(name = "vehicle_id", nullable = false, length = 128)
  val vehicleId: String,
  @Column(name = "trip_id", length = 128)
  val tripId: String? = null,
  @Column(name = "route_id", length = 128)
  val routeId: String? = null,
  @Column(name = "latitude", nullable = false)
  val latitude: Double,
  @Column(name = "longitude", nullable = false)
  val longitude: Double,
  @Column(name = "bearing")
  val bearing: Float? = null,
  @Column(name = "speed")
  val speed: Float? = null,
  @Column(name = "current_stop_sequence")
  val currentStopSequence: Int? = null,
  @Column(name = "current_status", length = 32)
  val currentStatus: String? = null,
  @Column(name = "timestamp", nullable = false)
  val timestamp: Instant,
  @Column(name = "congestion_level", length = 32)
  val congestionLevel: String? = null,
  @Column(name = "occupancy_status", length = 32)
  val occupancyStatus: String? = null,
  @Column(name = "created_at", nullable = false)
  val createdAt: Instant = Instant.now(),
)

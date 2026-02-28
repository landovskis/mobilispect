package com.mobilispect.backend.gtfsrt.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** JPA entity for persisting realtime trip updates. */
@Entity
@Table(name = "trip_updates")
class TripUpdateEntity(
  @Id
  @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
  val id: UUID = UUID.randomUUID(),
  @Column(name = "feed_id", nullable = false, length = 128)
  val feedId: String,
  @Column(name = "trip_id", nullable = false, length = 128)
  val tripId: String,
  @Column(name = "route_id", length = 128)
  val routeId: String? = null,
  @Column(name = "vehicle_id", length = 128)
  val vehicleId: String? = null,
  @Column(name = "timestamp", nullable = false)
  val timestamp: Instant,
  @Column(name = "delay")
  val delay: Int? = null,
  @Column(name = "schedule_relationship", length = 32)
  val scheduleRelationship: String? = null,
  @Column(name = "created_at", nullable = false)
  val createdAt: Instant = Instant.now(),
)

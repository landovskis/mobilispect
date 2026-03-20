package com.mobilispect.backend.gtfsrt.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** JPA entity for persisting realtime service alerts. */
@Entity
@Table(name = "service_alerts")
class ServiceAlertEntity(
  @Id
  @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
  val id: UUID = UUID.randomUUID(),
  @Column(name = "feed_id", nullable = false, length = 128) val feedId: String,
  @Column(name = "alert_id", nullable = false, length = 128) val alertId: String,
  @Column(name = "cause", length = 32) val cause: String? = null,
  @Column(name = "effect", length = 32) val effect: String? = null,
  @Column(name = "header_text", columnDefinition = "TEXT") val headerText: String? = null,
  @Column(name = "description_text", columnDefinition = "TEXT") val descriptionText: String? = null,
  @Column(name = "url", columnDefinition = "TEXT") val url: String? = null,
  @Column(name = "timestamp", nullable = false) val timestamp: Instant,
  @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
)

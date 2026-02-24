package com.mobilispect.backend.route.data.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * JPA entity for route common section persistence.
 *
 * Stores the longest continuous section of stops shared by ALL variants in a given direction for a
 * specific route. This is different from CommonSection which represents pairwise overlaps.
 */
@Entity
@Table(name = "route_common_sections")
class RouteCommonSectionEntity(
  @Id @Column(name = "id", nullable = false, updatable = false, length = 64) val id: String,
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "route_id", nullable = false)
  val route: RouteEntity,
  @Column(name = "direction_id") val directionId: Int? = null,
  @Column(name = "stop_pattern", nullable = false, columnDefinition = "TEXT")
  val stopPattern: String,
  @Column(name = "stop_name_pattern", nullable = false, columnDefinition = "TEXT")
  val stopNamePattern: String,
  @Column(name = "stop_count", nullable = false) val stopCount: Int,
  @Column(name = "first_stop_id", nullable = false, length = 255) val firstStopId: String,
  @Column(name = "last_stop_id", nullable = false, length = 255) val lastStopId: String,
  @Column(name = "variant_count", nullable = false) var variantCount: Int,
  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
  @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
) {
  @PrePersist
  fun onCreate() {
    val now = Instant.now()
    createdAt = now
    updatedAt = now
  }

  @PreUpdate
  fun onUpdate() {
    updatedAt = Instant.now()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RouteCommonSectionEntity) return false
    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()
}

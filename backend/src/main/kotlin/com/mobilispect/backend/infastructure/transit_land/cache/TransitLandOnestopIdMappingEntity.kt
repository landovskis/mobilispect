package com.mobilispect.backend.infastructure.transit_land.cache

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
  name = "gtfs_onestop_id_mappings",
  uniqueConstraints =
    [
      UniqueConstraint(
        name = "uk_gtfs_onestop_id_mapping",
        columnNames = ["feed_onestop_id", "entity_type", "gtfs_id"],
      )
    ],
)
class TransitLandOnestopIdMappingEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  val id: UUID? = null,
  @Column(name = "feed_onestop_id", nullable = false, length = 512) val feedOnestopId: String,
  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 20)
  val entityType: TransitLandEntityType,
  @Column(name = "gtfs_id", nullable = false, length = 255) val gtfsId: String,
  @Column(name = "onestop_id", nullable = false, length = 255) val onestopId: String,
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
    if (other !is TransitLandOnestopIdMappingEntity) return false
    return id != null && id == other.id
  }

  override fun hashCode(): Int = id?.hashCode() ?: 0
}

package com.mobilispect.backend.region.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/** JPA entity for metropolitan region persistence. */
@Entity
@Table(name = "metropolitan_regions")
class MetropolitanRegionEntity(
  @Id
  @Column(name = "region_onestop_id", nullable = false, updatable = false, length = 255)
  val regionOnestopId: String,
  @Column(name = "name", nullable = false, length = 255) var name: String,
  @Column(name = "adm0_name", length = 255) var adm0Name: String? = null,
  @Column(name = "adm1_name", length = 255) var adm1Name: String? = null,
  @Column(name = "auto_update_enabled", nullable = false) var autoUpdateEnabled: Boolean = true,
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
    if (other !is MetropolitanRegionEntity) return false
    return regionOnestopId == other.regionOnestopId
  }

  override fun hashCode(): Int = regionOnestopId.hashCode()
}

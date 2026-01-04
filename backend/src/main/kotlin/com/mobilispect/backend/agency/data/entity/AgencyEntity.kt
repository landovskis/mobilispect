package com.mobilispect.backend.agency.data.entity

import com.mobilispect.backend.route.data.entity.RouteEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * JPA entity for agency persistence.
 *
 * This is the data layer representation using plain String IDs for Hibernate 7 compatibility. The
 * domain layer uses Agency with type-safe AgencyId IDs.
 */
@Entity
@Table(name = "agencies")
class AgencyEntity(
  @Id
  @Column(name = "agency_onestop_id", nullable = false, updatable = false, length = 255)
  val id: String,
  @Column(name = "feed_onestop_id", nullable = false, length = 512) val feedId: String,
  @Column(name = "name", nullable = false, length = 255) var name: String,
  @Column(name = "active", nullable = false) var active: Boolean = true,
  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
  @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
) {
  @OneToMany(mappedBy = "agency", fetch = FetchType.LAZY)
  val routes: MutableSet<RouteEntity> = mutableSetOf()

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
    if (other !is AgencyEntity) return false
    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()
}

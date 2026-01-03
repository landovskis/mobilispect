package com.mobilispect.backend.route.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Geographic segment where multiple routes/variants overlap.
 *
 * Represents a common section of track or road where multiple route variants provide service along
 * the same sequence of stops. This is useful for calculating combined frequency on corridors served
 * by multiple routes.
 *
 * Constitutional requirement: Must have at least 3 stops to be considered a meaningful common
 * section.
 *
 * @property id Unique identifier (UUID)
 * @property stopPattern Pipe-separated ordered stop IDs (e.g., "stop1|stop2|stop3")
 * @property stopCount Number of stops in the pattern (must be >= 3)
 * @property firstStopId ID of the first stop in the pattern
 * @property lastStopId ID of the last stop in the pattern
 * @property geographicExtent PostGIS LineString geometry (optional, for future use)
 * @property createdAt Record creation timestamp
 * @property updatedAt Record last update timestamp
 */
@Entity
@Table(name = "common_sections")
class CommonSection(
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  val id: UUID? = null,
  @Column(name = "stop_pattern", nullable = false, columnDefinition = "TEXT")
  val stopPattern: String,
  @Column(name = "stop_count", nullable = false) val stopCount: Int,
  @Column(name = "first_stop_id", nullable = false, length = 255) val firstStopId: String,
  @Column(name = "last_stop_id", nullable = false, length = 255) val lastStopId: String,

  // PostGIS geometry support - commented out until PostGIS is enabled
  // @Column(name = "geographic_extent", columnDefinition = "GEOMETRY(LINESTRING, 4326)")
  // val geographicExtent: LineString? = null,

  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
  @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
) {
  @OneToMany(mappedBy = "commonSection", fetch = FetchType.LAZY)
  val variants: MutableSet<CommonSectionVariant> = mutableSetOf()

  constructor() :
    this(
      stopPattern = "",
      stopCount = 3,
      firstStopId = "",
      lastStopId = "",
      createdAt = Instant.EPOCH,
      updatedAt = Instant.EPOCH,
    )

  // Validation removed from init block to allow JPA no-arg constructor instantiation
  // Database constraints enforce these requirements (see migration V027)
  // Application-level validation should be done before calling the constructor

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
    if (other !is CommonSection) return false
    return id != null && id == other.id
  }

  override fun hashCode(): Int = id?.hashCode() ?: 0

  override fun toString(): String =
    "CommonSection(id=$id, stopCount=$stopCount, firstStopId='$firstStopId', lastStopId='$lastStopId')"
}

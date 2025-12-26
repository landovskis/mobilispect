package com.mobilispect.backend.stop.data.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * JPA entity for stop persistence.
 *
 * This is the data layer representation using plain String IDs for Hibernate 7 compatibility.
 * The domain layer uses Stop with type-safe StopId value class.
 */
@Entity
@Table(name = "stops")
class StopEntity(
    @Id
    @Column(name = "stop_onestop_id", nullable = false, updatable = false, length = 255)
    val stopOnestopId: String,

    @Column(name = "feed_onestop_id", nullable = false, length = 512)
    val feedOnestopId: String,

    @Column(name = "gtfs_stop_id", nullable = false, length = 255)
    val gtfsStopId: String,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "latitude", nullable = false)
    var latitude: Double,

    @Column(name = "longitude", nullable = false)
    var longitude: Double,

    @Column(name = "stop_code", length = 50)
    var stopCode: String? = null,

    @Column(name = "stop_desc", columnDefinition = "TEXT")
    var stopDesc: String? = null,

    @Column(name = "zone_id", length = 50)
    var zoneId: String? = null,

    @Column(name = "stop_url", length = 512)
    var stopUrl: String? = null,

    @Column(name = "location_type")
    var locationType: Int? = null,

    @Column(name = "parent_station", length = 255)
    var parentStation: String? = null,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "first_seen", nullable = false)
    var firstSeen: Instant,

    @Column(name = "last_seen", nullable = false)
    var lastSeen: Instant,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
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
        if (other !is StopEntity) return false
        return stopOnestopId == other.stopOnestopId
    }

    override fun hashCode(): Int = stopOnestopId.hashCode()
}

package com.mobilispect.backend.route.data.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * JPA entity for route persistence.
 *
 * This is the data layer representation using plain String IDs for Hibernate 7 compatibility.
 * The domain layer uses Route with type-safe RouteId IDs.
 */
@Entity
@Table(name = "routes")
class RouteEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 255)
    val id: String,

    @Column(name = "agency_onestop_id", nullable = false, length = 255)
    val agencyOnestopId: String,

    @Column(name = "gtfs_route_id", nullable = false, length = 255)
    val gtfsRouteId: String,

    @Column(name = "short_name", length = 255)
    var shortName: String? = null,

    @Column(name = "long_name", nullable = false, length = 255)
    var longName: String,

    @Column(name = "route_type", nullable = false, length = 20)
    var routeType: String,  // Store enum as String

    @Column(name = "color", length = 6)
    var color: String? = null,

    @Column(name = "text_color", length = 6)
    var textColor: String? = null,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToMany(mappedBy = "route", fetch = FetchType.LAZY)
    val variants: MutableSet<RouteVariantEntity> = mutableSetOf()

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
        if (other !is RouteEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

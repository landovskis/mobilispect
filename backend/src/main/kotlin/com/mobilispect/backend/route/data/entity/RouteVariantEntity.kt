package com.mobilispect.backend.route.data.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * JPA entity for route variant persistence.
 *
 * This is the data layer representation using plain String IDs for Hibernate 7 compatibility.
 * The domain layer uses RouteVariant with type-safe VariantHash IDs.
 */
@Entity
@Table(name = "route_variants")
class RouteVariantEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 64)
    val id: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    val route: RouteEntity,

    @Column(name = "direction_id")
    val directionId: Int? = null,

    @Column(name = "headsign", length = 255)
    val headsign: String? = null,

    @Column(name = "stop_pattern", nullable = false, columnDefinition = "TEXT")
    val stopPattern: String,

    @Column(name = "stop_name_pattern", columnDefinition = "TEXT")
    val stopNamePattern: String? = null,

    @Column(name = "stop_count", nullable = false)
    val stopCount: Int,

    @Column(name = "first_stop_id", nullable = false, length = 255)
    val firstStopId: String,

    @Column(name = "last_stop_id", nullable = false, length = 255)
    val lastStopId: String,

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
    @OneToMany(mappedBy = "variant", fetch = FetchType.LAZY)
    val frequencies: MutableSet<FrequencyEntity> = mutableSetOf()

    @OneToMany(mappedBy = "variant", fetch = FetchType.LAZY)
    val commonSectionVariants: MutableSet<CommonSectionVariantEntity> = mutableSetOf()

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
        if (other !is RouteVariantEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

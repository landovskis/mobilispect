package com.mobilispect.backend.transitanalysis.domain.model

import com.mobilispect.backend.transitanalysis.domain.model.converters.VariantHashConverter
import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * Specific service pattern for a route defined by unique stop sequence.
 *
 * Each variant represents a distinct pattern of stops served by trips on a route.
 * The variant ID is a SHA-256 hash of the ordered stop pattern for uniqueness.
 *
 * @property id SHA-256 hash of the stop pattern (64-character hex string)
 * @property route Route this variant belongs to
 * @property directionId GTFS direction_id (0 = outbound, 1 = inbound, null = unknown)
 * @property headsign Destination headsign shown to passengers
 * @property stopPattern Pipe-separated ordered stop IDs (e.g., "stop1|stop2|stop3")
 * @property stopCount Number of stops in the pattern
 * @property firstStopId ID of the first stop in the pattern
 * @property lastStopId ID of the last stop in the pattern
 * @property active Whether this variant is currently active
 * @property firstSeen Timestamp when this variant was first observed
 * @property lastSeen Timestamp when this variant was last observed
 * @property createdAt Record creation timestamp
 * @property updatedAt Record last update timestamp
 */
@Entity
@Table(name = "route_variants")
class RouteVariant(
    @Id
    @Convert(converter = VariantHashConverter::class)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "VARCHAR(64)")
    val id: VariantHash = VariantHash("0".repeat(64)),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    val route: Route,

    @Column(name = "direction_id")
    var directionId: Int? = null,

    @Column(name = "headsign", length = 255)
    var headsign: String? = null,

    @Column(name = "stop_pattern", nullable = false, columnDefinition = "TEXT")
    val stopPattern: String,

    @Column(name = "stop_count", nullable = false)
    val stopCount: Int,

    @Column(name = "first_stop_id", nullable = false, length = 255)
    val firstStopId: String,

    @Column(name = "last_stop_id", nullable = false, length = 255)
    val lastStopId: String,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "first_seen", nullable = false)
    var firstSeen: Instant = Instant.now(),

    @Column(name = "last_seen", nullable = false)
    var lastSeen: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToMany(mappedBy = "variant", fetch = FetchType.LAZY)
    val frequencies: MutableSet<Frequency> = mutableSetOf()

    @OneToMany(mappedBy = "variant", fetch = FetchType.LAZY)
    val commonSectionVariants: MutableSet<CommonSectionVariant> = mutableSetOf()

    constructor() : this(
        id = VariantHash("0".repeat(64)),
        route = Route(),
        stopPattern = "",
        stopCount = 0,
        firstStopId = "",
        lastStopId = "",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        firstSeen = Instant.EPOCH,
        lastSeen = Instant.EPOCH
    )

    // Validation removed from init block to allow JPA no-arg constructor instantiation
    // Database constraints enforce these requirements (see migration V025)
    // Application-level validation should be done before calling the constructor

    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
        firstSeen = firstSeen.takeIf { it != Instant.EPOCH } ?: now
        lastSeen = lastSeen.takeIf { it != Instant.EPOCH } ?: now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RouteVariant) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "RouteVariant(id=$id, stopCount=$stopCount, directionId=$directionId, headsign='$headsign', active=$active)"
}

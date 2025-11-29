package com.mobilispect.backend.transitanalysis.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Service headway (frequency) for a route variant during a specific time period.
 *
 * Tracks how often a particular route variant runs during different times of day
 * and days of the week. Headway is the time between consecutive vehicles serving
 * the same route variant.
 *
 * @property id Unique identifier (UUID)
 * @property variant Route variant this frequency applies to
 * @property serviceDate Date this frequency data applies to
 * @property timePeriod Time period (peak, off-peak, weekend, etc.)
 * @property averageHeadway Average headway in minutes (null if irregular schedule)
 * @property minHeadway Minimum headway in minutes
 * @property maxHeadway Maximum headway in minutes
 * @property tripCount Number of trips in this time period
 * @property isIrregular True if no fixed pattern exists (irregular schedule)
 * @property calculatedAt Timestamp when this frequency was calculated
 * @property createdAt Record creation timestamp
 */
@Entity
@Table(name = "frequencies")
class Frequency(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    val variant: RouteVariant,

    @Column(name = "service_date", nullable = false)
    val serviceDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "time_period", nullable = false, length = 50)
    val timePeriod: TimePeriod,

    @Column(name = "average_headway_minutes")
    var averageHeadway: Double? = null,

    @Column(name = "min_headway_minutes")
    var minHeadway: Double? = null,

    @Column(name = "max_headway_minutes")
    var maxHeadway: Double? = null,

    @Column(name = "trip_count", nullable = false)
    val tripCount: Int,

    @Column(name = "is_irregular", nullable = false)
    val isIrregular: Boolean = false,

    @Column(name = "calculated_at", nullable = false)
    val calculatedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
) {
    constructor() : this(
        variant = RouteVariant(),
        serviceDate = LocalDate.now(),
        timePeriod = TimePeriod.WEEKDAY_OFF_PEAK,
        tripCount = 0,
        createdAt = Instant.EPOCH,
        calculatedAt = Instant.EPOCH
    )

    init {
        require(tripCount >= 0) { "Trip count must be non-negative" }
        require(!isIrregular || averageHeadway == null) {
            "Irregular schedules should not have average headway"
        }
        averageHeadway?.let { avg ->
            require(avg > 0) { "Average headway must be positive if provided" }
        }
        minHeadway?.let { min ->
            require(min > 0) { "Min headway must be positive if provided" }
        }
        maxHeadway?.let { max ->
            require(max > 0) { "Max headway must be positive if provided" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frequency) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "Frequency(id=$id, serviceDate=$serviceDate, timePeriod=$timePeriod, " +
                "averageHeadway=$averageHeadway, tripCount=$tripCount, isIrregular=$isIrregular)"
}

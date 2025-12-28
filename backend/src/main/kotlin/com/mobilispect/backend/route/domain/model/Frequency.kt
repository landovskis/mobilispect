package com.mobilispect.backend.route.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Service headway (frequency) for a route variant during a specific time period.
 *
 * Tracks how often a particular route variant runs during different times of day and days of the
 * week. Headway is the time between consecutive vehicles serving the same route variant.
 *
 * @property id Unique identifier (UUID)
 * @property variantId Route variant ID this frequency applies to
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
  @Column(name = "variant_id", nullable = false, length = 64) val variantId: String,
  @Column(name = "service_date", nullable = false) val serviceDate: LocalDate,
  @Enumerated(EnumType.STRING)
  @Column(name = "time_period", nullable = false, length = 50)
  val timePeriod: TimePeriod,
  @Column(name = "average_headway_minutes") var averageHeadway: Double? = null,
  @Column(name = "min_headway_minutes") var minHeadway: Double? = null,
  @Column(name = "max_headway_minutes") var maxHeadway: Double? = null,
  @Column(name = "trip_count", nullable = false) val tripCount: Int,
  @Column(name = "is_irregular", nullable = false) val isIrregular: Boolean = false,
  @Column(name = "calculated_at", nullable = false) val calculatedAt: Instant = Instant.now(),
  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
) {
  constructor() :
    this(
      variantId = "",
      serviceDate = LocalDate.now(),
      timePeriod = TimePeriod.WEEKDAY_OFF_PEAK,
      tripCount = 0,
      createdAt = Instant.EPOCH,
      calculatedAt = Instant.EPOCH,
    )

  // Validation removed from init block to allow JPA no-arg constructor instantiation
  // Database constraints enforce these requirements (see migration V026)
  // Application-level validation should be done before calling the constructor

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

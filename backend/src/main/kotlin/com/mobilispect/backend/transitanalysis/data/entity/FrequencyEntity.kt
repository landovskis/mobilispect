package com.mobilispect.backend.transitanalysis.data.entity

import com.mobilispect.backend.transitanalysis.domain.model.TimePeriod
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA entity for frequency persistence.
 */
@Entity
@Table(name = "frequencies")
class FrequencyEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    val variant: RouteVariantEntity,

    @Column(name = "service_date", nullable = false)
    val serviceDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "time_period", nullable = false, length = 32)
    val timePeriod: TimePeriod,

    @Column(name = "average_headway_minutes")
    val averageHeadway: Double? = null,

    @Column(name = "min_headway_minutes")
    val minHeadway: Double? = null,

    @Column(name = "max_headway_minutes")
    val maxHeadway: Double? = null,

    @Column(name = "trip_count", nullable = false)
    val tripCount: Int,

    @Column(name = "is_irregular", nullable = false)
    val isIrregular: Boolean,

    @Column(name = "calculated_at", nullable = false)
    val calculatedAt: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

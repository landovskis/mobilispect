package com.mobilispect.backend.transitanalysis.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * Junction entity linking common sections to route variants.
 *
 * Represents the many-to-many relationship between common sections and route variants,
 * tracking which variants traverse which common sections and at what sequence positions.
 *
 * @property id Unique identifier (UUID)
 * @property commonSection Common section this variant traverses
 * @property variant Route variant that traverses the common section
 * @property startSequence Position in variant's stop pattern where common section starts
 * @property endSequence Position in variant's stop pattern where common section ends
 */
@Entity
@Table(name = "common_section_variants")
class CommonSectionVariant(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "common_section_id", nullable = false)
    val commonSection: CommonSection,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    val variant: RouteVariant,

    @Column(name = "start_sequence", nullable = false)
    val startSequence: Int,

    @Column(name = "end_sequence", nullable = false)
    val endSequence: Int
) {
    constructor() : this(
        commonSection = CommonSection(),
        variant = RouteVariant(),
        startSequence = 0,
        endSequence = 0
    )

    init {
        require(startSequence < endSequence) {
            "Start sequence must be less than end sequence"
        }
        require(startSequence >= 0) { "Start sequence must be non-negative" }
        require(endSequence >= 0) { "End sequence must be non-negative" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommonSectionVariant) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "CommonSectionVariant(id=$id, startSequence=$startSequence, endSequence=$endSequence)"
}

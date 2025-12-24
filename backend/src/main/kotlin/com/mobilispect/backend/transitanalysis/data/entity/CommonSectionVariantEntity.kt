package com.mobilispect.backend.transitanalysis.data.entity

import jakarta.persistence.*
import java.util.UUID

/**
 * JPA entity for common section variant association persistence.
 */
@Entity
@Table(name = "common_section_variants")
class CommonSectionVariantEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "common_section_id", nullable = false)
    val commonSection: CommonSectionEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    val variant: RouteVariantEntity,

    @Column(name = "start_sequence", nullable = false)
    val startSequence: Int,

    @Column(name = "end_sequence", nullable = false)
    val endSequence: Int
)

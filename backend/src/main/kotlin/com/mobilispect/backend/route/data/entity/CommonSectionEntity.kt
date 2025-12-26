package com.mobilispect.backend.route.data.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for common section persistence.
 */
@Entity
@Table(name = "common_sections")
class CommonSectionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "stop_pattern", nullable = false, columnDefinition = "TEXT")
    val stopPattern: String,

    @Column(name = "stop_count", nullable = false)
    val stopCount: Int,

    @Column(name = "first_stop_id", nullable = false, length = 255)
    val firstStopId: String,

    @Column(name = "last_stop_id", nullable = false, length = 255)
    val lastStopId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToMany(mappedBy = "commonSection", fetch = FetchType.LAZY)
    val variants: MutableSet<CommonSectionVariantEntity> = mutableSetOf()

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}

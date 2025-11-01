package com.mobilispect.backend.feed.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "metropolitan_regions")
class MetropolitanRegion(
    @Id
    @Column(name = "region_onestop_id", nullable = false, updatable = false, length = 255)
    val regionOnestopId: String = "",

    @Column(nullable = false, length = 255)
    var name: String = "",

    @Column(name = "auto_update_enabled", nullable = false)
    var autoUpdateEnabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToMany(mappedBy = "region", fetch = FetchType.LAZY)
    val feeds: MutableSet<FeedEntity> = mutableSetOf()

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
}

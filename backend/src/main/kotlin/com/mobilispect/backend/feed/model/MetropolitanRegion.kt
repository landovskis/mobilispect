package com.mobilispect.backend.feed.model

import com.mobilispect.backend.feed.model.ids.RegionId
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "metropolitan_regions")
class MetropolitanRegion(
    @EmbeddedId
    @AttributeOverride(name = "value", column = Column(name = "region_onestop_id", nullable = false, updatable = false, length = 255))
    var regionOnestopId: RegionId = RegionId(""),

    @Column(nullable = false, length = 255)
    var name: String = "",

    @Column(name = "adm0_name", nullable = true, length = 255)
    var adm0Name: String? = null,

    @Column(name = "adm1_name", nullable = true, length = 255)
    var adm1Name: String? = null,

    @Column(name = "auto_update_enabled", nullable = false)
    var autoUpdateEnabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {

    constructor() : this(
        regionOnestopId = RegionId("placeholder"),
        name = "",
        adm0Name = null,
        adm1Name = null,
        autoUpdateEnabled = false,
        updatedAt = Instant.EPOCH,
        createdAt = Instant.EPOCH
    )

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

package com.mobilispect.backend.feed.model

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import java.time.Instant

@Entity
@Table(name = "feeds")
class FeedEntity(
    @Id
    @Column(name = "feed_onestop_id", nullable = false, updatable = false, length = 255)
    val feedOnestopId: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_onestop_id", nullable = false)
    var region: MetropolitanRegion? = null,

    @Column(nullable = false, length = 255)
    var name: String = "",

    @Convert(converter = FeedSpecTypeConverter::class)
    @ColumnTransformer(read = "spec_type::text", write = "?::feed_spec_type")
    @Column(name = "spec_type", nullable = false, length = 15, columnDefinition = "feed_spec_type")
    var specType: FeedSpecType = FeedSpecType.GTFS,

    @Column(name = "download_url", nullable = false, columnDefinition = "text")
    var downloadUrl: String = "",

    @Column(name = "current_version_sha1", length = 40)
    var currentVersionSha1: String? = null,

    @Column(name = "last_checked_at")
    var lastCheckedAt: Instant? = null,

    @Column(name = "last_updated_at")
    var lastUpdatedAt: Instant? = null,

    @Convert(converter = FeedStatusConverter::class)
    @ColumnTransformer(read = "status::text", write = "?::feed_status")
    @Column(nullable = false, length = 15, columnDefinition = "feed_status")
    var status: FeedStatus = FeedStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToOne(mappedBy = "feed", fetch = FetchType.LAZY)
    var authentication: FeedAuthentication? = null

    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
        lastCheckedAt = lastCheckedAt ?: now
        lastUpdatedAt = lastUpdatedAt ?: lastCheckedAt
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}

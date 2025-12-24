package com.mobilispect.backend.feed.data.entity

import com.mobilispect.backend.agency.data.entity.AgencyEntity
import com.mobilispect.backend.feed.model.AuthType
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import jakarta.persistence.*
import java.time.Instant

/**
 * JPA entity for feed persistence.
 *
 * This is the data layer representation using plain String IDs for Hibernate 7 compatibility.
 */
@Entity(name = "FeedDataEntity")
@Table(name = "feeds")
class FeedEntity(
    @Id
    @Column(name = "feed_onestop_id", nullable = false, updatable = false, length = 512)
    val feedOnestopId: String,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "spec_type", nullable = false, length = 16)
    var specType: FeedSpecType,

    @Column(name = "download_url", nullable = false, columnDefinition = "TEXT")
    var downloadUrl: String,

    @Column(name = "static_feed_url", columnDefinition = "TEXT")
    var staticFeedUrl: String? = null,

    @Column(name = "realtime_feed_url", columnDefinition = "TEXT")
    var realtimeFeedUrl: String? = null,

    @Column(name = "operator_name", length = 255)
    var operatorName: String? = null,

    @Column(name = "current_version_sha1", length = 40)
    var currentVersionSha1: String? = null,

    @Column(name = "last_checked_at")
    var lastCheckedAt: Instant? = null,

    @Column(name = "last_updated_at")
    var lastUpdatedAt: Instant? = null,

    @Column(name = "last_discovered_at")
    var lastDiscoveredAt: Instant? = null,

    @Column(name = "status", nullable = false, length = 16)
    var status: FeedStatus,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "feed_regions",
        joinColumns = [JoinColumn(name = "feed_onestop_id")],
        inverseJoinColumns = [JoinColumn(name = "region_onestop_id")]
    )
    val regions: MutableSet<MetropolitanRegionEntity> = mutableSetOf()

    @OneToMany(mappedBy = "feed", fetch = FetchType.LAZY)
    val agencies: MutableSet<AgencyEntity> = mutableSetOf()

    @OneToOne(mappedBy = "feed", fetch = FetchType.LAZY)
    var authentication: FeedAuthenticationEntity? = null

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FeedEntity) return false
        return feedOnestopId == other.feedOnestopId
    }

    override fun hashCode(): Int = feedOnestopId.hashCode()
}

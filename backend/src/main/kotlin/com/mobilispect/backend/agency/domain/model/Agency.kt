package com.mobilispect.backend.agency.domain.model

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.transitanalysis.domain.model.Route
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * Transit operator providing public transportation service.
 *
 * An agency belongs to a feed, and inherits region membership through the feed's
 * many-to-many relationship with metropolitan regions via the feed_regions junction table.
 *
 * Relationship chain: Agency -> Feed (via feed_onestop_id) -> Regions (via feed_regions table)
 *
 * @property agencyOnestopId Unique agency identifier using Transitland Onestop ID format (o-geohash-name)
 * @property feed Feed entity this agency belongs to
 * @property gtfsAgencyId Agency ID from GTFS agency.txt file
 * @property name Agency display name
 * @property website Agency website URL
 * @property phone Agency contact phone number
 * @property lastFeedImport Timestamp of last successful feed import
 * @property active Whether this agency is currently active
 * @property createdAt Record creation timestamp
 * @property updatedAt Record last update timestamp
 */
@Entity
@Table(name = "agencies")
class Agency(
    @EmbeddedId
    @AttributeOverride(name = "value", column = Column(name = "agency_onestop_id", nullable = false, updatable = false, length = 255))
    val agencyOnestopId: AgencyId = AgencyId(""),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_onestop_id", nullable = false)
    val feed: FeedEntity,

    @Column(name = "gtfs_agency_id", nullable = false, length = 255)
    val gtfsAgencyId: String,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "website", length = 512)
    var website: String? = null,

    @Column(name = "phone", length = 50)
    var phone: String? = null,

    @Column(name = "last_feed_import")
    var lastFeedImport: Instant? = null,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToMany(mappedBy = "agency", fetch = FetchType.LAZY)
    val routes: MutableSet<Route> = mutableSetOf()

    constructor() : this(
        agencyOnestopId = AgencyId("placeholder"),
        feed = FeedEntity(),
        gtfsAgencyId = "",
        name = "",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Agency) return false
        return agencyOnestopId == other.agencyOnestopId
    }

    override fun hashCode(): Int = agencyOnestopId.hashCode()

    override fun toString(): String =
        "Agency(agencyOnestopId=$agencyOnestopId, gtfsAgencyId='$gtfsAgencyId', name='$name', active=$active)"
}

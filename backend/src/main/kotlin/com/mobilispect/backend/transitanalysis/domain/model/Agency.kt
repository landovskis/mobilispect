package com.mobilispect.backend.transitanalysis.domain.model

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.transitanalysis.domain.model.converters.AgencyIdConverter
import com.mobilispect.backend.transitanalysis.domain.model.ids.AgencyId
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
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
 * @property id Unique agency identifier using Onestop ID format (o-geohash-name)
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
    @Id
    @Convert(converter = AgencyIdConverter::class)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "VARCHAR(255)")
    val id: AgencyId = AgencyId(""),

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
        id = AgencyId("placeholder"),
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
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Agency(id=$id, gtfsAgencyId='$gtfsAgencyId', name='$name', active=$active)"
}

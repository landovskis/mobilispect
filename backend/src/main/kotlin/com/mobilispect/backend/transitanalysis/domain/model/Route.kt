package com.mobilispect.backend.transitanalysis.domain.model

import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Convert
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
 * Named transit line operated by an agency.
 *
 * @property id Unique route identifier in Transitland Onestop ID format (r-{geohash}-{route_identifier})
 * @property agency Agency that operates this route
 * @property gtfsRouteId Route ID from GTFS routes.txt file
 * @property shortName Short route name (e.g., "5", "Red Line")
 * @property longName Long route name (e.g., "Downtown Express")
 * @property routeType GTFS route type (bus, rail, subway, etc.)
 * @property color Route color in hex format (e.g., "FF0000")
 * @property textColor Text color for route in hex format
 * @property active Whether this route is currently active
 * @property createdAt Record creation timestamp
 * @property updatedAt Record last update timestamp
 */
@Entity
@Table(name = "routes")
class Route(
    @EmbeddedId
    @AttributeOverride(name = "value", column = Column(name = "id", nullable = false, updatable = false, columnDefinition = "VARCHAR(255)"))
    val id: RouteId = RouteId("r-placeholder"),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_onestop_id", nullable = false)
    val agency: Agency,

    @Column(name = "gtfs_route_id", nullable = false, length = 255)
    val gtfsRouteId: String,

    @Column(name = "short_name", length = 255)
    var shortName: String? = null,

    @Column(name = "long_name", nullable = false, length = 255)
    var longName: String,

    @Convert(converter = com.mobilispect.backend.transitanalysis.domain.model.converters.RouteTypeConverter::class)
    @Column(name = "route_type", nullable = false)
    var routeType: RouteType,

    @Column(name = "color", length = 6)
    var color: String? = null,

    @Column(name = "text_color", length = 6)
    var textColor: String? = null,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToMany(mappedBy = "route", fetch = FetchType.LAZY)
    val variants: MutableSet<RouteVariant> = mutableSetOf()

    constructor() : this(
        id = RouteId("placeholder"),
        agency = Agency(),
        gtfsRouteId = "",
        longName = "",
        routeType = RouteType.BUS,
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
        if (other !is Route) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Route(id=$id, gtfsRouteId='$gtfsRouteId', shortName='$shortName', longName='$longName', routeType=$routeType, active=$active)"
}

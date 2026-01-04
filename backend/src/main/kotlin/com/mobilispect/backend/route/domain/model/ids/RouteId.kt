package com.mobilispect.backend.route.domain.model.ids

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId

/**
 * Value class for Route identifiers using Transitland Onestop ID format. Ensures type safety and
 * prevents ID mixups across domain boundaries.
 *
 * Format: r-{geohash}-{route_identifier} Example: r-f25d-100 (Route 100 in Montreal area)
 *
 * The geohash is inherited from the agency's Onestop ID, providing geographic context while
 * maintaining global uniqueness.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Now using @JvmInline for zero-overhead type safety in the domain layer. Data layer uses plain
 * String IDs for Hibernate 7 compatibility.
 */
class RouteId {
  var value: String

  constructor(value: String) {
    this.value = value
  }

  constructor(agencyId: AgencyId, routeID: FeedLocalRouteId) : this("${agencyId}/${routeID}")

  override fun toString(): String = value

  fun feedLocalId() = FeedLocalRouteId(value.substringAfterLast("/"))
}

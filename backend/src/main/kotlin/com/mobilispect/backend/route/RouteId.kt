package com.mobilispect.backend.route

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId

private const val delimiter = "-"

/**
 * Value class for Route identifiers using Transitland Onestop ID format. Ensures type safety and
 * prevents ID mixups across domain boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 */
data class RouteId(val value: String) {
  init {
    require(value.isNotBlank()) { "Route ID cannot be blank" }
  }

  constructor(
    agencyId: AgencyId,
    routeID: FeedLocalRouteId,
  ) : this("r-${agencyId}${delimiter}${routeID}")

  override fun toString(): String = value

  fun feedLocalId() = FeedLocalRouteId(value.substringAfterLast(delimiter))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RouteId) return false
    return value == other.value
  }

  override fun hashCode(): Int = value.hashCode()
}

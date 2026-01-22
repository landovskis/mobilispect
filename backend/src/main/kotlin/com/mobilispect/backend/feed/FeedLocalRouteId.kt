package com.mobilispect.backend.feed.api.ids

/**
 * Value class for GTFS route identifiers.
 *
 * Distinguishes GTFS feed route IDs from Transitland Onestop route IDs, preventing accidental
 * mixing of identifier types.
 *
 * GTFS route IDs are defined in routes.txt and may contain any characters. Transitland Onestop
 * route IDs follow the format: r-{feedId}-{gtfsRouteId}
 */
@JvmInline
value class FeedLocalRouteId(val value: String) {
  init {
    require(value.isNotBlank()) { "GTFS Route ID cannot be blank" }
  }

  override fun toString(): String = value

  companion object {
    /** Creates a GTFSRouteId from a nullable string. Returns null if the value is null or blank. */
    fun from(value: String?): FeedLocalRouteId? =
      value?.takeIf { it.isNotBlank() }?.let { FeedLocalRouteId(it) }
  }
}

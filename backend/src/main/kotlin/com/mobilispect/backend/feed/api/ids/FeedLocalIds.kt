package com.mobilispect.backend.feed.api.ids

/**
 * Type aliases for GTFS IDs that are local to a specific feed.
 *
 * These type aliases clarify that the ID comes directly from a GTFS feed and has not been
 * transformed into a Transitland Onestop format.
 *
 * Using the underlying GTFS ID types ensures type safety while providing semantic clarity about the
 * ID's origin.
 */

/** A GTFS agency ID that is local to a specific feed. Maps to agency_id in agency.txt. */
typealias FeedLocalAgencyId = GTFSAgencyId

/** A GTFS route ID that is local to a specific feed. Maps to route_id in routes.txt. */
typealias FeedLocalRouteId = GTFSRouteId

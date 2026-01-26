package com.mobilispect.backend.feed.api.ids

/**
 * Type-safe identifiers for GTFS IDs that are local to a specific feed.
 *
 * This file documents the feed-local ID types used in the codebase:
 * - [FeedLocalAgencyId]: A GTFS agency ID that is local to a specific feed. Maps to agency_id in
 *   agency.txt.
 * - [FeedLocalRouteId]: A GTFS route ID that is local to a specific feed. Maps to route_id in
 *   routes.txt.
 * - [GTFSStopId]: A GTFS stop ID. Maps to stop_id in stops.txt.
 * - [GTFSTripId]: A GTFS trip ID. Maps to trip_id in trips.txt.
 *
 * These value classes provide compile-time type safety to distinguish feed-local IDs from
 * Transitland Onestop IDs.
 */

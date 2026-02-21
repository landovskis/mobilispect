package com.mobilispect.backend.vehicle

import org.springframework.modulith.ApplicationModule

/**
 * Vehicle Realtime Ingestion Module.
 *
 * Responsible for polling and processing GTFS-RT feeds (vehicle positions, trip updates, service
 * alerts) from transit agencies. Implements parallel fetching with deduplication and resilience
 * patterns per ADR 0011.
 *
 * ## Dependencies
 * - `feed`: For querying active feeds with realtime URLs and triggering on-demand discovery
 *
 * ## Public API
 * - None (internal processing module)
 *
 * ## Events Published
 * - `GtfsRtIngestionCompleted`: Published after each ingestion cycle
 *
 * ## Database Tables Owned
 * - `gtfsrt_feed_state`: Per-feed deduplication state (Redis)
 * - `vehicle_positions`: Realtime vehicle position data
 * - `trip_updates`: Realtime trip update data
 * - `service_alerts`: Realtime service alert data
 */
@ApplicationModule(displayName = "Vehicle Realtime", allowedDependencies = ["feed"])
class VehicleModule

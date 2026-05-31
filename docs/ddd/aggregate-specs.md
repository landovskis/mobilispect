# Aggregate Specifications

Each aggregate defines a consistency boundary. Invariants listed here must be enforced in code.

---

## Feed

**Root:** `FeedId`
**Table:** `feeds`

**Invariants:**
- Has a valid `gtfs_static_url`
- `last_ingested_at` is null until the first successful ingest

**Lifecycle:** Upserted from `config.toml` at startup by the worker. Ingest metadata
(`last_ingested_at`, `feed_hash`, `feed_version`) updated after each successful static ingest.
API keys are never persisted to the database.

---

## Agency

**Root:** `AgencyId` (Transitland operator Onestop ID)
**Table:** `agencies`

**Invariants:**
- Has at least one route (enforced at query time; an agency with no routes produces no metrics)
- `onestop_id` is a valid Transitland operator Onestop ID (`o-` prefix)

**Lifecycle:** Ingested from GTFS `agency.txt` during static feed ingest. Resolved to a
Transitland operator Onestop ID via the Transitland API. Upserted on each feed ingest.
If no Transitland match exists, the agency and all its dependent routes are skipped.

---

## Route

**Root:** `RouteId` (Transitland route Onestop ID)
**Table:** `routes`
**Contains:** One or more variants (each a unique ordered stop sequence in `route_variants` and `route_variant_stops`)

**Invariants:**
- Belongs to exactly one `AgencyId`
- Has at least one variant
- `onestop_id` is a valid Transitland route Onestop ID (`r-` prefix)

**Lifecycle:** Ingested from GTFS `routes.txt`. Resolved to a Transitland route Onestop ID.
Canonical route attributes (name, type) are upserted. Feed-local variants are upserted
per feed ingest and may change between feed versions.

---

## Trip

**Root:** `TripId`
**Table:** `trips`
**Belongs to:** One `VariantId` (mandatory), one `ServiceId`

**Invariants:**
- `variant_id` is NOT NULL — every trip belongs to a variant
- Direction is derivable via `variant_id → route_variants.direction_id`; not stored on trip
- Route is derivable via `variant_id → route_variants.route_id`; not stored on trip
- Scheduled stop times are monotonically increasing (stored in `scheduled_stops`)
- Belongs to exactly one service calendar entry

**Lifecycle:** Upserted on each static feed ingest. Delay observations appended to
`stop_time_events` during GTFS-RT polling. `TripResult` written to `trip_results` after
each service day is processed.

---

## RouteDailyStats (computed aggregate)

**Root:** `(FeedId, RouteId, Date, VariantId)`
**Table:** `route_daily_stats`
**Derived from:** All rows in `trip_results` for a given feed, route, service date, and variant

**Fields:**
- `on_time_stops` — count of stops observed within the threshold window
- `total_stops` — total scheduled stops across all trips of this variant on this date
- `skipped_stops` — stops explicitly skipped by the operator (`schedule_relationship=SKIPPED`)
- `trips_run` — number of trips with observed stop-time data
- `trips_total` — total scheduled trips for this variant on this date (excluding `service_exceptions` with `exception_type=2`)
- `avg_delay_secs`, `max_delay_secs` — delay aggregates in seconds
- `actual_speed_mps`, `avg_dwell_secs` — speed and dwell aggregates

**Invariants:**
- `on_time_stops` ≤ `total_stops`
- `skipped_stops` ≤ `total_stops`
- `trips_run` ≤ `trips_total`
- Immutable once written — recomputed by re-running the worker, not by mutation

**Derived query:** on-time % = `on_time_stops::float / total_stops * 100`
**Derived query:** route-level rollup = aggregate over `variant_id` at query time

**Lifecycle:** Written by the worker after processing a service day. Insert-only. Not user-writable.

---

## Notes

- `Vehicle` is referenced in GTFS-RT (`vehicle_positions`) but is not a domain aggregate — vehicles are ephemeral identifiers, not persisted entities with invariants.
- `Stop` and `Station` are reference data (canonical entities keyed by Transitland Onestop ID), not aggregates.
- `Service` and `ServiceException` are reference data owned by the Schedule context.
- `TripResult` is a pure computation value (Rust struct from `classify_trip_delays`) persisted to `trip_results`. Not an aggregate root — write-once, no invariants of its own.
- `route_daily_stats` replaces `route_daily`, `route_speed_daily`, and `route_speed_day_type`. Day-type breakdowns (weekday/Saturday/Sunday) are derived at query time by grouping on `calendar.day_of_week`.

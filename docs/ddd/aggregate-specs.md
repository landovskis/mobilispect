# Aggregate Specifications

Each aggregate defines a consistency boundary. Invariants listed here must be enforced in code.

---

## Agency

**Root:** `AgencyId`
**Table:** none — Agency has no database table. Agency identity is configuration, not database state.

**Invariants:**
- Has at least one route (enforced at query time; an agency with no routes produces no metrics)

**Lifecycle:** Loaded from `config.toml` at startup. Agency records are config-driven and never written to the database directly.

---

## Route

**Root:** `RouteId`
**Table:** `routes`
**Contains:** One or more variants (each variant is a unique ordered stop sequence stored in `route_variants` and `route_variant_stops`)

**Invariants:**
- Belongs to exactly one `AgencyId`
- Has at least one variant

**Lifecycle:** Upserted on each static feed ingest. A route's variants may change between feed versions.

---

## Trip

**Root:** `TripId`
**Table:** `trips`
**Belongs to:** One `RouteId`, one service calendar entry, optionally one `VariantId`

**Invariants:**
- `direction_id` is 0 (outbound) or 1 (inbound)
- Scheduled stop times are monotonically increasing (stored in `scheduled_stops`)
- Belongs to exactly one route and exactly one service calendar

**Lifecycle:** Upserted on each static feed ingest. Delay observations are appended to `stop_time_events` during GTFS-RT polling. `TripResult` (computed in memory from `stop_time_events`) is written to `trip_results` after each service day is processed.

---

## RouteDailyMetrics (computed aggregate)

**Root:** `(AgencyId, RouteId, Date)`
**Table:** `route_daily`
**Derived from:** All rows in `trip_results` for a given agency, route, and service date

**Fields:**
- `on_time_pct` — percentage of trips that were on time (all stops within threshold window)
- `avg_delay_secs` — average delay in seconds across completed trips
- `trips_run` — number of trips with observed stop-time data
- `trips_total` — total scheduled trips for the route

**Invariants:**
- `on_time_pct` ∈ [0, 100]
- `avg_delay_secs` is in seconds
- Immutable once written — recomputed by re-running the worker, not by mutation

**Lifecycle:** Written by the worker after processing a service day. Not user-writable.

---

## Notes

- `Vehicle` is referenced in GTFS-RT (`vehicle_positions`) but is not a domain aggregate — vehicles are ephemeral identifiers for real-time observations, not persisted entities with invariants.
- `Stop` and `Service` are referenced by aggregates but are themselves reference data, not aggregates with invariants.
- `TripResult` is a pure computation value (a Rust struct returned by `classify_trip_delays`) that is then persisted to the `trip_results` table. It is not an aggregate root — it does not enforce invariants and has no lifecycle of its own beyond write-once semantics.
- `route_daily` does not store `max_delay_secs` at the route level; that field exists only in `trip_results` at the individual trip level.

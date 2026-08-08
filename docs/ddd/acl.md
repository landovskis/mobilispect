# Anti-Corruption Layer

The worker crate (`mobilispect-worker`) is the anti-corruption boundary between external GTFS models and the Mobilispect domain.

## Rule

No file in `crates/core/` or `crates/server/` may import:
- `gtfs_structures::*`
- Prost-generated protobuf types (from `worker/src/proto/`)
- Any raw GTFS model

These are exclusively worker concerns.

## Static Feed Translation

Translation happens in `crates/worker/src/gtfs/static_feed.rs`. The entry point receives an `AgencyId` derived from config:

```rust
AgencyId::from(agency.id)   // from AgencyConfig, not from a GTFS field
```

Inside the load functions, GTFS strings are extracted as plain `String` values and written directly to the DB via dynamic `sqlx::query` calls (not `query!`). The domain ID newtypes (`RouteId`, `TripId`, `StopId`, `ServiceId`) are not used during bulk insertion — the binding layer handles strings directly.

Notable translations:

- **Route type** — `gtfs_structures::RouteType` → `i64` via `route_type_to_int()`
- **Direction** — `gtfs_structures::DirectionType` → `i64` via `direction_to_int()` (0 = Outbound, 1 = Inbound)
- **VariantId** — deterministic SHA-256 hash of the ordered stop-ID sequence, encoded as a 16-byte hex string via `variant_id_for(&[String])`. This is derived from stop patterns, not from any GTFS `shape_id` field.
- **Times** — GTFS seconds-since-midnight (`Option<u32>`) → `"HH:MM:SS"` string via `format_gtfs_time()`

No `gtfs_structures` types appear in `crates/core/` or `crates/server/`.

## Real-Time Feed Translation

GTFS-RT protobuf (decoded via prost from `worker/proto/gtfs-realtime.proto`) is handled in `crates/worker/src/gtfs/realtime.rs`. The protobuf module is compiled by `build.rs` and included locally:

```rust
pub mod proto {
    include!(concat!(env!("OUT_DIR"), "/transit_realtime.rs"));
}
```

Fields are extracted as plain Rust primitives before any DB write:

- `vp.vehicle.id` → `Option<String>` (stored as `vehicle_id`, not `VehicleId`)
- `tu.trip.trip_id` → `&String` (stored as `trip_id`, not `TripId`)
- `stu.stop_id` → `String` (stored as `stop_id`, not `StopId`)
- `stu.arrival.delay` / `stu.departure.delay` → `Option<i64>` seconds — no unit conversion
- `stu.arrival.time` / `stu.departure.time` → `Option<i64>` Unix timestamps — no unit conversion
- `pos.latitude` / `pos.longitude` → `f64`

No protobuf types leak past `realtime.rs`. The domain `AgencyId` is passed in from the caller; it is the only domain newtype used inside this file.

## Transitland API

Translation happens in `crates/core/src/transitland/mod.rs` — a synchronous,
on-demand external lookup (resolving IDs during import), not a batch/background
job, so it lives in `crates/core` rather than `crates/worker` (unlike GTFS
static/real-time translation below, which IS a batch/background job and does
live in `crates/worker`). The Transitland REST API is called during static
feed ingest to resolve GTFS-local IDs to canonical Onestop IDs.

**Rule:** No `reqwest` calls to Transitland may appear in `crates/core/` or `crates/server/`.

Translations:
- `gtfs_agency_id: String` → `AgencyId` (operator Onestop ID, `o-` prefix)
- `gtfs_route_id: String` → `RouteId` (route Onestop ID, `r-` prefix)
- `gtfs_stop_id: String` → `StopId` or `StationId` (stop Onestop ID, `s-` prefix; split by `location_type`)

If no Transitland match exists for an entity, it is skipped. All dependent downstream
entities (routes of unresolved agencies, trips of unresolved routes, etc.) are also skipped.

Junction tables (`feed_agency_ids`, `feed_route_ids`, `feed_stop_ids`) are the persisted
translation boundary — they map feed-local GTFS IDs to canonical Onestop IDs.

## Overpass API (OSM Import)

Translation happens in `crates/core/src/osm/mod.rs`. The Overpass API is called
on-demand when an analyst searches for or imports OSM street geometry — a
synchronous, user-triggered lookup, not a batch ingestion job, so (like
Transitland above) it lives in `crates/core` rather than `crates/worker`.

**Rule:** No `reqwest` calls to Overpass may appear in `crates/server` — route
handlers call into `mobilispect_core::osm::OverpassClient`, never `reqwest`
directly.

Translations:
- Overpass's raw JSON `elements` array → `OsmWay { osm_way_id: i64, points:
  Vec<corridor_design::geometry::RawPoint>, tags: HashMap<String, String> }`.
  Node ids and lat/lon coordinates are Overpass's `nodes`/`geometry` parallel
  arrays, zipped by index into `RawPoint { coordinate, osm_node_id }` —
  reusing the existing corridor-geometry type directly rather than a parallel
  one.
- No translation to domain ID newtypes happens here — OSM way/node ids stay
  plain `i64` (`osm_way_id`, `osm_node_id`) all the way through persistence,
  matching the existing `cross_sections.osm_way_id`/`osm_node_id` columns'
  own convention (see
  `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`).

---

## Adding a New External Source

If a future feature adds a new external data source (e.g., a cycling network feed):
1. All translation from external model → domain types happens in the worker crate
2. Document the translation mapping in this file under a new section
3. No external types may appear in `core/` or `server/`

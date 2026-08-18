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

Translation happens in `crates/core/src/transitland/mod.rs`. Unlike GTFS
static/real-time translation below (which is exclusively a
`crates/worker` batch/background concern), the Transitland client is shared
by both `crates/worker` (constructed in `crates/worker/src/main.rs` and used
by the batch static-feed ingest job in
`crates/worker/src/feed_ingestion/static_feed.rs`) and `crates/server`
(called on-demand from `crates/server/src/web/handlers.rs`). Because both
crates need it, the client lives in `crates/core` rather than being
duplicated — the same "shared by more than one crate" reasoning that puts
`crates/core/src/db/` in `crates/core`. The Transitland REST API is called
during static feed ingest, and on demand from web handlers, to resolve
GTFS-local IDs to canonical Onestop IDs.

**Rule:** No `reqwest` calls to Transitland may appear in `crates/server/` —
handlers call into `mobilispect_core::transitland::TransitlandClient`, never
`reqwest` directly. (`crates/core/src/transitland/` itself is exempt: it *is*
the client.)

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

## StatsCan / Geofabrik (Region OSM Data Caching)

Translation happens in `crates/worker/src/region_provisioning/`. Unlike
Transitland/Overpass above (shared by `crates/core`, called from both worker
and server), nothing in `crates/server` needs this data yet, so it follows
`feed_ingestion`'s precedent and lives in `crates/worker` as a batch/background
job run once per region at worker startup, not `crates/core`.

**Rule:** No `reqwest`/`shapefile`/`zip`/`dbase` calls related to StatsCan or
Geofabrik may appear outside `crates/worker/src/region_provisioning/`.

Translations:
- StatsCan's raw shapefile+DBF records → `CmaCaRecord { name: String,
  points_lambert: Vec<(f64, f64)> }` (`statcan.rs`) — only the `CMANAME` field
  and raw Lambert-projection (EPSG:3347) point coordinates cross the
  boundary; `CMAUID`, `CMATYPE`, `CMAPUID`, `PRUID` are read by nothing here.
- Geofabrik's per-province `.osm.pbf` files never get parsed into any Rust
  type at all — they're clipped/merged by shelling out to the `osmium` CLI
  (`osm_extract.rs`) and only the resulting cached file path crosses into the
  rest of the app.
- The only domain type either translation produces is `BoundingBox` (already
  existing, `mobilispect_core::remix::BoundingBox`) — reprojected to WGS84 and
  written into `regions.min_lat/min_lon/max_lat/max_lon`.

---

## Adding a New External Source

If a future feature adds a new external data source (e.g., a cycling network feed):
1. All translation from external model → domain types happens in the worker crate
2. Document the translation mapping in this file under a new section
3. No external types may appear in `core/` or `server/`

# Bounded Context Canvas

Five contexts, aligned to the Cargo crate structure.

---

## Feed Ingestion

**Crate:** `mobilispect-worker`
**Purpose:** Download, parse, and persist GTFS data into the domain model.

**Owns:**
- Config-driven upsert of `regions`, `networks`, `feeds` on startup
- GTFS static feed download, ZIP extraction, CSV parsing
- Transitland API resolution: GTFS IDs → Onestop IDs (agencies, routes, stops)
- Upsert of canonical entities (`agencies`, `routes`, `stops`, `stations`) and junction tables
- Upsert logic for all schedule and real-time data
- GTFS-RT protobuf polling and decoding

**Commands:**
- `IngestStaticFeed(feed_id: FeedId, url: &str)` — downloads and upserts a full GTFS static archive
- `PollRealtimeFeed(feed_id: FeedId, url: &str)` — fetches and processes one GTFS-RT snapshot

**External dependencies:**
- GTFS provider (upstream, opaque) — all GTFS types translated at boundary
- Transitland API (upstream) — resolves canonical Onestop IDs; all Transitland types translated in `crates/worker/src/transitland/`

**Also writes:** `stop_time_events`, `vehicle_positions` tables (real-time observations from GTFS-RT).

**Policy:** No GTFS-native types (`gtfs_structures::*`, prost-generated protobuf types) may leak past this context. No Transitland API calls outside `crates/worker/src/transitland/`.

---

## Schedule

**Crate:** `mobilispect-core` (static side: routes, trips, stops, variants, service calendars)
**Purpose:** The planned timetable as stored in Postgres. Source of truth for what was scheduled.

**Owns:**
- `routes`, `trips`, `stops`, `stations`, `scheduled_stops`, `route_variants`, `route_variant_stops`, `services`, `service_exceptions` tables
- Query functions that return planned data

**Consumed by:** Performance (read-only)

**Policy:** Schedule data is never mutated by Performance or Reporting. Only Feed Ingestion writes here.

---

## Performance

**Crate:** `mobilispect-core` (`metrics/`, `speed/`, `frequency/`)
**Purpose:** Compute and store what actually happened vs. what was planned.

**Owns:**
- Delay computation, on-time classification
- Speed computation (scheduled and actual)
- Headway computation
- `route_daily_stats`, `trip_results`, `route_speed`, `route_speed_hourly` tables
- Query functions returning computed metrics

**Depends on:** Schedule (reads planned data); real-time delay observations written by Feed Ingestion.

**Produces:** Route Daily records, speed cards, schedule cards.

**Policy:** All computation is pure (no I/O). Query functions are the only public API — handlers never access Performance tables directly.

---

## Reporting

**Crate:** `mobilispect-server`
**Purpose:** Present metrics via the web UI.

**Owns:**
- HTTP handlers (`web/handlers.rs`)
- Askama templates (`templates/`)
- UI cards and page layout

**Depends on:** Performance (calls query functions only — never raw `sqlx` in handlers).

**Policy:** No business logic in handlers. No raw `sqlx` in handlers. Handlers extract params, call slice API, render template.

---

## Corridor Design

**Crate:** `mobilispect-core` (`corridor_design/`, `remix/`); `mobilispect-server` (`web/corridor_design.rs`, `web/remix_api.rs`, `web/corridor_import.rs`); `corridor_builder_web` (Yew WASM shell, served at `/builder`)
**Purpose:** Analysts define and edit street corridors for proposed changes, scoped to remixes within a metro region.

**Owns:**
- `regions` table, extended with a bounding box (`min_lat`/`min_lon`/`max_lat`/`max_lon`) used to frame the region map
- `remixes`, `corridors`, `cross_sections` tables
- Corridor geometry import (GIS/OSM) and manual tracing; cross-section ordering, labelling, and edit operations
- Corridor Builder JSON API (regions, remixes, remix corridors) and the Yew WASM create/open-remix UI

**Aggregates:** `Region` (extended with a bounding box), `Remix`, `Corridor`, `CrossSection`

**Relationships:** A `Remix` belongs to one `Region`. A `Corridor` belongs to one `Remix`.

**Policy:** Edit and highlight-eligibility logic (`corridor_design::edit`, `remix::highlight`) is pure — no I/O. The repository layer persists and reads only; it performs no validation.

# Bounded Context Canvas

Four contexts, aligned to the Cargo crate structure.

---

## Feed Ingestion

**Crate:** `mobilispect-worker`
**Purpose:** Download, parse, and persist GTFS data into the domain model.

**Owns:**
- GTFS static feed download, ZIP extraction, CSV parsing
- GTFS-RT protobuf polling and decoding
- Upsert logic for all schedule and real-time data

**Commands:**
- `IngestStaticFeed(agency_id: AgencyId, url: &str)` — downloads and upserts a full GTFS static archive
- `PollRealtimeFeed(agency_id: AgencyId, url: &str)` — fetches and processes one GTFS-RT snapshot

**External dependency:** GTFS provider (upstream, opaque). Treated as an untrusted external model — all GTFS types are translated at the boundary.

**Policy:** No GTFS-native types (`gtfs_structures::*`, prost-generated protobuf types) may leak past this context.

---

## Schedule

**Crate:** `mobilispect-core` (static side: routes, trips, stops, variants, service calendars)
**Purpose:** The planned timetable as stored in Postgres. Source of truth for what was scheduled.

**Owns:**
- `routes`, `trips`, `stops`, `scheduled_stops`, `route_variants`, `route_variant_stops`, `calendar` tables
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
- `route_daily`, `trip_results`, `route_speed`, `route_speed_daily`, `route_speed_hourly`, `route_speed_day_type` tables
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

# DDD Artifacts + Workflow Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create five DDD artifact documents in `docs/ddd/` and a `.claude/rules/ddd.md` rule that anchors every future feature spec to Mobilispect's domain model.

**Architecture:** Pure documentation — no code changes. Six markdown files written to two locations. The rule file is loaded automatically into every Claude Code session alongside `testing.md` and `superpowers.md`.

**Tech Stack:** Markdown. No build steps, no tests, no migrations.

**Spec:** `docs/superpowers/specs/2026-05-23-ddd-artifacts-design.md`

---

### Task 1: Create `docs/ddd/ubiquitous-language.md`

**Files:**
- Create: `docs/ddd/ubiquitous-language.md`

- [ ] **Step 1: Create `docs/ddd/` and write the glossary**

Create `docs/ddd/ubiquitous-language.md` with this exact content:

```markdown
# Ubiquitous Language

Terms used consistently across code, specs, and conversations. When a new feature introduces a term not listed here, add it before writing implementation code.

| Term | Rust Type | Definition |
|------|-----------|------------|
| Agency | `AgencyId` | A transit operating company (e.g. STM). Top-level grouping for all routes. |
| Route | `RouteId` | A named service corridor. Has one or more variants. |
| Variant | `VariantId` | A specific stop-sequence pattern within a route. Different variants of the same route may serve different stops. |
| Trip | `TripId` | A single scheduled run of a route on a specific service day. |
| Stop | `StopId` | A physical or logical transit stop where passengers board or alight. |
| Service | `ServiceId` | A GTFS service calendar that defines which calendar days a trip operates. |
| Vehicle | `VehicleId` | A physical vehicle serving a trip in real time. |
| Direction | `DirectionId` | Inbound (0) or outbound (1) for a route. |
| Delay | `i64` (seconds) | Actual arrival minus scheduled arrival. Positive = late, negative = early. |
| Headway | seconds | Time gap between consecutive vehicles on the same route at a stop. |
| On-time Rate | `f64` (0.0–1.0) | Fraction of trips arriving within the configured early/late thresholds. |
| Speed | km/h | Average speed of a vehicle over a route segment or full run. |
| Dwell Time | seconds | Time a vehicle spends stopped at a station. |
| Route Daily | `(RouteId, Date)` | Aggregated performance metrics for a route on a specific calendar date. Computed from all trip results for that day. |
| Trip Result | — | The computed outcome of a single trip: on-time classification, avg delay, max delay. |
| Schedule Card | — | UI component displaying per-day-type headway statistics (Mon–Fri, Sat, Sun) for a route. |
| GTFS Static Feed | — | A ZIP archive of CSVs describing the planned timetable. Published by transit agencies. |
| GTFS-RT Feed | — | A protobuf stream of real-time vehicle positions and delay updates. |
| Early Threshold | `i64` (seconds) | How many seconds early a trip can arrive and still be considered on-time. Configured per agency. |
| Late Threshold | `i64` (seconds) | How many seconds late a trip can arrive and still be considered on-time. Configured per agency. |
```

- [ ] **Step 2: Commit**

```bash
git add docs/ddd/ubiquitous-language.md
git commit -m "docs(ddd): add ubiquitous language glossary"
```

---

### Task 2: Create `docs/ddd/bounded-context-canvas.md`

**Files:**
- Create: `docs/ddd/bounded-context-canvas.md`

- [ ] **Step 1: Write the bounded context canvas**

Create `docs/ddd/bounded-context-canvas.md` with this exact content:

```markdown
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
- `agencies`, `routes`, `variants`, `trips`, `stops`, `stop_times`, `service_calendars` tables
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
- `route_daily`, `trip_results`, `speed_observations` tables
- Query functions returning computed metrics

**Depends on:** Schedule (reads planned data); real-time delay observations written by Feed Ingestion.

**Produces:** Route Daily records, speed cards, schedule cards.

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
```

- [ ] **Step 2: Commit**

```bash
git add docs/ddd/bounded-context-canvas.md
git commit -m "docs(ddd): add bounded context canvas"
```

---

### Task 3: Create `docs/ddd/context-map.md`

**Files:**
- Create: `docs/ddd/context-map.md`

- [ ] **Step 1: Write the context map**

Create `docs/ddd/context-map.md` with this exact content:

````markdown
# Context Map

How the four bounded contexts relate to each other and to external systems.

```
GTFS Provider ──(ACL)──► Feed Ingestion ──(Conformist)──► Schedule
                                                               │
                                                     (Shared Kernel: ID types)
                                                               │
                                                               ▼
                                                          Performance ──(Customer/Supplier)──► Reporting
```

## Relationships

### GTFS Provider → Feed Ingestion: ACL (Anti-Corruption Layer)

GTFS is an external standard with its own model (raw strings, CSV conventions, protobuf schema). Feed Ingestion translates everything at the boundary — no GTFS-native types enter the domain. See `acl.md` for the translation rules.

### Feed Ingestion → Schedule: Conformist (schema level)

The database schema for schedule data mirrors GTFS structure intentionally (routes, trips, stops, stop_times). We accept this constraint rather than fighting it. The benefit: GTFS → DB upserts are straightforward. The cost: the schema is coupled to the GTFS spec version.

### Schedule → Performance: Shared Kernel

`RouteId`, `TripId`, `StopId`, and other ID types are defined once in `crates/core/src/ids.rs` and used across both Schedule and Performance. Neither context owns these IDs exclusively — they are the shared kernel.

### Performance → Reporting: Customer/Supplier

Reporting is the downstream consumer. Performance is the upstream supplier. Performance exposes query functions (in `metrics/`, `speed/`, `frequency/`) that Reporting calls. Reporting has no write access to Performance's tables.
````

- [ ] **Step 2: Commit**

```bash
git add docs/ddd/context-map.md
git commit -m "docs(ddd): add context map"
```

---

### Task 4: Create `docs/ddd/aggregate-specs.md`

**Files:**
- Create: `docs/ddd/aggregate-specs.md`

- [ ] **Step 1: Write the aggregate specifications**

Create `docs/ddd/aggregate-specs.md` with this exact content:

```markdown
# Aggregate Specifications

Each aggregate defines a consistency boundary. Invariants listed here must be enforced in code.

---

## Agency

**Root:** `AgencyId`
**Table:** `agencies`

**Invariants:**
- Has at least one route (enforced at query time; an agency with no routes produces no metrics)

**Lifecycle:** Created on static feed ingest. Never explicitly deleted — data retention policy handles old records.

---

## Route

**Root:** `RouteId`
**Table:** `routes`
**Contains:** One or more `Variant`s (each with an ordered stop list in `stop_times`)

**Invariants:**
- Belongs to exactly one `AgencyId`
- Has at least one variant

**Lifecycle:** Upserted on each static feed ingest. A route's variants may change between feed versions.

---

## Trip

**Root:** `TripId`
**Table:** `trips`
**Belongs to:** One `RouteId`, one `ServiceId`

**Invariants:**
- `direction_id` is 0 (outbound) or 1 (inbound)
- Scheduled stop times are monotonically increasing
- Belongs to exactly one route and exactly one service calendar

**Lifecycle:** Upserted on each static feed ingest. Delay observations are appended during GTFS-RT polling.

---

## RouteDailyMetrics (computed aggregate)

**Root:** `(RouteId, Date)`
**Table:** `route_daily`
**Derived from:** All `TripResult`s for a given route on a given date

**Invariants:**
- `on_time_rate` ∈ [0.0, 1.0]
- `avg_delay_secs` and `max_delay_secs` are in seconds
- Immutable once written — recomputed by re-running the worker, not by mutation

**Lifecycle:** Written by the worker after processing a service day. Not user-writable.

---

## Notes

- `Vehicle` is referenced in GTFS-RT but is not a domain aggregate — vehicles are ephemeral identifiers for real-time observations, not persisted entities with invariants.
- `Stop` and `Service` are referenced by aggregates but are themselves reference data, not aggregates with invariants.
```

- [ ] **Step 2: Commit**

```bash
git add docs/ddd/aggregate-specs.md
git commit -m "docs(ddd): add aggregate specifications"
```

---

### Task 5: Create `docs/ddd/acl.md`

**Files:**
- Create: `docs/ddd/acl.md`

- [ ] **Step 1: Write the ACL document**

Create `docs/ddd/acl.md` with this exact content:

````markdown
# Anti-Corruption Layer

The worker crate (`mobilispect-worker`) is the anti-corruption boundary between external GTFS models and the Mobilispect domain.

## Rule

No file in `crates/core/` or `crates/server/` may import:
- `gtfs_structures::*`
- Prost-generated protobuf types (from `worker/src/proto/`)
- Any raw GTFS model

These are exclusively worker concerns.

## Static Feed Translation

All GTFS raw strings become typed IDs before any domain logic or DB write:

```rust
AgencyId::from(gtfs_agency.id.clone())
RouteId::from(gtfs_route.id.clone())
TripId::from(gtfs_trip.id.clone())
StopId::from(gtfs_stop.id.clone())
ServiceId::from(gtfs_trip.service_id.clone())
VariantId::from(gtfs_shape.shape_id.clone())
DirectionId::from(gtfs_trip.direction_id as i64)
```

Translation happens in `crates/worker/src/gtfs/static_feed.rs` before any `sqlx` insert.

## Real-Time Feed Translation

GTFS-RT protobuf (decoded via prost from `worker/proto/gtfs-realtime.proto`) is translated in `crates/worker/src/gtfs/realtime.rs`:

- `VehiclePosition.vehicle.id` → `VehicleId::from(...)`
- `TripUpdate.trip.trip_id` → `TripId::from(...)`
- `StopTimeUpdate.stop_id` → `StopId::from(...)`
- Delay values remain `i64` seconds — no unit conversion

No protobuf types leak past `realtime.rs`.

## Adding a New External Source

If a future feature adds a new external data source (e.g., a cycling network feed):
1. All translation from external model → domain types happens in the worker crate
2. Document the translation mapping in this file under a new section
3. No external types may appear in `core/` or `server/`
````

- [ ] **Step 2: Commit**

```bash
git add docs/ddd/acl.md
git commit -m "docs(ddd): add anti-corruption layer specification"
```

---

### Task 6: Create `.claude/rules/ddd.md`

**Files:**
- Create: `.claude/rules/ddd.md`

- [ ] **Step 1: Write the DDD rule file**

Create `.claude/rules/ddd.md` with this exact content:

````markdown
# DDD Artifacts

DDD artifacts for the Mobilispect domain live in `docs/ddd/`. They are the authoritative reference for domain terms, bounded contexts, aggregates, and ACL boundaries.

## Before Writing Any Feature Spec

1. Read `docs/ddd/bounded-context-canvas.md` — identify which bounded context(s) the feature touches.
2. Read `docs/ddd/aggregate-specs.md` — identify which aggregates are involved.
3. Every feature spec must include a **Domain Context** section:

```markdown
## Domain Context

- **Bounded context(s):** [e.g. Performance, Reporting]
- **Aggregates touched:** [e.g. RouteDailyMetrics, Trip]
- **New ubiquitous language terms:** [list any new terms, or "none"]
```

## Keeping Artifacts Current

- **New domain term** introduced by a feature → add it to `docs/ddd/ubiquitous-language.md` in the same commit as the spec.
- **New or modified aggregate invariant** → update `docs/ddd/aggregate-specs.md` before writing implementation code.
- **New external data source** → document its translation boundary in `docs/ddd/acl.md`.

## ACL Boundary Rule

No file in `crates/core/` or `crates/server/` may import `gtfs_structures::*` or prost-generated protobuf types. Those are exclusively `mobilispect-worker` concerns. See `docs/ddd/acl.md` for the full mapping.
````

- [ ] **Step 2: Commit**

```bash
git add .claude/rules/ddd.md
git commit -m "chore(claude): add DDD artifacts rule for spec workflow"
```

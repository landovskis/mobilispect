# DDD Artifacts + Workflow Integration

**Date:** 2026-05-23
**Status:** Approved

## Problem

Mobilispect uses a spec-driven development workflow (brainstorming → spec → plan → implementation). Without explicit DDD artifacts, each new spec risks AI improvising domain invariants, inventing type names, or conflating models across bounded contexts. The five DDD artifacts described in "Your AI SDD Specification Isn't Incomplete — It Just Lacks DDD" give every spec a shared domain foundation to anchor against.

## Goal

1. Create five DDD artifact documents in `docs/ddd/` that capture Mobilispect's transit domain.
2. Add `.claude/rules/ddd.md` so every session loads the requirement to consult and maintain these artifacts during feature work.

## File Structure

```
docs/ddd/
  ubiquitous-language.md
  bounded-context-canvas.md
  context-map.md
  aggregate-specs.md
  acl.md

.claude/rules/ddd.md
```

No changes to `docs/superpowers/specs/` or `docs/superpowers/plans/`.

## Artifact Content

### ubiquitous-language.md

A glossary mapping every domain term to its Rust type and plain-language definition. Entries include:

| Term | Type | Definition |
|------|------|------------|
| Agency | `AgencyId` | A transit operating company (e.g. STM). Top-level grouping for all routes. |
| Route | `RouteId` | A named service corridor. Has one or more variants (stop sequences). |
| Variant | `VariantId` | A specific stop sequence pattern within a route. |
| Trip | `TripId` | A single scheduled run of a route on a specific service day. |
| Stop | `StopId` | A physical or logical transit stop where passengers board/alight. |
| Service | `ServiceId` | A GTFS service calendar defining which days a trip operates. |
| Vehicle | `VehicleId` | A physical vehicle serving a trip in real time. |
| Direction | `DirectionId` | Inbound (0) or outbound (1) for a route. |
| Delay | `i64` (seconds) | Actual arrival minus scheduled arrival. Positive = late. |
| Headway | seconds | Time gap between consecutive vehicles on the same route at a stop. |
| On-time Rate | `f64` (0.0–1.0) | Fraction of trips arriving within the early/late thresholds. |
| Speed | km/h | Average speed of a vehicle over a route segment or full run. |
| Dwell Time | seconds | Time a vehicle spends stopped at a station. |
| Route Daily | `(RouteId, Date)` | Aggregated performance metrics for a route on a specific calendar date. |
| Schedule Card | — | UI component displaying per-day-type headway statistics for a route. |
| GTFS Static Feed | — | A ZIP archive of CSVs describing the planned timetable. |
| GTFS-RT Feed | — | A protobuf stream of real-time vehicle positions and delay updates. |

### bounded-context-canvas.md

Four bounded contexts, aligned to the crate structure:

**Feed Ingestion** (worker crate)
- Owns: GTFS static download + parse + upsert; GTFS-RT polling + decode
- Commands: `IngestStaticFeed(agency_id, url)`, `PollRealtimeFeed(agency_id, url)`
- External dependency: GTFS provider (upstream, opaque)
- Policy: translate all GTFS types to domain types before DB write

**Schedule** (core, static side — routes, trips, stops, variants, service calendars)
- Owns: the planned timetable as stored in Postgres
- Consumed by: Performance (read-only)
- Source of truth: what was planned, not what happened

**Performance** (core — metrics/, speed/, frequency/)
- Owns: delay computation, on-time rates, speed, dwell time, headway computation
- Depends on: Schedule (reads planned data); GTFS-RT (receives real-time observations)
- Produces: Route Daily records; speed cards; schedule cards

**Reporting** (server crate)
- Owns: HTTP handlers + Askama templates + UI cards
- Depends on: Performance (query functions only, no raw sqlx)
- Rule: no business logic in handlers; no raw sqlx in handlers

### context-map.md

```
GTFS Provider ──(ACL)──► Feed Ingestion ──(Conformist)──► Schedule
                                                               │
                                                          (Shared Kernel: IDs)
                                                               │
                                                               ▼
                                                          Performance ──(Customer/Supplier)──► Reporting
```

Relationships:
- **GTFS Provider → Feed Ingestion**: ACL. GTFS is an external standard; the worker translates it without letting its model leak inward.
- **Feed Ingestion → Schedule**: Conformist. The DB schema mirrors GTFS structure intentionally; we accept that constraint.
- **Schedule → Performance**: Shared Kernel. `RouteId`, `TripId`, `StopId`, etc. are defined once in `ids.rs` and used by both.
- **Performance → Reporting**: Customer/Supplier. Reporting is the downstream consumer; Performance exposes query functions that Reporting calls.

### aggregate-specs.md

**Agency**
- Root: `AgencyId`
- Invariants: has at least one route
- Lifecycle: created on static feed ingest; never deleted (data retention only)

**Route**
- Root: `RouteId`
- Contains: one or more `Variant`s (each with an ordered stop list)
- Invariants: belongs to exactly one agency; has at least one variant
- Lifecycle: upserted on each static feed ingest

**Trip**
- Root: `TripId`
- Belongs to: one `Route`, one `Service`
- Invariants: direction is 0 or 1; scheduled times are monotonically increasing
- Lifecycle: upserted on each static feed ingest; delay observations attached via GTFS-RT

**RouteDailyMetrics** (computed, not mutable)
- Root: `(RouteId, Date)`
- Derived from: all `TripResult`s for that route on that date
- Invariants: on-time rate ∈ [0.0, 1.0]; avg/max delay are in seconds
- Lifecycle: written by the worker after processing a service day's worth of RT data

### acl.md

The worker crate is the anti-corruption layer. All GTFS raw strings and numerics become domain types before touching the database or any domain logic.

**ID translation (static feed):**
```rust
AgencyId::from(gtfs_agency.id)
RouteId::from(gtfs_route.id)
TripId::from(gtfs_trip.id)
StopId::from(gtfs_stop.id)
ServiceId::from(gtfs_trip.service_id)
VariantId::from(gtfs_shape.shape_id)
DirectionId::from(gtfs_trip.direction_id as i64)
```

**RT translation:**
GTFS-RT protobuf (decoded via prost) → domain delay observations. No protobuf types leak past `crates/worker/src/gtfs/realtime.rs`.

**Rule:** No file in `crates/core/` or `crates/server/` may import `gtfs_structures`, prost-generated types, or any raw GTFS model. Those are exclusively worker concerns.

## `.claude/rules/ddd.md` Rule

The rule file instructs Claude to:

1. **Before writing any feature spec**: read `docs/ddd/bounded-context-canvas.md` and `docs/ddd/aggregate-specs.md` to identify which context(s) and aggregate(s) the feature touches. Every spec must state this explicitly.
2. **New domain terms**: if a feature introduces a term not in `docs/ddd/ubiquitous-language.md`, update the glossary as part of the same spec commit.
3. **Aggregate changes**: if a feature modifies or adds invariants, update `aggregate-specs.md` before writing implementation code.
4. **ACL boundary**: if a feature adds a new external data source, document its translation boundary in `acl.md`.
5. **Spec template addition**: every feature spec must include a "Domain Context" section with: bounded context(s) touched, aggregates involved, any new ubiquitous language terms.

## Testing

No automated tests — these are living documents. Correctness is validated by:
- Spec self-review: every spec's "Domain Context" section is cross-checked against the artifacts
- Code review: type names and invariants in code match artifact definitions

## Out of Scope

- Automated validation that specs reference DDD artifacts
- Tooling to generate artifacts from code
- Bounded context decomposition into separate Cargo crates (a future architectural decision)

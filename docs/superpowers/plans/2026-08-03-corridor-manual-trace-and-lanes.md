# Corridor Manual Trace & Lane Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an analyst manually trace a corridor by clicking points on the WASM shell's existing MapLibre map, persist it with the lane-by-lane domain model (empty lane lists to start — editing lands in a later plan), and remove the dead, never-wired Askama corridor scaffolding this supersedes.

**Architecture:** Reuses `crates/core/src/corridor_design/{geometry,attribution}.rs`'s already-written, already-tested pure logic (implementing their `unimplemented!()` GREEN pass — no new tests needed for those). Adds the `Lane` domain model and its persistence, fixes a known pre-existing type mismatch (`CrossSection.position` is `i32` but its column is `NUMERIC`), makes the existing REQ-001/002 repository functions remix-scoped, and adds a new remix-scoped JSON API + WASM UI for the manual-trace flow, reusing the same click-on-map interaction pattern the region-map page already established.

**Tech Stack:** Rust 2024, sqlx 0.8 (Postgres, compile-time checked queries), Axum 0.7, Yew 0.23 (`corridor_builder_web`), MapLibre GL JS (already integrated), Playwright (E2E).

## Global Constraints

- No mocks in tests — integration tests use real Postgres via `testcontainers`.
- Functional Core / Imperative Shell is mandatory: pure logic has no I/O; I/O lives only in `repository.rs`/handler files.
- sqlx queries must be compile-time checked (`query!`/`query_as!`), except test-seeding `RETURNING id` inserts, which use the runtime `sqlx::query_scalar(...)` form — established precedent in this codebase.
- ID newtypes only — never raw `i64`/`String` for domain identifiers in `crates/core` or `crates/server` Rust code (HTTP/JSON boundaries use plain `i64`, converted immediately).
- This plan modifies `crates/core/migrations/` — called out explicitly per this project's Safety Rules.
- No file in `crates/core/` or `crates/server/` may import `gtfs_structures::*` or prost-generated protobuf types (this plan doesn't touch GTFS at all, but the rule is repo-wide).
- Design specs: `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md` (this plan implements the manual-trace and lane-infrastructure portions) and `docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md` (the shell this plugs into).
- **Out of scope for this plan** (separate, later plans): OSM import via Overpass, lane *editing* UI, cross-section add/reorder (REQ-004/005), intersection treatments. After this plan, finishing a manual trace navigates to the shell's existing `/builder/remix/:remix_id/corridor/:corridor_id` placeholder page ("Corridor editor coming soon") — that's expected; a later plan turns it into the real lane editor.

---

## Task 1: Remove the superseded Askama scaffolding

**Files:**
- Delete: `crates/server/src/web/corridor_design.rs`
- Delete: `crates/server/src/web/corridor_import.rs`
- Delete: `e2e/tests/req-001-import.spec.ts`
- Delete: `e2e/tests/req-002-manual-trace.spec.ts`
- Delete: `e2e/tests/req-005-reorder.spec.ts`
- Delete: `e2e/tests/graceful-degradation.spec.ts`
- Delete: `e2e/tests/feature-detection.spec.ts`
- Modify: `crates/server/src/web/mod.rs`

**Interfaces:** none — these files are confirmed never wired into `build_router` and contain only `unimplemented!()` stubs. Nothing else in the codebase references them.

- [ ] **Step 1: Verify nothing else references these files**

Run:
```bash
grep -rn "corridor_design::\|corridor_import::" crates/server/src --include="*.rs" | grep -v "crates/server/src/web/corridor_design.rs" | grep -v "crates/server/src/web/corridor_import.rs"
```
Expected: no output (confirms only `web/mod.rs`'s two `mod` declarations reference them).

- [ ] **Step 2: Delete the files**

```bash
git rm crates/server/src/web/corridor_design.rs crates/server/src/web/corridor_import.rs
git rm e2e/tests/req-001-import.spec.ts e2e/tests/req-002-manual-trace.spec.ts e2e/tests/req-005-reorder.spec.ts e2e/tests/graceful-degradation.spec.ts e2e/tests/feature-detection.spec.ts
```

- [ ] **Step 3: Remove the now-dangling module declarations**

In `crates/server/src/web/mod.rs`, remove these two lines (they appear near the top, alongside the other `mod` declarations):
```rust
mod corridor_design;
mod corridor_import;
```

- [ ] **Step 4: Verify it still builds**

Run: `cargo build --workspace`
Expected: succeeds. (`crates/core/src/corridor_design/*` — the pure logic these Askama handlers called into — is untouched and still compiles; only the dead Axum-handler layer is removed.)

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "chore(corridor-design): remove the unwired, unimplemented Askama corridor scaffolding

Superseded by the WASM rework — see docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md."
```

---

## Task 2: Fix `CrossSection.position`'s type to match its `NUMERIC` column

**Files:**
- Modify: `crates/core/src/corridor_design/mod.rs`
- Modify: `crates/core/src/corridor_design/repository.rs` (test helpers only — production functions are rewritten in Task 6)
- Modify: `crates/core/src/corridor_design/edit.rs` (test helper only)

**Interfaces:**
- Produces: `CrossSection.position: f64` (was `i32`) — every later task in this plan, and every future plan touching `CrossSection`, uses `f64`.

Migration `022_cross_section_fractional_position.sql` (already applied, part of the inherited Loop-A schema) changed `cross_sections.position` from `INTEGER` to `NUMERIC`, to support the fractional insert REQ-004 needs. The Rust-side `CrossSection.position` field was never updated to match — it's still `i32`, which cannot decode a `NUMERIC` column via `sqlx::FromRow` without an explicit cast. This was already flagged in `repository.rs`'s own comments ("will need to move to a fractional-compatible type once `position` is implemented for real") — this task is that fix, required now because Task 6 implements `get_corridor_cross_sections` for real against the actual column.

- [ ] **Step 1: Change the field type**

In `crates/core/src/corridor_design/mod.rs`, change:
```rust
#[derive(Debug, Clone, PartialEq, sqlx::FromRow)]
pub struct CrossSection {
    pub id: CrossSectionId,
    pub corridor_id: CorridorId,
    pub position: i32,
```
to:
```rust
#[derive(Debug, Clone, PartialEq, sqlx::FromRow)]
pub struct CrossSection {
    pub id: CrossSectionId,
    pub corridor_id: CorridorId,
    pub position: f64,
```

- [ ] **Step 2: Fix the compile errors this reveals**

Run: `cargo build -p mobilispect-core 2>&1 | grep -A3 "error\["`

This will surface every place a `CrossSection.position` is constructed or compared as `i32`. Fix each by hand:
- `crates/core/src/corridor_design/edit.rs`'s test module: `make_cross_section(id: i64, position: i32, ...)` constructs a `CrossSection` with `position,` (shorthand) — change the helper's own parameter to `position: f64` and its caller `sample_three()` to pass `f64` literals (`0.0`, `1.0`, `2.0` instead of `0`, `1`, `2`), and fix the `lat`/`lon` computation line (`f64::from(position) * 0.001` → just `position * 0.001`, since `position` is already `f64`). Also fix `assert_eq!(updated[1].position, 1);` near the bottom of that test to `assert_eq!(updated[1].position, 1.0);`.
- `crates/core/src/corridor_design/repository.rs`'s test module: `struct CrossSectionRow { position: i32, lat: f64, lon: f64 }` (used by `insert_corridor_persists_ordered_cross_sections` and `manual_trace_start_add_points_and_finalize_persists_ordered_cross_sections`) — change `position: i32` to `position: f64`. The query these structs decode (`"SELECT position, lat, lon FROM cross_sections ..."`) will need a cast — see Task 6, which rewrites these two test functions' queries anyway; for this task, just get the struct's field type right and leave the query text as-is (Task 6 fixes the cast).
- Any other `i32` position literal/comparison the compiler flags — fix following the same pattern (append `.0` or change the literal to a float).

Do not fix the query casts yet — that's genuinely Task 6's job, since it's implementing those functions for real. This task's job is just the type change and its ripple through already-written test fixtures.

- [ ] **Step 3: Confirm the crate compiles again (tests may still fail to compile at the query-cast level — that's expected and fixed in Task 6)**

Run: `cargo build -p mobilispect-core 2>&1 | tail -30`

If the ONLY remaining errors are inside `#[cfg(test)] mod tests` blocks in `repository.rs` about a query's decoded type not matching `CrossSectionRow`'s new `f64` field (a `NUMERIC`-to-`f64` decode mismatch), that's expected — Task 6 fixes those specific queries. If there are compile errors anywhere else, fix them now before moving on.

- [ ] **Step 4: Commit**

```bash
git add crates/core/src/corridor_design/mod.rs crates/core/src/corridor_design/edit.rs crates/core/src/corridor_design/repository.rs
git commit -m "fix(corridor-design): change CrossSection.position from i32 to f64, matching its NUMERIC column"
```

---

## Task 3: Migration — `lanes` and `lane_access_rules` tables, `LaneId`

**Files:**
- Create: `crates/core/migrations/026_corridor_lanes.sql`
- Modify: `crates/core/src/ids.rs`

**Interfaces:**
- Produces: `mobilispect_core::ids::LaneId` (int-backed newtype, same shape as `CrossSectionId`).

- [ ] **Step 1: Write the migration**

```sql
-- migrations/026_corridor_lanes.sql
-- Corridor Segment Editor: a cross-section is a lane-by-lane arrangement, not
-- just a labeled point. A lane's access is one or more time-windowed rules
-- (NULL day/start/end = always active). See
-- docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md.

CREATE TABLE lanes (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cross_section_id  BIGINT NOT NULL REFERENCES cross_sections(id) ON DELETE CASCADE,
    position          NUMERIC NOT NULL,
    lane_type         TEXT NOT NULL CHECK (lane_type IN (
                          'travel', 'turn', 'transit', 'queue_jump', 'cycle_lane',
                          'cycle_track', 'parking', 'sidewalk', 'median', 'buffer'
                      )),
    width_meters      DOUBLE PRECISION NOT NULL CHECK (width_meters > 0),
    direction         TEXT NOT NULL CHECK (direction IN ('forward', 'backward', 'both', 'none')),
    UNIQUE (cross_section_id, position)
);

CREATE INDEX idx_lanes_cross_section ON lanes (cross_section_id, position);

CREATE TABLE lane_access_rules (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lane_id        BIGINT NOT NULL REFERENCES lanes(id) ON DELETE CASCADE,
    days           TEXT,
    start_time     TIME,
    end_time       TIME,
    allowed_modes  TEXT[] NOT NULL
);

CREATE INDEX idx_lane_access_rules_lane ON lane_access_rules (lane_id);
```

- [ ] **Step 2: Add `LaneId`**

In `crates/core/src/ids.rs`, in the "Integer-based IDs" block, immediately after `int_id!(RemixId);`:
```rust
int_id!(LaneId);
```

- [ ] **Step 3: Verify**

Run: `cargo build -p mobilispect-core`
Expected: succeeds. (The migration itself is exercised the first time a test calls `test_utils::setup()` in Task 4.)

- [ ] **Step 4: Commit**

```bash
git add crates/core/migrations/026_corridor_lanes.sql crates/core/src/ids.rs
git commit -m "feat(corridor-design): add lanes and lane_access_rules tables, LaneId"
```

---

## Task 4: Lane domain types and default tables

**Files:**
- Create: `crates/core/src/corridor_design/lanes.rs`
- Modify: `crates/core/src/corridor_design/mod.rs`

**Interfaces:**
- Consumes: `crate::ids::{LaneId, CrossSectionId}` (existing/Task 3).
- Produces: `lanes::{LaneType, LaneDirection, AccessMode, TimeWindow, TimedAccessRule, Lane, LaneDraft}`, `lanes::default_access_rule_for(lane_type: LaneType) -> TimedAccessRule`, `lanes::default_width_meters_for(lane_type: LaneType) -> f64`.

`LaneDraft` is the pre-persistence shape (no `id`, no `cross_section_id` yet — what a caller builds before inserting). `Lane` is the persisted, read-back shape.

- [ ] **Step 1: Write the failing tests**

Create `crates/core/src/corridor_design/lanes.rs`:

```rust
//! Lane domain types: a cross-section is an ordered, left-to-right arrangement of
//! lanes, each with a type, width, direction, and access policy. See
//! `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`.

use crate::ids::{CrossSectionId, LaneId};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LaneType {
    Travel,
    Turn,
    Transit,
    QueueJump,
    CycleLane,
    CycleTrack,
    Parking,
    Sidewalk,
    Median,
    Buffer,
}

impl LaneType {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            LaneType::Travel => "travel",
            LaneType::Turn => "turn",
            LaneType::Transit => "transit",
            LaneType::QueueJump => "queue_jump",
            LaneType::CycleLane => "cycle_lane",
            LaneType::CycleTrack => "cycle_track",
            LaneType::Parking => "parking",
            LaneType::Sidewalk => "sidewalk",
            LaneType::Median => "median",
            LaneType::Buffer => "buffer",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "travel" => Some(LaneType::Travel),
            "turn" => Some(LaneType::Turn),
            "transit" => Some(LaneType::Transit),
            "queue_jump" => Some(LaneType::QueueJump),
            "cycle_lane" => Some(LaneType::CycleLane),
            "cycle_track" => Some(LaneType::CycleTrack),
            "parking" => Some(LaneType::Parking),
            "sidewalk" => Some(LaneType::Sidewalk),
            "median" => Some(LaneType::Median),
            "buffer" => Some(LaneType::Buffer),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LaneDirection {
    Forward,
    Backward,
    Both,
    None,
}

impl LaneDirection {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            LaneDirection::Forward => "forward",
            LaneDirection::Backward => "backward",
            LaneDirection::Both => "both",
            LaneDirection::None => "none",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "forward" => Some(LaneDirection::Forward),
            "backward" => Some(LaneDirection::Backward),
            "both" => Some(LaneDirection::Both),
            "none" => Some(LaneDirection::None),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AccessMode {
    Car,
    Transit,
    Bicycle,
    Pedestrian,
    Emergency,
    Taxi,
    Freight,
    Hov,
}

impl AccessMode {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            AccessMode::Car => "car",
            AccessMode::Transit => "transit",
            AccessMode::Bicycle => "bicycle",
            AccessMode::Pedestrian => "pedestrian",
            AccessMode::Emergency => "emergency",
            AccessMode::Taxi => "taxi",
            AccessMode::Freight => "freight",
            AccessMode::Hov => "hov",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "car" => Some(AccessMode::Car),
            "transit" => Some(AccessMode::Transit),
            "bicycle" => Some(AccessMode::Bicycle),
            "pedestrian" => Some(AccessMode::Pedestrian),
            "emergency" => Some(AccessMode::Emergency),
            "taxi" => Some(AccessMode::Taxi),
            "freight" => Some(AccessMode::Freight),
            "hov" => Some(AccessMode::Hov),
            _ => None,
        }
    }
}

/// `None` means "always active" (the default). A concrete `TimeWindow` narrows
/// the rule to specific days/hours (e.g. a part-time bus lane).
#[derive(Debug, Clone, PartialEq)]
pub struct TimeWindow {
    pub days: String,
    pub start_time: chrono::NaiveTime,
    pub end_time: chrono::NaiveTime,
}

#[derive(Debug, Clone, PartialEq)]
pub struct TimedAccessRule {
    pub time_window: Option<TimeWindow>,
    pub allowed_modes: Vec<AccessMode>,
}

/// A lane before it has been persisted — no `id`/`cross_section_id` yet.
#[derive(Debug, Clone, PartialEq)]
pub struct LaneDraft {
    pub lane_type: LaneType,
    pub width_meters: f64,
    pub direction: LaneDirection,
    pub access_rules: Vec<TimedAccessRule>,
}

/// A persisted lane, as returned from the repository.
#[derive(Debug, Clone, PartialEq)]
pub struct Lane {
    pub id: LaneId,
    pub cross_section_id: CrossSectionId,
    pub position: f64,
    pub lane_type: LaneType,
    pub width_meters: f64,
    pub direction: LaneDirection,
    pub access_rules: Vec<TimedAccessRule>,
}

/// The always-on access rule an analyst gets by default for a given lane type,
/// before overriding it with a time-windowed rule for a special treatment (a BAT
/// lane, a part-time bus lane, etc.).
pub fn default_access_rule_for(lane_type: LaneType) -> TimedAccessRule {
    let allowed_modes = match lane_type {
        LaneType::Travel | LaneType::Turn => vec![AccessMode::Car, AccessMode::Emergency],
        LaneType::Transit | LaneType::QueueJump => vec![AccessMode::Transit, AccessMode::Emergency],
        LaneType::CycleLane | LaneType::CycleTrack => vec![AccessMode::Bicycle],
        LaneType::Parking => vec![AccessMode::Car],
        LaneType::Sidewalk => vec![AccessMode::Pedestrian],
        LaneType::Median | LaneType::Buffer => vec![],
    };
    TimedAccessRule {
        time_window: None,
        allowed_modes,
    }
}

/// The default width (in meters) for a lane type, used when no explicit width is
/// supplied (e.g. no `width:lanes=*` OSM tag on import — the common case).
pub fn default_width_meters_for(lane_type: LaneType) -> f64 {
    match lane_type {
        LaneType::Travel | LaneType::Turn => 3.0,
        LaneType::Transit | LaneType::QueueJump => 3.2,
        LaneType::CycleLane => 1.5,
        LaneType::CycleTrack => 2.0,
        LaneType::Parking => 2.0,
        LaneType::Sidewalk => 1.8,
        LaneType::Median => 1.2,
        LaneType::Buffer => 0.6,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lane_type_db_str_round_trips_all_variants() {
        for lane_type in [
            LaneType::Travel,
            LaneType::Turn,
            LaneType::Transit,
            LaneType::QueueJump,
            LaneType::CycleLane,
            LaneType::CycleTrack,
            LaneType::Parking,
            LaneType::Sidewalk,
            LaneType::Median,
            LaneType::Buffer,
        ] {
            let s = lane_type.as_db_str();
            assert_eq!(LaneType::from_db_str(s), Some(lane_type));
        }
    }

    #[test]
    fn lane_type_from_db_str_rejects_unknown_value() {
        assert_eq!(LaneType::from_db_str("bogus"), None);
    }

    #[test]
    fn lane_direction_db_str_round_trips_all_variants() {
        for direction in [
            LaneDirection::Forward,
            LaneDirection::Backward,
            LaneDirection::Both,
            LaneDirection::None,
        ] {
            let s = direction.as_db_str();
            assert_eq!(LaneDirection::from_db_str(s), Some(direction));
        }
    }

    #[test]
    fn access_mode_db_str_round_trips_all_variants() {
        for mode in [
            AccessMode::Car,
            AccessMode::Transit,
            AccessMode::Bicycle,
            AccessMode::Pedestrian,
            AccessMode::Emergency,
            AccessMode::Taxi,
            AccessMode::Freight,
            AccessMode::Hov,
        ] {
            let s = mode.as_db_str();
            assert_eq!(AccessMode::from_db_str(s), Some(mode));
        }
    }

    #[test]
    fn default_access_rule_for_travel_is_car_and_emergency_always_on() {
        let rule = default_access_rule_for(LaneType::Travel);
        assert_eq!(rule.time_window, None);
        assert_eq!(rule.allowed_modes, vec![AccessMode::Car, AccessMode::Emergency]);
    }

    #[test]
    fn default_access_rule_for_cycle_lane_is_bicycle_only() {
        let rule = default_access_rule_for(LaneType::CycleLane);
        assert_eq!(rule.allowed_modes, vec![AccessMode::Bicycle]);
    }

    #[test]
    fn default_access_rule_for_median_allows_no_modes() {
        let rule = default_access_rule_for(LaneType::Median);
        assert_eq!(rule.allowed_modes, Vec::<AccessMode>::new());
    }

    #[test]
    fn default_width_meters_matches_the_approved_mockup_values() {
        assert_eq!(default_width_meters_for(LaneType::Travel), 3.0);
        assert_eq!(default_width_meters_for(LaneType::CycleLane), 1.5);
        assert_eq!(default_width_meters_for(LaneType::Parking), 2.0);
        assert_eq!(default_width_meters_for(LaneType::Sidewalk), 1.8);
    }
}
```

- [ ] **Step 2: Add `chrono` usage and register the module**

`chrono` is already a dependency of `mobilispect-core` (used elsewhere in the crate) — no `Cargo.toml` change needed.

In `crates/core/src/corridor_design/mod.rs`, add near the top:
```rust
pub mod lanes;
```

- [ ] **Step 3: Run the tests**

Run: `cargo nextest run -p mobilispect-core corridor_design::lanes::tests`
Expected: PASS (all tests written and implemented together in this task — there's no separate red/green split for straightforward type/lookup-table definitions).

- [ ] **Step 4: Commit**

```bash
git add crates/core/src/corridor_design/lanes.rs crates/core/src/corridor_design/mod.rs
git commit -m "feat(corridor-design): add Lane domain types and default access/width tables"
```

---

## Task 5: `geometry.rs` and `attribution.rs` GREEN pass

**Files:**
- Modify: `crates/core/src/corridor_design/geometry.rs`
- Modify: `crates/core/src/corridor_design/attribution.rs`

**Interfaces:**
- Produces: real (non-`unimplemented!()`) `normalize_corridor_geometry`, `validate_next_point`, `validate_finishable`, `next_position`, `attribution_visible` — Task 6's `insert_corridor`/`insert_cross_section` and the new API handlers in Task 8 call these.

Every test for these functions already exists and is already correctly written (confirmed by reading both files in full) — this task is purely implementing the function bodies to make the existing tests pass. No new tests are written here.

- [ ] **Step 1: Confirm the existing tests fail for the expected reason**

Run: `cargo nextest run -p mobilispect-core corridor_design::geometry::tests corridor_design::attribution::tests 2>&1 | tail -20`
Expected: FAIL — every test panics with `unimplemented!("IMP-REQ-...: ... not yet implemented")`.

- [ ] **Step 2: Implement `attribution_visible`**

In `crates/core/src/corridor_design/attribution.rs`, replace:
```rust
pub fn attribution_visible(geometry_source: Option<GeometrySource>) -> bool {
    let _ = geometry_source;
    unimplemented!("IMP-REQ-003-02: attribution_visible not yet implemented")
}
```
with:
```rust
pub fn attribution_visible(geometry_source: Option<GeometrySource>) -> bool {
    !matches!(geometry_source, Some(GeometrySource::Manual))
}
```

- [ ] **Step 3: Implement the geometry.rs functions**

In `crates/core/src/corridor_design/geometry.rs`:

```rust
pub fn validate_next_point(
    existing: &[Coordinate],
    candidate: Coordinate,
) -> Result<(), GeometryValidationError> {
    let Some(previous) = existing.last() else {
        return Ok(());
    };
    // Boundary points constructed via degree-based math (see
    // `point_north_of` in the test fixtures) round-trip through the
    // haversine formula with ~1e-9m of floating-point drift; the epsilon
    // keeps a point exactly at MIN_POINT_SEPARATION_METERS from being
    // rejected by that drift without masking genuinely-too-close points
    // (the nearest rejection case in the test suite is 9 orders of
    // magnitude further away than this epsilon).
    const EPSILON: f64 = 1e-9;
    if haversine_meters(*previous, candidate) < MIN_POINT_SEPARATION_METERS - EPSILON {
        return Err(GeometryValidationError::DuplicateOrTooClose);
    }
    Ok(())
}

pub fn validate_finishable(points: &[Coordinate]) -> Result<(), GeometryValidationError> {
    if points.len() < 2 {
        return Err(GeometryValidationError::InsufficientPoints);
    }
    Ok(())
}

pub fn next_position(existing: &[Coordinate]) -> i32 {
    existing.len() as i32
}
```

Add the haversine helper (private to this module — mirrors `speed_analysis::haversine_meters`'s formula, matching the existing test fixture's `EARTH_RADIUS_M` constant exactly):

```rust
fn haversine_meters(a: Coordinate, b: Coordinate) -> f64 {
    const EARTH_RADIUS_M: f64 = 6_371_000.0;
    let lat1 = a.lat.to_radians();
    let lat2 = b.lat.to_radians();
    let delta_lat = (b.lat - a.lat).to_radians();
    let delta_lon = (b.lon - a.lon).to_radians();
    let h = (delta_lat / 2.0).sin().powi(2)
        + lat1.cos() * lat2.cos() * (delta_lon / 2.0).sin().powi(2);
    2.0 * EARTH_RADIUS_M * h.sqrt().asin()
}
```

Now `normalize_corridor_geometry`. This is the most involved function in this task: order way segments into one connected path (matching shared endpoint nodes), reject self-intersection and disconnection, validate WGS84 ranges.

```rust
pub fn normalize_corridor_geometry(
    raw: RawGeometry,
) -> Result<NormalizedCorridor, ImportGeometryError> {
    if raw.segments.is_empty() {
        return Err(ImportGeometryError::IncompleteGeometry);
    }
    for segment in &raw.segments {
        if segment.points.len() < 2 {
            return Err(ImportGeometryError::IncompleteGeometry);
        }
        for point in &segment.points {
            if !point.coordinate.is_valid() {
                return Err(ImportGeometryError::Malformed(format!(
                    "coordinate ({}, {}) is outside valid WGS84 range",
                    point.coordinate.lat, point.coordinate.lon
                )));
            }
        }
    }

    let ordered_points = order_segments_into_path(&raw.segments)?;

    if path_self_intersects(&ordered_points) {
        return Err(ImportGeometryError::SelfIntersecting);
    }

    let cross_sections = ordered_points
        .into_iter()
        .enumerate()
        .map(|(i, p)| CrossSectionPoint {
            position: i as i32,
            coordinate: p.coordinate,
            osm_way_id: p.osm_way_id,
            osm_node_id: p.osm_node_id,
        })
        .collect();

    Ok(NormalizedCorridor { cross_sections })
}

#[derive(Debug, Clone, Copy)]
struct OrderedPoint {
    coordinate: Coordinate,
    osm_way_id: Option<i64>,
    osm_node_id: Option<i64>,
}

/// Orders way segments end-to-end into one connected path. A single segment is
/// trivially "ordered" as-is. Multiple segments must chain via shared endpoint
/// coordinates (within floating-point tolerance) — this rejects (as
/// `Disconnected`) any segment that doesn't connect to the growing chain at
/// either end.
fn order_segments_into_path(
    segments: &[RawWaySegment],
) -> Result<Vec<OrderedPoint>, ImportGeometryError> {
    const COORDINATE_TOLERANCE: f64 = 1e-9;

    fn coords_match(a: Coordinate, b: Coordinate) -> bool {
        (a.lat - b.lat).abs() < COORDINATE_TOLERANCE && (a.lon - b.lon).abs() < COORDINATE_TOLERANCE
    }

    fn segment_points(segment: &RawWaySegment) -> Vec<OrderedPoint> {
        segment
            .points
            .iter()
            .map(|p| OrderedPoint {
                coordinate: p.coordinate,
                osm_way_id: segment.osm_way_id,
                osm_node_id: p.osm_node_id,
            })
            .collect()
    }

    let mut remaining: Vec<&RawWaySegment> = segments.iter().collect();
    let first = remaining.remove(0);
    let mut chain = segment_points(first);

    while !remaining.is_empty() {
        let chain_start = chain.first().unwrap().coordinate;
        let chain_end = chain.last().unwrap().coordinate;

        let match_index = remaining.iter().position(|seg| {
            let seg_start = seg.points.first().unwrap().coordinate;
            let seg_end = seg.points.last().unwrap().coordinate;
            coords_match(chain_end, seg_start)
                || coords_match(chain_end, seg_end)
                || coords_match(chain_start, seg_start)
                || coords_match(chain_start, seg_end)
        });

        let Some(index) = match_index else {
            return Err(ImportGeometryError::Disconnected);
        };

        let segment = remaining.remove(index);
        let seg_start = segment.points.first().unwrap().coordinate;
        let seg_end = segment.points.last().unwrap().coordinate;
        let mut points = segment_points(segment);

        if coords_match(chain_end, seg_start) {
            // Appends after the chain's end, dropping the duplicate shared point.
            points.remove(0);
            chain.extend(points);
        } else if coords_match(chain_end, seg_end) {
            points.reverse();
            points.remove(0);
            chain.extend(points);
        } else if coords_match(chain_start, seg_end) {
            points.pop();
            points.extend(chain);
            chain = points;
        } else {
            // coords_match(chain_start, seg_start)
            points.reverse();
            points.pop();
            points.extend(chain);
            chain = points;
        }
    }

    Ok(chain)
}

/// True if any two non-adjacent segments of the path cross each other.
/// Adjacent segments sharing an endpoint are not considered a self-intersection.
fn path_self_intersects(points: &[OrderedPoint]) -> bool {
    if points.len() < 4 {
        return false;
    }
    for i in 0..points.len() - 1 {
        let a1 = points[i].coordinate;
        let a2 = points[i + 1].coordinate;
        for j in (i + 2)..points.len() - 1 {
            // Skip the pair that shares an endpoint with segment i, but only
            // when the path is actually closed (points[0] == points[last]).
            // An unconditional skip here misses real self-intersections on
            // open paths — e.g. the bowtie (0,0)->(1,1)->(0,1)->(1,0), whose
            // only checkable pair (i=0, j=points.len()-2) crosses at
            // (0.5, 0.5) but would never be checked.
            if i == 0
                && j == points.len() - 2
                && points[0].coordinate == points[points.len() - 1].coordinate
            {
                continue;
            }
            let b1 = points[j].coordinate;
            let b2 = points[j + 1].coordinate;
            if segments_intersect(a1, a2, b1, b2) {
                return true;
            }
        }
    }
    false
}

fn segments_intersect(p1: Coordinate, p2: Coordinate, p3: Coordinate, p4: Coordinate) -> bool {
    fn orientation(a: Coordinate, b: Coordinate, c: Coordinate) -> f64 {
        (b.lon - a.lon) * (c.lat - a.lat) - (b.lat - a.lat) * (c.lon - a.lon)
    }
    // Bounding-box containment check for a point c already known to be
    // collinear with a/b (the caller only calls this when the orientation
    // test found o == 0.0). Compares c against a/b's range only — folding c
    // into its own min/max (as an earlier draft of this function did) makes
    // every conjunct a tautology (`min(c, x) <= c` and `c <= max(c, x)` are
    // always true), which flags any exactly-collinear point as "on segment"
    // regardless of whether it actually falls within a/b's bounding box.
    fn on_segment(a: Coordinate, b: Coordinate, c: Coordinate) -> bool {
        a.lon.min(b.lon) <= c.lon
            && c.lon <= a.lon.max(b.lon)
            && a.lat.min(b.lat) <= c.lat
            && c.lat <= a.lat.max(b.lat)
    }

    let o1 = orientation(p1, p2, p3);
    let o2 = orientation(p1, p2, p4);
    let o3 = orientation(p3, p4, p1);
    let o4 = orientation(p3, p4, p2);

    if (o1 > 0.0) != (o2 > 0.0) && (o3 > 0.0) != (o4 > 0.0) && o1 != 0.0 && o2 != 0.0 {
        return true;
    }

    (o1 == 0.0 && on_segment(p1, p2, p3))
        || (o2 == 0.0 && on_segment(p1, p2, p4))
        || (o3 == 0.0 && on_segment(p3, p4, p1))
        || (o4 == 0.0 && on_segment(p3, p4, p2))
}
```

- [ ] **Step 4: Run the tests**

Run: `cargo nextest run -p mobilispect-core corridor_design::geometry::tests corridor_design::attribution::tests`
Expected: all PASS. If `normalize_corridor_geometry_rejects_self_intersecting_path` or the ordering test fails, re-check the fixture in the test against the algorithm above step by step — the bowtie fixture `(0,0)->(1,1)->(0,1)->(1,0)` has segments `[0,1]` and `[2,3]` (0-indexed) crossing, which `path_self_intersects`'s `i=0,j=2` iteration checks.

- [ ] **Step 5: Commit**

```bash
git add crates/core/src/corridor_design/geometry.rs crates/core/src/corridor_design/attribution.rs
git commit -m "feat(corridor-design): implement normalize_corridor_geometry, point validators, and attribution_visible"
```

---

## Task 6: `repository.rs` — remix-scoped GREEN pass + lane persistence

**Files:**
- Modify: `crates/core/src/corridor_design/repository.rs`

**Interfaces:**
- Consumes: `crate::ids::{RemixId, LaneId}` (Task 3), `crate::corridor_design::lanes::{Lane, LaneDraft, LaneType, LaneDirection, TimedAccessRule, AccessMode, TimeWindow}` (Task 4), `crate::corridor_design::geometry::{normalize_corridor_geometry, NormalizedCorridor}` (Task 5, already existed).
- Produces:
  - `repository::insert_corridor(pool, remix_id: RemixId, name: &str, import_format: &str, osm_attribution: Option<&str>, normalized: &NormalizedCorridor) -> anyhow::Result<CorridorId>` (added `remix_id` param)
  - `repository::get_corridor_cross_sections(pool, corridor_id: CorridorId) -> anyhow::Result<Vec<CrossSection>>`
  - `repository::start_manual_corridor(pool, remix_id: RemixId, name: &str) -> anyhow::Result<CorridorId>` (added `remix_id` param)
  - `repository::insert_cross_section(pool, corridor_id: CorridorId, coordinate: Coordinate, position: i32) -> anyhow::Result<CrossSectionId>`
  - `repository::finalize_corridor(pool, corridor_id: CorridorId) -> anyhow::Result<()>`
  - `repository::insert_lanes_for_cross_section(pool, cross_section_id: CrossSectionId, drafts: &[LaneDraft]) -> anyhow::Result<Vec<LaneId>>`
  - `repository::get_lanes_for_cross_section(pool, cross_section_id: CrossSectionId) -> anyhow::Result<Vec<Lane>>`

- [ ] **Step 1: Confirm the existing tests fail for the expected reason**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core corridor_design::repository::tests::insert_corridor_persists_ordered_cross_sections corridor_design::repository::tests::manual_trace_start_add_points_and_finalize_persists_ordered_cross_sections 2>&1 | tail -20`
Expected: FAIL — `unimplemented!()` panics.

- [ ] **Step 2: Rewrite the five function signatures and implementations**

Replace the `insert_corridor` through `finalize_corridor` functions in `crates/core/src/corridor_design/repository.rs` (everything from the top of the file through the `finalize_corridor` function, leaving `add_cross_section`, `reorder_cross_sections`, `update_cross_section_label` untouched below — those stay `unimplemented!()`, they're a later plan's job) with:

```rust
//! Corridor Design repository: the imperative I/O shell for corridors and
//! cross-sections. Pure normalization logic lives in `geometry.rs`; this module
//! persists an already-normalized corridor and reads it back — no validation or
//! geometry computation happens here.

use crate::corridor_design::Coordinate;
use crate::corridor_design::CrossSection;
use crate::corridor_design::geometry::NormalizedCorridor;
use crate::corridor_design::lanes::{Lane, LaneDraft, LaneDirection, LaneType, TimedAccessRule};
use crate::ids::{CorridorId, CrossSectionId, LaneId, RemixId};

/// Persists a newly imported corridor and its ordered cross-sections, scoped to
/// `remix_id`. `normalized` must already be validated (see
/// `geometry::normalize_corridor_geometry`) — this function performs no geometry
/// validation of its own. Does not create any lanes; callers that want an
/// OSM-tag-derived baseline lane set insert them separately via
/// `insert_lanes_for_cross_section` after this returns.
pub async fn insert_corridor(
    pool: &sqlx::PgPool,
    remix_id: RemixId,
    name: &str,
    import_format: &str,
    osm_attribution: Option<&str>,
    normalized: &NormalizedCorridor,
) -> Result<CorridorId, anyhow::Error> {
    let mut tx = pool.begin().await?;

    // `RETURNING id` on a non-test-seeding insert uses the compile-time-checked
    // `query!` macro, per this plan's Global Constraints — the test-seeding
    // exception (runtime `sqlx::query_scalar`) doesn't apply to production code.
    let corridor_id = sqlx::query!(
        "INSERT INTO corridors (remix_id, name, geometry_source, import_format, osm_attribution) \
         VALUES ($1, $2, 'imported', $3, $4) RETURNING id",
        remix_id.as_i64(),
        name,
        import_format,
        osm_attribution,
    )
    .fetch_one(&mut *tx)
    .await?
    .id;

    for cs in &normalized.cross_sections {
        // `$2::float8`: `position` is a NUMERIC column and this crate has no
        // bigdecimal/rust_decimal sqlx feature enabled (see `position.rs`'s
        // top-of-file note on the same constraint) — casting the bind
        // placeholder to `float8` lets `query!` accept an `f64` argument here
        // (Postgres implicitly casts float8 -> numeric on insert), mirroring
        // this codebase's existing `position::float8 AS "position!"` pattern
        // for the read direction.
        sqlx::query!(
            "INSERT INTO cross_sections (corridor_id, position, lat, lon, osm_way_id, osm_node_id) \
             VALUES ($1, $2::float8, $3, $4, $5, $6)",
            corridor_id,
            f64::from(cs.position),
            cs.coordinate.lat,
            cs.coordinate.lon,
            cs.osm_way_id,
            cs.osm_node_id,
        )
        .execute(&mut *tx)
        .await?;
    }

    tx.commit().await?;
    Ok(CorridorId::from(corridor_id))
}

/// Fetches all cross-sections for a corridor, ordered by `position`.
pub async fn get_corridor_cross_sections(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
) -> Result<Vec<CrossSection>, anyhow::Error> {
    let rows = sqlx::query!(
        r#"SELECT id, corridor_id, position::float8 AS "position!", lat, lon,
                  osm_way_id, osm_node_id, label
           FROM cross_sections
           WHERE corridor_id = $1
           ORDER BY position"#,
        corridor_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;

    Ok(rows
        .into_iter()
        .map(|row| CrossSection {
            id: CrossSectionId::from(row.id),
            corridor_id: CorridorId::from(row.corridor_id),
            position: row.position,
            lat: row.lat,
            lon: row.lon,
            osm_way_id: row.osm_way_id,
            osm_node_id: row.osm_node_id,
            label: row.label,
        })
        .collect())
}

/// Creates a new corridor for a manual trace (REQ-002), scoped to `remix_id`,
/// with `geometry_source = 'manual'` and no `import_format`/`osm_attribution`.
/// Cross-sections are added one at a time afterward via `insert_cross_section`
/// as the analyst clicks.
pub async fn start_manual_corridor(
    pool: &sqlx::PgPool,
    remix_id: RemixId,
    name: &str,
) -> Result<CorridorId, anyhow::Error> {
    let corridor_id = sqlx::query!(
        "INSERT INTO corridors (remix_id, name, geometry_source) VALUES ($1, $2, 'manual') RETURNING id",
        remix_id.as_i64(),
        name,
    )
    .fetch_one(pool)
    .await?
    .id;
    Ok(CorridorId::from(corridor_id))
}

/// Inserts a single cross-section point at `position` for an existing corridor.
/// Caller must have already validated `coordinate` against the corridor's
/// existing points (see `geometry::validate_next_point`) — this function
/// performs no geometry validation of its own. Returns an error if
/// `corridor_id` does not reference an existing corridor.
pub async fn insert_cross_section(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
    coordinate: Coordinate,
    position: i32,
) -> Result<CrossSectionId, anyhow::Error> {
    // `AS "exists!"`: `EXISTS(...)` always yields a genuine boolean, but sqlx's
    // `describe` reports computed expressions as nullable by default — the `!`
    // suffix forces the non-null type, matching this codebase's established
    // force-non-null idiom (e.g. `db/feeds.rs`'s `agency_onestop_id!`).
    let exists = sqlx::query_scalar!(
        r#"SELECT EXISTS(SELECT 1 FROM corridors WHERE id = $1) AS "exists!""#,
        corridor_id.as_i64(),
    )
    .fetch_one(pool)
    .await?;
    if !exists {
        anyhow::bail!("corridor {corridor_id} does not exist");
    }

    let id = sqlx::query!(
        "INSERT INTO cross_sections (corridor_id, position, lat, lon) VALUES ($1, $2::float8, $3, $4) RETURNING id",
        corridor_id.as_i64(),
        f64::from(position),
        coordinate.lat,
        coordinate.lon,
    )
    .fetch_one(pool)
    .await?
    .id;
    Ok(CrossSectionId::from(id))
}

/// Marks a manually-traced corridor as finished. Caller must have already
/// validated the corridor has enough points (see `geometry::validate_finishable`)
/// — this function performs no geometry validation of its own. Currently a
/// no-op beyond confirming the corridor exists (there is no "finished" flag on
/// `corridors` yet) — kept as its own function/step because the manual-trace
/// UI flow (start -> add points -> finish) treats it as a distinct action, and
/// a later plan may add real finalization behavior here (e.g. a
/// `finalized_at` timestamp).
pub async fn finalize_corridor(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
) -> Result<(), anyhow::Error> {
    let exists = sqlx::query_scalar!(
        r#"SELECT EXISTS(SELECT 1 FROM corridors WHERE id = $1) AS "exists!""#,
        corridor_id.as_i64(),
    )
    .fetch_one(pool)
    .await?;
    if !exists {
        anyhow::bail!("corridor {corridor_id} does not exist");
    }
    Ok(())
}

fn time_window_columns(rule: &TimedAccessRule) -> (Option<String>, Option<chrono::NaiveTime>, Option<chrono::NaiveTime>) {
    match &rule.time_window {
        Some(w) => (Some(w.days.clone()), Some(w.start_time), Some(w.end_time)),
        None => (None, None, None),
    }
}

/// Inserts `drafts` as new lanes for `cross_section_id`, in the given order
/// (left-to-right), assigning each a fractional `position` (`1.0`, `2.0`, ...).
/// Each lane's `access_rules` are inserted as `lane_access_rules` rows.
pub async fn insert_lanes_for_cross_section(
    pool: &sqlx::PgPool,
    cross_section_id: CrossSectionId,
    drafts: &[LaneDraft],
) -> Result<Vec<LaneId>, anyhow::Error> {
    let mut tx = pool.begin().await?;
    let mut lane_ids = Vec::with_capacity(drafts.len());

    for (i, draft) in drafts.iter().enumerate() {
        let position = (i + 1) as f64;
        let lane_type = draft.lane_type.as_db_str();
        let direction = draft.direction.as_db_str();
        // `$2::float8` — same NUMERIC-column reasoning as `insert_corridor`'s
        // cross-section insert above.
        let lane_id = sqlx::query!(
            "INSERT INTO lanes (cross_section_id, position, lane_type, width_meters, direction) \
             VALUES ($1, $2::float8, $3, $4, $5) RETURNING id",
            cross_section_id.as_i64(),
            position,
            lane_type,
            draft.width_meters,
            direction,
        )
        .fetch_one(&mut *tx)
        .await?
        .id;

        for rule in &draft.access_rules {
            let (days, start_time, end_time) = time_window_columns(rule);
            let allowed_modes: Vec<&str> =
                rule.allowed_modes.iter().map(|m| m.as_db_str()).collect();
            sqlx::query!(
                "INSERT INTO lane_access_rules (lane_id, days, start_time, end_time, allowed_modes) \
                 VALUES ($1, $2, $3, $4, $5)",
                lane_id,
                days,
                start_time,
                end_time,
                &allowed_modes as &[&str],
            )
            .execute(&mut *tx)
            .await?;
        }

        lane_ids.push(LaneId::from(lane_id));
    }

    tx.commit().await?;
    Ok(lane_ids)
}

struct LaneRow {
    id: i64,
    cross_section_id: i64,
    position: f64,
    lane_type: String,
    width_meters: f64,
    direction: String,
}

struct AccessRuleRow {
    lane_id: i64,
    days: Option<String>,
    start_time: Option<chrono::NaiveTime>,
    end_time: Option<chrono::NaiveTime>,
    allowed_modes: Vec<String>,
}

/// Fetches all lanes for a cross-section, ordered left-to-right, each with its
/// access rules.
pub async fn get_lanes_for_cross_section(
    pool: &sqlx::PgPool,
    cross_section_id: CrossSectionId,
) -> Result<Vec<Lane>, anyhow::Error> {
    let lane_rows: Vec<LaneRow> = sqlx::query_as!(
        LaneRow,
        r#"SELECT id, cross_section_id, position::float8 AS "position!", lane_type, width_meters, direction
           FROM lanes
           WHERE cross_section_id = $1
           ORDER BY position"#,
        cross_section_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;

    if lane_rows.is_empty() {
        return Ok(Vec::new());
    }

    let lane_ids: Vec<i64> = lane_rows.iter().map(|r| r.id).collect();
    let rule_rows: Vec<AccessRuleRow> = sqlx::query_as!(
        AccessRuleRow,
        r#"SELECT lane_id, days, start_time, end_time, allowed_modes AS "allowed_modes!"
           FROM lane_access_rules
           WHERE lane_id = ANY($1)"#,
        &lane_ids,
    )
    .fetch_all(pool)
    .await?;

    let mut lanes = Vec::with_capacity(lane_rows.len());
    for row in lane_rows {
        let lane_type = LaneType::from_db_str(&row.lane_type)
            .ok_or_else(|| anyhow::anyhow!("unknown lane_type value: {}", row.lane_type))?;
        let direction = LaneDirection::from_db_str(&row.direction)
            .ok_or_else(|| anyhow::anyhow!("unknown direction value: {}", row.direction))?;

        let mut access_rules = Vec::new();
        for rule_row in rule_rows.iter().filter(|r| r.lane_id == row.id) {
            let mut allowed_modes = Vec::with_capacity(rule_row.allowed_modes.len());
            for mode_str in &rule_row.allowed_modes {
                let mode = crate::corridor_design::lanes::AccessMode::from_db_str(mode_str)
                    .ok_or_else(|| anyhow::anyhow!("unknown access mode value: {mode_str}"))?;
                allowed_modes.push(mode);
            }
            let time_window = match (&rule_row.days, rule_row.start_time, rule_row.end_time) {
                (Some(days), Some(start_time), Some(end_time)) => {
                    Some(crate::corridor_design::lanes::TimeWindow {
                        days: days.clone(),
                        start_time,
                        end_time,
                    })
                }
                _ => None,
            };
            access_rules.push(TimedAccessRule {
                time_window,
                allowed_modes,
            });
        }

        lanes.push(Lane {
            id: LaneId::from(row.id),
            cross_section_id: CrossSectionId::from(row.cross_section_id),
            position: row.position,
            lane_type,
            width_meters: row.width_meters,
            direction,
            access_rules,
        });
    }

    Ok(lanes)
}
```

- [ ] **Step 3: Update the existing tests to seed a remix and pass `remix_id`**

In `crates/core/src/corridor_design/repository.rs`'s test module, add a shared seeding helper near the top of `mod tests` (after the existing `use` lines, before `sample_normalized`):

```rust
    /// Seeds a region (with a bounding box) and a remix in it, returning the
    /// remix id. Every REQ-001/002 test needs a remix to scope its corridor to.
    async fn seed_remix(pool: &sqlx::PgPool) -> RemixId {
        sqlx::query(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon) \
             VALUES (1, 'Test Region', 'UTC', 45.40, -73.70, 45.60, -73.50) \
             ON CONFLICT (id) DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();

        let remix_id: i64 =
            sqlx::query_scalar("INSERT INTO remixes (name, region_id) VALUES ($1, 1) RETURNING id")
                .bind("Test Remix")
                .fetch_one(pool)
                .await
                .unwrap();
        RemixId::from(remix_id)
    }
```

Update every call site of `insert_corridor`/`start_manual_corridor` in this test module to seed a remix first and pass its id as the second argument:

- `insert_corridor_persists_ordered_cross_sections`: add `let remix_id = seed_remix(&db.pool).await;` after `let normalized = sample_normalized();`, then change the `insert_corridor(&db.pool, "Test Corridor A", ...)` call to `insert_corridor(&db.pool, remix_id, "Test Corridor A", ...)`.
- `insert_corridor_stores_osm_attribution_and_import_format`: same pattern, `insert_corridor(&db.pool, remix_id, "Test Corridor B", ...)`.
- `manual_trace_start_add_points_and_finalize_persists_ordered_cross_sections`: add `let remix_id = seed_remix(&db.pool).await;` before `let corridor_id = start_manual_corridor(...)`, change the call to `start_manual_corridor(&db.pool, remix_id, "5th Ave Transit Priority")`.
- `manual_and_imported_cross_sections_have_identical_shape`: add `let remix_id = seed_remix(&db.pool).await;` once near the top (shared by both the manual and imported corridor creation calls in this test), change `start_manual_corridor(&db.pool, remix_id, "CORR-MANUAL")` and `insert_corridor(&db.pool, remix_id, "CORR-IMPORTED", ...)`.

Also update the two `CrossSectionRow` query strings (in `insert_corridor_persists_ordered_cross_sections` and `manual_trace_start_add_points_and_finalize_persists_ordered_cross_sections`) to cast `position` explicitly, matching Task 2's type fix:
```rust
"SELECT position::float8 AS position, lat, lon FROM cross_sections WHERE corridor_id = $1 ORDER BY position"
```
(was `"SELECT position, lat, lon FROM ..."` without the cast — `CrossSectionRow.position` is now `f64`, decoding a plain `NUMERIC` column into `f64` without a cast fails; the explicit `::float8` cast fixes it, matching the pattern `fetch_full_cross_section_row` already uses further down in this same file).

Also update this test's assertions from `positions, vec![0, 1, 2]` (integer literals) to `positions, vec![0.0, 1.0, 2.0]` (float literals) in both tests, matching the new `f64` type.

Add `use crate::ids::RemixId;` to the test module's imports if not already present via the outer `use super::*;` (it is, via the production code's own `use crate::ids::{CorridorId, CrossSectionId, LaneId, RemixId};`).

- [ ] **Step 4: Write new tests for the lane persistence functions**

Add to the end of the test module:

```rust
    // --- Lane persistence ---

    fn sample_lane_drafts() -> Vec<LaneDraft> {
        vec![
            LaneDraft {
                lane_type: LaneType::Sidewalk,
                width_meters: 1.8,
                direction: LaneDirection::None,
                access_rules: vec![crate::corridor_design::lanes::default_access_rule_for(
                    LaneType::Sidewalk,
                )],
            },
            LaneDraft {
                lane_type: LaneType::Travel,
                width_meters: 3.0,
                direction: LaneDirection::Forward,
                access_rules: vec![crate::corridor_design::lanes::default_access_rule_for(
                    LaneType::Travel,
                )],
            },
        ]
    }

    async fn seed_bare_cross_section(pool: &sqlx::PgPool, remix_id: RemixId) -> CrossSectionId {
        let corridor_id = start_manual_corridor(pool, remix_id, "Lane Test Corridor")
            .await
            .unwrap();
        insert_cross_section(pool, corridor_id, Coordinate::new(45.50, -73.60), 0)
            .await
            .unwrap()
    }

    #[tokio::test]
    async fn insert_lanes_for_cross_section_persists_in_order() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;

        let lane_ids = insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
            .await
            .expect("insert_lanes_for_cross_section should succeed");

        assert_eq!(lane_ids.len(), 2);

        let lanes = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .expect("get_lanes_for_cross_section should succeed");

        assert_eq!(lanes.len(), 2);
        assert_eq!(lanes[0].lane_type, LaneType::Sidewalk);
        assert_eq!(lanes[0].width_meters, 1.8);
        assert_eq!(lanes[0].direction, LaneDirection::None);
        assert_eq!(lanes[1].lane_type, LaneType::Travel);
        assert_eq!(lanes[1].width_meters, 3.0);
        assert_eq!(lanes[1].direction, LaneDirection::Forward);
        assert!(lanes[0].position < lanes[1].position);
    }

    #[tokio::test]
    async fn insert_lanes_for_cross_section_persists_default_access_rules() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;

        insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
            .await
            .unwrap();

        let lanes = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .unwrap();

        let travel_lane = lanes.iter().find(|l| l.lane_type == LaneType::Travel).unwrap();
        assert_eq!(travel_lane.access_rules.len(), 1);
        assert_eq!(travel_lane.access_rules[0].time_window, None);
        assert_eq!(
            travel_lane.access_rules[0].allowed_modes,
            vec![
                crate::corridor_design::lanes::AccessMode::Car,
                crate::corridor_design::lanes::AccessMode::Emergency
            ]
        );
    }

    #[tokio::test]
    async fn insert_lanes_for_cross_section_persists_time_windowed_rule() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;

        let bat_lane = LaneDraft {
            lane_type: LaneType::Transit,
            width_meters: 3.2,
            direction: LaneDirection::Forward,
            access_rules: vec![TimedAccessRule {
                time_window: Some(crate::corridor_design::lanes::TimeWindow {
                    days: "weekdays".to_string(),
                    start_time: chrono::NaiveTime::from_hms_opt(7, 0, 0).unwrap(),
                    end_time: chrono::NaiveTime::from_hms_opt(9, 0, 0).unwrap(),
                }),
                allowed_modes: vec![
                    crate::corridor_design::lanes::AccessMode::Transit,
                    crate::corridor_design::lanes::AccessMode::Car,
                ],
            }],
        };

        insert_lanes_for_cross_section(&db.pool, cross_section_id, &[bat_lane])
            .await
            .unwrap();

        let lanes = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .unwrap();

        assert_eq!(lanes.len(), 1);
        assert_eq!(lanes[0].access_rules.len(), 1);
        let window = lanes[0].access_rules[0]
            .time_window
            .as_ref()
            .expect("time window should round-trip");
        assert_eq!(window.days, "weekdays");
        assert_eq!(window.start_time, chrono::NaiveTime::from_hms_opt(7, 0, 0).unwrap());
        assert_eq!(window.end_time, chrono::NaiveTime::from_hms_opt(9, 0, 0).unwrap());
    }

    #[tokio::test]
    async fn get_lanes_for_cross_section_returns_empty_for_a_cross_section_with_no_lanes() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;

        let lanes = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .unwrap();

        assert_eq!(lanes.len(), 0);
    }
```

Add the necessary imports to the top of the test module (alongside the existing `use super::*;`):
```rust
    use crate::corridor_design::lanes::{LaneDirection, LaneDraft, LaneType, TimedAccessRule};
```

- [ ] **Step 5: Run all the tests in this file**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core corridor_design::repository::tests`
Expected: all PASS. This includes the pre-existing REQ-004/005/006 tests further down in the file (`add_cross_section_*`, `reorder_cross_sections_*`, `update_cross_section_label_*`) — those still call `unimplemented!()` functions this task doesn't touch, so they still fail; confirm they fail with `unimplemented!()` panics (unchanged from before this task), not a new/different failure.

- [ ] **Step 6: Commit**

```bash
git add crates/core/src/corridor_design/repository.rs
git commit -m "feat(corridor-design): implement remix-scoped corridor/cross-section persistence and lane persistence"
```

---

## Task 7: New remix-scoped JSON API for manual trace

**Files:**
- Create: `crates/server/src/web/corridor_api.rs`
- Modify: `crates/server/src/web/mod.rs`

**Interfaces:**
- Consumes: `mobilispect_core::corridor_design::{geometry, repository}` (Tasks 5-6), `mobilispect_core::ids::{RemixId, CorridorId}`.
- Produces: `POST /api/remixes/:remix_id/corridors/manual`, `POST /api/corridors/:corridor_id/points`, `POST /api/corridors/:corridor_id/finish`. (An earlier draft of this line also advertised `DELETE /api/corridors/:corridor_id/points/last`, but neither this task's own Steps nor Task 9's Consumes line ever implement or call it — corrected to match what the task actually builds.)

- [ ] **Step 1: Write the failing tests**

Create `crates/server/src/web/corridor_api.rs`:

```rust
//! JSON API for corridor creation (manual trace). See
//! `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`.

use axum::Json;
use axum::extract::{Path, State};
use axum::http::StatusCode;

use mobilispect_core::corridor_design::{Coordinate, geometry, repository};
use mobilispect_core::ids::{CorridorId, RemixId};

use crate::web::AppState;

type ApiError = (StatusCode, Json<serde_json::Value>);

fn internal_error(context: &str, err: anyhow::Error) -> ApiError {
    tracing::error!(error = %err, "{context}");
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        Json(serde_json::json!({ "error": "Internal Server Error" })),
    )
}

fn bad_request(message: &str) -> ApiError {
    (
        StatusCode::BAD_REQUEST,
        Json(serde_json::json!({ "error": message })),
    )
}

#[derive(Debug, serde::Deserialize)]
pub struct StartManualCorridorRequest {
    pub name: String,
}

#[derive(Debug, serde::Serialize)]
pub struct StartManualCorridorResponse {
    pub id: i64,
}

/// `POST /api/remixes/:remix_id/corridors/manual` — starts a new in-progress
/// manual trace, scoped to the given remix.
pub async fn start_manual_corridor(
    State(state): State<AppState>,
    Path(remix_id): Path<i64>,
    Json(req): Json<StartManualCorridorRequest>,
) -> Result<(StatusCode, Json<StartManualCorridorResponse>), ApiError> {
    if req.name.trim().is_empty() {
        return Err(bad_request("name must not be blank"));
    }

    let corridor_id = repository::start_manual_corridor(
        &state.db.pool,
        RemixId::from(remix_id),
        req.name.trim(),
    )
    .await
    .map_err(|e| internal_error("start_manual_corridor", e))?;

    Ok((
        StatusCode::CREATED,
        Json(StartManualCorridorResponse {
            id: corridor_id.as_i64(),
        }),
    ))
}

#[derive(Debug, serde::Deserialize)]
pub struct AddManualPointRequest {
    pub lat: f64,
    pub lon: f64,
}

#[derive(Debug, serde::Serialize)]
pub struct CrossSectionResponse {
    pub id: i64,
    pub position: f64,
    pub lat: f64,
    pub lon: f64,
}

/// `POST /api/corridors/:corridor_id/points` — validates and persists the next
/// point in an in-progress manual trace.
pub async fn add_manual_point(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
    Json(req): Json<AddManualPointRequest>,
) -> Result<(StatusCode, Json<CrossSectionResponse>), ApiError> {
    let coordinate = Coordinate::new(req.lat, req.lon);
    if !coordinate.is_valid() {
        return Err(bad_request("lat/lon is outside valid WGS84 range"));
    }

    let corridor_id = CorridorId::from(corridor_id);
    let existing = repository::get_corridor_cross_sections(&state.db.pool, corridor_id)
        .await
        .map_err(|e| internal_error("add_manual_point: get_corridor_cross_sections", e))?;
    let existing_coordinates: Vec<Coordinate> = existing
        .iter()
        .map(|cs| Coordinate::new(cs.lat, cs.lon))
        .collect();

    if let Err(e) = geometry::validate_next_point(&existing_coordinates, coordinate) {
        return Err(bad_request(&e.to_string()));
    }

    let position = geometry::next_position(&existing_coordinates);
    let cross_section_id =
        repository::insert_cross_section(&state.db.pool, corridor_id, coordinate, position)
            .await
            .map_err(|e| internal_error("add_manual_point: insert_cross_section", e))?;

    Ok((
        StatusCode::CREATED,
        Json(CrossSectionResponse {
            id: cross_section_id.as_i64(),
            position: f64::from(position),
            lat: req.lat,
            lon: req.lon,
        }),
    ))
}

#[derive(Debug, serde::Serialize)]
pub struct FinishManualCorridorResponse {
    pub id: i64,
    pub cross_section_count: i64,
}

/// `POST /api/corridors/:corridor_id/finish` — finalizes an in-progress manual
/// trace, rejecting the request if fewer than the minimum number of points have
/// been placed.
pub async fn finish_manual_corridor(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
) -> Result<Json<FinishManualCorridorResponse>, ApiError> {
    let corridor_id = CorridorId::from(corridor_id);
    let existing = repository::get_corridor_cross_sections(&state.db.pool, corridor_id)
        .await
        .map_err(|e| internal_error("finish_manual_corridor: get_corridor_cross_sections", e))?;
    let existing_coordinates: Vec<Coordinate> = existing
        .iter()
        .map(|cs| Coordinate::new(cs.lat, cs.lon))
        .collect();

    if let Err(e) = geometry::validate_finishable(&existing_coordinates) {
        return Err(bad_request(&e.to_string()));
    }

    repository::finalize_corridor(&state.db.pool, corridor_id)
        .await
        .map_err(|e| internal_error("finish_manual_corridor: finalize_corridor", e))?;

    Ok(Json(FinishManualCorridorResponse {
        id: corridor_id.as_i64(),
        cross_section_count: existing.len() as i64,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::web::SetupState;
    use mobilispect_core::config::Config;
    use mobilispect_core::db::test_utils;
    use std::sync::Arc;
    use tokio::sync::RwLock;

    fn test_config() -> Config {
        Config {
            database_url: String::new(),
            poll_interval_secs: 30,
            bind_address: "0.0.0.0:3000".to_string(),
            on_time_early_threshold_secs: -60,
            on_time_late_threshold_secs: 300,
            retention_days: 30,
            worker_health_bind_address: "0.0.0.0:9090".to_string(),
            transitland_api_key: None,
        }
    }

    async fn test_state() -> (AppState, test_utils::TestDb) {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db.clone(),
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        (state, td)
    }

    async fn seed_remix(state: &AppState) -> i64 {
        sqlx::query(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon) \
             VALUES (1, 'Test Region', 'UTC', 45.40, -73.70, 45.60, -73.50) \
             ON CONFLICT (id) DO NOTHING",
        )
        .execute(&state.db.pool)
        .await
        .unwrap();
        sqlx::query_scalar("INSERT INTO remixes (name, region_id) VALUES ('Test Remix', 1) RETURNING id")
            .fetch_one(&state.db.pool)
            .await
            .unwrap()
    }

    #[tokio::test]
    async fn start_manual_corridor_with_blank_name_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = start_manual_corridor(
            State(state),
            Path(remix_id),
            Json(StartManualCorridorRequest {
                name: "   ".to_string(),
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn start_manual_corridor_happy_path_returns_201() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = start_manual_corridor(
            State(state),
            Path(remix_id),
            Json(StartManualCorridorRequest {
                name: "5th Ave".to_string(),
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0, StatusCode::CREATED);
        assert!(response.1.id > 0);
    }

    #[tokio::test]
    async fn add_manual_point_with_invalid_coordinate_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            RemixId::from(remix_id),
            "Test Corridor",
        )
        .await
        .unwrap();

        let response = add_manual_point(
            State(state),
            Path(corridor_id.as_i64()),
            Json(AddManualPointRequest {
                lat: 200.0,
                lon: -73.6,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn add_manual_point_too_close_to_previous_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            RemixId::from(remix_id),
            "Test Corridor",
        )
        .await
        .unwrap();

        add_manual_point(
            State(state.clone()),
            Path(corridor_id.as_i64()),
            Json(AddManualPointRequest {
                lat: 45.5017,
                lon: -73.5673,
            }),
        )
        .await
        .unwrap();

        let response = add_manual_point(
            State(state),
            Path(corridor_id.as_i64()),
            Json(AddManualPointRequest {
                lat: 45.5017,
                lon: -73.5673,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn finish_manual_corridor_with_fewer_than_two_points_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            RemixId::from(remix_id),
            "Test Corridor",
        )
        .await
        .unwrap();

        let response = finish_manual_corridor(State(state), Path(corridor_id.as_i64())).await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn finish_manual_corridor_happy_path_returns_correct_count() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            RemixId::from(remix_id),
            "Test Corridor",
        )
        .await
        .unwrap();

        add_manual_point(
            State(state.clone()),
            Path(corridor_id.as_i64()),
            Json(AddManualPointRequest {
                lat: 45.5017,
                lon: -73.5673,
            }),
        )
        .await
        .unwrap();
        add_manual_point(
            State(state.clone()),
            Path(corridor_id.as_i64()),
            Json(AddManualPointRequest {
                lat: 45.5031,
                lon: -73.5661,
            }),
        )
        .await
        .unwrap();

        let response = finish_manual_corridor(State(state), Path(corridor_id.as_i64()))
            .await
            .unwrap();

        assert_eq!(response.0.id, corridor_id.as_i64());
        assert_eq!(response.0.cross_section_count, 2);
    }
}
```

Note: `AppState` must be `Clone` for `state.clone()` used in the multi-point tests above — confirm this by checking `crates/server/src/web/mod.rs`'s `AppState` derive; it already has `#[derive(Clone)]` (used throughout the existing `remix_api.rs` tests too), so no change needed there.

- [ ] **Step 2: Register the module and routes**

In `crates/server/src/web/mod.rs`, add the module declaration:
```rust
mod corridor_api;
```

Add these routes to `build_router`, after the existing `/api/remixes/:remix_id/corridors` route and before `.nest_service("/builder", ...)`:
```rust
        .route(
            "/api/remixes/:remix_id/corridors/manual",
            post(corridor_api::start_manual_corridor),
        )
        .route(
            "/api/corridors/:corridor_id/points",
            post(corridor_api::add_manual_point),
        )
        .route(
            "/api/corridors/:corridor_id/finish",
            post(corridor_api::finish_manual_corridor),
        )
```

- [ ] **Step 3: Run the tests**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-server corridor_api::tests`
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add crates/server/src/web/corridor_api.rs crates/server/src/web/mod.rs
git commit -m "feat(corridor-design): add remix-scoped JSON API for manual corridor tracing"
```

---

## Task 8: E2E specs for manual trace (written first, failing)

**Files:**
- Create: `e2e/tests/builder-manual-trace.spec.ts`

**Interfaces:**
- Consumes: a running `mobilispect-server` on `localhost:3000` with a region seeded (`ensureRegionHasBoundingBox()` from `e2e/tests/helpers/db.ts`, already built).

- [ ] **Step 1: Write the spec**

```typescript
import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

/**
 * Corridor Design — manual trace flow (see
 * docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md,
 * "Manual trace (REQ-002)"). Written before the WASM UI for it exists (Task 9),
 * so it fails today for the correct reason, matching this repo's established
 * precedent.
 */

let remixId: number;

test.beforeAll(async () => {
  await ensureRegionHasBoundingBox();
  await withDb(async (client) => {
    const result = await client.query(
      `INSERT INTO remixes (name, region_id) VALUES ('Manual Trace Test Remix', 1) RETURNING id`
    );
    remixId = result.rows[0].id;
  });
});

test.afterAll(async () => {
  await withDb(async (client) => {
    await client.query(`DELETE FROM cross_sections WHERE corridor_id IN (SELECT id FROM corridors WHERE remix_id = $1)`, [remixId]);
    await client.query(`DELETE FROM corridors WHERE remix_id = $1`, [remixId]);
    await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
  });
});

test.describe('Corridor Design: manual trace', () => {
  test('tracing a corridor by clicking the map persists it and navigates to its editor page', async ({
    page,
  }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForSelector('.maplibregl-canvas');

    await page.getByRole('button', { name: 'Add corridor' }).click();
    await page.getByRole('button', { name: 'Manual trace' }).click();
    await page.getByLabel('Corridor name').fill('Test Traced Corridor');
    await page.getByRole('button', { name: 'Start tracing' }).click();

    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);
    const canvas = page.locator('.maplibregl-canvas');
    await canvas.click({ position: { x: 300, y: 200 } });
    await canvas.click({ position: { x: 320, y: 220 } });
    await canvas.click({ position: { x: 340, y: 240 } });

    await page.getByRole('button', { name: 'Finish trace' }).click();

    await expect(page).toHaveURL(new RegExp(`/builder/remix/${remixId}/corridor/\\d+$`));
    await expect(page.getByText('editor coming soon')).toBeVisible();
  });

  test('finishing a trace with fewer than two points shows an error and stays on the trace screen', async ({
    page,
  }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForSelector('.maplibregl-canvas');

    await page.getByRole('button', { name: 'Add corridor' }).click();
    await page.getByRole('button', { name: 'Manual trace' }).click();
    await page.getByLabel('Corridor name').fill('Too Short Corridor');
    await page.getByRole('button', { name: 'Start tracing' }).click();

    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);
    await page.locator('.maplibregl-canvas').click({ position: { x: 300, y: 200 } });

    await page.getByRole('button', { name: 'Finish trace' }).click();

    await expect(page.getByText('not enough points')).toBeVisible();
  });
});
```

- [ ] **Step 2: Confirm it fails for the right reason**

Run (with the dev server running, per previous plans' established setup):
```bash
cd e2e && npx playwright test builder-manual-trace --project=chromium --list
npx playwright test builder-manual-trace --project=chromium
```
Expected: discovered with no parse errors; fails because `getByRole('button', { name: 'Add corridor' })` never appears (the region-map page has no such button yet — Task 9 adds it).

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/builder-manual-trace.spec.ts
git commit -m "test(corridor-design): add failing E2E spec for the manual trace flow"
```

---

## Task 9: WASM UI — "Add corridor" entry point and manual trace flow

**Files:**
- Create: `crates/corridor_builder_web/src/api.rs` (modify — add new functions)
- Create: `crates/corridor_builder_web/src/pages/manual_trace.rs`
- Modify: `crates/corridor_builder_web/src/pages/region_map.rs`
- Modify: `crates/corridor_builder_web/src/app.rs`
- Modify: `crates/corridor_builder_web/src/pages/mod.rs`

**Interfaces:**
- Consumes: `POST /api/remixes/:remix_id/corridors/manual`, `POST /api/corridors/:corridor_id/points`, `POST /api/corridors/:corridor_id/finish` (Task 7); `app::Route`, `maplibre::Map`, `feature_support::webgl_is_supported` (existing, from the WASM shell).
- Produces: `Route::ManualTrace { remix_id: i64, corridor_id: i64 }` (new route variant); an "Add corridor" button on the region-map page.

- [ ] **Step 1: Add API client functions**

In `crates/corridor_builder_web/src/api.rs`, add (after the existing functions):

```rust
#[derive(Debug, Clone, Serialize)]
struct StartManualCorridorRequest {
    name: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct StartManualCorridorResponse {
    pub id: i64,
}

pub async fn start_manual_corridor(
    remix_id: i64,
    name: String,
) -> Result<StartManualCorridorResponse, String> {
    let request = gloo_net::http::Request::post(&format!(
        "{API_BASE}/remixes/{remix_id}/corridors/manual"
    ))
    .json(&StartManualCorridorRequest { name })
    .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, Serialize)]
struct AddManualPointRequest {
    lat: f64,
    lon: f64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CrossSectionResponse {
    pub id: i64,
    pub position: f64,
    pub lat: f64,
    pub lon: f64,
}

pub async fn add_manual_point(
    corridor_id: i64,
    lat: f64,
    lon: f64,
) -> Result<CrossSectionResponse, String> {
    let request = gloo_net::http::Request::post(&format!("{API_BASE}/corridors/{corridor_id}/points"))
        .json(&AddManualPointRequest { lat, lon })
        .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, Deserialize)]
pub struct FinishManualCorridorResponse {
    pub id: i64,
    pub cross_section_count: i64,
}

pub async fn finish_manual_corridor(corridor_id: i64) -> Result<FinishManualCorridorResponse, String> {
    let request = gloo_net::http::Request::post(&format!("{API_BASE}/corridors/{corridor_id}/finish"));
    send_and_decode(request).await
}
```

- [ ] **Step 2: Add the `ManualTrace` route**

In `crates/corridor_builder_web/src/app.rs`, add a new variant to the `Route` enum (after `RegionMap`):
```rust
    #[at("/builder/remix/:remix_id/trace")]
    ManualTrace { remix_id: i64 },
```
Add a `use` for the new page and a `switch` match arm:
```rust
use crate::pages::manual_trace::ManualTracePage;
```
```rust
        Route::ManualTrace { remix_id } => html! { <ManualTracePage {remix_id} /> },
```

- [ ] **Step 3: Add the "Add corridor" button to the region map page**

In `crates/corridor_builder_web/src/pages/region_map.rs`, the `RegionMapPage` component's returned `html!` currently ends with:
```rust
            <div id="map" style="width: 100%; height: 100vh;"></div>
        </div>
    }
```
Change it to add a floating "Add corridor" button that opens a small choice menu (Manual trace is real; Import is a placeholder for a later plan):

```rust
            <div id="map" style="width: 100%; height: 100vh;"></div>
            <div style="position:absolute; top:16px; right:16px; z-index:10;">
                if *show_add_menu {
                    <div class="setup-card" style="padding:1rem;">
                        <button class="btn btn-primary" style="display:block; width:100%; margin-bottom:0.5rem;" onclick={on_choose_manual_trace}>{ "Manual trace" }</button>
                        <button class="btn" style="display:block; width:100%;" disabled=true title="Coming soon">{ "Import from OSM" }</button>
                    </div>
                } else {
                    <button class="btn btn-primary" onclick={on_open_add_menu}>{ "Add corridor" }</button>
                }
            </div>
        </div>
    }
```

Add the supporting state/callbacks near the top of the component function, alongside the existing `webgl_ok`/`error` state declarations:
```rust
    let show_add_menu = use_state(|| false);
    let on_open_add_menu = {
        let show_add_menu = show_add_menu.clone();
        Callback::from(move |_: MouseEvent| show_add_menu.set(true))
    };
    let on_choose_manual_trace = {
        let navigator = navigator.clone();
        Callback::from(move |_: MouseEvent| {
            navigator.push(&Route::ManualTrace { remix_id });
        })
    };
```

(`navigator` and `remix_id` are already in scope in this component from the existing code — `navigator` is created via `use_navigator()` near the top of the function, and `remix_id` is `props.remix_id`.)

- [ ] **Step 4: Write the manual trace page**

Create `crates/corridor_builder_web/src/pages/mod.rs` addition — add the new module:
```rust
pub mod manual_trace;
```

Create `crates/corridor_builder_web/src/pages/manual_trace.rs`:

```rust
use wasm_bindgen::prelude::*;
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;
use crate::maplibre::Map;

#[derive(Properties, PartialEq)]
pub struct ManualTracePageProps {
    pub remix_id: i64,
}

#[component]
pub fn ManualTracePage(props: &ManualTracePageProps) -> Html {
    let remix_id = props.remix_id;
    let navigator = use_navigator().expect("BrowserRouter provides a Navigator");
    // Split into two states deliberately: `corridor_id` only ever transitions
    // None -> Some once (when tracing starts), while `point_count` changes on
    // every click. Keeping them separate means the map-mounting effect below
    // can depend on `corridor_id` alone — if a single combined enum carried
    // both, using it as the effect's dependency would remount a brand new
    // MapLibre map (and stack a duplicate click listener) on every point
    // clicked, since the whole enum value changes each time.
    let corridor_id = use_state(|| None::<i64>);
    let point_count = use_state(|| 0usize);
    let error = use_state(|| None::<String>);
    let name_input = use_node_ref();

    let on_start_tracing = {
        let name_input = name_input.clone();
        let corridor_id = corridor_id.clone();
        let error = error.clone();
        Callback::from(move |_: MouseEvent| {
            let name_input = name_input.clone();
            let corridor_id = corridor_id.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                let name = name_input
                    .cast::<web_sys::HtmlInputElement>()
                    .map(|el| el.value())
                    .unwrap_or_default();
                if name.trim().is_empty() {
                    error.set(Some("name must not be blank".to_string()));
                    return;
                }
                match api::start_manual_corridor(remix_id, name).await {
                    Ok(response) => corridor_id.set(Some(response.id)),
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    // Mounts the trace map exactly once, the moment `corridor_id` goes from
    // `None` to `Some(id)`. See the field comment above for why this depends
    // on `corridor_id` specifically, not a combined state enum.
    {
        let point_count = point_count.clone();
        let error = error.clone();
        use_effect_with(*corridor_id, move |corridor_id: &Option<i64>| {
            if let Some(id) = *corridor_id {
                mount_trace_map(id, point_count, error);
            }
            || ()
        });
    }

    let on_finish = {
        let corridor_id = corridor_id.clone();
        let error = error.clone();
        let navigator = navigator.clone();
        Callback::from(move |_: MouseEvent| {
            let Some(id) = *corridor_id else {
                return;
            };
            let error = error.clone();
            let navigator = navigator.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::finish_manual_corridor(id).await {
                    Ok(_) => navigator.push(&Route::Corridor {
                        remix_id,
                        corridor_id: id,
                    }),
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    html! {
        <div class="setup-wrap">
            <div class="setup-card">
                <h1 class="setup-title">{ "Trace a corridor" }</h1>
                if let Some(err) = &*error {
                    <div class="alert alert--err">{ err }</div>
                }
                if corridor_id.is_none() {
                    <div>
                        <label class="field-label" for="trace-name">{ "Corridor name" }</label>
                        <input class="field" id="trace-name" type="text" ref={name_input.clone()} />
                        <button class="btn btn-primary" style="width:100%;margin-top:1rem;" onclick={on_start_tracing}>{ "Start tracing" }</button>
                    </div>
                } else {
                    <div>
                        <p>{ format!("Click the map to place points ({} placed so far, minimum 2).", *point_count) }</p>
                        <button class="btn btn-primary" style="width:100%;margin-top:1rem;" onclick={on_finish}>{ "Finish trace" }</button>
                    </div>
                }
            </div>
            <div id="trace-map" style="width: 100%; height: 100vh; margin-top:1rem;"></div>
        </div>
    }
}

fn mount_trace_map(
    corridor_id: i64,
    point_count: UseStateHandle<usize>,
    error: UseStateHandle<Option<String>>,
) {
    let options = to_js_value(&serde_json::json!({
        "container": "trace-map",
        "style": osm_raster_style(),
        "center": [-73.6, 45.5],
        "zoom": 13,
    }));
    let Ok(options) = options else {
        error.set(Some("failed to build map options".to_string()));
        return;
    };
    let map = Map::new(&options);

    let click_point_count = point_count.clone();
    let click_error = error.clone();
    let onclick = Closure::wrap(Box::new(move |event: JsValue| {
        let Ok(lng_lat) = js_sys::Reflect::get(&event, &"lngLat".into()) else {
            return;
        };
        let Some(lon) = js_sys::Reflect::get(&lng_lat, &"lng".into()).ok().and_then(|v| v.as_f64()) else {
            return;
        };
        let Some(lat) = js_sys::Reflect::get(&lng_lat, &"lat".into()).ok().and_then(|v| v.as_f64()) else {
            return;
        };
        let click_point_count = click_point_count.clone();
        let click_error = click_error.clone();
        wasm_bindgen_futures::spawn_local(async move {
            match api::add_manual_point(corridor_id, lat, lon).await {
                Ok(_) => click_point_count.set(*click_point_count + 1),
                Err(e) => click_error.set(Some(e)),
            }
        });
    }) as Box<dyn FnMut(JsValue)>);
    map.on("click", &onclick);
    onclick.forget();
}

fn to_js_value<T: serde::Serialize>(value: &T) -> Result<JsValue, String> {
    let json = serde_json::to_string(value).map_err(|e| e.to_string())?;
    js_sys::JSON::parse(&json).map_err(|e| format!("{e:?}"))
}

fn osm_raster_style() -> serde_json::Value {
    serde_json::json!({
        "version": 8,
        "sources": {
            "osm": {
                "type": "raster",
                "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                "tileSize": 256,
                "attribution": "© OpenStreetMap contributors"
            }
        },
        "layers": [
            { "id": "osm-tiles", "type": "raster", "source": "osm" }
        ]
    })
}
```

Note: this reuses `map.on("click", &onclick)` (the map-wide, 2-argument overload from `maplibre.rs`, already bound in the WASM shell) — no new `maplibre.rs` binding needed. The click handler here doesn't need `query_rendered_features`-based prioritization (there's nothing to click *on* yet — this map has no corridor layers, just base tiles — the click's `lngLat` is read directly off the event object).

- [ ] **Step 5: Build and verify against the E2E spec**

```bash
cd crates/corridor_builder_web && trunk build && cd ../..
dotenvx run -- cargo run --bin mobilispect-server &
cd e2e && npx playwright test builder-manual-trace --project=chromium
```
Expected: both tests in `builder-manual-trace.spec.ts` pass. Also re-run the full `builder-*.spec.ts` suite to confirm no regressions: `npx playwright test builder- --project=chromium`.

- [ ] **Step 6: Commit**

```bash
git add crates/corridor_builder_web/src
git commit -m "feat(corridor-design): add manual trace UI, wired to the new corridor-creation API"
```

---

## Task 10: Full verification pass

**Files:** none (verification only).

- [ ] **Step 1: Rust workspace**

```bash
cargo build --workspace
cargo clippy -p mobilispect-core -p mobilispect-server --all-targets -- -D warnings
cargo fmt --all -- --check
DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core -p mobilispect-server --no-fail-fast
```
Expected: all succeed on the workspace crates this plan touches. As established by the WASM shell plan's own verification task, `cargo clippy --workspace`/`cargo nextest run --workspace` (unscoped) will still show pre-existing, unrelated failures in `crates/worker/` — confirmed out of scope, not this plan's concern.

- [ ] **Step 2: WASM crate**

```bash
cd crates/corridor_builder_web
cargo fmt --check
cargo clippy --target wasm32-unknown-unknown -- -D warnings
trunk build
```
Expected: all clean.

- [ ] **Step 3: Full E2E suite**

```bash
cd crates/corridor_builder_web && trunk build && cd ../..
dotenvx run -- cargo run --bin mobilispect-server &
cd e2e && npx playwright test
```
Expected: all `builder-*.spec.ts` files pass across chromium/firefox/webkit, including the new `builder-manual-trace.spec.ts`. Confirm the deleted `req-001-import.spec.ts`/`req-002-manual-trace.spec.ts`/`req-005-reorder.spec.ts`/`graceful-degradation.spec.ts`/`feature-detection.spec.ts` no longer appear in the test run at all (they were deleted in Task 1).

- [ ] **Step 4: Scope check**

```bash
git diff $(git merge-base main HEAD) HEAD --stat
```
Confirm the file list matches this plan's tasks (migration 026, `corridor_design/{lanes,geometry,attribution,repository,mod}.rs`, `ids.rs`, `web/{corridor_api,mod}.rs`, `corridor_builder_web/src/{api,app,pages/*}.rs`, `e2e/tests/builder-manual-trace.spec.ts`, plus the five deleted files from Task 1) and nothing unexpected.

No commit for this task — verification only. If anything fails, fix it in the relevant earlier task's files and re-run.

---

## Summary

After all 10 tasks: an analyst can open a remix, click "Add corridor" → "Manual trace" on the region map, name a corridor, click points on a map to trace its path, and finish — landing on the corridor's own page (still a placeholder; a later plan turns it into the lane editor). The corridor persists with an empty lane list per cross-section, ready for that later plan to populate and edit. OSM import (Overpass) is a separate follow-up plan; lane *editing*, cross-section add/reorder, and intersection treatments are separate follow-up plans per `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`.

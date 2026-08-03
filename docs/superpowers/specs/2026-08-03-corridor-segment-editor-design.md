# Corridor & Intersection Editor (Lane-by-Lane, Multi-Modal, Time-Aware)

**Date:** 2026-08-03
**Status:** Approved

## Summary

The WASM shell (`docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md`) lets an analyst create/open a remix and see a region's corridors on a map, but has no way to create a corridor or edit one — both placeholder pages ("Corridor editor coming soon", "Intersection editor coming soon") explicitly deferred that work here. This spec builds it: corridor creation (import from OSM or manual trace), and a **lane-by-lane cross-section editor** — reworking "cross-section" from a labeled point into an actual street cross-section: an ordered, left-to-right arrangement of lanes, each with a type, width, direction, and a (possibly time-windowed, multi-modal) access policy. It also adds intersection-level treatments (bus gates, turn-conflict management) and bus-stop treatments (bulbs, protected platforms), grounded in the CROW Design Manual for Bicycle Traffic and NACTO's Transit Priority Atlas taxonomy.

This supersedes the existing `crates/server/src/web/corridor_design.rs`/`corridor_import.rs` Askama+JSON scaffolding (REQ-001–007, never implemented past `unimplemented!()` stubs) — per the WASM shell design's own confirmed direction, the segment/corridor editor is a WASM rework, not a continuation of that Askama path. Those two files, and their Playwright specs, are removed as part of this work.

**Explicitly out of scope, a separate future spec:** area-wide policies (limited traffic zones, congestion/road pricing) — these apply to a geographic area, not a specific corridor or lane, and don't belong in the Corridor Design bounded context at all.

## Domain Context

- **Bounded context(s):** Corridor Design (existing).
- **Aggregates touched:** `Corridor` (unchanged shape), `CrossSection` (significantly extended: gains `lanes`, `bus_stop`; loses nothing — `label` stays), new `Lane`, new `IntersectionTreatment` (attached to a `CrossSection` that is a corridor endpoint).
- **New ubiquitous language terms:**
  - **Lane** — a single travel way within a cross-section (e.g. one travel lane, one bike lane), with a type, width, direction, and access policy. Ordered left-to-right across the street.
  - **Access policy** — the set of travel modes (car, transit, bicycle, pedestrian, emergency, taxi, freight, HOV) permitted to use a lane, optionally varying by time window (e.g. a part-time bus lane).
  - **Intersection treatment** — a bus-gate, turn-conflict-management, or bus-stop configuration attached to a specific point along a corridor. Bus-gate and turn-conflict treatments only apply to a corridor's endpoint cross-sections (its "intersections", per the WASM shell spec's identifier clarification); bus-stop treatments can attach to any cross-section, since a stop can be mid-block.
  - **Modal filter** (not a distinct data field — an emergent property) — a stretch of corridor where every cross-section's lanes are all transit-type, with no general travel lane.

## Domain Model

```rust
pub enum LaneType {
    Travel, Turn, Transit, QueueJump, CycleLane, CycleTrack,
    Parking, Sidewalk, Median, Buffer,
}

pub enum LaneDirection {
    Forward,   // same direction as the corridor's cross-section sequence (first -> last)
    Backward,  // opposite direction
    Both,      // bidirectional (e.g. a two-way cycle track)
    None,      // undirected (sidewalk, median, buffer)
}

pub enum AccessMode {
    Car, Transit, Bicycle, Pedestrian, Emergency, Taxi, Freight, Hov,
}

/// A lane's access is a set of rules; `time_window: None` means "always active."
/// A lane always has at least one rule (defaulted per `LaneType` if the analyst
/// doesn't override it — see `default_access_for_lane_type`).
pub struct TimedAccessRule {
    pub time_window: Option<TimeWindow>,
    pub allowed_modes: Vec<AccessMode>,
}

pub struct TimeWindow {
    pub days: DaySet,        // e.g. Weekdays, Weekends, or an explicit day set
    pub start_time: NaiveTime,
    pub end_time: NaiveTime,
}

pub struct Lane {
    pub id: LaneId,
    pub cross_section_id: CrossSectionId,
    pub position: f64,       // fractional position, same pattern as cross_sections.position
    pub lane_type: LaneType,
    pub width_meters: f64,
    pub direction: LaneDirection,
    pub access_rules: Vec<TimedAccessRule>,
}

pub enum BusGateType { SignalControlled, YieldControlled }
pub enum TurnConflictType {
    IndirectLeftViaAlternative, IndirectLeftWithinIntersection,
    RightInRightOut, DeadEndLateralStreet,
}
pub enum BusStopTreatment { BusBulb, SignalProtectedPlatform }

/// Only meaningful for a CrossSection that is a corridor endpoint.
pub struct IntersectionTreatment {
    pub cross_section_id: CrossSectionId,
    pub bus_gate: Option<BusGateType>,
    pub turn_conflict: Option<TurnConflictType>,
}
```

`CrossSection` (existing type, `crates/core/src/corridor_design/mod.rs`) gains two fields: `lanes: Vec<Lane>` (loaded separately, ordered by `position`) and `bus_stop: Option<BusStopTreatment>`. `label` is unchanged and still shown as the cross-section's human-friendly name (e.g. "Main St @ 5th Ave"), independent of its lane configuration.

**Default access policy per lane type** (used when a lane is created without an explicit override; an analyst can always add time-windowed rules on top):

| LaneType | Default `TimedAccessRule` |
|---|---|
| Travel | always, `[Car, Emergency]` |
| Turn | always, `[Car, Emergency]` |
| Transit | always, `[Transit, Emergency]` |
| QueueJump | always, `[Transit, Emergency]` |
| CycleLane | always, `[Bicycle]` |
| CycleTrack | always, `[Bicycle]` |
| Parking | always, `[Car]` |
| Sidewalk | always, `[Pedestrian]` |
| Median | always, `[]` (no travel) |
| Buffer | always, `[]` (no travel) |

**Default width per lane type** (used when OSM tags don't specify an explicit width, e.g. no `width:lanes=*` tag — the common case; matches the values already shown in the approved cross-section diagram mockup):

| LaneType | Default width |
|---|---|
| Travel | 3.0 m |
| Turn | 3.0 m |
| Transit | 3.2 m |
| QueueJump | 3.2 m |
| CycleLane | 1.5 m |
| CycleTrack | 2.0 m |
| Parking | 2.0 m |
| Sidewalk | 1.8 m |
| Median | 1.2 m |
| Buffer | 0.6 m |

## How Atlas Categories Compose (no separate treatment aggregate needed for these)

The Transit Priority Atlas's "Linear Continuous Measures" (categories A–G) are expressed entirely through the `Lane` model above, not a new aggregate:

- **Center/edge/offset/curbside transit lanes (B–E)** — a `Transit`-type lane's position in the cross-section's lane sequence.
- **Contraflow (F)** — a lane whose `direction` is `Backward` relative to the corridor's general flow.
- **Queue jumps (G)** — a `QueueJump`-type lane that appears only in the cross-sections near one intersection, not the whole corridor's length.
- **Transit modal filter (N)** — a stretch of corridor where every cross-section's lanes exclude `Travel`/general-access types. Not a stored flag; computed from lane composition when needed (e.g. for a future map overlay), consistent with this project's `is_corridor_edited`-style approach of deriving status from data rather than storing redundant flags.

## Corridor Creation

### Import (REQ-001)

Reuses the already-written, already-tested pure logic in `crates/core/src/corridor_design/geometry.rs` (`normalize_corridor_geometry`: CRS normalization, self-intersection detection, disconnected-segment detection) — this logic doesn't change. What's new:

1. **OSM tag → baseline lane parser** (new pure function, `crates/core/src/corridor_design/lanes_from_osm.rs`): takes the imported way's OSM tags (`HashMap<String, String>` — `lanes`, `lanes:forward`, `lanes:backward`, `cycleway`, `cycleway:left`, `cycleway:right`, `sidewalk`, `parking:lane:both`/`:left`/`:right`, `oneway`) and produces an initial `Vec<Lane>` with default widths per type and the default access policy table above. Unrecognized/absent tags fall back to a single bidirectional `Travel` lane (the safest baseline when OSM data is sparse) — this fallback and its rationale get called out explicitly in the eventual implementation plan, not left implicit.
2. Every imported corridor's cross-sections get this derived `Vec<Lane>` at creation time. The analyst edits from there.
3. **We do not adopt osm2streets or osm2lanes as a dependency** — osm2lanes is archived/unmaintained; osm2streets targets full street-network reconstruction (intersection polygons, dual-carriageway merging) from `.osm.xml` extracts, a heavier and differently-shaped problem than deriving one selected way's lane list from its tags, and its own docs describe the API as not yet stable. The hand-written parser above is narrower, fully within our control, and easier to test exhaustively against the specific tag vocabulary above.

### Manual trace (REQ-002)

Reuses the existing corridor/cross-section creation repository functions' *intent* (a corridor started empty, points added one at a time) but the interaction moves from a canvas-based click-to-trace (the original Askama-era design) to clicking directly on the already-built MapLibre map in `corridor_builder_web` — the same map instance the region-overview page already renders. Manually traced cross-sections start with no lanes (nothing to derive from); the analyst builds the lane arrangement from scratch via the editor below.

### OSM attribution (REQ-003)

Unchanged in spirit from the original REQ-003: an attribution strip shows whenever a corridor (or any of its cross-sections) traces back to imported OSM geometry. The existing `crates/core/src/corridor_design/attribution.rs` (`attribution_visible`) pure function is reused as-is.

## Cross-Section Sequence (REQ-004/005)

Unchanged from the original scope: adding a cross-section at a fractional position between two existing ones, and reordering the sequence, are both about the corridor's ordered list of *points* — nothing about the lane-by-lane redesign changes this. The existing `crates/core/src/corridor_design/position.rs` (`assign_position`, `compute_reordered_positions`) is reused as-is. A cross-section added via REQ-004 always starts with no lanes, the same as a manually-traced point — deriving lanes for an inserted mid-corridor point would require interpolating which OSM way segment it falls on and isn't designed here; the analyst builds its lane arrangement from scratch via the editor below, same as any manually-traced cross-section.

## Cross-Section Editing (REQ-006, redefined)

Replaces the original REQ-006 (edit a text label) with lane-by-lane editing. UI: the visual, to-scale cross-section diagram (colored by lane type, proportional to width) the brainstorming settled on. Clicking a lane opens a side panel (not a small popover — the time-windowed access-rule list needs room) showing type, width, direction, and the access-rule list (defaulted, with "+ add time window" to override for special treatments). A "+" control in every gap between lanes (and at both ends) inserts a new lane at that position; each lane has a remove control. `label` remains editable too (the existing `validate_label`/isolation-guarantee logic from `crates/core/src/corridor_design/edit.rs` is reused for that specific field), but is no longer the entirety of what "editing a cross-section" means.

## Intersection Treatments (new)

The WASM shell already routes to `/builder/remix/:remix_id/intersection/:cross_section_id` for a corridor's endpoint cross-sections. This spec replaces that placeholder with a real page: a simple form with three optional selects (bus gate, turn-conflict type) plus, since bus stops aren't endpoint-only, a bus-stop select that's available from *any* cross-section's edit panel (part of the lane-editing side panel, not the intersection-only page).

## Data Model / Migrations

New migration (next sequential number after 025):

```sql
-- migrations/026_corridor_lanes_and_treatments.sql

CREATE TABLE lanes (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cross_section_id  BIGINT NOT NULL REFERENCES cross_sections(id) ON DELETE CASCADE,
    position          NUMERIC NOT NULL,  -- fractional, same pattern as cross_sections.position
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
    -- NULL day/start/end = always active (the default rule).
    days           TEXT,             -- e.g. 'weekdays', 'weekends', or a comma-separated day set
    start_time     TIME,
    end_time       TIME,
    allowed_modes  TEXT[] NOT NULL   -- array of 'car'|'transit'|'bicycle'|'pedestrian'
                                     --        |'emergency'|'taxi'|'freight'|'hov'
);

CREATE INDEX idx_lane_access_rules_lane ON lane_access_rules (lane_id);

ALTER TABLE cross_sections
    ADD COLUMN bus_stop TEXT CHECK (bus_stop IN ('bus_bulb', 'signal_protected_platform'));

CREATE TABLE intersection_treatments (
    cross_section_id  BIGINT PRIMARY KEY REFERENCES cross_sections(id) ON DELETE CASCADE,
    bus_gate          TEXT CHECK (bus_gate IN ('signal_controlled', 'yield_controlled')),
    turn_conflict     TEXT CHECK (turn_conflict IN (
                          'indirect_left_via_alternative', 'indirect_left_within_intersection',
                          'right_in_right_out', 'dead_end_lateral_street'
                      ))
);
```

`intersection_treatments` is a separate table (one row per treated cross-section, both fields optional) rather than columns directly on `cross_sections`, since most cross-sections will never have one — keeps the common case's row narrow. `bus_stop` goes directly on `cross_sections` since it's a single nullable enum, not worth a whole table.

This touches `crates/core/migrations/` — per this project's Safety Rules, called out explicitly here and again in the implementation plan.

## Removing the Superseded Askama Scaffolding

`crates/server/src/web/corridor_design.rs`, `crates/server/src/web/corridor_import.rs`, and their Playwright specs (`e2e/tests/req-001-import.spec.ts`, `req-002-manual-trace.spec.ts`, `req-005-reorder.spec.ts`, `graceful-degradation.spec.ts`, `feature-detection.spec.ts`) are removed as part of this work's implementation plan — they were never wired into `build_router` and contain only `unimplemented!()` stubs, so nothing currently depends on them. `crates/core/src/corridor_design/{geometry,position,edit,attribution}.rs` and their existing tests are **kept and reused** (the pure logic, not the Askama layer).

## Testing

Same conventions as the rest of this codebase: pure logic (OSM-tag lane derivation, access-rule defaulting, lane insertion/reordering) gets plain unit tests; repository functions get integration tests via testcontainers; the WASM UI (diagram rendering, side-panel editing, intersection form) gets Playwright E2E specs extending the existing `e2e/` suite, following the same pattern established for the WASM shell (click-priority hit-testing via MapLibre's `queryRenderedFeatures`, `window.__corridorBuilderMap` test hooks where needed).

## Out of Scope

- Area-wide policies (limited traffic zones, congestion/road pricing) — a separate future spec, different bounded context.
- Time-window UI polish beyond a functional "add a rule" list (e.g. a visual weekly schedule grid) — the data model supports it; the first version's UI doesn't need to be fancy about it.
- Any change to the WASM shell's remix/region-map/click-routing behavior (unchanged, already shipped).

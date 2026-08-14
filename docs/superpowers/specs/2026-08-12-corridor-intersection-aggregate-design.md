# Corridor Design: Intersection Aggregate (osm2streets-Inspired)

**Date:** 2026-08-12
**Status:** Draft — pending user review

## Domain Context

- **Bounded context(s):** Corridor Design
- **Aggregates touched:** `Corridor`, `CrossSection` (both modified); `Intersection`, `TurnMovement` (both new)
- **New ubiquitous language terms:**
  - **Intersection** — a shared point where one or more corridors meet, holding its own geometry, optional OSM node identity, bus-gate/turn-conflict/bus-stop treatment, and turn movements. Replaces the prior "a corridor's first/last cross-section stands in for its endpoint" convention.
  - **Turn Movement** — a legal source-lane → destination-lane pairing at an `Intersection`, either `Inferred` from OSM turn-lane/destination tags at import or `Manual` (analyst-authored, never overwritten by re-inference).
  - **Split** — the operation that promotes an interior `CrossSection` of an existing corridor into a real `Intersection`, dividing the corridor into two.
  - **Dual-Carriageway Merge** — an automatic, heuristic-driven step at OSM import that collapses two distinct `Intersection`s (each formed from a different OSM node) into one, when they're close together and their originating ways look like a divided road meeting a cross street. Recorded in `intersection_merges` for audit, since the merge is silent and irreversible from the analyst's point of view.

## Background

Corridor Design's current model (`crates/core/src/corridor_design/`) is a `Corridor` owning an ordered sequence of `CrossSection`s, each carrying `Lane`s with `TimedAccessRule`s. There is no separate intersection concept — a corridor's first and last cross-section merely *stand in* for its endpoints (see `docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md`), and corridors are not linked into any shared graph. Each corridor is an independently-persisted sequence.

This gap was explicitly noted rather than solved by the in-flight, unmerged `corridor-lane-editor` worktree (plan: `docs/superpowers/plans/2026-08-10-corridor-intersection-treatments.md`, migration `027_intersection_treatments.sql`), which adds `bus_gate`, `turn_conflict`, and `bus_stop` as metadata hung directly off a `CrossSection` row, explicitly stating "this shell has no separate 'Intersection' aggregate yet."

This design supersedes that approach: it assumes migration 027 lands on `main` first, then this design's migration moves that data into a real, shared `Intersection` aggregate — modeled on osm2streets' Road/Intersection/Lane split (roads/corridors connect at intersections; intersections hold turn movements between lanes). Unlike osm2streets itself, this is **not adopted as a dependency** — see the prior rejection in `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md` (its own API is unstable, and it solves full street-network reconstruction from an OSM extract, a different-shaped problem from this codebase's editable, persisted, per-corridor domain model). Only the architectural shape is borrowed, implemented natively in this codebase's existing vertical-slice / functional-core-imperative-shell conventions.

## Goals

- Replace the implicit "endpoint stands in for intersection" convention with a real, shared `Intersection` aggregate.
- Support corridors sharing an `Intersection` (multiple corridors meeting at one physical junction).
- Support splitting a corridor at an interior cross-section when another corridor's endpoint lands there, so mid-span junctions can become real shared intersections too.
- Model explicit turn movements between lanes at an `Intersection`, inferred from OSM tags at import and editable/overridable by an analyst afterward.
- Auto-match corridors imported from OSM onto a shared `Intersection` via `osm_node_id`; manually-traced corridors always get a private `Intersection` until an analyst explicitly merges them (out of scope for this design — no such merge UI is specified here beyond the split operation).
- Automatically collapse dual-carriageway pairs — two distinct OSM-node-derived `Intersection`s a short distance apart, on oneway ways with matching name/ref, meeting the same cross street — into one shared `Intersection`, via a heuristic pass at import time (see "Dual-Carriageway Merge" in Architecture and Edge Cases below).

## Non-Goals

- A manual "merge these two nearby intersections" UI action for cases the automatic heuristic doesn't catch (only dual-carriageway pairs are auto-merged; general clusters of nearby intersections — e.g. complex multi-way junctions that aren't a simple divided-road-meets-cross-street shape — are not). This is a known gap, not an oversight — see "Edge Cases" below.
- Undoing an automatic dual-carriageway merge. The merge is logged (`intersection_merges`) for audit, but no "split this Intersection back into two" tool is specified here — a false-positive merge must currently be fixed by direct data correction, not a UI action. See Open Points.
- Roundabout-specific turn-movement semantics (yield-on-entry, continuous circulating flow, multi-connection classification). Roundabout *topology* (a ring of short corridor segments linked by `Intersection`s) is representable under this design without special-casing, but no roundabout-aware turn-movement inference is in scope.
- Any change to `crates/worker/` or GTFS-related code — this is entirely within Corridor Design's existing `mobilispect-core`/`mobilispect-server`/`corridor_builder_web` boundary.
- Routing, simulation, intersection crossing-time/delay modeling, or any consumer of turn-movement data beyond Corridor Design's own editor.

## Architecture

Three data-model changes to `crates/core/src/corridor_design/`:

1. **`Intersection`** (new aggregate) — identity, position, the treatment metadata currently planned for cross-sections (`bus_gate`, `turn_conflict`, `bus_stop`), and a set of associated OSM node ids (usually one; more than one after a dual-carriageway merge — see below).
2. **`CrossSection`** (modified) — gains `intersection_id: Option<IntersectionId>`. Populated only for a corridor's first and last cross-section (an "endpoint"); always `None` for interior cross-sections. Every endpoint must reference an `Intersection` — including a corridor with no real junction, which gets its own private, single-corridor `Intersection`.
3. **`TurnMovement`** (new aggregate, owned by `Intersection`) — `{ intersection_id, from_lane_id, to_lane_id, source: Inferred | Manual }`, a legal lane-to-lane pairing between corridors meeting at that intersection.

**Splitting** is the mechanism by which a mid-span junction becomes real: `split_corridor_at_cross_section` turns one interior cross-section into a new shared `Intersection`, dividing its corridor into two (head keeps the original `corridor_id`; tail gets a new one).

**Auto-matching** happens at OSM import time: an endpoint cross-section's `osm_node_id` is looked up against every `Intersection`'s associated node ids — a match links to (shares) that `Intersection`; no match creates a new private one with that single node id.

**Dual-carriageway merge** is a second, heuristic pass that runs immediately after auto-matching, within the same import: it looks for pairs of *distinct* `Intersection`s just created or touched by this import that are close together, each newly linked from a `oneway`-tagged way, where the two ways share a `name` or `ref` tag. A match collapses the pair into one `Intersection`, re-pointing every `cross_sections.intersection_id` that referenced the absorbed one, merging its OSM node ids onto the survivor, and logging the merge to `intersection_merges` for later audit (this design does not include an "undo" tool — see Non-Goals). Because this pass runs on exact-match distance/tag heuristics rather than full geometric reconstruction, it is scoped narrowly (oneway pairs with matching name/ref only) to keep false-positive merges rare; see Edge Cases for the full rationale and Open Points for the unresolved distance-threshold value.

**Turn-movement inference** runs whenever an `Intersection` gains a corridor beyond its first (whether via ordinary auto-matching or as a result of a dual-carriageway merge bringing multiple corridors together at once), pairing the newly-added corridor's lanes against each corridor already present. It parses OSM `turn:lanes`/`destination` tags captured during import into candidate `TurnMovement`s with `source: Inferred`. Analyst edits flip a row to `source: Manual`, which re-inference never overwrites; a dual-carriageway merge that brings previously-separate `Intersection`s' corridors together triggers inference across the newly-combined set the same way, without touching any row already marked `Manual`.

## Components

### `crates/core/src/ids.rs`

New `IntersectionId` newtype — `i64`-backed, `#[sqlx(transparent)]`, following the existing pattern (`AgencyId`, `CrossSectionId`, etc.).

### `crates/core/src/corridor_design/intersection.rs` (new; supersedes the #027 worktree's file of the same name)

```rust
pub struct Intersection {
    pub id: IntersectionId,
    pub lat: f64,
    pub lon: f64,
    pub osm_node_ids: Vec<i64>,  // usually one; more after a dual-carriageway merge
    pub bus_gate: Option<BusGate>,
    pub turn_conflict: Option<TurnConflict>,
    pub bus_stop: Option<BusStop>,
}

// BusGate, TurnConflict, BusStop: same enums and as_db_str/from_db_str shape
// as the #027 worktree defines today, relocated here unchanged.

pub struct TurnMovement {
    pub intersection_id: IntersectionId,
    pub from_lane_id: LaneId,
    pub to_lane_id: LaneId,
    pub source: TurnMovementSource,
}

pub enum TurnMovementSource { Inferred, Manual }

/// A single dual-carriageway merge event, recorded for audit -- this design
/// has no "undo" tool, so this table is the only record of what was folded
/// into `surviving_intersection_id` and why.
pub struct IntersectionMerge {
    pub surviving_intersection_id: IntersectionId,
    pub absorbed_osm_node_ids: Vec<i64>,
    pub treatment_conflict: bool,
    pub merged_at: chrono::DateTime<chrono::Utc>,
}
```

### `crates/core/src/corridor_design/mod.rs` (modified)

`CrossSection` gains `pub intersection_id: Option<IntersectionId>`.

### `crates/core/src/corridor_design/splitting.rs` (new)

Pure function computing the head/tail partition of a corridor's cross-sections and lanes at a given split point, and the new `Intersection`'s attributes — no I/O, matching this module's existing `position.rs`/`geometry.rs` pattern. A thin repository function executes the computed change as one transaction.

### `crates/core/src/corridor_design/turn_inference.rs` (new)

Pure function `infer_turn_movements(tags, corridor_a_lanes, corridor_b_lanes) -> Vec<TurnMovement>`, consuming captured OSM turn-lane/destination tag strings plus both corridors' already-persisted lanes. Unrecognized/malformed tag values are skipped, never panic.

### `crates/core/src/corridor_design/dual_carriageway.rs` (new)

Pure function `detect_dual_carriageway_merges(candidates: &[IntersectionImportCandidate]) -> Vec<MergeInstruction>`, where each candidate carries an `Intersection`'s position, its originating way's `oneway`/`name`/`ref` tags, and its id. No I/O — takes plain values, returns plain values, fully unit-testable against fixed fixture data without a database. The repository shell executes each returned `MergeInstruction` as a transaction (re-point cross-sections, merge OSM node ids, reconcile treatment fields, delete the absorbed row, insert an `IntersectionMerge` log entry).

### `crates/core/src/osm/mod.rs` (modified)

Import extended to capture `turn:lanes`, `turn:lanes:forward`, `turn:lanes:backward`, `destination`, `oneway`, `name`, and `ref` tags per way/node (currently only tags needed for `lanes_from_osm.rs` are captured; `oneway`/`name`/`ref` are the new additions needed for dual-carriageway detection specifically, since `turn:lanes`/`destination` were already required for turn-movement inference).

### `crates/core/src/corridor_design/repository.rs` (modified)

New functions: `create_or_match_intersection`, `get_intersection`, `set_intersection_treatment` (superseding #027's cross-section-scoped version), `split_corridor_at_cross_section`, `merge_intersections` (executes a `MergeInstruction`), `list_turn_movements`, `set_turn_movement`, `delete_turn_movement`.

### `crates/server/src/web/intersection_api.rs` (new)

- `GET /api/intersections/:id` / `PUT /api/intersections/:id` — treatment fields (bus_gate/turn_conflict/bus_stop).
- `GET /api/intersections/:id/turn-movements`, `POST`, `DELETE /api/turn-movements/:id`.
- `POST /api/corridors/:corridor_id/cross-sections/:cross_section_id/split`.

### `crates/corridor_builder_web/src/pages/intersection.rs` (modified)

Becomes the real editor the #027 worktree intended, now scoped to a shared `Intersection` rather than a single cross-section; gains a turn-movement panel (lane-pair list with an inferred/manual badge, add/remove, matching this codebase's existing `.field`/`.chip`/`.alert` classes per `DESIGN.md` — no new UI classes or inline colors).

## Data Flow

### Migration (next sequential number after #027 on `main`; referred to here as `028`, confirmed at implementation time)

```sql
CREATE TABLE intersections (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lat           DOUBLE PRECISION NOT NULL,
    lon           DOUBLE PRECISION NOT NULL,
    bus_gate      TEXT CHECK (bus_gate IN ('signal_controlled', 'yield_controlled')),
    turn_conflict TEXT CHECK (turn_conflict IN (
                      'indirect_left_via_alternative', 'indirect_left_within_intersection',
                      'right_in_right_out', 'dead_end_lateral_street'
                  )),
    bus_stop      TEXT CHECK (bus_stop IN ('bus_bulb', 'signal_protected_platform'))
);

-- One row per OSM node an Intersection was matched from. Usually one row per
-- Intersection; more than one after a dual-carriageway merge folds a second
-- node's Intersection into the first. A private/manual Intersection (no OSM
-- origin) has zero rows here -- this table, not a nullable column on
-- `intersections`, is the source of truth for "is this Intersection linked
-- to any OSM node(s), and which."
CREATE TABLE intersection_osm_nodes (
    intersection_id BIGINT NOT NULL REFERENCES intersections(id) ON DELETE CASCADE,
    osm_node_id     BIGINT NOT NULL UNIQUE,
    PRIMARY KEY (intersection_id, osm_node_id)
);

ALTER TABLE cross_sections ADD COLUMN intersection_id BIGINT REFERENCES intersections(id);

CREATE TABLE turn_movements (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    intersection_id BIGINT NOT NULL REFERENCES intersections(id) ON DELETE CASCADE,
    from_lane_id    BIGINT NOT NULL REFERENCES lanes(id) ON DELETE CASCADE,
    to_lane_id      BIGINT NOT NULL REFERENCES lanes(id) ON DELETE CASCADE,
    source          TEXT NOT NULL CHECK (source IN ('inferred', 'manual')),
    UNIQUE (intersection_id, from_lane_id, to_lane_id)
);

-- Audit log for automatic dual-carriageway merges (Architecture, "Dual-carriageway
-- merge"). No FK to the absorbed Intersection -- that row is deleted as part
-- of the same merge transaction that inserts this log entry.
CREATE TABLE intersection_merges (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    surviving_intersection_id  BIGINT NOT NULL REFERENCES intersections(id) ON DELETE CASCADE,
    absorbed_osm_node_ids      BIGINT[] NOT NULL,
    treatment_conflict         BOOLEAN NOT NULL DEFAULT FALSE,
    merged_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Backfill: one private Intersection per existing corridor endpoint that has
-- none yet, matching on osm_node_id where cross_sections.osm_node_id
-- coincides across corridors (populating intersection_osm_nodes accordingly);
-- one new row per endpoint otherwise. The dual-carriageway merge heuristic is
-- NOT run retroactively during this backfill -- it only runs going forward,
-- on new imports (existing dual-carriageway pairs already in the database
-- stay as separate Intersections unless/until re-imported). Exact backfill
-- query written at implementation time.

-- Enforce "endpoint has an intersection_id, interior cross-section does not":
-- expressed as a CHECK or trigger against per-corridor position MIN/MAX,
-- exact constraint shape finalized during implementation (open point, see
-- "Open Points" below).

-- Move #027's data across, then drop it:
-- UPDATE intersections ... FROM intersection_treatments ...
DROP TABLE intersection_treatments;
ALTER TABLE cross_sections DROP COLUMN bus_stop;
```

### Import flow (OSM)

1. Overpass fetch → raw ways/nodes (existing, unchanged), now also capturing `oneway`, `name`, `ref`, `turn:lanes*`, and `destination` tags.
2. For each way's two boundary nodes: look up `intersection_osm_nodes` for a match on that node id.
   - Hit → link the new `cross_sections.intersection_id` to the matched `Intersection` (now shared).
   - Miss → insert a new `Intersection` plus an `intersection_osm_nodes` row for that node id.
3. **Dual-carriageway merge pass:** once this import's ways are all resolved to `Intersection`s (some new, some matched), run `detect_dual_carriageway_merges` over every `Intersection` touched by this import, paired against every other one (new or pre-existing) within the distance threshold. Each returned `MergeInstruction` is executed via `repository::merge_intersections`: re-point `cross_sections.intersection_id` from absorbed to survivor, move the absorbed row's `intersection_osm_nodes` onto the survivor, reconcile `bus_gate`/`turn_conflict`/`bus_stop` (survivor's non-null value wins; if both were non-null and differed, keep survivor's and set `treatment_conflict = true` in the log), delete the absorbed row, insert the `intersection_merges` audit entry.
4. Turn-movement inference: for every `Intersection` that (as of this import, including any dual-carriageway merges just performed) now has more than one corridor, run `infer_turn_movements` pairwise between the newly-added corridor(s) and each corridor already present, for every pair not already covered by an existing row; insert results with `source = 'inferred'`. A pair that already has a `Manual` row is skipped, never overwritten.
5. Manually-traced corridors always create a new private `Intersection` (no `osm_node_id` to match on, and never a dual-carriageway merge candidate — the heuristic only considers OSM-imported endpoints, since a manually-traced corridor has no `oneway`/`name`/`ref` tag data to key on).

### Splitting flow

1. Triggered either by an analyst action in the UI, or automatically when a second corridor's import endpoint lands on an existing corridor's interior cross-section.
2. `splitting.rs`'s pure function computes the head/tail cross-section and lane groupings, and the new `Intersection`'s attributes (position from the split cross-section; `osm_node_ids` stays empty unless the split was itself triggered by an `osm_node_id` match, in which case it holds that one node id).
3. Repository executes the computed change as one transaction: insert `Intersection` (and its `intersection_osm_nodes` row, if any), insert new `Corridor`, reassign the tail's `cross_sections.corridor_id`/`intersection_id`, link any newly-arriving corridor's endpoint to the same `Intersection`.
4. If tag data supports it, infer turn movements as in import step 4 above. The dual-carriageway merge pass (import step 3) does not apply to splits — a split's new `Intersection` is never itself a merge candidate, since it wasn't derived from a `oneway`-tagged way pair.

## Edge Cases (Learned from osm2streets)

osm2streets' own docs and issue tracker document a set of real-world scenarios that broke early, naive versions of its intersection model. Each is mapped here to what it means for this design — either "handled," "explicitly deferred with a documented reason," or "not applicable because this design avoids the problem class entirely."

- **Dual carriageways / divided highways.** OSM (and this codebase's own OSM import) represents each direction of a divided road as a separate way. Two such ways meeting a cross street produce *two* distinct OSM nodes a few meters apart at what a human sees as one intersection — plain `osm_node_id` matching, being exact, would **not** merge them on its own. **Action taken here:** this is exactly what the dual-carriageway merge heuristic exists to fix (Architecture) — two `Intersection`s within a small distance, each freshly linked from a `oneway`-tagged way, sharing a `name`/`ref` tag, are automatically collapsed into one, with the merge logged to `intersection_merges` for audit rather than performed silently-and-untraceably. This reintroduces a form of proximity-based matching that an earlier version of this design deliberately avoided; the heuristic is deliberately narrow (oneway + matching name/ref, not bare proximity) to bound the false-positive risk, and every merge is recorded so a wrong one can be identified after the fact even though no automated "undo" exists yet (see Non-Goals, Open Points).
- **Dog-leg / near-duplicate splits.** osm2streets found that roads jogging slightly as they cross another create short interior segments that may or may not deserve separate treatment. The analogous risk here: splitting a corridor at a cross-section that sits only centimeters or a few meters from an existing endpoint produces a degenerate near-zero-length corridor fragment. **Action taken here:** `split_corridor_at_cross_section`'s error cases (Error Handling, below) are extended from "already an endpoint" to also reject splits within a minimum distance of an existing endpoint, surfaced as a distinct typed error rather than silently creating a sliver corridor.
- **Complex multi-way junction clusters.** Same root cause as dual carriageways (multiple real OSM nodes representing one physical junction), generalized beyond the two-carriageway case the merge heuristic specifically targets (oneway pair + matching name/ref). A cluster that doesn't fit that narrow shape — e.g. more than two ways, or ways without matching name/ref tags — is **not** auto-merged; still deferred to a future manual-merge design, documented in Non-Goals.
- **Corrupted / self-intersecting geometry ("Lovecraftian geometry" in osm2streets' own terms).** This codebase already guards against this in `geometry.rs`'s `normalize_corridor_geometry` (`Malformed`, `Disconnected`, `SelfIntersecting` errors). **Action taken here:** both the auto-match path and the splitting path must run through those existing validators before creating an `Intersection` or reassigning cross-sections — neither path gets a shortcut around them. Called out explicitly so implementation doesn't bypass existing validation for the sake of a "simple" insert.
- **Sausage links / silently-collapsed mid-block points.** osm2streets' automatic geometry-simplification pipeline sometimes collapses a short link road that actually contained a legitimate mid-block crossing, losing information a human might have wanted kept. This design has no analogous automatic-collapse step at all — cross-sections are only ever added by an analyst or an import, and splitting only ever *adds* structure (promotes an existing point into an `Intersection`); nothing in this design ever automatically removes or merges an existing cross-section. **Action taken here:** none needed — stated here as a validated design property, worth keeping true as an invariant during implementation (a future "auto-simplify a corridor" feature, if ever proposed, must not violate it).
- **Missing/inconsistent crossing and turn-lane tags.** osm2streets found `footway=crossing` tags inconsistently present, and turn-lane tags absent even at signalized intersections. This design already tolerates unparseable turn-lane tag values (Error Handling, "Turn-movement inference failures"); this edge case extends that same tolerance explicitly to *absent* tags, not just malformed ones — no tag data means zero inferred `TurnMovement`s for that lane, not an error and not an assumed-legal default. An analyst fills gaps manually.
- **Sharp/acute-angle junctions.** osm2streets' geometry math (perpendicular projection for lane trimming) breaks down at extreme angles, requiring fallback bevel joins. This design does not compute intersection polygon geometry at all — `Intersection` only stores a point (`lat`/`lon`), not a shape — so this specific failure mode does not apply. Noted here so a future design that *does* add intersection-polygon rendering knows to budget for it.
- **Roundabouts.** osm2streets classifies roundabout connections specially (regular crossing, uninterrupted/turn-lane-continues, multi-connection, terminus). Under this design, a roundabout is representable as a ring of short corridor segments each ending at a shared `Intersection` with its neighbors — the splitting mechanism supports building this shape with no special-casing required. What is *not* supported is any turn-movement inference aware of roundabout right-of-way rules (yield-on-entry, circulating priority) — `infer_turn_movements` has no roundabout-specific logic, so movements around one must be entered manually. Recorded in Non-Goals.

## Error Handling

- `split_corridor_at_cross_section` returns a typed error distinguishing "cross-section not found," "already an endpoint" (nothing to split), "too close to an existing endpoint" (dog-leg guard — see Edge Cases), and "corridor mismatch" — not a generic `anyhow` 500, since UI double-submission makes these reachable in normal use.
- **Concurrent split vs. lane edit** (same race class fixed for access rules in `c77105cb`): `Corridor` gains a `version` column (it has none today; only `CrossSection` does). Both split and lane-mutating endpoints check/bump it — optimistic concurrency, same shape as `update_cross_section_label`.
- **`osm_node_id` collision with implausible distance**: reject the auto-match with `ImportError::IntersectionPositionMismatch` rather than silently linking two unrelated locations.
- **Turn-movement inference failures**: unparseable *or absent* tag values are skipped per-lane (log + continue, or simply produce zero candidate movements for absent tags); never fail the whole import, matching `lanes_from_osm.rs`'s existing tag-parsing tolerance. Absence of tag data is never treated as "crossing is legal by default."
- **Dual-carriageway merge treatment conflicts**: if both `Intersection`s being merged already had a non-null, *different* value for `bus_gate`, `turn_conflict`, or `bus_stop` (possible if an analyst had already set treatment on one side before the other side was imported), the survivor's value is kept and `intersection_merges.treatment_conflict` is set `true` rather than the import failing — an analyst reviewing the merge log can correct it manually. Never silently drop or average conflicting values.
- **Dual-carriageway merge false positives**: since the heuristic reintroduces proximity-based matching, a wrong merge is possible (e.g. two genuinely distinct oneway junctions with the same street name a short distance apart, such as a street that crosses itself at a grade separation). No automatic detection or rollback of a bad merge is in scope — `intersection_merges` exists specifically so a wrong merge is at least discoverable, not silent. See Open Points.
- **Splitting or auto-matching producing invalid geometry**: both paths run through `geometry.rs`'s existing `normalize_corridor_geometry` validation before committing — a split or match that would produce malformed, disconnected, or self-intersecting geometry is rejected with the same typed errors that path already defines, not bypassed.
- **Deleting a lane with turn movements**: `ON DELETE CASCADE` handles the DB side; the lane-delete API response reports how many turn movements were removed alongside it, so an analyst doesn't silently lose manually-curated turn data.
- **Orphaned `Intersection`s** (last corridor referencing it deleted/moved): harmless, no FK forces cleanup; addressed by a periodic sweep in `crates/worker/src/maintenance/`, consistent with the existing retention-cleanup pattern, rather than synchronous delete-time logic.

## Testing

**Unit (pure, no I/O):**
- `splitting.rs`'s partition logic: table-driven over corridors of 2, 3, and many cross-sections; rejects splitting at an already-existing endpoint; rejects splitting within the minimum-distance guard of an existing endpoint (dog-leg edge case).
- `infer_turn_movements` against fixed tag strings, including: malformed/unknown values (must skip, not panic), and **absent** tags (must produce zero candidate movements, not an assumed-legal default — missing-crossing-tag edge case).
- `infer_turn_movements` against a pair of corridors meeting at a sharp/acute angle: since inference here is tag-driven rather than geometric, confirm it produces the same result regardless of the corridors' relative angle (this design has no angle-dependent logic to break, and a test pinning that is cheap insurance against a future geometric enhancement accidentally introducing angle-sensitivity).
- `detect_dual_carriageway_merges`: table-driven fixtures covering — two oneway ways with matching `name`, close together → merge; matching `name` but far apart → no merge; close together but *not* both `oneway` → no merge; close together and oneway but mismatched `name`/`ref` (and both empty) → no merge; three-plus close-together oneway candidates with matching names (a cluster, not a pair) → merges pairwise or is explicitly left unmerged (exact multi-way behavior is an implementation decision pinned by this test, not left ambiguous).

**Integration (real Postgres via testcontainers, per `.claude/rules/testing.md`):**
- `create_or_match_intersection`: hit and miss cases on an OSM node id already present in `intersection_osm_nodes`.
- `merge_intersections`: full transaction; assert `cross_sections.intersection_id` was re-pointed from absorbed to survivor for every affected row, `intersection_osm_nodes` now lists both node ids under the survivor, the absorbed `Intersection` row is gone, and an `intersection_merges` row was inserted with the correct `absorbed_osm_node_ids`. Separately: a merge where both sides had a conflicting non-null `bus_gate` value sets `treatment_conflict = true` and keeps the survivor's value (not the average, not an error).
- Import-level test: two oneway ways with matching `name` meeting a cross street produce **one** merged `Intersection` with both corridors linked to it (the case the earlier draft of this design had explicitly left unmerged) — and turn movements get inferred across all corridors now sharing it, not just the original pair.
- `split_corridor_at_cross_section`: full transaction; assert the tail's cross-sections/lanes moved, the new `Intersection`'s fields are correct, and — per the persistence-verifying-test discipline established in `c77105cb` — that `Lane`/`TimedAccessRule` data on moved lanes survived unchanged. Also: a roundabout-shaped fixture (corridor ring split into several segments via repeated splitting) ends with each segment's endpoints correctly linked to shared `Intersection`s around the ring.
- A split attempt that would produce geometry `geometry.rs` already rejects (e.g. a split point coincident with another cross-section, producing a zero-length fragment) is rejected with the existing typed geometry error, not silently committed.
- Version-conflict test: split concurrent with a lane edit on the same corridor; one must lose with a version-conflict error, not silent data loss.
- `turn_movements` CRUD: insert inferred, manual override marks `source = Manual`, re-inference does not clobber a manually-edited row.
- Migration: apply against a fixture DB seeded to look like current-main-plus-#027 state; assert the backfill produces exactly one `Intersection` per prior endpoint (no duplicates, no orphans), that `intersection_treatments` data landed on the correct row, and that the backfill does **not** run the dual-carriageway merge heuristic retroactively (existing endpoint pairs that would now match stay separate, per the Data Flow note).

**E2E (Axum test client / Playwright, per the existing lane-editor E2E precedent):**
- Split action from the corridor UI produces two corridors on screen.
- Turn-movement panel shows inferred pairs with an "inferred" badge; editing one flips it to "manual" and a subsequent re-import doesn't overwrite it.

## Open Points

- The exact SQL shape of the "endpoint has `intersection_id`, interior cross-section does not" constraint (CHECK vs. trigger) is deferred to implementation — it depends on how cheaply "is this cross-section a corridor endpoint" can be expressed against `position` in a CHECK constraint versus needing a trigger.
- The precise backfill query for migration 028 (matching existing corridor endpoints onto shared `Intersection`s via `osm_node_id` during the one-time migration) is deferred to implementation; the design fixes its *goal* (no duplicate `Intersection`s for endpoints that already share an `osm_node_id`) but not its SQL.
- The exact minimum-distance threshold for the dog-leg split guard (Edge Cases) is deferred to implementation — likely a small constant (a handful of meters), not user-configurable, but the precise value needs a real-data sanity check rather than being picked arbitrarily here.
- The exact distance threshold for the dual-carriageway merge heuristic is similarly deferred to implementation, and needs its own real-data sanity check separate from the split guard's threshold — the two serve different purposes (one rejects near-duplicate points within a single corridor; the other matches distinct nodes across corridors) and there's no reason to assume the same numeric value is right for both.
- No automatic "undo a dual-carriageway merge" tool is specified. `intersection_merges` makes a bad merge discoverable, but fixing one today means direct data correction, not a UI action. If false-positive merges turn out to be common in practice, that's a follow-up design (likely reusing much of the splitting mechanism's shape, since "un-merge" is structurally similar to "split," just without touching cross-sections/lanes).
- No manual "merge two intersections" UI is specified for clusters the automatic heuristic doesn't catch (non-oneway, non-matching-name cases); if analysts need to correct those, that's a follow-up design.
- Roundabout turn-movement semantics (yield-on-entry, circulating priority) are not modeled; only the ring topology is supported. A follow-up design would be needed if roundabout-aware inference becomes a requirement.

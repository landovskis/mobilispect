# Corridor Lane Editor (REQ-006)

**Date:** 2026-08-09
**Status:** Approved

## Summary

The manual-trace (PR #227) and OSM-import (PR #228) plans, both merged, let an analyst create a corridor — but every cross-section's lane arrangement is either empty (manual trace) or an OSM-tag-derived starting guess (OSM import), and nothing lets the analyst view or edit it. The corridor page is still a "coming soon" placeholder. This spec builds the editor: a mini-map to pick a cross-section, a to-scale visual diagram of its lanes (colored by type, proportional to width — the layout already approved during the original corridor-design brainstorming), and a side panel to edit a clicked lane's type/width/direction/access rules. The cross-section's descriptive `label` is also editable here, folding in the last piece of the original, never-implemented Loop-A scaffolding (REQ-006).

This is a corridor-editing path, not a new bounded context. It reuses the existing `Lane`/`LaneDraft`/`LaneType`/`LaneDirection`/`AccessMode`/`TimedAccessRule` domain types, `lanes.rs`'s default access/width tables, and (once GREEN-passed here) `position.rs`'s pure fractional-position logic — all already built and tested by earlier plans.

**Explicitly out of scope, separate future slices:** bus stops and other intersection treatments (a different part of the same original design spec, not touched here — no `bus_stop` column, no `intersection_treatments` table); cross-section add/reorder (REQ-004/005) — this slice implements `position::assign_position` because lane insertion needs it, but not `add_cross_section`/`reorder_cross_sections`, which stay deferred.

## Domain Context

- **Bounded context(s):** Corridor Design (existing).
- **Aggregates touched:** `Lane` (existing shape, new mutation paths), `CrossSection` (existing shape — `label` becomes editable via already-defined fields, no schema change).
- **New ubiquitous language terms:** none — this slice implements editing operations on already-named concepts (`Lane`, `TimedAccessRule`, cross-section `label`), it doesn't introduce new domain vocabulary.

## Architecture

```
CorridorPage (was: "coming soon" placeholder)
  -> mini-map (own MapLibre instance, same expose_map_for_e2e_tests convention
     as every other map page) renders the corridor's cross-sections as
     clickable points, fetched via GET /api/corridors/:corridor_id/cross-sections
  -> clicking a point fetches that cross-section's lanes
     (GET /api/cross-sections/:cross_section_id/lanes) and shows, below the
     map: an editable label field, and the to-scale lane diagram
  -> "+" controls in every gap (and at both ends) of the diagram insert a new
     lane there; clicking an existing lane opens a side panel: type/width/
     direction selects, the access-rule list (time window + allowed modes
     per rule), "+ add time window", and a remove-lane control
  -> every field edit persists immediately (no explicit Save/Cancel anywhere
     in this app yet, and no reason to introduce one here) -- the diagram/
     panel re-render from each response
```

## Pure Logic (GREEN-pass existing stubs)

Three functions already exist as fully-tested `unimplemented!()` stubs from the original Loop-A scaffolding. This slice implements them for real, no signature changes:

- `position::assign_position(neighbors: Neighbors) -> Result<f64, PositionAssignmentError>` (`crates/core/src/corridor_design/position.rs`) — computes a fractional position between two neighbors (`None` on either side means "insert at a sequence boundary"). Generic over `f64` positions, not tied to cross-sections — reused here for lane insertion, and will be reused again, unmodified, by a future cross-section-insertion slice.
- `edit::validate_label(raw: &str) -> Result<Option<String>, LabelValidationError>` (`crates/core/src/corridor_design/edit.rs`) — trims, rejects empty/too-long/control-character labels.
- `edit::apply_cross_section_edit(...)` (`crates/core/src/corridor_design/edit.rs`) — proves the isolation guarantee (editing one cross-section's label never touches another's fields) in memory, ahead of the repository's single-row `UPDATE`.

`compute_reordered_positions` (REQ-005) stays an unimplemented stub — out of scope here.

## Repository Layer (`crates/core/src/corridor_design/repository.rs`)

- `update_cross_section_label(pool, corridor_id, cross_section_id, new_label, expected_version) -> Result<CrossSection, Error>` — GREEN-pass the existing stub (signature unchanged).
- `insert_lane(pool, cross_section_id: CrossSectionId, lane_type: LaneType, width_meters: f64, direction: LaneDirection, neighbors: Neighbors) -> Result<Lane, Error>` (new) — computes the new lane's position via `position::assign_position`, inserts it, and defaults its access rule via the existing `lanes::default_access_rule_for(lane_type)` so a freshly-added lane is never silently inaccessible to every mode.
- `update_lane(pool, lane_id: LaneId, lane_type: LaneType, width_meters: f64, direction: LaneDirection) -> Result<Lane, Error>` (new) — updates an existing lane's own attributes; position/ordering isn't editable through this function.
- `delete_lane(pool, lane_id: LaneId) -> Result<(), Error>` (new) — cascades to the lane's access rules via the existing `lane_access_rules` FK.
- `set_lane_access_rules(pool, lane_id: LaneId, rules: &[TimedAccessRule]) -> Result<(), Error>` (new) — replaces a lane's whole rule list in one call (delete-then-reinsert, mirroring `insert_lanes_for_cross_section`'s existing shape), rather than exposing individual rule IDs for fine-grained CRUD. Access rules have no `id` in the domain model today and lists are small (1-2 rules is the common case), so whole-list replace is simpler and avoids extending `TimedAccessRule`.

## API Layer (new file, `crates/server/src/web/lane_editor_api.rs`)

| Endpoint | Purpose |
|---|---|
| `GET /api/corridors/:corridor_id/cross-sections` | Cross-section list for the mini-map (id, position, label, lat, lon per cross-section). |
| `GET /api/cross-sections/:cross_section_id/lanes` | Lane list for the diagram. |
| `PATCH /api/cross-sections/:cross_section_id/label` | Body: `{ label: Option<String>, expected_version: i32 }`. |
| `POST /api/cross-sections/:cross_section_id/lanes` | Body: `{ lane_type, width_meters, direction, neighbor_before_position: Option<f64>, neighbor_after_position: Option<f64> }` — the client already holds the full ordered lane list it's rendering, so it supplies the two adjacent positions directly (no server-side lookup needed), the same trust pattern already used for manual-trace point positions. |
| `PATCH /api/lanes/:lane_id` | Body: `{ lane_type, width_meters, direction }`. |
| `DELETE /api/lanes/:lane_id` | Removes a lane. |
| `PUT /api/lanes/:lane_id/access-rules` | Body: `{ rules: Vec<TimedAccessRuleRequest> }` — replaces the whole list. |

All handlers follow the established `ApiError`/`internal_error`/`bad_request` pattern from `corridor_api.rs`/`osm_import.rs`; `internal_error` continues to return a fixed generic message, never leaking `err.to_string()`.

## WASM UI Layer

`crates/corridor_builder_web/src/pages/corridor.rs` stops being a placeholder and becomes the editor described in Architecture above. New API client functions in `api.rs` (`get_corridor_cross_sections`, `get_lanes_for_cross_section`, `update_cross_section_label`, `insert_lane`, `update_lane`, `delete_lane`, `set_lane_access_rules`), following the existing `gloo_net`/`send_and_decode` pattern. The lane diagram reuses the exact visual style already approved during the original corridor-design brainstorming (proportional-width colored `div`s per lane, one color per `LaneType`) — exact color values for the 5 lane types not covered by that original mockup (turn, transit, queue_jump, cycle_track, buffer) are chosen from the Lumina design system's existing palette at implementation-plan time, not re-litigated here.

## Error Handling

| Condition | Response |
|---|---|
| Label validation failure (empty/too-long/control-characters) | `400`, via `validate_label`'s existing error variants |
| Stale `expected_version` on a label edit | `409 Conflict` — new to this codebase (the version-conflict path was never built before, since `update_cross_section_label` was always a stub); `409` is the standard fit for an optimistic-concurrency mismatch |
| Lane or cross-section not found | `500` via the existing `anyhow::bail!` pattern, matching every other "not found" case in this codebase today — a known, pre-existing gap (no typed 404 anywhere yet), not something this slice fixes |
| `width_meters` outside `(0, 20]` meters | `400`, validated server-side rather than trusting the client and letting the DB's `CHECK (width_meters > 0)` constraint surface as a generic `500` — same lesson the OSM-import plan's lane-count-clamp finding already established for this codebase |

## Testing

- Pure logic (`assign_position`, `validate_label`, `apply_cross_section_edit`): already has full unit test coverage from the original Loop-A scaffolding — GREEN-pass only, no new tests needed.
- New repository functions (`insert_lane`, `update_lane`, `delete_lane`, `set_lane_access_rules`, `update_cross_section_label`): integration tests via testcontainers, no mocks, matching this project's established convention.
- New API endpoints: integration tests following `corridor_api.rs`/`osm_import.rs`'s established pattern.
- WASM UI: new Playwright E2E specs, written first and failing, covering: selecting a cross-section on the mini-map, editing a label, adding a lane via a gap "+" control, editing a lane's type/width/direction, adding and removing an access rule.

## Out of Scope

- `bus_stop` and other intersection treatments — a separate, already-deferred slice; no `bus_stop` column, no `intersection_treatments` table added here.
- Cross-section add/reorder (REQ-004/005) — a separate, already-deferred slice. This slice implements `position::assign_position` (needed for lane insertion) but not `add_cross_section`, `reorder_cross_sections`, or `compute_reordered_positions`.
- Typed 404s for not-found lookups — matches this codebase's existing, pre-existing convention everywhere else; not introduced or fixed here.
- Any visual weekly-schedule grid for time windows — the parent design spec already scopes this to a functional list, not polished UI.

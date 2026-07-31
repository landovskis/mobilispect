# Implementation Checklist: Implementation Plan: Corridor Design — Corridor Segment Editor

**Source Implementation Plan:** `implementation-plan-corridor-segment-editor.md` (local file — Confluence not connected this session)
**Target directory:** /Users/alex/src/mobilispect

## REQ-001 — Corridor creation from imported road geometry

### Loop A — Test Plan Implementation Breakdown
- [x] TC-REQ-001-1 — Import a well-formed connected road path produces an ordered corridor
- [x] TC-REQ-001-2 — Import the smallest valid corridor (single 2-point way segment)
- [x] TC-REQ-001-3 — Self-intersecting geometry is rejected without persisting anything
- [x] TC-REQ-001-4 — Disconnected way segments are rejected without persisting a partial corridor
- [ ] TC-REQ-001-5 — Overpass API unreachable during map search returns a clear service error ⚠️ Needs Human Review: test written as #[ignore]d placeholder — Overpass fetch shell (IMP-REQ-001-09) not yet stubbed as a callable unit; will complete once that task lands in Loop B
- [x] TC-REQ-001-6 — Malformed JSON request body is rejected before reaching validation logic
- [x] TC-REQ-001-7 — Source geometry in a non-WGS84 CRS is normalized to WGS84 on import
- [x] TC-REQ-001-8 — Partial source geometry (truncated mid-import) is rejected without persisting anything

### Loop B — Task Breakdown
#### Backend Engineer
- [ ] IMP-REQ-001-01 — Survey existing slice conventions before writing new code
- [ ] IMP-REQ-001-02 — Write migration `021_corridor_design_tables.sql`
- [ ] IMP-REQ-001-03 — Add `CorridorId`, `CrossSectionId` newtypes
- [ ] IMP-REQ-001-04 — Define domain model structs and `ImportGeometryError`
- [ ] IMP-REQ-001-05 — RED: failing unit tests for `normalize_corridor_geometry`
- [ ] IMP-REQ-001-06 — GREEN: implement `normalize_corridor_geometry`
- [ ] IMP-REQ-001-07 — Implement `insert_corridor` DB repository function
- [ ] IMP-REQ-001-08 — Integration tests for `insert_corridor`
- [ ] IMP-REQ-001-09 — Overpass fetch shell with fallback/retry
- [ ] IMP-REQ-001-10 — Axum handlers `fetch_preview`/`import_corridor`
- [ ] IMP-REQ-001-11 — Request size/coordinate safety caps
- [ ] IMP-REQ-001-12 — Register routes in `build_router`
#### Frontend Engineer
- [ ] IMP-REQ-001-13 — Askama template for corridor-creation entry modal
- [ ] IMP-REQ-001-14 — Client-side search → preview → import interaction
- [ ] IMP-REQ-001-15 — Loading / empty / error state rendering
#### QA Engineer
- [ ] IMP-REQ-001-16 — E2E tests for TC-REQ-001-1 through -6
- [ ] IMP-REQ-001-17 — Update DDD artifacts (bounded context, ACL)
- [ ] IMP-REQ-001-18 — Full verification pass (build/lint/format/tests)

## REQ-002 — Manual corridor creation (no import)

### Loop A — Test Plan Implementation Breakdown
- [x] TC-REQ-002-01 — Analyst successfully traces and saves a manual corridor
- [x] TC-REQ-002-02 — Finish Trace is rejected at the minimum point boundary
- [x] TC-REQ-002-03 — Duplicate/degenerate point click is rejected
- [x] TC-REQ-002-04 — Adding a point to a corridor deleted mid-session returns 404
- [x] TC-REQ-002-05 — Manually created corridor's schema matches an imported corridor's shape
- [ ] TC-REQ-002-06 — Point insert fails when database is unavailable ⚠️ Needs Human Review: ignored — DB-unavailability simulation needs typed error + container-stop helper, deferred to Loop B

### Loop B — Task Breakdown
#### Backend Engineer
- [ ] IMP-REQ-002-01 — Confirm REQ-001's migration (incl. `geometry_source`) exists; author it if not
- [ ] IMP-REQ-002-02 — Add `CorridorId`, `CrossSectionId` newtypes
- [ ] IMP-REQ-002-03 — RED: failing unit tests for point/finish validators
- [ ] IMP-REQ-002-04 — GREEN: implement point/finish validators
- [ ] IMP-REQ-002-05 — RED: failing integration tests for repository fns
- [ ] IMP-REQ-002-06 — GREEN: implement repository functions
- [ ] IMP-REQ-002-07 — Define error type + response mapping
- [ ] IMP-REQ-002-08 — Implement Axum handlers + wire routes
#### Frontend Engineer
- [ ] IMP-REQ-002-09 — "New Corridor" entry-point template (Import/Trace cards)
- [ ] IMP-REQ-002-10 — Manual trace screen template + click-to-trace JS
- [ ] IMP-REQ-002-11 — Wire Undo/Cancel/Finish + error handling
- [ ] IMP-REQ-002-12 — Responsive/touch layout
#### QA Engineer
- [ ] IMP-REQ-002-13 — E2E tests for TC-REQ-002-01 through -06
- [ ] IMP-REQ-002-14 — Dedicated schema-parity regression test
- [ ] IMP-REQ-002-15 — Exploratory pass: manual corridor fully editable via REQ-004

## REQ-003 — Display OSM attribution in the editor

### Loop A — Test Plan Implementation Breakdown
- [x] TC-REQ-003-1 — Imported corridor displays OSM attribution strip in editor
- [x] TC-REQ-003-2 — Manual-only corridor does not display OSM attribution strip
- [x] TC-REQ-003-3 — Corridor with partially-imported geometry still shows attribution
- [x] TC-REQ-003-4 — Corridor with missing geometry_source fails safe to showing attribution
- [ ] TC-REQ-003-5 — No analyst-facing control can hide or dismiss the attribution strip ⚠️ Needs Human Review: ignored — no real editor page/rendered HTML exists yet to inspect for a dismiss control; deferred to Loop B once IMP-REQ-003-05 lands

### Loop B — Task Breakdown
#### Backend Engineer
- [ ] IMP-REQ-003-01 — Confirm actual `geometry_source` column/type from REQ-001/002
- [ ] IMP-REQ-003-02 — Implement pure `attribution_visible()`
- [ ] IMP-REQ-003-03 — Thread `geometry_source`/`attribution_visible` into template context
#### Frontend Engineer
- [ ] IMP-REQ-003-04 — Create shared `_osm_attribution.html` partial
- [ ] IMP-REQ-003-05 — Include partial in editor template, gated by visibility flag
- [ ] IMP-REQ-003-06 — Verify contrast/legibility in both themes, z-index
#### QA Engineer
- [ ] IMP-REQ-003-07 — Unit tests for `attribution_visible()`
- [ ] IMP-REQ-003-08 — Integration tests: presence/absence assertions
- [ ] IMP-REQ-003-09 — Compliance-regression suite (mixed-origin, fail-safe, no-dismiss)
- [ ] IMP-REQ-003-10 — DDD ubiquitous-language update if needed

## REQ-004 — Add cross-sections to a corridor sequence

### Loop A — Test Plan Implementation Breakdown
- [x] TC-REQ-004-1 — Add cross-section at end of corridor sequence
- [x] TC-REQ-004-2 — Add cross-section at start of corridor sequence
- [x] TC-REQ-004-3 — Add cross-section between two existing mid-sequence cross-sections
- [x] TC-REQ-004-4 — Reject add to a non-existent corridor
- [x] TC-REQ-004-5 — Reject add when no valid position can be determined
- [x] TC-REQ-004-6 — Reject the losing side of a concurrent add targeting the same slot

### Loop B — Task Breakdown
#### Backend Engineer
- [ ] IMP-REQ-004-01 — Confirm actual base schema from REQ-001/002
- [ ] IMP-REQ-004-02 — Migration: `position` → `NUMERIC`, unique constraint, index
- [ ] IMP-REQ-004-03 — RED: failing unit tests for `assign_position`
- [ ] IMP-REQ-004-04 — GREEN: implement `assign_position`
- [ ] IMP-REQ-004-05 — RED: failing integration tests for `add_cross_section`
- [ ] IMP-REQ-004-06 — GREEN: implement `add_cross_section` shell
- [ ] IMP-REQ-004-07 — Axum handler + route + error mapping
- [ ] IMP-REQ-004-08 — Location coordinate range validation
#### Frontend Engineer
- [ ] IMP-REQ-004-09 — "⊕ Add cross-section" affordance in sequence template
- [ ] IMP-REQ-004-10 — Wire affordance to POST endpoint
- [ ] IMP-REQ-004-11 — Success/error alert rendering
#### QA Engineer
- [ ] IMP-REQ-004-12 — E2E tests for TC-REQ-004-1 through -6
- [ ] IMP-REQ-004-13 — Full-suite verification pass

## REQ-005 — Reorder cross-sections

### Loop A — Test Plan Implementation Breakdown
- [x] TC-REQ-005-1 — Reorder a middle cross-section to the start via up/down controls
- [x] TC-REQ-005-2 — Move the last cross-section to the first position (boundary)
- [x] TC-REQ-005-3 — Move the first cross-section to the last position (boundary)
- [x] TC-REQ-005-4 — Reorder request references a cross-section not in the corridor
- [x] TC-REQ-005-5 — Reorder rejected when corridor was modified elsewhere (stale-state)
- [ ] TC-REQ-005-6 — Unauthorized user cannot reorder a corridor — ⚠️ Needs Human Review: ignored — no auth/authorization layer exists anywhere in this codebase; cannot test "unauthorized user" until one is built, tracked as a cross-cutting project blocker (see Implementation Plan Open Risks)

### Loop B — Task Breakdown
#### Backend Engineer
- [ ] IMP-REQ-005-01 — Document reorder invariant in `aggregate-specs.md`
- [ ] IMP-REQ-005-02 — RED: failing unit tests for `compute_reordered_positions`
- [ ] IMP-REQ-005-03 — GREEN: implement `compute_reordered_positions`
- [ ] IMP-REQ-005-04 — RED: failing integration tests (happy path + stale conflict)
- [ ] IMP-REQ-005-05 — GREEN: implement `reorder_cross_sections` shell
- [ ] IMP-REQ-005-06 — RED: failing handler tests (200/400/403/404/409)
- [ ] IMP-REQ-005-07 — GREEN: implement handler + route + error mapping
- [ ] IMP-REQ-005-08 — Verify/add `sequence_version` column + unique index
#### Frontend Engineer
- [ ] IMP-REQ-005-09 — Per-row ▲/▼ buttons in sequence template
- [ ] IMP-REQ-005-10 — Client script: optimistic reorder + settle
- [ ] IMP-REQ-005-11 — Error/conflict UI states
- [ ] IMP-REQ-005-12 — (Enhancement, non-blocking) drag-and-drop layer
#### QA Engineer
- [ ] IMP-REQ-005-13 — Execute TC-REQ-005-1 through -6
- [ ] IMP-REQ-005-14 — Keyboard-only accessibility verification
- [ ] IMP-REQ-005-15 — DDD ubiquitous-language check

## REQ-006 — Edit an individual cross-section

### Loop A — Test Plan Implementation Breakdown
- [x] TC-REQ-006-1 — Edit and save a cross-section's label successfully
- [x] TC-REQ-006-2 — Editing one cross-section does not alter its siblings (isolation)
- [x] TC-REQ-006-3 — Label length boundary is enforced at exactly 200 and 201 characters
- [x] TC-REQ-006-4 — Cancel discards edits without contacting the server — verified via code comment; no server-side assertion applies (client-only behavior), see corridor_design.rs
- [x] TC-REQ-006-5 — Concurrent edit of the same cross-section is rejected with a conflict
- [x] TC-REQ-006-6 — Saving an edit for a cross-section deleted since the edit view loaded

### Loop B — Task Breakdown
#### Backend Engineer
- [ ] IMP-REQ-006-01 — Confirm schema + read DDD artifacts
- [ ] IMP-REQ-006-02 — Update `aggregate-specs.md`/`ubiquitous-language.md`
- [ ] IMP-REQ-006-03 — Migration: add `version` (and `label` if absent)
- [ ] IMP-REQ-006-04 — RED: failing unit tests for `validate_label`/`apply_cross_section_edit`
- [ ] IMP-REQ-006-05 — GREEN: implement pure functions
- [ ] IMP-REQ-006-06 — RED: failing integration tests for `update_cross_section_label`
- [ ] IMP-REQ-006-07 — GREEN: implement `update_cross_section_label`
- [ ] IMP-REQ-006-08 — Axum handlers (GET edit / PATCH) + routes
- [ ] IMP-REQ-006-09 — Agency-ownership authorization check
- [ ] IMP-REQ-006-15 — Confirm migration-before-deploy ordering
#### Frontend Engineer
- [ ] IMP-REQ-006-10 — Edit-mode Askama partial + MEL placeholder slot
- [ ] IMP-REQ-006-11 — Edit affordance + DOM-swap JS, single-editor constraint
- [ ] IMP-REQ-006-12 — Client-side Cancel (zero-request revert)
#### QA Engineer
- [ ] IMP-REQ-006-13 — Execute TC-REQ-006-1 through -6
- [ ] IMP-REQ-006-14 — Isolation-guarantee verification (sibling row diff)

## REQ-007 — Browser compatibility, no plugins

### Loop A — Test Plan Implementation Breakdown
- [x] TC-REQ-007-1 — Import flow produces identical output across Chromium/Firefox/WebKit (`e2e/tests/req-001-import.spec.ts`, provisional selectors — real assertions, discoverable/runs, fails today for the correct reason: `/corridors/new` is 404)
- [x] TC-REQ-007-2 — Manual trace flow works on pinned minimum-supported browser versions (`e2e/tests/req-002-manual-trace.spec.ts`, provisional selectors — real assertions, discoverable/runs, fails today for the correct reason: `/corridors/new` is 404)
- [x] TC-REQ-007-3 — Graceful degradation when Pointer Events API is unavailable (`e2e/tests/graceful-degradation.spec.ts` — real assertions, discoverable/runs, fails today for the correct reason: `/corridors/new` is 404)
- [x] TC-REQ-007-4 — Engine-specific drag-and-drop failure is surfaced, not silently swallowed (`e2e/tests/req-005-reorder.spec.ts`, Firefox-scoped per test plan preconditions, provisional selectors — real assertions, discoverable/runs, fails today for the correct reason: `/corridors/{id}/edit` route has no seeded fixture/markup yet)

### Loop B — Task Breakdown
#### QA Engineer
- [ ] IMP-REQ-007-01 — Survey repo for existing Node/CI tooling
- [ ] IMP-REQ-007-02 — Initialize Playwright project (3 engines pinned)
- [ ] IMP-REQ-007-03 — Feature-detection smoke tests
- [ ] IMP-REQ-007-04 — Graceful-degradation test
- [ ] IMP-REQ-007-05 — REQ-001 import flow test (3 engines)
- [ ] IMP-REQ-007-06 — REQ-002 manual trace test (pinned versions)
- [ ] IMP-REQ-007-07 — REQ-005 drag-reorder test (`pointercancel` handling)
- [ ] IMP-REQ-007-08 — Wire Playwright suite into CI as required check
#### Frontend Engineer
- [ ] IMP-REQ-007-09 — Client-side feature-detection guard + fallback alert
- [ ] IMP-REQ-007-10 — `pointercancel` handling for drag-reorder

## System Tests (Loop A suite vs. Loop B production code)
- [ ] TC-REQ-001-1
- [ ] TC-REQ-001-2
- [ ] TC-REQ-001-3
- [ ] TC-REQ-001-4
- [ ] TC-REQ-001-5
- [ ] TC-REQ-001-6
- [ ] TC-REQ-001-7
- [ ] TC-REQ-001-8
- [ ] TC-REQ-002-01
- [ ] TC-REQ-002-02
- [ ] TC-REQ-002-03
- [ ] TC-REQ-002-04
- [ ] TC-REQ-002-05
- [ ] TC-REQ-002-06
- [ ] TC-REQ-003-1
- [ ] TC-REQ-003-2
- [ ] TC-REQ-003-3
- [ ] TC-REQ-003-4
- [ ] TC-REQ-003-5
- [ ] TC-REQ-004-1
- [ ] TC-REQ-004-2
- [ ] TC-REQ-004-3
- [ ] TC-REQ-004-4
- [ ] TC-REQ-004-5
- [ ] TC-REQ-004-6
- [ ] TC-REQ-005-1
- [ ] TC-REQ-005-2
- [ ] TC-REQ-005-3
- [ ] TC-REQ-005-4
- [ ] TC-REQ-005-5
- [ ] TC-REQ-005-6
- [ ] TC-REQ-006-1
- [ ] TC-REQ-006-2
- [ ] TC-REQ-006-3
- [ ] TC-REQ-006-4
- [ ] TC-REQ-006-5
- [ ] TC-REQ-006-6
- [x] TC-REQ-007-1 — spec written (`e2e/tests/req-001-import.spec.ts`), discoverable/runs, fails today because `/corridors/new` doesn't exist yet (expected Loop A state)
- [x] TC-REQ-007-2 — spec written (`e2e/tests/req-002-manual-trace.spec.ts`), discoverable/runs, fails today because `/corridors/new` doesn't exist yet (expected Loop A state)
- [x] TC-REQ-007-3 — spec written (`e2e/tests/graceful-degradation.spec.ts`), discoverable/runs, fails today because `/corridors/new` doesn't exist yet (expected Loop A state)
- [x] TC-REQ-007-4 — spec written (`e2e/tests/req-005-reorder.spec.ts`), discoverable/runs, fails today because no seeded fixture/route exists yet (expected Loop A state)

## Notes on scope adaptation for this local run

- **Environment requirement — `DOCKER_HOST`:** `testcontainers` defaults to `/var/run/docker.sock`, which doesn't exist on this machine — Docker Desktop's real socket is `/Users/alex/.docker/run/docker.sock` (confirmed via `docker context inspect`). Every command that runs DB-backed tests (`repository.rs` integration tests, and any handler test that touches Postgres) must be run with `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock` set, e.g. `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run ...`. Confirmed working: with this set, `insert_corridor_persists_ordered_cross_sections` fails for the correct reason (`unimplemented!()` panic), not a Docker connection error.
- **Loop B landmine — `cross_sections.position` is `NUMERIC` but decoded as `f64` in Rust throughout:** `sqlx`'s `bigdecimal`/`rust_decimal` feature isn't enabled in `crates/core/Cargo.toml` (see REQ-004's deviation note), so every query selecting `position` needs an explicit `position::float8 AS position` cast (or the feature needs to be added as a deliberate Loop B decision). REQ-006's pass hit and fixed this in its own new query; REQ-004/005's existing fixture queries haven't been exercised past their `unimplemented!()` stubs yet, so this same fix will be needed there once Loop B's GREEN passes make those functions real. Flagging here so it's addressed once, consistently, rather than rediscovered per-task.

- REQ-007 (Playwright/cross-browser) requires a Node toolchain this Rust workspace does not have. Loop A will still write REQ-007's test *specifications* as Playwright `.spec.ts` files (per the plan), but they cannot execute inside this cargo-only environment without `npm`/`npx` available — flagged here up front rather than discovered mid-run. Will check for `npm` availability before Loop A processes REQ-007's group.
- **Update:** `node`/`npm`/`npx` ARE available on this machine (`node v26.5.0`). New sibling toolchain added at `e2e/` (package.json, playwright.config.ts, tests/*.spec.ts — `@playwright/test` devDependency, resolved to `^1.62.1`). `npm install` and `npx playwright install --with-deps chromium firefox webkit` both succeeded; `npx playwright test --list` discovers all 21 tests (7 specs × 3 engine projects) with no syntax/parse errors; `npx tsc --noEmit` also passes cleanly. All 4 TC-REQ-007 spec files were additionally *run* (not just listed): `feature-detection.spec.ts` genuinely passes (9/9) across real Chromium/Firefox/WebKit since it tests engine capability directly rather than app UI; the other three fail today with locator-timeout/navigation errors because `/corridors/new` and `/corridors/{id}/edit` aren't wired up yet (an already-running local `mobilispect-server` on port 3000 confirms this via a real `404`, not a connection-refused) — the expected, correct Loop A failure mode.
- Frontend tasks (Askama templates + inline JS) are implemented as part of Loop B same as backend tasks — this project has no separate frontend test runner; template rendering is verified via Axum test-client integration tests per existing project convention (see `speed_analysis`/`handlers.rs` tests), not a browser-based unit test framework.

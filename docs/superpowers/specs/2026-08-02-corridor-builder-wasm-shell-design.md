# Street Corridor Builder — WASM Shell (Remix & Region Map)

**Date:** 2026-08-02
**Status:** Approved

## Summary

A new WebAssembly (WASM) app, built with Yew and served alongside the existing Askama-rendered pages, lets an analyst create or open a **remix** (a named draft of proposed street changes scoped to one metro region), see that region's OpenStreetMap street network with edited corridors highlighted, and click either an intersection or a segment between intersections to navigate to its editor.

This spec covers only the shell: remix creation/selection, region selection, the region overview map, and click-routing. The intersection editor and the corridor/segment editor (a WASM rework of the already-scaffolded REQ-001–007 "Corridor Segment Editor" work) are separate, follow-up specs. Their destination pages exist here only as placeholders.

## Domain Context

- **Bounded context(s):** Corridor Design (existing, not yet documented in `bounded-context-canvas.md` — see Notes) plus the existing Configuration/Feed Management context, which owns `regions`.
- **Aggregates touched:** `Region` (extended with a bounding box), new `Remix` aggregate, `Corridor` (gains a `remix_id` association it didn't have before).
- **New ubiquitous language terms:**
  - **Remix** — a named, user-created draft of proposed street corridor changes, scoped to exactly one metro region. Not tied to any user account (this codebase has no auth system).
  - **Metro region bounding box** — the lat/lon extent used to frame the region map on load; an extension of the existing `Region` entity, not a new entity.
  - **Edited corridor** — a corridor that differs from a pristine imported state: `geometry_source = 'manual'` (inherently authored) or `updated_at > created_at` (has been mutated since creation).

## Architecture

### What changes

| Component | Change |
|-----------|--------|
| `regions` table | Add nullable `min_lat`, `min_lon`, `max_lat`, `max_lon` (`DOUBLE PRECISION`) columns |
| Database | New `remixes` table: `id`, `name`, `region_id` (FK), `created_at`, `updated_at` |
| `corridors` table | Add nullable `remix_id BIGINT REFERENCES remixes(id)` — corridors currently exist in a flat global namespace with no region/remix association |
| New crate | `crates/corridor_builder_web` — Yew WASM app, built with Trunk |
| `crates/core` | New `remix` module: pure domain logic (bounding box validation, highlight-rule computation) plus a `region` submodule extension; compiled for both native (server) and `wasm32-unknown-unknown` (client) targets |
| `crates/server` | New `crates/server/src/web/remix_api.rs` — JSON API handlers (see below); `build_router` gains these routes plus a static-file mount for the built WASM assets at `/builder` |
| `docs/ddd/bounded-context-canvas.md`, `ubiquitous-language.md` | Updated with the terms above (tracked as an implementation task, per this project's DDD rule) |

### What stays the same

- Existing Askama-rendered pages (`/`, `/speed`, `/schedule`, etc.) are untouched.
- The existing `corridor_design`/`corridor_import` Askama scaffolding (REQ-001–007, Loop A complete, Loop B not started) is **not modified by this spec**. It will be reworked into WASM by the follow-up segment-editor spec. Its Playwright specs in `e2e/` continue to fail for the same "route not wired" reason they do today, unaffected by this work.
- `crates/core/src/corridor_design/*` (existing `Coordinate`, `GeometrySource`, `Corridor`, `CrossSection` types) is reused as-is; this spec only adds the `remix_id` association.

## Data Model

```sql
-- New migration (next sequential number after 024)
ALTER TABLE regions
    ADD COLUMN min_lat DOUBLE PRECISION,
    ADD COLUMN min_lon DOUBLE PRECISION,
    ADD COLUMN max_lat DOUBLE PRECISION,
    ADD COLUMN max_lon DOUBLE PRECISION;

CREATE TABLE remixes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT NOT NULL CHECK (length(trim(name)) > 0),
    region_id   BIGINT NOT NULL REFERENCES regions(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_remixes_region ON remixes (region_id, updated_at DESC);

ALTER TABLE corridors
    ADD COLUMN remix_id BIGINT REFERENCES remixes(id);

CREATE INDEX idx_corridors_remix ON corridors (remix_id);
```

This touches `crates/core/migrations/` — per this project's Safety Rules, called out explicitly here and again in the implementation plan.

**Note on bounding box population:** the existing first-launch setup flow (`POST /setup`, see `2026-06-03-first-launch-region-setup-design.md`) inserts a `regions` row without a bounding box, and there's no admin UI to set one. This spec does not touch that flow. The metro-region picker (below) only lists regions where a bounding box has been set (`WHERE min_lat IS NOT NULL`); populating it for now is a manual one-time operator step (direct `UPDATE`), documented in the migration file's comment. Building an admin UI for this is out of scope.

## User Flow

1. **Landing page** (`/builder`): two entry points — "Create remix" and "Open remix."
2. **Create remix**: pick a metro region (dropdown, from `regions` with a bounding box set) and enter a name → `POST /api/remixes` → navigate to the region map for the new remix.
3. **Open remix**: pick a metro region first, then see a list of that region's remixes (most-recently-updated first) → select one → navigate to its region map.
4. **Region map** (`/builder/remix/:remix_id`): MapLibre GL JS renders OSM base tiles framed by the region's bounding box. The remix's corridors are drawn as a GeoJSON overlay; corridors matching the "edited corridor" rule are visually highlighted (distinct color/weight), others are drawn but not highlighted.
5. **Click an intersection node** → navigate to `/builder/remix/:remix_id/intersection/:node_id` — a real route, placeholder "editor coming soon" page.
6. **Click a segment between intersections** (a corridor) → navigate to `/builder/remix/:remix_id/corridor/:corridor_id` — a real route, placeholder page. Reuses the existing `corridor_id` identity from the REQ-001–007 work.

## API Endpoints (new)

| Method & Path | Purpose |
|---|---|
| `GET /api/regions` | List regions with a bounding box set: `[{id, name, bbox}]` |
| `GET /api/regions/:id/remixes` | List remixes for a region, most-recently-updated first |
| `POST /api/remixes` | Create a remix: `{name, region_id}` → `{id}` |
| `GET /api/remixes/:id` | Remix detail: `{id, name, region: {id, name, bbox}}` |
| `GET /api/remixes/:id/corridors` | GeoJSON `FeatureCollection`; one `LineString` feature per corridor with properties `{corridor_id, highlighted: bool}` |

All handlers live in `crates/server/src/web/remix_api.rs`, following this project's vertical-slice convention (query + computation + handler together), with pure logic (bounding box validation, the highlight-rule predicate) in `crates/core/src/remix/`.

## WASM App Structure

- `crates/corridor_builder_web`: Yew app, built with Trunk, output served by Axum via a static-file mount at `/builder` with an `index.html` fallback for client-side routing (Yew Router).
- Client-side routes: `/builder`, `/builder/remix/:id`, `/builder/remix/:id/intersection/:node_id` (placeholder), `/builder/remix/:id/corridor/:id` (placeholder).
- Map rendering is delegated to MapLibre GL JS via `wasm-bindgen` interop — OSM tiles, the corridor GeoJSON overlay, pan/zoom, and click events all go through MapLibre; the Yew app owns state (current remix, region, corridor list) and click-to-route logic, calling into `mobilispect-core`'s pure functions (compiled for `wasm32` as well as native) for hit-testing and the highlight-rule predicate.

## Error Handling

| Scenario | Behavior |
|---|---|
| Create remix with a region that has no bounding box | `400`; region shouldn't appear in the picker in the first place, but the API validates independently |
| Create remix with blank/whitespace-only name | `400`, matching the existing `corridors.name` check constraint pattern |
| Open a remix ID that doesn't exist | `404`, WASM app shows an error state and a link back to `/builder` |
| `GET .../corridors` for a remix with zero corridors | `200` with an empty `FeatureCollection`; map renders with no overlay, not an error |
| MapLibre / WASM fails to load (unsupported browser) | Feature-detection guard (consistent with the existing REQ-007 graceful-degradation pattern) shows a fallback message instead of a blank page |

## Testing

| Test | Type | Covers |
|---|---|---|
| Highlight-rule predicate — manual corridor | Unit | Always highlighted regardless of timestamps |
| Highlight-rule predicate — imported, untouched | Unit | Not highlighted (`updated_at == created_at`) |
| Highlight-rule predicate — imported, then edited | Unit | Highlighted once `updated_at > created_at` |
| Region bounding box validation — degenerate box (min > max) | Unit | Rejected |
| `POST /api/remixes` happy path | Integration (testcontainers) | Row inserted, correct `region_id` |
| `POST /api/remixes` — region without bounding box | Integration (testcontainers) | `400` |
| `GET /api/regions/:id/remixes` — ordering | Integration (testcontainers) | Most-recently-updated first |
| `GET /api/remixes/:id/corridors` — mixed edited/unedited | Integration (testcontainers) | Correct `highlighted` flags in GeoJSON output |
| Create remix flow (region picker → name → map loads) | E2E (Playwright, extends existing `e2e/` suite) | Full happy path |
| Open remix flow | E2E (Playwright) | Region picker → remix list → map loads |
| Click intersection navigates to placeholder | E2E (Playwright) | Route contract, not editor content |
| Click corridor navigates to placeholder | E2E (Playwright) | Route contract, not editor content |
| Graceful degradation, no WASM/MapLibre support | E2E (Playwright), consistent with existing `graceful-degradation.spec.ts` pattern | Fallback message shown |

## Out of Scope

- The intersection editor and the corridor/segment editor themselves (placeholder pages only; each gets its own follow-up spec).
- Reworking the existing `corridor_design`/`corridor_import` Askama scaffolding, stub handlers, and their Playwright specs — deferred to the segment-editor follow-up spec, which was confirmed to fully replace that rendering approach.
- An admin UI for setting a region's bounding box (manual `UPDATE` for now).
- Any user/auth system (remixes remain unowned, consistent with the rest of this codebase today).

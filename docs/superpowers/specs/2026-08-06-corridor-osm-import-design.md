# Corridor OSM Import (via Overpass)

**Date:** 2026-08-06
**Status:** Approved

## Summary

The manual-trace plan (`docs/superpowers/plans/2026-08-03-corridor-manual-trace-and-lanes.md`, merged) let an analyst trace a corridor by clicking points on a map. The other half of corridor creation named by the parent design spec (`docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`'s REQ-001) — importing an existing OSM street — was explicitly deferred. This spec builds it: an analyst loads OSM street geometry for the area they're viewing, clicks one or more contiguous street segments, and imports them as a corridor with lanes pre-populated from OSM tags.

This is a corridor-creation path, not a new bounded context. It reuses the existing `normalize_corridor_geometry` (geometry ordering/validation), `derive_lanes_from_osm_tags`'s sibling defaults (`lanes.rs`'s default access/width tables), and `insert_corridor`/`insert_lanes_for_cross_section` (persistence) as-is. What's new: fetching OSM data from the Overpass API, a pure OSM-tag-to-lane parser, two new API endpoints, and a new WASM page.

**Explicitly out of scope, deferred to later plans:** lane *editing* UI, cross-section add/reorder, intersection treatments (all per the parent spec's own scope); the OSM attribution UI strip (the corridor still lands on the existing placeholder page; attribution rendering waits until the real corridor-editor page exists); caching/de-duplicating Overpass responses.

## Domain Context

- **Bounded context(s):** Corridor Design (existing).
- **Aggregates touched:** `Corridor`, `CrossSection`, `Lane` — no shape changes, only a new creation path populating them from OSM data instead of manual clicks.
- **New ubiquitous language terms:**
  - **Way** — an OSM linear feature (a street or path segment), identified by an OSM way id, with an ordered sequence of node coordinates and a set of tags. OSM frequently splits one real-world street into several ways, broken at every intersection.
  - **Load streets** — the analyst action that fetches OSM ways within the current map viewport from Overpass and renders them as a selectable layer.

## Architecture

One Overpass query per import session, proxied through `mobilispect-server` (never called directly from the browser — consistent with the WASM crate's existing convention of only talking to our own `/api/*` routes). Two-step flow: **Load streets** (fetch + render a clickable way layer) → **select + import** (chain selected ways into one corridor, derive baseline lanes, persist).

```
Analyst clicks "Import from OSM" on the region map (button currently disabled,
placeholder from the manual-trace plan)
  -> navigates to /builder/remix/:remix_id/import (new page, own MapLibre instance,
     mirroring manual_trace.rs's structure)
  -> pans/zooms, clicks "Load streets" (disabled below a minimum zoom level, with a
     "zoom in to load streets" hint)
  -> WASM sends the current viewport bounding box to POST /api/remixes/:remix_id/streets
  -> server builds an Overpass QL query (highway=* filter incl. cycleway/path, "out
     geom;" -- confirmed via a live query that this alone returns both tags and full
     node-by-node geometry, no separate "tags" keyword needed) and fetches it via a
     new OverpassClient (crates/core/src/osm/mod.rs)
  -> server returns parsed ways (osm_way_id, ordered node coordinates, tags) as JSON
  -> WASM renders them as a MapLibre line layer; holds the full way list in state
  -> analyst clicks ways to toggle selection (highlighted distinctly); click order
     doesn't matter -- normalize_corridor_geometry orders by shared endpoints
  -> analyst confirms a name (pre-filled if every selected way shares a `name` tag,
     otherwise blank/required) and clicks "Import"
  -> WASM sends the selected ways' already-held data (ids + geometry + tags -- no
     second Overpass call) to POST /api/remixes/:remix_id/corridors/import
  -> server converts selected ways into RawGeometry, calls the existing (unchanged)
     normalize_corridor_geometry for ordering/self-intersection/disconnection checks
  -> server derives baseline lanes per cross-section from that segment's originating
     way's OSM tags, via a new pure function derive_lanes_from_osm_tags
  -> server persists corridor + cross-sections + lanes (reusing Task 6's remix-scoped
     insert_corridor and insert_lanes_for_cross_section), returns the corridor id
  -> WASM navigates to the existing corridor placeholder page
     (/builder/remix/:remix_id/corridor/:corridor_id, "Corridor editor coming soon")
```

## Domain / Core Layer (`crates/core/`)

### `crates/core/src/osm/mod.rs` (new)

Mirrors the existing `crates/core/src/transitland/mod.rs`'s shape (a `reqwest`-based external API client living in `crates/core`, the established precedent for this codebase's on-demand, synchronous external lookups — as opposed to `crates/worker`'s batch/background GTFS ingestion).

```rust
pub struct OverpassClient {
    http: reqwest::Client,
    base_url: String,  // default: https://overpass-api.de/api/interpreter
}

#[derive(Debug, Clone, PartialEq)]
pub struct OsmWay {
    pub osm_way_id: i64,
    // Reuses `corridor_design::geometry::RawPoint` directly (coordinate + optional
    // OSM node id per point) rather than a parallel type -- this is exactly the
    // shape `RawWaySegment.points` needs, and Overpass's response gives us both a
    // `nodes` array (node ids) and a `geometry` array (lat/lon) as parallel,
    // same-order, same-length arrays per way -- confirmed against a live query.
    pub points: Vec<crate::corridor_design::geometry::RawPoint>,
    pub tags: std::collections::HashMap<String, String>,
}

#[derive(Debug)]
pub enum OverpassError {
    Http(String),      // request failed, timed out, or non-2xx
    Parse(String),      // response body didn't match the expected shape
}

impl OverpassClient {
    pub fn new() -> Self { .. }

    #[cfg(test)]
    pub(crate) fn with_base_url(base_url: String) -> Self { .. }

    pub async fn fetch_ways_in_bbox(
        &self,
        bbox: crate::remix::BoundingBox,
    ) -> Result<Vec<OsmWay>, OverpassError>;
}

/// Pure -- parses a raw Overpass JSON response body into `OsmWay`s. Split out from
/// `fetch_ways_in_bbox` so query-shape and response-parsing are independently unit
/// testable without a network call.
fn parse_overpass_response(body: &str) -> Result<Vec<OsmWay>, OverpassError>;

/// Pure -- builds the Overpass QL query string for a bbox and the fixed highway=*
/// filter below. No network, independently testable.
fn build_query(bbox: crate::remix::BoundingBox) -> String;
```

**Highway filter** (fixed, not user-configurable): `motorway`, `trunk`, `primary`, `secondary`, `tertiary`, `unclassified`, `residential`, `service`, `cycleway`, `path`. Sends a descriptive `User-Agent` header identifying this application, per Overpass's usage etiquette.

Reuses the existing `crates/core/src/remix::BoundingBox` (and its `validate()`) for the query's viewport bbox — no new bounding-box type.

### `crates/core/src/corridor_design/lanes_from_osm.rs` (new)

Already named in the parent spec's REQ-001 section. Pure function, no I/O:

```rust
pub fn derive_lanes_from_osm_tags(
    tags: &std::collections::HashMap<String, String>,
) -> Vec<crate::corridor_design::lanes::LaneDraft>
```

Reads `lanes`, `lanes:forward`, `lanes:backward`, `cycleway`/`cycleway:left`/`cycleway:right`, `sidewalk`, `parking:lane:both`/`:left`/`:right`, `oneway`. Uses `lanes.rs`'s existing `default_access_rule_for`/`default_width_meters_for` for each derived lane's baseline access/width. Falls back to a single bidirectional `Travel`-type `LaneDraft` when tags are absent or unrecognized — this function is total; it never fails.

### Multi-way geometry chaining — no new logic

Selected `OsmWay`s convert into `RawWaySegment { osm_way_id, points }` (existing type, `geometry.rs`), bundle into one `RawGeometry`, and pass through the existing, already-tested `normalize_corridor_geometry`. This is where segment ordering (by shared endpoint, independent of click/selection order), self-intersection detection, and disconnection detection all already happen — reused unchanged.

## Server API Layer (`crates/server/src/web/osm_import.rs`, new)

Follows the `ApiError`/`internal_error`/`bad_request` pattern established in `corridor_api.rs` (Task 7 of the manual-trace plan). A new file, parallel to `corridor_api.rs`, since this is a distinct creation path (search + import) rather than manual point-by-point tracing.

```rust
#[derive(Deserialize)]
pub struct SearchStreetsRequest {
    pub min_lat: f64, pub min_lon: f64, pub max_lat: f64, pub max_lon: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OsmPointResponse {
    pub lat: f64,
    pub lon: f64,
    pub osm_node_id: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OsmWayResponse {
    pub osm_way_id: i64,
    pub points: Vec<OsmPointResponse>,
    pub tags: std::collections::HashMap<String, String>,
}

/// POST /api/remixes/:remix_id/streets
pub async fn search_streets(
    State(state): State<AppState>,
    Path(remix_id): Path<i64>,
    Json(req): Json<SearchStreetsRequest>,
) -> Result<Json<Vec<OsmWayResponse>>, ApiError>;

#[derive(Deserialize)]
pub struct ImportCorridorRequest {
    pub name: String,
    pub ways: Vec<OsmWayResponse>,   // client sends back what it received from search_streets
}

#[derive(Serialize)]
pub struct ImportCorridorResponse { pub id: i64 }

/// POST /api/remixes/:remix_id/corridors/import
pub async fn import_corridor(
    State(state): State<AppState>,
    Path(remix_id): Path<i64>,
    Json(req): Json<ImportCorridorRequest>,
) -> Result<(StatusCode, Json<ImportCorridorResponse>), ApiError>;
```

`search_streets`: validates the bbox via `BoundingBox::validate()` (`bad_request` on failure), calls `OverpassClient::fetch_ways_in_bbox`, maps `OverpassError::Http`/`::Parse` to `internal_error` (fixed generic message to the client; details logged server-side only, per this codebase's established convention). No persistence.

`import_corridor`: validates `ways` non-empty and `name` non-blank (`bad_request`), builds `RawGeometry` from `req.ways`, calls `normalize_corridor_geometry` (surfacing `SelfIntersecting`/`Disconnected`/`Malformed`/etc. as `bad_request` with the error's own message — same pattern `corridor_api.rs` already uses for geometry errors), persists the corridor via `insert_corridor(pool, remix_id, name, "geojson_osm_export", Some("© OpenStreetMap contributors"), &normalized)` (the existing signature from Task 6, `remix_id`-scoped — the `osm_attribution` parameter exists specifically for this import path per that function's own doc comment, so this is the first caller to actually populate it; `import_format` uses the pre-existing `"geojson_osm_export"` value rather than a new `"overpass_import"` one, since `corridors.import_format` has a CHECK constraint from migration 021 that only allows the former, and this plan adds no new migration — provenance is still fully captured by `geometry_source = 'imported'` and the OSM attribution string), then for each resulting cross-section calls `derive_lanes_from_osm_tags` against that cross-section's originating way's tags and persists via `insert_lanes_for_cross_section`.

**Trust note:** `import_corridor` persists geometry/tags the client sends back, rather than the server re-fetching by way id from Overpass a second time. This keeps the Overpass query count at exactly one per import session. There is no auth model in this codebase yet, and the result only ever produces a corridor draft inside the analyst's own remix — manual trace already persists client-supplied lat/lon with no independent verification, so this is consistent with existing trust boundaries, not a new one. Revisit if/when auth is introduced.

## WASM UI Layer (`crates/corridor_builder_web/`)

- **`region_map.rs`**: the existing disabled "Import from OSM" button becomes a real navigation to the new route (mirrors the existing "Manual trace" button's `on_choose_manual_trace` callback shape).
- **`app.rs`**: new `Route::ImportCorridor { remix_id: i64 }` at `/builder/remix/:remix_id/import`, added the same way `Route::ManualTrace` was.
- **`pages/import_osm.rs`** (new, mirrors `manual_trace.rs`'s structure):
  - Mounts its own MapLibre map on load (empty until "Load streets" is clicked).
  - State: fetched way list (`Vec<OsmWay>`, `None` until first load), `HashSet<i64>` of selected way ids, current zoom (read from the map, to gate the button), name input, error.
  - **"Load streets" button**: disabled below zoom level 15 (roughly a few city blocks across at typical screen sizes) with a "zoom in to load streets" hint. On click: reads current map bounds, calls `api::search_streets`, on success adds the ways as a MapLibre GeoJSON line-layer source.
  - **Click handling**: `queryRenderedFeatures` against that layer (the WASM shell's established click-priority pattern) toggles the clicked way's id in the selection set; selected ways get a distinct highlight via a MapLibre paint expression keyed on selection state.
  - Once at least one way is selected: a name input appears (pre-filled if every selected way shares one `name` tag, blank/required otherwise) plus an "Import" button.
  - **On Import**: calls `api::import_corridor`; success navigates to the existing `Route::Corridor { remix_id, corridor_id }`; failure shows the server's error message inline (same pattern `manual_trace.rs` uses).
- **`api.rs`**: `search_streets`/`import_corridor` client functions plus DTOs, following the existing `gloo_net`/`send_and_decode` pattern.

## Error Handling

| Condition | Response |
|---|---|
| Invalid/degenerate bbox | `400`, via `BoundingBox::validate()` |
| Overpass unreachable/timeout/malformed response | `500`, fixed generic message (details server-logged only) |
| Empty `ways` on import | `400 "select at least one street"` |
| Blank name | `400`, matching manual trace's existing validation |
| Selected ways don't chain (`Disconnected`), self-intersect, or contain invalid coordinates | `400` with `normalize_corridor_geometry`'s own error message — reused, not reimplemented |
| Unrecognized/absent lane-relevant tags | Never an error — falls back to one bidirectional `Travel` lane |

## Testing

- **`crates/core/src/osm/`**: unit tests for `parse_overpass_response` (fixture JSON → `Vec<OsmWay>`, no network) and `build_query` (given a bbox, assert the generated QL string contains the expected filter/bbox clauses). No live Overpass calls in the suite.
- **`lanes_from_osm.rs`**: pure unit tests per tag combination (lane count + forward/backward split, cycleway variants, parking variants, oneway, and the no-tags fallback), following `lanes.rs`'s existing test style.
- **`osm_import.rs`**: integration tests via testcontainers (real Postgres), covering both endpoints' happy and error paths, following `corridor_api.rs`'s existing test style. `OverpassClient::with_base_url` (mirroring `TransitlandClient`'s existing `#[cfg(test)]` pattern) points `search_streets`'s tests at a local fixture HTTP server instead of the real Overpass endpoint.
- **E2E**: a new `e2e/tests/builder-import-osm.spec.ts`, written first and failing, matching this repo's established precedent. Covers: load streets → select two contiguous ways → import → lands on the corridor page; and a disconnected-selection error case. Since real Overpass can't be hit reliably in CI, `mobilispect-server`'s Overpass calls need interception in the E2E environment — exact mechanism (most likely an env-var-driven base-URL override read by `OverpassClient::new()`, pointed at a small fixture server the E2E setup starts) to be finalized in the implementation plan.

## Documentation Follow-up

`docs/ddd/acl.md` currently says Transitland translation "happens in `crates/worker/src/transitland/`" — it actually lives in `crates/core/src/transitland/`, which is the pattern this plan follows for `OverpassClient` too. The implementation plan should add an OSM/Overpass section to `acl.md` documenting the translation boundary (per `docs/ddd.md`'s "New external data source" rule) and correct the existing stale Transitland location in the same pass.

## Out of Scope

- Lane *editing* UI, cross-section add/reorder, intersection treatments — separate follow-up plans, unchanged from the parent spec (`docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`).
- OSM attribution UI strip — `attribution_visible` stays wired to nothing in the UI until the real corridor-editor page replaces the current placeholder.
- Caching Overpass responses server-side, or de-duplicating against previously-imported ways.
- Configurable highway-type filtering (analyst choosing which road classes to show) — the filter is fixed for this first version.

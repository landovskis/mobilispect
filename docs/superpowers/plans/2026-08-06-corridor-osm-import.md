# Corridor OSM Import (via Overpass) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an analyst load OSM street geometry for the map area they're viewing, select one or more contiguous street segments, and import them as a corridor with baseline lanes derived from OSM tags.

**Architecture:** A new `OverpassClient` (`crates/core/src/osm/`) proxies one Overpass query per import session through `mobilispect-server`; a new `derive_lanes_from_osm_tags` pure function turns each imported way's OSM tags into baseline `LaneDraft`s; two new API endpoints (search, import) reuse the existing `normalize_corridor_geometry`/`insert_corridor`/`insert_lanes_for_cross_section` machinery unchanged; a new WASM page renders fetched ways as a clickable MapLibre layer.

**Tech Stack:** Rust (2024 edition), `reqwest` (already a `crates/core` dependency), Axum, Yew 0.23 + MapLibre GL JS (existing WASM shell), Playwright.

## Global Constraints

- No mocks in tests — integration tests use real Postgres via `testcontainers`; the Overpass API itself is mocked via `wiremock` (already a `crates/core` dev-dependency, used identically for `TransitlandClient`), never a real network call in the test suite.
- Functional Core / Imperative Shell is mandatory: `derive_lanes_from_osm_tags` and Overpass-response parsing are pure (no I/O); `OverpassClient`, the new server handlers, and the WASM page are the imperative shell.
- sqlx queries must be compile-time checked (`query!`/`query_as!`), except test-seeding `RETURNING id` inserts, which use the runtime `sqlx::query_scalar(...)` form. This plan adds no new sqlx queries of its own — it calls existing `repository.rs` functions unchanged.
- ID newtypes only — never raw `i64`/`String` for domain identifiers in `crates/core`/`crates/server` Rust code (HTTP/JSON boundaries use plain `i64`, converted immediately). OSM way/node ids are NOT domain identifiers (no newtype exists or is needed for them) — they stay plain `i64` throughout, matching the existing `cross_sections.osm_way_id`/`osm_node_id` columns' own convention.
- This plan adds **no new database migration** — it persists entirely through the existing `lanes`/`lane_access_rules`/`cross_sections`/`corridors` schema from `docs/superpowers/plans/2026-08-03-corridor-manual-trace-and-lanes.md`.
- ACL boundary: no `reqwest` calls to Overpass may appear in `crates/server` — route handlers call into `mobilispect_core::osm::OverpassClient`, never `reqwest` directly (Task 1 also documents this in `docs/ddd/acl.md`).
- Design spec: `docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md` (this plan implements it in full). Parent spec: `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`'s REQ-001/REQ-003.
- Overpass QL verified live against `overpass-api.de` while writing this plan: `way["highway"~"^(...)$"](minlat,minlon,maxlat,maxlon);out geom;` returns both tags and full node-by-node geometry (a way element's JSON has `id`, `nodes` (array of node ids), `geometry` (parallel array of `{lat,lon}`), and `tags` (object) — `nodes[i]` and `geometry[i]` are the same physical node).

---

## Task 1: Overpass client

**Files:**
- Create: `crates/core/src/osm/mod.rs`
- Modify: `crates/core/src/lib.rs`
- Modify: `docs/ddd/acl.md`

**Interfaces:**
- Consumes: `crate::corridor_design::geometry::RawPoint` (existing), `crate::corridor_design::Coordinate` (existing), `crate::remix::BoundingBox` (existing).
- Produces: `osm::{OverpassClient, OsmWay, OverpassError}`. `OverpassClient::new() -> Self`, `OverpassClient::fetch_ways_in_bbox(&self, bbox: BoundingBox) -> Result<Vec<OsmWay>, OverpassError>`.

- [ ] **Step 1: Write the module with its tests**

Create `crates/core/src/osm/mod.rs`:

```rust
//! Overpass API client: fetches OSM way geometry+tags for a bounding box. See
//! `docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md`.

use crate::corridor_design::Coordinate;
use crate::corridor_design::geometry::RawPoint;
use crate::remix::BoundingBox;

const DEFAULT_BASE_URL: &str = "https://overpass-api.de/api/interpreter";

/// The fixed set of `highway=*` values this app treats as an importable
/// "street" — anything a vehicle can drive on, plus dedicated cycle
/// infrastructure. Not user-configurable in this first version.
const HIGHWAY_FILTER: &[&str] = &[
    "motorway",
    "trunk",
    "primary",
    "secondary",
    "tertiary",
    "unclassified",
    "residential",
    "service",
    "cycleway",
    "path",
];

pub struct OverpassClient {
    http: reqwest::Client,
    base_url: String,
}

impl Default for OverpassClient {
    fn default() -> Self {
        Self::new()
    }
}

impl OverpassClient {
    /// Reads `OVERPASS_BASE_URL` from the environment, if set, so E2E test
    /// runs can point this at a local fixture server without threading a new
    /// field through `Config`/`config.toml` — this is a test/E2E-environment
    /// concern only, not a real operator-facing setting (mirrors this
    /// codebase's existing `MOBILISPECT_DATABASE_URL` convention for
    /// `dev.sh`). Falls back to the real Overpass endpoint when unset.
    pub fn new() -> Self {
        let base_url =
            std::env::var("OVERPASS_BASE_URL").unwrap_or_else(|_| DEFAULT_BASE_URL.to_string());
        Self {
            http: reqwest::Client::new(),
            base_url,
        }
    }

    #[cfg(test)]
    pub(crate) fn with_base_url(base_url: String) -> Self {
        Self {
            http: reqwest::Client::new(),
            base_url,
        }
    }

    pub async fn fetch_ways_in_bbox(&self, bbox: BoundingBox) -> Result<Vec<OsmWay>, OverpassError> {
        let query = build_query(bbox);
        let response = self
            .http
            .post(&self.base_url)
            .header(
                "User-Agent",
                "mobilispect/1.0 (+https://github.com/landovskis/mobilispect)",
            )
            .form(&[("data", query.as_str())])
            .send()
            .await
            .map_err(|e| OverpassError::Http(e.to_string()))?;

        if !response.status().is_success() {
            return Err(OverpassError::Http(format!(
                "Overpass returned status {}",
                response.status()
            )));
        }

        let body = response
            .text()
            .await
            .map_err(|e| OverpassError::Http(e.to_string()))?;

        parse_overpass_response(&body)
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct OsmWay {
    pub osm_way_id: i64,
    // Reuses `corridor_design::geometry::RawPoint` directly (coordinate +
    // optional OSM node id per point) -- this is exactly the shape
    // `RawWaySegment.points` needs, and Overpass's response gives both a
    // `nodes` array (node ids) and a `geometry` array (lat/lon) as parallel,
    // same-order, same-length arrays per way -- confirmed against a live
    // query (see this plan's Global Constraints).
    pub points: Vec<RawPoint>,
    pub tags: std::collections::HashMap<String, String>,
}

#[derive(Debug)]
pub enum OverpassError {
    /// The request failed, timed out, or the server returned a non-2xx status.
    Http(String),
    /// The response body didn't match the expected shape.
    Parse(String),
}

impl std::fmt::Display for OverpassError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            OverpassError::Http(msg) => write!(f, "Overpass request failed: {msg}"),
            OverpassError::Parse(msg) => write!(f, "Overpass response parse error: {msg}"),
        }
    }
}

impl std::error::Error for OverpassError {}

/// Builds the Overpass QL query for `bbox`, filtered to this app's fixed
/// `highway=*` value set. Pure — no I/O, independently testable.
fn build_query(bbox: BoundingBox) -> String {
    let highway_pattern = HIGHWAY_FILTER.join("|");
    format!(
        "[out:json][timeout:25];way[\"highway\"~\"^({highway_pattern})$\"]({},{},{},{});out geom;",
        bbox.min_lat, bbox.min_lon, bbox.max_lat, bbox.max_lon,
    )
}

#[derive(serde::Deserialize)]
struct OverpassResponse {
    elements: Vec<OverpassElement>,
}

#[derive(serde::Deserialize)]
struct OverpassElement {
    #[serde(rename = "type")]
    element_type: String,
    id: i64,
    #[serde(default)]
    nodes: Vec<i64>,
    #[serde(default)]
    geometry: Vec<OverpassGeometryPoint>,
    #[serde(default)]
    tags: std::collections::HashMap<String, String>,
}

#[derive(serde::Deserialize)]
struct OverpassGeometryPoint {
    lat: f64,
    lon: f64,
}

/// Parses a raw Overpass JSON response body into `OsmWay`s. Split out from
/// `fetch_ways_in_bbox` so query-shape and response-parsing are independently
/// unit testable without a network call. Silently skips any non-`"way"`
/// element (this app's `way[...]` query never returns another type, but this
/// guards against a future query shape change) and rejects any way whose
/// `nodes`/`geometry` arrays don't match in length (a malformed or truncated
/// response) rather than silently mis-pairing them.
fn parse_overpass_response(body: &str) -> Result<Vec<OsmWay>, OverpassError> {
    let parsed: OverpassResponse =
        serde_json::from_str(body).map_err(|e| OverpassError::Parse(e.to_string()))?;

    let mut ways = Vec::new();
    for element in parsed.elements {
        if element.element_type != "way" {
            continue;
        }
        if element.nodes.len() != element.geometry.len() {
            return Err(OverpassError::Parse(format!(
                "way {} has {} nodes but {} geometry points",
                element.id,
                element.nodes.len(),
                element.geometry.len()
            )));
        }
        let points = element
            .nodes
            .iter()
            .zip(element.geometry.iter())
            .map(|(node_id, point)| RawPoint {
                coordinate: Coordinate::new(point.lat, point.lon),
                osm_node_id: Some(*node_id),
            })
            .collect();
        ways.push(OsmWay {
            osm_way_id: element.id,
            points,
            tags: element.tags,
        });
    }
    Ok(ways)
}

#[cfg(test)]
mod tests {
    use super::*;
    use wiremock::matchers::method;
    use wiremock::{Mock, MockServer, ResponseTemplate};

    fn sample_bbox() -> BoundingBox {
        BoundingBox {
            min_lat: 45.40,
            min_lon: -73.70,
            max_lat: 45.60,
            max_lon: -73.50,
        }
    }

    // --- build_query ---

    #[test]
    fn build_query_includes_bbox_coordinates_in_order() {
        let query = build_query(sample_bbox());
        assert!(query.contains("(45.4,-73.7,45.6,-73.5)"));
    }

    #[test]
    fn build_query_ends_with_out_geom() {
        let query = build_query(sample_bbox());
        assert!(query.ends_with("out geom;"));
    }

    #[test]
    fn build_query_includes_highway_filter_values() {
        let query = build_query(sample_bbox());
        assert!(query.contains("residential"));
        assert!(query.contains("cycleway"));
        assert!(query.contains("path"));
    }

    // --- parse_overpass_response ---

    #[test]
    fn parse_overpass_response_extracts_way_with_tags_and_points() {
        let body = r#"{
            "version": 0.6,
            "elements": [
                {
                    "type": "way",
                    "id": 4517656,
                    "nodes": [111, 222],
                    "geometry": [
                        {"lat": 45.500, "lon": -73.580},
                        {"lat": 45.501, "lon": -73.579}
                    ],
                    "tags": {"highway": "residential", "name": "Main St"}
                }
            ]
        }"#;

        let ways = parse_overpass_response(body).unwrap();
        assert_eq!(ways.len(), 1);
        assert_eq!(ways[0].osm_way_id, 4517656);
        assert_eq!(ways[0].points.len(), 2);
        assert_eq!(ways[0].points[0].osm_node_id, Some(111));
        assert_eq!(
            ways[0].points[0].coordinate,
            Coordinate::new(45.500, -73.580)
        );
        assert_eq!(ways[0].points[1].osm_node_id, Some(222));
        assert_eq!(
            ways[0].tags.get("highway"),
            Some(&"residential".to_string())
        );
        assert_eq!(ways[0].tags.get("name"), Some(&"Main St".to_string()));
    }

    #[test]
    fn parse_overpass_response_returns_empty_vec_for_no_elements() {
        let body = r#"{"version": 0.6, "elements": []}"#;
        let ways = parse_overpass_response(body).unwrap();
        assert!(ways.is_empty());
    }

    #[test]
    fn parse_overpass_response_skips_non_way_elements() {
        let body = r#"{
            "version": 0.6,
            "elements": [
                {"type": "node", "id": 111, "lat": 45.5, "lon": -73.5},
                {
                    "type": "way",
                    "id": 200,
                    "nodes": [1, 2],
                    "geometry": [
                        {"lat": 45.500, "lon": -73.580},
                        {"lat": 45.501, "lon": -73.579}
                    ],
                    "tags": {}
                }
            ]
        }"#;

        let ways = parse_overpass_response(body).unwrap();
        assert_eq!(ways.len(), 1);
        assert_eq!(ways[0].osm_way_id, 200);
    }

    #[test]
    fn parse_overpass_response_rejects_malformed_json() {
        let result = parse_overpass_response("not json");
        assert!(matches!(result, Err(OverpassError::Parse(_))));
    }

    #[test]
    fn parse_overpass_response_rejects_mismatched_nodes_and_geometry_length() {
        let body = r#"{
            "version": 0.6,
            "elements": [
                {
                    "type": "way",
                    "id": 300,
                    "nodes": [1, 2, 3],
                    "geometry": [
                        {"lat": 45.500, "lon": -73.580},
                        {"lat": 45.501, "lon": -73.579}
                    ],
                    "tags": {}
                }
            ]
        }"#;

        let result = parse_overpass_response(body);
        assert!(matches!(result, Err(OverpassError::Parse(_))));
    }

    // --- fetch_ways_in_bbox (network, via wiremock) ---

    #[tokio::test]
    async fn fetch_ways_in_bbox_returns_parsed_ways_on_success() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "version": 0.6,
                "elements": [{
                    "type": "way",
                    "id": 500,
                    "nodes": [1, 2],
                    "geometry": [
                        {"lat": 45.500, "lon": -73.580},
                        {"lat": 45.501, "lon": -73.579}
                    ],
                    "tags": {"highway": "residential"}
                }]
            })))
            .mount(&server)
            .await;

        let client = OverpassClient::with_base_url(server.uri());
        let ways = client.fetch_ways_in_bbox(sample_bbox()).await.unwrap();

        assert_eq!(ways.len(), 1);
        assert_eq!(ways[0].osm_way_id, 500);
    }

    #[tokio::test]
    async fn fetch_ways_in_bbox_returns_http_error_on_non_success_status() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(ResponseTemplate::new(504))
            .mount(&server)
            .await;

        let client = OverpassClient::with_base_url(server.uri());
        let result = client.fetch_ways_in_bbox(sample_bbox()).await;

        assert!(matches!(result, Err(OverpassError::Http(_))));
    }

    #[tokio::test]
    async fn fetch_ways_in_bbox_returns_parse_error_on_malformed_body() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(ResponseTemplate::new(200).set_body_string("not json"))
            .mount(&server)
            .await;

        let client = OverpassClient::with_base_url(server.uri());
        let result = client.fetch_ways_in_bbox(sample_bbox()).await;

        assert!(matches!(result, Err(OverpassError::Parse(_))));
    }
}
```

- [ ] **Step 2: Register the module**

In `crates/core/src/lib.rs`, add `pub mod osm;` alphabetically between `pub mod on_time_performance;` and `pub mod service_frequency;`:

```rust
pub mod on_time_performance;
pub mod osm;
pub mod service_frequency;
```

- [ ] **Step 3: Document the ACL boundary**

In `docs/ddd/acl.md`, this project's translation-boundary rule ("New domain term...added to ubiquitous-language.md" style convention, per `docs/ddd.md`) requires documenting a new external data source. Add a new section immediately after the existing `## Transitland API` section (before `## Adding a New External Source`):

```markdown
## Overpass API (OSM Import)

Translation happens in `crates/core/src/osm/mod.rs`. The Overpass API is called
on-demand when an analyst searches for or imports OSM street geometry — a
synchronous, user-triggered lookup, not a batch ingestion job, so (like
Transitland above) it lives in `crates/core` rather than `crates/worker`.

**Rule:** No `reqwest` calls to Overpass may appear in `crates/server` — route
handlers call into `mobilispect_core::osm::OverpassClient`, never `reqwest`
directly.

Translations:
- Overpass's raw JSON `elements` array → `OsmWay { osm_way_id: i64, points:
  Vec<corridor_design::geometry::RawPoint>, tags: HashMap<String, String> }`.
  Node ids and lat/lon coordinates are Overpass's `nodes`/`geometry` parallel
  arrays, zipped by index into `RawPoint { coordinate, osm_node_id }` —
  reusing the existing corridor-geometry type directly rather than a parallel
  one.
- No translation to domain ID newtypes happens here — OSM way/node ids stay
  plain `i64` (`osm_way_id`, `osm_node_id`) all the way through persistence,
  matching the existing `cross_sections.osm_way_id`/`osm_node_id` columns'
  own convention (see
  `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`).
```

Also correct this file's existing Transitland section, which currently says translation "happens in `crates/worker/src/transitland/`" — the actual `TransitlandClient` lives in `crates/core/src/transitland/mod.rs` (confirmed by reading the file), so the doc is stale. Change:

```markdown
## Transitland API

Translation happens in `crates/worker/src/transitland/`. The Transitland REST API is called
during static feed ingest to resolve GTFS-local IDs to canonical Onestop IDs.
```

to:

```markdown
## Transitland API

Translation happens in `crates/core/src/transitland/mod.rs` — a synchronous,
on-demand external lookup (resolving IDs during import), not a batch/background
job, so it lives in `crates/core` rather than `crates/worker` (unlike GTFS
static/real-time translation below, which IS a batch/background job and does
live in `crates/worker`). The Transitland REST API is called during static
feed ingest to resolve GTFS-local IDs to canonical Onestop IDs.
```

- [ ] **Step 4: Run the tests**

Run: `cargo nextest run -p mobilispect-core osm::tests`
Expected: all 10 tests PASS (3 `build_query_*`, 4 `parse_overpass_response_*`, 3 `fetch_ways_in_bbox_*`).

- [ ] **Step 5: Verify the crate still builds clean**

Run: `cargo build -p mobilispect-core`
Expected: succeeds with no errors.

- [ ] **Step 6: Commit**

```bash
git add crates/core/src/osm/mod.rs crates/core/src/lib.rs docs/ddd/acl.md
git commit -m "feat(corridor-design): add Overpass API client for OSM street import"
```

---

## Task 2: OSM tag → lane parser

**Files:**
- Create: `crates/core/src/corridor_design/lanes_from_osm.rs`
- Modify: `crates/core/src/corridor_design/mod.rs`

**Interfaces:**
- Consumes: `crate::corridor_design::lanes::{LaneDraft, LaneType, LaneDirection, default_access_rule_for, default_width_meters_for}` (existing, Task 4 of the manual-trace plan).
- Produces: `lanes_from_osm::derive_lanes_from_osm_tags(tags: &HashMap<String, String>) -> Vec<LaneDraft>` — Task 3 calls this per cross-section during import.

- [ ] **Step 1: Write the module with its tests**

Create `crates/core/src/corridor_design/lanes_from_osm.rs`:

```rust
//! Derives a baseline lane arrangement from an OSM way's tags, for corridor
//! import (REQ-001). Pure — no I/O. See
//! `docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md`.
//!
//! This is a deliberately simple approximation of OSM's real-world lane
//! tagging (which is notoriously inconsistent): it collapses `left`/`right`
//! variants of `cycleway`/`parking` into a single symmetric pair (one lane on
//! each side of the travel lanes) rather than modeling true per-side
//! placement, and a bare `cycleway=*`/`sidewalk=*` presence tag (without a
//! `:left`/`:right` suffix) is treated the same as "both sides present."
//! This is a starting point the analyst edits from, not an authoritative
//! reconstruction — refining it is future work if real usage shows gaps.

use std::collections::HashMap;

use crate::corridor_design::lanes::{
    LaneDirection, LaneDraft, LaneType, default_access_rule_for, default_width_meters_for,
};

/// Derives a left-to-right `Vec<LaneDraft>` from an OSM way's tags. Total —
/// never fails. Falls back to a single bidirectional `Travel` lane when none
/// of the recognized tags (`lanes`, `lanes:forward`, `lanes:backward`,
/// `cycleway`/`cycleway:left`/`cycleway:right`, `sidewalk`,
/// `parking:lane:both`/`:left`/`:right`) are present — the safest baseline
/// when OSM data is sparse.
pub fn derive_lanes_from_osm_tags(tags: &HashMap<String, String>) -> Vec<LaneDraft> {
    let has_lane_count_tag = tags.contains_key("lanes")
        || tags.contains_key("lanes:forward")
        || tags.contains_key("lanes:backward");
    let has_cycleway = ["cycleway", "cycleway:left", "cycleway:right"]
        .iter()
        .any(|k| tags.get(*k).is_some_and(|v| v != "no"));
    let has_parking = ["parking:lane:both", "parking:lane:left", "parking:lane:right"]
        .iter()
        .any(|k| tags.get(*k).is_some_and(|v| v != "no"));
    let has_sidewalk = tags
        .get("sidewalk")
        .is_some_and(|v| v != "none" && v != "no");

    if !has_lane_count_tag && !has_cycleway && !has_parking && !has_sidewalk {
        return vec![lane_draft(LaneType::Travel, LaneDirection::Both)];
    }

    let oneway = tags.get("oneway").is_some_and(|v| v == "yes");
    let (forward_count, backward_count) = travel_lane_counts(tags, oneway);

    let mut lanes = Vec::new();
    if has_sidewalk {
        lanes.push(lane_draft(LaneType::Sidewalk, LaneDirection::None));
    }
    if has_parking {
        lanes.push(lane_draft(LaneType::Parking, LaneDirection::None));
    }
    if has_cycleway {
        lanes.push(lane_draft(LaneType::CycleLane, LaneDirection::Both));
    }
    for _ in 0..backward_count {
        lanes.push(lane_draft(LaneType::Travel, LaneDirection::Backward));
    }
    for _ in 0..forward_count {
        lanes.push(lane_draft(LaneType::Travel, LaneDirection::Forward));
    }
    if has_cycleway {
        lanes.push(lane_draft(LaneType::CycleLane, LaneDirection::Both));
    }
    if has_parking {
        lanes.push(lane_draft(LaneType::Parking, LaneDirection::None));
    }
    if has_sidewalk {
        lanes.push(lane_draft(LaneType::Sidewalk, LaneDirection::None));
    }

    if lanes.is_empty() {
        // e.g. `lanes=0` (rare/malformed) with no other relevant tags.
        return vec![lane_draft(LaneType::Travel, LaneDirection::Both)];
    }

    lanes
}

/// Resolves the forward/backward travel-lane counts: an explicit
/// `lanes:forward`/`lanes:backward` pair wins outright; otherwise `lanes`
/// (or a default of 1 for a oneway street / 2 otherwise) is split as evenly
/// as possible, with the forward direction getting the extra lane on an odd
/// total.
fn travel_lane_counts(tags: &HashMap<String, String>, oneway: bool) -> (u32, u32) {
    let forward_backward = (
        tags.get("lanes:forward").and_then(|v| v.parse::<u32>().ok()),
        tags.get("lanes:backward").and_then(|v| v.parse::<u32>().ok()),
    );
    if let (Some(forward), Some(backward)) = forward_backward {
        return (forward, backward);
    }

    let total = tags
        .get("lanes")
        .and_then(|v| v.parse::<u32>().ok())
        .unwrap_or(if oneway { 1 } else { 2 });
    if oneway {
        (total, 0)
    } else {
        let forward = total.div_ceil(2);
        (forward, total.saturating_sub(forward))
    }
}

fn lane_draft(lane_type: LaneType, direction: LaneDirection) -> LaneDraft {
    LaneDraft {
        lane_type,
        width_meters: default_width_meters_for(lane_type),
        direction,
        access_rules: vec![default_access_rule_for(lane_type)],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tags(pairs: &[(&str, &str)]) -> HashMap<String, String> {
        pairs
            .iter()
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect()
    }

    #[test]
    fn no_relevant_tags_falls_back_to_single_bidirectional_travel_lane() {
        let lanes = derive_lanes_from_osm_tags(&HashMap::new());
        assert_eq!(lanes.len(), 1);
        assert_eq!(lanes[0].lane_type, LaneType::Travel);
        assert_eq!(lanes[0].direction, LaneDirection::Both);
    }

    #[test]
    fn irrelevant_tags_only_falls_back_to_single_bidirectional_travel_lane() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("surface", "asphalt")]));
        assert_eq!(lanes.len(), 1);
        assert_eq!(lanes[0].lane_type, LaneType::Travel);
        assert_eq!(lanes[0].direction, LaneDirection::Both);
    }

    #[test]
    fn lanes_four_not_oneway_splits_evenly() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("lanes", "4")]));
        let directions: Vec<LaneDirection> = lanes.iter().map(|l| l.direction).collect();
        assert_eq!(
            directions,
            vec![
                LaneDirection::Backward,
                LaneDirection::Backward,
                LaneDirection::Forward,
                LaneDirection::Forward,
            ]
        );
    }

    #[test]
    fn lanes_three_not_oneway_gives_forward_the_extra_lane() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("lanes", "3")]));
        let directions: Vec<LaneDirection> = lanes.iter().map(|l| l.direction).collect();
        assert_eq!(
            directions,
            vec![
                LaneDirection::Backward,
                LaneDirection::Forward,
                LaneDirection::Forward,
            ]
        );
    }

    #[test]
    fn oneway_yes_with_lanes_two_gives_two_forward_lanes_no_backward() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("lanes", "2"), ("oneway", "yes")]));
        let directions: Vec<LaneDirection> = lanes.iter().map(|l| l.direction).collect();
        assert_eq!(directions, vec![LaneDirection::Forward, LaneDirection::Forward]);
    }

    #[test]
    fn explicit_lanes_forward_and_backward_overrides_the_even_split() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[
            ("lanes:forward", "2"),
            ("lanes:backward", "1"),
        ]));
        let directions: Vec<LaneDirection> = lanes.iter().map(|l| l.direction).collect();
        assert_eq!(
            directions,
            vec![
                LaneDirection::Backward,
                LaneDirection::Forward,
                LaneDirection::Forward,
            ]
        );
    }

    #[test]
    fn cycleway_present_adds_cycle_lanes_on_both_sides_of_travel_lanes() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("cycleway", "lane")]));
        let types: Vec<LaneType> = lanes.iter().map(|l| l.lane_type).collect();
        assert_eq!(
            types,
            vec![
                LaneType::CycleLane,
                LaneType::Travel,
                LaneType::Travel,
                LaneType::CycleLane,
            ]
        );
    }

    #[test]
    fn sidewalk_none_is_treated_as_absent() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("sidewalk", "none"), ("lanes", "2")]));
        assert!(!lanes.iter().any(|l| l.lane_type == LaneType::Sidewalk));
    }

    #[test]
    fn sidewalk_both_adds_sidewalk_lanes_on_both_ends() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("sidewalk", "both"), ("lanes", "2")]));
        assert_eq!(lanes.first().unwrap().lane_type, LaneType::Sidewalk);
        assert_eq!(lanes.last().unwrap().lane_type, LaneType::Sidewalk);
    }

    #[test]
    fn parking_lane_right_adds_parking_lanes() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[
            ("parking:lane:right", "parallel"),
            ("lanes", "2"),
        ]));
        assert!(lanes.iter().any(|l| l.lane_type == LaneType::Parking));
    }

    #[test]
    fn parking_lane_no_is_treated_as_absent() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[
            ("parking:lane:both", "no"),
            ("lanes", "2"),
        ]));
        assert!(!lanes.iter().any(|l| l.lane_type == LaneType::Parking));
    }

    #[test]
    fn every_derived_lane_gets_the_default_width_and_access_rule_for_its_type() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("lanes", "2")]));
        for lane in &lanes {
            assert_eq!(lane.width_meters, default_width_meters_for(lane.lane_type));
            assert_eq!(lane.access_rules, vec![default_access_rule_for(lane.lane_type)]);
        }
    }
}
```

- [ ] **Step 2: Register the module**

In `crates/core/src/corridor_design/mod.rs`, add `pub mod lanes_from_osm;` alphabetically between `pub mod lanes;` and `pub mod position;`:

```rust
pub mod lanes;
pub mod lanes_from_osm;
pub mod position;
```

- [ ] **Step 3: Run the tests**

Run: `cargo nextest run -p mobilispect-core corridor_design::lanes_from_osm::tests`
Expected: all 12 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add crates/core/src/corridor_design/lanes_from_osm.rs crates/core/src/corridor_design/mod.rs
git commit -m "feat(corridor-design): add OSM tag to baseline lane parser"
```

---

## Task 3: Server API — search + import

**Files:**
- Create: `crates/server/src/web/osm_import.rs`
- Modify: `crates/server/src/web/mod.rs`
- Modify: `crates/server/Cargo.toml`

**Interfaces:**
- Consumes: `mobilispect_core::osm::{OverpassClient, OsmWay}` (Task 1), `mobilispect_core::corridor_design::lanes_from_osm::derive_lanes_from_osm_tags` (Task 2), `mobilispect_core::corridor_design::geometry::{RawGeometry, RawWaySegment, RawPoint, normalize_corridor_geometry}` (existing), `mobilispect_core::corridor_design::repository::{insert_corridor, get_corridor_cross_sections, insert_lanes_for_cross_section}` (existing), `mobilispect_core::remix::BoundingBox` (existing).
- Produces: `POST /api/remixes/:remix_id/streets`, `POST /api/remixes/:remix_id/corridors/import`. Response DTOs `osm_import::{OsmPointResponse, OsmWayResponse, ImportCorridorResponse}` — Task 5 (WASM) mirrors these field-for-field in `api.rs`.

- [ ] **Step 0: Add `wiremock` as a `crates/server` dev-dependency**

This crate's tests don't yet depend on `wiremock` (only `crates/core` does, for `TransitlandClient`'s tests) — Step 1's tests below need it too, to mock the Overpass HTTP call `search_streets` makes. In `crates/server/Cargo.toml`'s `[dev-dependencies]` section, add:

```toml
wiremock = "0.6"
```

- [ ] **Step 1: Write the module with its tests**

Create `crates/server/src/web/osm_import.rs`:

```rust
//! JSON API for OSM-based corridor import (search + import). See
//! `docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md`.

use std::collections::HashMap;

use axum::Json;
use axum::extract::{Path, State};
use axum::http::StatusCode;

use mobilispect_core::corridor_design::geometry::{
    RawGeometry, RawPoint, RawWaySegment, normalize_corridor_geometry,
};
use mobilispect_core::corridor_design::lanes_from_osm::derive_lanes_from_osm_tags;
use mobilispect_core::corridor_design::{Coordinate, repository};
use mobilispect_core::ids::RemixId;
use mobilispect_core::osm::OverpassClient;
use mobilispect_core::remix::BoundingBox;

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
pub struct SearchStreetsRequest {
    pub min_lat: f64,
    pub min_lon: f64,
    pub max_lat: f64,
    pub max_lon: f64,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct OsmPointResponse {
    pub lat: f64,
    pub lon: f64,
    pub osm_node_id: Option<i64>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct OsmWayResponse {
    pub osm_way_id: i64,
    pub points: Vec<OsmPointResponse>,
    pub tags: HashMap<String, String>,
}

/// `POST /api/remixes/:remix_id/streets` — fetches OSM ways within a
/// bounding box for the analyst to select from. Read-only; persists nothing.
pub async fn search_streets(
    State(_state): State<AppState>,
    Path(_remix_id): Path<i64>,
    Json(req): Json<SearchStreetsRequest>,
) -> Result<Json<Vec<OsmWayResponse>>, ApiError> {
    let bbox = BoundingBox {
        min_lat: req.min_lat,
        min_lon: req.min_lon,
        max_lat: req.max_lat,
        max_lon: req.max_lon,
    };
    if bbox.validate().is_err() {
        return Err(bad_request("bounding box is invalid"));
    }

    let client = OverpassClient::new();
    let ways = client
        .fetch_ways_in_bbox(bbox)
        .await
        .map_err(|e| internal_error("search_streets: fetch_ways_in_bbox", anyhow::Error::new(e)))?;

    Ok(Json(
        ways.into_iter()
            .map(|way| OsmWayResponse {
                osm_way_id: way.osm_way_id,
                points: way
                    .points
                    .into_iter()
                    .map(|p| OsmPointResponse {
                        lat: p.coordinate.lat,
                        lon: p.coordinate.lon,
                        osm_node_id: p.osm_node_id,
                    })
                    .collect(),
                tags: way.tags,
            })
            .collect(),
    ))
}

#[derive(Debug, serde::Deserialize)]
pub struct ImportCorridorRequest {
    pub name: String,
    pub ways: Vec<OsmWayResponse>,
}

#[derive(Debug, serde::Serialize)]
pub struct ImportCorridorResponse {
    pub id: i64,
}

/// `POST /api/remixes/:remix_id/corridors/import` — normalizes the analyst's
/// selected OSM ways into one corridor, derives baseline lanes per
/// cross-section from each way's tags, and persists both.
pub async fn import_corridor(
    State(state): State<AppState>,
    Path(remix_id): Path<i64>,
    Json(req): Json<ImportCorridorRequest>,
) -> Result<(StatusCode, Json<ImportCorridorResponse>), ApiError> {
    if req.name.trim().is_empty() {
        return Err(bad_request("name must not be blank"));
    }
    if req.ways.is_empty() {
        return Err(bad_request("select at least one street"));
    }

    // Built before `req.ways` is consumed below — looked up per cross-section
    // by which way it originated from, once cross-sections exist.
    let tags_by_way_id: HashMap<i64, HashMap<String, String>> = req
        .ways
        .iter()
        .map(|w| (w.osm_way_id, w.tags.clone()))
        .collect();

    let raw = RawGeometry {
        segments: req
            .ways
            .into_iter()
            .map(|way| RawWaySegment {
                osm_way_id: Some(way.osm_way_id),
                points: way
                    .points
                    .into_iter()
                    .map(|p| RawPoint {
                        coordinate: Coordinate::new(p.lat, p.lon),
                        osm_node_id: p.osm_node_id,
                    })
                    .collect(),
            })
            .collect(),
    };

    let normalized = normalize_corridor_geometry(raw).map_err(|e| bad_request(&e.to_string()))?;

    // `corridors.import_format` has a CHECK constraint (migration 021) that
    // only allows 'geojson_osm_export' -- predates this plan, and this plan
    // adds no new migration, so this is the only valid value here despite
    // the OSM data actually arriving via Overpass, not a GeoJSON export.
    // Provenance is still fully captured by `geometry_source = 'imported'`
    // and the OSM attribution string passed below.
    let corridor_id = repository::insert_corridor(
        &state.db.pool,
        RemixId::from(remix_id),
        req.name.trim(),
        "geojson_osm_export",
        Some("© OpenStreetMap contributors"),
        &normalized,
    )
    .await
    .map_err(|e| internal_error("import_corridor: insert_corridor", e))?;

    let cross_sections = repository::get_corridor_cross_sections(&state.db.pool, corridor_id)
        .await
        .map_err(|e| internal_error("import_corridor: get_corridor_cross_sections", e))?;

    let empty_tags: HashMap<String, String> = HashMap::new();
    for cross_section in cross_sections {
        let tags = cross_section
            .osm_way_id
            .and_then(|id| tags_by_way_id.get(&id))
            .unwrap_or(&empty_tags);
        let drafts = derive_lanes_from_osm_tags(tags);
        repository::insert_lanes_for_cross_section(&state.db.pool, cross_section.id, &drafts)
            .await
            .map_err(|e| internal_error("import_corridor: insert_lanes_for_cross_section", e))?;
    }

    Ok((
        StatusCode::CREATED,
        Json(ImportCorridorResponse {
            id: corridor_id.as_i64(),
        }),
    ))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::web::SetupState;
    use mobilispect_core::config::Config;
    use mobilispect_core::db::test_utils;
    use std::sync::Arc;
    use tokio::sync::RwLock;
    use wiremock::matchers::method;
    use wiremock::{Mock, MockServer, ResponseTemplate};

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
        sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id) VALUES ('Test Remix', 1) RETURNING id",
        )
        .fetch_one(&state.db.pool)
        .await
        .unwrap()
    }

    fn sample_way_response(
        osm_way_id: i64,
        points: Vec<(f64, f64, i64)>,
        tags: HashMap<String, String>,
    ) -> OsmWayResponse {
        OsmWayResponse {
            osm_way_id,
            points: points
                .into_iter()
                .map(|(lat, lon, node_id)| OsmPointResponse {
                    lat,
                    lon,
                    osm_node_id: Some(node_id),
                })
                .collect(),
            tags,
        }
    }

    // --- search_streets ---
    //
    // These tests set `OVERPASS_BASE_URL` via `std::env::set_var` to point at a
    // per-test wiremock server. This project's test runner is `cargo nextest`,
    // which isolates every test in its own process (unlike plain `cargo test`'s
    // shared-process threads) — so this env var never leaks between tests.
    // `std::env::set_var` is `unsafe` as of this project's Rust edition (2024);
    // wrapped accordingly below.

    #[tokio::test]
    async fn search_streets_with_invalid_bbox_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = search_streets(
            State(state),
            Path(remix_id),
            Json(SearchStreetsRequest {
                min_lat: 46.0,
                min_lon: -73.70,
                max_lat: 45.0, // max < min: invalid
                max_lon: -73.50,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn search_streets_happy_path_returns_parsed_ways() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "version": 0.6,
                "elements": [{
                    "type": "way",
                    "id": 777,
                    "nodes": [1, 2],
                    "geometry": [
                        {"lat": 45.500, "lon": -73.580},
                        {"lat": 45.501, "lon": -73.579}
                    ],
                    "tags": {"highway": "residential", "name": "Test St"}
                }]
            })))
            .mount(&server)
            .await;
        unsafe {
            std::env::set_var("OVERPASS_BASE_URL", server.uri());
        }

        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = search_streets(
            State(state),
            Path(remix_id),
            Json(SearchStreetsRequest {
                min_lat: 45.40,
                min_lon: -73.70,
                max_lat: 45.60,
                max_lon: -73.50,
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0.len(), 1);
        assert_eq!(response.0[0].osm_way_id, 777);
        assert_eq!(response.0[0].points.len(), 2);
        assert_eq!(
            response.0[0].tags.get("name"),
            Some(&"Test St".to_string())
        );
    }

    #[tokio::test]
    async fn search_streets_returns_500_when_overpass_unreachable() {
        // Port 1 is a reserved/privileged port nothing listens on locally —
        // connection refused, simulating Overpass being unreachable.
        unsafe {
            std::env::set_var("OVERPASS_BASE_URL", "http://127.0.0.1:1");
        }

        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = search_streets(
            State(state),
            Path(remix_id),
            Json(SearchStreetsRequest {
                min_lat: 45.40,
                min_lon: -73.70,
                max_lat: 45.60,
                max_lon: -73.50,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::INTERNAL_SERVER_ERROR);
    }

    // --- import_corridor ---

    #[tokio::test]
    async fn import_corridor_with_blank_name_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = import_corridor(
            State(state),
            Path(remix_id),
            Json(ImportCorridorRequest {
                name: "   ".to_string(),
                ways: vec![sample_way_response(
                    1,
                    vec![(45.500, -73.580, 10), (45.501, -73.579, 11)],
                    HashMap::new(),
                )],
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn import_corridor_with_no_ways_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = import_corridor(
            State(state),
            Path(remix_id),
            Json(ImportCorridorRequest {
                name: "Test Import".to_string(),
                ways: vec![],
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn import_corridor_with_disconnected_ways_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let way_a = sample_way_response(
            1,
            vec![(45.500, -73.580, 10), (45.501, -73.579, 11)],
            HashMap::new(),
        );
        // ~150m away -- no shared endpoint with way_a.
        let way_b = sample_way_response(
            2,
            vec![(45.503, -73.575, 12), (45.504, -73.574, 13)],
            HashMap::new(),
        );

        let response = import_corridor(
            State(state),
            Path(remix_id),
            Json(ImportCorridorRequest {
                name: "Test Import".to_string(),
                ways: vec![way_a, way_b],
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn import_corridor_happy_path_persists_corridor_and_lanes() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let mut tags = HashMap::new();
        tags.insert("highway".to_string(), "residential".to_string());
        tags.insert("lanes".to_string(), "2".to_string());

        let way = sample_way_response(
            42,
            vec![(45.500, -73.580, 10), (45.501, -73.579, 11)],
            tags,
        );

        let response = import_corridor(
            State(state.clone()),
            Path(remix_id),
            Json(ImportCorridorRequest {
                name: "Test Imported Corridor".to_string(),
                ways: vec![way],
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0, StatusCode::CREATED);
        let corridor_id = mobilispect_core::ids::CorridorId::from(response.1.id);

        let cross_sections = repository::get_corridor_cross_sections(&state.db.pool, corridor_id)
            .await
            .unwrap();
        assert_eq!(cross_sections.len(), 2);

        let lanes = repository::get_lanes_for_cross_section(&state.db.pool, cross_sections[0].id)
            .await
            .unwrap();
        assert_eq!(lanes.len(), 2, "lanes=2 tag should derive 2 travel lanes");
    }
}
```

- [ ] **Step 2: Register the module and routes**

In `crates/server/src/web/mod.rs`, add the module declaration:

```rust
mod osm_import;
```

Add these routes to `build_router`, after the existing `/api/corridors/:corridor_id/finish` route and before `.nest_service("/builder", ...)`:

```rust
        .route(
            "/api/remixes/:remix_id/streets",
            post(osm_import::search_streets),
        )
        .route(
            "/api/remixes/:remix_id/corridors/import",
            post(osm_import::import_corridor),
        )
```

- [ ] **Step 3: Run the tests**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-server osm_import::tests`
Expected: all 7 tests PASS.

- [ ] **Step 4: Verify the workspace still builds**

Run: `cargo build --workspace`
Expected: succeeds with no errors.

- [ ] **Step 5: Commit**

```bash
git add crates/server/src/web/osm_import.rs crates/server/src/web/mod.rs crates/server/Cargo.toml
git commit -m "feat(corridor-design): add search/import JSON API for OSM corridor import"
```

---

## Task 4: E2E fixture + spec (written first, failing)

**Files:**
- Create: `e2e/tests/helpers/overpass-fixture.ts`
- Create: `e2e/tests/builder-import-osm.spec.ts`
- Create: `e2e/global-setup.ts`
- Create: `e2e/global-teardown.ts`
- Modify: `e2e/playwright.config.ts`

**Interfaces:**
- Consumes: `ensureRegionHasBoundingBox`, `withDb` (existing, `e2e/tests/helpers/db.ts`).
- Produces: `startOverpassFixture(): Promise<void>`, `stopOverpassFixture(): Promise<void>` — a fixed-port (`19999`) local Overpass stand-in that Task 6's verification run points `mobilispect-server` at via `OVERPASS_BASE_URL`.

**Why `globalSetup`/`globalTeardown`, not per-spec `beforeAll`/`afterAll`:** `e2e/playwright.config.ts` sets `fullyParallel: true` across three browser projects (chromium/firefox/webkit), each running in its own worker process locally (`workers` is unset for local runs, only pinned to 1 under CI). Playwright's own docs confirm a spec file's `beforeAll`/`afterAll` run once *per worker*, not once globally — so a per-spec `beforeAll` calling `startOverpassFixture()` would have every worker racing to bind the same fixed port 19999 (the losing workers' `listen()` calls reject with `EADDRINUSE`, throwing inside `beforeAll` and failing those workers' tests outright), and whichever worker's `afterAll` finishes first would tear down the one shared fixture server out from under the others still mid-test. `globalSetup`/`globalTeardown` run exactly once for the whole suite regardless of worker/project count, which is the right shape for this one shared, fixed-port resource — no error-swallowing or reference-counting workaround needed.

- [ ] **Step 1: Write the Overpass fixture helper**

Create `e2e/tests/helpers/overpass-fixture.ts`:

```typescript
import { createServer, Server } from 'http';

/**
 * A minimal local stand-in for the Overpass API, so
 * builder-import-osm.spec.ts can exercise the real import flow without
 * depending on the live overpass-api.de endpoint (which E2E runs should
 * never hit — see
 * docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md).
 *
 * Binds to a fixed port rather than an ephemeral one, since
 * `mobilispect-server` is started as a separate process this test suite does
 * not control — its `OVERPASS_BASE_URL` env var must be set to
 * `http://localhost:19999` *before* that process starts, matching this
 * repo's existing convention of fixed, documented ports for test
 * infrastructure (e.g. `mobilispect-pg` on 5433).
 *
 * Always responds with the same three fixture ways, regardless of the
 * actual query body — sufficient to exercise this app's own code paths
 * without needing to parse/validate Overpass QL. Way 9001001 and 9001002
 * share an endpoint node (90012) and form one contiguous street; way
 * 9001003 is ~400m from both (no shared endpoint, but still comfortably
 * within the import page's default zoom-16 viewport so it actually renders
 * and is clickable — see clickWayAt below), for
 * exercising the disconnected-selection error path.
 */
export const FIXTURE_PORT = 19999;

const FIXTURE_RESPONSE = {
  version: 0.6,
  elements: [
    {
      type: 'way',
      id: 9001001,
      nodes: [90011, 90012],
      geometry: [
        { lat: 45.5, lon: -73.58 },
        { lat: 45.501, lon: -73.579 },
      ],
      tags: { highway: 'residential', name: 'Fixture Test Street' },
    },
    {
      type: 'way',
      id: 9001002,
      nodes: [90012, 90013],
      geometry: [
        { lat: 45.501, lon: -73.579 },
        { lat: 45.502, lon: -73.578 },
      ],
      tags: { highway: 'residential', name: 'Fixture Test Street' },
    },
    {
      type: 'way',
      id: 9001003,
      nodes: [90021, 90022],
      geometry: [
        { lat: 45.505, lon: -73.575 },
        { lat: 45.506, lon: -73.574 },
      ],
      tags: { highway: 'residential', name: 'Disconnected Fixture Street' },
    },
  ],
};

let server: Server | undefined;

export function startOverpassFixture(): Promise<void> {
  return new Promise((resolve, reject) => {
    server = createServer((_req, res) => {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(FIXTURE_RESPONSE));
    });
    server.on('error', reject);
    server.listen(FIXTURE_PORT, () => resolve());
  });
}

export function stopOverpassFixture(): Promise<void> {
  return new Promise((resolve) => {
    if (!server) {
      resolve();
      return;
    }
    server.close(() => resolve());
    server = undefined;
  });
}
```

- [ ] **Step 2: Wire the fixture into `globalSetup`/`globalTeardown`**

Create `e2e/global-setup.ts`:

```typescript
import type { FullConfig } from '@playwright/test';
import { startOverpassFixture } from './tests/helpers/overpass-fixture';

/**
 * Starts the Overpass fixture server exactly once, before any test
 * worker/project starts running tests. `globalSetup`/`globalTeardown` run
 * once for the entire suite regardless of worker or project count, unlike a
 * per-spec `beforeAll`/`afterAll` (which runs once *per worker process* --
 * with `fullyParallel: true` across three browser projects, that would mean
 * multiple workers racing to bind the fixture's one fixed port, and racing
 * on which worker's `afterAll` tears the shared server down first). See
 * `tests/helpers/overpass-fixture.ts` for the fixture itself.
 */
async function globalSetup(_config: FullConfig) {
  await startOverpassFixture();
}

export default globalSetup;
```

Create `e2e/global-teardown.ts`:

```typescript
import type { FullConfig } from '@playwright/test';
import { stopOverpassFixture } from './tests/helpers/overpass-fixture';

async function globalTeardown(_config: FullConfig) {
  await stopOverpassFixture();
}

export default globalTeardown;
```

In `e2e/playwright.config.ts`, add `globalSetup`/`globalTeardown` to the `defineConfig({...})` call, alongside the existing `testDir`/`fullyParallel`/etc. top-level options (not inside `use` or `projects`):

```typescript
  globalSetup: require.resolve('./global-setup'),
  globalTeardown: require.resolve('./global-teardown'),
```

- [ ] **Step 3: Write the spec**

Create `e2e/tests/builder-import-osm.spec.ts`:

```typescript
import { test, expect, type Page } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

/**
 * Corridor Design — OSM import flow (see
 * docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md).
 * Written before the WASM UI for it exists (Task 5), so it fails today for
 * the correct reason, matching this repo's established precedent. Uses
 * `window.__corridorBuilderMap.project()` (see
 * builder-click-routing.spec.ts) to compute exact click pixel coordinates
 * for the fixture ways below, since MapLibre's pan/zoom means a rendered
 * way's screen position isn't otherwise predictable from outside the page.
 *
 * The Overpass fixture server this test's OSM data comes from is started
 * once for the whole suite by `../global-setup.ts` (not per-file
 * `beforeAll`) -- see that file's doc comment for why. Requires
 * `mobilispect-server` to have been started with
 * `OVERPASS_BASE_URL=http://localhost:19999` so its Overpass calls hit that
 * fixture server instead of the real overpass-api.de.
 */

const WAY_A_MIDPOINT = { lat: 45.5005, lon: -73.5795 }; // fixture way 9001001
const WAY_B_MIDPOINT = { lat: 45.5015, lon: -73.5785 }; // fixture way 9001002, contiguous with A
const WAY_C_MIDPOINT = { lat: 45.5055, lon: -73.5745 }; // fixture way 9001003, disconnected

let remixId: number;

test.beforeAll(async ({}, testInfo) => {
  await ensureRegionHasBoundingBox();
  await withDb(async (client) => {
    const result = await client.query(
      `INSERT INTO remixes (name, region_id) VALUES ($1, 1) RETURNING id`,
      [`OSM Import Test Remix ${testInfo.parallelIndex}`]
    );
    remixId = result.rows[0].id;
  });
});

test.afterAll(async () => {
  await withDb(async (client) => {
    await client.query(
      `DELETE FROM lanes WHERE cross_section_id IN (
         SELECT id FROM cross_sections WHERE corridor_id IN (
           SELECT id FROM corridors WHERE remix_id = $1))`,
      [remixId]
    );
    await client.query(
      `DELETE FROM cross_sections WHERE corridor_id IN (SELECT id FROM corridors WHERE remix_id = $1)`,
      [remixId]
    );
    await client.query(`DELETE FROM corridors WHERE remix_id = $1`, [remixId]);
    await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
  });
});

async function clickWayAt(page: Page, lonLat: { lat: number; lon: number }) {
  const px = await page.evaluate(
    ({ lat, lon }) => {
      const point = (window as any).__corridorBuilderMap.project([lon, lat]);
      return { x: point.x, y: point.y };
    },
    lonLat
  );
  await page.locator('.maplibregl-canvas').click({ position: px });
}

test.describe('Corridor Design: OSM import', () => {
  test('loading streets, selecting two contiguous ways, and importing persists a corridor and navigates to its editor page', async ({
    page,
  }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForSelector('.maplibregl-canvas');

    await page.getByRole('button', { name: 'Add corridor' }).click();
    await page.getByRole('button', { name: 'Import from OSM' }).click();

    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);
    await page.getByRole('button', { name: 'Load streets' }).click();

    await clickWayAt(page, WAY_A_MIDPOINT);
    await clickWayAt(page, WAY_B_MIDPOINT);

    await expect(page.getByLabel('Corridor name')).toHaveValue('Fixture Test Street');
    await page.getByRole('button', { name: 'Import' }).click();

    await expect(page).toHaveURL(new RegExp(`/builder/remix/${remixId}/corridor/\\d+$`));
    await expect(page.getByText('editor coming soon')).toBeVisible();
  });

  test('selecting two disconnected ways shows a disconnected error and stays on the import screen', async ({
    page,
  }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForSelector('.maplibregl-canvas');

    await page.getByRole('button', { name: 'Add corridor' }).click();
    await page.getByRole('button', { name: 'Import from OSM' }).click();

    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);
    await page.getByRole('button', { name: 'Load streets' }).click();

    await clickWayAt(page, WAY_A_MIDPOINT);
    await clickWayAt(page, WAY_C_MIDPOINT);

    await page.getByRole('button', { name: 'Import' }).click();

    await expect(page.getByText('not connected')).toBeVisible();
  });
});
```

- [ ] **Step 4: Confirm it fails for the right reason**

With the dev server running (per the environment setup pattern established in `docs/superpowers/plans/2026-08-03-corridor-manual-trace-and-lanes.md`'s Task 8/9/10 — Postgres via `mobilispect-pg`, `mobilispect-server` started in the background; `OVERPASS_BASE_URL` doesn't need to be set for this RED check, since the spec should fail before ever reaching a real Overpass call):

```bash
cd e2e && npx playwright test builder-import-osm --project=chromium --list
npx playwright test builder-import-osm --project=chromium
```

Expected: both tests discovered with no parse errors; both fail because `getByRole('button', { name: 'Import from OSM' })` times out — the region-map page's "Import from OSM" button is still `disabled=true` (Task 9 of the manual-trace plan left it as a placeholder), so clicking it does nothing and the page never navigates. `globalSetup` should run once (visible in Playwright's console output as a brief pause before "Running N tests" — no per-test fixture-startup noise), confirming it isn't running per-worker.

- [ ] **Step 5: Commit**

```bash
git add e2e/tests/helpers/overpass-fixture.ts e2e/tests/builder-import-osm.spec.ts e2e/global-setup.ts e2e/global-teardown.ts e2e/playwright.config.ts
git commit -m "test(corridor-design): add failing E2E spec and fixture server for OSM import"
```

---

## Task 5: WASM UI — enable "Import from OSM" and build the import page

**Files:**
- Create: `crates/corridor_builder_web/src/pages/import_osm.rs`
- Modify: `crates/corridor_builder_web/src/maplibre.rs`
- Modify: `crates/corridor_builder_web/src/api.rs`
- Modify: `crates/corridor_builder_web/src/app.rs`
- Modify: `crates/corridor_builder_web/src/pages/mod.rs`
- Modify: `crates/corridor_builder_web/src/pages/region_map.rs`
- Modify: `crates/corridor_builder_web/src/pages/manual_trace.rs`

**Interfaces:**
- Consumes: `POST /api/remixes/:remix_id/streets`, `POST /api/remixes/:remix_id/corridors/import` (Task 3); `app::Route`, `maplibre::Map` (existing).
- Produces: `Route::ImportCorridor { remix_id: i64 }` (new route variant); a working "Import from OSM" button on the region-map page; `maplibre::expose_map_for_e2e_tests` (new shared helper, also adopted by the manual-trace page in this task, closing a gap flagged during the manual-trace plan's final review).

- [ ] **Step 1: Add new `maplibre.rs` bindings and the shared E2E-exposure helper**

In `crates/corridor_builder_web/src/maplibre.rs`, add these bindings inside the existing `extern "C"` block (after the existing `query_rendered_features` binding), and the two new items after the `extern "C"` block closes:

```rust
    /// Current visible geographic bounds — used to build the Overpass bbox
    /// query when the analyst clicks "Load streets" (see
    /// `pages/import_osm.rs`).
    #[wasm_bindgen(method, js_name = getBounds)]
    pub fn get_bounds(this: &Map) -> LngLatBounds;

    /// Current zoom level — used to gate the "Load streets" button.
    #[wasm_bindgen(method, js_name = getZoom)]
    pub fn get_zoom(this: &Map) -> f64;

    /// Updates one paint property of an already-added layer — used to
    /// re-color selected OSM ways as the analyst clicks them (see
    /// `pages/import_osm.rs`), without re-adding the layer.
    #[wasm_bindgen(method, js_name = setPaintProperty)]
    pub fn set_paint_property(this: &Map, layer_id: &str, name: &str, value: &JsValue);

    #[wasm_bindgen(js_namespace = maplibregl)]
    pub type LngLatBounds;

    #[wasm_bindgen(method, js_name = getWest)]
    pub fn get_west(this: &LngLatBounds) -> f64;

    #[wasm_bindgen(method, js_name = getSouth)]
    pub fn get_south(this: &LngLatBounds) -> f64;

    #[wasm_bindgen(method, js_name = getEast)]
    pub fn get_east(this: &LngLatBounds) -> f64;

    #[wasm_bindgen(method, js_name = getNorth)]
    pub fn get_north(this: &LngLatBounds) -> f64;
}

/// Stashes `map` on `window.__corridorBuilderMap` so Playwright E2E tests can
/// compute exact click pixel coordinates via `map.project(...)` instead of
/// guessing. Shared across every page that mounts its own MapLibre map
/// instance (`pages/region_map.rs`, `pages/manual_trace.rs`,
/// `pages/import_osm.rs`).
pub fn expose_map_for_e2e_tests(map: &Map) {
    if let Some(window) = web_sys::window() {
        let _ = js_sys::Reflect::set(&window, &"__corridorBuilderMap".into(), map);
    }
}
```

(The file's existing `extern "C"` block closing `}` moves down to after the new `LngLatBounds` bindings; `expose_map_for_e2e_tests` is a normal `pub fn` outside it, at the end of the file.)

- [ ] **Step 2: Update `region_map.rs` to use the shared helper and enable the button**

In `crates/corridor_builder_web/src/pages/region_map.rs`, remove the file's own private `expose_map_for_e2e_tests` function (at the bottom of the file) entirely, and change its one call site (inside `finish_map_setup`) from:

```rust
    expose_map_for_e2e_tests(map);
```

to:

```rust
    crate::maplibre::expose_map_for_e2e_tests(map);
```

Add a new callback alongside the existing `on_choose_manual_trace`:

```rust
    let on_choose_import_osm = {
        let navigator = navigator.clone();
        Callback::from(move |_: MouseEvent| {
            navigator.push(&Route::ImportCorridor { remix_id });
        })
    };
```

Change the "Import from OSM" button from disabled to wired up:

```rust
                        <button class="btn" style="display:block; width:100%;" onclick={on_choose_import_osm}>{ "Import from OSM" }</button>
```

(was: `<button class="btn" style="display:block; width:100%;" disabled=true title="Coming soon">{ "Import from OSM" }</button>`)

- [ ] **Step 3: Close the manual-trace map's E2E-exposure gap**

In `crates/corridor_builder_web/src/pages/manual_trace.rs`'s `mount_trace_map`, add a call to the newly-shared helper immediately after the existing:

```rust
    map.on("click", &onclick);
    onclick.forget();
```

add:

```rust
    crate::maplibre::expose_map_for_e2e_tests(&map);
```

- [ ] **Step 4: Add the route**

In `crates/corridor_builder_web/src/app.rs`, add the import and route variant:

```rust
use crate::pages::corridor::CorridorPage;
use crate::pages::import_osm::ImportOsmPage;
use crate::pages::intersection::IntersectionPage;
use crate::pages::landing::LandingPage;
use crate::pages::manual_trace::ManualTracePage;
use crate::pages::region_map::RegionMapPage;

#[derive(Clone, Routable, PartialEq, Debug)]
pub enum Route {
    #[at("/builder")]
    Landing,
    #[at("/builder/remix/:remix_id")]
    RegionMap { remix_id: i64 },
    #[at("/builder/remix/:remix_id/trace")]
    ManualTrace { remix_id: i64 },
    #[at("/builder/remix/:remix_id/import")]
    ImportCorridor { remix_id: i64 },
    #[at("/builder/remix/:remix_id/intersection/:cross_section_id")]
    Intersection {
        remix_id: i64,
        cross_section_id: i64,
    },
    #[at("/builder/remix/:remix_id/corridor/:corridor_id")]
    Corridor { remix_id: i64, corridor_id: i64 },
    #[not_found]
    #[at("/builder/404")]
    NotFound,
}

fn switch(route: Route) -> Html {
    match route {
        Route::Landing => html! { <LandingPage /> },
        Route::RegionMap { remix_id } => html! { <RegionMapPage {remix_id} /> },
        Route::ManualTrace { remix_id } => html! { <ManualTracePage {remix_id} /> },
        Route::ImportCorridor { remix_id } => html! { <ImportOsmPage {remix_id} /> },
        Route::Intersection {
            remix_id,
            cross_section_id,
        } => html! { <IntersectionPage {remix_id} {cross_section_id} /> },
        Route::Corridor {
            remix_id,
            corridor_id,
        } => html! { <CorridorPage {remix_id} {corridor_id} /> },
        Route::NotFound => html! { <p>{ "Not found" }</p> },
    }
}
```

- [ ] **Step 5: Register the new page module**

In `crates/corridor_builder_web/src/pages/mod.rs`, add `pub mod import_osm;` alphabetically:

```rust
pub mod corridor;
pub mod import_osm;
pub mod intersection;
pub mod landing;
pub mod manual_trace;
pub mod region_map;
```

- [ ] **Step 6: Add API client functions**

In `crates/corridor_builder_web/src/api.rs`, add after the existing `finish_manual_corridor` function:

```rust
#[derive(Debug, Clone, Serialize)]
struct SearchStreetsRequest {
    min_lat: f64,
    min_lon: f64,
    max_lat: f64,
    max_lon: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct OsmPointResponse {
    pub lat: f64,
    pub lon: f64,
    pub osm_node_id: Option<i64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct OsmWayResponse {
    pub osm_way_id: i64,
    pub points: Vec<OsmPointResponse>,
    pub tags: std::collections::HashMap<String, String>,
}

pub async fn search_streets(
    remix_id: i64,
    min_lat: f64,
    min_lon: f64,
    max_lat: f64,
    max_lon: f64,
) -> Result<Vec<OsmWayResponse>, String> {
    let request = gloo_net::http::Request::post(&format!("{API_BASE}/remixes/{remix_id}/streets"))
        .json(&SearchStreetsRequest {
            min_lat,
            min_lon,
            max_lat,
            max_lon,
        })
        .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, Serialize)]
struct ImportCorridorRequest {
    name: String,
    ways: Vec<OsmWayResponse>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ImportCorridorResponse {
    pub id: i64,
}

pub async fn import_corridor(
    remix_id: i64,
    name: String,
    ways: Vec<OsmWayResponse>,
) -> Result<ImportCorridorResponse, String> {
    let request =
        gloo_net::http::Request::post(&format!("{API_BASE}/remixes/{remix_id}/corridors/import"))
            .json(&ImportCorridorRequest { name, ways })
            .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}
```

- [ ] **Step 7: Write the import page**

Create `crates/corridor_builder_web/src/pages/import_osm.rs`:

```rust
use std::cell::RefCell;
use std::collections::HashSet;
use std::rc::Rc;

use wasm_bindgen::prelude::*;
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;
use crate::maplibre::Map;

#[derive(Properties, PartialEq)]
pub struct ImportOsmPageProps {
    pub remix_id: i64,
}

/// Minimum zoom level (roughly a few city blocks across) required before
/// "Load streets" is enabled — keeps every Overpass query small. See the
/// design spec's WASM UI Layer section.
const MIN_LOAD_STREETS_ZOOM: f64 = 15.0;

#[component]
pub fn ImportOsmPage(props: &ImportOsmPageProps) -> Html {
    let remix_id = props.remix_id;
    let navigator = use_navigator().expect("BrowserRouter provides a Navigator");

    // `map_ref`/`ways_ref`/`selected_ref` are `Rc<RefCell<...>>` (via
    // `use_mut_ref`), not `UseStateHandle`s, deliberately: the map's native
    // click listener below is a `wasm_bindgen::Closure` registered once (via
    // `map.on(...)` + `.forget()`) and reused for the page's whole lifetime,
    // not recreated on every Yew render the way a `Callback` is. A
    // `UseStateHandle` captured into a Closure like that only ever sees the
    // value from the render where the Closure was created — this is the
    // exact hazard the manual-trace page's point counter hit (see
    // `pages/manual_trace.rs`'s comment on `click_point_count`), fixed there
    // by reading from a live, non-snapshotted source instead of an old
    // `UseStateHandle` dereference. `RefCell::borrow()` always returns the
    // current value, so the click closure reads live state through these
    // three instead. `selection_count`/`name_value` below remain
    // `UseStateHandle`s because the click closure only ever *writes* fresh,
    // freshly-computed values into them (`.set(...)`) -- writing a computed
    // value has no staleness hazard, only *reading* a handle to compute one
    // does.
    let map_ref = use_mut_ref(|| None::<Map>);
    let ways_ref = use_mut_ref(|| None::<Vec<api::OsmWayResponse>>);
    let selected_ref = use_mut_ref(HashSet::<i64>::new);

    let selection_count = use_state(|| 0usize);
    let name_value = use_state(String::new);
    let zoom_ok = use_state(|| false);
    let error = use_state(|| None::<String>);

    // Mounts the map exactly once, on first render: creates it, tracks zoom,
    // and registers the ways-layer click listener up front (querying
    // `queryRenderedFeatures` against a layer that doesn't exist yet, before
    // "Load streets" is clicked, simply returns no results -- registering
    // early is harmless and avoids ever needing to re-register).
    {
        let map_ref = map_ref.clone();
        let ways_ref = ways_ref.clone();
        let selected_ref = selected_ref.clone();
        let selection_count = selection_count.clone();
        let name_value = name_value.clone();
        let zoom_ok = zoom_ok.clone();
        use_effect_with((), move |()| {
            let options = to_js_value(&serde_json::json!({
                "container": "import-map",
                "style": osm_raster_style(),
                "center": [-73.5795, 45.5005],
                "zoom": 16,
            }));
            if let Ok(options) = options {
                let map = Map::new(&options);
                *map_ref.borrow_mut() = Some(map.clone());
                zoom_ok.set(map.get_zoom() >= MIN_LOAD_STREETS_ZOOM);

                let zoom_watch_map = map.clone();
                let zoom_watch_flag = zoom_ok.clone();
                let onzoom = Closure::wrap(Box::new(move |_event: JsValue| {
                    zoom_watch_flag.set(zoom_watch_map.get_zoom() >= MIN_LOAD_STREETS_ZOOM);
                }) as Box<dyn FnMut(JsValue)>);
                map.on("zoomend", &onzoom);
                map.on("moveend", &onzoom);
                onzoom.forget();

                let click_map = map.clone();
                let click_ways_ref = ways_ref.clone();
                let click_selected_ref = selected_ref.clone();
                let click_selection_count = selection_count.clone();
                let click_name_value = name_value.clone();
                let onclick = Closure::wrap(Box::new(move |event: JsValue| {
                    handle_way_click(
                        &click_map,
                        &event,
                        &click_ways_ref,
                        &click_selected_ref,
                        &click_selection_count,
                        &click_name_value,
                    );
                }) as Box<dyn FnMut(JsValue)>);
                map.on("click", &onclick);
                onclick.forget();

                crate::maplibre::expose_map_for_e2e_tests(&map);
            }
            || ()
        });
    }

    let on_load_streets = {
        let map_ref = map_ref.clone();
        let ways_ref = ways_ref.clone();
        let error = error.clone();
        Callback::from(move |_: MouseEvent| {
            let Some(map) = map_ref.borrow().clone() else {
                return;
            };
            let bounds = map.get_bounds();
            let (min_lat, min_lon, max_lat, max_lon) = (
                bounds.get_south(),
                bounds.get_west(),
                bounds.get_north(),
                bounds.get_east(),
            );
            let map_ref = map_ref.clone();
            let ways_ref = ways_ref.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::search_streets(remix_id, min_lat, min_lon, max_lat, max_lon).await {
                    Ok(fetched) => {
                        if let Some(map) = map_ref.borrow().clone() {
                            render_ways_layer(&map, &fetched);
                        }
                        *ways_ref.borrow_mut() = Some(fetched);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let on_name_input = {
        let name_value = name_value.clone();
        Callback::from(move |e: InputEvent| {
            let value = e
                .target_dyn_into::<web_sys::HtmlInputElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            name_value.set(value);
        })
    };

    let on_import = {
        let ways_ref = ways_ref.clone();
        let selected_ref = selected_ref.clone();
        let name_value = name_value.clone();
        let error = error.clone();
        let navigator = navigator.clone();
        Callback::from(move |_: MouseEvent| {
            let Some(all_ways) = ways_ref.borrow().clone() else {
                return;
            };
            let selected_ids = selected_ref.borrow().clone();
            let selected: Vec<api::OsmWayResponse> = all_ways
                .into_iter()
                .filter(|w| selected_ids.contains(&w.osm_way_id))
                .collect();
            if selected.is_empty() {
                return;
            }
            let name = (*name_value).clone();
            let error = error.clone();
            let navigator = navigator.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::import_corridor(remix_id, name, selected).await {
                    Ok(response) => navigator.push(&Route::Corridor {
                        remix_id,
                        corridor_id: response.id,
                    }),
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let has_selection = *selection_count > 0;

    html! {
        <div class="setup-wrap">
            <div class="setup-card">
                <h1 class="setup-title">{ "Import a corridor from OpenStreetMap" }</h1>
                if let Some(err) = &*error {
                    <div class="alert alert--err">{ err }</div>
                }
                if !*zoom_ok {
                    <p>{ "Zoom in to load streets." }</p>
                } else {
                    <button class="btn btn-primary" style="width:100%;" onclick={on_load_streets}>{ "Load streets" }</button>
                }
                if has_selection {
                    <div style="margin-top:1rem;">
                        <label class="field-label" for="import-name">{ "Corridor name" }</label>
                        <input class="field" id="import-name" type="text" value={(*name_value).clone()} oninput={on_name_input} />
                        <button class="btn btn-primary" style="width:100%;margin-top:1rem;" onclick={on_import}>{ "Import" }</button>
                    </div>
                }
            </div>
            <div id="import-map" style="width: 100%; height: 100vh; margin-top:1rem;"></div>
        </div>
    }
}

/// Registered once via `map.on("click", ...)` and reused for every native
/// click event thereafter -- see the state-management comment at the top of
/// `ImportOsmPage` for why `ways_ref`/`selected_ref` are read live via
/// `RefCell` here rather than through a captured `UseStateHandle`.
fn handle_way_click(
    map: &Map,
    event: &JsValue,
    ways_ref: &Rc<RefCell<Option<Vec<api::OsmWayResponse>>>>,
    selected_ref: &Rc<RefCell<HashSet<i64>>>,
    selection_count: &UseStateHandle<usize>,
    name_value: &UseStateHandle<String>,
) {
    let Some(clicked_id) = extract_clicked_way_id(map, event) else {
        return;
    };

    let next_selected = {
        let mut selected = selected_ref.borrow_mut();
        if !selected.remove(&clicked_id) {
            selected.insert(clicked_id);
        }
        selected.clone()
    };

    restyle_ways_layer(map, &next_selected);
    let was_first_selection = next_selected.len() == 1;
    selection_count.set(next_selected.len());

    // Suggest a name the moment the first way gets selected -- an explicit,
    // simple starting point the analyst can freely edit afterward; further
    // selection changes don't fight the analyst's own typing. See the
    // design spec's WASM UI Layer section.
    if was_first_selection {
        if let Some(ways) = &*ways_ref.borrow() {
            let names: HashSet<&str> = ways
                .iter()
                .filter(|w| next_selected.contains(&w.osm_way_id))
                .filter_map(|w| w.tags.get("name").map(|s| s.as_str()))
                .collect();
            if names.len() == 1 {
                let name = *names.iter().next().unwrap();
                name_value.set(name.to_string());
            }
        }
    }
}

fn extract_clicked_way_id(map: &Map, event: &JsValue) -> Option<i64> {
    let point = js_sys::Reflect::get(event, &"point".into()).ok()?;
    let options = js_sys::Object::new();
    let layers = js_sys::Array::of1(&"osm-ways".into());
    js_sys::Reflect::set(&options, &"layers".into(), &layers).ok()?;

    let features = map.query_rendered_features(&point, &options);
    if features.length() == 0 {
        return None;
    }
    let feature = features.get(0);
    let properties = js_sys::Reflect::get(&feature, &"properties".into()).ok()?;
    js_sys::Reflect::get(&properties, &"osm_way_id".into())
        .ok()
        .and_then(|v| v.as_f64())
        .map(|v| v as i64)
}

fn render_ways_layer(map: &Map, ways: &[api::OsmWayResponse]) {
    let features: Vec<serde_json::Value> = ways
        .iter()
        .map(|way| {
            serde_json::json!({
                "type": "Feature",
                "properties": { "osm_way_id": way.osm_way_id },
                "geometry": {
                    "type": "LineString",
                    "coordinates": way.points.iter().map(|p| [p.lon, p.lat]).collect::<Vec<_>>(),
                },
            })
        })
        .collect();
    let collection = serde_json::json!({ "type": "FeatureCollection", "features": features });

    if let Ok(source) = to_js_value(&serde_json::json!({ "type": "geojson", "data": collection })) {
        map.add_source("osm-ways", &source);
    }
    if let Ok(layer) = to_js_value(&osm_ways_layer()) {
        map.add_layer(&layer);
    }
}

fn restyle_ways_layer(map: &Map, selected_ids: &HashSet<i64>) {
    let selected: Vec<i64> = selected_ids.iter().copied().collect();
    if let Ok(expression) = to_js_value(&serde_json::json!([
        "case",
        ["in", ["get", "osm_way_id"], ["literal", selected]],
        "#C8463A",
        "#1D4E89"
    ])) {
        map.set_paint_property("osm-ways", "line-color", &expression);
    }
}

fn osm_ways_layer() -> serde_json::Value {
    serde_json::json!({
        "id": "osm-ways",
        "type": "line",
        "source": "osm-ways",
        "paint": {
            "line-color": "#1D4E89",
            "line-width": 3
        }
    })
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

- [ ] **Step 8: Build and verify against the E2E spec**

```bash
cd crates/corridor_builder_web && cargo fmt --check && cargo clippy --target wasm32-unknown-unknown -- -D warnings && trunk build && cd ../..
```

Expected: all clean, no warnings.

With Postgres running and `mobilispect-pg` up:

```bash
export MOBILISPECT_DATABASE_URL=postgres://mobilispect:mobilispect@localhost:5433/mobilispect
export OVERPASS_BASE_URL=http://localhost:19999
dotenvx run -- cargo run --bin mobilispect-server > /tmp/mobilispect-server.log 2>&1 &
cd e2e && npx playwright test builder-import-osm --project=chromium
```

Expected: both tests in `builder-import-osm.spec.ts` pass. Also re-run the full `builder-*.spec.ts` suite to confirm no regressions: `npx playwright test builder- --project=chromium`.

- [ ] **Step 9: Commit**

```bash
git add crates/corridor_builder_web/src
git commit -m "feat(corridor-design): add OSM import UI, wired to the search/import API"
```

---

## Task 6: Full verification pass

**Files:** none (verification only).

- [ ] **Step 1: Rust workspace**

```bash
cargo build --workspace
cargo clippy -p mobilispect-core -p mobilispect-server --all-targets -- -D warnings
cargo fmt --all -- --check
DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core -p mobilispect-server --no-fail-fast
```

Expected: all succeed cleanly on the crates this plan touches. Unscoped `cargo clippy --workspace`/`cargo nextest run --workspace` will still show pre-existing, unrelated failures in `crates/worker/` and in `corridor_design::{edit,position,repository}`'s still-unimplemented REQ-004/005/006 stubs (confirmed pre-existing at `main`'s tip by the manual-trace plan's own Task 10/final review) — both are out of scope, not this plan's concern.

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
export MOBILISPECT_DATABASE_URL=postgres://mobilispect:mobilispect@localhost:5433/mobilispect
export OVERPASS_BASE_URL=http://localhost:19999
dotenvx run -- cargo run --bin mobilispect-server &
cd e2e && npx playwright test
```

Expected: all `builder-*.spec.ts` files pass across chromium/firefox/webkit, including `builder-import-osm.spec.ts`.

- [ ] **Step 4: Scope check**

```bash
git diff $(git merge-base main HEAD) HEAD --stat
```

Confirm the file list matches this plan's tasks: `crates/core/src/osm/mod.rs`, `crates/core/src/lib.rs`, `docs/ddd/acl.md`, `crates/core/src/corridor_design/{lanes_from_osm,mod}.rs`, `crates/server/src/web/{osm_import,mod}.rs`, `e2e/tests/helpers/overpass-fixture.ts`, `e2e/tests/builder-import-osm.spec.ts`, `crates/corridor_builder_web/src/{maplibre,api,app,pages/mod,pages/region_map,pages/manual_trace,pages/import_osm}.rs`, plus this plan's own design-spec/plan documents — and nothing unexpected.

No commit for this task — verification only. If anything fails, fix it in the relevant earlier task's files and re-run.

---

## Summary

After all 6 tasks: an analyst can open a remix, click "Add corridor" → "Import from OSM" on the region map, pan/zoom to an area, click "Load streets" to fetch OSM way geometry for the current viewport, click one or more contiguous street segments to select them, confirm a name (pre-filled from OSM's `name` tag when available), and import — landing on the corridor's own page (still a placeholder; a later plan turns it into the lane editor) with baseline lanes already derived from each segment's OSM tags. Lane *editing*, cross-section add/reorder, and intersection treatments remain separate follow-up plans per `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`.

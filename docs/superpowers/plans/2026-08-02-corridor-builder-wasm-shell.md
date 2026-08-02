# Corridor Builder WASM Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the WASM "shell" of the street corridor builder — create/open a remix, pick a metro region, see that region's OpenStreetMap street network with edited corridors highlighted, and click an intersection or a corridor to navigate to its (placeholder, for now) editor page.

**Architecture:** A new Yew/Trunk WASM app (`crates/corridor_builder_web`, excluded from the root Cargo workspace) is served by the existing Axum server at `/builder`, talking to five new JSON API endpoints backed by a new `mobilispect_core::remix` module. Map rendering and click hit-testing are delegated to MapLibre GL JS via hand-written `wasm-bindgen` bindings; highlighting is computed once server-side and sent as data.

**Tech Stack:** Rust 2024, Yew 0.23 + yew-router 0.20 (WASM app), Trunk (build tool), MapLibre GL JS 6.1.0 (CDN), wasm-bindgen 0.2 / web-sys 0.3 / js-sys 0.3 / wasm-bindgen-futures 0.4 / gloo-net 0.7, sqlx 0.8 (Postgres, compile-time checked queries), Playwright (E2E, extending the existing `e2e/` suite), pg 8.22 (Node, E2E test data seeding only).

## Global Constraints

- No mocks in tests — integration tests use real Postgres via `testcontainers` (see `.claude/rules/testing.md`).
- Functional Core / Imperative Shell is mandatory: pure logic has no I/O; I/O lives only in `repository.rs`/handler files.
- sqlx queries must be compile-time checked (`query!`/`query_as!`), except test-seeding `RETURNING id` inserts, which use the runtime `sqlx::query_scalar(...)` form — this matches the existing precedent in `crates/core/src/corridor_design/repository.rs`'s own test module.
- ID newtypes only — never raw `i64`/`String` for domain identifiers in `crates/core` or `crates/server` Rust code (HTTP/JSON boundaries use plain `i64`, converted immediately).
- This plan modifies `crates/core/migrations/` — called out explicitly per this project's Safety Rules.
- `crates/corridor_builder_web` has no dependency on `mobilispect-core` (see the design spec's Architecture correction note) and is excluded from the root Cargo workspace.
- Every Askama page, handler, and route from the existing `corridor_design`/`corridor_import` REQ-001–007 scaffolding is untouched by this plan.
- Design spec: `docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md`.

---

## Task 1: Migration and `RemixId`

**Files:**
- Create: `crates/core/migrations/025_remix_region_tables.sql`
- Modify: `crates/core/src/ids.rs`

**Interfaces:**
- Produces: `mobilispect_core::ids::RemixId` (int-backed newtype, same shape as `CorridorId`/`RegionId`).

- [ ] **Step 1: Write the migration**

```sql
-- migrations/025_remix_region_tables.sql
-- Corridor Builder: a remix is a named draft of proposed street corridor
-- changes scoped to one metro region. Regions gain a bounding box for map
-- framing; corridors gain a remix_id association they didn't have before
-- (they previously lived in a flat global namespace). See
-- docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md.

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

- [ ] **Step 2: Add `RemixId`**

In `crates/core/src/ids.rs`, immediately after the existing `int_id!(CrossSectionId);` line (in the "Integer-based IDs" block):

```rust
int_id!(RemixId);
```

- [ ] **Step 3: Verify it compiles and the migration applies**

Run:
```bash
cargo build -p mobilispect-core
DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core ids::tests
```
Expected: both succeed (existing `ids.rs` tests unaffected; migration correctness is exercised indirectly the first time a test calls `test_utils::setup()` in Task 5).

- [ ] **Step 4: Commit**

```bash
git add crates/core/migrations/025_remix_region_tables.sql crates/core/src/ids.rs
git commit -m "feat(remix): add remixes table, region bounding box columns, RemixId"
```

---

## Task 2: `remix` module — pure types and `BoundingBox`

**Files:**
- Create: `crates/core/src/remix/mod.rs`
- Modify: `crates/core/src/lib.rs` (add `pub mod remix;`)

**Interfaces:**
- Produces: `remix::BoundingBox { min_lat, min_lon, max_lat, max_lon }` with `.validate() -> Result<(), BoundingBoxValidationError>`; `remix::Region { id: RegionId, name: String, bounding_box: BoundingBox }`; `remix::Remix { id: RemixId, name: String, region_id: RegionId }`; `remix::CorridorForMap { corridor_id: CorridorId, highlighted: bool, cross_sections: Vec<CrossSectionPointForMap> }`; `remix::CrossSectionPointForMap { cross_section_id: CrossSectionId, lat: f64, lon: f64 }`.
- Consumes: `crate::ids::{CorridorId, CrossSectionId, RegionId, RemixId}` (Task 1).

- [ ] **Step 1: Write the failing tests**

Create `crates/core/src/remix/mod.rs`:

```rust
//! Corridor Builder shell: a remix is a named draft of proposed street
//! corridor changes scoped to one metro region. See
//! `docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md`.

use crate::ids::{CorridorId, CrossSectionId, RegionId, RemixId};

/// A metro region's lat/lon extent, used to frame the region map on load.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BoundingBox {
    pub min_lat: f64,
    pub min_lon: f64,
    pub max_lat: f64,
    pub max_lon: f64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct BoundingBoxValidationError;

impl BoundingBox {
    /// A bounding box is valid when both extents are non-degenerate
    /// (`min < max` on each axis) and all four values fall within valid
    /// WGS84 ranges.
    pub fn validate(&self) -> Result<(), BoundingBoxValidationError> {
        let lat_range_ok = (-90.0..=90.0).contains(&self.min_lat)
            && (-90.0..=90.0).contains(&self.max_lat)
            && self.min_lat < self.max_lat;
        let lon_range_ok = (-180.0..=180.0).contains(&self.min_lon)
            && (-180.0..=180.0).contains(&self.max_lon)
            && self.min_lon < self.max_lon;
        if lat_range_ok && lon_range_ok {
            Ok(())
        } else {
            Err(BoundingBoxValidationError)
        }
    }
}

/// A metro region an analyst can build corridors in, as returned from the repository.
#[derive(Debug, Clone, PartialEq)]
pub struct Region {
    pub id: RegionId,
    pub name: String,
    pub bounding_box: BoundingBox,
}

/// A named draft of proposed street corridor changes, scoped to one region.
#[derive(Debug, Clone, PartialEq)]
pub struct Remix {
    pub id: RemixId,
    pub name: String,
    pub region_id: RegionId,
}

/// One corridor's geometry and highlight state, as needed to render it on
/// the region map. `cross_sections` is ordered by `position`; the first and
/// last entries are the corridor's two "intersection" endpoints (see the
/// design spec's identifier clarification — there is no separate
/// Intersection aggregate yet).
#[derive(Debug, Clone, PartialEq)]
pub struct CorridorForMap {
    pub corridor_id: CorridorId,
    pub highlighted: bool,
    pub cross_sections: Vec<CrossSectionPointForMap>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CrossSectionPointForMap {
    pub cross_section_id: CrossSectionId,
    pub lat: f64,
    pub lon: f64,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn valid_bbox() -> BoundingBox {
        BoundingBox {
            min_lat: 45.40,
            min_lon: -73.70,
            max_lat: 45.60,
            max_lon: -73.50,
        }
    }

    #[test]
    fn valid_bounding_box_passes_validation() {
        assert_eq!(valid_bbox().validate(), Ok(()));
    }

    #[test]
    fn bounding_box_with_min_lat_greater_than_max_lat_is_rejected() {
        let mut bbox = valid_bbox();
        bbox.min_lat = 46.0;
        bbox.max_lat = 45.0;
        assert_eq!(bbox.validate(), Err(BoundingBoxValidationError));
    }

    #[test]
    fn bounding_box_with_equal_min_and_max_lon_is_rejected() {
        let mut bbox = valid_bbox();
        bbox.min_lon = -73.60;
        bbox.max_lon = -73.60;
        assert_eq!(bbox.validate(), Err(BoundingBoxValidationError));
    }

    #[test]
    fn bounding_box_with_out_of_range_latitude_is_rejected() {
        let mut bbox = valid_bbox();
        bbox.max_lat = 95.0;
        assert_eq!(bbox.validate(), Err(BoundingBoxValidationError));
    }

    #[test]
    fn bounding_box_with_out_of_range_longitude_is_rejected() {
        let mut bbox = valid_bbox();
        bbox.min_lon = -185.0;
        assert_eq!(bbox.validate(), Err(BoundingBoxValidationError));
    }
}
```

- [ ] **Step 2: Register the module**

In `crates/core/src/lib.rs`, add `pub mod remix;` alongside the other top-level module declarations (e.g. next to `pub mod corridor_design;`).

- [ ] **Step 3: Run the tests**

Run: `cargo nextest run -p mobilispect-core remix::tests`
Expected: PASS (this task writes the type and its validation together — there's no separate red/green split for a type definition; the tests above are the specification and pass once written correctly).

- [ ] **Step 4: Commit**

```bash
git add crates/core/src/remix/mod.rs crates/core/src/lib.rs
git commit -m "feat(remix): add remix domain types and bounding box validation"
```

---

## Task 3: Highlight-rule predicate

**Files:**
- Create: `crates/core/src/remix/highlight.rs`
- Modify: `crates/core/src/remix/mod.rs` (add `pub mod highlight;`)

**Interfaces:**
- Consumes: `crate::corridor_design::GeometrySource` (existing).
- Produces: `remix::highlight::is_corridor_edited(geometry_source: GeometrySource, created_at: DateTime<Utc>, updated_at: DateTime<Utc>) -> bool`.

- [ ] **Step 1: Write the failing tests**

Create `crates/core/src/remix/highlight.rs`:

```rust
//! Pure predicate for whether a corridor counts as "edited" for the region
//! map's highlight overlay. See the design spec's "Edited corridor" term.

use crate::corridor_design::GeometrySource;
use chrono::{DateTime, Utc};

/// A corridor counts as edited if it was traced manually (inherently
/// authored — there's no pristine baseline to diff against) or if it has
/// been mutated since creation (`updated_at` advanced past `created_at` by
/// one of the not-yet-built segment-editor's add/reorder/edit operations).
pub fn is_corridor_edited(
    geometry_source: GeometrySource,
    created_at: DateTime<Utc>,
    updated_at: DateTime<Utc>,
) -> bool {
    geometry_source == GeometrySource::Manual || updated_at > created_at
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::TimeZone;

    fn ts(seconds: i64) -> DateTime<Utc> {
        Utc.timestamp_opt(seconds, 0).unwrap()
    }

    #[test]
    fn manual_corridor_is_always_edited_regardless_of_timestamps() {
        assert!(is_corridor_edited(GeometrySource::Manual, ts(100), ts(100)));
    }

    #[test]
    fn imported_corridor_untouched_since_creation_is_not_edited() {
        assert!(!is_corridor_edited(
            GeometrySource::Imported,
            ts(100),
            ts(100)
        ));
    }

    #[test]
    fn imported_corridor_mutated_after_creation_is_edited() {
        assert!(is_corridor_edited(
            GeometrySource::Imported,
            ts(100),
            ts(200)
        ));
    }
}
```

- [ ] **Step 2: Register the module**

In `crates/core/src/remix/mod.rs`, add near the top:

```rust
pub mod highlight;
```

- [ ] **Step 3: Run the tests**

Run: `cargo nextest run -p mobilispect-core remix::highlight::tests`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add crates/core/src/remix/highlight.rs crates/core/src/remix/mod.rs
git commit -m "feat(remix): add is_corridor_edited highlight-rule predicate"
```

---

## Task 4: GeoJSON assembly

**Files:**
- Create: `crates/core/src/remix/geojson.rs`
- Modify: `crates/core/src/remix/mod.rs` (add `pub mod geojson;`)
- Modify: `crates/core/Cargo.toml` (no change needed — `serde`/`serde_json` are already dependencies)

**Interfaces:**
- Consumes: `remix::{CorridorForMap, CrossSectionPointForMap}` (Task 2).
- Produces: `remix::geojson::{FeatureCollection, Feature, Geometry}` (all `Serialize`); `remix::geojson::build_corridors_feature_collection(corridors: &[CorridorForMap]) -> FeatureCollection`.

- [ ] **Step 1: Write the failing tests**

Create `crates/core/src/remix/geojson.rs`:

```rust
//! Pure GeoJSON `FeatureCollection` assembly for the region map's corridor
//! overlay. Takes already-fetched `CorridorForMap` rows (`repository.rs`'s
//! concern) and produces the exact JSON shape MapLibre GL JS expects — no
//! I/O here.

use crate::remix::CorridorForMap;
use serde::Serialize;

#[derive(Debug, Serialize)]
pub struct FeatureCollection {
    #[serde(rename = "type")]
    pub kind: &'static str,
    pub features: Vec<Feature>,
}

#[derive(Debug, Serialize)]
pub struct Feature {
    #[serde(rename = "type")]
    pub kind: &'static str,
    pub geometry: Geometry,
    pub properties: serde_json::Value,
}

#[derive(Debug, Serialize)]
#[serde(tag = "type")]
pub enum Geometry {
    LineString { coordinates: Vec<[f64; 2]> },
    Point { coordinates: [f64; 2] },
}

/// Builds the region map's corridor overlay: one `LineString` feature per
/// corridor (properties: `feature_type: "corridor"`, `corridor_id`,
/// `highlighted`) plus one `Point` feature per corridor endpoint — its
/// first and last cross-section (properties: `feature_type:
/// "intersection"`, `cross_section_id`) — see the design spec's
/// intersection-identifier clarification. Corridors with fewer than 2
/// cross-sections contribute no features (nothing to draw or click).
pub fn build_corridors_feature_collection(corridors: &[CorridorForMap]) -> FeatureCollection {
    let mut features = Vec::new();

    for corridor in corridors {
        if corridor.cross_sections.len() < 2 {
            continue;
        }

        let coordinates: Vec<[f64; 2]> = corridor
            .cross_sections
            .iter()
            .map(|cs| [cs.lon, cs.lat])
            .collect();

        features.push(Feature {
            kind: "Feature",
            geometry: Geometry::LineString { coordinates },
            properties: serde_json::json!({
                "feature_type": "corridor",
                "corridor_id": corridor.corridor_id.as_i64(),
                "highlighted": corridor.highlighted,
            }),
        });

        let first = corridor.cross_sections.first().unwrap();
        let last = corridor.cross_sections.last().unwrap();
        for endpoint in [first, last] {
            features.push(Feature {
                kind: "Feature",
                geometry: Geometry::Point {
                    coordinates: [endpoint.lon, endpoint.lat],
                },
                properties: serde_json::json!({
                    "feature_type": "intersection",
                    "cross_section_id": endpoint.cross_section_id.as_i64(),
                }),
            });
        }
    }

    FeatureCollection {
        kind: "FeatureCollection",
        features,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ids::{CorridorId, CrossSectionId};
    use crate::remix::CrossSectionPointForMap;

    fn point(id: i64, lat: f64, lon: f64) -> CrossSectionPointForMap {
        CrossSectionPointForMap {
            cross_section_id: CrossSectionId::from(id),
            lat,
            lon,
        }
    }

    #[test]
    fn corridor_with_three_points_produces_one_line_and_two_endpoint_points() {
        let corridors = vec![CorridorForMap {
            corridor_id: CorridorId::from(1),
            highlighted: true,
            cross_sections: vec![point(10, 45.50, -73.60), point(11, 45.51, -73.59), point(
                12, 45.52, -73.58,
            )],
        }];

        let fc = build_corridors_feature_collection(&corridors);

        assert_eq!(fc.kind, "FeatureCollection");
        assert_eq!(fc.features.len(), 3, "1 LineString + 2 Point endpoints");

        let line = &fc.features[0];
        assert_eq!(line.kind, "Feature");
        match &line.geometry {
            Geometry::LineString { coordinates } => {
                assert_eq!(
                    coordinates,
                    &vec![[-73.60, 45.50], [-73.59, 45.51], [-73.58, 45.52]]
                );
            }
            _ => panic!("expected LineString"),
        }
        assert_eq!(line.properties["feature_type"], "corridor");
        assert_eq!(line.properties["corridor_id"], 1);
        assert_eq!(line.properties["highlighted"], true);

        let first_point = &fc.features[1];
        match &first_point.geometry {
            Geometry::Point { coordinates } => assert_eq!(coordinates, &[-73.60, 45.50]),
            _ => panic!("expected Point"),
        }
        assert_eq!(first_point.properties["feature_type"], "intersection");
        assert_eq!(first_point.properties["cross_section_id"], 10);

        let last_point = &fc.features[2];
        match &last_point.geometry {
            Geometry::Point { coordinates } => assert_eq!(coordinates, &[-73.58, 45.52]),
            _ => panic!("expected Point"),
        }
        assert_eq!(last_point.properties["cross_section_id"], 12);
    }

    #[test]
    fn corridor_with_fewer_than_two_points_contributes_no_features() {
        let corridors = vec![CorridorForMap {
            corridor_id: CorridorId::from(1),
            highlighted: false,
            cross_sections: vec![point(10, 45.50, -73.60)],
        }];

        let fc = build_corridors_feature_collection(&corridors);

        assert_eq!(fc.features.len(), 0);
    }

    #[test]
    fn multiple_corridors_accumulate_features_from_all() {
        let corridors = vec![
            CorridorForMap {
                corridor_id: CorridorId::from(1),
                highlighted: false,
                cross_sections: vec![point(10, 45.50, -73.60), point(11, 45.51, -73.59)],
            },
            CorridorForMap {
                corridor_id: CorridorId::from(2),
                highlighted: true,
                cross_sections: vec![point(20, 46.00, -74.00), point(21, 46.01, -74.01)],
            },
        ];

        let fc = build_corridors_feature_collection(&corridors);

        assert_eq!(fc.features.len(), 6, "2 corridors x (1 line + 2 points)");
    }

    #[test]
    fn empty_input_produces_empty_feature_collection() {
        let fc = build_corridors_feature_collection(&[]);
        assert_eq!(fc.features.len(), 0);
    }
}
```

- [ ] **Step 2: Register the module**

In `crates/core/src/remix/mod.rs`, add:

```rust
pub mod geojson;
```

- [ ] **Step 3: Run the tests**

Run: `cargo nextest run -p mobilispect-core remix::geojson::tests`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add crates/core/src/remix/geojson.rs crates/core/src/remix/mod.rs
git commit -m "feat(remix): add pure GeoJSON FeatureCollection assembly for the region map"
```

---

## Task 5: Repository — DB I/O

**Files:**
- Create: `crates/core/src/remix/repository.rs`
- Modify: `crates/core/src/remix/mod.rs` (add `pub mod repository;`)

**Interfaces:**
- Consumes: `remix::{BoundingBox, Region, Remix, CorridorForMap, CrossSectionPointForMap}` (Task 2), `remix::highlight::is_corridor_edited` (Task 3), `crate::corridor_design::GeometrySource` (existing), `crate::ids::{RegionId, RemixId, CorridorId, CrossSectionId}`.
- Produces:
  - `repository::list_regions_with_bounding_box(pool: &PgPool) -> anyhow::Result<Vec<Region>>`
  - `repository::insert_remix(pool: &PgPool, name: &str, region_id: RegionId) -> anyhow::Result<RemixId>`
  - `repository::list_remixes_for_region(pool: &PgPool, region_id: RegionId) -> anyhow::Result<Vec<Remix>>`
  - `repository::get_remix(pool: &PgPool, remix_id: RemixId) -> anyhow::Result<Option<(Remix, Region)>>`
  - `repository::list_corridors_for_remix(pool: &PgPool, remix_id: RemixId) -> anyhow::Result<Vec<CorridorForMap>>`

- [ ] **Step 1: Write the failing tests**

Create `crates/core/src/remix/repository.rs`:

```rust
//! Remix repository: the imperative I/O shell for regions, remixes, and the
//! corridors that belong to them. Pure logic (highlight rule, GeoJSON
//! assembly) lives in `highlight.rs`/`geojson.rs`; this module persists and
//! reads — no validation or geometry computation happens here.

use sqlx::PgPool;

use crate::corridor_design::GeometrySource;
use crate::ids::{CorridorId, CrossSectionId, RegionId, RemixId};
use crate::remix::highlight::is_corridor_edited;
use crate::remix::{BoundingBox, CorridorForMap, CrossSectionPointForMap, Region, Remix};

/// Regions with a bounding box already set, ordered by name — the only
/// regions that appear in the metro-region picker. See the design spec's
/// note on bounding-box population (manual one-time operator step, no admin
/// UI yet).
pub async fn list_regions_with_bounding_box(pool: &PgPool) -> anyhow::Result<Vec<Region>> {
    let rows = sqlx::query!(
        r#"SELECT id, name, min_lat, min_lon, max_lat, max_lon
           FROM regions
           WHERE min_lat IS NOT NULL AND min_lon IS NOT NULL
             AND max_lat IS NOT NULL AND max_lon IS NOT NULL
           ORDER BY name"#
    )
    .fetch_all(pool)
    .await?;

    Ok(rows
        .into_iter()
        .map(|row| Region {
            id: RegionId::from(row.id),
            name: row.name,
            bounding_box: BoundingBox {
                // Safe: the WHERE clause guarantees these four columns are non-null.
                min_lat: row.min_lat.unwrap(),
                min_lon: row.min_lon.unwrap(),
                max_lat: row.max_lat.unwrap(),
                max_lon: row.max_lon.unwrap(),
            },
        })
        .collect())
}

pub async fn insert_remix(pool: &PgPool, name: &str, region_id: RegionId) -> anyhow::Result<RemixId> {
    let row = sqlx::query!(
        "INSERT INTO remixes (name, region_id) VALUES ($1, $2) RETURNING id",
        name,
        region_id.as_i64(),
    )
    .fetch_one(pool)
    .await?;
    Ok(RemixId::from(row.id))
}

pub async fn list_remixes_for_region(
    pool: &PgPool,
    region_id: RegionId,
) -> anyhow::Result<Vec<Remix>> {
    let rows = sqlx::query!(
        "SELECT id, name, region_id FROM remixes WHERE region_id = $1 ORDER BY updated_at DESC",
        region_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;

    Ok(rows
        .into_iter()
        .map(|row| Remix {
            id: RemixId::from(row.id),
            name: row.name,
            region_id: RegionId::from(row.region_id),
        })
        .collect())
}

/// A remix plus its region (with bounding box), for `GET /api/remixes/:id`.
/// Returns `Ok(None)` if `remix_id` doesn't exist. Returns `Err` (rather
/// than silently defaulting) if the remix's region has no bounding box —
/// that can only happen if an operator cleared a bbox out from under an
/// existing remix, a data-integrity problem worth surfacing loudly.
pub async fn get_remix(pool: &PgPool, remix_id: RemixId) -> anyhow::Result<Option<(Remix, Region)>> {
    let row = sqlx::query!(
        r#"SELECT r.id AS remix_id, r.name AS remix_name, r.region_id,
                  reg.name AS region_name, reg.min_lat, reg.min_lon, reg.max_lat, reg.max_lon
           FROM remixes r
           JOIN regions reg ON reg.id = r.region_id
           WHERE r.id = $1"#,
        remix_id.as_i64(),
    )
    .fetch_optional(pool)
    .await?;

    let Some(row) = row else {
        return Ok(None);
    };

    let (Some(min_lat), Some(min_lon), Some(max_lat), Some(max_lon)) =
        (row.min_lat, row.min_lon, row.max_lat, row.max_lon)
    else {
        anyhow::bail!(
            "remix {} references region {} which has no bounding box set",
            remix_id,
            row.region_id
        );
    };

    Ok(Some((
        Remix {
            id: RemixId::from(row.remix_id),
            name: row.remix_name,
            region_id: RegionId::from(row.region_id),
        },
        Region {
            id: RegionId::from(row.region_id),
            name: row.region_name,
            bounding_box: BoundingBox {
                min_lat,
                min_lon,
                max_lat,
                max_lon,
            },
        },
    )))
}

/// A remix's corridors with their ordered cross-sections and highlight
/// state, for the region map's GeoJSON overlay (see `geojson.rs`).
pub async fn list_corridors_for_remix(
    pool: &PgPool,
    remix_id: RemixId,
) -> anyhow::Result<Vec<CorridorForMap>> {
    let rows = sqlx::query!(
        r#"SELECT c.id AS corridor_id, c.geometry_source, c.created_at, c.updated_at,
                  cs.id AS cross_section_id, cs.lat, cs.lon
           FROM corridors c
           JOIN cross_sections cs ON cs.corridor_id = c.id
           WHERE c.remix_id = $1
           ORDER BY c.id, cs.position"#,
        remix_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;

    let mut corridors: Vec<CorridorForMap> = Vec::new();

    for row in rows {
        let geometry_source = GeometrySource::from_db_str(&row.geometry_source).ok_or_else(|| {
            anyhow::anyhow!("unknown geometry_source value: {}", row.geometry_source)
        })?;
        let highlighted = is_corridor_edited(geometry_source, row.created_at, row.updated_at);
        let point = CrossSectionPointForMap {
            cross_section_id: CrossSectionId::from(row.cross_section_id),
            lat: row.lat,
            lon: row.lon,
        };
        let corridor_id = CorridorId::from(row.corridor_id);

        match corridors.last_mut() {
            Some(last) if last.corridor_id == corridor_id => {
                last.cross_sections.push(point);
            }
            _ => corridors.push(CorridorForMap {
                corridor_id,
                highlighted,
                cross_sections: vec![point],
            }),
        }
    }

    Ok(corridors)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;

    async fn seed_region(
        pool: &PgPool,
        id: i64,
        name: &str,
        bbox: Option<BoundingBox>,
    ) -> RegionId {
        match bbox {
            Some(b) => {
                sqlx::query!(
                    "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon)
                     VALUES ($1, $2, 'UTC', $3, $4, $5, $6)",
                    id,
                    name,
                    b.min_lat,
                    b.min_lon,
                    b.max_lat,
                    b.max_lon,
                )
                .execute(pool)
                .await
                .unwrap();
            }
            None => {
                sqlx::query!(
                    "INSERT INTO regions (id, name, timezone) VALUES ($1, $2, 'UTC')",
                    id,
                    name,
                )
                .execute(pool)
                .await
                .unwrap();
            }
        }
        RegionId::from(id)
    }

    fn sample_bbox() -> BoundingBox {
        BoundingBox {
            min_lat: 45.40,
            min_lon: -73.70,
            max_lat: 45.60,
            max_lon: -73.50,
        }
    }

    #[tokio::test]
    async fn list_regions_with_bounding_box_excludes_regions_missing_a_bbox() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        seed_region(pool, 1, "Has Bbox", Some(sample_bbox())).await;
        seed_region(pool, 2, "No Bbox", None).await;

        let regions = list_regions_with_bounding_box(pool).await.unwrap();

        assert_eq!(regions.len(), 1);
        assert_eq!(regions[0].name, "Has Bbox");
    }

    #[tokio::test]
    async fn list_regions_with_bounding_box_returns_correct_bbox_values() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let bbox = sample_bbox();
        seed_region(pool, 1, "Test Region", Some(bbox)).await;

        let regions = list_regions_with_bounding_box(pool).await.unwrap();

        assert_eq!(regions.len(), 1);
        assert_eq!(regions[0].bounding_box, bbox);
    }

    #[tokio::test]
    async fn insert_remix_persists_a_row_with_the_correct_region() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;

        let remix_id = insert_remix(pool, "Downtown bike lanes", region_id)
            .await
            .unwrap();

        let row: (String, i64) =
            sqlx::query_as("SELECT name, region_id FROM remixes WHERE id = $1")
                .bind(remix_id.as_i64())
                .fetch_one(pool)
                .await
                .unwrap();
        assert_eq!(row.0, "Downtown bike lanes");
        assert_eq!(row.1, region_id.as_i64());
    }

    #[tokio::test]
    async fn list_remixes_for_region_orders_most_recently_updated_first() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;

        let older_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id, updated_at) \
             VALUES ($1, $2, now() - interval '1 day') RETURNING id",
        )
        .bind("Older remix")
        .bind(region_id.as_i64())
        .fetch_one(pool)
        .await
        .unwrap();
        let newer_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id, updated_at) VALUES ($1, $2, now()) RETURNING id",
        )
        .bind("Newer remix")
        .bind(region_id.as_i64())
        .fetch_one(pool)
        .await
        .unwrap();

        let remixes = list_remixes_for_region(pool, region_id).await.unwrap();

        assert_eq!(remixes.len(), 2);
        assert_eq!(remixes[0].id, RemixId::from(newer_id));
        assert_eq!(remixes[1].id, RemixId::from(older_id));
    }

    #[tokio::test]
    async fn get_remix_returns_none_for_unknown_id() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;

        let result = get_remix(pool, RemixId::from(999_999)).await.unwrap();

        assert!(result.is_none());
    }

    #[tokio::test]
    async fn get_remix_returns_remix_and_region_with_bbox() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_id = insert_remix(pool, "Downtown bike lanes", region_id)
            .await
            .unwrap();

        let (remix, region) = get_remix(pool, remix_id).await.unwrap().unwrap();

        assert_eq!(remix.id, remix_id);
        assert_eq!(remix.name, "Downtown bike lanes");
        assert_eq!(region.id, region_id);
        assert_eq!(region.name, "Test Region");
        assert_eq!(region.bounding_box, sample_bbox());
    }

    #[tokio::test]
    async fn get_remix_errors_when_regions_bbox_was_cleared_after_remix_creation() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_id = insert_remix(pool, "Downtown bike lanes", region_id)
            .await
            .unwrap();
        sqlx::query(
            "UPDATE regions SET min_lat = NULL, min_lon = NULL, max_lat = NULL, max_lon = NULL \
             WHERE id = $1",
        )
        .bind(region_id.as_i64())
        .execute(pool)
        .await
        .unwrap();

        let result = get_remix(pool, remix_id).await;

        assert!(result.is_err());
    }

    async fn seed_corridor(
        pool: &PgPool,
        remix_id: RemixId,
        name: &str,
        geometry_source: &str,
        points: &[(f64, f64)],
    ) -> CorridorId {
        let corridor_id: i64 = sqlx::query_scalar(
            "INSERT INTO corridors (name, geometry_source, remix_id) VALUES ($1, $2, $3) \
             RETURNING id",
        )
        .bind(name)
        .bind(geometry_source)
        .bind(remix_id.as_i64())
        .fetch_one(pool)
        .await
        .unwrap();

        for (i, (lat, lon)) in points.iter().enumerate() {
            sqlx::query(
                "INSERT INTO cross_sections (corridor_id, position, lat, lon) \
                 VALUES ($1, $2, $3, $4)",
            )
            .bind(corridor_id)
            .bind(i as f64)
            .bind(lat)
            .bind(lon)
            .execute(pool)
            .await
            .unwrap();
        }

        CorridorId::from(corridor_id)
    }

    #[tokio::test]
    async fn list_corridors_for_remix_returns_ordered_cross_sections() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_id = insert_remix(pool, "Test Remix", region_id).await.unwrap();
        let corridor_id = seed_corridor(
            pool,
            remix_id,
            "Main St",
            "manual",
            &[(45.50, -73.60), (45.51, -73.59), (45.52, -73.58)],
        )
        .await;

        let corridors = list_corridors_for_remix(pool, remix_id).await.unwrap();

        assert_eq!(corridors.len(), 1);
        assert_eq!(corridors[0].corridor_id, corridor_id);
        assert_eq!(corridors[0].cross_sections.len(), 3);
        assert_eq!(corridors[0].cross_sections[0].lat, 45.50);
        assert_eq!(corridors[0].cross_sections[2].lat, 45.52);
    }

    #[tokio::test]
    async fn list_corridors_for_remix_marks_manual_corridors_highlighted() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_id = insert_remix(pool, "Test Remix", region_id).await.unwrap();
        seed_corridor(
            pool,
            remix_id,
            "Manual corridor",
            "manual",
            &[(45.50, -73.60), (45.51, -73.59)],
        )
        .await;

        let corridors = list_corridors_for_remix(pool, remix_id).await.unwrap();

        assert_eq!(corridors.len(), 1);
        assert!(corridors[0].highlighted);
    }

    #[tokio::test]
    async fn list_corridors_for_remix_marks_untouched_imported_corridors_not_highlighted() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_id = insert_remix(pool, "Test Remix", region_id).await.unwrap();
        seed_corridor(
            pool,
            remix_id,
            "Imported corridor",
            "imported",
            &[(45.50, -73.60), (45.51, -73.59)],
        )
        .await;

        let corridors = list_corridors_for_remix(pool, remix_id).await.unwrap();

        assert_eq!(corridors.len(), 1);
        assert!(!corridors[0].highlighted);
    }

    #[tokio::test]
    async fn list_corridors_for_remix_excludes_corridors_outside_the_remix() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_a = insert_remix(pool, "Remix A", region_id).await.unwrap();
        let remix_b = insert_remix(pool, "Remix B", region_id).await.unwrap();
        seed_corridor(
            pool,
            remix_a,
            "In remix A",
            "manual",
            &[(45.50, -73.60), (45.51, -73.59)],
        )
        .await;
        seed_corridor(
            pool,
            remix_b,
            "In remix B",
            "manual",
            &[(46.00, -74.00), (46.01, -74.01)],
        )
        .await;

        let corridors = list_corridors_for_remix(pool, remix_a).await.unwrap();

        assert_eq!(corridors.len(), 1);
        assert_eq!(corridors[0].cross_sections[0].lat, 45.50);
    }
}
```

- [ ] **Step 2: Register the module**

In `crates/core/src/remix/mod.rs`, add:

```rust
pub mod repository;
```

- [ ] **Step 3: Run the tests and verify they pass**

Run:
```bash
DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core remix::repository::tests
```
Expected: all PASS. (Unlike the rest of this codebase's Loop-A/Loop-B split, this task writes the real implementation directly — there is no separate stub-then-implement pass here.) If `sqlx::query!`'s inferred column nullability doesn't match a field's expected type, fix the Rust-side type (e.g. wrap in `Option`) to match what the compiler reports and re-run — this is a normal, expected part of using compile-time-checked queries against a real schema.

- [ ] **Step 4: Commit**

```bash
git add crates/core/src/remix/repository.rs crates/core/src/remix/mod.rs
git commit -m "feat(remix): add repository for regions, remixes, and remix corridors"
```

---

## Task 6: JSON API handlers and routes

**Files:**
- Create: `crates/server/src/web/remix_api.rs`
- Modify: `crates/server/src/web/mod.rs`

**Interfaces:**
- Consumes: `mobilispect_core::remix::{repository, geojson}` (Tasks 4–5), `mobilispect_core::ids::{RegionId, RemixId}`, `crate::web::AppState` (existing).
- Produces: five Axum handlers registered on routes `GET /api/regions`, `GET /api/regions/:region_id/remixes`, `POST /api/remixes`, `GET /api/remixes/:remix_id`, `GET /api/remixes/:remix_id/corridors`.

- [ ] **Step 1: Write the failing tests**

Create `crates/server/src/web/remix_api.rs`:

```rust
//! JSON API for the Corridor Builder WASM shell: regions, remixes, and a
//! remix's corridors as GeoJSON. See
//! `docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md`.

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::Json;

use mobilispect_core::ids::{RegionId, RemixId};
use mobilispect_core::remix::{geojson, repository};

use crate::web::AppState;

type ApiError = (StatusCode, Json<serde_json::Value>);

fn internal_error(context: &str, err: anyhow::Error) -> ApiError {
    tracing::error!(error = %err, "{context}");
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        Json(serde_json::json!({ "error": err.to_string() })),
    )
}

#[derive(Debug, serde::Serialize)]
pub struct BoundingBoxResponse {
    pub min_lat: f64,
    pub min_lon: f64,
    pub max_lat: f64,
    pub max_lon: f64,
}

impl From<mobilispect_core::remix::BoundingBox> for BoundingBoxResponse {
    fn from(bbox: mobilispect_core::remix::BoundingBox) -> Self {
        Self {
            min_lat: bbox.min_lat,
            min_lon: bbox.min_lon,
            max_lat: bbox.max_lat,
            max_lon: bbox.max_lon,
        }
    }
}

#[derive(Debug, serde::Serialize)]
pub struct RegionResponse {
    pub id: i64,
    pub name: String,
    pub bbox: BoundingBoxResponse,
}

impl From<mobilispect_core::remix::Region> for RegionResponse {
    fn from(region: mobilispect_core::remix::Region) -> Self {
        Self {
            id: region.id.as_i64(),
            name: region.name,
            bbox: region.bounding_box.into(),
        }
    }
}

/// `GET /api/regions` — regions with a bounding box set, for the
/// metro-region picker.
pub async fn list_regions(
    State(state): State<AppState>,
) -> Result<Json<Vec<RegionResponse>>, ApiError> {
    let regions = repository::list_regions_with_bounding_box(&state.db.pool)
        .await
        .map_err(|e| internal_error("list_regions", e))?;
    Ok(Json(regions.into_iter().map(RegionResponse::from).collect()))
}

#[derive(Debug, serde::Serialize)]
pub struct RemixSummaryResponse {
    pub id: i64,
    pub name: String,
}

/// `GET /api/regions/:region_id/remixes` — a region's remixes,
/// most-recently-updated first.
pub async fn list_region_remixes(
    State(state): State<AppState>,
    Path(region_id): Path<i64>,
) -> Result<Json<Vec<RemixSummaryResponse>>, ApiError> {
    let remixes = repository::list_remixes_for_region(&state.db.pool, RegionId::from(region_id))
        .await
        .map_err(|e| internal_error("list_region_remixes", e))?;
    Ok(Json(
        remixes
            .into_iter()
            .map(|r| RemixSummaryResponse {
                id: r.id.as_i64(),
                name: r.name,
            })
            .collect(),
    ))
}

#[derive(Debug, serde::Deserialize)]
pub struct CreateRemixRequest {
    pub name: String,
    pub region_id: i64,
}

#[derive(Debug, serde::Serialize)]
pub struct CreateRemixResponse {
    pub id: i64,
}

/// `POST /api/remixes` — creates a remix. Rejects a blank name or a region
/// with no bounding box set (400 either way).
pub async fn create_remix(
    State(state): State<AppState>,
    Json(req): Json<CreateRemixRequest>,
) -> Result<(StatusCode, Json<CreateRemixResponse>), ApiError> {
    if req.name.trim().is_empty() {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "error": "name must not be blank" })),
        ));
    }

    let regions = repository::list_regions_with_bounding_box(&state.db.pool)
        .await
        .map_err(|e| internal_error("create_remix: list_regions_with_bounding_box", e))?;
    if !regions.iter().any(|r| r.id.as_i64() == req.region_id) {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "error": "region has no bounding box set" })),
        ));
    }

    let remix_id = repository::insert_remix(
        &state.db.pool,
        req.name.trim(),
        RegionId::from(req.region_id),
    )
    .await
    .map_err(|e| internal_error("create_remix: insert_remix", e))?;

    Ok((
        StatusCode::CREATED,
        Json(CreateRemixResponse {
            id: remix_id.as_i64(),
        }),
    ))
}

#[derive(Debug, serde::Serialize)]
pub struct RemixDetailResponse {
    pub id: i64,
    pub name: String,
    pub region: RegionResponse,
}

/// `GET /api/remixes/:remix_id` — a remix plus its region (with bounding box).
pub async fn get_remix(
    State(state): State<AppState>,
    Path(remix_id): Path<i64>,
) -> Result<Json<RemixDetailResponse>, ApiError> {
    let found = repository::get_remix(&state.db.pool, RemixId::from(remix_id))
        .await
        .map_err(|e| internal_error("get_remix", e))?;

    let Some((remix, region)) = found else {
        return Err((
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({ "error": "remix not found" })),
        ));
    };

    Ok(Json(RemixDetailResponse {
        id: remix.id.as_i64(),
        name: remix.name,
        region: RegionResponse::from(region),
    }))
}

/// `GET /api/remixes/:remix_id/corridors` — the remix's corridors as a
/// GeoJSON `FeatureCollection` for the region map's overlay.
pub async fn list_remix_corridors(
    State(state): State<AppState>,
    Path(remix_id): Path<i64>,
) -> Result<Json<geojson::FeatureCollection>, ApiError> {
    let corridors = repository::list_corridors_for_remix(&state.db.pool, RemixId::from(remix_id))
        .await
        .map_err(|e| internal_error("list_remix_corridors", e))?;
    Ok(Json(geojson::build_corridors_feature_collection(&corridors)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::web::SetupState;
    use axum::body::to_bytes;
    use axum::response::IntoResponse;
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

    async fn seed_region_with_bbox(state: &AppState, id: i64, name: &str) -> RegionId {
        sqlx::query(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon) \
             VALUES ($1, $2, 'UTC', 45.40, -73.70, 45.60, -73.50)",
        )
        .bind(id)
        .bind(name)
        .execute(&state.db.pool)
        .await
        .unwrap();
        RegionId::from(id)
    }

    #[tokio::test]
    async fn list_regions_returns_only_regions_with_a_bbox() {
        let (state, _td) = test_state().await;
        seed_region_with_bbox(&state, 1, "Has Bbox").await;
        sqlx::query("INSERT INTO regions (id, name, timezone) VALUES (2, 'No Bbox', 'UTC')")
            .execute(&state.db.pool)
            .await
            .unwrap();

        let response = list_regions(State(state)).await.unwrap();

        assert_eq!(response.0.len(), 1);
        assert_eq!(response.0[0].name, "Has Bbox");
    }

    #[tokio::test]
    async fn create_remix_with_blank_name_returns_400() {
        let (state, _td) = test_state().await;
        let region_id = seed_region_with_bbox(&state, 1, "Test Region").await;

        let response = create_remix(
            State(state),
            Json(CreateRemixRequest {
                name: "   ".to_string(),
                region_id: region_id.as_i64(),
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn create_remix_with_region_missing_bbox_returns_400() {
        let (state, _td) = test_state().await;
        sqlx::query("INSERT INTO regions (id, name, timezone) VALUES (1, 'No Bbox', 'UTC')")
            .execute(&state.db.pool)
            .await
            .unwrap();

        let response = create_remix(
            State(state),
            Json(CreateRemixRequest {
                name: "Test Remix".to_string(),
                region_id: 1,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn create_remix_happy_path_returns_201_with_id() {
        let (state, _td) = test_state().await;
        let region_id = seed_region_with_bbox(&state, 1, "Test Region").await;

        let response = create_remix(
            State(state),
            Json(CreateRemixRequest {
                name: "Downtown bike lanes".to_string(),
                region_id: region_id.as_i64(),
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0, StatusCode::CREATED);
        assert!(response.1.id > 0);
    }

    #[tokio::test]
    async fn get_remix_for_unknown_id_returns_404() {
        let (state, _td) = test_state().await;

        let response = get_remix(State(state), Path(999_999)).await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::NOT_FOUND);
    }

    #[tokio::test]
    async fn list_remix_corridors_for_remix_with_no_corridors_returns_empty_feature_collection() {
        let (state, _td) = test_state().await;
        let region_id = seed_region_with_bbox(&state, 1, "Test Region").await;
        let remix_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id) VALUES ('Empty Remix', $1) RETURNING id",
        )
        .bind(region_id.as_i64())
        .fetch_one(&state.db.pool)
        .await
        .unwrap();

        let response = list_remix_corridors(State(state), Path(remix_id))
            .await
            .unwrap();

        assert_eq!(response.0.features.len(), 0);
    }

    // Kept as a body-shape smoke test rather than deep JSON assertions —
    // `remix::geojson::build_corridors_feature_collection`'s own unit tests
    // already cover the exact shape.
    #[tokio::test]
    async fn list_remix_corridors_response_is_valid_json() {
        let (state, _td) = test_state().await;
        let region_id = seed_region_with_bbox(&state, 1, "Test Region").await;
        let remix_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id) VALUES ('Test Remix', $1) RETURNING id",
        )
        .bind(region_id.as_i64())
        .fetch_one(&state.db.pool)
        .await
        .unwrap();

        let response = list_remix_corridors(State(state), Path(remix_id))
            .await
            .unwrap()
            .into_response();
        let bytes = to_bytes(response.into_body(), usize::MAX).await.unwrap();
        let parsed: serde_json::Value = serde_json::from_slice(&bytes).unwrap();

        assert_eq!(parsed["type"], "FeatureCollection");
    }
}
```

- [ ] **Step 2: Register the module and routes**

In `crates/server/src/web/mod.rs`:

Change the import line:
```rust
use axum::{Router, routing::get};
```
to:
```rust
use axum::{Router, routing::{get, post}};
use tower_http::services::{ServeDir, ServeFile};
```

Add the module declaration near the other `mod` lines:
```rust
mod remix_api;
```

In `build_router`, add these routes before the `.layer(...)` calls (after the existing `.route("/api/routes/speed", ...)` line):

```rust
        .route("/api/regions", get(remix_api::list_regions))
        .route(
            "/api/regions/:region_id/remixes",
            get(remix_api::list_region_remixes),
        )
        .route("/api/remixes", post(remix_api::create_remix))
        .route("/api/remixes/:remix_id", get(remix_api::get_remix))
        .route(
            "/api/remixes/:remix_id/corridors",
            get(remix_api::list_remix_corridors),
        )
```

(The `/builder` static-file mount is added in Task 8, once the WASM crate actually produces a `dist/` directory to serve.)

- [ ] **Step 3: Run the tests**

Run:
```bash
DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-server remix_api::tests
```
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add crates/server/src/web/remix_api.rs crates/server/src/web/mod.rs
git commit -m "feat(remix): add JSON API for regions, remixes, and remix corridors"
```

---

## Task 7: E2E specs (written first, failing)

Per this project's own precedent for browser-facing work (see `IMPLEMENTATION_CHECKLIST.md`'s Loop A), these Playwright specs are written and confirmed failing *before* the WASM app exists — they'll fail today with a 404/timeout against `/builder`, which is failing for the right reason. Tasks 8–10 make them pass one by one.

**Files:**
- Create: `e2e/tests/helpers/db.ts`
- Create: `e2e/tests/builder-create-remix.spec.ts`
- Create: `e2e/tests/builder-open-remix.spec.ts`
- Create: `e2e/tests/builder-click-routing.spec.ts`
- Create: `e2e/tests/builder-graceful-degradation.spec.ts`
- Modify: `e2e/package.json` (add `pg` devDependency)

**Interfaces:**
- Consumes: a running `mobilispect-server` on `localhost:3000` (existing `e2e/playwright.config.ts` `baseURL`), with `/setup` already completed (existing precondition for all specs in this suite) and region `id = 1` present.
- Produces: nothing consumed by later Rust tasks — this is the acceptance-test layer for Tasks 8–10.

- [ ] **Step 1: Add the `pg` devDependency**

In `e2e/package.json`, add to `devDependencies`:
```json
"pg": "^8.22.0"
```
Run: `cd e2e && npm install`

- [ ] **Step 2: Write the DB seeding helper**

Create `e2e/tests/helpers/db.ts`:

```typescript
import { Client } from 'pg';

/**
 * Direct DB access for E2E test data setup only — the Corridor Builder
 * shell intentionally has no API for seeding regions/corridors (see
 * docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md's
 * "Out of Scope": no admin UI for bounding boxes, and corridor creation
 * belongs to the not-yet-built segment-editor). Mirrors how this repo's
 * Rust integration tests seed fixtures with raw SQL directly against the
 * test database.
 */
export async function withDb<T>(fn: (client: Client) => Promise<T>): Promise<T> {
  const client = new Client({
    connectionString:
      process.env.DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/mobilispect',
  });
  await client.connect();
  try {
    return await fn(client);
  } finally {
    await client.end();
  }
}

/** Ensures region id=1 (the single region this repo's first-launch setup
 * creates) has a bounding box, so it appears in the metro-region picker. */
export async function ensureRegionHasBoundingBox(): Promise<void> {
  await withDb(async (client) => {
    await client.query(
      `UPDATE regions SET min_lat = 45.40, min_lon = -73.70, max_lat = 45.60, max_lon = -73.50
       WHERE id = 1`
    );
  });
}
```

- [ ] **Step 3: Write the create-remix spec**

Create `e2e/tests/builder-create-remix.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox } from './helpers/db';

/**
 * Corridor Builder WASM shell — create-remix flow (see
 * docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md,
 * "User Flow" steps 1-2). Written before the /builder app exists, so it
 * fails today for the correct reason (404/timeout), matching this repo's
 * established Loop-A-style precedent (see req-001-import.spec.ts).
 */

test.beforeAll(async () => {
  await ensureRegionHasBoundingBox();
});

test.describe('Corridor Builder: create remix', () => {
  test('creating a remix navigates to its region map', async ({ page }) => {
    await page.goto('/builder');

    await page.getByRole('button', { name: 'Create remix' }).click();
    await page.getByLabel('Metro region').selectOption({ label: 'Test Region' });
    await page.getByLabel('Remix name').fill('Downtown bike lane proposal');
    await page.getByRole('button', { name: 'Create' }).click();

    await expect(page).toHaveURL(/\/builder\/remix\/\d+$/);
    await expect(page.locator('.maplibregl-canvas')).toBeVisible();
  });

  test('blank remix name is rejected without navigating', async ({ page }) => {
    await page.goto('/builder');

    await page.getByRole('button', { name: 'Create remix' }).click();
    await page.getByLabel('Metro region').selectOption({ label: 'Test Region' });
    await page.getByRole('button', { name: 'Create' }).click();

    await expect(page).toHaveURL('/builder');
    await expect(page.getByText('name must not be blank')).toBeVisible();
  });
});
```

- [ ] **Step 4: Write the open-remix spec**

Create `e2e/tests/builder-open-remix.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

/**
 * Corridor Builder WASM shell — open-remix flow (see design spec's "User
 * Flow" step 3): pick a region, see its remix list, select one, land on
 * its region map.
 */

let seededRemixId: number;

test.beforeAll(async () => {
  await ensureRegionHasBoundingBox();
  await withDb(async (client) => {
    const result = await client.query(
      `INSERT INTO remixes (name, region_id) VALUES ('Open Flow Test Remix', 1) RETURNING id`
    );
    seededRemixId = result.rows[0].id;
  });
});

test.afterAll(async () => {
  await withDb(async (client) => {
    await client.query(`DELETE FROM remixes WHERE id = $1`, [seededRemixId]);
  });
});

test.describe('Corridor Builder: open remix', () => {
  test('selecting a region lists its remixes, and opening one loads its map', async ({
    page,
  }) => {
    await page.goto('/builder');

    await page.getByRole('button', { name: 'Open remix' }).click();
    await page.getByLabel('Metro region').selectOption({ label: 'Test Region' });

    const remixLink = page.getByRole('link', { name: 'Open Flow Test Remix' });
    await expect(remixLink).toBeVisible();
    await remixLink.click();

    await expect(page).toHaveURL(`/builder/remix/${seededRemixId}`);
    await expect(page.locator('.maplibregl-canvas')).toBeVisible();
  });
});
```

- [ ] **Step 5: Write the click-routing spec**

Create `e2e/tests/builder-click-routing.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

/**
 * Corridor Builder WASM shell — click-routing flow (see design spec's User
 * Flow steps 5-6). Seeds a remix with one corridor directly via SQL, since
 * this shell has no API for creating corridors (that's the segment-editor
 * follow-up spec's job). Uses `window.__corridorBuilderMap.project()` (a
 * test-only hook exposed by the app — see region_map.rs) to compute exact
 * click pixel coordinates, since MapLibre's pan/zoom-to-fit means a
 * corridor's screen position isn't otherwise predictable from outside the
 * page.
 */

let remixId: number;
let corridorId: number;
const CORRIDOR_START = { lat: 45.50, lon: -73.60 };
const CORRIDOR_END = { lat: 45.52, lon: -73.58 };

test.beforeAll(async () => {
  await ensureRegionHasBoundingBox();
  await withDb(async (client) => {
    const remixResult = await client.query(
      `INSERT INTO remixes (name, region_id) VALUES ('Click Routing Test Remix', 1) RETURNING id`
    );
    remixId = remixResult.rows[0].id;

    const corridorResult = await client.query(
      `INSERT INTO corridors (name, geometry_source, remix_id) VALUES ('Test Corridor', 'manual', $1) RETURNING id`,
      [remixId]
    );
    corridorId = corridorResult.rows[0].id;

    await client.query(
      `INSERT INTO cross_sections (corridor_id, position, lat, lon) VALUES
         ($1, 0, $2, $3), ($1, 1, $4, $5), ($1, 2, $6, $7)`,
      [
        corridorId,
        CORRIDOR_START.lat,
        CORRIDOR_START.lon,
        45.51,
        -73.59,
        CORRIDOR_END.lat,
        CORRIDOR_END.lon,
      ]
    );
  });
});

test.afterAll(async () => {
  await withDb(async (client) => {
    await client.query(`DELETE FROM corridors WHERE id = $1`, [corridorId]);
    await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
  });
});

test.describe('Corridor Builder: click routing', () => {
  test('clicking a corridor line navigates to its placeholder page', async ({ page }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);

    const midpoint = {
      lat: (CORRIDOR_START.lat + CORRIDOR_END.lat) / 2,
      lon: (CORRIDOR_START.lon + CORRIDOR_END.lon) / 2,
    };
    const px = await page.evaluate(({ lat, lon }) => {
      const point = (window as any).__corridorBuilderMap.project([lon, lat]);
      return { x: point.x, y: point.y };
    }, midpoint);

    await page.locator('.maplibregl-canvas').click({ position: px });

    await expect(page).toHaveURL(`/builder/remix/${remixId}/corridor/${corridorId}`);
    await expect(page.getByText('editor coming soon')).toBeVisible();
  });

  test('clicking an intersection point navigates to its placeholder page', async ({ page }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);

    const px = await page.evaluate((lonLat) => {
      const point = (window as any).__corridorBuilderMap.project(lonLat);
      return { x: point.x, y: point.y };
    }, [CORRIDOR_START.lon, CORRIDOR_START.lat]);

    await page.locator('.maplibregl-canvas').click({ position: px });

    await expect(page).toHaveURL(new RegExp(`/builder/remix/${remixId}/intersection/\\d+$`));
    await expect(page.getByText('editor coming soon')).toBeVisible();
  });
});
```

- [ ] **Step 6: Write the graceful-degradation spec**

Create `e2e/tests/builder-graceful-degradation.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox } from './helpers/db';

/**
 * Corridor Builder WASM shell — WebGL graceful degradation (see design
 * spec's Error Handling table). Scoped to what this shell's map actually
 * depends on (WebGL, for MapLibre GL JS), distinct from the existing
 * canvas/Pointer-Events feature-detection pattern in feature-detection.spec.ts
 * (which covers the separate, canvas-based corridor segment editor).
 */

test.beforeAll(async () => {
  await ensureRegionHasBoundingBox();
});

test.describe('Corridor Builder: graceful degradation', () => {
  test('shows a fallback message when WebGL is unavailable', async ({ browser }) => {
    const context = await browser.newContext();
    await context.addInitScript(() => {
      // Force HTMLCanvasElement.getContext('webgl') to return null, simulating
      // a browser/GPU without WebGL support.
      const original = HTMLCanvasElement.prototype.getContext;
      // @ts-expect-error overriding for test purposes
      HTMLCanvasElement.prototype.getContext = function (type: string, ...rest: unknown[]) {
        if (type === 'webgl' || type === 'webgl2') {
          return null;
        }
        return original.apply(this, [type, ...rest]);
      };
    });
    const page = await context.newPage();

    await page.goto('/builder');
    await page.getByRole('button', { name: 'Create remix' }).click();
    await page.getByLabel('Metro region').selectOption({ label: 'Test Region' });
    await page.getByLabel('Remix name').fill('WebGL fallback test');
    await page.getByRole('button', { name: 'Create' }).click();

    await expect(page.getByText("doesn't support WebGL")).toBeVisible();
    await expect(page.locator('.maplibregl-canvas')).toHaveCount(0);

    await context.close();
  });
});
```

- [ ] **Step 7: Confirm all four specs fail for the right reason**

Run:
```bash
cd e2e && npx playwright test builder- --project=chromium
```
Expected: all fail — with navigation/timeout errors against `/builder` (404, since the route doesn't exist yet), not syntax or assertion-logic errors. Confirm via `npx playwright test builder- --project=chromium --list` that all specs are discovered with no parse errors first.

- [ ] **Step 8: Commit**

```bash
git add e2e/package.json e2e/package-lock.json e2e/tests/helpers/db.ts e2e/tests/builder-create-remix.spec.ts e2e/tests/builder-open-remix.spec.ts e2e/tests/builder-click-routing.spec.ts e2e/tests/builder-graceful-degradation.spec.ts
git commit -m "test(corridor-builder): add failing E2E specs for the WASM shell"
```

---

## Task 8: Scaffold the WASM crate and Axum static serving

**Files:**
- Create: `crates/corridor_builder_web/Cargo.toml`
- Create: `crates/corridor_builder_web/index.html`
- Create: `crates/corridor_builder_web/Trunk.toml`
- Create: `crates/corridor_builder_web/src/main.rs`
- Create: `crates/corridor_builder_web/src/app.rs`
- Modify: `Cargo.toml` (root — exclude the new crate from the workspace)
- Modify: `crates/server/src/web/mod.rs` (add the `/builder` static mount)

**Interfaces:**
- Produces: a booting Yew app at `/builder` rendering a "Corridor Builder" heading (routing/pages come in Task 9).

- [ ] **Step 1: Exclude the crate from the root workspace**

In the root `Cargo.toml`, change:
```toml
[workspace]
members = ["crates/core", "crates/server", "crates/worker"]
resolver = "2"
```
to:
```toml
[workspace]
members = ["crates/core", "crates/server", "crates/worker"]
exclude = ["crates/corridor_builder_web"]
resolver = "2"
```

- [ ] **Step 2: Write the crate's Cargo.toml**

Create `crates/corridor_builder_web/Cargo.toml`:

```toml
[package]
name = "corridor-builder-web"
version = "0.1.0"
edition = "2024"

[[bin]]
name = "corridor-builder-web"
path = "src/main.rs"

[dependencies]
yew = { version = "0.23", features = ["csr"] }
yew-router = "0.20"
wasm-bindgen = "0.2"
wasm-bindgen-futures = "0.4"
js-sys = "0.3"
web-sys = { version = "0.3", features = [
    "Window",
    "Document",
    "Element",
    "HtmlCanvasElement",
    "HtmlInputElement",
    "HtmlSelectElement",
    "EventTarget",
] }
gloo-net = "0.7"
serde = { version = "1", features = ["derive"] }
serde_json = "1"
```

- [ ] **Step 3: Write the Trunk config and HTML shell**

Create `crates/corridor_builder_web/Trunk.toml`:

```toml
[build]
target = "index.html"
dist = "dist"
public_url = "/builder/"

[watch]
watch = ["src", "index.html"]
```

Create `crates/corridor_builder_web/index.html`:

```html
<!doctype html>
<html lang="en">
    <head>
        <meta charset="utf-8" />
        <title>Corridor Builder</title>
        <link data-trunk rel="rust" href="Cargo.toml" />
        <script src="https://unpkg.com/maplibre-gl@6.1.0/dist/maplibre-gl.js"></script>
        <link href="https://unpkg.com/maplibre-gl@6.1.0/dist/maplibre-gl.css" rel="stylesheet" />
    </head>
    <body></body>
</html>
```

- [ ] **Step 4: Write a minimal booting app**

Create `crates/corridor_builder_web/src/app.rs`:

```rust
use yew::prelude::*;

#[component]
pub fn App() -> Html {
    html! {
        <div class="builder-landing">
            <h1>{ "Corridor Builder" }</h1>
        </div>
    }
}
```

Create `crates/corridor_builder_web/src/main.rs`:

```rust
mod app;

fn main() {
    yew::Renderer::<app::App>::new().render();
}
```

- [ ] **Step 5: Build it**

Run:
```bash
cd crates/corridor_builder_web
rustup target add wasm32-unknown-unknown
cargo install trunk --locked
trunk build
```
Expected: succeeds, producing `crates/corridor_builder_web/dist/index.html` plus generated `.js`/`.wasm` assets.

- [ ] **Step 6: Wire Axum to serve the built assets**

In `crates/server/src/web/mod.rs`, add to `build_router` (alongside the routes added in Task 6, before the `.layer(...)` calls):

```rust
        .nest_service(
            "/builder",
            ServeDir::new("crates/corridor_builder_web/dist")
                .not_found_service(ServeFile::new("crates/corridor_builder_web/dist/index.html")),
        )
```

- [ ] **Step 7: Verify end to end**

Run (from the repo root, in three terminals or backgrounded):
```bash
cd crates/corridor_builder_web && trunk build
cd ../.. && dotenvx run -- cargo run --bin mobilispect-server &
curl -s http://localhost:3000/builder/ | grep -o '<title>[^<]*</title>'
```
Expected: `<title>Corridor Builder</title>` (confirms the static mount and SPA fallback both work). Stop the server afterward.

- [ ] **Step 8: Confirm the rest of the workspace still builds normally**

Run: `cargo build --workspace`
Expected: succeeds (the new crate is excluded, so this command doesn't touch it at all).

- [ ] **Step 9: Commit**

```bash
git add Cargo.toml crates/corridor_builder_web/Cargo.toml crates/corridor_builder_web/Trunk.toml crates/corridor_builder_web/index.html crates/corridor_builder_web/src/main.rs crates/corridor_builder_web/src/app.rs crates/corridor_builder_web/.gitignore crates/server/src/web/mod.rs
git commit -m "feat(corridor-builder): scaffold the Yew WASM app and serve it at /builder"
```

Before committing, create `crates/corridor_builder_web/.gitignore` containing `dist/` and `target/` so build output isn't tracked.

---

## Task 9: Routing, API client, and the landing page (create/open remix)

**Files:**
- Create: `crates/corridor_builder_web/src/api.rs`
- Create: `crates/corridor_builder_web/src/pages/mod.rs`
- Create: `crates/corridor_builder_web/src/pages/landing.rs`
- Create: `crates/corridor_builder_web/src/pages/intersection.rs`
- Create: `crates/corridor_builder_web/src/pages/corridor.rs`
- Create: `crates/corridor_builder_web/src/pages/region_map.rs` (stub for now — full implementation in Task 10)
- Modify: `crates/corridor_builder_web/src/app.rs`
- Modify: `crates/corridor_builder_web/src/main.rs`

**Interfaces:**
- Produces: `app::Route` (yew-router `Routable` enum: `Landing`, `RegionMap { remix_id: i64 }`, `Intersection { remix_id: i64, cross_section_id: i64 }`, `Corridor { remix_id: i64, corridor_id: i64 }`, `NotFound`); `api::{Region, RemixSummary, RemixDetail, list_regions, list_region_remixes, create_remix, get_remix, get_remix_corridors}`.
- Consumes: JSON API from Task 6.

This task makes `e2e/tests/builder-create-remix.spec.ts` and `builder-open-remix.spec.ts` pass (except the `.maplibregl-canvas` assertions, which need Task 10's real map — leave `RegionMapPage` as a stub that at least renders *something* at the right route for now; Task 10 finishes it).

- [ ] **Step 1: Write the API client**

Create `crates/corridor_builder_web/src/api.rs`:

```rust
//! JSON API client for the server's `/api/regions`, `/api/remixes` endpoints
//! (see `crates/server/src/web/remix_api.rs`).

use serde::{Deserialize, Serialize};

const API_BASE: &str = "/api";

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct BoundingBox {
    pub min_lat: f64,
    pub min_lon: f64,
    pub max_lat: f64,
    pub max_lon: f64,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct Region {
    pub id: i64,
    pub name: String,
    pub bbox: BoundingBox,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct RemixSummary {
    pub id: i64,
    pub name: String,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct RemixDetail {
    pub id: i64,
    pub name: String,
    pub region: Region,
}

#[derive(Debug, Clone, Serialize)]
struct CreateRemixRequest {
    name: String,
    region_id: i64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CreateRemixResponse {
    pub id: i64,
}

/// Sends a `gloo_net` request and decodes a JSON response, surfacing the
/// server's `{"error": "..."}` message as `Err` on any non-2xx status (e.g.
/// a blank name on create, or 404 on an unknown remix) instead of trying —
/// and failing confusingly — to decode an error body as the success type.
async fn send_and_decode<T: for<'de> Deserialize<'de>>(
    request: gloo_net::http::RequestBuilder,
) -> Result<T, String> {
    let response = request.send().await.map_err(|e| e.to_string())?;

    if !response.ok() {
        let body: serde_json::Value = response.json().await.unwrap_or_default();
        let message = body["error"]
            .as_str()
            .unwrap_or("request failed")
            .to_string();
        return Err(message);
    }

    response.json().await.map_err(|e| e.to_string())
}

pub async fn list_regions() -> Result<Vec<Region>, String> {
    send_and_decode(gloo_net::http::Request::get(&format!("{API_BASE}/regions"))).await
}

pub async fn list_region_remixes(region_id: i64) -> Result<Vec<RemixSummary>, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/regions/{region_id}/remixes"
    )))
    .await
}

pub async fn create_remix(name: String, region_id: i64) -> Result<CreateRemixResponse, String> {
    let request = gloo_net::http::Request::post(&format!("{API_BASE}/remixes"))
        .json(&CreateRemixRequest { name, region_id })
        .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

/// Returns `Err("remix not found")` (the server's own message) when
/// `remix_id` doesn't exist, rather than a confusing JSON-decode error —
/// see the design spec's Error Handling table.
pub async fn get_remix(remix_id: i64) -> Result<RemixDetail, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/remixes/{remix_id}"
    )))
    .await
}

/// Returns the raw GeoJSON `FeatureCollection` as a `serde_json::Value` —
/// it's only ever handed straight to MapLibre, never inspected field by
/// field on the Rust side, so a typed struct would add nothing.
pub async fn get_remix_corridors(remix_id: i64) -> Result<serde_json::Value, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/remixes/{remix_id}/corridors"
    )))
    .await
}
```

- [ ] **Step 2: Define routes**

Rewrite `crates/corridor_builder_web/src/app.rs`:

```rust
use yew::prelude::*;
use yew_router::prelude::*;

use crate::pages::corridor::CorridorPage;
use crate::pages::intersection::IntersectionPage;
use crate::pages::landing::LandingPage;
use crate::pages::region_map::RegionMapPage;

#[derive(Clone, Routable, PartialEq, Debug)]
pub enum Route {
    #[at("/builder")]
    Landing,
    #[at("/builder/remix/:remix_id")]
    RegionMap { remix_id: i64 },
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

#[component]
pub fn App() -> Html {
    html! {
        <BrowserRouter>
            <Switch<Route> render={switch} />
        </BrowserRouter>
    }
}
```

- [ ] **Step 3: Write the placeholder intersection and corridor pages**

Create `crates/corridor_builder_web/src/pages/intersection.rs`:

```rust
use yew::prelude::*;
use yew_router::prelude::*;

use crate::app::Route;

#[derive(Properties, PartialEq)]
pub struct IntersectionPageProps {
    pub remix_id: i64,
    pub cross_section_id: i64,
}

/// Placeholder — the intersection editor itself is a follow-up spec. See
/// the design spec's "Out of Scope".
#[component]
pub fn IntersectionPage(props: &IntersectionPageProps) -> Html {
    html! {
        <div class="builder-placeholder">
            <p>{ "Intersection editor coming soon." }</p>
            <Link<Route> to={Route::RegionMap { remix_id: props.remix_id }}>{ "Back to map" }</Link<Route>>
        </div>
    }
}
```

Create `crates/corridor_builder_web/src/pages/corridor.rs`:

```rust
use yew::prelude::*;
use yew_router::prelude::*;

use crate::app::Route;

#[derive(Properties, PartialEq)]
pub struct CorridorPageProps {
    pub remix_id: i64,
    pub corridor_id: i64,
}

/// Placeholder — the segment editor itself is a follow-up spec (the WASM
/// rework of REQ-001-007). See the design spec's "Out of Scope".
#[component]
pub fn CorridorPage(props: &CorridorPageProps) -> Html {
    html! {
        <div class="builder-placeholder">
            <p>{ "Corridor editor coming soon." }</p>
            <Link<Route> to={Route::RegionMap { remix_id: props.remix_id }}>{ "Back to map" }</Link<Route>>
        </div>
    }
}
```

- [ ] **Step 4: Write a stub region-map page (finished in Task 10)**

Create `crates/corridor_builder_web/src/pages/region_map.rs`:

```rust
use yew::prelude::*;

#[derive(Properties, PartialEq)]
pub struct RegionMapPageProps {
    pub remix_id: i64,
}

/// Stub — replaced with the real MapLibre-backed map in Task 10 of
/// docs/superpowers/plans/2026-08-02-corridor-builder-wasm-shell.md.
#[component]
pub fn RegionMapPage(props: &RegionMapPageProps) -> Html {
    html! {
        <div class="builder-region-map">
            <p>{ format!("Region map for remix {} coming soon.", props.remix_id) }</p>
        </div>
    }
}
```

- [ ] **Step 5: Write the landing page**

Create `crates/corridor_builder_web/src/pages/mod.rs`:

```rust
pub mod corridor;
pub mod intersection;
pub mod landing;
pub mod region_map;
```

Create `crates/corridor_builder_web/src/pages/landing.rs`:

```rust
use web_sys::{HtmlInputElement, HtmlSelectElement};
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;

#[derive(Clone, PartialEq)]
enum Mode {
    Choose,
    Create,
    Open,
}

#[component]
pub fn LandingPage() -> Html {
    let mode = use_state(|| Mode::Choose);
    let regions = use_state(Vec::<api::Region>::new);
    let load_error = use_state(|| None::<String>);
    let name_input = use_node_ref();
    let region_select = use_node_ref();
    let create_error = use_state(|| None::<String>);
    let open_region_select = use_node_ref();
    let remixes = use_state(Vec::<api::RemixSummary>::new);
    let remixes_error = use_state(|| None::<String>);
    let navigator = use_navigator().expect("BrowserRouter provides a Navigator");

    {
        let regions = regions.clone();
        let load_error = load_error.clone();
        use_effect_with((), move |_| {
            wasm_bindgen_futures::spawn_local(async move {
                match api::list_regions().await {
                    Ok(fetched) => regions.set(fetched),
                    Err(err) => load_error.set(Some(err)),
                }
            });
            || ()
        });
    }

    let on_choose_create = {
        let mode = mode.clone();
        Callback::from(move |_: MouseEvent| mode.set(Mode::Create))
    };
    let on_choose_open = {
        let mode = mode.clone();
        Callback::from(move |_: MouseEvent| mode.set(Mode::Open))
    };

    let on_submit_create = {
        let name_input = name_input.clone();
        let region_select = region_select.clone();
        let create_error = create_error.clone();
        let navigator = navigator.clone();
        Callback::from(move |_: MouseEvent| {
            let name_input = name_input.clone();
            let region_select = region_select.clone();
            let create_error = create_error.clone();
            let navigator = navigator.clone();
            wasm_bindgen_futures::spawn_local(async move {
                let name = name_input
                    .cast::<HtmlInputElement>()
                    .map(|el| el.value())
                    .unwrap_or_default();
                let region_id = region_select
                    .cast::<HtmlSelectElement>()
                    .and_then(|el| el.value().parse::<i64>().ok());

                if name.trim().is_empty() {
                    create_error.set(Some("name must not be blank".to_string()));
                    return;
                }
                let Some(region_id) = region_id else {
                    create_error.set(Some("select a metro region".to_string()));
                    return;
                };

                match api::create_remix(name, region_id).await {
                    Ok(response) => navigator.push(&Route::RegionMap {
                        remix_id: response.id,
                    }),
                    Err(err) => create_error.set(Some(err)),
                }
            });
        })
    };

    let on_pick_open_region = {
        let open_region_select = open_region_select.clone();
        let remixes = remixes.clone();
        let remixes_error = remixes_error.clone();
        Callback::from(move |_: Event| {
            let open_region_select = open_region_select.clone();
            let remixes = remixes.clone();
            let remixes_error = remixes_error.clone();
            let region_id = open_region_select
                .cast::<HtmlSelectElement>()
                .and_then(|el| el.value().parse::<i64>().ok());
            if let Some(region_id) = region_id {
                wasm_bindgen_futures::spawn_local(async move {
                    match api::list_region_remixes(region_id).await {
                        Ok(fetched) => remixes.set(fetched),
                        Err(err) => remixes_error.set(Some(err)),
                    }
                });
            }
        })
    };

    html! {
        <div class="builder-landing">
            <h1>{ "Corridor Builder" }</h1>
            if let Some(err) = &*load_error {
                <p class="error">{ err }</p>
            }
            {
                match &*mode {
                    Mode::Choose => html! {
                        <div>
                            <button onclick={on_choose_create}>{ "Create remix" }</button>
                            <button onclick={on_choose_open}>{ "Open remix" }</button>
                        </div>
                    },
                    Mode::Create => html! {
                        <div>
                            <label for="create-region">{ "Metro region" }</label>
                            <select id="create-region" ref={region_select.clone()}>
                                { for regions.iter().map(|r| html! {
                                    <option value={r.id.to_string()}>{ &r.name }</option>
                                }) }
                            </select>
                            <label for="create-name">{ "Remix name" }</label>
                            <input id="create-name" type="text" ref={name_input.clone()} />
                            <button onclick={on_submit_create}>{ "Create" }</button>
                            if let Some(err) = &*create_error {
                                <p class="error">{ err }</p>
                            }
                        </div>
                    },
                    Mode::Open => html! {
                        <div>
                            <label for="open-region">{ "Metro region" }</label>
                            <select id="open-region" ref={open_region_select.clone()} onchange={on_pick_open_region}>
                                <option value="" selected=true disabled=true>{ "Select a region" }</option>
                                { for regions.iter().map(|r| html! {
                                    <option value={r.id.to_string()}>{ &r.name }</option>
                                }) }
                            </select>
                            if let Some(err) = &*remixes_error {
                                <p class="error">{ err }</p>
                            }
                            <ul>
                                { for remixes.iter().map(|r| {
                                    let remix_id = r.id;
                                    html! {
                                        <li>
                                            <Link<Route> to={Route::RegionMap { remix_id }}>{ &r.name }</Link<Route>>
                                        </li>
                                    }
                                }) }
                            </ul>
                        </div>
                    },
                }
            }
        </div>
    }
}
```

- [ ] **Step 6: Wire it all into `main.rs`**

Rewrite `crates/corridor_builder_web/src/main.rs`:

```rust
mod api;
mod app;
mod pages;

fn main() {
    yew::Renderer::<app::App>::new().render();
}
```

- [ ] **Step 7: Build and verify against the E2E specs**

Run:
```bash
cd crates/corridor_builder_web && trunk build && cd ../..
dotenvx run -- cargo run --bin mobilispect-server &
cd e2e && npx playwright test builder-create-remix builder-open-remix --project=chromium
```
Expected: both spec files PASS (the `.maplibregl-canvas` visibility assertions will still fail until Task 10 — if so, that's the expected, correct state for this task; everything else in those two files should pass). Stop the server afterward.

- [ ] **Step 8: Commit**

```bash
git add crates/corridor_builder_web/src
git commit -m "feat(corridor-builder): add routing, API client, and the create/open remix landing page"
```

---

## Task 10: The region map — MapLibre integration and click routing

**Files:**
- Create: `crates/corridor_builder_web/src/maplibre.rs`
- Create: `crates/corridor_builder_web/src/feature_support.rs`
- Modify: `crates/corridor_builder_web/src/pages/region_map.rs` (replace the stub)
- Modify: `crates/corridor_builder_web/src/main.rs`

**Interfaces:**
- Consumes: `api::{get_remix, get_remix_corridors}` (Task 9), `app::Route` (Task 9).
- Produces: a working region map with corridor/intersection click routing; `window.__corridorBuilderMap`, a test-only hook (see Task 7's click-routing spec).

This task makes `builder-click-routing.spec.ts` and `builder-graceful-degradation.spec.ts` pass, and completes the `.maplibregl-canvas` assertions in the two specs from Task 9.

- [ ] **Step 1: Write the MapLibre GL JS bindings**

Create `crates/corridor_builder_web/src/maplibre.rs`:

```rust
//! Hand-written wasm-bindgen bindings for the subset of the MapLibre GL JS
//! API this app needs. Deliberately narrow rather than depending on an
//! unofficial third-party wrapper crate — see the design spec's
//! Architecture section.

use wasm_bindgen::prelude::*;

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = maplibregl)]
    #[derive(Clone)]
    pub type Map;

    #[wasm_bindgen(constructor, js_namespace = maplibregl)]
    pub fn new(options: &JsValue) -> Map;

    #[wasm_bindgen(method, js_name = addSource)]
    pub fn add_source(this: &Map, id: &str, source: &JsValue);

    #[wasm_bindgen(method, js_name = addLayer)]
    pub fn add_layer(this: &Map, layer: &JsValue);

    #[wasm_bindgen(method, js_name = fitBounds)]
    pub fn fit_bounds(this: &Map, bounds: &JsValue, options: &JsValue);

    /// Map-wide click listener (not layer-scoped) — see `handle_map_click`
    /// in `pages/region_map.rs` for why: a corridor's line passes directly
    /// through its own endpoints, so a layer-scoped listener per layer
    /// would fire twice for the same click and race on which navigation
    /// wins. A single listener plus `query_rendered_features` lets us pick
    /// one winner deliberately (intersections take priority).
    #[wasm_bindgen(method)]
    pub fn on(this: &Map, event_type: &str, callback: &Closure<dyn FnMut(JsValue)>);

    #[wasm_bindgen(method, js_name = queryRenderedFeatures)]
    pub fn query_rendered_features(this: &Map, point: &JsValue, options: &JsValue) -> js_sys::Array;
}
```

- [ ] **Step 2: Write the WebGL feature-detection guard**

Create `crates/corridor_builder_web/src/feature_support.rs`:

```rust
//! WebGL availability check for MapLibre GL JS graceful degradation.
//! Mirrors the feature-detection pattern already established for the
//! (separate, canvas-based) corridor segment editor's REQ-007 — see
//! e2e/tests/feature-detection.spec.ts — scoped here to what this shell's
//! map actually depends on: WebGL, not canvas-2D/Pointer Events.

use wasm_bindgen::JsCast;
use web_sys::HtmlCanvasElement;

/// True if the browser can create a WebGL rendering context, which
/// MapLibre GL JS requires. Checked by creating a throwaway canvas rather
/// than touching the real map canvas, so it's safe to call before the map
/// exists.
pub fn webgl_is_supported() -> bool {
    let Some(window) = web_sys::window() else {
        return false;
    };
    let Some(document) = window.document() else {
        return false;
    };
    let Ok(element) = document.create_element("canvas") else {
        return false;
    };
    let Ok(canvas) = element.dyn_into::<HtmlCanvasElement>() else {
        return false;
    };
    canvas.get_context("webgl").ok().flatten().is_some()
}
```

- [ ] **Step 3: Write the real region map page**

Replace `crates/corridor_builder_web/src/pages/region_map.rs` entirely:

```rust
use wasm_bindgen::prelude::*;
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;
use crate::feature_support::webgl_is_supported;
use crate::maplibre::Map;

#[derive(Properties, PartialEq)]
pub struct RegionMapPageProps {
    pub remix_id: i64,
}

#[component]
pub fn RegionMapPage(props: &RegionMapPageProps) -> Html {
    let remix_id = props.remix_id;
    let navigator = use_navigator().expect("BrowserRouter provides a Navigator");
    let error = use_state(|| None::<String>);
    let webgl_ok = use_state(webgl_is_supported);

    {
        let error = error.clone();
        let webgl_ok = *webgl_ok;
        let navigator = navigator.clone();
        use_effect_with(remix_id, move |remix_id| {
            let remix_id = *remix_id;
            if webgl_ok {
                let error = error.clone();
                wasm_bindgen_futures::spawn_local(async move {
                    if let Err(e) = mount_map(remix_id, navigator).await {
                        error.set(Some(e));
                    }
                });
            }
            || ()
        });
    }

    html! {
        <div class="builder-region-map">
            if !*webgl_ok {
                <div class="alert" style="background:var(--al-warn-bg);border-color:var(--al-warn-bd);">
                    <p style="color:var(--al-warn-title);">{ "Your browser doesn't support WebGL, which the region map requires." }</p>
                </div>
            } else if let Some(err) = &*error {
                <div class="builder-error">
                    <p class="error">{ err }</p>
                    <Link<Route> to={Route::Landing}>{ "Back to builder" }</Link<Route>>
                </div>
            }
            <div id="map" style="width: 100%; height: 100vh;"></div>
        </div>
    }
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

fn corridor_line_layer() -> serde_json::Value {
    serde_json::json!({
        "id": "corridor-lines",
        "type": "line",
        "source": "corridors",
        "filter": ["==", ["get", "feature_type"], "corridor"],
        "paint": {
            "line-color": ["case", ["get", "highlighted"], "#C8463A", "#1D4E89"],
            "line-width": ["case", ["get", "highlighted"], 4, 2]
        }
    })
}

fn corridor_intersection_layer() -> serde_json::Value {
    serde_json::json!({
        "id": "corridor-intersections",
        "type": "circle",
        "source": "corridors",
        "filter": ["==", ["get", "feature_type"], "intersection"],
        "paint": {
            "circle-radius": 6,
            "circle-color": "#3D3935"
        }
    })
}

async fn mount_map(remix_id: i64, navigator: Navigator) -> Result<(), String> {
    let remix = api::get_remix(remix_id).await?;
    let corridors = api::get_remix_corridors(remix_id).await?;

    let options = to_js_value(&serde_json::json!({
        "container": "map",
        "style": osm_raster_style(),
    }))?;
    let map = Map::new(&options);

    let bbox = &remix.region.bbox;
    let bounds = to_js_value(&serde_json::json!([
        [bbox.min_lon, bbox.min_lat],
        [bbox.max_lon, bbox.max_lat]
    ]))?;
    map.fit_bounds(&bounds, &to_js_value(&serde_json::json!({}))?);

    let source = to_js_value(&serde_json::json!({
        "type": "geojson",
        "data": corridors,
    }))?;
    map.add_source("corridors", &source);
    map.add_layer(&to_js_value(&corridor_line_layer())?);
    map.add_layer(&to_js_value(&corridor_intersection_layer())?);

    expose_map_for_e2e_tests(&map);

    let click_map = map.clone();
    let click_navigator = navigator.clone();
    let onclick = Closure::wrap(Box::new(move |event: JsValue| {
        handle_map_click(&click_map, &event, &click_navigator, remix_id);
    }) as Box<dyn FnMut(JsValue)>);
    map.on("click", &onclick);
    onclick.forget();

    Ok(())
}

/// A single map-wide click handler (not two layer-scoped ones — see
/// `maplibre.rs`'s `on` binding doc comment for why): checks
/// `corridor-intersections` first, then `corridor-lines`, and acts on
/// whichever is hit first at the click point. Intersections must win at a
/// corridor's endpoints, since the line passes directly through them too.
fn handle_map_click(map: &Map, event: &JsValue, navigator: &Navigator, remix_id: i64) {
    let Ok(point) = js_sys::Reflect::get(event, &"point".into()) else {
        return;
    };

    for layer_id in ["corridor-intersections", "corridor-lines"] {
        let options = js_sys::Object::new();
        let layers = js_sys::Array::of1(&layer_id.into());
        js_sys::Reflect::set(&options, &"layers".into(), &layers).unwrap();

        let features = map.query_rendered_features(&point, &options);
        if features.length() == 0 {
            continue;
        }

        let feature = features.get(0);
        let Ok(properties) = js_sys::Reflect::get(&feature, &"properties".into()) else {
            continue;
        };

        match layer_id {
            "corridor-intersections" => {
                if let Some(cross_section_id) =
                    js_sys::Reflect::get(&properties, &"cross_section_id".into())
                        .ok()
                        .and_then(|v| v.as_f64())
                {
                    navigator.push(&Route::Intersection {
                        remix_id,
                        cross_section_id: cross_section_id as i64,
                    });
                }
            }
            "corridor-lines" => {
                if let Some(corridor_id) =
                    js_sys::Reflect::get(&properties, &"corridor_id".into())
                        .ok()
                        .and_then(|v| v.as_f64())
                {
                    navigator.push(&Route::Corridor {
                        remix_id,
                        corridor_id: corridor_id as i64,
                    });
                }
            }
            _ => {}
        }
        return;
    }
}

/// Stashes the map instance on `window.__corridorBuilderMap` so Playwright
/// E2E tests can compute exact click pixel coordinates via `map.project()`
/// instead of guessing — see `e2e/tests/builder-click-routing.spec.ts`.
fn expose_map_for_e2e_tests(map: &Map) {
    if let Some(window) = web_sys::window() {
        let _ = js_sys::Reflect::set(&window, &"__corridorBuilderMap".into(), map);
    }
}
```

- [ ] **Step 4: Wire the new modules into `main.rs`**

Rewrite `crates/corridor_builder_web/src/main.rs`:

```rust
mod api;
mod app;
mod feature_support;
mod maplibre;
mod pages;

fn main() {
    yew::Renderer::<app::App>::new().render();
}
```

- [ ] **Step 5: Build and run the full E2E suite**

Run:
```bash
cd crates/corridor_builder_web && trunk build && cd ../..
dotenvx run -- cargo run --bin mobilispect-server &
cd e2e && npx playwright test builder- --project=chromium
```
Expected: all four `builder-*.spec.ts` files PASS across all assertions, including the `.maplibregl-canvas` visibility checks from Task 9's specs. Stop the server afterward.

- [ ] **Step 6: Commit**

```bash
git add crates/corridor_builder_web/src
git commit -m "feat(corridor-builder): render the region map with MapLibre and wire click routing"
```

---

## Task 11: Full verification pass

**Files:** none (verification only).

- [ ] **Step 1: Rust workspace — build, lint, format, test**

```bash
cargo build --workspace
cargo clippy --workspace --all-targets -- -D warnings
cargo fmt --all -- --check
DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run --workspace --no-fail-fast
```
Expected: all succeed; zero failures anywhere in the workspace (the excluded `corridor_builder_web` crate isn't touched by any of these). If `cargo fmt --check` reports diffs, run `cargo fmt --all` and re-verify, then amend the affected commits' files were already committed — just re-run the check, no need to re-commit formatting separately unless it actually changes something.

- [ ] **Step 2: WASM crate — build, lint**

```bash
cd crates/corridor_builder_web
cargo clippy --target wasm32-unknown-unknown -- -D warnings
trunk build
```
Expected: both succeed.

- [ ] **Step 3: Full E2E suite across all three engines**

```bash
cd crates/corridor_builder_web && trunk build && cd ../..
dotenvx run -- cargo run --bin mobilispect-server &
cd e2e && npx playwright test
```
Expected: the four new `builder-*.spec.ts` files pass on all three engines (chromium/firefox/webkit). The pre-existing REQ-001–007 specs continue to fail with the same "route not wired" reason they did before this plan (unaffected — confirm no *new* failures appear in those files). Stop the server afterward.

- [ ] **Step 4: Confirm no regressions outside this plan's scope**

Run: `git diff main --stat` (or the appropriate base branch) and review the file list — confirm it only touches files this plan created or the specific lines this plan modified in `crates/core/src/lib.rs`, `crates/core/src/ids.rs`, `crates/server/src/web/mod.rs`, root `Cargo.toml`, and `e2e/package.json`/`package-lock.json`.

No commit for this task — it's verification of Tasks 1–10's commits, not new changes. If any step fails, fix the issue in the relevant task's files and re-run this task's steps before proceeding to Task 12.

---

## Task 12: DDD documentation

**Files:**
- Modify: `docs/ddd/bounded-context-canvas.md`
- Modify: `docs/ddd/ubiquitous-language.md`

Per this project's `.claude/rules/ddd.md`: new domain terms and bounded-context involvement must be documented in the same change that introduces them.

- [ ] **Step 1: Read the current state of both files**

```bash
cat docs/ddd/bounded-context-canvas.md
cat docs/ddd/ubiquitous-language.md
```

- [ ] **Step 2: Add the Corridor Design bounded context (if not already present)**

Following the existing format in `docs/ddd/bounded-context-canvas.md` (each context gets its own section — match whatever heading/field structure the other contexts already use), add or extend a "Corridor Design" section noting:
- Purpose: analysts define and edit street corridors for proposed changes, scoped to remixes within a metro region.
- Aggregates: `Region` (extended with bounding box), `Remix`, `Corridor`, `CrossSection` (existing).
- Relationships: `Remix` belongs to one `Region`; `Corridor` belongs to one `Remix`.

- [ ] **Step 3: Add new ubiquitous-language terms**

In `docs/ddd/ubiquitous-language.md`, following its existing entry format, add:
- **Remix** — a named, user-created draft of proposed street corridor changes, scoped to exactly one metro region. Not tied to any user account.
- **Edited corridor** — a corridor that differs from a pristine imported state: manually traced, or mutated since creation. Drives the region map's highlight overlay.

- [ ] **Step 4: Commit**

```bash
git add docs/ddd/bounded-context-canvas.md docs/ddd/ubiquitous-language.md
git commit -m "docs(ddd): document the Remix aggregate and Corridor Design bounded context"
```

---

## Summary

After all 12 tasks: an analyst can visit `/builder`, create or open a remix scoped to a metro region, see that region's corridors on an OpenStreetMap-tiled map with edited corridors highlighted in cinnabar, and click either a corridor or one of its endpoints to navigate to a (placeholder) editor page. The intersection editor and the segment/corridor editor are separate follow-up specs, tracked in the design spec's "Out of Scope" section.

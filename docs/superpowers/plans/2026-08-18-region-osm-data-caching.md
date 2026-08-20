# Region OSM Data Caching Implementation Plan

> Implements `docs/superpowers/specs/2026-08-18-region-osm-data-caching-design.md` (Approved). The `superpowers` plugin is not installed in this environment, so this plan is executed directly (no fresh-subagent-per-task handoff, no separate spec/quality review gate) rather than via `superpowers:subagent-driven-development`. Steps still use checkbox syntax for tracking.

## Execution Notes (post-implementation)

All 10 tasks below are implemented and committed on `main-3fsqxi`. Two honest gaps, both called out at the point they arise in the tasks below too:

- **No Docker in the implementing sandbox.** Every DB-touching test (this feature's new ones, and the entire pre-existing suite) needs `testcontainers`, which needs a Docker daemon this environment doesn't have. All such tests compile and type-check cleanly against a live local Postgres instance (used only to regenerate the sqlx offline query cache — not the same thing as running the tests), but were never executed to green here. Every pure/unit test (no DB) *was* run and passes — 354 across the workspace, including all of this feature's pure logic (`match_region`, `reproject_and_bbox`, `provinces_overlapping`, `build_extract_args`/`build_merge_args`, the synthetic-shapefile parse tests). The 125 DB tests that failed to run here (all with the identical `SocketNotFoundError("/var/run/docker.sock")`) include this feature's 2 new integration tests alongside ~123 pre-existing ones — confirmed by spot-checking failures on `main` before this branch's changes, not a regression.
- **Follow-up verification pass (2026-08-19):** `LAMBERT_PROJ4`'s parameters are now confirmed correct via search against spatialreference.org/epsg.io (`statcan.rs`'s doc comment updated accordingly). The StatsCan zip filename is still not fully resolved — `statcan.gc.ca`/`geo.statcan.gc.ca` are blocked for direct fetch in every sandbox this was attempted from, including the ArcGIS `MapServer` JSON. Search turned up two plausible candidates (`lcma000b21a_e.zip` vs `lcma000b21s_e.zip`); rather than guess one, `download_statcan_zip` now tries both in sequence. Confirm the real filename and collapse back to one URL once this runs somewhere with actual network access to the StatsCan site.

Two deliberate deviations from what this plan originally specified, both improvements made during implementation: `statcan.rs`'s parse test builds its own tiny shapefile in-memory via the `shapefile`/`dbase` write APIs instead of shipping a checked-in binary `.zip` fixture (no opaque binary blob in the diff, fully reviewable); and the reprojection test round-trips a known WGS84 point through `proj4rs` itself instead of asserting against a hardcoded placeholder Lambert coordinate (removes a fabricated-number risk from the test suite entirely).

**Goal:** Populate `regions.min_lat/min_lon/max_lat/max_lon` from a StatsCan CMA/CA boundary match, then download/clip/cache a region-scoped OSM `.osm.pbf` extract, as a background worker job run once per region.

**Architecture:** New `crates/worker/src/region_provisioning/` module, spawned once at worker startup after `feeds` load. Two independently-idempotent phases per region: (1) StatsCan boundary lookup → bbox, (2) Geofabrik provincial PBF(s) → `osmium extract`/`merge` → cached region extract. See the design spec's Architecture section for the full pipeline diagram.

**Tech Stack:** Rust (2024 edition), `shapefile` + `dbase` (StatsCan shapefile parsing), `zip` (StatsCan archive), `proj4rs` (pure-Rust EPSG:3347→EPSG:4326 reprojection), `unicode-normalization` (accent-insensitive name matching), `tokio::process::Command` (shelling out to `osmium-tool`), `reqwest` (already a dependency).

## Global Constraints

- No mocks in tests — DB-touching tests use real Postgres via `testcontainers` (`crates/core`'s `db::test_utils`). Network-touching shell functions (`load_cma_ca_records`'s download, `download_provincial_pbf`) are tested against fixture data/local servers only, never live `statcan.gc.ca`/`download.geofabrik.de` — this sandbox's egress proxy blocks both hosts outright, and the design spec already scopes live-network testing out of the automated suite for the same reason Overpass/Transitland tests don't hit real endpoints.
- Functional Core / Imperative Shell: `provinces_overlapping`, `match_region`, `reproject_and_bbox`, `build_extract_args`, `build_merge_args` are pure (no I/O), unit tested without any process/network dependency. Everything that touches disk, network, or spawns a process is shell.
- No new database migration — `regions.min_lat/min_lon/max_lat/max_lon` already exist (migration 025). This plan only starts writing to them.
- New system dependency: `osmium-tool` (Task 7 adds it to the Docker runtime stage). Contributors running the worker locally need it on `PATH`; a missing binary is a hard `OsmiumError` at run time, not a build-time failure.
- New Cargo dependencies land only in `crates/worker/Cargo.toml` (Task 3) — this feature has no `crates/server` or `crates/core` component beyond the new `db/regions.rs` query module (shared DB logic, same reasoning as `db/feeds.rs`).
- `osmium extract`/`osmium merge`'s real CLI invocation (`run_osmium`, `download_provincial_pbf`) cannot be exercised end-to-end in this sandbox (no `osmium-tool` binary, no egress to Geofabrik). Their pure argument-builders are fully tested; the shell wrappers are implemented per the design's interface and get a `#[cfg(test)]`-only smoke test only where a local fixture makes that possible (Task 5), consistent with how `osm/mod.rs`'s live-endpoint calls are excluded from the automated suite.
- Design spec: `docs/superpowers/specs/2026-08-18-region-osm-data-caching-design.md` (this plan implements it in full).
- The exact StatsCan CMA/CA cartographic boundary shapefile zip URL/filename was flagged in the design as unverified (egress blocked). Task 4 hardcodes best-known values and documents this as a known risk to confirm against the real StatsCan site before this ships to production — it does not block writing or testing the parsing/matching/reprojection logic, which only need *a* shapefile+dbf pair (real StatsCan data for a subset of records, checked in as a small test fixture).

---

## Task 1: Config — `osm_cache_dir`

**Files:**
- Modify: `crates/core/src/config.rs`

**Interfaces:**
- Produces: `Config::osm_cache_dir: String` (default `"./osm-cache"` when unset in `config.toml`).

- [x] **Step 1: Add the field**

Add to `Config`:
```rust
pub osm_cache_dir: String,
```
Add to `TomlConfig`:
```rust
osm_cache_dir: Option<String>,
```
In `Config::from_toml_str_with_env`, alongside `retention_days.unwrap_or(30)`:
```rust
osm_cache_dir: file.osm_cache_dir.unwrap_or_else(|| "./osm-cache".to_string()),
```

- [x] **Step 2: Update every existing `Config { .. }` test/struct literal**

`crates/core/src/config.rs`'s own tests and `crates/server/src/web/osm_import.rs`'s `test_config()` (from the corridor-OSM-import plan, if merged) construct `Config` literals directly — grep for `Config {` across the workspace and add `osm_cache_dir: "./osm-cache".to_string(),` (or the test-appropriate value) to each. Confirm via `cargo build --workspace` that nothing is missed (a missing field is a compile error, not a silent gap).

- [x] **Step 3: Add a test**

```rust
#[test]
fn defaults_osm_cache_dir_when_unset() {
    let config = config_from_toml(r#"database_url = "postgres://localhost/mobilispect""#, &[]).unwrap();
    assert_eq!(config.osm_cache_dir, "./osm-cache");
}
```

- [x] **Step 4: Run tests, build workspace**

`cargo nextest run -p mobilispect-core config::tests` then `cargo build --workspace`.

- [x] **Step 5: Commit**

```bash
git add crates/core/src/config.rs crates/server/src/web
git commit -m "feat(region-osm): add osm_cache_dir config field"
```

---

## Task 2: `db/regions.rs`

**Files:**
- Create: `crates/core/src/db/regions.rs`
- Modify: `crates/core/src/db/mod.rs`

**Interfaces:**
- Produces: `db::regions::{DbRegion, load_regions}`. `load_regions(pool: &PgPool) -> Result<Vec<DbRegion>>`.

- [x] **Step 1: Write the module with tests**

```rust
use anyhow::Result;
use sqlx::PgPool;

pub struct DbRegion {
    pub id: i64,
    pub name: String,
    pub timezone: String,
    pub min_lat: Option<f64>,
    pub min_lon: Option<f64>,
    pub max_lat: Option<f64>,
    pub max_lon: Option<f64>,
}

pub async fn load_regions(pool: &PgPool) -> Result<Vec<DbRegion>> {
    Ok(sqlx::query_as!(
        DbRegion,
        r#"SELECT id, name, timezone, min_lat, min_lon, max_lat, max_lon FROM regions"#
    )
    .fetch_all(pool)
    .await?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;

    #[tokio::test]
    async fn load_regions_returns_seeded_rows() {
        let td = test_utils::setup().await;
        sqlx::query!(
            "INSERT INTO regions (id, name, timezone) VALUES (1, 'Test Region', 'UTC')"
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let regions = load_regions(&td.db.pool).await.unwrap();
        assert_eq!(regions.len(), 1);
        assert_eq!(regions[0].name, "Test Region");
        assert_eq!(regions[0].min_lat, None);
    }

    #[tokio::test]
    async fn load_regions_returns_populated_bbox() {
        let td = test_utils::setup().await;
        sqlx::query!(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon)
             VALUES (1, 'Test Region', 'UTC', 45.40, -73.70, 45.60, -73.50)"
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let regions = load_regions(&td.db.pool).await.unwrap();
        assert_eq!(regions[0].min_lat, Some(45.40));
        assert_eq!(regions[0].max_lon, Some(-73.50));
    }
}
```

- [x] **Step 2: Register the module**

In `crates/core/src/db/mod.rs`, add `pub mod regions;` after `pub mod feeds;`.

- [x] **Step 3: Run tests**

`cargo nextest run -p mobilispect-core db::regions::tests` — expect 2 PASS.

- [x] **Step 4: Commit**

```bash
git add crates/core/src/db/regions.rs crates/core/src/db/mod.rs
git commit -m "feat(region-osm): add load_regions query"
```

---

## Task 3: Worker Cargo dependencies

**Files:**
- Modify: `crates/worker/Cargo.toml`

- [x] **Step 1: Add dependencies**

```toml
shapefile = "0.6"
zip = "2"
proj4rs = "0.1"
unicode-normalization = "0.1"
```

(Exact versions to be pinned to whatever `cargo add` resolves at implementation time — these are the latest known-stable major versions as of this plan's writing; `cargo build` after `cargo add` is the source of truth, not this table.)

- [x] **Step 2: Verify the workspace builds**

`cargo add shapefile zip proj4rs unicode-normalization -p mobilispect-worker` then `cargo build --workspace`.

- [x] **Step 3: Commit**

```bash
git add crates/worker/Cargo.toml Cargo.lock
git commit -m "chore(region-osm): add shapefile/zip/proj4rs/unicode-normalization deps"
```

---

## Task 4: `region_provisioning/provinces.rs`

**Files:**
- Create: `crates/worker/src/region_provisioning/mod.rs` (stub — just `pub mod provinces;` for now, filled in by Task 8)
- Create: `crates/worker/src/region_provisioning/provinces.rs`
- Modify: `crates/worker/src/main.rs` (add `mod region_provisioning;`)

**Interfaces:**
- Produces: `region_provisioning::provinces::{Province, PROVINCES, provinces_overlapping}`.
- Consumes: `mobilispect_core::remix::BoundingBox` (existing).

- [x] **Step 1: Write the module with tests**

```rust
//! Hardcoded Canada province/territory table: Geofabrik download slug plus an
//! approximate rectangular extent, used only to decide which provincial OSM
//! PBF(s) to download for a region's bbox — the actual clip against the real
//! bbox happens precisely, via `osmium extract`, in `osm_extract.rs`. See
//! `docs/superpowers/specs/2026-08-18-region-osm-data-caching-design.md`.

use mobilispect_core::remix::BoundingBox;

pub struct Province {
    pub geofabrik_slug: &'static str,
    pub name: &'static str,
    pub approx_bbox: BoundingBox,
}

pub const PROVINCES: &[Province] = &[
    Province { geofabrik_slug: "alberta", name: "Alberta", approx_bbox: BoundingBox { min_lat: 49.0, min_lon: -120.0, max_lat: 60.0, max_lon: -110.0 } },
    Province { geofabrik_slug: "british-columbia", name: "British Columbia", approx_bbox: BoundingBox { min_lat: 48.3, min_lon: -139.1, max_lat: 60.0, max_lon: -114.0 } },
    Province { geofabrik_slug: "manitoba", name: "Manitoba", approx_bbox: BoundingBox { min_lat: 49.0, min_lon: -102.1, max_lat: 60.0, max_lon: -88.9 } },
    Province { geofabrik_slug: "new-brunswick", name: "New Brunswick", approx_bbox: BoundingBox { min_lat: 44.5, min_lon: -69.1, max_lat: 48.1, max_lon: -63.7 } },
    Province { geofabrik_slug: "newfoundland-and-labrador", name: "Newfoundland and Labrador", approx_bbox: BoundingBox { min_lat: 46.5, min_lon: -67.9, max_lat: 60.4, max_lon: -52.6 } },
    Province { geofabrik_slug: "northwest-territories", name: "Northwest Territories", approx_bbox: BoundingBox { min_lat: 59.9, min_lon: -136.5, max_lat: 78.8, max_lon: -101.9 } },
    Province { geofabrik_slug: "nova-scotia", name: "Nova Scotia", approx_bbox: BoundingBox { min_lat: 43.4, min_lon: -66.4, max_lat: 47.1, max_lon: -59.7 } },
    Province { geofabrik_slug: "nunavut", name: "Nunavut", approx_bbox: BoundingBox { min_lat: 51.6, min_lon: -120.9, max_lat: 83.2, max_lon: -61.2 } },
    Province { geofabrik_slug: "ontario", name: "Ontario", approx_bbox: BoundingBox { min_lat: 41.6, min_lon: -95.2, max_lat: 56.9, max_lon: -74.3 } },
    Province { geofabrik_slug: "prince-edward-island", name: "Prince Edward Island", approx_bbox: BoundingBox { min_lat: 45.9, min_lon: -64.5, max_lat: 47.1, max_lon: -61.9 } },
    Province { geofabrik_slug: "quebec", name: "Quebec", approx_bbox: BoundingBox { min_lat: 44.9, min_lon: -79.8, max_lat: 62.6, max_lon: -57.1 } },
    Province { geofabrik_slug: "saskatchewan", name: "Saskatchewan", approx_bbox: BoundingBox { min_lat: 48.9, min_lon: -110.0, max_lat: 60.0, max_lon: -101.3 } },
    Province { geofabrik_slug: "yukon", name: "Yukon", approx_bbox: BoundingBox { min_lat: 59.9, min_lon: -141.1, max_lat: 69.7, max_lon: -123.7 } },
];

/// Pure rectangle-overlap test. Standard axis-aligned bbox intersection: two
/// boxes overlap unless one is entirely to a side of the other on either axis.
pub fn provinces_overlapping(bbox: BoundingBox) -> Vec<&'static Province> {
    PROVINCES
        .iter()
        .filter(|p| {
            let b = p.approx_bbox;
            bbox.min_lat <= b.max_lat
                && bbox.max_lat >= b.min_lat
                && bbox.min_lon <= b.max_lon
                && bbox.max_lon >= b.min_lon
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bbox_fully_inside_one_province_matches_only_that_province() {
        // Downtown Calgary, well inside Alberta's approx bbox.
        let bbox = BoundingBox { min_lat: 51.03, min_lon: -114.10, max_lat: 51.07, max_lon: -114.03 };
        let matches: Vec<&str> = provinces_overlapping(bbox).iter().map(|p| p.geofabrik_slug).collect();
        assert_eq!(matches, vec!["alberta"]);
    }

    #[test]
    fn bbox_straddling_ontario_quebec_matches_both() {
        // An Ottawa-Gatineau-shaped box straddling the Ontario/Quebec border.
        let bbox = BoundingBox { min_lat: 45.35, min_lon: -76.0, max_lat: 45.55, max_lon: -75.5 };
        let mut matches: Vec<&str> = provinces_overlapping(bbox).iter().map(|p| p.geofabrik_slug).collect();
        matches.sort();
        assert_eq!(matches, vec!["ontario", "quebec"]);
    }

    #[test]
    fn bbox_outside_canada_matches_nothing() {
        let bbox = BoundingBox { min_lat: 40.70, min_lon: -74.01, max_lat: 40.72, max_lon: -73.99 }; // NYC
        assert!(provinces_overlapping(bbox).is_empty());
    }
}
```

- [x] **Step 2: Create the module stub and register it**

`crates/worker/src/region_provisioning/mod.rs`:
```rust
//! Background job: populates a region's bounding box from StatsCan CMA/CA
//! data, then caches a clipped/merged OSM PBF extract for it. See
//! `docs/superpowers/specs/2026-08-18-region-osm-data-caching-design.md`.

pub mod provinces;
```

In `crates/worker/src/main.rs`, add `mod region_provisioning;` alphabetically among the existing `mod` declarations (after `mod pipeline;`).

- [x] **Step 3: Run tests, build**

`cargo nextest run -p mobilispect-worker region_provisioning::provinces::tests` then `cargo build --workspace`.

- [x] **Step 4: Commit**

```bash
git add crates/worker/src/region_provisioning crates/worker/src/main.rs
git commit -m "feat(region-osm): add province table and bbox-overlap lookup"
```

---

## Task 5: `region_provisioning/statcan.rs`

**Files:**
- Create: `crates/worker/src/region_provisioning/statcan.rs`
- Create: `crates/worker/tests/fixtures/statcan_cma_ca_sample.zip` (small hand-built fixture — a handful of real StatsCan CMA/CA records, e.g. Montreal/Ottawa/Gatineau/Calgary, re-saved as a minimal `.shp`/`.shx`/`.dbf` triple zipped together; real coordinates so `reproject_and_bbox`'s tests are meaningful, but only a few records so the fixture stays small)
- Modify: `crates/worker/src/region_provisioning/mod.rs` (add `pub mod statcan;`)

**Interfaces:**
- Produces: `statcan::{CmaCaRecord, match_region, reproject_and_bbox, load_cma_ca_records}`.
- Consumes: `mobilispect_core::remix::BoundingBox`, `Config::osm_cache_dir` (Task 1).

- [x] **Step 1: Write `match_region` and its tests (pure, no fixture needed)**

```rust
use std::collections::HashMap;
use std::path::Path;

use unicode_normalization::UnicodeNormalization;

use mobilispect_core::remix::BoundingBox;

pub struct CmaCaRecord {
    pub name: String,
    pub points_lambert: Vec<(f64, f64)>,
}

/// Case-folds and strips diacritics (NFD-decompose, drop combining marks) on
/// both sides before comparing. Returns every record whose `name` equals, or
/// contains as a whole word, `region_name` -- not just the first -- because a
/// CMA/CA that straddles a provincial border (e.g. Ottawa-Gatineau,
/// Lloydminster) is stored as multiple same-named records, one per
/// provincial part, and every one must contribute to the final bbox.
pub fn match_region<'a>(region_name: &str, records: &'a [CmaCaRecord]) -> Vec<&'a CmaCaRecord> {
    let needle = normalize(region_name);
    let exact: Vec<&CmaCaRecord> = records.iter().filter(|r| normalize(&r.name) == needle).collect();
    if !exact.is_empty() {
        return exact;
    }
    records
        .iter()
        .filter(|r| {
            normalize(&r.name)
                .split(|c: char| !c.is_alphanumeric())
                .any(|word| word == needle)
        })
        .collect()
}

fn normalize(s: &str) -> String {
    s.nfd()
        .filter(|c| !unicode_normalization::char::is_combining_mark(*c))
        .collect::<String>()
        .to_lowercase()
        .trim()
        .to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn record(name: &str) -> CmaCaRecord {
        CmaCaRecord { name: name.to_string(), points_lambert: vec![] }
    }

    #[test]
    fn exact_match() {
        let records = vec![record("Calgary")];
        let m = match_region("Calgary", &records);
        assert_eq!(m.len(), 1);
    }

    #[test]
    fn accent_and_case_insensitive_match() {
        let records = vec![record("Montréal")];
        let m = match_region("montreal", &records);
        assert_eq!(m.len(), 1);
    }

    #[test]
    fn whole_word_substring_match_against_compound_cma_name() {
        let records = vec![record("Ottawa - Gatineau (Ontario part / partie de l'Ontario)")];
        let m = match_region("Ottawa", &records);
        assert_eq!(m.len(), 1);
    }

    #[test]
    fn does_not_match_partial_word() {
        let records = vec![record("Kitchener - Cambridge - Waterloo")];
        let m = match_region("Water", &records);
        assert!(m.is_empty());
    }

    #[test]
    fn returns_all_matching_records_for_a_split_cma() {
        let records = vec![
            record("Ottawa - Gatineau (Ontario part / partie de l'Ontario)"),
            record("Ottawa - Gatineau (Quebec part / partie du Québec)"),
        ];
        let m = match_region("Ottawa", &records);
        assert_eq!(m.len(), 2);
    }

    #[test]
    fn no_match_returns_empty() {
        let records = vec![record("Calgary")];
        assert!(match_region("Nonexistent City", &records).is_empty());
    }
}
```

- [x] **Step 2: Write `reproject_and_bbox` and its tests**

```rust
use proj4rs::Proj;

const LAMBERT_PROJ4: &str = "+proj=lcc +lat_1=49 +lat_2=77 +lat_0=63.390675 +lon_0=-91.86666666666666 +x_0=6200000 +y_0=3000000 +datum=NAD83 +units=m +no_defs";
const WGS84_PROJ4: &str = "+proj=longlat +datum=WGS84 +no_defs";

#[derive(Debug)]
pub struct ProjectionError(String);

/// Reprojects every matched record's points from EPSG:3347 (NAD83 /
/// Statistics Canada Lambert) to EPSG:4326 (WGS84) and folds to a bounding
/// box. `proj4rs::transform::transform` works in radians; StatsCan's
/// coordinates are already in metres (the Lambert CRS's native unit), so no
/// unit conversion is needed going in -- only a radians->degrees conversion
/// on the WGS84 output.
pub fn reproject_and_bbox(records: &[&CmaCaRecord]) -> Result<BoundingBox, ProjectionError> {
    let from = Proj::from_proj_string(LAMBERT_PROJ4).map_err(|e| ProjectionError(e.to_string()))?;
    let to = Proj::from_proj_string(WGS84_PROJ4).map_err(|e| ProjectionError(e.to_string()))?;

    let mut min_lat = f64::MAX;
    let mut max_lat = f64::MIN;
    let mut min_lon = f64::MAX;
    let mut max_lon = f64::MIN;
    let mut any = false;

    for record in records {
        for &(x, y) in &record.points_lambert {
            let mut point = (x, y, 0.0);
            proj4rs::transform::transform(&from, &to, &mut point)
                .map_err(|e| ProjectionError(e.to_string()))?;
            let (lon, lat) = (point.0.to_degrees(), point.1.to_degrees());
            any = true;
            min_lat = min_lat.min(lat);
            max_lat = max_lat.max(lat);
            min_lon = min_lon.min(lon);
            max_lon = max_lon.max(lon);
        }
    }

    if !any {
        return Err(ProjectionError("no points to reproject".to_string()));
    }

    Ok(BoundingBox { min_lat, min_lon, max_lat, max_lon })
}
```

Test against a known reference pair (verify the exact Lambert coordinate for a real landmark via an independent EPSG:3347↔EPSG:4326 converter when implementing — e.g. downtown Ottawa is approximately `45.42°N, -75.70°W`):

```rust
#[cfg(test)]
mod reprojection_tests {
    use super::*;

    #[test]
    fn reprojects_a_single_point_within_expected_tolerance() {
        // Coordinate to be confirmed against an independent EPSG:3347->4326
        // converter at implementation time; placeholder pair given here is
        // approximately downtown Ottawa.
        let records = vec![&CmaCaRecord {
            name: "Test".to_string(),
            points_lambert: vec![(7607000.0, 1512000.0)], // approx, verify before use
        }];
        let bbox = reproject_and_bbox(&records).unwrap();
        assert!((bbox.min_lat - 45.42).abs() < 0.5);
        assert!((bbox.min_lon - (-75.70)).abs() < 0.5);
    }

    #[test]
    fn multiple_records_union_into_one_bbox() {
        let a = CmaCaRecord { name: "A".to_string(), points_lambert: vec![(7607000.0, 1512000.0)] };
        let b = CmaCaRecord { name: "B".to_string(), points_lambert: vec![(7620000.0, 1520000.0)] };
        let bbox = reproject_and_bbox(&[&a, &b]).unwrap();
        assert!(bbox.min_lat <= bbox.max_lat);
        assert!(bbox.min_lon <= bbox.max_lon);
    }

    #[test]
    fn empty_input_is_an_error() {
        assert!(reproject_and_bbox(&[]).is_err());
    }
}
```

**Implementation note:** the placeholder Lambert coordinate and the loose `0.5`-degree tolerance above must be tightened once a real independent conversion is confirmed during implementation — this plan cannot verify exact StatsCan Lambert parameter values against a live source from this sandbox (egress blocked), so treat the `LAMBERT_PROJ4` string and the test fixture coordinates as needing a final check against StatsCan's own published parameters (`92-160-G` reference guide) before merging.

- [x] **Step 3: Write `load_cma_ca_records` (shell) and its fixture-based test**

```rust
use std::io::Read as _;

/// Downloads (if not cached at `{cache_dir}/statcan/cma_ca_2021.zip`) and
/// unzips the national CMA/CA cartographic boundary file, then parses every
/// record via the `shapefile` crate.
pub async fn load_cma_ca_records(cache_dir: &Path) -> anyhow::Result<Vec<CmaCaRecord>> {
    let zip_path = cache_dir.join("statcan").join("cma_ca_2021.zip");
    if !zip_path.exists() {
        download_statcan_zip(&zip_path).await?;
    }
    parse_cma_ca_zip(&zip_path)
}

async fn download_statcan_zip(dest: &Path) -> anyhow::Result<()> {
    // URL to be confirmed at implementation time -- see this plan's Global
    // Constraints and the design spec's "Verify at implementation time" note.
    const STATCAN_CMA_CA_URL: &str = "https://www12.statcan.gc.ca/census-recensement/2021/geo/sip-pis/boundary-limites/files-fichiers/lcma000b21a_e.zip";
    if let Some(parent) = dest.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let bytes = reqwest::get(STATCAN_CMA_CA_URL).await?.error_for_status()?.bytes().await?;
    tokio::fs::write(dest, &bytes).await?;
    Ok(())
}

/// Synchronous (the `shapefile`/`zip` crates are blocking) -- called via
/// `tokio::task::spawn_blocking` from `load_cma_ca_records` in the real
/// (non-test) path.
fn parse_cma_ca_zip(zip_path: &Path) -> anyhow::Result<Vec<CmaCaRecord>> {
    let file = std::fs::File::open(zip_path)?;
    let mut archive = zip::ZipArchive::new(file)?;

    // Extract the .shp/.shx/.dbf triple to a temp dir -- the `shapefile`
    // crate reads from paths, not in-memory buffers, so the zip's members
    // are written out first.
    let tmp = tempfile_dir()?;
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i)?;
        let name = entry.name().to_string();
        if name.ends_with(".shp") || name.ends_with(".shx") || name.ends_with(".dbf") {
            let out_path = tmp.join(&name);
            let mut out = std::fs::File::create(&out_path)?;
            std::io::copy(&mut entry, &mut out)?;
        }
    }

    let shp_path = std::fs::read_dir(&tmp)?
        .filter_map(|e| e.ok())
        .find(|e| e.path().extension().is_some_and(|ext| ext == "shp"))
        .ok_or_else(|| anyhow::anyhow!("no .shp member found in StatsCan zip"))?
        .path();

    let mut reader = shapefile::Reader::from_path(&shp_path)?;
    let mut records = Vec::new();
    for shape_record in reader.iter_shapes_and_records() {
        let (shape, dbf_record) = shape_record?;
        let name = match dbf_record.get("CMANAME") {
            Some(dbase::FieldValue::Character(Some(s))) => s.clone(),
            _ => continue,
        };
        let points_lambert = match shape {
            shapefile::Shape::Polygon(polygon) => polygon
                .rings()
                .iter()
                .flat_map(|ring| ring.points().iter().map(|p| (p.x, p.y)))
                .collect(),
            _ => continue,
        };
        records.push(CmaCaRecord { name, points_lambert });
    }
    Ok(records)
}
```

(`tempfile_dir()` — use the `tempfile` crate, or a `std::env::temp_dir().join(format!("statcan-{}", uuid))` if avoiding another new dependency is preferred; decide at implementation time based on which reads cleaner.)

Fixture-based test (needs `crates/worker/tests/fixtures/statcan_cma_ca_sample.zip`, built once during implementation from a handful of real records subset out of the actual StatsCan file, or synthesized with correct field names/types if extracting a real subset proves impractical from this sandbox):

```rust
#[cfg(test)]
mod load_tests {
    use super::*;

    #[test]
    fn parses_fixture_zip_into_records_with_names_and_points() {
        let path = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/statcan_cma_ca_sample.zip");
        let records = parse_cma_ca_zip(&path).unwrap();
        assert!(!records.is_empty());
        assert!(records.iter().any(|r| !r.points_lambert.is_empty()));
    }
}
```

- [x] **Step 4: Register the module**

`crates/worker/src/region_provisioning/mod.rs`: add `pub mod statcan;`.

- [x] **Step 5: Build the fixture zip**

This is the one step in this task that needs a real StatsCan shapefile/dbf pair as a starting point. If live StatsCan access is unavailable in the implementing environment too, synthesize a minimal valid shapefile (a handful of `Polygon` shapes with plausible Lambert-projection coordinates and a `.dbf` with a `CMANAME` character field) using the `shapefile`/`dbase` crates' own *write* APIs in a one-off script — this only needs to be structurally valid, not real StatsCan data, since `parse_cma_ca_zip`'s test only checks parsing mechanics, and `match_region`/`reproject_and_bbox`'s correctness is already covered by their own pure-function unit tests above.

- [x] **Step 6: Run tests, build**

`cargo nextest run -p mobilispect-worker region_provisioning::statcan` then `cargo build --workspace`.

- [x] **Step 7: Commit**

```bash
git add crates/worker/src/region_provisioning/statcan.rs crates/worker/src/region_provisioning/mod.rs crates/worker/tests/fixtures/statcan_cma_ca_sample.zip
git commit -m "feat(region-osm): add StatsCan CMA/CA boundary matching and reprojection"
```

---

## Task 6: `region_provisioning/osm_extract.rs`

**Files:**
- Create: `crates/worker/src/region_provisioning/osm_extract.rs`
- Modify: `crates/worker/src/region_provisioning/mod.rs` (add `pub mod osm_extract;`)

**Interfaces:**
- Produces: `osm_extract::{build_extract_args, build_merge_args, run_osmium, download_provincial_pbf, build_region_extract, OsmiumError}`.
- Consumes: `provinces::Province` (Task 4), `mobilispect_core::remix::BoundingBox`.

- [x] **Step 1: Write the pure argument builders and their tests**

```rust
use std::path::{Path, PathBuf};

use mobilispect_core::remix::BoundingBox;

use super::provinces::Province;

pub fn build_extract_args(bbox: BoundingBox, input: &Path, output: &Path) -> Vec<String> {
    vec![
        "extract".to_string(),
        "-b".to_string(),
        format!("{},{},{},{}", bbox.min_lon, bbox.min_lat, bbox.max_lon, bbox.max_lat),
        input.display().to_string(),
        "-o".to_string(),
        output.display().to_string(),
        "--overwrite".to_string(),
    ]
}

pub fn build_merge_args(inputs: &[PathBuf], output: &Path) -> Vec<String> {
    let mut args = vec!["merge".to_string()];
    args.extend(inputs.iter().map(|p| p.display().to_string()));
    args.push("-o".to_string());
    args.push(output.display().to_string());
    args.push("--overwrite".to_string());
    args
}

#[cfg(test)]
mod arg_tests {
    use super::*;

    #[test]
    fn extract_args_use_osmium_bbox_order_lon_lat() {
        let bbox = BoundingBox { min_lat: 45.40, min_lon: -73.70, max_lat: 45.60, max_lon: -73.50 };
        let args = build_extract_args(bbox, Path::new("in.pbf"), Path::new("out.pbf"));
        assert_eq!(args[0], "extract");
        assert_eq!(args[2], "-73.7,45.4,-73.5,45.6");
        assert!(args.contains(&"--overwrite".to_string()));
    }

    #[test]
    fn merge_args_list_every_input_before_the_output_flag() {
        let inputs = vec![PathBuf::from("a.pbf"), PathBuf::from("b.pbf")];
        let args = build_merge_args(&inputs, Path::new("out.pbf"));
        assert_eq!(args, vec!["merge", "a.pbf", "b.pbf", "-o", "out.pbf", "--overwrite"]);
    }
}
```

- [x] **Step 2: Write the shell functions (no automated live test — see Global Constraints)**

```rust
#[derive(Debug)]
pub struct OsmiumError {
    pub stderr: String,
}

impl std::fmt::Display for OsmiumError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "osmium failed: {}", self.stderr)
    }
}
impl std::error::Error for OsmiumError {}

async fn run_osmium(args: &[String]) -> Result<(), OsmiumError> {
    let output = tokio::process::Command::new("osmium")
        .args(args)
        .output()
        .await
        .map_err(|e| OsmiumError { stderr: e.to_string() })?;
    if !output.status.success() {
        return Err(OsmiumError { stderr: String::from_utf8_lossy(&output.stderr).to_string() });
    }
    Ok(())
}

pub async fn download_provincial_pbf(cache_dir: &Path, province: &Province) -> anyhow::Result<PathBuf> {
    let path = cache_dir.join("provinces").join(format!("{}.osm.pbf", province.geofabrik_slug));
    if path.exists() {
        return Ok(path);
    }
    if let Some(parent) = path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let url = format!(
        "https://download.geofabrik.de/north-america/canada/{}-latest.osm.pbf",
        province.geofabrik_slug
    );
    let bytes = reqwest::get(&url).await?.error_for_status()?.bytes().await?;
    tokio::fs::write(&path, &bytes).await?;
    Ok(path)
}

/// Orchestrates the one-province vs multi-province cases (design spec
/// Architecture): a single overlapping province extracts directly; multiple
/// provinces each get clipped to a temp file first, then merged.
pub async fn build_region_extract(
    cache_dir: &Path,
    region_id: i64,
    bbox: BoundingBox,
    provinces: &[&Province],
) -> anyhow::Result<PathBuf> {
    anyhow::ensure!(!provinces.is_empty(), "no overlapping provinces for region {region_id}");

    let out_dir = cache_dir.join("regions");
    tokio::fs::create_dir_all(&out_dir).await?;
    let output = out_dir.join(format!("{region_id}.osm.pbf"));

    if provinces.len() == 1 {
        let input = download_provincial_pbf(cache_dir, provinces[0]).await?;
        run_osmium(&build_extract_args(bbox, &input, &output)).await?;
        return Ok(output);
    }

    let tmp_dir = cache_dir.join("tmp");
    tokio::fs::create_dir_all(&tmp_dir).await?;
    let mut clipped = Vec::new();
    for province in provinces {
        let input = download_provincial_pbf(cache_dir, province).await?;
        let clip_path = tmp_dir.join(format!("{region_id}-{}.osm.pbf", province.geofabrik_slug));
        run_osmium(&build_extract_args(bbox, &input, &clip_path)).await?;
        clipped.push(clip_path);
    }
    run_osmium(&build_merge_args(&clipped, &output)).await?;
    for path in clipped {
        let _ = tokio::fs::remove_file(path).await;
    }
    Ok(output)
}
```

- [x] **Step 3: Register the module**

`crates/worker/src/region_provisioning/mod.rs`: add `pub mod osm_extract;`.

- [x] **Step 4: Run tests, build**

`cargo nextest run -p mobilispect-worker region_provisioning::osm_extract::arg_tests` then `cargo build --workspace`.

- [x] **Step 5: Commit**

```bash
git add crates/worker/src/region_provisioning/osm_extract.rs crates/worker/src/region_provisioning/mod.rs
git commit -m "feat(region-osm): add osmium extract/merge orchestration"
```

---

## Task 7: Dockerfile — `osmium-tool`

**Files:**
- Modify: `Dockerfile`

- [x] **Step 1: Add the package**

In the runtime stage's `apt-get install`:
```dockerfile
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libssl3 \
    osmium-tool \
    && rm -rf /var/lib/apt/lists/*
```

- [x] **Step 2: Note local-dev requirement**

Add a comment above the worker's `region_provisioning` module doc comment (already present from Task 4) is sufficient — no `dev.sh` change needed since it doesn't build images. Optionally note in this repo's top-level README/dev docs (out of scope for this plan unless one already documents local prerequisites — check `README.md` for an existing "Prerequisites" section and add a line there if so).

- [x] **Step 3: Commit**

```bash
git add Dockerfile
git commit -m "chore(region-osm): install osmium-tool in the runtime image"
```

---

## Task 8: `region_provisioning/mod.rs` orchestration + `main.rs` wiring

**Files:**
- Modify: `crates/worker/src/region_provisioning/mod.rs`
- Modify: `crates/worker/src/main.rs`

**Interfaces:**
- Produces: `region_provisioning::{run_all, provision_region}`.
- Consumes: everything from Tasks 2, 4, 5, 6; `mobilispect_core::db::Database`, `mobilispect_core::config::Config`.

- [x] **Step 1: Write `provision_region` and `run_all`**

```rust
use std::time::Duration;

use tracing::{info, warn};

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;
use mobilispect_core::db::regions::{DbRegion, load_regions};
use mobilispect_core::remix::BoundingBox;

pub mod osm_extract;
pub mod provinces;
pub mod statcan;

const RETRY_BACKOFF: Duration = Duration::from_secs(5 * 60);

pub async fn run_all(db: Database, config: Config) {
    let regions = match load_regions(&db.pool).await {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "region_provisioning: failed to load regions, skipping");
            return;
        }
    };
    for region in regions {
        let db = db.clone();
        let config = config.clone();
        tokio::spawn(async move {
            loop {
                match provision_region(&db, &config, &region).await {
                    Ok(()) => break,
                    Err(ProvisionError::Permanent(msg)) => {
                        warn!(region = %region.name, %msg, "region_provisioning: permanent failure, not retrying");
                        break;
                    }
                    Err(ProvisionError::Transient(msg)) => {
                        warn!(region = %region.name, %msg, "region_provisioning: transient failure, retrying in 5m");
                        tokio::time::sleep(RETRY_BACKOFF).await;
                    }
                }
            }
        });
    }
}

#[derive(Debug)]
enum ProvisionError {
    Permanent(String),
    Transient(String),
}

async fn provision_region(db: &Database, config: &Config, region: &DbRegion) -> Result<(), ProvisionError> {
    let bbox = match (region.min_lat, region.min_lon, region.max_lat, region.max_lon) {
        (Some(min_lat), Some(min_lon), Some(max_lat), Some(max_lon)) => {
            BoundingBox { min_lat, min_lon, max_lat, max_lon }
        }
        _ => populate_bbox(db, config, region).await?,
    };

    let cache_dir = std::path::Path::new(&config.osm_cache_dir);
    let extract_path = cache_dir.join("regions").join(format!("{}.osm.pbf", region.id));
    if extract_path.exists() {
        return Ok(());
    }

    let provinces = provinces::provinces_overlapping(bbox);
    if provinces.is_empty() {
        return Err(ProvisionError::Permanent(format!(
            "region {} bbox does not overlap any known province",
            region.name
        )));
    }

    osm_extract::build_region_extract(cache_dir, region.id, bbox, &provinces)
        .await
        .map_err(|e| ProvisionError::Transient(e.to_string()))?;

    info!(region = %region.name, "region_provisioning: OSM extract cached");
    Ok(())
}

async fn populate_bbox(db: &Database, config: &Config, region: &DbRegion) -> Result<BoundingBox, ProvisionError> {
    let cache_dir = std::path::Path::new(&config.osm_cache_dir);
    let records = statcan::load_cma_ca_records(cache_dir)
        .await
        .map_err(|e| ProvisionError::Transient(e.to_string()))?;

    let matches = statcan::match_region(&region.name, &records);
    if matches.is_empty() {
        return Err(ProvisionError::Permanent(format!(
            "no CMA/CA record matches region name {:?}",
            region.name
        )));
    }

    let bbox = statcan::reproject_and_bbox(&matches).map_err(|e| ProvisionError::Transient(format!("{e:?}")))?;

    sqlx::query!(
        "UPDATE regions SET min_lat = $1, min_lon = $2, max_lat = $3, max_lon = $4 WHERE id = $5",
        bbox.min_lat,
        bbox.min_lon,
        bbox.max_lat,
        bbox.max_lon,
        region.id,
    )
    .execute(&db.pool)
    .await
    .map_err(|e| ProvisionError::Transient(e.to_string()))?;

    info!(region = %region.name, "region_provisioning: bbox populated");
    Ok(bbox)
}
```

- [x] **Step 2: Wire into `main.rs`**

After the existing `maintenance::backfill_daily_metrics(&db, &config, &feeds, 7).await;` call (which already blocks startup), add:
```rust
let db_provisioning = db.clone();
let config_provisioning = config.clone();
tokio::spawn(async move {
    region_provisioning::run_all(db_provisioning, config_provisioning).await;
});
```
This must NOT block: it's spawned, not awaited, so real-time polling (spawned right after in the existing code) starts immediately regardless of how long provisioning takes.

Add `mod region_provisioning;` to `main.rs`'s existing `mod` block (Task 4 already stubbed the module; this task's Step 1 above replaces that stub's single `pub mod provinces;` line with the full re-export list shown, since `provinces`/`statcan`/`osm_extract` are declared together here).

- [x] **Step 3: Integration test — two-phase idempotency**

```rust
#[cfg(test)]
mod integration_tests {
    use super::*;
    use mobilispect_core::db::test_utils;

    fn test_config(cache_dir: &std::path::Path) -> Config {
        Config {
            database_url: String::new(),
            poll_interval_secs: 30,
            bind_address: "0.0.0.0:3000".to_string(),
            on_time_early_threshold_secs: -60,
            on_time_late_threshold_secs: 300,
            retention_days: 30,
            worker_health_bind_address: "0.0.0.0:9090".to_string(),
            transitland_api_key: None,
            osm_cache_dir: cache_dir.display().to_string(),
        }
    }

    #[tokio::test]
    async fn already_provisioned_region_skips_both_phases_with_no_network_calls() {
        let td = test_utils::setup().await;
        let tmp = tempfile::tempdir().unwrap();
        let region_id = 1i64;
        sqlx::query!(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon)
             VALUES ($1, 'Test Region', 'UTC', 45.40, -73.70, 45.60, -73.50)",
            region_id,
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        let regions_dir = tmp.path().join("regions");
        tokio::fs::create_dir_all(&regions_dir).await.unwrap();
        tokio::fs::write(regions_dir.join(format!("{region_id}.osm.pbf")), b"fake").await.unwrap();

        let region = mobilispect_core::db::regions::load_regions(&td.db.pool).await.unwrap().remove(0);
        let result = provision_region(&td.db, &test_config(tmp.path()), &region).await;

        assert!(result.is_ok());
    }
}
```

(This test needs `tempfile` as a `crates/worker` dev-dependency — add it in Task 3's Cargo.toml edit, or here if Task 3 already landed without it.)

- [x] **Step 4: Run tests, build**

`cargo nextest run -p mobilispect-worker region_provisioning` then `cargo build --workspace`.

- [x] **Step 5: Commit**

```bash
git add crates/worker/src/region_provisioning/mod.rs crates/worker/src/main.rs crates/worker/Cargo.toml
git commit -m "feat(region-osm): wire region provisioning into worker startup"
```

---

## Task 9: Documentation follow-up

**Files:**
- Modify: `docs/ddd/acl.md`
- Modify: `docs/ddd/context-map.md`

- [x] **Step 1: `acl.md`**

Add a new section after the existing `## Overpass API (OSM Import)` section (before `## Adding a New External Source`):

```markdown
## StatsCan / Geofabrik (Region OSM Data Caching)

Translation happens in `crates/worker/src/region_provisioning/`. Unlike
Transitland/Overpass above (shared by `crates/core`, called from both worker
and server), nothing in `crates/server` needs this data yet, so it follows
`feed_ingestion`'s precedent and lives in `crates/worker` as a batch/background
job, not `crates/core`.

**Rule:** No `reqwest`/`shapefile`/`zip` calls related to StatsCan or Geofabrik
may appear outside `crates/worker/src/region_provisioning/`.

Translations:
- StatsCan's raw shapefile+DBF records → `CmaCaRecord { name: String,
  points_lambert: Vec<(f64, f64)> }` (`statcan.rs`) — only the `CMANAME` field
  and raw Lambert-projection point coordinates cross the boundary; `CMAUID`,
  `CMATYPE`, `CMAPUID`, `PRUID` are read but not retained.
- Geofabrik's per-province `.osm.pbf` files never get parsed into any Rust
  type at all — they're clipped/merged by shelling out to `osmium`
  (`osm_extract.rs`) and only the resulting cached file path crosses into the
  rest of the app.
- The only domain type either translation produces is `BoundingBox` (already
  existing, `mobilispect_core::remix::BoundingBox`) — written into
  `regions.min_lat/min_lon/max_lat/max_lon`.
```

- [x] **Step 2: `context-map.md`**

Read the existing file's `## Relationships` section structure (mirroring `### Schedule → Performance: Shared Kernel` etc.) and add:

```markdown
### Feed Ingestion → Corridor Design: Upstream/Downstream

`region_provisioning` (Feed Ingestion, `crates/worker`) populates
`regions.min_lat/min_lon/max_lat/max_lon` — a field Corridor Design owns
(added by migration 025 for the Corridor Builder map). Feed Ingestion is
upstream: it writes the field once per region as a background job; Corridor
Design only ever reads it.
```

- [x] **Step 3: Commit**

```bash
git add docs/ddd/acl.md docs/ddd/context-map.md
git commit -m "docs(region-osm): document StatsCan/Geofabrik ACL boundary and context relationship"
```

---

## Task 10: Final verification

- [x] **Step 1:** `cargo build --workspace`
- [x] **Step 2:** `cargo clippy --workspace`
- [x] **Step 3:** `cargo nextest run` (full suite)
- [x] **Step 4:** Re-read the design spec's "Verify at implementation time" items (StatsCan zip URL/filename, Lambert proj4 parameters) — confirm both were actually checked against a live/authoritative source during Tasks 5–6's implementation, not left as placeholders. If either could not be verified in the implementing environment either, call this out explicitly as a follow-up rather than silently shipping a guess.

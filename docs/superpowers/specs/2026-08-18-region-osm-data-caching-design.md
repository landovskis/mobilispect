# Region OSM Data Caching

**Date:** 2026-08-18
**Status:** Draft

## Summary

`regions.min_lat/min_lon/max_lat/max_lon` (added by migration 025 for the Corridor Builder map) exist but are never populated — the first-launch GTFS setup wizard (`store_discovered_feeds`) inserts a region row with only `name` and `timezone`. Separately, Corridor Builder's OSM street import (`docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md`) only ever fetches OSM data live, on demand, per viewport, via Overpass — there is no bulk/offline OSM dataset for a region anywhere in the system.

This plan adds a background worker job, run once per region, that:

1. Downloads Statistics Canada's cartographic boundary file for Census Metropolitan Areas/Census Agglomerations (CMA/CA), matches the region's `name` against it, and computes a WGS84 bounding box from the matched polygon — populating `regions.min_lat/min_lon/max_lat/max_lon`.
2. Downloads the Geofabrik OSM extract(s) for the province(s) that bounding box overlaps, clips to the bounding box with `osmium extract`, and (when the box straddles more than one province) merges the clipped pieces with `osmium merge` — producing one cached `.osm.pbf` file per region on local disk.

Scope is Canada-only, matching this project's current Canadian transit-agency focus (StatsCan has no equivalent outside Canada). Consuming the cached PBF (e.g. as a Corridor Builder base layer, or to replace live Overpass calls) is a separate, later feature — this plan only produces and caches the artifact.

## Domain Context

- **Bounded context(s):** Feed Ingestion (new batch job, worker-only — not shared with `mobilispect-server`, so it does not qualify for `crates/core` placement under the existing "shared by more than one crate" rule that put Transitland/Overpass there); Corridor Design (consumes: writes into the `Region` aggregate's bounding box field it already owns).
- **Aggregates touched:** `Region` (`regions.min_lat/min_lon/max_lat/max_lon` populated for the first time; no shape change).
- **New ubiquitous language terms:**
  - **CMA/CA** — Statistics Canada's Census Metropolitan Area / Census Agglomeration: the standard StatsCan geography for an urban region, keyed by name (`CMANAME`) in the cartographic boundary file.
  - **Region OSM extract** — the cached, region-clipped `.osm.pbf` file produced by this job; not consumed by any feature yet.

`docs/ddd/context-map.md` should gain a `Feed Ingestion → Corridor Design` relationship (this job populates a field Corridor Design owns) in the same commit as implementation.

## Architecture

Runs once per region row, as a background task spawned at worker startup (not blocking `main()` the way `backfill_daily_metrics` does — first-run downloads can be large and slow, and must not delay real-time GTFS-RT polling from starting):

```
main() — after `feeds` load succeeds (region rows are guaranteed to exist by
then, inserted transactionally alongside feeds by store_discovered_feeds)
  -> tokio::spawn(region_provisioning::run_all(db, config))
     load_regions(&db.pool) [new, crates/core/src/db/regions.rs, mirrors
                              db/feeds.rs's DbFeed/load_feeds exactly]
     for each row currently in `regions`:
       -> tokio::spawn(region_provisioning::provision_region(db, config, region))

provision_region (one region, two independently-idempotent phases):

  Phase 1 — bounding box (skip entirely if regions.min_lat IS NOT NULL):
    download_statcan_boundary_file()  [shell, cached on disk, one national
                                        file shared by all regions]
      -> parse_cma_ca_records()       [shell: shapefile + dbf -> Vec<CmaCaRecord>
                                        { name: String, points_lambert: Vec<(f64,f64)> }]
      -> match_region(region.name, &records)   [pure, see below]
           no match -> log warn!, stop (permanent for this process's lifetime;
                       not a retry-worthy failure)
      -> reproject_and_bbox(matched_points)     [pure: EPSG:3347 -> EPSG:4326
                                                  via proj4rs, then min/max]
      -> UPDATE regions SET min_lat=.., min_lon=.., max_lat=.., max_lon=..

  Phase 2 — OSM extract (skip if the deterministic output file already exists
  on disk; independent of Phase 1's DB check, so a wiped cache dir on an
  otherwise-provisioned region still gets re-extracted):
    re-read region's bbox from DB (Phase 1 may have run in a prior process)
      -> provinces_overlapping(bbox)            [pure, const table + rect
                                                  overlap test]
      -> for each overlapping province:
           download_provincial_pbf(province)    [shell, cached at
                                                  {cache_dir}/provinces/{slug}.osm.pbf,
                                                  shared across all regions in
                                                  that province]
      -> if one province: osmium extract -b <bbox> province.pbf -o out.pbf
         if N>1 provinces: osmium extract each province.pbf to a temp file,
                            then osmium merge the N temp files -> out.pbf
         written to {cache_dir}/regions/{region_id}.osm.pbf
```

Retry policy: on any transient failure (network, non-2xx, `osmium` non-zero exit), log a `warn!` and retry the whole `provision_region` after a fixed backoff (5 minutes) — mirrors `feed_ingestion::realtime::poll_loop`'s "restart in 30s" pattern in `main.rs`. A permanent failure (no CMA/CA name match) logs once and does not retry.

## New Worker Module: `crates/worker/src/region_provisioning/`

Lives in `mobilispect-worker`, not `mobilispect-core`: unlike Transitland/Overpass (called from both worker and server), nothing in `crates/server` needs this data yet, so it follows `feed_ingestion`'s precedent rather than the "shared" exception. Functional Core/Imperative Shell applies within the module (pure functions colocated with their shell callers, the same way `osm/mod.rs`'s `build_query`/`parse_overpass_response` sit next to `fetch_ways_in_bbox`), not as a `core`/`worker` split.

### `crates/core/src/db/regions.rs` (new)

Mirrors `db/feeds.rs`'s `DbFeed`/`load_feeds` exactly — the only existing region read today is an inline `SELECT name FROM regions LIMIT 1` in `web/mod.rs`, not a reusable query function.

```rust
pub struct DbRegion {
    pub id: i64,
    pub name: String,
    pub timezone: String,
    pub min_lat: Option<f64>,
    pub min_lon: Option<f64>,
    pub max_lat: Option<f64>,
    pub max_lon: Option<f64>,
}

pub async fn load_regions(pool: &PgPool) -> Result<Vec<DbRegion>>;
```

### `mod.rs`

```rust
pub async fn run_all(db: Database, config: Config) { .. }   // load_regions, spawns one task per row
async fn provision_region(db: &Database, config: &Config, region: DbRegion) -> Result<()> { .. }
```

### `provinces.rs` (pure, no I/O)

```rust
pub struct Province {
    pub geofabrik_slug: &'static str,   // e.g. "ontario", "british-columbia"
    pub name: &'static str,
    pub approx_bbox: BoundingBox,       // reuses core::remix::BoundingBox
}

/// Hardcoded table, all 13 provinces/territories, approximate (not exact
/// polygon) rectangular extents -- an approximation is sufficient since it
/// only decides *which* provincial PBFs to download, not the final clip
/// (osmium extract does the precise clip against the real region bbox).
pub const PROVINCES: &[Province] = &[ .. ];

/// Pure rectangle-overlap test against each province's approx_bbox.
pub fn provinces_overlapping(bbox: BoundingBox) -> Vec<&'static Province>;
```

### `statcan.rs`

```rust
pub struct CmaCaRecord {
    pub name: String,               // CMANAME field, as-is from the DBF
    pub points_lambert: Vec<(f64, f64)>,  // all ring points, EPSG:3347, concatenated
}

/// Shell: downloads (if not already cached at {cache_dir}/statcan/cma_ca_2021.zip)
/// and unzips the national CMA/CA cartographic boundary file, parses every
/// record via the `shapefile` crate.
async fn load_cma_ca_records(cache_dir: &Path) -> Result<Vec<CmaCaRecord>>;

/// Pure. Case-folds and NFD-strips diacritics (`unicode-normalization`) on
/// both sides, then: exact match first; else CMANAME containing the region
/// name as a whole word (handles StatsCan's "Ottawa - Gatineau" naming
/// against a region.name of "Ottawa"). Returns *all* matches, not just the
/// first -- a CMA that straddles a provincial border (e.g. Ottawa-Gatineau,
/// Lloydminster) is stored as multiple DBF records, one per provincial part,
/// sharing a name; every matched record's points must contribute to the bbox.
fn match_region<'a>(region_name: &str, records: &'a [CmaCaRecord]) -> Vec<&'a CmaCaRecord>;

/// Pure. Reprojects every matched point EPSG:3347 -> EPSG:4326 via `proj4rs`
/// (pure-Rust, no libproj system dependency -- keeps the Docker build
/// unchanged) and folds to a min/max bounding box.
fn reproject_and_bbox(records: &[&CmaCaRecord]) -> Result<BoundingBox, ProjectionError>;
```

**Verify at implementation time:** the exact CMA/CA cartographic boundary shapefile zip filename and its base URL under `https://www12.statcan.gc.ca/census-recensement/2021/geo/sip-pis/boundary-limites/`. This sandbox's egress proxy blocks `statcan.gc.ca`, so the filename could not be confirmed live during design (unlike the Overpass query shape in the referenced OSM-import plan, which was). Confirmed independently: the file is a national ZIP (all CMA/CAs in one file, cached once and reused for every region), Shapefile format, projected in NAD83 / Statistics Canada Lambert (**EPSG:3347**) — not WGS84 — so the reprojection step above is required, not optional. Confirmed DBF attribute fields: `CMANAME` (name, char 100), `CMAUID`, `CMATYPE`, `CMAPUID`, `PRUID`.

### `osm_extract.rs`

```rust
/// Shell: downloads (if not cached at {cache_dir}/provinces/{slug}.osm.pbf)
/// https://download.geofabrik.de/north-america/canada/{slug}-latest.osm.pbf
async fn download_provincial_pbf(cache_dir: &Path, province: &Province) -> Result<PathBuf>;

/// Pure -- builds the argv for `osmium extract`, independently testable
/// without invoking the binary (mirrors osm/mod.rs's build_query pattern).
fn build_extract_args(bbox: BoundingBox, input: &Path, output: &Path) -> Vec<String>;

/// Pure -- builds the argv for `osmium merge`.
fn build_merge_args(inputs: &[PathBuf], output: &Path) -> Vec<String>;

/// Shell: runs `osmium` via tokio::process::Command with the above argv,
/// mapping a non-zero exit or spawn failure to OsmiumError { stderr: String }.
async fn run_osmium(args: &[String]) -> Result<(), OsmiumError>;

/// Shell: orchestrates the one-province (single extract) vs multi-province
/// (extract each, then merge) cases described in Architecture, writing the
/// final file to {cache_dir}/regions/{region_id}.osm.pbf.
pub async fn build_region_extract(
    cache_dir: &Path,
    region_id: i64,
    bbox: BoundingBox,
    provinces: &[&Province],
) -> Result<PathBuf>;
```

## New External Dependency: `osmium-tool`

Not a Rust crate — a C++ CLI (`osmium extract`, `osmium merge`) with no equivalent-maturity pure-Rust alternative for PBF clipping/merging at this data scale. New for this codebase (no prior `std::process`/`tokio::process` usage anywhere in `crates/`).

- **`Dockerfile` runtime stage:** add `osmium-tool` to the `apt-get install` list (alongside `ca-certificates`, `libssl3`). Not needed in the builder stage — only the running worker container invokes it.
- **Local dev:** `dev.sh` doesn't build anything, so no image change; contributors running the worker locally need `osmium-tool` installed via their OS package manager (`apt install osmium-tool` / `brew install osmium-tool`). Note this in the module's doc comment; a hard runtime failure (`run_osmium` returning `OsmiumError` when the binary isn't found) is an acceptable dev-environment failure mode — no `cargo-watch`-style auto-install, since it's a system package, not a Cargo tool.

## New Cargo Dependencies (`crates/worker/Cargo.toml`)

| Crate | Purpose |
|---|---|
| `shapefile` | Parses the StatsCan `.shp`/`.dbf` pair into `CmaCaRecord`s. |
| `zip` | Extracts the StatsCan boundary file's `.zip`. |
| `proj4rs` | Pure-Rust EPSG:3347 → EPSG:4326 reprojection (no libproj system dependency, keeping the existing Docker build untouched). |

## Config (`crates/core/src/config.rs`)

New optional field, same `Option<T>`-with-default pattern as `retention_days`:

```rust
pub osm_cache_dir: String,   // default: "./osm-cache"
```

Added to `TomlConfig` as `osm_cache_dir: Option<String>`, defaulted in `Config::from_toml_str_with_env` the same way `retention_days.unwrap_or(30)` is. No secret handling needed (not sensitive).

**Deployment note:** every other piece of state in this codebase lives in Postgres; this is the first feature to require a persistent local directory. On Railway (referenced in the Dockerfile's deployment comment) the container filesystem is ephemeral by default — without an attached volume mounted at `osm_cache_dir`, every redeploy re-downloads the national StatsCan file and every province's PBF from scratch. This doesn't break correctness (both phases are idempotent and safe to re-run), only wastes bandwidth/time on each redeploy. Flagging for the implementation plan to call out in deployment docs; not blocking for this design.

## Error Handling

| Condition | Behavior |
|---|---|
| StatsCan download/unzip/parse fails | Transient: log `warn!`, retry whole `provision_region` after 5 min |
| No CMA/CA record matches `region.name` | Permanent for this process: log `warn!` once, do not retry, bbox stays `NULL` |
| Reprojection failure (malformed/out-of-range Lambert coordinates) | Treated as a parse error — transient retry (StatsCan data is presumed well-formed; a failure here more likely indicates a corrupt partial download) |
| Geofabrik download fails for a needed province | Transient: log `warn!`, retry whole `provision_region` (bbox from Phase 1 is preserved — only Phase 2 re-runs, since Phase 1 is separately gated on `min_lat IS NOT NULL`) |
| `osmium extract`/`merge` non-zero exit | Transient: log `warn!` with captured stderr, retry |
| Region bbox already populated but on-disk extract missing | Not an error — Phase 2 alone re-runs (see Architecture) |

## Testing

- **`provinces.rs`**: unit tests for `provinces_overlapping` — a bbox fully inside one province, a bbox straddling two (e.g. an Ottawa-shaped box against Ontario+Quebec), a bbox touching none (sanity check against a non-Canadian bbox).
- **`statcan.rs`**: unit tests for `match_region` against an in-memory `Vec<CmaCaRecord>` fixture — exact match, accent/case-insensitive match, "Ottawa" matching "Ottawa - Gatineau", multiple same-name records (split CMA) both returned, no match. Unit tests for `reproject_and_bbox` against known EPSG:3347 ↔ EPSG:4326 coordinate pairs for a real Canadian city (verifiable against public reference conversions, no network). `load_cma_ca_records` gets an integration-style test against a small fixture `.zip` checked into the repo (not the real multi-MB national file) — mirrors how `osm_import.rs`'s tests use a local fixture server rather than live Overpass.
- **`osm_extract.rs`**: unit tests for `build_extract_args`/`build_merge_args` (pure, argv assertions, no process spawn — mirrors `build_query`'s test style). `run_osmium`/`download_provincial_pbf` are not covered by the automated suite (no live Geofabrik/osmium-tool dependency in CI); the implementation plan should decide whether a fixture-based integration test (a tiny hand-built `.pbf` and a real local `osmium` invocation) is worth adding, given `osmium-tool` becomes a CI dependency either way once the Docker build needs it.
- **`mod.rs` (`provision_region`)**: integration test via testcontainers (real Postgres) asserting the two-phase idempotency: bbox already set + extract file present → both phases skipped (no download calls); bbox set + file missing → only Phase 2 runs.

## Documentation Follow-up

- `docs/ddd/context-map.md`: add the `Feed Ingestion → Corridor Design` relationship described in Domain Context.
- `docs/ddd/acl.md`: new section documenting the StatsCan and Geofabrik/OSM-PBF translation boundary (`crates/worker/src/region_provisioning/`), per the "New external data source" rule — a `CmaCaRecord`'s raw Lambert coordinates and the Geofabrik province table are external shapes that never leak past this module; only the resulting `BoundingBox` (already an existing core type) and a cached file path cross into the rest of the app.

## Out of Scope

- Consuming the cached region `.osm.pbf` extract anywhere (Corridor Builder base layer, offline analysis, replacing live Overpass calls in the existing OSM-import flow) — this plan only produces and caches the artifact.
- Non-Canadian regions — no StatsCan equivalent exists; a region whose name doesn't match any CMA/CA simply never gets a bbox or extract (logged, not fatal).
- Re-running provisioning when `region.name` changes after initial setup, or supporting a later census year's boundary file — this plan hardcodes the 2021 boundary file and provisions once per region row.
- A UI/API surface for provisioning status — this is a silent background job; its only observable output is the DB bbox and the cached file's existence, both queryable directly, not through a new endpoint.

//! Background job: populates a region's bounding box from StatsCan CMA/CA
//! data, then caches a clipped/merged OSM PBF extract for it. See
//! `docs/superpowers/specs/2026-08-18-region-osm-data-caching-design.md`.
//!
//! Needs the `osmium-tool` CLI on `PATH` (installed in the Docker runtime
//! image; contributors running the worker locally need it via their OS
//! package manager, e.g. `apt install osmium-tool` / `brew install osmium-tool`).

pub mod osm_extract;
pub mod provinces;
pub mod statcan;

use std::time::Duration;

use tracing::{info, warn};

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;
use mobilispect_core::db::regions::{DbRegion, load_regions};
use mobilispect_core::remix::BoundingBox;

const RETRY_BACKOFF: Duration = Duration::from_secs(5 * 60);

/// Spawns one task per region row currently in the DB and awaits all of
/// them via a `JoinSet` (mirrors `main.rs`'s own pattern for loading
/// per-agency static GTFS feeds) before returning. Each task retries its own
/// region on transient failure and gives up (permanently, for this
/// process's lifetime) on a failure that re-running won't fix -- so this
/// only returns once every region has either succeeded or hit a permanent
/// failure (a region stuck in transient retries keeps its task, and this
/// call, running). Does not block worker startup itself: the caller
/// (`main.rs`) wraps this whole call in its own `tokio::spawn`, not `.await`s
/// it directly.
pub async fn run_all(db: Database, config: Config) {
    let regions = match load_regions(&db.pool).await {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "region_provisioning: failed to load regions, skipping");
            return;
        }
    };
    let mut set = tokio::task::JoinSet::new();
    for region in regions {
        let db = db.clone();
        let config = config.clone();
        set.spawn(async move {
            loop {
                match provision_region(&db, &config, &region).await {
                    Ok(()) => break,
                    Err(ProvisionError::Permanent(msg)) => {
                        warn!(
                            region = %region.name,
                            %msg,
                            "region_provisioning: permanent failure, not retrying"
                        );
                        break;
                    }
                    Err(ProvisionError::Transient(msg)) => {
                        warn!(
                            region = %region.name,
                            %msg,
                            "region_provisioning: transient failure, retrying in 5m"
                        );
                        tokio::time::sleep(RETRY_BACKOFF).await;
                    }
                }
            }
        });
    }
    while set.join_next().await.is_some() {}
}

#[derive(Debug)]
enum ProvisionError {
    /// Re-running `provision_region` won't fix this -- e.g. no CMA/CA name
    /// match, or a bbox that overlaps no known province.
    Permanent(String),
    /// Network/process failure -- worth retrying after a backoff.
    Transient(String),
}

/// Two independently-idempotent phases for one region: (1) populate its
/// bbox from StatsCan, if not already set; (2) cache its OSM extract, if not
/// already on disk. Either phase can be skipped independently -- a region
/// with a bbox already in the DB but a missing cache file (e.g. an ephemeral
/// disk wiped on redeploy) only re-runs phase 2.
async fn provision_region(
    db: &Database,
    config: &Config,
    region: &DbRegion,
) -> Result<(), ProvisionError> {
    let bbox = match (
        region.min_lat,
        region.min_lon,
        region.max_lat,
        region.max_lon,
    ) {
        (Some(min_lat), Some(min_lon), Some(max_lat), Some(max_lon)) => BoundingBox {
            min_lat,
            min_lon,
            max_lat,
            max_lon,
        },
        _ => populate_bbox(db, config, region).await?,
    };

    let cache_dir = std::path::Path::new(&config.osm_cache_dir);
    let extract_path = cache_dir
        .join("regions")
        .join(format!("{}.osm.pbf", region.id));
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

async fn populate_bbox(
    db: &Database,
    config: &Config,
    region: &DbRegion,
) -> Result<BoundingBox, ProvisionError> {
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

    let bbox = statcan::reproject_and_bbox(&matches)
        .map_err(|e| ProvisionError::Transient(e.to_string()))?;

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

#[cfg(test)]
mod tests {
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

    #[test]
    fn retry_backoff_is_five_minutes() {
        assert_eq!(RETRY_BACKOFF, Duration::from_secs(300));
    }

    #[tokio::test]
    async fn run_all_awaits_provisioning_and_persists_the_populated_bbox() {
        // A whole-function no-op mutant for run_all returns `()` immediately
        // without ever calling load_regions/provision_region, which is
        // otherwise unobservable (the real fn's return type is also `()`).
        // This test forces an observable, awaited side effect: seed a
        // region with NO bbox, name-matched to a StatsCan fixture whose
        // polygon reprojects to a real lat/lon outside every entry in
        // `provinces::PROVINCES` (so Phase 2 fails fast, permanently, with
        // no network call) -- then assert the DB's bbox actually changed
        // from NULL to that reprojected value. A no-op mutant leaves it
        // NULL; only a real Phase 1 run populates it.
        let td = test_utils::setup().await;
        let tmp = tempfile::tempdir().unwrap();
        let region_id = 1i64;
        sqlx::query!(
            "INSERT INTO regions (id, name, timezone) VALUES ($1, 'Fixture City', 'UTC')",
            region_id,
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        // Mid-Atlantic: north of the equator, west of the prime meridian
        // (matching every real Canadian province's sign convention, so this
        // isn't caught by degenerate-bbox checks alone), but east of every
        // PROVINCES entry's max_lon (Newfoundland's, the easternmost, tops
        // out at -52.6) -- guaranteed no province overlap.
        let point = statcan::wgs84_to_lambert_for_tests(40.0, -40.0);
        let zip_path = tmp.path().join("statcan").join("cma_ca_2021.zip");
        statcan::build_fixture_zip(&zip_path, "Fixture City", point);

        run_all(td.db.clone(), test_config(tmp.path())).await;

        let region = load_regions(&td.db.pool).await.unwrap().remove(0);
        assert!(
            region.min_lat.is_some(),
            "Phase 1 should have populated the bbox"
        );
        let lat = region.min_lat.unwrap();
        let lon = region.min_lon.unwrap();
        assert!(
            (39.0..=41.0).contains(&lat) && (-41.0..=-39.0).contains(&lon),
            "expected bbox near (40, -40), got ({lat}, {lon})"
        );
    }

    #[tokio::test]
    async fn already_provisioned_region_skips_both_phases() {
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
        tokio::fs::write(regions_dir.join(format!("{region_id}.osm.pbf")), b"fake")
            .await
            .unwrap();

        let region = load_regions(&td.db.pool).await.unwrap().remove(0);
        let result = provision_region(&td.db, &test_config(tmp.path()), &region).await;

        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn bbox_populated_but_extract_missing_reports_no_overlapping_province_for_non_canadian_bbox()
     {
        // A bbox already set but with no matching province (e.g. seeded
        // outside Canada) should fail Phase 2 permanently rather than
        // attempting a StatsCan lookup at all -- Phase 1 is skipped because
        // the bbox is already populated.
        let td = test_utils::setup().await;
        let tmp = tempfile::tempdir().unwrap();
        let region_id = 1i64;
        sqlx::query!(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon)
             VALUES ($1, 'Test Region', 'UTC', 40.70, -74.01, 40.72, -73.99)",
            region_id,
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let region = load_regions(&td.db.pool).await.unwrap().remove(0);
        let result = provision_region(&td.db, &test_config(tmp.path()), &region).await;

        assert!(matches!(result, Err(ProvisionError::Permanent(_))));
    }
}

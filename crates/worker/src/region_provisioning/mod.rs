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

/// Spawns one background task per region row currently in the DB. Each task
/// retries its own region on transient failure and gives up (permanently,
/// for this process's lifetime) on a failure that re-running won't fix. Does
/// not block the caller -- intended to be `tokio::spawn`ed itself, not
/// awaited, so it never delays worker startup.
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
    let bbox = match (region.min_lat, region.min_lon, region.max_lat, region.max_lon) {
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

//! Downloads Geofabrik provincial OSM PBF extracts and clips/merges them
//! down to one region's bounding box via the `osmium-tool` CLI. See
//! `docs/superpowers/specs/2026-08-18-region-osm-data-caching-design.md`.

use std::path::{Path, PathBuf};

use tracing::info;

use mobilispect_core::remix::BoundingBox;

use super::provinces::Province;

/// Pure -- builds the argv for `osmium extract`, independently testable
/// without invoking the binary. `-b` takes `left,bottom,right,top`
/// (lon,lat,lon,lat), not this app's usual lat-first `BoundingBox` field
/// order.
pub fn build_extract_args(bbox: BoundingBox, input: &Path, output: &Path) -> Vec<String> {
    vec![
        "extract".to_string(),
        "-b".to_string(),
        format!(
            "{},{},{},{}",
            bbox.min_lon, bbox.min_lat, bbox.max_lon, bbox.max_lat
        ),
        input.display().to_string(),
        "-o".to_string(),
        output.display().to_string(),
        "--overwrite".to_string(),
    ]
}

/// Pure -- builds the argv for `osmium merge`.
pub fn build_merge_args(inputs: &[PathBuf], output: &Path) -> Vec<String> {
    let mut args = vec!["merge".to_string()];
    args.extend(inputs.iter().map(|p| p.display().to_string()));
    args.push("-o".to_string());
    args.push(output.display().to_string());
    args.push("--overwrite".to_string());
    args
}

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
    run_osmium_program("osmium", args).await
}

/// Split out from `run_osmium` so tests can substitute a real, always-present
/// binary (e.g. the Unix `true`/`false` commands) for `osmium` itself --
/// this sandbox and CI's mutation-testing job both lack `osmium-tool`, so
/// exercising the success/failure branches below needs a stand-in program,
/// not a mock (mirrors this codebase's existing `with_base_url` test-seam
/// convention for external HTTP clients).
async fn run_osmium_program(program: &str, args: &[String]) -> Result<(), OsmiumError> {
    let output = tokio::process::Command::new(program)
        .args(args)
        .output()
        .await
        .map_err(|e| OsmiumError {
            stderr: e.to_string(),
        })?;
    if !output.status.success() {
        return Err(OsmiumError {
            stderr: String::from_utf8_lossy(&output.stderr).to_string(),
        });
    }
    Ok(())
}

/// Downloads (if not already cached at
/// `{cache_dir}/provinces/{slug}.osm.pbf`) a province's full Geofabrik OSM
/// extract. Shared across every region in that province -- downloaded once,
/// reused by every subsequent `build_region_extract` call that needs it.
pub async fn download_provincial_pbf(
    cache_dir: &Path,
    province: &Province,
) -> anyhow::Result<PathBuf> {
    let path = cache_dir
        .join("provinces")
        .join(format!("{}.osm.pbf", province.geofabrik_slug));
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
    info!(province = %province.name, "region_provisioning: downloading provincial OSM extract");
    let bytes = reqwest::get(&url)
        .await?
        .error_for_status()?
        .bytes()
        .await?;
    tokio::fs::write(&path, &bytes).await?;
    Ok(path)
}

/// Orchestrates the one-province vs multi-province cases: a single
/// overlapping province is clipped directly to the output; multiple
/// provinces are each clipped to a temp file first, then merged into one
/// output. Writes the final extract to `{cache_dir}/regions/{region_id}.osm.pbf`.
pub async fn build_region_extract(
    cache_dir: &Path,
    region_id: i64,
    bbox: BoundingBox,
    provinces: &[&Province],
) -> anyhow::Result<PathBuf> {
    anyhow::ensure!(
        !provinces.is_empty(),
        "no overlapping provinces for region {region_id}"
    );

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

#[cfg(test)]
mod arg_tests {
    use super::*;

    #[test]
    fn extract_args_use_osmium_bbox_order_lon_lat() {
        let bbox = BoundingBox {
            min_lat: 45.40,
            min_lon: -73.70,
            max_lat: 45.60,
            max_lon: -73.50,
        };
        let args = build_extract_args(bbox, Path::new("in.pbf"), Path::new("out.pbf"));
        assert_eq!(args[0], "extract");
        assert_eq!(args[1], "-b");
        assert_eq!(args[2], "-73.7,45.4,-73.5,45.6");
        assert_eq!(args[3], "in.pbf");
        assert_eq!(args[4], "-o");
        assert_eq!(args[5], "out.pbf");
        assert!(args.contains(&"--overwrite".to_string()));
    }

    #[test]
    fn merge_args_list_every_input_before_the_output_flag() {
        let inputs = vec![PathBuf::from("a.pbf"), PathBuf::from("b.pbf")];
        let args = build_merge_args(&inputs, Path::new("out.pbf"));
        assert_eq!(
            args,
            vec!["merge", "a.pbf", "b.pbf", "-o", "out.pbf", "--overwrite"]
        );
    }

    #[test]
    fn merge_args_with_a_single_input() {
        let inputs = vec![PathBuf::from("only.pbf")];
        let args = build_merge_args(&inputs, Path::new("out.pbf"));
        assert_eq!(
            args,
            vec!["merge", "only.pbf", "-o", "out.pbf", "--overwrite"]
        );
    }
}

#[cfg(test)]
mod shell_tests {
    use super::*;

    #[test]
    fn osmium_error_display_includes_stderr() {
        let err = OsmiumError {
            stderr: "boom".to_string(),
        };
        assert_eq!(err.to_string(), "osmium failed: boom");
    }

    #[tokio::test]
    async fn run_osmium_program_returns_ok_when_the_command_exits_zero() {
        // `true` is a real Unix binary present on every CI/dev machine that
        // always exits 0 -- substitutes for `osmium` itself, which this
        // sandbox and CI's mutation-testing job both lack.
        let result = run_osmium_program("true", &[]).await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn run_osmium_program_returns_err_when_the_command_exits_nonzero() {
        let result = run_osmium_program("false", &[]).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn run_osmium_program_returns_err_when_the_program_does_not_exist() {
        let result = run_osmium_program("definitely-not-a-real-binary-xyz", &[]).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn download_provincial_pbf_skips_download_when_already_cached() {
        let tmp = tempfile::tempdir().unwrap();
        let province = &super::super::provinces::PROVINCES[0];
        let cached = tmp
            .path()
            .join("provinces")
            .join(format!("{}.osm.pbf", province.geofabrik_slug));
        tokio::fs::create_dir_all(cached.parent().unwrap())
            .await
            .unwrap();
        tokio::fs::write(&cached, b"already cached").await.unwrap();

        let result = download_provincial_pbf(tmp.path(), province).await.unwrap();

        assert_eq!(result, cached);
        assert_eq!(tokio::fs::read(&result).await.unwrap(), b"already cached");
    }

    #[tokio::test]
    async fn build_region_extract_with_one_province_does_not_create_the_multi_province_tmp_dir() {
        // `osmium` isn't installed in this environment (or in CI's
        // mutation-testing job), so this can't assert success -- but it can
        // assert the *shape* of what was attempted: with exactly one
        // overlapping province, build_region_extract must take the direct
        // single-extract path, never the multi-province clip-then-merge
        // path (which creates a `tmp/` scratch dir it wouldn't otherwise
        // need). Pre-caching the province's PBF also means no network call
        // is needed to reach this point.
        let tmp = tempfile::tempdir().unwrap();
        let province = &super::super::provinces::PROVINCES[0];
        let cached = tmp
            .path()
            .join("provinces")
            .join(format!("{}.osm.pbf", province.geofabrik_slug));
        tokio::fs::create_dir_all(cached.parent().unwrap())
            .await
            .unwrap();
        tokio::fs::write(&cached, b"fake pbf").await.unwrap();

        let bbox = BoundingBox {
            min_lat: 0.0,
            min_lon: 0.0,
            max_lat: 1.0,
            max_lon: 1.0,
        };
        let result = build_region_extract(tmp.path(), 1, bbox, &[province]).await;

        assert!(
            result.is_err(),
            "osmium is not installed in this test environment, so the extract itself must fail"
        );
        assert!(
            !tmp.path().join("tmp").exists(),
            "single-province path must not create the multi-province merge tmp dir"
        );
    }

    #[tokio::test]
    async fn build_region_extract_with_no_provinces_is_an_error() {
        let tmp = tempfile::tempdir().unwrap();
        let bbox = BoundingBox {
            min_lat: 0.0,
            min_lon: 0.0,
            max_lat: 1.0,
            max_lon: 1.0,
        };
        let result = build_region_extract(tmp.path(), 1, bbox, &[]).await;
        assert!(result.is_err());
    }
}

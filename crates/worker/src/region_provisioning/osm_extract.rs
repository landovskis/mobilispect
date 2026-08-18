//! Downloads Geofabrik provincial OSM PBF extracts and clips/merges them
//! down to one region's bounding box via the `osmium-tool` CLI. See
//! `docs/superpowers/specs/2026-08-18-region-osm-data-caching-design.md`.

use std::path::{Path, PathBuf};

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
    let output = tokio::process::Command::new("osmium")
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
    let bytes = reqwest::get(&url).await?.error_for_status()?.bytes().await?;
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
        assert_eq!(args, vec!["merge", "only.pbf", "-o", "out.pbf", "--overwrite"]);
    }
}

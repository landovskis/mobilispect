//! Matches a region's name against Statistics Canada's CMA/CA cartographic
//! boundary file and computes a WGS84 bounding box from the match. See
//! `docs/superpowers/specs/2026-08-18-region-osm-data-caching-design.md`.

use std::path::Path;

use proj4rs::proj::Proj;
use unicode_normalization::UnicodeNormalization;

use mobilispect_core::remix::BoundingBox;

/// One record from the StatsCan CMA/CA cartographic boundary shapefile: the
/// `CMANAME` attribute and every ring point of its polygon geometry, still in
/// the file's native projection (EPSG:3347 -- NAD83 / Statistics Canada
/// Lambert), concatenated across all rings.
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

    let exact: Vec<&CmaCaRecord> = records
        .iter()
        .filter(|r| normalize(&r.name) == needle)
        .collect();
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

/// EPSG:3347 (NAD83 / Statistics Canada Lambert). Confirmed via independent
/// search against spatialreference.org/epsg.io (direct fetch to those
/// domains is blocked in this sandbox, so this was verified through search
/// result text, not a live page render) -- the published proj4 string uses
/// `+ellps=GRS80 +towgs84=...` where this uses the equivalent `+datum=NAD83`
/// shorthand (NAD83 is defined as GRS80 with a near-zero WGS84 shift, so
/// these resolve to the same transform); all numeric parameters
/// (lat_1/lat_2/lat_0/lon_0/x_0/y_0) match exactly.
const LAMBERT_PROJ4: &str = "+proj=lcc +lat_1=49 +lat_2=77 +lat_0=63.390675 +lon_0=-91.86666666666666 +x_0=6200000 +y_0=3000000 +datum=NAD83 +units=m +no_defs";
const WGS84_PROJ4: &str = "+proj=longlat +datum=WGS84 +no_defs";

#[derive(Debug)]
pub struct ProjectionError(String);

impl std::fmt::Display for ProjectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "projection error: {}", self.0)
    }
}
impl std::error::Error for ProjectionError {}

/// Reprojects every matched record's points from EPSG:3347 (NAD83 /
/// Statistics Canada Lambert) to EPSG:4326 (WGS84) and folds them to a
/// bounding box. `proj4rs::transform::transform` works in radians;
/// StatsCan's Lambert coordinates are already in metres (the CRS's native
/// unit), so no unit conversion is needed on input -- only a
/// radians-to-degrees conversion on the WGS84 output.
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

    Ok(BoundingBox {
        min_lat,
        min_lon,
        max_lat,
        max_lon,
    })
}

/// Downloads (if not already cached at `{cache_dir}/statcan/cma_ca_2021.zip`)
/// and unzips the national CMA/CA cartographic boundary file, then parses
/// every record via the `shapefile` crate.
///
/// **Implementation note:** this sandbox's egress proxy blocks
/// `statcan.gc.ca` outright (confirmed via both direct fetch and search),
/// so the exact 2021 CMA/CA cartographic boundary zip filename could not be
/// confirmed with certainty. Two candidate filename patterns turned up
/// searching independently:
/// - `lcma000b21a_e.zip` -- the `a` (ArcInfo/shapefile export) format-code
///   letter StatsCan's boundary files have used across census cycles back to
///   at least 2001/2006/2011/2016 (a real `lcma000b16a_e.zip` for the 2016
///   census was found via search).
/// - `lcma000b21s_e.zip` -- multiple layer names on StatsCan's live 2021
///   `Cartographic_boundary_files` ArcGIS `MapServer` use an `s` suffix
///   (`lcma000b21s_e`, `lcd_000b21s_e`, `lfsa000b21s_e`, etc.), though that's
///   the REST service's internal layer naming, not necessarily the
///   downloadable zip's filename.
///
/// `download_statcan_zip` tries `a` first (the pattern with historical
/// precedent as an actual download filename) and falls back to `s` on a 404,
/// rather than hardcoding a single guess. Confirm against the real download
/// page (`https://www12.statcan.gc.ca/census-recensement/2021/geo/sip-pis/boundary-limites/index2021-eng.cfm`)
/// when this runs somewhere with real network access, and simplify back to
/// one URL once confirmed.
pub async fn load_cma_ca_records(cache_dir: &Path) -> anyhow::Result<Vec<CmaCaRecord>> {
    let zip_path = cache_dir.join("statcan").join("cma_ca_2021.zip");
    if !zip_path.exists() {
        download_statcan_zip(&zip_path).await?;
    }
    let zip_path = zip_path.clone();
    tokio::task::spawn_blocking(move || parse_cma_ca_zip(&zip_path)).await?
}

const STATCAN_CMA_CA_URL_CANDIDATES: &[&str] = &[
    "https://www12.statcan.gc.ca/census-recensement/2021/geo/sip-pis/boundary-limites/files-fichiers/lcma000b21a_e.zip",
    "https://www12.statcan.gc.ca/census-recensement/2021/geo/sip-pis/boundary-limites/files-fichiers/lcma000b21s_e.zip",
];

async fn download_statcan_zip(dest: &Path) -> anyhow::Result<()> {
    if let Some(parent) = dest.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }

    let mut last_err = None;
    for url in STATCAN_CMA_CA_URL_CANDIDATES {
        match reqwest::get(*url).await.and_then(|r| r.error_for_status()) {
            Ok(response) => {
                let bytes = response.bytes().await?;
                tokio::fs::write(dest, &bytes).await?;
                return Ok(());
            }
            Err(e) => last_err = Some(e),
        }
    }
    Err(anyhow::anyhow!(
        "all StatsCan CMA/CA boundary file URL candidates failed: {}",
        last_err.expect("STATCAN_CMA_CA_URL_CANDIDATES is non-empty")
    ))
}

/// Synchronous (the `shapefile`/`zip` crates are blocking I/O) -- always
/// called via `tokio::task::spawn_blocking` from `load_cma_ca_records`.
fn parse_cma_ca_zip(zip_path: &Path) -> anyhow::Result<Vec<CmaCaRecord>> {
    let file = std::fs::File::open(zip_path)?;
    let mut archive = zip::ZipArchive::new(file)?;

    // Extract the .shp/.shx/.dbf triple to a temp dir -- the `shapefile`
    // crate reads from paths, not in-memory buffers, so the zip's members
    // are written out first.
    let tmp = tempfile::tempdir()?;
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i)?;
        let name = entry.name().to_string();
        let lower = name.to_lowercase();
        if lower.ends_with(".shp") || lower.ends_with(".shx") || lower.ends_with(".dbf") {
            let file_name = Path::new(&name)
                .file_name()
                .ok_or_else(|| anyhow::anyhow!("invalid zip entry name: {name}"))?;
            let out_path = tmp.path().join(file_name);
            let mut out = std::fs::File::create(&out_path)?;
            std::io::copy(&mut entry, &mut out)?;
        }
    }

    let shp_path = std::fs::read_dir(tmp.path())?
        .filter_map(|e| e.ok())
        .find(|e| e.path().extension().is_some_and(|ext| ext == "shp"))
        .ok_or_else(|| anyhow::anyhow!("no .shp member found in StatsCan zip"))?
        .path();

    parse_cma_ca_shapefile(&shp_path)
}

/// Reads CMA/CA records from an already-extracted `.shp` (with its
/// sibling `.shx`/`.dbf` alongside it). Split out from `parse_cma_ca_zip` so
/// tests can build a small shapefile directly, without a checked-in binary
/// zip fixture.
fn parse_cma_ca_shapefile(shp_path: &Path) -> anyhow::Result<Vec<CmaCaRecord>> {
    let mut reader = shapefile::Reader::from_path(shp_path)?;
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
        records.push(CmaCaRecord {
            name,
            points_lambert,
        });
    }
    Ok(records)
}

#[cfg(test)]
mod match_tests {
    use super::*;

    fn record(name: &str) -> CmaCaRecord {
        CmaCaRecord {
            name: name.to_string(),
            points_lambert: vec![],
        }
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
        let records = vec![record(
            "Ottawa - Gatineau (Ontario part / partie de l'Ontario)",
        )];
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

#[cfg(test)]
mod reprojection_tests {
    use super::*;

    /// Round-trips a known WGS84 point through the same `proj4rs` library
    /// (WGS84 -> Lambert, forward) and back via `reproject_and_bbox`
    /// (Lambert -> WGS84, the function under test), rather than asserting
    /// against a hardcoded Lambert coordinate pair -- this sandbox has no
    /// live access to feed the same point through an independent EPSG:3347
    /// converter to cross-check a hardcoded value against (`LAMBERT_PROJ4`'s
    /// own parameters are separately confirmed correct via search against
    /// spatialreference.org/epsg.io -- see that constant's doc comment). A
    /// round trip still proves `reproject_and_bbox` correctly drives
    /// `proj4rs` and correctly converts radians<->degrees.
    #[test]
    fn reprojection_round_trips_a_known_wgs84_point() {
        let wgs84 = Proj::from_proj_string(WGS84_PROJ4).unwrap();
        let lambert = Proj::from_proj_string(LAMBERT_PROJ4).unwrap();

        // Approximately downtown Ottawa.
        let (orig_lon, orig_lat) = (-75.70_f64, 45.42_f64);
        let mut point = (orig_lon.to_radians(), orig_lat.to_radians(), 0.0);
        proj4rs::transform::transform(&wgs84, &lambert, &mut point).unwrap();

        let record = CmaCaRecord {
            name: "Test".to_string(),
            points_lambert: vec![(point.0, point.1)],
        };
        let bbox = reproject_and_bbox(&[&record]).unwrap();

        assert!((bbox.min_lat - orig_lat).abs() < 0.01);
        assert!((bbox.min_lon - orig_lon).abs() < 0.01);
        assert_eq!(bbox.min_lat, bbox.max_lat);
        assert_eq!(bbox.min_lon, bbox.max_lon);
    }

    #[test]
    fn multiple_records_union_into_one_bbox() {
        let wgs84 = Proj::from_proj_string(WGS84_PROJ4).unwrap();
        let lambert = Proj::from_proj_string(LAMBERT_PROJ4).unwrap();

        let mut point_a = ((-75.70_f64).to_radians(), (45.42_f64).to_radians(), 0.0);
        proj4rs::transform::transform(&wgs84, &lambert, &mut point_a).unwrap();
        let mut point_b = ((-73.60_f64).to_radians(), (45.50_f64).to_radians(), 0.0);
        proj4rs::transform::transform(&wgs84, &lambert, &mut point_b).unwrap();

        let a = CmaCaRecord {
            name: "A".to_string(),
            points_lambert: vec![(point_a.0, point_a.1)],
        };
        let b = CmaCaRecord {
            name: "B".to_string(),
            points_lambert: vec![(point_b.0, point_b.1)],
        };
        let bbox = reproject_and_bbox(&[&a, &b]).unwrap();

        assert!(bbox.min_lon < -74.0, "should span both points' longitudes");
        assert!(bbox.max_lon > -74.0);
    }

    #[test]
    fn empty_input_is_an_error() {
        assert!(reproject_and_bbox(&[]).is_err());
    }
}

#[cfg(test)]
mod parse_tests {
    use super::*;
    use std::convert::TryInto;

    /// Builds a small, valid CMA/CA-shaped shapefile (one polygon record
    /// with a `CMANAME` field, coordinates in the same rough magnitude as
    /// real EPSG:3347 values) in a temp dir and parses it back -- proves
    /// `parse_cma_ca_shapefile`'s mechanics (shape + dbf record pairing,
    /// `CMANAME` extraction, ring-point flattening) without needing a
    /// checked-in binary StatsCan fixture or live network access.
    #[test]
    fn parses_a_polygon_record_with_cmaname_and_ring_points() {
        let tmp = tempfile::tempdir().unwrap();
        let shp_path = tmp.path().join("test.shp");

        let table_builder =
            dbase::TableWriterBuilder::new().add_character_field("CMANAME".try_into().unwrap(), 100);
        let mut writer = shapefile::Writer::from_path(&shp_path, table_builder).unwrap();

        let polygon = shapefile::Polygon::new(shapefile::PolygonRing::Outer(vec![
            shapefile::Point::new(7_600_000.0, 1_500_000.0),
            shapefile::Point::new(7_610_000.0, 1_500_000.0),
            shapefile::Point::new(7_610_000.0, 1_510_000.0),
            shapefile::Point::new(7_600_000.0, 1_500_000.0),
        ]));
        let mut record = dbase::Record::default();
        record.insert(
            "CMANAME".to_string(),
            dbase::FieldValue::Character(Some("Test City".to_string())),
        );
        writer.write_shape_and_record(&polygon, &record).unwrap();
        drop(writer);

        let records = parse_cma_ca_shapefile(&shp_path).unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].name, "Test City");
        assert_eq!(records[0].points_lambert.len(), 4);
        assert_eq!(records[0].points_lambert[0], (7_600_000.0, 1_500_000.0));
    }

    #[test]
    fn skips_records_with_no_cmaname_field_value() {
        let tmp = tempfile::tempdir().unwrap();
        let shp_path = tmp.path().join("test.shp");

        let table_builder =
            dbase::TableWriterBuilder::new().add_character_field("CMANAME".try_into().unwrap(), 100);
        let mut writer = shapefile::Writer::from_path(&shp_path, table_builder).unwrap();

        let polygon = shapefile::Polygon::new(shapefile::PolygonRing::Outer(vec![
            shapefile::Point::new(0.0, 0.0),
            shapefile::Point::new(1.0, 0.0),
            shapefile::Point::new(1.0, 1.0),
            shapefile::Point::new(0.0, 0.0),
        ]));
        // CMANAME present in the schema but with no value -- the writer
        // requires every declared field to be set on write, so this is how
        // a "null" character field is represented, matching how a real
        // shapefile can have blank CMANAME values for some record types.
        let mut record = dbase::Record::default();
        record.insert("CMANAME".to_string(), dbase::FieldValue::Character(None));
        writer.write_shape_and_record(&polygon, &record).unwrap();
        drop(writer);

        let records = parse_cma_ca_shapefile(&shp_path).unwrap();
        assert!(records.is_empty());
    }
}

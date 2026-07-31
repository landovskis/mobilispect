//! Corridor Design repository: the imperative I/O shell for corridors and
//! cross-sections. Pure normalization logic lives in `geometry.rs`; this module
//! persists an already-normalized corridor and reads it back — no validation or
//! geometry computation happens here.

use crate::corridor_design::CrossSection;
use crate::corridor_design::geometry::NormalizedCorridor;
use crate::ids::CorridorId;

/// Persists a newly imported corridor and its ordered cross-sections.
///
/// `normalized` must already be validated (see `geometry::normalize_corridor_geometry`)
/// — this function performs no geometry validation of its own.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-001-07 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub async fn insert_corridor(
    pool: &sqlx::PgPool,
    name: &str,
    import_format: &str,
    osm_attribution: Option<&str>,
    normalized: &NormalizedCorridor,
) -> Result<CorridorId, anyhow::Error> {
    let _ = (pool, name, import_format, osm_attribution, normalized);
    unimplemented!("IMP-REQ-001-07: insert_corridor not yet implemented")
}

/// Fetches all cross-sections for a corridor, ordered by `position`.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-001-07 (Loop B GREEN pass). This stub exists so
/// later requirements' integration tests can compile and fail for the right reason.
pub async fn get_corridor_cross_sections(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
) -> Result<Vec<CrossSection>, anyhow::Error> {
    let _ = (pool, corridor_id);
    unimplemented!("IMP-REQ-001-07: get_corridor_cross_sections not yet implemented")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::corridor_design::Coordinate;
    use crate::corridor_design::geometry::CrossSectionPoint;
    use crate::db::test_utils;

    /// A normalized 3-point corridor spanning two source ways, matching the shape
    /// `normalize_corridor_geometry` would produce for TC-REQ-001-1's fixture.
    fn sample_normalized() -> NormalizedCorridor {
        NormalizedCorridor {
            cross_sections: vec![
                CrossSectionPoint {
                    position: 0,
                    coordinate: Coordinate::new(45.500, -73.580),
                    osm_way_id: Some(111),
                    osm_node_id: Some(1),
                },
                CrossSectionPoint {
                    position: 1,
                    coordinate: Coordinate::new(45.501, -73.579),
                    osm_way_id: Some(111),
                    osm_node_id: Some(2),
                },
                CrossSectionPoint {
                    position: 2,
                    coordinate: Coordinate::new(45.502, -73.578),
                    osm_way_id: Some(112),
                    osm_node_id: Some(3),
                },
            ],
        }
    }

    #[derive(sqlx::FromRow)]
    struct CrossSectionRow {
        position: i32,
        lat: f64,
        lon: f64,
    }

    /// TC-REQ-001-1 (persistence slice): a normalized 3-point corridor is persisted
    /// with `cross_sections` ordered 0,1,2 and coordinates matching the input, in
    /// physical path order.
    #[tokio::test]
    async fn insert_corridor_persists_ordered_cross_sections() {
        let td = test_utils::setup().await;
        let db = td.db;
        let normalized = sample_normalized();

        let corridor_id = insert_corridor(
            &db.pool,
            "Test Corridor A",
            "geojson_osm_export",
            Some("© OpenStreetMap contributors"),
            &normalized,
        )
        .await
        .expect("insert_corridor should succeed once implemented");

        let rows: Vec<CrossSectionRow> = sqlx::query_as(
            "SELECT position, lat, lon FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        )
        .bind(corridor_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 3);
        let positions: Vec<i32> = rows.iter().map(|r| r.position).collect();
        assert_eq!(positions, vec![0, 1, 2]);
        for (row, expected) in rows.iter().zip(normalized.cross_sections.iter()) {
            assert!((row.lat - expected.coordinate.lat).abs() < 1e-9);
            assert!((row.lon - expected.coordinate.lon).abs() < 1e-9);
        }
    }

    /// TC-REQ-001-1 (persistence slice): `osm_attribution` and `import_format` are
    /// stored verbatim, and `geometry_source` is recorded as `'imported'`.
    #[tokio::test]
    async fn insert_corridor_stores_osm_attribution_and_import_format() {
        let td = test_utils::setup().await;
        let db = td.db;
        let normalized = sample_normalized();
        let attribution = "© OpenStreetMap contributors";

        let corridor_id = insert_corridor(
            &db.pool,
            "Test Corridor B",
            "geojson_osm_export",
            Some(attribution),
            &normalized,
        )
        .await
        .expect("insert_corridor should succeed once implemented");

        let row: (String, Option<String>, String) = sqlx::query_as(
            "SELECT geometry_source, osm_attribution, import_format FROM corridors WHERE id = $1",
        )
        .bind(corridor_id.as_i64())
        .fetch_one(&db.pool)
        .await
        .unwrap();

        let (geometry_source, osm_attribution, import_format) = row;
        assert_eq!(geometry_source, "imported");
        assert_eq!(osm_attribution.as_deref(), Some(attribution));
        assert_eq!(import_format, "geojson_osm_export");
    }
}

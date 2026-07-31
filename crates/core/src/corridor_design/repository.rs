//! Corridor Design repository: the imperative I/O shell for corridors and
//! cross-sections. Pure normalization logic lives in `geometry.rs`; this module
//! persists an already-normalized corridor and reads it back — no validation or
//! geometry computation happens here.

use crate::corridor_design::Coordinate;
use crate::corridor_design::CrossSection;
use crate::corridor_design::geometry::NormalizedCorridor;
use crate::ids::{CorridorId, CrossSectionId};

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

/// Creates a new corridor for a manual trace (REQ-002), with `geometry_source =
/// 'manual'` and no `import_format`/`osm_attribution`. Cross-sections are added one
/// at a time afterward via `insert_cross_section` as the analyst clicks.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-002-06 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub async fn start_manual_corridor(
    pool: &sqlx::PgPool,
    name: &str,
) -> Result<CorridorId, anyhow::Error> {
    let _ = (pool, name);
    unimplemented!("IMP-REQ-002-06: start_manual_corridor not yet implemented")
}

/// Inserts a single cross-section point at `position` for an existing corridor.
///
/// Caller must have already validated `coordinate` against the corridor's existing
/// points (see `geometry::validate_next_point`) — this function performs no geometry
/// validation of its own. Returns an error if `corridor_id` does not reference an
/// existing corridor.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-002-06 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub async fn insert_cross_section(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
    coordinate: Coordinate,
    position: i32,
) -> Result<CrossSectionId, anyhow::Error> {
    let _ = (pool, corridor_id, coordinate, position);
    unimplemented!("IMP-REQ-002-06: insert_cross_section not yet implemented")
}

/// Marks a manually-traced corridor as finished. Caller must have already validated
/// the corridor has enough points (see `geometry::validate_finishable`) — this
/// function performs no geometry validation of its own.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-002-06 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub async fn finalize_corridor(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
) -> Result<(), anyhow::Error> {
    let _ = (pool, corridor_id);
    unimplemented!("IMP-REQ-002-06: finalize_corridor not yet implemented")
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

    // --- REQ-002: manual trace persistence ---

    /// TC-REQ-002-01: a full manual trace — start a corridor, add 4 points one at a
    /// time, then finalize — results in exactly 4 `cross_sections` rows ordered 0-3
    /// and `corridors.geometry_source = 'manual'`.
    #[tokio::test]
    async fn manual_trace_start_add_points_and_finalize_persists_ordered_cross_sections() {
        let td = test_utils::setup().await;
        let db = td.db;

        let corridor_id = start_manual_corridor(&db.pool, "5th Ave Transit Priority")
            .await
            .expect("start_manual_corridor should succeed once implemented");

        let points = [
            Coordinate::new(45.5017, -73.5673),
            Coordinate::new(45.5031, -73.5661),
            Coordinate::new(45.5045, -73.5649),
            Coordinate::new(45.5059, -73.5637),
        ];
        for (i, coordinate) in points.iter().enumerate() {
            insert_cross_section(&db.pool, corridor_id, *coordinate, i as i32)
                .await
                .expect("insert_cross_section should succeed once implemented");
        }

        finalize_corridor(&db.pool, corridor_id)
            .await
            .expect("finalize_corridor should succeed once implemented");

        let rows: Vec<CrossSectionRow> = sqlx::query_as(
            "SELECT position, lat, lon FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        )
        .bind(corridor_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 4);
        let positions: Vec<i32> = rows.iter().map(|r| r.position).collect();
        assert_eq!(positions, vec![0, 1, 2, 3]);
        for (row, expected) in rows.iter().zip(points.iter()) {
            assert!((row.lat - expected.lat).abs() < 1e-9);
            assert!((row.lon - expected.lon).abs() < 1e-9);
        }

        let geometry_source: String =
            sqlx::query_scalar("SELECT geometry_source FROM corridors WHERE id = $1")
                .bind(corridor_id.as_i64())
                .fetch_one(&db.pool)
                .await
                .unwrap();
        assert_eq!(geometry_source, "manual");
    }

    /// TC-REQ-002-05: a corridor built via the manual point-at-a-time path and one
    /// built via the imported bulk-insert path produce structurally identical
    /// `Vec<CrossSection>` shapes from `get_corridor_cross_sections` — same length,
    /// same ascending `position` sequence, and no field `Some` on one side and
    /// `None` on the other. The imported fixture below deliberately carries no
    /// `osm_way_id`/`osm_node_id` values so this test isolates shape parity from the
    /// (expected, and separately covered) provenance difference between the two
    /// creation paths.
    #[tokio::test]
    async fn manual_and_imported_cross_sections_have_identical_shape() {
        let td = test_utils::setup().await;
        let db = td.db;

        let manual_corridor_id = start_manual_corridor(&db.pool, "CORR-MANUAL")
            .await
            .expect("start_manual_corridor should succeed once implemented");
        let manual_points = [
            Coordinate::new(45.500, -73.580),
            Coordinate::new(45.501, -73.579),
            Coordinate::new(45.502, -73.578),
            Coordinate::new(45.503, -73.577),
        ];
        for (i, coordinate) in manual_points.iter().enumerate() {
            insert_cross_section(&db.pool, manual_corridor_id, *coordinate, i as i32)
                .await
                .expect("insert_cross_section should succeed once implemented");
        }

        let imported_normalized = NormalizedCorridor {
            cross_sections: vec![
                CrossSectionPoint {
                    position: 0,
                    coordinate: Coordinate::new(46.100, -74.100),
                    osm_way_id: None,
                    osm_node_id: None,
                },
                CrossSectionPoint {
                    position: 1,
                    coordinate: Coordinate::new(46.101, -74.099),
                    osm_way_id: None,
                    osm_node_id: None,
                },
                CrossSectionPoint {
                    position: 2,
                    coordinate: Coordinate::new(46.102, -74.098),
                    osm_way_id: None,
                    osm_node_id: None,
                },
                CrossSectionPoint {
                    position: 3,
                    coordinate: Coordinate::new(46.103, -74.097),
                    osm_way_id: None,
                    osm_node_id: None,
                },
            ],
        };
        let imported_corridor_id = insert_corridor(
            &db.pool,
            "CORR-IMPORTED",
            "geojson_osm_export",
            Some("© OpenStreetMap contributors"),
            &imported_normalized,
        )
        .await
        .expect("insert_corridor should succeed once implemented");

        let manual_sections = get_corridor_cross_sections(&db.pool, manual_corridor_id)
            .await
            .expect("get_corridor_cross_sections should succeed once implemented");
        let imported_sections = get_corridor_cross_sections(&db.pool, imported_corridor_id)
            .await
            .expect("get_corridor_cross_sections should succeed once implemented");

        assert_eq!(manual_sections.len(), 4);
        assert_eq!(imported_sections.len(), 4);

        let manual_positions: Vec<i32> = manual_sections.iter().map(|cs| cs.position).collect();
        let imported_positions: Vec<i32> = imported_sections.iter().map(|cs| cs.position).collect();
        assert_eq!(manual_positions, vec![0, 1, 2, 3]);
        assert_eq!(imported_positions, vec![0, 1, 2, 3]);

        for (manual, imported) in manual_sections.iter().zip(imported_sections.iter()) {
            assert_eq!(
                manual.position, imported.position,
                "position should occupy the identical slot in both sequences"
            );
            assert_eq!(
                manual.osm_way_id.is_none(),
                imported.osm_way_id.is_none(),
                "osm_way_id presence should match across creation paths for this schema-parity fixture"
            );
            assert_eq!(
                manual.osm_node_id.is_none(),
                imported.osm_node_id.is_none(),
                "osm_node_id presence should match across creation paths for this schema-parity fixture"
            );
        }
    }

    /// TC-REQ-002-04: adding a point to a corridor that was never inserted (simulating
    /// one deleted/cancelled mid-session) fails rather than silently succeeding.
    #[tokio::test]
    async fn insert_cross_section_into_nonexistent_corridor_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;

        // TODO(Loop B): assert specific NotFound variant once typed error exists
        let result = insert_cross_section(
            &db.pool,
            CorridorId::from(999_999_i64),
            Coordinate::new(45.5017, -73.5673),
            0,
        )
        .await;

        assert!(result.is_err());
    }

    /// TC-REQ-002-06: a point insert while the database is unavailable should fail
    /// with `STORAGE_UNAVAILABLE` (503) and leave previously-placed points intact.
    /// The current `test_utils::setup()` doesn't expose a way to stop the Postgres
    /// container mid-test without also tearing down the harness, so this is deferred
    /// rather than forced into something fragile.
    #[ignore = "IMP-REQ-002-07: needs typed error + a way to simulate DB unavailability mid-test"]
    #[tokio::test]
    async fn point_insert_fails_when_database_is_unavailable() {
        todo!()
    }
}

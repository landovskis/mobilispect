//! Corridor Design repository: the imperative I/O shell for corridors and
//! cross-sections. Pure normalization logic lives in `geometry.rs`; this module
//! persists an already-normalized corridor and reads it back — no validation or
//! geometry computation happens here.

use crate::corridor_design::Coordinate;
use crate::corridor_design::CrossSection;
use crate::corridor_design::geometry::NormalizedCorridor;
use crate::corridor_design::lanes::{Lane, LaneDirection, LaneDraft, LaneType, TimedAccessRule};
use crate::ids::{CorridorId, CrossSectionId, LaneId, RemixId};

/// Persists a newly imported corridor and its ordered cross-sections, scoped to
/// `remix_id`. `normalized` must already be validated (see
/// `geometry::normalize_corridor_geometry`) — this function performs no geometry
/// validation of its own. Does not create any lanes; callers that want an
/// OSM-tag-derived baseline lane set insert them separately via
/// `insert_lanes_for_cross_section` after this returns.
pub async fn insert_corridor(
    pool: &sqlx::PgPool,
    remix_id: RemixId,
    name: &str,
    import_format: &str,
    osm_attribution: Option<&str>,
    normalized: &NormalizedCorridor,
) -> Result<CorridorId, anyhow::Error> {
    let mut tx = pool.begin().await?;

    // `RETURNING id` on a non-test-seeding insert uses the compile-time-checked
    // `query!` macro, per this plan's Global Constraints — the test-seeding
    // exception (runtime `sqlx::query_scalar`) doesn't apply to production code.
    let corridor_id = sqlx::query!(
        "INSERT INTO corridors (remix_id, name, geometry_source, import_format, osm_attribution) \
         VALUES ($1, $2, 'imported', $3, $4) RETURNING id",
        remix_id.as_i64(),
        name,
        import_format,
        osm_attribution,
    )
    .fetch_one(&mut *tx)
    .await?
    .id;

    for cs in &normalized.cross_sections {
        // `$2::float8`: `position` is a NUMERIC column and this crate has no
        // bigdecimal/rust_decimal sqlx feature enabled (see `position.rs`'s
        // top-of-file note on the same constraint) — casting the bind
        // placeholder to `float8` lets `query!` accept an `f64` argument here
        // (Postgres implicitly casts float8 -> numeric on insert), mirroring
        // this codebase's existing `position::float8 AS "position!"` pattern
        // for the read direction.
        sqlx::query!(
            "INSERT INTO cross_sections (corridor_id, position, lat, lon, osm_way_id, osm_node_id) \
             VALUES ($1, $2::float8, $3, $4, $5, $6)",
            corridor_id,
            f64::from(cs.position),
            cs.coordinate.lat,
            cs.coordinate.lon,
            cs.osm_way_id,
            cs.osm_node_id,
        )
        .execute(&mut *tx)
        .await?;
    }

    tx.commit().await?;
    Ok(CorridorId::from(corridor_id))
}

/// Fetches all cross-sections for a corridor, ordered by `position`.
pub async fn get_corridor_cross_sections(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
) -> Result<Vec<CrossSection>, anyhow::Error> {
    let rows = sqlx::query!(
        r#"SELECT id, corridor_id, position::float8 AS "position!", lat, lon,
                  osm_way_id, osm_node_id, label
           FROM cross_sections
           WHERE corridor_id = $1
           ORDER BY position"#,
        corridor_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;

    Ok(rows
        .into_iter()
        .map(|row| CrossSection {
            id: CrossSectionId::from(row.id),
            corridor_id: CorridorId::from(row.corridor_id),
            position: row.position,
            lat: row.lat,
            lon: row.lon,
            osm_way_id: row.osm_way_id,
            osm_node_id: row.osm_node_id,
            label: row.label,
        })
        .collect())
}

/// Creates a new corridor for a manual trace (REQ-002), scoped to `remix_id`,
/// with `geometry_source = 'manual'` and no `import_format`/`osm_attribution`.
/// Cross-sections are added one at a time afterward via `insert_cross_section`
/// as the analyst clicks.
pub async fn start_manual_corridor(
    pool: &sqlx::PgPool,
    remix_id: RemixId,
    name: &str,
) -> Result<CorridorId, anyhow::Error> {
    let corridor_id = sqlx::query!(
        "INSERT INTO corridors (remix_id, name, geometry_source) VALUES ($1, $2, 'manual') RETURNING id",
        remix_id.as_i64(),
        name,
    )
    .fetch_one(pool)
    .await?
    .id;
    Ok(CorridorId::from(corridor_id))
}

/// Inserts a single cross-section point at `position` for an existing corridor.
/// Caller must have already validated `coordinate` against the corridor's
/// existing points (see `geometry::validate_next_point`) — this function
/// performs no geometry validation of its own. Returns an error if
/// `corridor_id` does not reference an existing corridor.
pub async fn insert_cross_section(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
    coordinate: Coordinate,
    position: i32,
) -> Result<CrossSectionId, anyhow::Error> {
    // `AS "exists!"`: `EXISTS(...)` always yields a genuine boolean, but sqlx's
    // `describe` reports computed expressions as nullable by default — the `!`
    // suffix forces the non-null type, matching this codebase's established
    // force-non-null idiom (e.g. `db/feeds.rs`'s `agency_onestop_id!`).
    let exists = sqlx::query_scalar!(
        r#"SELECT EXISTS(SELECT 1 FROM corridors WHERE id = $1) AS "exists!""#,
        corridor_id.as_i64(),
    )
    .fetch_one(pool)
    .await?;
    if !exists {
        anyhow::bail!("corridor {corridor_id} does not exist");
    }

    let id = sqlx::query!(
        "INSERT INTO cross_sections (corridor_id, position, lat, lon) VALUES ($1, $2::float8, $3, $4) RETURNING id",
        corridor_id.as_i64(),
        f64::from(position),
        coordinate.lat,
        coordinate.lon,
    )
    .fetch_one(pool)
    .await?
    .id;
    Ok(CrossSectionId::from(id))
}

/// Marks a manually-traced corridor as finished. Caller must have already
/// validated the corridor has enough points (see `geometry::validate_finishable`)
/// — this function performs no geometry validation of its own. Currently a
/// no-op beyond confirming the corridor exists (there is no "finished" flag on
/// `corridors` yet) — kept as its own function/step because the manual-trace
/// UI flow (start -> add points -> finish) treats it as a distinct action, and
/// a later plan may add real finalization behavior here (e.g. a
/// `finalized_at` timestamp).
pub async fn finalize_corridor(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
) -> Result<(), anyhow::Error> {
    let exists = sqlx::query_scalar!(
        r#"SELECT EXISTS(SELECT 1 FROM corridors WHERE id = $1) AS "exists!""#,
        corridor_id.as_i64(),
    )
    .fetch_one(pool)
    .await?;
    if !exists {
        anyhow::bail!("corridor {corridor_id} does not exist");
    }
    Ok(())
}

fn time_window_columns(
    rule: &TimedAccessRule,
) -> (
    Option<String>,
    Option<chrono::NaiveTime>,
    Option<chrono::NaiveTime>,
) {
    match &rule.time_window {
        Some(w) => (Some(w.days.clone()), Some(w.start_time), Some(w.end_time)),
        None => (None, None, None),
    }
}

/// Inserts `drafts` as new lanes for `cross_section_id`, in the given order
/// (left-to-right), assigning each a fractional `position` (`1.0`, `2.0`, ...).
/// Each lane's `access_rules` are inserted as `lane_access_rules` rows.
pub async fn insert_lanes_for_cross_section(
    pool: &sqlx::PgPool,
    cross_section_id: CrossSectionId,
    drafts: &[LaneDraft],
) -> Result<Vec<LaneId>, anyhow::Error> {
    let mut tx = pool.begin().await?;
    let mut lane_ids = Vec::with_capacity(drafts.len());

    for (i, draft) in drafts.iter().enumerate() {
        let position = (i + 1) as f64;
        let lane_type = draft.lane_type.as_db_str();
        let direction = draft.direction.as_db_str();
        // `$2::float8` — same NUMERIC-column reasoning as `insert_corridor`'s
        // cross-section insert above.
        let lane_id = sqlx::query!(
            "INSERT INTO lanes (cross_section_id, position, lane_type, width_meters, direction) \
             VALUES ($1, $2::float8, $3, $4, $5) RETURNING id",
            cross_section_id.as_i64(),
            position,
            lane_type,
            draft.width_meters,
            direction,
        )
        .fetch_one(&mut *tx)
        .await?
        .id;

        for rule in &draft.access_rules {
            let (days, start_time, end_time) = time_window_columns(rule);
            let allowed_modes: Vec<&str> =
                rule.allowed_modes.iter().map(|m| m.as_db_str()).collect();
            sqlx::query!(
                "INSERT INTO lane_access_rules (lane_id, days, start_time, end_time, allowed_modes) \
                 VALUES ($1, $2, $3, $4, $5)",
                lane_id,
                days,
                start_time,
                end_time,
                &allowed_modes as &[&str],
            )
            .execute(&mut *tx)
            .await?;
        }

        lane_ids.push(LaneId::from(lane_id));
    }

    tx.commit().await?;
    Ok(lane_ids)
}

struct LaneRow {
    id: i64,
    cross_section_id: i64,
    position: f64,
    lane_type: String,
    width_meters: f64,
    direction: String,
}

struct AccessRuleRow {
    lane_id: i64,
    days: Option<String>,
    start_time: Option<chrono::NaiveTime>,
    end_time: Option<chrono::NaiveTime>,
    allowed_modes: Vec<String>,
}

/// Fetches all lanes for a cross-section, ordered left-to-right, each with its
/// access rules.
pub async fn get_lanes_for_cross_section(
    pool: &sqlx::PgPool,
    cross_section_id: CrossSectionId,
) -> Result<Vec<Lane>, anyhow::Error> {
    let lane_rows: Vec<LaneRow> = sqlx::query_as!(
        LaneRow,
        r#"SELECT id, cross_section_id, position::float8 AS "position!", lane_type, width_meters, direction
           FROM lanes
           WHERE cross_section_id = $1
           ORDER BY position"#,
        cross_section_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;

    if lane_rows.is_empty() {
        return Ok(Vec::new());
    }

    let lane_ids: Vec<i64> = lane_rows.iter().map(|r| r.id).collect();
    let rule_rows: Vec<AccessRuleRow> = sqlx::query_as!(
        AccessRuleRow,
        r#"SELECT lane_id, days, start_time, end_time, allowed_modes AS "allowed_modes!"
           FROM lane_access_rules
           WHERE lane_id = ANY($1)"#,
        &lane_ids,
    )
    .fetch_all(pool)
    .await?;

    let mut lanes = Vec::with_capacity(lane_rows.len());
    for row in lane_rows {
        let lane_type = LaneType::from_db_str(&row.lane_type)
            .ok_or_else(|| anyhow::anyhow!("unknown lane_type value: {}", row.lane_type))?;
        let direction = LaneDirection::from_db_str(&row.direction)
            .ok_or_else(|| anyhow::anyhow!("unknown direction value: {}", row.direction))?;

        let mut access_rules = Vec::new();
        for rule_row in rule_rows.iter().filter(|r| r.lane_id == row.id) {
            let mut allowed_modes = Vec::with_capacity(rule_row.allowed_modes.len());
            for mode_str in &rule_row.allowed_modes {
                let mode = crate::corridor_design::lanes::AccessMode::from_db_str(mode_str)
                    .ok_or_else(|| anyhow::anyhow!("unknown access mode value: {mode_str}"))?;
                allowed_modes.push(mode);
            }
            let time_window = match (&rule_row.days, rule_row.start_time, rule_row.end_time) {
                (Some(days), Some(start_time), Some(end_time)) => {
                    Some(crate::corridor_design::lanes::TimeWindow {
                        days: days.clone(),
                        start_time,
                        end_time,
                    })
                }
                _ => None,
            };
            access_rules.push(TimedAccessRule {
                time_window,
                allowed_modes,
            });
        }

        lanes.push(Lane {
            id: LaneId::from(row.id),
            cross_section_id: CrossSectionId::from(row.cross_section_id),
            position: row.position,
            lane_type,
            width_meters: row.width_meters,
            direction,
            access_rules,
        });
    }

    Ok(lanes)
}

/// Inserts a new cross-section into an existing corridor's sequence, at a
/// fractional `position` computed between the two rows bracketing `insert_after`
/// (see `corridor_design::position::assign_position`). `insert_after = None` means
/// "insert at the start of the sequence"; a cross-section with no successor is
/// treated as "insert at the end."
///
/// Returns an error if `corridor_id` does not reference an existing corridor, if
/// `insert_after` does not resolve to a cross-section belonging to `corridor_id`
/// (e.g. it belongs to a different corridor), or if a concurrent insert lands on
/// the same computed position first.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-004-06 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub async fn add_cross_section(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
    insert_after: Option<CrossSectionId>,
    coordinate: Coordinate,
) -> Result<CrossSection, anyhow::Error> {
    let _ = (pool, corridor_id, insert_after, coordinate);
    unimplemented!("IMP-REQ-004-06: add_cross_section not yet implemented")
}

/// Reorders every cross-section in a corridor's sequence to match
/// `requested_order`, in a single transaction: validates `requested_order` is
/// exactly a permutation of the corridor's current cross-section ID set (see
/// `corridor_design::position::compute_reordered_positions`), batch-rewrites
/// every row's `position` to the freshly computed evenly-spaced values, and
/// advances `corridors.sequence_version` by one.
///
/// `expected_version` is an optimistic-concurrency check: if it no longer
/// matches `corridors.sequence_version` (the corridor was reordered elsewhere
/// since the caller loaded it), the whole operation is rejected and nothing is
/// written. Returns the new `sequence_version` and the corridor's
/// cross-sections in their new order on success.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-005-05 (Loop B GREEN pass). This stub
/// exists so Loop A's tests compile and fail for the right reason (production
/// code absent).
pub async fn reorder_cross_sections(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
    expected_version: i64,
    requested_order: &[CrossSectionId],
) -> Result<(i64, Vec<CrossSection>), anyhow::Error> {
    let _ = (pool, corridor_id, expected_version, requested_order);
    unimplemented!("IMP-REQ-005-05: reorder_cross_sections not yet implemented")
}

/// Updates a single cross-section's descriptive `label`, enforcing optimistic
/// concurrency via `expected_version` against the row's `version` column. Issues a
/// single targeted `UPDATE cross_sections SET label = ..., version = version + 1
/// WHERE id = $1 AND corridor_id = $2 AND version = $3` — reads and writes exactly
/// one row, proving the isolation guarantee's data-layer half (see
/// `corridor_design::edit::apply_cross_section_edit` for the pure Functional-Core
/// half that proves the same guarantee in memory). Returns an error if
/// `cross_section_id` does not exist (e.g. deleted since the caller's edit view
/// loaded) or does not belong to `corridor_id`, or if `expected_version` no longer
/// matches the stored `version` (a concurrent edit landed first).
///
/// NOT YET IMPLEMENTED — see IMP-REQ-006-07 (Loop B GREEN pass). This stub exists
/// so Loop A's tests compile and fail for the right reason (production code
/// absent).
pub async fn update_cross_section_label(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
    cross_section_id: CrossSectionId,
    new_label: Option<String>,
    expected_version: i32,
) -> Result<CrossSection, anyhow::Error> {
    let _ = (
        pool,
        corridor_id,
        cross_section_id,
        new_label,
        expected_version,
    );
    unimplemented!("IMP-REQ-006-07: update_cross_section_label not yet implemented")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::corridor_design::Coordinate;
    use crate::corridor_design::geometry::CrossSectionPoint;
    use crate::corridor_design::lanes::{LaneDirection, LaneDraft, LaneType, TimedAccessRule};
    use crate::db::test_utils;

    /// Seeds a region (with a bounding box) and a remix in it, returning the
    /// remix id. Every REQ-001/002 test needs a remix to scope its corridor to.
    async fn seed_remix(pool: &sqlx::PgPool) -> RemixId {
        sqlx::query(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon) \
             VALUES (1, 'Test Region', 'UTC', 45.40, -73.70, 45.60, -73.50) \
             ON CONFLICT (id) DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();

        let remix_id: i64 =
            sqlx::query_scalar("INSERT INTO remixes (name, region_id) VALUES ($1, 1) RETURNING id")
                .bind("Test Remix")
                .fetch_one(pool)
                .await
                .unwrap();
        RemixId::from(remix_id)
    }

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
        position: f64,
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
        let remix_id = seed_remix(&db.pool).await;

        let corridor_id = insert_corridor(
            &db.pool,
            remix_id,
            "Test Corridor A",
            "geojson_osm_export",
            Some("© OpenStreetMap contributors"),
            &normalized,
        )
        .await
        .expect("insert_corridor should succeed once implemented");

        let rows: Vec<CrossSectionRow> = sqlx::query_as(
            "SELECT position::float8 AS position, lat, lon FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        )
        .bind(corridor_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 3);
        let positions: Vec<f64> = rows.iter().map(|r| r.position).collect();
        assert_eq!(positions, vec![0.0, 1.0, 2.0]);
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
        let remix_id = seed_remix(&db.pool).await;

        let corridor_id = insert_corridor(
            &db.pool,
            remix_id,
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
        let remix_id = seed_remix(&db.pool).await;

        let corridor_id = start_manual_corridor(&db.pool, remix_id, "5th Ave Transit Priority")
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
            "SELECT position::float8 AS position, lat, lon FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        )
        .bind(corridor_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 4);
        let positions: Vec<f64> = rows.iter().map(|r| r.position).collect();
        assert_eq!(positions, vec![0.0, 1.0, 2.0, 3.0]);
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
        let remix_id = seed_remix(&db.pool).await;

        let manual_corridor_id = start_manual_corridor(&db.pool, remix_id, "CORR-MANUAL")
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
            remix_id,
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

        let manual_positions: Vec<f64> = manual_sections.iter().map(|cs| cs.position).collect();
        let imported_positions: Vec<f64> = imported_sections.iter().map(|cs| cs.position).collect();
        assert_eq!(manual_positions, vec![0.0, 1.0, 2.0, 3.0]);
        assert_eq!(imported_positions, vec![0.0, 1.0, 2.0, 3.0]);

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

    // --- REQ-004: add cross-section to an existing sequence ---
    //
    // `insert_corridor`/`insert_cross_section` are themselves still stubs at this
    // point in the sequence, so the fixtures below seed `corridors`/`cross_sections`
    // directly via SQL rather than routing through them. By the time these tests run,
    // migration 022 has already changed `cross_sections.position` to `NUMERIC` — the
    // existing `CrossSectionRow` fixture above (`position: i32`) predates that change
    // and is left untouched per this pass's "additive only" rule; a fractional-aware
    // row type is used below instead. See this module's final report for the
    // follow-on note this leaves for Loop B (both `CrossSectionRow` above and
    // `corridor_design::CrossSection.position` itself are still typed `i32`/decode as
    // such, and will need to move to a fractional-compatible type once `position` is
    // implemented for real).

    #[derive(Debug, sqlx::FromRow)]
    struct FractionalPositionRow {
        id: i64,
        position: f64,
    }

    /// Seeds a corridor (`geometry_source = 'manual'`) with 4 cross-sections at
    /// fractional positions `1.0, 2.0, 3.0, 4.0` — matching TC-REQ-004-1/2/3's stated
    /// preconditions (4 existing cross-sections). Returns the corridor id and the 4
    /// cross-section ids in position order.
    async fn seed_corridor_with_four_cross_sections(
        pool: &sqlx::PgPool,
    ) -> (CorridorId, Vec<CrossSectionId>) {
        let corridor_id: i64 = sqlx::query_scalar(
            "INSERT INTO corridors (name, geometry_source) VALUES ($1, 'manual') RETURNING id",
        )
        .bind("REQ-004 Test Corridor")
        .fetch_one(pool)
        .await
        .unwrap();

        let mut cross_section_ids = Vec::with_capacity(4);
        for position in 1..=4i64 {
            let id: i64 = sqlx::query_scalar(
                "INSERT INTO cross_sections (corridor_id, position, lat, lon) VALUES ($1, $2, $3, $4) RETURNING id",
            )
            .bind(corridor_id)
            .bind(position as f64)
            .bind(45.500 + (position as f64) * 0.001)
            .bind(-73.600 + (position as f64) * 0.001)
            .fetch_one(pool)
            .await
            .unwrap();
            cross_section_ids.push(CrossSectionId::from(id));
        }

        (CorridorId::from(corridor_id), cross_section_ids)
    }

    /// TC-REQ-004-1: adding a cross-section after the last one in the sequence lands
    /// it at the end — its position is strictly greater than the previous last
    /// position (4), and the DB shows exactly 5 rows with the new one last.
    #[tokio::test]
    async fn add_cross_section_at_end_of_sequence() {
        let td = test_utils::setup().await;
        let db = td.db;
        let (corridor_id, cross_section_ids) =
            seed_corridor_with_four_cross_sections(&db.pool).await;
        let last_cross_section_id = *cross_section_ids.last().unwrap();

        let result = add_cross_section(
            &db.pool,
            corridor_id,
            Some(last_cross_section_id),
            Coordinate::new(45.42, -75.69),
        )
        .await;

        let new_cross_section = result.expect("add_cross_section should succeed once implemented");
        assert_eq!(new_cross_section.corridor_id, corridor_id);

        let rows: Vec<FractionalPositionRow> = sqlx::query_as(
            "SELECT id, position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        )
        .bind(corridor_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 5, "TC-REQ-004-1: 4 existing + 1 new = 5 rows");
        assert!(
            rows.last().unwrap().position > 4.0,
            "TC-REQ-004-1: new row's position should be strictly greater than the previous last position (4)"
        );
        assert_eq!(
            rows.last().unwrap().id,
            new_cross_section.id.as_i64(),
            "TC-REQ-004-1: the new row should sort last"
        );
    }

    /// TC-REQ-004-2: adding a cross-section with no `insert_after` (start-of-sequence
    /// boundary) lands it before every existing row — its position is strictly less
    /// than the previous first position (1).
    #[tokio::test]
    async fn add_cross_section_at_start_of_sequence() {
        let td = test_utils::setup().await;
        let db = td.db;
        let (corridor_id, _cross_section_ids) =
            seed_corridor_with_four_cross_sections(&db.pool).await;

        let result =
            add_cross_section(&db.pool, corridor_id, None, Coordinate::new(45.40, -75.70)).await;

        let new_cross_section = result.expect("add_cross_section should succeed once implemented");

        let rows: Vec<FractionalPositionRow> = sqlx::query_as(
            "SELECT id, position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        )
        .bind(corridor_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 5, "TC-REQ-004-2: 4 existing + 1 new = 5 rows");
        assert!(
            rows.first().unwrap().position < 1.0,
            "TC-REQ-004-2: new row's position should be strictly less than the previous first position (1)"
        );
        assert_eq!(
            rows.first().unwrap().id,
            new_cross_section.id.as_i64(),
            "TC-REQ-004-2: the new row should sort first"
        );
    }

    /// TC-REQ-004-3: adding a cross-section between two existing mid-sequence rows
    /// lands its position strictly between its two named neighbors.
    #[tokio::test]
    async fn add_cross_section_between_two_mid_sequence_cross_sections() {
        let td = test_utils::setup().await;
        let db = td.db;
        let (corridor_id, cross_section_ids) =
            seed_corridor_with_four_cross_sections(&db.pool).await;
        // cross_section_ids[1] is at position 2 ("cs_002"); its successor
        // cross_section_ids[2] is at position 3 ("cs_003").
        let anchor = cross_section_ids[1];

        let result = add_cross_section(
            &db.pool,
            corridor_id,
            Some(anchor),
            Coordinate::new(45.41, -75.685),
        )
        .await;

        let new_cross_section = result.expect("add_cross_section should succeed once implemented");

        let rows: Vec<FractionalPositionRow> = sqlx::query_as(
            "SELECT id, position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        )
        .bind(corridor_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 5, "TC-REQ-004-3: 4 existing + 1 new = 5 rows");
        let new_row = rows
            .iter()
            .find(|r| r.id == new_cross_section.id.as_i64())
            .expect("new row should be present");
        assert!(
            new_row.position > 2.0 && new_row.position < 3.0,
            "TC-REQ-004-3: new row's position ({}) should lie strictly between cs_002 (2) and cs_003 (3)",
            new_row.position
        );
    }

    /// TC-REQ-004-4: adding a cross-section to a corridor id that doesn't exist fails
    /// rather than silently succeeding.
    #[tokio::test]
    async fn add_cross_section_to_nonexistent_corridor_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;

        // TODO(Loop B): assert specific CorridorNotFound variant / 404
        // CORRIDOR_NOT_FOUND mapping once a typed error exists (see TC-REQ-004-4).
        let result = add_cross_section(
            &db.pool,
            CorridorId::from(999_999_i64),
            None,
            Coordinate::new(45.42, -75.69),
        )
        .await;

        assert!(result.is_err());
    }

    /// TC-REQ-004-5: an `insert_after_cross_section_id` that resolves to a real
    /// cross-section, but one belonging to a *different* corridor than the one being
    /// added to, cannot be used to determine a position and is rejected.
    #[tokio::test]
    async fn add_cross_section_with_anchor_from_different_corridor_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;

        let (corridor_a_id, _) = seed_corridor_with_four_cross_sections(&db.pool).await;
        let (_corridor_b_id, corridor_b_cross_section_ids) =
            seed_corridor_with_four_cross_sections(&db.pool).await;
        let foreign_anchor = corridor_b_cross_section_ids[0];

        // TODO(Loop B): assert specific PositionAssignmentError::UnresolvableInterval
        // / 400 POSITION_UNRESOLVABLE mapping once a typed error exists (see
        // TC-REQ-004-5).
        let result = add_cross_section(
            &db.pool,
            corridor_a_id,
            Some(foreign_anchor),
            Coordinate::new(45.42, -75.69),
        )
        .await;

        assert!(result.is_err());
    }

    /// TC-REQ-004-6: two concurrent adds targeting the same `insert_after` slot must
    /// not both succeed — one wins the insertion, the other loses to the unique
    /// `(corridor_id, position)` constraint and is rejected.
    #[tokio::test]
    async fn concurrent_add_targeting_same_slot_rejects_at_least_one() {
        let td = test_utils::setup().await;
        let db = td.db;

        let (corridor_id, cross_section_ids) =
            seed_corridor_with_four_cross_sections(&db.pool).await;
        let anchor = cross_section_ids[1]; // "cs_002"

        // TODO(Loop B): once real conflict detection exists, assert exactly one 201 /
        // one 409 POSITION_COLLISION, and that the DB ends up with exactly 5 rows with
        // all-distinct positions (see TC-REQ-004-6). This coarsely asserts at least
        // one side fails, per this pass's precedent for not-yet-typed errors.
        let (result_a, result_b) = tokio::join!(
            add_cross_section(
                &db.pool,
                corridor_id,
                Some(anchor),
                Coordinate::new(45.41, -75.685),
            ),
            add_cross_section(
                &db.pool,
                corridor_id,
                Some(anchor),
                Coordinate::new(45.415, -75.686),
            ),
        );

        assert!(
            result_a.is_err() || result_b.is_err(),
            "TC-REQ-004-6: at least one concurrent add targeting the same insertion slot should be rejected"
        );
    }

    // --- REQ-005: reorder cross-sections ---
    //
    // `reorder_cross_sections` is itself still a stub at this point in the
    // sequence, so the fixtures below seed `corridors`/`cross_sections` directly
    // via SQL, same pattern as REQ-004's fixtures above.

    /// Seeds a corridor (`geometry_source = 'manual'`) with 5 cross-sections at
    /// fractional positions `1.0..5.0` and `corridors.sequence_version` set to
    /// `initial_version` — matching TC-REQ-005-1/2/3's stated preconditions (5
    /// existing cross-sections, `sequence_version = 7`). Returns the corridor id
    /// and the 5 cross-section ids in position order (`[XS-1, XS-2, XS-3, XS-4,
    /// XS-5]`).
    async fn seed_corridor_with_five_cross_sections(
        pool: &sqlx::PgPool,
        initial_version: i64,
    ) -> (CorridorId, Vec<CrossSectionId>) {
        let corridor_id: i64 = sqlx::query_scalar(
            "INSERT INTO corridors (name, geometry_source, sequence_version) VALUES ($1, 'manual', $2) RETURNING id",
        )
        .bind("REQ-005 Test Corridor")
        .bind(initial_version)
        .fetch_one(pool)
        .await
        .unwrap();

        let mut cross_section_ids = Vec::with_capacity(5);
        for position in 1..=5i64 {
            let id: i64 = sqlx::query_scalar(
                "INSERT INTO cross_sections (corridor_id, position, lat, lon) VALUES ($1, $2, $3, $4) RETURNING id",
            )
            .bind(corridor_id)
            .bind(position as f64)
            .bind(45.500 + (position as f64) * 0.001)
            .bind(-73.600 + (position as f64) * 0.001)
            .fetch_one(pool)
            .await
            .unwrap();
            cross_section_ids.push(CrossSectionId::from(id));
        }

        (CorridorId::from(corridor_id), cross_section_ids)
    }

    /// TC-REQ-005-1: reordering a middle cross-section (XS-3) to the start —
    /// `[XS-3, XS-1, XS-2, XS-4, XS-5]` — succeeds, returns the new order and
    /// advanced `sequence_version`, and persists strictly increasing DB
    /// positions matching the requested order.
    #[tokio::test]
    async fn reorder_cross_sections_moves_middle_to_start() {
        let td = test_utils::setup().await;
        let db = td.db;
        let (corridor_id, cross_section_ids) =
            seed_corridor_with_five_cross_sections(&db.pool, 7).await;
        let requested_order = vec![
            cross_section_ids[2],
            cross_section_ids[0],
            cross_section_ids[1],
            cross_section_ids[3],
            cross_section_ids[4],
        ];

        let result = reorder_cross_sections(&db.pool, corridor_id, 7, &requested_order).await;

        let (new_version, cross_sections) =
            result.expect("reorder_cross_sections should succeed once implemented");
        assert_eq!(
            new_version, 8,
            "TC-REQ-005-1: sequence_version should advance from 7 to 8"
        );
        let returned_ids: Vec<CrossSectionId> = cross_sections.iter().map(|cs| cs.id).collect();
        assert_eq!(
            returned_ids, requested_order,
            "TC-REQ-005-1: returned cross-sections should be in the requested order"
        );

        let rows: Vec<FractionalPositionRow> = sqlx::query_as(
            "SELECT id, position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        )
        .bind(corridor_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();
        let row_ids: Vec<i64> = rows.iter().map(|r| r.id).collect();
        let expected_ids: Vec<i64> = requested_order.iter().map(|id| id.as_i64()).collect();
        assert_eq!(
            row_ids, expected_ids,
            "TC-REQ-005-1: DB positions should be strictly increasing in the requested order"
        );

        let db_version: i64 =
            sqlx::query_scalar("SELECT sequence_version FROM corridors WHERE id = $1")
                .bind(corridor_id.as_i64())
                .fetch_one(&db.pool)
                .await
                .unwrap();
        assert_eq!(
            db_version, 8,
            "TC-REQ-005-1: corridors.sequence_version should be persisted as 8"
        );
    }

    /// TC-REQ-005-2 (boundary): moving the last cross-section (XS-5) to the first
    /// position leaves it with the smallest `position` value in the corridor.
    #[tokio::test]
    async fn reorder_cross_sections_moves_last_to_first_boundary() {
        let td = test_utils::setup().await;
        let db = td.db;
        let (corridor_id, cross_section_ids) =
            seed_corridor_with_five_cross_sections(&db.pool, 7).await;
        let requested_order = vec![
            cross_section_ids[4],
            cross_section_ids[0],
            cross_section_ids[1],
            cross_section_ids[2],
            cross_section_ids[3],
        ];

        let result = reorder_cross_sections(&db.pool, corridor_id, 7, &requested_order).await;

        let (_new_version, cross_sections) =
            result.expect("reorder_cross_sections should succeed once implemented");
        let returned_ids: Vec<CrossSectionId> = cross_sections.iter().map(|cs| cs.id).collect();
        assert_eq!(
            returned_ids, requested_order,
            "TC-REQ-005-2: returned cross-sections should be in the requested order"
        );

        let rows: Vec<FractionalPositionRow> = sqlx::query_as(
            "SELECT id, position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        )
        .bind(corridor_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();
        assert_eq!(
            rows.first().unwrap().id,
            cross_section_ids[4].as_i64(),
            "TC-REQ-005-2: XS-5 should now have the smallest position value in the corridor"
        );
    }

    /// TC-REQ-005-3 (boundary): moving the first cross-section (XS-1) to the last
    /// position leaves it with the largest `position` value in the corridor.
    #[tokio::test]
    async fn reorder_cross_sections_moves_first_to_last_boundary() {
        let td = test_utils::setup().await;
        let db = td.db;
        let (corridor_id, cross_section_ids) =
            seed_corridor_with_five_cross_sections(&db.pool, 7).await;
        let requested_order = vec![
            cross_section_ids[1],
            cross_section_ids[2],
            cross_section_ids[3],
            cross_section_ids[4],
            cross_section_ids[0],
        ];

        let result = reorder_cross_sections(&db.pool, corridor_id, 7, &requested_order).await;

        let (_new_version, cross_sections) =
            result.expect("reorder_cross_sections should succeed once implemented");
        let returned_ids: Vec<CrossSectionId> = cross_sections.iter().map(|cs| cs.id).collect();
        assert_eq!(
            returned_ids, requested_order,
            "TC-REQ-005-3: returned cross-sections should be in the requested order"
        );

        let rows: Vec<FractionalPositionRow> = sqlx::query_as(
            "SELECT id, position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        )
        .bind(corridor_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();
        assert_eq!(
            rows.last().unwrap().id,
            cross_section_ids[0].as_i64(),
            "TC-REQ-005-3: XS-1 should now have the largest position value in the corridor"
        );
    }

    /// TC-REQ-005-4: a reorder request whose ID list includes a cross-section
    /// belonging to a *different* corridor is rejected rather than silently
    /// mixing sequences.
    #[tokio::test]
    async fn reorder_cross_sections_with_foreign_cross_section_id_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;

        let (corridor_a_id, corridor_a_ids) =
            seed_corridor_with_five_cross_sections(&db.pool, 7).await;
        let (_corridor_b_id, corridor_b_ids) =
            seed_corridor_with_five_cross_sections(&db.pool, 7).await;

        // Swap in a foreign ID from corridor B in place of one of corridor A's
        // own cross-sections (XS-5 omitted), per TC-REQ-005-4's precondition.
        let requested_order = vec![
            corridor_b_ids[0],
            corridor_a_ids[0],
            corridor_a_ids[1],
            corridor_a_ids[2],
            corridor_a_ids[3],
        ];

        // TODO(Loop B): assert specific ReorderError::Validation(UnknownCrossSection)
        // / 400 reorder.invalid_order mapping once implemented (see TC-REQ-005-4).
        let result = reorder_cross_sections(&db.pool, corridor_a_id, 7, &requested_order).await;

        assert!(
            result.is_err(),
            "TC-REQ-005-4: a foreign cross-section ID in the requested order should be rejected"
        );
    }

    /// TC-REQ-005-5: a stale `expected_version` (the corridor was already
    /// reordered elsewhere since the caller loaded it) is rejected rather than
    /// silently clobbering the concurrent change. Coarse `is_err()` assertion,
    /// matching this codebase's established precedent for not-yet-typed errors
    /// (see `add_cross_section_with_anchor_from_different_corridor_returns_err`
    /// above).
    #[tokio::test]
    async fn reorder_cross_sections_with_stale_expected_version_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;

        let (corridor_id, cross_section_ids) =
            seed_corridor_with_five_cross_sections(&db.pool, 7).await;
        let requested_order = vec![
            cross_section_ids[4],
            cross_section_ids[0],
            cross_section_ids[1],
            cross_section_ids[2],
            cross_section_ids[3],
        ];

        // TODO(Loop B): assert specific ReorderError::VersionConflict { expected:
        // 6, actual: 7 } / 409 reorder.version_conflict mapping once implemented
        // (see TC-REQ-005-5).
        let result = reorder_cross_sections(&db.pool, corridor_id, 6, &requested_order).await;

        assert!(
            result.is_err(),
            "TC-REQ-005-5: a stale expected_version should be rejected"
        );
    }

    /// TC-REQ-005-6: an analyst without editor access to the corridor's owning
    /// agency cannot reorder it. There is no auth/authorization layer anywhere in
    /// this codebase yet (confirmed absent project-wide), so this cannot be
    /// meaningfully tested until one exists — tracked as a cross-cutting project
    /// blocker (see Implementation Plan Open Risks).
    #[ignore = "IMP-REQ-005-06/07: no auth/authorization layer exists anywhere in this codebase yet — see Corridor Design BRD, no auth constraint"]
    #[tokio::test]
    async fn reorder_cross_sections_by_unauthorized_user_returns_403() {
        todo!()
    }

    // --- REQ-006: edit a single cross-section's label ---
    //
    // `update_cross_section_label` is itself still a stub at this point in the
    // sequence, so the fixtures below seed `corridors`/`cross_sections` directly
    // via SQL, same pattern as REQ-004/005's fixtures above. Migration 024 has
    // already added `cross_sections.version`/`label` by the time these tests run.

    #[derive(Debug, PartialEq, sqlx::FromRow)]
    struct FullCrossSectionRow {
        id: i64,
        corridor_id: i64,
        position: f64,
        lat: f64,
        lon: f64,
        osm_way_id: Option<i64>,
        osm_node_id: Option<i64>,
        label: Option<String>,
        version: i32,
    }

    async fn fetch_full_cross_section_row(
        pool: &sqlx::PgPool,
        id: CrossSectionId,
    ) -> FullCrossSectionRow {
        // `position` is stored as NUMERIC (see migration 022); it's cast to
        // FLOAT8 here so it decodes into this fixture's `f64` field without
        // requiring sqlx's "bigdecimal"/"rust_decimal" feature (not enabled in
        // this crate's Cargo.toml — see `position.rs`'s top-of-file note on the
        // same constraint).
        sqlx::query_as(
            "SELECT id, corridor_id, position::float8 AS position, lat, lon, osm_way_id, \
             osm_node_id, label, version FROM cross_sections WHERE id = $1",
        )
        .bind(id.as_i64())
        .fetch_one(pool)
        .await
        .unwrap()
    }

    /// Seeds a corridor (`geometry_source = 'manual'`) with 3 cross-sections A, B,
    /// C at fractional positions `1.0, 2.0, 3.0`, each with a distinct label and
    /// `version = 1` (the column default) — matching TC-REQ-006-1/2's stated
    /// preconditions. Returns the corridor id and the 3 cross-section ids in
    /// position order (`[A, B, C]`).
    async fn seed_corridor_with_three_labeled_cross_sections(
        pool: &sqlx::PgPool,
    ) -> (CorridorId, Vec<CrossSectionId>) {
        let corridor_id: i64 = sqlx::query_scalar(
            "INSERT INTO corridors (name, geometry_source) VALUES ($1, 'manual') RETURNING id",
        )
        .bind("REQ-006 Test Corridor")
        .fetch_one(pool)
        .await
        .unwrap();

        let labels = [
            "Main St @ 5th Ave",
            "Main St @ 6th Ave",
            "Main St @ 7th Ave",
        ];
        let mut cross_section_ids = Vec::with_capacity(3);
        for (i, label) in labels.iter().enumerate() {
            let position = (i + 1) as f64;
            let id: i64 = sqlx::query_scalar(
                "INSERT INTO cross_sections (corridor_id, position, lat, lon, label) \
                 VALUES ($1, $2, $3, $4, $5) RETURNING id",
            )
            .bind(corridor_id)
            .bind(position)
            .bind(45.500 + position * 0.001)
            .bind(-73.600 + position * 0.001)
            .bind(*label)
            .fetch_one(pool)
            .await
            .unwrap();
            cross_section_ids.push(CrossSectionId::from(id));
        }

        (CorridorId::from(corridor_id), cross_section_ids)
    }

    /// TC-REQ-006-1: editing cross-section B's label persists the new label and
    /// advances `version` from 1 to 2.
    #[tokio::test]
    async fn update_cross_section_label_edits_and_saves_successfully() {
        let td = test_utils::setup().await;
        let db = td.db;
        let (corridor_id, cross_section_ids) =
            seed_corridor_with_three_labeled_cross_sections(&db.pool).await;
        let b_id = cross_section_ids[1];

        let result = update_cross_section_label(
            &db.pool,
            corridor_id,
            b_id,
            Some("Main St @ 6th Ave (widened)".to_string()),
            1,
        )
        .await;

        let updated = result.expect("update_cross_section_label should succeed once implemented");
        assert_eq!(updated.id, b_id);
        assert_eq!(
            updated.label.as_deref(),
            Some("Main St @ 6th Ave (widened)")
        );

        let row = fetch_full_cross_section_row(&db.pool, b_id).await;
        assert_eq!(row.label.as_deref(), Some("Main St @ 6th Ave (widened)"));
        assert_eq!(
            row.version, 2,
            "TC-REQ-006-1: version should advance from 1 to 2 on a successful edit"
        );
    }

    /// TC-REQ-006-2 (isolation guarantee, explicit acceptance-criterion check):
    /// editing B leaves every column of A and C byte-identical before and after.
    #[tokio::test]
    async fn update_cross_section_label_does_not_alter_siblings() {
        let td = test_utils::setup().await;
        let db = td.db;
        let (corridor_id, cross_section_ids) =
            seed_corridor_with_three_labeled_cross_sections(&db.pool).await;
        let (a_id, b_id, c_id) = (
            cross_section_ids[0],
            cross_section_ids[1],
            cross_section_ids[2],
        );

        let before_a = fetch_full_cross_section_row(&db.pool, a_id).await;
        let before_c = fetch_full_cross_section_row(&db.pool, c_id).await;

        update_cross_section_label(
            &db.pool,
            corridor_id,
            b_id,
            Some("Main St @ 6th Ave (widened)".to_string()),
            1,
        )
        .await
        .expect("update_cross_section_label should succeed once implemented");

        let after_a = fetch_full_cross_section_row(&db.pool, a_id).await;
        let after_c = fetch_full_cross_section_row(&db.pool, c_id).await;

        assert_eq!(
            before_a, after_a,
            "TC-REQ-006-2: every column of sibling A must be byte-identical before/after"
        );
        assert_eq!(
            before_c, after_c,
            "TC-REQ-006-2: every column of sibling C must be byte-identical before/after"
        );
    }

    /// TC-REQ-006-5: a second edit submitted with a stale `expected_version` is
    /// rejected rather than silently clobbering the first edit. Coarse `is_err()`
    /// assertion, matching this codebase's established precedent for not-yet-typed
    /// errors (see `reorder_cross_sections_with_stale_expected_version_returns_err`
    /// above).
    #[tokio::test]
    async fn update_cross_section_label_with_stale_version_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;
        let (corridor_id, cross_section_ids) =
            seed_corridor_with_three_labeled_cross_sections(&db.pool).await;
        let b_id = cross_section_ids[1];

        update_cross_section_label(
            &db.pool,
            corridor_id,
            b_id,
            Some("First edit".to_string()),
            1,
        )
        .await
        .expect("first update_cross_section_label should succeed once implemented");

        // TODO(Loop B): assert specific EDIT_CONFLICT / 409 mapping once a typed
        // error exists (see TC-REQ-006-5).
        let result = update_cross_section_label(
            &db.pool,
            corridor_id,
            b_id,
            Some("Second edit, stale version".to_string()),
            1,
        )
        .await;

        assert!(
            result.is_err(),
            "TC-REQ-006-5: a stale expected_version should be rejected"
        );
    }

    /// TC-REQ-006-6: saving an edit for a cross-section deleted since the edit
    /// view loaded fails rather than silently succeeding. No delete function
    /// exists yet in this module, so the deletion is simulated with a raw
    /// `DELETE` query directly in test setup, matching this pass's precedent for
    /// not-yet-buildable setup steps.
    #[tokio::test]
    async fn update_cross_section_label_for_deleted_cross_section_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;
        let (corridor_id, cross_section_ids) =
            seed_corridor_with_three_labeled_cross_sections(&db.pool).await;
        let b_id = cross_section_ids[1];

        sqlx::query("DELETE FROM cross_sections WHERE id = $1")
            .bind(b_id.as_i64())
            .execute(&db.pool)
            .await
            .unwrap();

        // TODO(Loop B): assert specific CROSS_SECTION_NOT_FOUND / 404 mapping once
        // a typed error exists (see TC-REQ-006-6).
        let result = update_cross_section_label(
            &db.pool,
            corridor_id,
            b_id,
            Some("too late".to_string()),
            1,
        )
        .await;

        assert!(
            result.is_err(),
            "TC-REQ-006-6: editing a deleted cross-section should be rejected"
        );
    }

    // --- Lane persistence ---

    fn sample_lane_drafts() -> Vec<LaneDraft> {
        vec![
            LaneDraft {
                lane_type: LaneType::Sidewalk,
                width_meters: 1.8,
                direction: LaneDirection::None,
                access_rules: vec![crate::corridor_design::lanes::default_access_rule_for(
                    LaneType::Sidewalk,
                )],
            },
            LaneDraft {
                lane_type: LaneType::Travel,
                width_meters: 3.0,
                direction: LaneDirection::Forward,
                access_rules: vec![crate::corridor_design::lanes::default_access_rule_for(
                    LaneType::Travel,
                )],
            },
        ]
    }

    async fn seed_bare_cross_section(pool: &sqlx::PgPool, remix_id: RemixId) -> CrossSectionId {
        let corridor_id = start_manual_corridor(pool, remix_id, "Lane Test Corridor")
            .await
            .unwrap();
        insert_cross_section(pool, corridor_id, Coordinate::new(45.50, -73.60), 0)
            .await
            .unwrap()
    }

    #[tokio::test]
    async fn insert_lanes_for_cross_section_persists_in_order() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;

        let lane_ids =
            insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
                .await
                .expect("insert_lanes_for_cross_section should succeed");

        assert_eq!(lane_ids.len(), 2);

        let lanes = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .expect("get_lanes_for_cross_section should succeed");

        assert_eq!(lanes.len(), 2);
        assert_eq!(lanes[0].lane_type, LaneType::Sidewalk);
        assert_eq!(lanes[0].width_meters, 1.8);
        assert_eq!(lanes[0].direction, LaneDirection::None);
        assert_eq!(lanes[1].lane_type, LaneType::Travel);
        assert_eq!(lanes[1].width_meters, 3.0);
        assert_eq!(lanes[1].direction, LaneDirection::Forward);
        assert!(lanes[0].position < lanes[1].position);
    }

    #[tokio::test]
    async fn insert_lanes_for_cross_section_persists_default_access_rules() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;

        insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
            .await
            .unwrap();

        let lanes = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .unwrap();

        let travel_lane = lanes
            .iter()
            .find(|l| l.lane_type == LaneType::Travel)
            .unwrap();
        assert_eq!(travel_lane.access_rules.len(), 1);
        assert_eq!(travel_lane.access_rules[0].time_window, None);
        assert_eq!(
            travel_lane.access_rules[0].allowed_modes,
            vec![
                crate::corridor_design::lanes::AccessMode::Car,
                crate::corridor_design::lanes::AccessMode::Emergency
            ]
        );
    }

    #[tokio::test]
    async fn insert_lanes_for_cross_section_persists_time_windowed_rule() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;

        let bat_lane = LaneDraft {
            lane_type: LaneType::Transit,
            width_meters: 3.2,
            direction: LaneDirection::Forward,
            access_rules: vec![TimedAccessRule {
                time_window: Some(crate::corridor_design::lanes::TimeWindow {
                    days: "weekdays".to_string(),
                    start_time: chrono::NaiveTime::from_hms_opt(7, 0, 0).unwrap(),
                    end_time: chrono::NaiveTime::from_hms_opt(9, 0, 0).unwrap(),
                }),
                allowed_modes: vec![
                    crate::corridor_design::lanes::AccessMode::Transit,
                    crate::corridor_design::lanes::AccessMode::Car,
                ],
            }],
        };

        insert_lanes_for_cross_section(&db.pool, cross_section_id, &[bat_lane])
            .await
            .unwrap();

        let lanes = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .unwrap();

        assert_eq!(lanes.len(), 1);
        assert_eq!(lanes[0].access_rules.len(), 1);
        let window = lanes[0].access_rules[0]
            .time_window
            .as_ref()
            .expect("time window should round-trip");
        assert_eq!(window.days, "weekdays");
        assert_eq!(
            window.start_time,
            chrono::NaiveTime::from_hms_opt(7, 0, 0).unwrap()
        );
        assert_eq!(
            window.end_time,
            chrono::NaiveTime::from_hms_opt(9, 0, 0).unwrap()
        );
    }

    #[tokio::test]
    async fn get_lanes_for_cross_section_returns_empty_for_a_cross_section_with_no_lanes() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;

        let lanes = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .unwrap();

        assert_eq!(lanes.len(), 0);
    }
}

//! Corridor Design repository: the imperative I/O shell for corridors and
//! cross-sections. Pure normalization logic lives in `geometry.rs`; this module
//! persists an already-normalized corridor and reads it back — no validation or
//! geometry computation happens here.

use crate::corridor_design::Coordinate;
use crate::corridor_design::CrossSection;
use crate::corridor_design::geometry::NormalizedCorridor;
use crate::corridor_design::intersection::{BusGate, BusStop, Intersection, TurnConflict};
use crate::corridor_design::lanes::{
    AccessMode, Lane, LaneDirection, LaneDraft, LaneType, TimeWindow, TimedAccessRule,
};
use crate::ids::{CorridorId, CrossSectionId, IntersectionId, LaneId, RemixId};

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
                  osm_way_id, osm_node_id, label, version, intersection_id
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
            version: row.version,
            intersection_id: row.intersection_id.map(crate::ids::IntersectionId::from),
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

/// Inserts a new lane into `cross_section_id` at `position` (already computed by
/// the caller via `position::assign_position` — this function does no position
/// arithmetic of its own, keeping that pure logic in the Functional Core). Defaults
/// the new lane's access rule via `lanes::default_access_rule_for(lane_type)`, so
/// it's never silently inaccessible to every mode.
pub async fn insert_lane(
    pool: &sqlx::PgPool,
    cross_section_id: CrossSectionId,
    lane_type: LaneType,
    width_meters: f64,
    direction: LaneDirection,
    position: f64,
) -> Result<Lane, anyhow::Error> {
    let lane_type_str = lane_type.as_db_str();
    let direction_str = direction.as_db_str();

    let mut tx = pool.begin().await?;

    let lane_id = sqlx::query!(
        "INSERT INTO lanes (cross_section_id, position, lane_type, width_meters, direction) \
         VALUES ($1, $2::float8, $3, $4, $5) RETURNING id",
        cross_section_id.as_i64(),
        position,
        lane_type_str,
        width_meters,
        direction_str,
    )
    .fetch_one(&mut *tx)
    .await?
    .id;

    let default_rule = crate::corridor_design::lanes::default_access_rule_for(lane_type);
    let (days, start_time, end_time) = time_window_columns(&default_rule);
    let allowed_modes: Vec<&str> = default_rule
        .allowed_modes
        .iter()
        .map(|m| m.as_db_str())
        .collect();
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

    tx.commit().await?;

    Ok(Lane {
        id: LaneId::from(lane_id),
        cross_section_id,
        position,
        lane_type,
        width_meters,
        direction,
        access_rules: vec![default_rule],
    })
}

struct LaneUpdateRow {
    id: i64,
    cross_section_id: i64,
    position: f64,
}

/// Updates an existing lane's own attributes (type, width, direction). Position
/// and access rules are untouched -- reordering isn't exposed through this
/// function, and access rules have their own dedicated `set_lane_access_rules`.
pub async fn update_lane(
    pool: &sqlx::PgPool,
    lane_id: LaneId,
    lane_type: LaneType,
    width_meters: f64,
    direction: LaneDirection,
) -> Result<Lane, anyhow::Error> {
    let lane_type_str = lane_type.as_db_str();
    let direction_str = direction.as_db_str();

    let row: Option<LaneUpdateRow> = sqlx::query_as!(
        LaneUpdateRow,
        r#"UPDATE lanes SET lane_type = $1, width_meters = $2, direction = $3
           WHERE id = $4
           RETURNING id, cross_section_id, position::float8 AS "position!""#,
        lane_type_str,
        width_meters,
        direction_str,
        lane_id.as_i64(),
    )
    .fetch_optional(pool)
    .await?;

    let row = row.ok_or_else(|| anyhow::anyhow!("lane {lane_id} not found"))?;
    let access_rules = fetch_access_rules_for_lane(pool, row.id).await?;

    Ok(Lane {
        id: LaneId::from(row.id),
        cross_section_id: CrossSectionId::from(row.cross_section_id),
        position: row.position,
        lane_type,
        width_meters,
        direction,
        access_rules,
    })
}

/// Deletes a lane. Cascades to its access rules via `lane_access_rules`' existing
/// foreign key (`ON DELETE CASCADE`, migration 026).
pub async fn delete_lane(pool: &sqlx::PgPool, lane_id: LaneId) -> Result<(), anyhow::Error> {
    let result = sqlx::query!("DELETE FROM lanes WHERE id = $1", lane_id.as_i64())
        .execute(pool)
        .await?;
    if result.rows_affected() == 0 {
        anyhow::bail!("lane {lane_id} not found");
    }
    Ok(())
}

/// Replaces a lane's whole access-rule list (delete-then-reinsert, mirroring
/// `insert_lanes_for_cross_section`'s existing shape) rather than exposing
/// individual rule IDs for fine-grained CRUD -- access rules have no `id` in the
/// domain model, and lists are small (1-2 rules is the common case).
pub async fn set_lane_access_rules(
    pool: &sqlx::PgPool,
    lane_id: LaneId,
    rules: &[TimedAccessRule],
) -> Result<(), anyhow::Error> {
    let mut tx = pool.begin().await?;

    sqlx::query!(
        "DELETE FROM lane_access_rules WHERE lane_id = $1",
        lane_id.as_i64(),
    )
    .execute(&mut *tx)
    .await?;

    for rule in rules {
        let (days, start_time, end_time) = time_window_columns(rule);
        let allowed_modes: Vec<&str> = rule.allowed_modes.iter().map(|m| m.as_db_str()).collect();
        sqlx::query!(
            "INSERT INTO lane_access_rules (lane_id, days, start_time, end_time, allowed_modes) \
             VALUES ($1, $2, $3, $4, $5)",
            lane_id.as_i64(),
            days,
            start_time,
            end_time,
            &allowed_modes as &[&str],
        )
        .execute(&mut *tx)
        .await?;
    }

    tx.commit().await?;
    Ok(())
}

/// Fetches every access rule for one lane. Shares its per-row decode logic with
/// `get_lanes_for_cross_section`'s batch (`= ANY($1)`) query, scoped here to a
/// single `lane_id` (`update_lane` needs just one lane's rules, not a batch).
async fn fetch_access_rules_for_lane(
    pool: &sqlx::PgPool,
    lane_id: i64,
) -> Result<Vec<TimedAccessRule>, anyhow::Error> {
    let rows: Vec<AccessRuleRow> = sqlx::query_as!(
        AccessRuleRow,
        r#"SELECT lane_id, days, start_time, end_time, allowed_modes AS "allowed_modes!"
           FROM lane_access_rules
           WHERE lane_id = $1"#,
        lane_id,
    )
    .fetch_all(pool)
    .await?;

    let mut access_rules = Vec::with_capacity(rows.len());
    for rule_row in rows {
        let mut allowed_modes = Vec::with_capacity(rule_row.allowed_modes.len());
        for mode_str in &rule_row.allowed_modes {
            let mode = AccessMode::from_db_str(mode_str)
                .ok_or_else(|| anyhow::anyhow!("unknown access mode value: {mode_str}"))?;
            allowed_modes.push(mode);
        }
        let time_window = match (&rule_row.days, rule_row.start_time, rule_row.end_time) {
            (Some(days), Some(start_time), Some(end_time)) => Some(TimeWindow {
                days: days.clone(),
                start_time,
                end_time,
            }),
            _ => None,
        };
        access_rules.push(TimedAccessRule {
            time_window,
            allowed_modes,
        });
    }
    Ok(access_rules)
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
    // The neighbor read and the position INSERT must happen inside one
    // SERIALIZABLE transaction, not as separate autocommit statements on the
    // pool: two concurrent calls targeting the same insertion slot both need
    // to observe the *same* pre-insert neighbor state and compute the same
    // `new_position`, so that whichever commits second is guaranteed to
    // conflict rather than silently reading the other's already-committed
    // row and computing a different, non-colliding position. Under plain
    // READ COMMITTED that guarantee only holds if the two calls happen to
    // overlap in time; SERIALIZABLE's predicate locking (SSI) makes Postgres
    // detect the write skew and abort one transaction even when the actual
    // wall-clock overlap is small. See TC-REQ-004-6.
    let mut tx = pool.begin().await?;
    sqlx::query("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE")
        .execute(&mut *tx)
        .await?;

    let corridor_exists = sqlx::query_scalar!(
        r#"SELECT EXISTS(SELECT 1 FROM corridors WHERE id = $1) AS "exists!""#,
        corridor_id.as_i64(),
    )
    .fetch_one(&mut *tx)
    .await?;
    if !corridor_exists {
        anyhow::bail!("corridor {corridor_id} does not exist");
    }

    let (before, after) = match insert_after {
        Some(anchor_id) => {
            let anchor = sqlx::query!(
                r#"SELECT corridor_id, position::float8 AS "position!" FROM cross_sections WHERE id = $1"#,
                anchor_id.as_i64(),
            )
            .fetch_optional(&mut *tx)
            .await?;
            let Some(anchor) = anchor else {
                anyhow::bail!("insert_after cross-section {anchor_id} does not exist");
            };
            if anchor.corridor_id != corridor_id.as_i64() {
                anyhow::bail!(
                    "insert_after cross-section {anchor_id} does not belong to corridor {corridor_id}"
                );
            }
            let after = sqlx::query_scalar!(
                r#"SELECT position::float8 AS "position!" FROM cross_sections
                   WHERE corridor_id = $1 AND position > $2::float8
                   ORDER BY position ASC LIMIT 1"#,
                corridor_id.as_i64(),
                anchor.position,
            )
            .fetch_optional(&mut *tx)
            .await?;
            (Some(anchor.position), after)
        }
        None => {
            let after = sqlx::query_scalar!(
                r#"SELECT position::float8 AS "position!" FROM cross_sections
                   WHERE corridor_id = $1 ORDER BY position ASC LIMIT 1"#,
                corridor_id.as_i64(),
            )
            .fetch_optional(&mut *tx)
            .await?;
            (None, after)
        }
    };

    let new_position = crate::corridor_design::position::assign_position(
        crate::corridor_design::position::Neighbors { before, after },
    )
    .map_err(|e| anyhow::anyhow!("{e}"))?;

    let id = sqlx::query_scalar!(
        "INSERT INTO cross_sections (corridor_id, position, lat, lon) \
         VALUES ($1, $2::float8, $3, $4) RETURNING id",
        corridor_id.as_i64(),
        new_position,
        coordinate.lat,
        coordinate.lon,
    )
    .fetch_one(&mut *tx)
    .await?;

    tx.commit().await?;

    Ok(CrossSection {
        id: CrossSectionId::from(id),
        corridor_id,
        position: new_position,
        lat: coordinate.lat,
        lon: coordinate.lon,
        osm_way_id: None,
        osm_node_id: None,
        label: None,
        version: 1,
        intersection_id: None,
    })
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
    let current_order: Vec<CrossSectionId> = sqlx::query_scalar!(
        "SELECT id FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
        corridor_id.as_i64(),
    )
    .fetch_all(pool)
    .await?
    .into_iter()
    .map(CrossSectionId::from)
    .collect();

    let new_positions = crate::corridor_design::position::compute_reordered_positions(
        &current_order,
        requested_order,
    )
    .map_err(|e| anyhow::anyhow!("{e}"))?;

    let mut tx = pool.begin().await?;

    let current_version = sqlx::query_scalar!(
        "SELECT sequence_version FROM corridors WHERE id = $1 FOR UPDATE",
        corridor_id.as_i64(),
    )
    .fetch_optional(&mut *tx)
    .await?;
    let Some(current_version) = current_version else {
        anyhow::bail!("corridor {corridor_id} does not exist");
    };
    if current_version != expected_version {
        anyhow::bail!(
            "stale expected_version: expected {expected_version}, corridor is at {current_version}"
        );
    }

    for (id, position) in &new_positions {
        sqlx::query!(
            "UPDATE cross_sections SET position = $1::float8 WHERE id = $2 AND corridor_id = $3",
            position,
            id.as_i64(),
            corridor_id.as_i64(),
        )
        .execute(&mut *tx)
        .await?;
    }

    let new_version = current_version + 1;
    sqlx::query!(
        "UPDATE corridors SET sequence_version = $1 WHERE id = $2",
        new_version,
        corridor_id.as_i64(),
    )
    .execute(&mut *tx)
    .await?;

    tx.commit().await?;

    let cross_sections = get_corridor_cross_sections(pool, corridor_id).await?;
    Ok((new_version, cross_sections))
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
pub async fn update_cross_section_label(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
    cross_section_id: CrossSectionId,
    new_label: Option<String>,
    expected_version: i32,
) -> Result<CrossSection, anyhow::Error> {
    let row = sqlx::query!(
        r#"UPDATE cross_sections
           SET label = $1, version = version + 1
           WHERE id = $2 AND corridor_id = $3 AND version = $4
           RETURNING id, corridor_id, position::float8 AS "position!", lat, lon,
                     osm_way_id, osm_node_id, label, version, intersection_id"#,
        new_label,
        cross_section_id.as_i64(),
        corridor_id.as_i64(),
        expected_version,
    )
    .fetch_optional(pool)
    .await?;

    // A single query covers all three failure modes at once (doesn't exist,
    // belongs to a different corridor, or a concurrent edit already advanced
    // `version`) -- matching this file's established "coarse is_err()"
    // precedent for not-yet-typed errors (see the REQ-004/005 stubs' own test
    // comments).
    let row = row.ok_or_else(|| {
        anyhow::anyhow!(
            "cross-section {cross_section_id} not found, not part of corridor {corridor_id}, or version conflict"
        )
    })?;

    Ok(CrossSection {
        id: CrossSectionId::from(row.id),
        corridor_id: CorridorId::from(row.corridor_id),
        position: row.position,
        lat: row.lat,
        lon: row.lon,
        osm_way_id: row.osm_way_id,
        osm_node_id: row.osm_node_id,
        label: row.label,
        version: row.version,
        intersection_id: row.intersection_id.map(crate::ids::IntersectionId::from),
    })
}

/// Looks up an existing `Intersection` by `osm_node_id` (if present); creates
/// a new one otherwise. `osm_node_id = None` (manual/private endpoints)
/// always creates a new row -- there is nothing to match on.
pub async fn create_or_match_intersection(
    pool: &sqlx::PgPool,
    lat: f64,
    lon: f64,
    osm_node_id: Option<i64>,
) -> Result<IntersectionId, anyhow::Error> {
    if let Some(node_id) = osm_node_id {
        let matched = sqlx::query_scalar!(
            "SELECT intersection_id FROM intersection_osm_nodes WHERE osm_node_id = $1",
            node_id,
        )
        .fetch_optional(pool)
        .await?;
        if let Some(intersection_id) = matched {
            return Ok(IntersectionId::from(intersection_id));
        }
    }

    let mut tx = pool.begin().await?;
    let id = sqlx::query_scalar!(
        "INSERT INTO intersections (lat, lon) VALUES ($1, $2) RETURNING id",
        lat,
        lon,
    )
    .fetch_one(&mut *tx)
    .await?;
    if let Some(node_id) = osm_node_id {
        // Atomic upsert against `intersection_osm_nodes.osm_node_id UNIQUE`
        // (migration 028): the initial SELECT above is only a fast-path
        // optimization, not a guarantee -- two concurrent callers can both
        // miss it and race to here. `ON CONFLICT ... DO NOTHING RETURNING`
        // makes the actual claim atomic: at most one concurrent INSERT wins
        // the row and gets a `Some` back; the loser gets `None` instead of a
        // raw unique-violation propagating out of this function.
        let inserted = sqlx::query_scalar!(
            r#"INSERT INTO intersection_osm_nodes (intersection_id, osm_node_id)
               VALUES ($1, $2)
               ON CONFLICT (osm_node_id) DO NOTHING
               RETURNING intersection_id"#,
            id,
            node_id,
        )
        .fetch_optional(&mut *tx)
        .await?;

        if inserted.is_none() {
            // Lost the race: some other concurrent call already claimed
            // `node_id` first. Roll back this transaction -- discarding the
            // `intersections` row just inserted above, which would otherwise
            // be an orphan -- and match onto the winner's row instead.
            tx.rollback().await?;
            let winner = sqlx::query_scalar!(
                "SELECT intersection_id FROM intersection_osm_nodes WHERE osm_node_id = $1",
                node_id,
            )
            .fetch_one(pool)
            .await?;
            return Ok(IntersectionId::from(winner));
        }
    }
    tx.commit().await?;

    Ok(IntersectionId::from(id))
}

/// Fetches a single `Intersection`, including its full `osm_node_ids` list.
pub async fn get_intersection(
    pool: &sqlx::PgPool,
    intersection_id: IntersectionId,
) -> Result<Intersection, anyhow::Error> {
    let row = sqlx::query!(
        "SELECT id, lat, lon, bus_gate, turn_conflict, bus_stop FROM intersections WHERE id = $1",
        intersection_id.as_i64(),
    )
    .fetch_optional(pool)
    .await?;
    let row = row.ok_or_else(|| anyhow::anyhow!("intersection {intersection_id} not found"))?;

    let osm_node_ids: Vec<i64> = sqlx::query_scalar!(
        "SELECT osm_node_id FROM intersection_osm_nodes WHERE intersection_id = $1",
        intersection_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;

    Ok(Intersection {
        id: IntersectionId::from(row.id),
        lat: row.lat,
        lon: row.lon,
        osm_node_ids,
        bus_gate: row
            .bus_gate
            .map(|s| {
                BusGate::from_db_str(&s)
                    .ok_or_else(|| anyhow::anyhow!("unknown bus_gate value: {s}"))
            })
            .transpose()?,
        turn_conflict: row
            .turn_conflict
            .map(|s| {
                TurnConflict::from_db_str(&s)
                    .ok_or_else(|| anyhow::anyhow!("unknown turn_conflict value: {s}"))
            })
            .transpose()?,
        bus_stop: row
            .bus_stop
            .map(|s| {
                BusStop::from_db_str(&s)
                    .ok_or_else(|| anyhow::anyhow!("unknown bus_stop value: {s}"))
            })
            .transpose()?,
    })
}

/// Whole-record replace of an intersection's treatment fields, matching this
/// file's established `set_lane_access_rules` precedent -- `None` clears a
/// field, it does not mean "leave unchanged".
pub async fn set_intersection_treatment(
    pool: &sqlx::PgPool,
    intersection_id: IntersectionId,
    bus_gate: Option<BusGate>,
    turn_conflict: Option<TurnConflict>,
    bus_stop: Option<BusStop>,
) -> Result<Intersection, anyhow::Error> {
    let updated = sqlx::query_scalar!(
        r#"UPDATE intersections SET bus_gate = $1, turn_conflict = $2, bus_stop = $3
           WHERE id = $4 RETURNING id"#,
        bus_gate.map(|g| g.as_db_str()),
        turn_conflict.map(|c| c.as_db_str()),
        bus_stop.map(|b| b.as_db_str()),
        intersection_id.as_i64(),
    )
    .fetch_optional(pool)
    .await?;
    updated.ok_or_else(|| anyhow::anyhow!("intersection {intersection_id} not found"))?;

    get_intersection(pool, intersection_id).await
}

/// Links a cross-section to the intersection it's an endpoint of. Does not
/// validate that `cross_section_id` is actually a corridor endpoint (by
/// position) -- callers (Task 8's `resolve_corridor_endpoints`) are
/// responsible for only calling this on a first/last cross-section, per this
/// design's Open Points decision to enforce that invariant in application
/// code rather than a DB constraint.
pub async fn set_cross_section_intersection(
    pool: &sqlx::PgPool,
    cross_section_id: CrossSectionId,
    intersection_id: IntersectionId,
) -> Result<(), anyhow::Error> {
    let result = sqlx::query!(
        "UPDATE cross_sections SET intersection_id = $1 WHERE id = $2",
        intersection_id.as_i64(),
        cross_section_id.as_i64(),
    )
    .execute(pool)
    .await?;
    if result.rows_affected() == 0 {
        anyhow::bail!("cross-section {cross_section_id} not found");
    }
    Ok(())
}

/// Every distinct corridor with at least one cross-section referencing
/// `intersection_id`.
pub async fn corridors_at_intersection(
    pool: &sqlx::PgPool,
    intersection_id: IntersectionId,
) -> Result<Vec<CorridorId>, anyhow::Error> {
    let ids: Vec<i64> = sqlx::query_scalar!(
        "SELECT DISTINCT corridor_id FROM cross_sections WHERE intersection_id = $1",
        intersection_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;
    Ok(ids.into_iter().map(CorridorId::from).collect())
}

/// Splits `corridor_id` at `cross_section_id` into two corridors meeting at a
/// new shared `Intersection`. `expected_sequence_version` is an
/// optimistic-concurrency check against `corridors.sequence_version`
/// (migration 023) -- reused here rather than adding a new column, since a
/// split is exactly the kind of cross-section-arrangement change that column
/// already exists to guard. Returns `(head_corridor_id, tail_corridor_id,
/// new_intersection_id)`.
pub async fn split_corridor_at_cross_section(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
    cross_section_id: CrossSectionId,
    expected_sequence_version: i64,
) -> Result<(CorridorId, CorridorId, IntersectionId), anyhow::Error> {
    let cross_sections = get_corridor_cross_sections(pool, corridor_id).await?;
    let partition = crate::corridor_design::splitting::partition_at_split_point(
        &cross_sections,
        cross_section_id,
    )
    .map_err(|e| anyhow::anyhow!("{e}"))?;

    let mut tx = pool.begin().await?;

    // `FOR UPDATE` locks the corridor row for the duration of the
    // transaction, serializing concurrent splits/reorders of the same
    // corridor so the sequence-version check below can't race.
    let current_version = sqlx::query_scalar!(
        "SELECT sequence_version FROM corridors WHERE id = $1 FOR UPDATE",
        corridor_id.as_i64(),
    )
    .fetch_optional(&mut *tx)
    .await?;
    let Some(current_version) = current_version else {
        anyhow::bail!("corridor {corridor_id} does not exist");
    };
    if current_version != expected_sequence_version {
        anyhow::bail!(
            "stale expected_sequence_version: expected {expected_sequence_version}, corridor is at {current_version}"
        );
    }

    // The new Intersection is never itself a dual-carriageway merge
    // candidate (Task 6) -- it wasn't derived from a oneway-tagged way pair,
    // it's a split point. Mirrors `create_or_match_intersection`'s
    // TOCTOU-safe `ON CONFLICT ... DO NOTHING RETURNING` claim on
    // `intersection_osm_nodes.osm_node_id UNIQUE` in case the split point's
    // `osm_node_id` (when present) is already linked to another Intersection
    // -- e.g. this corridor's endpoint coincides with an already-imported
    // node elsewhere.
    let new_intersection_id: i64 = {
        let inserted = sqlx::query_scalar!(
            "INSERT INTO intersections (lat, lon) VALUES ($1, $2) RETURNING id",
            partition.new_intersection_lat,
            partition.new_intersection_lon,
        )
        .fetch_one(&mut *tx)
        .await?;

        if let Some(node_id) = partition.new_intersection_osm_node_id {
            let claimed = sqlx::query_scalar!(
                r#"INSERT INTO intersection_osm_nodes (intersection_id, osm_node_id)
                   VALUES ($1, $2)
                   ON CONFLICT (osm_node_id) DO NOTHING
                   RETURNING intersection_id"#,
                inserted,
                node_id,
            )
            .fetch_optional(&mut *tx)
            .await?;

            match claimed {
                Some(_) => inserted,
                None => {
                    // Lost the race: `node_id` is already linked to another
                    // Intersection. Discard the just-inserted orphan row and
                    // match onto the existing one instead.
                    sqlx::query!("DELETE FROM intersections WHERE id = $1", inserted)
                        .execute(&mut *tx)
                        .await?;
                    sqlx::query_scalar!(
                        "SELECT intersection_id FROM intersection_osm_nodes WHERE osm_node_id = $1",
                        node_id,
                    )
                    .fetch_one(&mut *tx)
                    .await?
                }
            }
        } else {
            inserted
        }
    };

    // `remix_id` is carried across via this SELECT (not a separate bind) --
    // otherwise the tail corridor would be created with a NULL remix_id and
    // silently vanish from `list_corridors_for_remix`.
    let new_corridor_id = sqlx::query_scalar!(
        "INSERT INTO corridors (remix_id, name, geometry_source) \
         SELECT remix_id, name || ' (split)', geometry_source FROM corridors WHERE id = $1 \
         RETURNING id",
        corridor_id.as_i64(),
    )
    .fetch_one(&mut *tx)
    .await?;

    // Reassign every tail cross-section after the split point to the new
    // corridor, keeping their existing `position` values (unique per
    // corridor, and the fresh corridor has no other rows yet, so no
    // collision). The split point itself (`partition.tail[0]`) stays on the
    // ORIGINAL corridor (head) as its new last cross-section -- only one row
    // can carry that id in the database, and `partition_at_split_point`
    // deliberately includes it in both `head` and `tail`. The tail's first
    // cross-section is a freshly inserted row at the same coordinate, not a
    // second reference to the same id.
    for cs in partition.tail.iter().skip(1) {
        sqlx::query!(
            "UPDATE cross_sections SET corridor_id = $1 WHERE id = $2",
            new_corridor_id,
            cs.id.as_i64(),
        )
        .execute(&mut *tx)
        .await?;
    }

    let split_point = &partition.tail[0];
    sqlx::query!(
        "INSERT INTO cross_sections \
         (corridor_id, position, lat, lon, osm_way_id, osm_node_id, intersection_id) \
         VALUES ($1, 0, $2, $3, $4, $5, $6)",
        new_corridor_id,
        split_point.lat,
        split_point.lon,
        split_point.osm_way_id,
        split_point.osm_node_id,
        new_intersection_id,
    )
    .execute(&mut *tx)
    .await?;

    sqlx::query!(
        "UPDATE cross_sections SET intersection_id = $1 WHERE id = $2",
        new_intersection_id,
        split_point.id.as_i64(),
    )
    .execute(&mut *tx)
    .await?;

    sqlx::query!(
        "UPDATE corridors SET sequence_version = sequence_version + 1 WHERE id = $1",
        corridor_id.as_i64(),
    )
    .execute(&mut *tx)
    .await?;

    tx.commit().await?;

    Ok((
        corridor_id,
        CorridorId::from(new_corridor_id),
        IntersectionId::from(new_intersection_id),
    ))
}

/// Merges `absorbed` into `surviving`: re-points every cross-section,
/// turn_movement, and intersection_osm_nodes row from `absorbed` onto
/// `surviving`, reconciles treatment fields (survivor's own non-null value
/// always wins; a real conflict -- both sides non-null and different -- is
/// recorded, not silently dropped or averaged), deletes the absorbed row,
/// and returns the audit log entry.
///
/// `turn_movements.intersection_id` has `ON DELETE CASCADE` (migration 028),
/// so this function MUST re-point any turn movements already attached to
/// `absorbed` before it deletes that row -- otherwise they'd be silently
/// destroyed by the cascade rather than merged onto `surviving`. Callers
/// should therefore prefer running merges before turn-movement inference
/// when both are part of the same import (as Task 8's import orchestration
/// does), but this function is safe to call regardless of ordering: any
/// turn movement already on `absorbed` survives the merge.
pub async fn merge_intersections(
    pool: &sqlx::PgPool,
    surviving: IntersectionId,
    absorbed: IntersectionId,
) -> Result<crate::corridor_design::intersection::IntersectionMerge, anyhow::Error> {
    let mut tx = pool.begin().await?;

    let survivor_row = sqlx::query!(
        "SELECT bus_gate, turn_conflict, bus_stop FROM intersections WHERE id = $1 FOR UPDATE",
        surviving.as_i64(),
    )
    .fetch_optional(&mut *tx)
    .await?
    .ok_or_else(|| anyhow::anyhow!("intersection {surviving} not found"))?;
    let absorbed_row = sqlx::query!(
        "SELECT bus_gate, turn_conflict, bus_stop FROM intersections WHERE id = $1 FOR UPDATE",
        absorbed.as_i64(),
    )
    .fetch_optional(&mut *tx)
    .await?
    .ok_or_else(|| anyhow::anyhow!("intersection {absorbed} not found"))?;

    fn reconcile(survivor: Option<String>, absorbed: Option<String>) -> (Option<String>, bool) {
        match (&survivor, &absorbed) {
            (Some(s), Some(a)) if s != a => (survivor, true),
            (Some(_), _) => (survivor, false),
            (None, Some(_)) => (absorbed, false),
            (None, None) => (None, false),
        }
    }
    let (bus_gate, bus_gate_conflict) = reconcile(survivor_row.bus_gate, absorbed_row.bus_gate);
    let (turn_conflict, turn_conflict_conflict) =
        reconcile(survivor_row.turn_conflict, absorbed_row.turn_conflict);
    let (bus_stop, bus_stop_conflict) = reconcile(survivor_row.bus_stop, absorbed_row.bus_stop);
    let treatment_conflict = bus_gate_conflict || turn_conflict_conflict || bus_stop_conflict;

    sqlx::query!(
        "UPDATE intersections SET bus_gate = $1, turn_conflict = $2, bus_stop = $3 WHERE id = $4",
        bus_gate,
        turn_conflict,
        bus_stop,
        surviving.as_i64(),
    )
    .execute(&mut *tx)
    .await?;

    sqlx::query!(
        "UPDATE cross_sections SET intersection_id = $1 WHERE intersection_id = $2",
        surviving.as_i64(),
        absorbed.as_i64(),
    )
    .execute(&mut *tx)
    .await?;

    // Re-point turn movements from `absorbed` onto `surviving`, guarding
    // against `turn_movements`' `UNIQUE (intersection_id, from_lane_id,
    // to_lane_id)` constraint (migration 028): if `surviving` already has a
    // row for the same `(from_lane_id, to_lane_id)` pair, the plain UPDATE
    // below skips that absorbed-side row via the NOT EXISTS guard rather
    // than hitting a unique-violation. Any row left un-repointed by the
    // guard (a genuine collision) is then cleaned up by the `DELETE FROM
    // intersections WHERE id = $absorbed` below, which cascades onto any
    // remaining `turn_movements.intersection_id = absorbed` rows.
    sqlx::query!(
        r#"UPDATE turn_movements
           SET intersection_id = $1
           WHERE intersection_id = $2
             AND NOT EXISTS (
                 SELECT 1 FROM turn_movements t2
                 WHERE t2.intersection_id = $1
                   AND t2.from_lane_id = turn_movements.from_lane_id
                   AND t2.to_lane_id = turn_movements.to_lane_id
             )"#,
        surviving.as_i64(),
        absorbed.as_i64(),
    )
    .execute(&mut *tx)
    .await?;

    let absorbed_osm_node_ids: Vec<i64> = sqlx::query_scalar!(
        "SELECT osm_node_id FROM intersection_osm_nodes WHERE intersection_id = $1",
        absorbed.as_i64(),
    )
    .fetch_all(&mut *tx)
    .await?;
    sqlx::query!(
        "UPDATE intersection_osm_nodes SET intersection_id = $1 WHERE intersection_id = $2",
        surviving.as_i64(),
        absorbed.as_i64(),
    )
    .execute(&mut *tx)
    .await?;

    sqlx::query!("DELETE FROM intersections WHERE id = $1", absorbed.as_i64())
        .execute(&mut *tx)
        .await?;

    let merged_at = sqlx::query_scalar!(
        r#"INSERT INTO intersection_merges
             (surviving_intersection_id, absorbed_osm_node_ids, treatment_conflict)
           VALUES ($1, $2, $3)
           RETURNING merged_at"#,
        surviving.as_i64(),
        &absorbed_osm_node_ids,
        treatment_conflict,
    )
    .fetch_one(&mut *tx)
    .await?;

    tx.commit().await?;

    Ok(crate::corridor_design::intersection::IntersectionMerge {
        surviving_intersection_id: surviving,
        absorbed_osm_node_ids,
        treatment_conflict,
        merged_at,
    })
}

/// Fetches all turn movements recorded for `intersection_id`.
pub async fn list_turn_movements(
    pool: &sqlx::PgPool,
    intersection_id: IntersectionId,
) -> Result<Vec<crate::corridor_design::intersection::TurnMovement>, anyhow::Error> {
    use crate::corridor_design::intersection::{TurnMovement, TurnMovementSource};
    let rows = sqlx::query!(
        "SELECT from_lane_id, to_lane_id, source FROM turn_movements WHERE intersection_id = $1",
        intersection_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;
    rows.into_iter()
        .map(|row| {
            Ok(TurnMovement {
                intersection_id,
                from_lane_id: LaneId::from(row.from_lane_id),
                to_lane_id: LaneId::from(row.to_lane_id),
                source: TurnMovementSource::from_db_str(&row.source).ok_or_else(|| {
                    anyhow::anyhow!("unknown turn_movement source: {}", row.source)
                })?,
            })
        })
        .collect()
}

/// Inserts or overwrites one turn movement. Used both for analyst-authored
/// (`source = Manual`) and one-off inferred rows.
pub async fn set_turn_movement(
    pool: &sqlx::PgPool,
    intersection_id: IntersectionId,
    from_lane_id: LaneId,
    to_lane_id: LaneId,
    source: crate::corridor_design::intersection::TurnMovementSource,
) -> Result<(), anyhow::Error> {
    sqlx::query!(
        r#"INSERT INTO turn_movements (intersection_id, from_lane_id, to_lane_id, source)
           VALUES ($1, $2, $3, $4)
           ON CONFLICT (intersection_id, from_lane_id, to_lane_id) DO UPDATE SET source = EXCLUDED.source"#,
        intersection_id.as_i64(),
        from_lane_id.as_i64(),
        to_lane_id.as_i64(),
        source.as_db_str(),
    )
    .execute(pool)
    .await?;
    Ok(())
}

/// Bulk-inserts inference results with `source = 'inferred'`, skipping any
/// pair that already has a row -- crucially, this means skipping (not
/// overwriting) a pair already marked `Manual`, since `ON CONFLICT DO
/// NOTHING` never touches an existing row regardless of its current source.
pub async fn insert_inferred_turn_movements(
    pool: &sqlx::PgPool,
    intersection_id: IntersectionId,
    pairs: &[(LaneId, LaneId)],
) -> Result<(), anyhow::Error> {
    for (from_lane_id, to_lane_id) in pairs {
        sqlx::query!(
            r#"INSERT INTO turn_movements (intersection_id, from_lane_id, to_lane_id, source)
               VALUES ($1, $2, $3, 'inferred')
               ON CONFLICT (intersection_id, from_lane_id, to_lane_id) DO NOTHING"#,
            intersection_id.as_i64(),
            from_lane_id.as_i64(),
            to_lane_id.as_i64(),
        )
        .execute(pool)
        .await?;
    }
    Ok(())
}

pub async fn delete_turn_movement(
    pool: &sqlx::PgPool,
    intersection_id: IntersectionId,
    from_lane_id: LaneId,
    to_lane_id: LaneId,
) -> Result<(), anyhow::Error> {
    sqlx::query!(
        "DELETE FROM turn_movements WHERE intersection_id = $1 AND from_lane_id = $2 AND to_lane_id = $3",
        intersection_id.as_i64(),
        from_lane_id.as_i64(),
        to_lane_id.as_i64(),
    )
    .execute(pool)
    .await?;
    Ok(())
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
            "SELECT id, position::float8 AS position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
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
            "SELECT id, position::float8 AS position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
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
            "SELECT id, position::float8 AS position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
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
    ///
    /// Needs a real multi-threaded runtime: on the default current-thread
    /// flavor, `tokio::join!` only interleaves the two futures cooperatively
    /// on one OS thread, so one side can run its entire read-then-insert
    /// sequence to completion (and commit) before the other's first query is
    /// even dispatched — no actual race, so nothing to reject. Two worker
    /// threads let both sides' queries genuinely execute concurrently, which
    /// `add_cross_section`'s SERIALIZABLE transaction then relies on to
    /// detect the conflict deterministically.
    #[tokio::test(flavor = "multi_thread", worker_threads = 2)]
    async fn concurrent_add_targeting_same_slot_rejects_at_least_one() {
        let td = test_utils::setup().await;
        let db = td.db;

        let (corridor_id, cross_section_ids) =
            seed_corridor_with_four_cross_sections(&db.pool).await;
        let anchor = cross_section_ids[1]; // "cs_002"

        // A plain `tokio::join!` only interleaves these two futures
        // cooperatively; nothing stops one from running its entire
        // read-then-insert sequence to completion (and committing) before
        // the other's first query is even dispatched, which would make this
        // test non-deterministic — a real race requires both sides to reach
        // the database at (as close as possible to) the same instant. A
        // `Barrier` combined with the multi-thread runtime above gives each
        // side its own OS thread and forces them to start their DB work
        // together.
        let barrier = std::sync::Arc::new(tokio::sync::Barrier::new(2));

        let pool_a = db.pool.clone();
        let barrier_a = std::sync::Arc::clone(&barrier);
        let task_a = tokio::spawn(async move {
            barrier_a.wait().await;
            add_cross_section(
                &pool_a,
                corridor_id,
                Some(anchor),
                Coordinate::new(45.41, -75.685),
            )
            .await
        });

        let pool_b = db.pool.clone();
        let barrier_b = std::sync::Arc::clone(&barrier);
        let task_b = tokio::spawn(async move {
            barrier_b.wait().await;
            add_cross_section(
                &pool_b,
                corridor_id,
                Some(anchor),
                Coordinate::new(45.415, -75.686),
            )
            .await
        });

        // TODO(Loop B): once real conflict detection exists, assert exactly one 201 /
        // one 409 POSITION_COLLISION, and that the DB ends up with exactly 5 rows with
        // all-distinct positions (see TC-REQ-004-6). This coarsely asserts at least
        // one side fails, per this pass's precedent for not-yet-typed errors.
        let (result_a, result_b) = tokio::join!(task_a, task_b);
        let result_a = result_a.expect("task_a panicked");
        let result_b = result_b.expect("task_b panicked");

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
            "SELECT id, position::float8 AS position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
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
            "SELECT id, position::float8 AS position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
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
            "SELECT id, position::float8 AS position FROM cross_sections WHERE corridor_id = $1 ORDER BY position",
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

    // --- Lane CRUD (insert/update/delete/access-rules) ---

    #[tokio::test]
    async fn insert_lane_computes_position_between_neighbors_and_defaults_access_rule() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;
        // sample_lane_drafts() (existing helper) produces lanes at position 1.0, 2.0.
        insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
            .await
            .unwrap();

        let new_lane = insert_lane(
            &db.pool,
            cross_section_id,
            LaneType::CycleLane,
            1.5,
            LaneDirection::Forward,
            1.5, // midpoint of the two existing lanes' positions
        )
        .await
        .expect("insert_lane should succeed");

        assert_eq!(new_lane.lane_type, LaneType::CycleLane);
        assert_eq!(new_lane.width_meters, 1.5);
        assert_eq!(new_lane.direction, LaneDirection::Forward);
        assert_eq!(new_lane.position, 1.5);
        assert_eq!(new_lane.access_rules.len(), 1);
        assert_eq!(
            new_lane.access_rules[0].allowed_modes,
            vec![crate::corridor_design::lanes::AccessMode::Bicycle],
            "a freshly-inserted lane defaults its access rule from LaneType, per default_access_rule_for"
        );

        let all_lanes = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .unwrap();
        assert_eq!(all_lanes.len(), 3);
    }

    #[tokio::test]
    async fn insert_lane_at_start_of_sequence_with_no_before_neighbor() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;
        insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
            .await
            .unwrap();

        let new_lane = insert_lane(
            &db.pool,
            cross_section_id,
            LaneType::Sidewalk,
            1.8,
            LaneDirection::None,
            0.5, // caller (API layer, per Task 3) computes this via assign_position
                 // with neighbors { before: None, after: Some(1.0) }; the repository
                 // function itself just persists whatever position it's given.
        )
        .await
        .expect("insert_lane should succeed");

        assert!(new_lane.position < 1.0);
    }

    #[tokio::test]
    async fn update_lane_changes_type_width_direction_but_not_access_rules() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;
        let lane_ids =
            insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
                .await
                .unwrap();
        let sidewalk_lane_id = lane_ids[0];

        let updated = update_lane(
            &db.pool,
            sidewalk_lane_id,
            LaneType::Parking,
            2.0,
            LaneDirection::None,
        )
        .await
        .expect("update_lane should succeed");

        assert_eq!(updated.lane_type, LaneType::Parking);
        assert_eq!(updated.width_meters, 2.0);
        assert_eq!(updated.direction, LaneDirection::None);
        // access_rules unchanged -- the original Sidewalk default (Pedestrian-only),
        // not re-derived for the new Parking type; update_lane only touches the
        // lane's own type/width/direction columns, never lane_access_rules.
        assert_eq!(
            updated.access_rules[0].allowed_modes,
            vec![crate::corridor_design::lanes::AccessMode::Pedestrian]
        );

        // Independent read-back. `update_lane` builds the `Lane` it returns from
        // its own lane_type/width_meters/direction ARGUMENTS (only id,
        // cross_section_id and position come from the RETURNING clause), so the
        // assertions above would hold even if the UPDATE never wrote those
        // columns at all. Only a fresh SELECT proves the write landed.
        let persisted = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .unwrap()
            .into_iter()
            .find(|l| l.id == sidewalk_lane_id)
            .expect("the updated lane is still in the cross-section");
        assert_eq!(persisted.lane_type, LaneType::Parking);
        assert_eq!(persisted.width_meters, 2.0);
        assert_eq!(persisted.direction, LaneDirection::None);
        assert_eq!(
            persisted.access_rules[0].allowed_modes,
            vec![crate::corridor_design::lanes::AccessMode::Pedestrian]
        );
    }

    #[tokio::test]
    async fn update_lane_for_nonexistent_lane_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;

        let result = update_lane(
            &db.pool,
            LaneId::from(999_999_i64),
            LaneType::Travel,
            3.0,
            LaneDirection::Forward,
        )
        .await;

        assert!(result.is_err());
    }

    #[tokio::test]
    async fn delete_lane_removes_it_and_its_access_rules() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;
        let lane_ids =
            insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
                .await
                .unwrap();
        let sidewalk_lane_id = lane_ids[0];

        delete_lane(&db.pool, sidewalk_lane_id)
            .await
            .expect("delete_lane should succeed");

        let remaining = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .unwrap();
        assert_eq!(remaining.len(), 1);
        assert!(!remaining.iter().any(|l| l.id == sidewalk_lane_id));

        let orphaned_rule_count: i64 =
            sqlx::query_scalar("SELECT COUNT(*) FROM lane_access_rules WHERE lane_id = $1")
                .bind(sidewalk_lane_id.as_i64())
                .fetch_one(&db.pool)
                .await
                .unwrap();
        assert_eq!(
            orphaned_rule_count, 0,
            "deleting a lane must cascade-delete its access rules"
        );
    }

    #[tokio::test]
    async fn delete_lane_for_nonexistent_lane_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;

        let result = delete_lane(&db.pool, LaneId::from(999_999_i64)).await;

        assert!(result.is_err());
    }

    #[tokio::test]
    async fn set_lane_access_rules_replaces_existing_rules() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;
        let lane_ids =
            insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
                .await
                .unwrap();
        let travel_lane_id = lane_ids[1];

        let new_rules = vec![
            TimedAccessRule {
                time_window: Some(crate::corridor_design::lanes::TimeWindow {
                    days: "weekdays".to_string(),
                    start_time: chrono::NaiveTime::from_hms_opt(7, 0, 0).unwrap(),
                    end_time: chrono::NaiveTime::from_hms_opt(9, 0, 0).unwrap(),
                }),
                allowed_modes: vec![crate::corridor_design::lanes::AccessMode::Transit],
            },
            TimedAccessRule {
                time_window: None,
                allowed_modes: vec![
                    crate::corridor_design::lanes::AccessMode::Car,
                    crate::corridor_design::lanes::AccessMode::Emergency,
                ],
            },
        ];

        set_lane_access_rules(&db.pool, travel_lane_id, &new_rules)
            .await
            .expect("set_lane_access_rules should succeed");

        let lanes = get_lanes_for_cross_section(&db.pool, cross_section_id)
            .await
            .unwrap();
        let travel_lane = lanes.iter().find(|l| l.id == travel_lane_id).unwrap();
        assert_eq!(
            travel_lane.access_rules.len(),
            2,
            "the original single default rule should be replaced, not appended to"
        );
        assert!(
            travel_lane
                .access_rules
                .iter()
                .any(|r| r.time_window.is_some())
        );
        assert!(
            travel_lane
                .access_rules
                .iter()
                .any(|r| r.time_window.is_none())
        );
    }

    // --- Intersections ---

    use crate::corridor_design::intersection::{BusGate, BusStop, TurnConflict};
    use crate::ids::IntersectionId;

    #[tokio::test]
    async fn create_or_match_intersection_with_no_matching_node_creates_new_row() {
        let td = test_utils::setup().await;
        let db = td.db;

        let id = create_or_match_intersection(&db.pool, 45.50, -73.60, Some(100))
            .await
            .expect("create_or_match_intersection should succeed");

        let intersection = get_intersection(&db.pool, id).await.unwrap();
        assert_eq!(intersection.lat, 45.50);
        assert_eq!(intersection.osm_node_ids, vec![100]);
    }

    #[tokio::test]
    async fn create_or_match_intersection_with_matching_node_reuses_existing_row() {
        let td = test_utils::setup().await;
        let db = td.db;

        let first = create_or_match_intersection(&db.pool, 45.50, -73.60, Some(200))
            .await
            .unwrap();
        let second = create_or_match_intersection(&db.pool, 45.50, -73.60, Some(200))
            .await
            .expect("second call with the same osm_node_id should match, not create");

        assert_eq!(first, second);
    }

    #[tokio::test]
    async fn create_or_match_intersection_without_osm_node_id_always_creates_new_row() {
        let td = test_utils::setup().await;
        let db = td.db;

        let first = create_or_match_intersection(&db.pool, 45.50, -73.60, None)
            .await
            .unwrap();
        let second = create_or_match_intersection(&db.pool, 45.50, -73.60, None)
            .await
            .unwrap();

        assert_ne!(
            first, second,
            "manual/private intersections never auto-match"
        );
    }

    /// Two concurrent calls racing to claim the same `osm_node_id` must both
    /// resolve to the same `IntersectionId`, and neither may surface a raw
    /// unique-violation error -- regression test for the
    /// SELECT-then-INSERT TOCTOU race on `intersection_osm_nodes.osm_node_id
    /// UNIQUE` (migration 028) that `create_or_match_intersection`'s
    /// `ON CONFLICT ... DO NOTHING RETURNING` + re-select fallback closes.
    /// `tokio::join!` starts both futures on the same pool essentially
    /// simultaneously, so both are very likely to miss the initial
    /// fast-path SELECT and race on the INSERT.
    #[tokio::test]
    async fn create_or_match_intersection_concurrent_calls_with_same_osm_node_id_do_not_race() {
        let td = test_utils::setup().await;
        let db = td.db;

        let (first, second) = tokio::join!(
            create_or_match_intersection(&db.pool, 45.50, -73.60, Some(400)),
            create_or_match_intersection(&db.pool, 45.50, -73.60, Some(400)),
        );

        let first = first.expect("first concurrent call should not surface a raw DB error");
        let second = second.expect("second concurrent call should not surface a raw DB error");
        assert_eq!(
            first, second,
            "both concurrent callers should resolve to the single winning intersection"
        );

        // Exactly one `intersections` row should exist for this node -- the
        // loser's row must have been rolled back, not left as an orphan.
        let intersection = get_intersection(&db.pool, first).await.unwrap();
        assert_eq!(intersection.osm_node_ids, vec![400]);
    }

    #[tokio::test]
    async fn get_intersection_returns_not_found_error_for_unknown_id() {
        let td = test_utils::setup().await;
        let db = td.db;

        let result = get_intersection(&db.pool, IntersectionId::from(999_999)).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn set_intersection_treatment_persists_all_three_fields() {
        let td = test_utils::setup().await;
        let db = td.db;
        let id = create_or_match_intersection(&db.pool, 45.50, -73.60, None)
            .await
            .unwrap();

        let updated = set_intersection_treatment(
            &db.pool,
            id,
            Some(BusGate::SignalControlled),
            Some(TurnConflict::RightInRightOut),
            Some(BusStop::BusBulb),
        )
        .await
        .expect("set_intersection_treatment should succeed");

        assert_eq!(updated.bus_gate, Some(BusGate::SignalControlled));
        assert_eq!(updated.turn_conflict, Some(TurnConflict::RightInRightOut));
        assert_eq!(updated.bus_stop, Some(BusStop::BusBulb));

        let reloaded = get_intersection(&db.pool, id).await.unwrap();
        assert_eq!(reloaded, updated);
    }

    #[tokio::test]
    async fn set_cross_section_intersection_persists_the_link() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let corridor_id = start_manual_corridor(&db.pool, remix_id, "Test Corridor")
            .await
            .unwrap();
        let cross_section_id =
            insert_cross_section(&db.pool, corridor_id, Coordinate::new(45.50, -73.60), 0)
                .await
                .unwrap();
        let intersection_id = create_or_match_intersection(&db.pool, 45.50, -73.60, None)
            .await
            .unwrap();

        set_cross_section_intersection(&db.pool, cross_section_id, intersection_id)
            .await
            .expect("set_cross_section_intersection should succeed");

        let cross_sections = get_corridor_cross_sections(&db.pool, corridor_id)
            .await
            .unwrap();
        assert_eq!(cross_sections[0].intersection_id, Some(intersection_id));
    }

    #[tokio::test]
    async fn corridors_at_intersection_lists_every_corridor_referencing_it() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let intersection_id = create_or_match_intersection(&db.pool, 45.50, -73.60, Some(300))
            .await
            .unwrap();

        let corridor_a = start_manual_corridor(&db.pool, remix_id, "Corridor A")
            .await
            .unwrap();
        let cs_a = insert_cross_section(&db.pool, corridor_a, Coordinate::new(45.50, -73.60), 0)
            .await
            .unwrap();
        set_cross_section_intersection(&db.pool, cs_a, intersection_id)
            .await
            .unwrap();

        let corridor_b = start_manual_corridor(&db.pool, remix_id, "Corridor B")
            .await
            .unwrap();
        let cs_b = insert_cross_section(&db.pool, corridor_b, Coordinate::new(45.50, -73.60), 0)
            .await
            .unwrap();
        set_cross_section_intersection(&db.pool, cs_b, intersection_id)
            .await
            .unwrap();

        let corridors = corridors_at_intersection(&db.pool, intersection_id)
            .await
            .unwrap();
        assert_eq!(corridors.len(), 2);
        assert!(corridors.contains(&corridor_a));
        assert!(corridors.contains(&corridor_b));
    }

    // --- Splitting ---
    //
    // NOTE: this task's brief called `insert_cross_section(&db.pool,
    // corridor_id, Coordinate::new(lat, -73.600))` (3 args, returning a
    // struct with a `.id` field). The real signature (see
    // `insert_cross_section` above) takes an explicit 4th `position: i32`
    // argument and returns `CrossSectionId` directly -- there is no
    // auto-increment. These tests pass `position` explicitly (0, 1, 2, ...)
    // in the order each point is inserted, and use the returned
    // `CrossSectionId` directly instead of `.id`.

    #[tokio::test]
    async fn split_corridor_at_cross_section_creates_new_corridor_and_intersection() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let corridor_id = start_manual_corridor(&db.pool, remix_id, "Splittable Corridor")
            .await
            .unwrap();

        // Four points ~111m apart, well beyond the split guard's minimum
        // distance -- points at (45.500,45.501,45.502,45.503).
        let mut cross_section_ids = Vec::new();
        for (position, lat) in [45.500, 45.501, 45.502, 45.503].into_iter().enumerate() {
            let cross_section_id = insert_cross_section(
                &db.pool,
                corridor_id,
                Coordinate::new(lat, -73.600),
                position as i32,
            )
            .await
            .unwrap();
            cross_section_ids.push(cross_section_id);
        }

        let (head_id, tail_id, new_intersection_id) = split_corridor_at_cross_section(
            &db.pool,
            corridor_id,
            cross_section_ids[1],
            0, // sequence_version starts at 0 (migration 023's default)
        )
        .await
        .expect("split should succeed");

        assert_eq!(head_id, corridor_id, "head keeps the original corridor id");
        assert_ne!(tail_id, corridor_id, "tail is a new corridor");

        let head_sections = get_corridor_cross_sections(&db.pool, head_id)
            .await
            .unwrap();
        assert_eq!(head_sections.len(), 2);
        assert_eq!(
            head_sections.last().unwrap().intersection_id,
            Some(new_intersection_id)
        );

        let tail_sections = get_corridor_cross_sections(&db.pool, tail_id)
            .await
            .unwrap();
        assert_eq!(
            tail_sections.len(),
            3,
            "tail includes the split point plus everything after it"
        );
        assert_eq!(
            tail_sections.first().unwrap().intersection_id,
            Some(new_intersection_id)
        );
    }

    #[tokio::test]
    async fn split_corridor_at_cross_section_preserves_lane_data_on_moved_cross_sections() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let corridor_id = start_manual_corridor(&db.pool, remix_id, "Splittable Corridor")
            .await
            .unwrap();

        let mut cross_section_ids = Vec::new();
        for (position, lat) in [45.500, 45.501, 45.502].into_iter().enumerate() {
            let cross_section_id = insert_cross_section(
                &db.pool,
                corridor_id,
                Coordinate::new(lat, -73.600),
                position as i32,
            )
            .await
            .unwrap();
            cross_section_ids.push(cross_section_id);
        }
        // Attach a lane with an access rule to the cross-section that will
        // move to the tail after splitting at index 1.
        let drafts = vec![LaneDraft {
            lane_type: LaneType::Travel,
            width_meters: 3.0,
            direction: LaneDirection::Forward,
            access_rules: vec![TimedAccessRule {
                time_window: None,
                allowed_modes: vec![AccessMode::Car],
            }],
        }];
        insert_lanes_for_cross_section(&db.pool, cross_section_ids[2], &drafts)
            .await
            .unwrap();

        let (_, tail_id, _) =
            split_corridor_at_cross_section(&db.pool, corridor_id, cross_section_ids[1], 0)
                .await
                .unwrap();

        let tail_sections = get_corridor_cross_sections(&db.pool, tail_id)
            .await
            .unwrap();
        let moved_cross_section = tail_sections
            .iter()
            .find(|cs| cs.id == cross_section_ids[2])
            .expect("cross-section 2 should have moved to the tail corridor");
        let lanes = get_lanes_for_cross_section(&db.pool, moved_cross_section.id)
            .await
            .unwrap();
        assert_eq!(lanes.len(), 1);
        assert_eq!(
            lanes[0].access_rules[0].allowed_modes,
            vec![AccessMode::Car]
        );
    }

    #[tokio::test]
    async fn split_corridor_at_cross_section_rejects_stale_sequence_version() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let corridor_id = start_manual_corridor(&db.pool, remix_id, "Splittable Corridor")
            .await
            .unwrap();
        let mut cross_section_ids = Vec::new();
        for (position, lat) in [45.500, 45.501, 45.502].into_iter().enumerate() {
            let cross_section_id = insert_cross_section(
                &db.pool,
                corridor_id,
                Coordinate::new(lat, -73.600),
                position as i32,
            )
            .await
            .unwrap();
            cross_section_ids.push(cross_section_id);
        }

        let result =
            split_corridor_at_cross_section(&db.pool, corridor_id, cross_section_ids[1], 999).await;

        assert!(
            result.is_err(),
            "wrong expected_sequence_version should be rejected"
        );
    }

    #[tokio::test]
    async fn split_corridor_at_cross_section_rejects_split_at_endpoint() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let corridor_id = start_manual_corridor(&db.pool, remix_id, "Splittable Corridor")
            .await
            .unwrap();
        let mut cross_section_ids = Vec::new();
        for (position, lat) in [45.500, 45.501, 45.502].into_iter().enumerate() {
            let cross_section_id = insert_cross_section(
                &db.pool,
                corridor_id,
                Coordinate::new(lat, -73.600),
                position as i32,
            )
            .await
            .unwrap();
            cross_section_ids.push(cross_section_id);
        }

        let result =
            split_corridor_at_cross_section(&db.pool, corridor_id, cross_section_ids[0], 0).await;

        assert!(result.is_err());
    }

    // --- Dual-carriageway merge ---

    #[tokio::test]
    async fn merge_intersections_repoints_cross_sections_and_deletes_absorbed_row() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;

        let survivor = create_or_match_intersection(&db.pool, 45.5000, -73.6000, Some(10))
            .await
            .unwrap();
        let absorbed = create_or_match_intersection(&db.pool, 45.50005, -73.6000, Some(20))
            .await
            .unwrap();

        let corridor_id = start_manual_corridor(&db.pool, remix_id, "Corridor on absorbed side")
            .await
            .unwrap();
        let cross_section_id = insert_cross_section(
            &db.pool,
            corridor_id,
            Coordinate::new(45.50005, -73.6000),
            0,
        )
        .await
        .unwrap();
        set_cross_section_intersection(&db.pool, cross_section_id, absorbed)
            .await
            .unwrap();

        let merge_record = merge_intersections(&db.pool, survivor, absorbed)
            .await
            .expect("merge should succeed");

        assert_eq!(merge_record.surviving_intersection_id, survivor);
        assert_eq!(merge_record.absorbed_osm_node_ids, vec![20]);
        assert!(!merge_record.treatment_conflict);

        let cross_sections = get_corridor_cross_sections(&db.pool, corridor_id)
            .await
            .unwrap();
        assert_eq!(cross_sections[0].intersection_id, Some(survivor));

        let survivor_details = get_intersection(&db.pool, survivor).await.unwrap();
        assert_eq!(survivor_details.osm_node_ids.len(), 2);
        assert!(survivor_details.osm_node_ids.contains(&10));
        assert!(survivor_details.osm_node_ids.contains(&20));

        let absorbed_still_exists = get_intersection(&db.pool, absorbed).await;
        assert!(
            absorbed_still_exists.is_err(),
            "absorbed intersection row should be gone"
        );
    }

    #[tokio::test]
    async fn merge_intersections_flags_conflicting_non_null_treatment_values() {
        let td = test_utils::setup().await;
        let db = td.db;

        let survivor = create_or_match_intersection(&db.pool, 45.5000, -73.6000, Some(30))
            .await
            .unwrap();
        set_intersection_treatment(
            &db.pool,
            survivor,
            Some(BusGate::SignalControlled),
            None,
            None,
        )
        .await
        .unwrap();

        let absorbed = create_or_match_intersection(&db.pool, 45.50005, -73.6000, Some(40))
            .await
            .unwrap();
        set_intersection_treatment(
            &db.pool,
            absorbed,
            Some(BusGate::YieldControlled),
            None,
            None,
        )
        .await
        .unwrap();

        let merge_record = merge_intersections(&db.pool, survivor, absorbed)
            .await
            .unwrap();

        assert!(merge_record.treatment_conflict);
        let survivor_details = get_intersection(&db.pool, survivor).await.unwrap();
        assert_eq!(
            survivor_details.bus_gate,
            Some(BusGate::SignalControlled),
            "survivor's own value wins on conflict, not the absorbed side's"
        );
    }

    /// Regression test for the reviewer finding on Task 6:
    /// `turn_movements.intersection_id` has `ON DELETE CASCADE` (migration
    /// 028), so `merge_intersections` deleting the absorbed row without
    /// first re-pointing `turn_movements` would silently destroy any turn
    /// movements already attached to the absorbed intersection.
    #[tokio::test]
    async fn merge_intersections_repoints_turn_movements() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;

        let survivor = create_or_match_intersection(&db.pool, 45.5000, -73.6000, Some(50))
            .await
            .unwrap();
        let absorbed = create_or_match_intersection(&db.pool, 45.50005, -73.6000, Some(60))
            .await
            .unwrap();

        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;
        let lane_ids =
            insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
                .await
                .unwrap();
        let from_lane_id = lane_ids[0];
        let to_lane_id = lane_ids[1];

        sqlx::query!(
            "INSERT INTO turn_movements (intersection_id, from_lane_id, to_lane_id, source) \
             VALUES ($1, $2, $3, 'inferred')",
            absorbed.as_i64(),
            from_lane_id.as_i64(),
            to_lane_id.as_i64(),
        )
        .execute(&db.pool)
        .await
        .unwrap();

        merge_intersections(&db.pool, survivor, absorbed)
            .await
            .expect("merge should succeed");

        let remaining_intersection_id: i64 = sqlx::query_scalar!(
            "SELECT intersection_id FROM turn_movements WHERE from_lane_id = $1 AND to_lane_id = $2",
            from_lane_id.as_i64(),
            to_lane_id.as_i64(),
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();
        assert_eq!(
            remaining_intersection_id,
            survivor.as_i64(),
            "turn movement should be re-pointed onto the surviving intersection, not lost to the CASCADE delete"
        );
    }

    /// Same regression as above, but exercises the unique-constraint
    /// collision path: both the surviving and absorbed intersection already
    /// have a `turn_movements` row for the same `(from_lane_id,
    /// to_lane_id)` pair. The absorbed-side duplicate must be dropped
    /// (via the `ON DELETE CASCADE` once the guarded UPDATE skips it),
    /// leaving exactly one row on the survivor -- not a unique-violation
    /// error.
    #[tokio::test]
    async fn merge_intersections_drops_colliding_duplicate_turn_movement() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;

        let survivor = create_or_match_intersection(&db.pool, 45.5000, -73.6000, Some(70))
            .await
            .unwrap();
        let absorbed = create_or_match_intersection(&db.pool, 45.50005, -73.6000, Some(80))
            .await
            .unwrap();

        let cross_section_id = seed_bare_cross_section(&db.pool, remix_id).await;
        let lane_ids =
            insert_lanes_for_cross_section(&db.pool, cross_section_id, &sample_lane_drafts())
                .await
                .unwrap();
        let from_lane_id = lane_ids[0];
        let to_lane_id = lane_ids[1];

        for intersection_id in [survivor, absorbed] {
            sqlx::query!(
                "INSERT INTO turn_movements (intersection_id, from_lane_id, to_lane_id, source) \
                 VALUES ($1, $2, $3, 'inferred')",
                intersection_id.as_i64(),
                from_lane_id.as_i64(),
                to_lane_id.as_i64(),
            )
            .execute(&db.pool)
            .await
            .unwrap();
        }

        merge_intersections(&db.pool, survivor, absorbed)
            .await
            .expect("merge should succeed despite the colliding pair");

        let rows: Vec<i64> = sqlx::query_scalar!(
            "SELECT intersection_id FROM turn_movements WHERE from_lane_id = $1 AND to_lane_id = $2",
            from_lane_id.as_i64(),
            to_lane_id.as_i64(),
        )
        .fetch_all(&db.pool)
        .await
        .unwrap();
        assert_eq!(
            rows,
            vec![survivor.as_i64()],
            "exactly one turn movement should remain, on the surviving intersection"
        );
    }

    // --- Turn movements ---

    use crate::corridor_design::intersection::TurnMovementSource;

    #[tokio::test]
    async fn set_turn_movement_then_list_returns_it_with_manual_source() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let intersection_id = create_or_match_intersection(&db.pool, 45.50, -73.60, None)
            .await
            .unwrap();
        let corridor_id = start_manual_corridor(&db.pool, remix_id, "Corridor")
            .await
            .unwrap();
        let cs_id = insert_cross_section(&db.pool, corridor_id, Coordinate::new(45.50, -73.60), 0)
            .await
            .unwrap();
        let drafts = vec![LaneDraft {
            lane_type: LaneType::Travel,
            width_meters: 3.0,
            direction: LaneDirection::Forward,
            access_rules: vec![],
        }];
        insert_lanes_for_cross_section(&db.pool, cs_id, &drafts)
            .await
            .unwrap();
        let lanes = get_lanes_for_cross_section(&db.pool, cs_id).await.unwrap();
        let lane_id = lanes[0].id;

        set_turn_movement(
            &db.pool,
            intersection_id,
            lane_id,
            lane_id,
            TurnMovementSource::Manual,
        )
        .await
        .expect("set_turn_movement should succeed");

        let movements = list_turn_movements(&db.pool, intersection_id)
            .await
            .unwrap();
        assert_eq!(movements.len(), 1);
        assert_eq!(movements[0].source, TurnMovementSource::Manual);
    }

    #[tokio::test]
    async fn insert_inferred_turn_movements_skips_pairs_already_marked_manual() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let intersection_id = create_or_match_intersection(&db.pool, 45.50, -73.60, None)
            .await
            .unwrap();
        let corridor_id = start_manual_corridor(&db.pool, remix_id, "Corridor")
            .await
            .unwrap();
        let cs_id = insert_cross_section(&db.pool, corridor_id, Coordinate::new(45.50, -73.60), 0)
            .await
            .unwrap();
        let drafts = vec![LaneDraft {
            lane_type: LaneType::Travel,
            width_meters: 3.0,
            direction: LaneDirection::Forward,
            access_rules: vec![],
        }];
        insert_lanes_for_cross_section(&db.pool, cs_id, &drafts)
            .await
            .unwrap();
        let lanes = get_lanes_for_cross_section(&db.pool, cs_id).await.unwrap();
        let lane_id = lanes[0].id;

        set_turn_movement(
            &db.pool,
            intersection_id,
            lane_id,
            lane_id,
            TurnMovementSource::Manual,
        )
        .await
        .unwrap();

        insert_inferred_turn_movements(&db.pool, intersection_id, &[(lane_id, lane_id)])
            .await
            .expect("insert_inferred_turn_movements should succeed");

        let movements = list_turn_movements(&db.pool, intersection_id)
            .await
            .unwrap();
        assert_eq!(
            movements.len(),
            1,
            "the manual row must not be duplicated or overwritten"
        );
        assert_eq!(movements[0].source, TurnMovementSource::Manual);
    }

    #[tokio::test]
    async fn delete_turn_movement_removes_it() {
        let td = test_utils::setup().await;
        let db = td.db;
        let remix_id = seed_remix(&db.pool).await;
        let intersection_id = create_or_match_intersection(&db.pool, 45.50, -73.60, None)
            .await
            .unwrap();
        let corridor_id = start_manual_corridor(&db.pool, remix_id, "Corridor")
            .await
            .unwrap();
        let cs_id = insert_cross_section(&db.pool, corridor_id, Coordinate::new(45.50, -73.60), 0)
            .await
            .unwrap();
        let drafts = vec![LaneDraft {
            lane_type: LaneType::Travel,
            width_meters: 3.0,
            direction: LaneDirection::Forward,
            access_rules: vec![],
        }];
        insert_lanes_for_cross_section(&db.pool, cs_id, &drafts)
            .await
            .unwrap();
        let lanes = get_lanes_for_cross_section(&db.pool, cs_id).await.unwrap();
        let lane_id = lanes[0].id;
        set_turn_movement(
            &db.pool,
            intersection_id,
            lane_id,
            lane_id,
            TurnMovementSource::Manual,
        )
        .await
        .unwrap();

        delete_turn_movement(&db.pool, intersection_id, lane_id, lane_id)
            .await
            .expect("delete_turn_movement should succeed");

        let movements = list_turn_movements(&db.pool, intersection_id)
            .await
            .unwrap();
        assert!(movements.is_empty());
    }
}

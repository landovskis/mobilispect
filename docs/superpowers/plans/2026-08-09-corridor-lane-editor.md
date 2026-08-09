# Corridor Lane Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an analyst view and edit a corridor's cross-sections — descriptive label, and lane-by-lane arrangement (type, width, direction, time-windowed access rules) — replacing the "coming soon" placeholder corridor page.

**Architecture:** A mini-map on the corridor page lets the analyst pick a cross-section; a to-scale lane diagram (already-approved visual style) shows its lanes; clicking a lane opens a side panel for immediate-save editing. Three pre-existing, fully-tested pure-logic stubs (`assign_position`, `validate_label`, `apply_cross_section_edit`) and one repository stub (`update_cross_section_label`) get GREEN-passed; new repository/API/UI layers handle lane CRUD.

**Tech Stack:** Rust (2024 edition), Axum, sqlx, Yew 0.23 + MapLibre GL JS (existing WASM shell), Playwright.

## Global Constraints

- No mocks in tests — integration tests use real Postgres via `testcontainers`.
- Functional Core / Imperative Shell is mandatory: `position::assign_position`, `edit::validate_label`, `edit::apply_cross_section_edit` are pure (no I/O); all I/O lives in `repository.rs`/handler files.
- sqlx queries must be compile-time checked (`query!`/`query_as!`), except test-seeding `RETURNING id` inserts, which use the runtime `sqlx::query_scalar(...)` form.
- ID newtypes only — never raw `i64`/`String` for domain identifiers in `crates/core`/`crates/server` Rust code (HTTP/JSON boundaries use plain `i64`, converted immediately).
- This plan modifies `crates/core/migrations/` is NOT true — no new migration is added. `cross_sections.version`/`cross_sections.label` already exist (migration 024); `lanes`/`lane_access_rules` already exist (migration 026).
- Design spec: `docs/superpowers/specs/2026-08-09-corridor-lane-editor-design.md`. One small, necessary refinement made while writing this plan (documented in Task 3): the label-update endpoint's path gained `:corridor_id` (the spec's own endpoint table omitted it, but `update_cross_section_label`'s existing, pre-tested signature requires both ids) — `PATCH /api/corridors/:corridor_id/cross-sections/:cross_section_id/label`, not `PATCH /api/cross-sections/:cross_section_id/label`.
- Out of scope (separate, already-identified future slices): `bus_stop`/intersection treatments, cross-section add/reorder (`add_cross_section`, `reorder_cross_sections`, `compute_reordered_positions` all stay `unimplemented!()`), typed 404s (matches this codebase's existing convention everywhere else), any visual weekly-schedule grid for time windows.

---

## Task 1: GREEN-pass pure logic + `update_cross_section_label`

**Files:**
- Modify: `crates/core/src/corridor_design/mod.rs` (add `version: i32` to `CrossSection`)
- Modify: `crates/core/src/corridor_design/repository.rs` (`get_corridor_cross_sections` query + `update_cross_section_label` GREEN-pass)
- Modify: `crates/core/src/corridor_design/edit.rs` (`validate_label`/`apply_cross_section_edit` GREEN-pass + `make_cross_section` fixture)
- Modify: `crates/core/src/corridor_design/position.rs` (`assign_position` GREEN-pass)

**Interfaces:**
- Consumes: nothing new — all four functions/fields already exist as stubs with complete, passing-once-implemented tests.
- Produces: `CrossSection.version: i32` (new field, read by later tasks' API responses); `position::assign_position(neighbors: Neighbors) -> Result<f64, PositionAssignmentError>`; `repository::update_cross_section_label(pool, corridor_id, cross_section_id, new_label: Option<String>, expected_version: i32) -> Result<CrossSection, anyhow::Error>` — Task 3's API layer calls both.

- [ ] **Step 1: Add `version` to `CrossSection` and ripple it through**

`update_cross_section_label` needs to return the row's new (incremented) `version` to its caller, but `CrossSection` doesn't carry that field today. In `crates/core/src/corridor_design/mod.rs`, find the `CrossSection` struct:

```rust
pub struct CrossSection {
    pub id: CrossSectionId,
    pub corridor_id: CorridorId,
    pub position: f64,
    pub lat: f64,
    pub lon: f64,
    pub osm_way_id: Option<i64>,
    pub osm_node_id: Option<i64>,
    /// Descriptive label, editable via REQ-006's edit shell. `NULL`/`None` until an
    /// analyst sets one; not populated by REQ-001/002/004's creation paths.
    pub label: Option<String>,
}
```

Add a `version` field after `label`:

```rust
    pub label: Option<String>,
    /// Optimistic-concurrency counter (migration 024's `cross_sections.version`
    /// column, `DEFAULT 1`). Bumped on every successful `update_cross_section_label`
    /// call.
    pub version: i32,
}
```

In `crates/core/src/corridor_design/repository.rs`'s `get_corridor_cross_sections`, add `version` to the SELECT column list and the struct construction:

```rust
pub async fn get_corridor_cross_sections(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
) -> Result<Vec<CrossSection>, anyhow::Error> {
    let rows = sqlx::query!(
        r#"SELECT id, corridor_id, position::float8 AS "position!", lat, lon,
                  osm_way_id, osm_node_id, label, version
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
        })
        .collect())
}
```

In `crates/core/src/corridor_design/edit.rs`'s test module, `make_cross_section` constructs a `CrossSection` literal directly and will fail to compile without the new field. Update it:

```rust
    fn make_cross_section(id: i64, position: f64, label: &str) -> CrossSection {
        CrossSection {
            id: CrossSectionId::from(id),
            corridor_id: CorridorId::from(1),
            position,
            lat: 45.500 + position * 0.001,
            lon: -73.600 + position * 0.001,
            osm_way_id: None,
            osm_node_id: None,
            label: Some(label.to_string()),
            version: 1,
        }
    }
```

Run `cargo build -p mobilispect-core 2>&1 | grep -A3 "error\["` and fix any other `CrossSection` struct-literal construction the compiler flags (there should be none beyond this one fixture — `get_corridor_cross_sections` above is the only other Rust-side constructor, already updated).

- [ ] **Step 2: Implement `position::assign_position`**

In `crates/core/src/corridor_design/position.rs`, replace:

```rust
pub fn assign_position(neighbors: Neighbors) -> Result<f64, PositionAssignmentError> {
    let _ = neighbors;
    unimplemented!("IMP-REQ-004-04: assign_position not yet implemented")
}
```

with:

```rust
pub fn assign_position(neighbors: Neighbors) -> Result<f64, PositionAssignmentError> {
    match (neighbors.before, neighbors.after) {
        (Some(before), Some(after)) => {
            if before >= after {
                return Err(PositionAssignmentError::NonMonotonicNeighbors);
            }
            let midpoint = before + (after - before) / 2.0;
            if midpoint <= before || midpoint >= after {
                // Floating-point precision exhausted: `before`/`after` are so
                // close together that no representable value lies strictly
                // between them.
                return Err(PositionAssignmentError::UnresolvableInterval);
            }
            Ok(midpoint)
        }
        (None, Some(after)) => Ok(after - 1.0),
        (Some(before), None) => Ok(before + 1.0),
        (None, None) => Ok(0.0),
    }
}
```

Run: `cargo nextest run -p mobilispect-core corridor_design::position::tests::assign_position`
Expected: all 4 `assign_position_*` tests PASS. The `compute_reordered_positions` tests further down in this same file remain failing (`unimplemented!()`) — that function is explicitly out of scope for this plan (REQ-005, a separate future slice); do not touch it.

- [ ] **Step 3: Implement `edit::validate_label` and `edit::apply_cross_section_edit`**

In `crates/core/src/corridor_design/edit.rs`, replace:

```rust
pub fn validate_label(raw: &str) -> Result<Option<String>, LabelValidationError> {
    let _ = raw;
    unimplemented!("IMP-REQ-006-05: validate_label not yet implemented")
}
```

with:

```rust
pub fn validate_label(raw: &str) -> Result<Option<String>, LabelValidationError> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return Err(LabelValidationError::Empty);
    }
    if trimmed.chars().count() > MAX_LABEL_LENGTH {
        return Err(LabelValidationError::TooLong);
    }
    if trimmed.chars().any(|c| c.is_control()) {
        return Err(LabelValidationError::ContainsControlCharacters);
    }
    Ok(Some(trimmed.to_string()))
}
```

Replace:

```rust
pub fn apply_cross_section_edit(
    cross_sections: Vec<CrossSection>,
    target_id: CrossSectionId,
    new_label: Option<String>,
) -> Result<Vec<CrossSection>, EditError> {
    let _ = (cross_sections, target_id, new_label);
    unimplemented!("IMP-REQ-006-05: apply_cross_section_edit not yet implemented")
}
```

with:

```rust
pub fn apply_cross_section_edit(
    cross_sections: Vec<CrossSection>,
    target_id: CrossSectionId,
    new_label: Option<String>,
) -> Result<Vec<CrossSection>, EditError> {
    if !cross_sections.iter().any(|cs| cs.id == target_id) {
        return Err(EditError::NotFound(target_id));
    }
    Ok(cross_sections
        .into_iter()
        .map(|mut cs| {
            if cs.id == target_id {
                cs.label = new_label.clone();
            }
            cs
        })
        .collect())
}
```

Run: `cargo nextest run -p mobilispect-core corridor_design::edit::tests`
Expected: all 5 tests PASS (`apply_cross_section_edit_updates_only_target_label`, `apply_cross_section_edit_with_unknown_target_returns_not_found`, `validate_label_rejects_201_char_label`, `validate_label_rejects_whitespace_only_label`, `validate_label_accepts_200_char_label`, `validate_label_accepts_normal_trimmed_label` — 6, not 5; count them from the file, don't hardcode a number you haven't verified against the actual test module).

- [ ] **Step 4: Implement `repository::update_cross_section_label`**

In `crates/core/src/corridor_design/repository.rs`, replace:

```rust
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
```

with:

```rust
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
                     osm_way_id, osm_node_id, label, version"#,
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
    })
}
```

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core corridor_design::repository::tests::update_cross_section_label`
Expected: all 4 `update_cross_section_label_*` tests PASS (`edits_and_saves_successfully`, `does_not_alter_siblings`, `with_stale_version_returns_err`, `for_deleted_cross_section_returns_err`).

- [ ] **Step 5: Confirm the crate still builds and the untouched REQ-004/005 stubs still fail the same way**

Run: `cargo build -p mobilispect-core`
Expected: succeeds.

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core corridor_design --no-fail-fast 2>&1 | tail -15`
Expected: the only remaining failures in `corridor_design::` are `add_cross_section_*`, `reorder_cross_sections_*` (repository.rs) and `compute_reordered_positions` (there are no direct tests of that function alone, but `reorder_cross_sections_*` exercise it transitively) — the same `unimplemented!()` panics as before this task, unchanged. If anything else fails, fix it before moving on.

- [ ] **Step 6: Commit**

```bash
git add crates/core/src/corridor_design/mod.rs crates/core/src/corridor_design/repository.rs crates/core/src/corridor_design/edit.rs crates/core/src/corridor_design/position.rs
git commit -m "feat(corridor-design): GREEN-pass assign_position, label validation/edit, and update_cross_section_label"
```

---

## Task 2: Lane CRUD repository functions

**Files:**
- Modify: `crates/core/src/corridor_design/repository.rs`

**Interfaces:**
- Consumes: `crate::corridor_design::position::{Neighbors, assign_position}` (existing, GREEN-passed in Task 1 but NOT called from this task's functions directly — the API layer in Task 3 calls `assign_position` itself and passes the computed `f64` down, so pure validation stays out of the imperative shell), `crate::corridor_design::lanes::{Lane, LaneType, LaneDirection, AccessMode, TimeWindow, TimedAccessRule, default_access_rule_for}` (existing).
- Produces: `repository::{insert_lane, update_lane, delete_lane, set_lane_access_rules}` — Task 3's API layer calls all four.

- [ ] **Step 1: Write the failing tests**

Add to `crates/core/src/corridor_design/repository.rs`'s `#[cfg(test)] mod tests` block, after the existing `get_lanes_for_cross_section_returns_empty_for_a_cross_section_with_no_lanes` test (the module's last test today):

```rust
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
```

- [ ] **Step 2: Run the tests to verify they fail for the right reason**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core corridor_design::repository::tests::insert_lane corridor_design::repository::tests::update_lane corridor_design::repository::tests::delete_lane corridor_design::repository::tests::set_lane_access_rules 2>&1 | tail -20`
Expected: FAIL — `cannot find function insert_lane/update_lane/delete_lane/set_lane_access_rules in this scope` (a compile error, since these functions don't exist yet).

- [ ] **Step 3: Implement the four functions**

Add to `crates/core/src/corridor_design/repository.rs`, after `get_lanes_for_cross_section` (the production code, not the test module):

```rust
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
```

This uses `AccessMode` and `TimeWindow`, which aren't in this file's current import list. Update the existing import line near the top of `repository.rs`:

```rust
use crate::corridor_design::lanes::{
    AccessMode, Lane, LaneDirection, LaneDraft, LaneType, TimeWindow, TimedAccessRule,
};
```

(was: `use crate::corridor_design::lanes::{Lane, LaneDirection, LaneDraft, LaneType, TimedAccessRule};`)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core corridor_design::repository::tests::insert_lane corridor_design::repository::tests::update_lane corridor_design::repository::tests::delete_lane corridor_design::repository::tests::set_lane_access_rules`
Expected: all 7 new tests PASS.

- [ ] **Step 5: Confirm the crate still builds**

Run: `cargo build -p mobilispect-core`
Expected: succeeds.

- [ ] **Step 6: Commit**

```bash
git add crates/core/src/corridor_design/repository.rs
git commit -m "feat(corridor-design): add lane insert/update/delete and access-rule CRUD"
```

---

## Task 3: JSON API — cross-sections, lanes, label, access rules

**Files:**
- Create: `crates/server/src/web/lane_editor_api.rs`
- Modify: `crates/server/src/web/mod.rs`

**Interfaces:**
- Consumes: `mobilispect_core::corridor_design::edit::validate_label`, `mobilispect_core::corridor_design::position::{Neighbors, assign_position}`, `mobilispect_core::corridor_design::repository::{get_corridor_cross_sections, get_lanes_for_cross_section, update_cross_section_label, insert_lane, update_lane, delete_lane, set_lane_access_rules}` (all from Tasks 1-2), `mobilispect_core::corridor_design::lanes::{LaneType, LaneDirection, AccessMode, TimeWindow, TimedAccessRule}` (existing).
- Produces: `GET /api/corridors/:corridor_id/cross-sections`, `GET /api/cross-sections/:cross_section_id/lanes`, `PATCH /api/corridors/:corridor_id/cross-sections/:cross_section_id/label`, `POST /api/cross-sections/:cross_section_id/lanes`, `PATCH /api/lanes/:lane_id`, `DELETE /api/lanes/:lane_id`, `PUT /api/lanes/:lane_id/access-rules`. Response DTOs `lane_editor_api::{CrossSectionResponse, LaneResponse, AccessRuleResponse, TimeWindowResponse}` — Task 5 (WASM) mirrors these field-for-field in `api.rs`.

- [ ] **Step 1: Write the module with its tests**

Create `crates/server/src/web/lane_editor_api.rs`:

```rust
//! JSON API for cross-section label editing and lane CRUD (REQ-006). See
//! `docs/superpowers/specs/2026-08-09-corridor-lane-editor-design.md`.

use axum::Json;
use axum::extract::{Path, State};
use axum::http::StatusCode;

use mobilispect_core::corridor_design::edit::validate_label;
use mobilispect_core::corridor_design::lanes::{
    AccessMode, Lane, LaneDirection, LaneType, TimeWindow, TimedAccessRule,
};
use mobilispect_core::corridor_design::position::{Neighbors, assign_position};
use mobilispect_core::corridor_design::repository;
use mobilispect_core::ids::{CorridorId, CrossSectionId, LaneId};

use crate::web::AppState;

type ApiError = (StatusCode, Json<serde_json::Value>);

fn internal_error(context: &str, err: anyhow::Error) -> ApiError {
    tracing::error!(error = %err, "{context}");
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        Json(serde_json::json!({ "error": "Internal Server Error" })),
    )
}

fn bad_request(message: &str) -> ApiError {
    (
        StatusCode::BAD_REQUEST,
        Json(serde_json::json!({ "error": message })),
    )
}

/// Every failure mode of `update_cross_section_label` (not found, wrong corridor,
/// stale version) collapses into one untyped `anyhow::Error` at the repository
/// layer -- see that function's own doc comment. Mapped uniformly to 409 here
/// (rather than the 500 every other "not found" case in this codebase uses),
/// since this is the one endpoint in this plan with a real optimistic-concurrency
/// story; a not-found row is a degenerate case of "conflicts with what the client
/// expected to be true," not a server fault.
fn conflict(message: &str) -> ApiError {
    (
        StatusCode::CONFLICT,
        Json(serde_json::json!({ "error": message })),
    )
}

const MAX_LANE_WIDTH_METERS: f64 = 20.0;

fn parse_lane_type(raw: &str) -> Result<LaneType, ApiError> {
    LaneType::from_db_str(raw).ok_or_else(|| bad_request("unrecognized lane_type"))
}

fn parse_lane_direction(raw: &str) -> Result<LaneDirection, ApiError> {
    LaneDirection::from_db_str(raw).ok_or_else(|| bad_request("unrecognized direction"))
}

fn validate_width(width_meters: f64) -> Result<(), ApiError> {
    if width_meters <= 0.0 || width_meters > MAX_LANE_WIDTH_METERS {
        return Err(bad_request("width_meters must be between 0 and 20 meters"));
    }
    Ok(())
}

// --- Cross-sections ---

#[derive(Debug, serde::Serialize)]
pub struct CrossSectionResponse {
    pub id: i64,
    pub position: f64,
    pub label: Option<String>,
    pub lat: f64,
    pub lon: f64,
    pub version: i32,
}

/// `GET /api/corridors/:corridor_id/cross-sections` — for the corridor page's
/// mini-map.
pub async fn list_cross_sections(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
) -> Result<Json<Vec<CrossSectionResponse>>, ApiError> {
    let cross_sections =
        repository::get_corridor_cross_sections(&state.db.pool, CorridorId::from(corridor_id))
            .await
            .map_err(|e| internal_error("list_cross_sections", e))?;

    Ok(Json(
        cross_sections
            .into_iter()
            .map(|cs| CrossSectionResponse {
                id: cs.id.as_i64(),
                position: cs.position,
                label: cs.label,
                lat: cs.lat,
                lon: cs.lon,
                version: cs.version,
            })
            .collect(),
    ))
}

#[derive(Debug, serde::Deserialize)]
pub struct UpdateLabelRequest {
    pub label: Option<String>,
    pub expected_version: i32,
}

/// `PATCH /api/corridors/:corridor_id/cross-sections/:cross_section_id/label`
pub async fn update_label(
    State(state): State<AppState>,
    Path((corridor_id, cross_section_id)): Path<(i64, i64)>,
    Json(req): Json<UpdateLabelRequest>,
) -> Result<Json<CrossSectionResponse>, ApiError> {
    // `req.label: None` means "clear the label" (skips validate_label entirely --
    // an absent label is never invalid). `Some(raw)` goes through validate_label,
    // which rejects empty/too-long/control-character input as 400.
    let new_label = req
        .label
        .map(|raw| validate_label(&raw).map_err(|e| bad_request(&e.to_string())))
        .transpose()?
        .flatten();

    let updated = repository::update_cross_section_label(
        &state.db.pool,
        CorridorId::from(corridor_id),
        CrossSectionId::from(cross_section_id),
        new_label,
        req.expected_version,
    )
    .await
    .map_err(|e| conflict(&e.to_string()))?;

    Ok(Json(CrossSectionResponse {
        id: updated.id.as_i64(),
        position: updated.position,
        label: updated.label,
        lat: updated.lat,
        lon: updated.lon,
        version: updated.version,
    }))
}

// --- Lanes ---

#[derive(Debug, serde::Serialize)]
pub struct TimeWindowResponse {
    pub days: String,
    pub start_time: String,
    pub end_time: String,
}

#[derive(Debug, serde::Serialize)]
pub struct AccessRuleResponse {
    pub time_window: Option<TimeWindowResponse>,
    pub allowed_modes: Vec<String>,
}

#[derive(Debug, serde::Serialize)]
pub struct LaneResponse {
    pub id: i64,
    pub position: f64,
    pub lane_type: String,
    pub width_meters: f64,
    pub direction: String,
    pub access_rules: Vec<AccessRuleResponse>,
}

fn to_lane_response(lane: Lane) -> LaneResponse {
    LaneResponse {
        id: lane.id.as_i64(),
        position: lane.position,
        lane_type: lane.lane_type.as_db_str().to_string(),
        width_meters: lane.width_meters,
        direction: lane.direction.as_db_str().to_string(),
        access_rules: lane
            .access_rules
            .into_iter()
            .map(to_access_rule_response)
            .collect(),
    }
}

fn to_access_rule_response(rule: TimedAccessRule) -> AccessRuleResponse {
    AccessRuleResponse {
        time_window: rule.time_window.map(|w| TimeWindowResponse {
            days: w.days,
            start_time: w.start_time.format("%H:%M").to_string(),
            end_time: w.end_time.format("%H:%M").to_string(),
        }),
        allowed_modes: rule
            .allowed_modes
            .iter()
            .map(|m| m.as_db_str().to_string())
            .collect(),
    }
}

/// `GET /api/cross-sections/:cross_section_id/lanes` — for the lane diagram.
pub async fn list_lanes(
    State(state): State<AppState>,
    Path(cross_section_id): Path<i64>,
) -> Result<Json<Vec<LaneResponse>>, ApiError> {
    let lanes =
        repository::get_lanes_for_cross_section(&state.db.pool, CrossSectionId::from(cross_section_id))
            .await
            .map_err(|e| internal_error("list_lanes", e))?;
    Ok(Json(lanes.into_iter().map(to_lane_response).collect()))
}

#[derive(Debug, serde::Deserialize)]
pub struct InsertLaneRequest {
    pub lane_type: String,
    pub width_meters: f64,
    pub direction: String,
    pub neighbor_before_position: Option<f64>,
    pub neighbor_after_position: Option<f64>,
}

/// `POST /api/cross-sections/:cross_section_id/lanes` — inserts a new lane in the
/// gap between two neighbors the client already knows (it's rendering the full
/// ordered lane list), or at either end. `assign_position`'s pure logic runs here,
/// in the shell, before any I/O -- a `PositionAssignmentError` becomes a 400, never
/// reaching the repository layer.
pub async fn insert_lane(
    State(state): State<AppState>,
    Path(cross_section_id): Path<i64>,
    Json(req): Json<InsertLaneRequest>,
) -> Result<(StatusCode, Json<LaneResponse>), ApiError> {
    let lane_type = parse_lane_type(&req.lane_type)?;
    let direction = parse_lane_direction(&req.direction)?;
    validate_width(req.width_meters)?;

    let neighbors = Neighbors {
        before: req.neighbor_before_position,
        after: req.neighbor_after_position,
    };
    let position = assign_position(neighbors).map_err(|e| bad_request(&e.to_string()))?;

    let lane = repository::insert_lane(
        &state.db.pool,
        CrossSectionId::from(cross_section_id),
        lane_type,
        req.width_meters,
        direction,
        position,
    )
    .await
    .map_err(|e| internal_error("insert_lane", e))?;

    Ok((StatusCode::CREATED, Json(to_lane_response(lane))))
}

#[derive(Debug, serde::Deserialize)]
pub struct UpdateLaneRequest {
    pub lane_type: String,
    pub width_meters: f64,
    pub direction: String,
}

/// `PATCH /api/lanes/:lane_id`
pub async fn update_lane(
    State(state): State<AppState>,
    Path(lane_id): Path<i64>,
    Json(req): Json<UpdateLaneRequest>,
) -> Result<Json<LaneResponse>, ApiError> {
    let lane_type = parse_lane_type(&req.lane_type)?;
    let direction = parse_lane_direction(&req.direction)?;
    validate_width(req.width_meters)?;

    let lane = repository::update_lane(
        &state.db.pool,
        LaneId::from(lane_id),
        lane_type,
        req.width_meters,
        direction,
    )
    .await
    .map_err(|e| internal_error("update_lane", e))?;

    Ok(Json(to_lane_response(lane)))
}

/// `DELETE /api/lanes/:lane_id`
pub async fn delete_lane(
    State(state): State<AppState>,
    Path(lane_id): Path<i64>,
) -> Result<StatusCode, ApiError> {
    repository::delete_lane(&state.db.pool, LaneId::from(lane_id))
        .await
        .map_err(|e| internal_error("delete_lane", e))?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Debug, serde::Deserialize)]
pub struct TimeWindowRequest {
    pub days: String,
    pub start_time: String,
    pub end_time: String,
}

#[derive(Debug, serde::Deserialize)]
pub struct AccessRuleRequest {
    pub time_window: Option<TimeWindowRequest>,
    pub allowed_modes: Vec<String>,
}

#[derive(Debug, serde::Deserialize)]
pub struct SetAccessRulesRequest {
    pub rules: Vec<AccessRuleRequest>,
}

/// `PUT /api/lanes/:lane_id/access-rules` — replaces the lane's whole rule list.
pub async fn set_access_rules(
    State(state): State<AppState>,
    Path(lane_id): Path<i64>,
    Json(req): Json<SetAccessRulesRequest>,
) -> Result<Json<Vec<AccessRuleResponse>>, ApiError> {
    let mut rules = Vec::with_capacity(req.rules.len());
    for rule in req.rules {
        let allowed_modes = rule
            .allowed_modes
            .iter()
            .map(|m| AccessMode::from_db_str(m).ok_or_else(|| bad_request("unrecognized access mode")))
            .collect::<Result<Vec<_>, _>>()?;
        let time_window = match rule.time_window {
            Some(tw) => Some(TimeWindow {
                days: tw.days,
                start_time: chrono::NaiveTime::parse_from_str(&tw.start_time, "%H:%M")
                    .map_err(|_| bad_request("start_time must be HH:MM"))?,
                end_time: chrono::NaiveTime::parse_from_str(&tw.end_time, "%H:%M")
                    .map_err(|_| bad_request("end_time must be HH:MM"))?,
            }),
            None => None,
        };
        rules.push(TimedAccessRule {
            time_window,
            allowed_modes,
        });
    }

    repository::set_lane_access_rules(&state.db.pool, LaneId::from(lane_id), &rules)
        .await
        .map_err(|e| internal_error("set_access_rules", e))?;

    Ok(Json(rules.into_iter().map(to_access_rule_response).collect()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::web::SetupState;
    use mobilispect_core::config::Config;
    use mobilispect_core::db::test_utils;
    use std::sync::Arc;
    use tokio::sync::RwLock;

    fn test_config() -> Config {
        Config {
            database_url: String::new(),
            poll_interval_secs: 30,
            bind_address: "0.0.0.0:3000".to_string(),
            on_time_early_threshold_secs: -60,
            on_time_late_threshold_secs: 300,
            retention_days: 30,
            worker_health_bind_address: "0.0.0.0:9090".to_string(),
            transitland_api_key: None,
        }
    }

    async fn test_state() -> (AppState, test_utils::TestDb) {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db.clone(),
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        (state, td)
    }

    async fn seed_corridor_with_cross_section_and_lanes(
        state: &AppState,
    ) -> (i64, i64, i64, i64) {
        sqlx::query(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon) \
             VALUES (1, 'Test Region', 'UTC', 45.40, -73.70, 45.60, -73.50) \
             ON CONFLICT (id) DO NOTHING",
        )
        .execute(&state.db.pool)
        .await
        .unwrap();
        let remix_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id) VALUES ('Test Remix', 1) RETURNING id",
        )
        .fetch_one(&state.db.pool)
        .await
        .unwrap();
        let corridor_id: i64 = sqlx::query_scalar(
            "INSERT INTO corridors (name, geometry_source, remix_id) VALUES ('Test Corridor', 'manual', $1) RETURNING id",
        )
        .bind(remix_id)
        .fetch_one(&state.db.pool)
        .await
        .unwrap();
        let cross_section_id: i64 = sqlx::query_scalar(
            "INSERT INTO cross_sections (corridor_id, position, lat, lon, label) \
             VALUES ($1, 0, 45.50, -73.60, 'Main St @ 5th') RETURNING id",
        )
        .bind(corridor_id)
        .fetch_one(&state.db.pool)
        .await
        .unwrap();
        let lane_id: i64 = sqlx::query_scalar(
            "INSERT INTO lanes (cross_section_id, position, lane_type, width_meters, direction) \
             VALUES ($1, 1, 'travel', 3.0, 'forward') RETURNING id",
        )
        .bind(cross_section_id)
        .fetch_one(&state.db.pool)
        .await
        .unwrap();
        (remix_id, corridor_id, cross_section_id, lane_id)
    }

    #[tokio::test]
    async fn list_cross_sections_returns_seeded_cross_section() {
        let (state, _td) = test_state().await;
        let (_remix_id, corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = list_cross_sections(State(state), Path(corridor_id))
            .await
            .unwrap();

        assert_eq!(response.0.len(), 1);
        assert_eq!(response.0[0].id, cross_section_id);
        assert_eq!(response.0[0].label.as_deref(), Some("Main St @ 5th"));
        assert_eq!(response.0[0].version, 1);
    }

    #[tokio::test]
    async fn update_label_happy_path_returns_incremented_version() {
        let (state, _td) = test_state().await;
        let (_remix_id, corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = update_label(
            State(state),
            Path((corridor_id, cross_section_id)),
            Json(UpdateLabelRequest {
                label: Some("Main St @ 5th Ave (widened)".to_string()),
                expected_version: 1,
            }),
        )
        .await
        .unwrap();

        assert_eq!(
            response.0.label.as_deref(),
            Some("Main St @ 5th Ave (widened)")
        );
        assert_eq!(response.0.version, 2);
    }

    #[tokio::test]
    async fn update_label_with_blank_label_returns_400() {
        let (state, _td) = test_state().await;
        let (_remix_id, corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = update_label(
            State(state),
            Path((corridor_id, cross_section_id)),
            Json(UpdateLabelRequest {
                label: Some("   ".to_string()),
                expected_version: 1,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn update_label_with_stale_version_returns_409() {
        let (state, _td) = test_state().await;
        let (_remix_id, corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = update_label(
            State(state),
            Path((corridor_id, cross_section_id)),
            Json(UpdateLabelRequest {
                label: Some("wrong version".to_string()),
                expected_version: 999,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::CONFLICT);
    }

    #[tokio::test]
    async fn list_lanes_returns_seeded_lane() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = list_lanes(State(state), Path(cross_section_id))
            .await
            .unwrap();

        assert_eq!(response.0.len(), 1);
        assert_eq!(response.0[0].id, lane_id);
        assert_eq!(response.0[0].lane_type, "travel");
    }

    #[tokio::test]
    async fn insert_lane_happy_path_returns_201() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = insert_lane(
            State(state),
            Path(cross_section_id),
            Json(InsertLaneRequest {
                lane_type: "sidewalk".to_string(),
                width_meters: 1.8,
                direction: "none".to_string(),
                neighbor_before_position: None,
                neighbor_after_position: Some(1.0),
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0, StatusCode::CREATED);
        assert_eq!(response.1.lane_type, "sidewalk");
        assert!(response.1.position < 1.0);
    }

    #[tokio::test]
    async fn insert_lane_with_invalid_width_returns_400() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = insert_lane(
            State(state),
            Path(cross_section_id),
            Json(InsertLaneRequest {
                lane_type: "sidewalk".to_string(),
                width_meters: 0.0,
                direction: "none".to_string(),
                neighbor_before_position: None,
                neighbor_after_position: Some(1.0),
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn insert_lane_with_non_monotonic_neighbors_returns_400() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = insert_lane(
            State(state),
            Path(cross_section_id),
            Json(InsertLaneRequest {
                lane_type: "sidewalk".to_string(),
                width_meters: 1.8,
                direction: "none".to_string(),
                neighbor_before_position: Some(5.0),
                neighbor_after_position: Some(3.0),
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn update_lane_happy_path() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, _cross_section_id, lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = update_lane(
            State(state),
            Path(lane_id),
            Json(UpdateLaneRequest {
                lane_type: "turn".to_string(),
                width_meters: 3.2,
                direction: "backward".to_string(),
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0.lane_type, "turn");
        assert_eq!(response.0.width_meters, 3.2);
        assert_eq!(response.0.direction, "backward");
    }

    #[tokio::test]
    async fn delete_lane_happy_path_returns_204() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, _cross_section_id, lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = delete_lane(State(state), Path(lane_id)).await.unwrap();

        assert_eq!(response, StatusCode::NO_CONTENT);
    }

    #[tokio::test]
    async fn set_access_rules_happy_path() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, _cross_section_id, lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = set_access_rules(
            State(state),
            Path(lane_id),
            Json(SetAccessRulesRequest {
                rules: vec![AccessRuleRequest {
                    time_window: Some(TimeWindowRequest {
                        days: "weekdays".to_string(),
                        start_time: "07:00".to_string(),
                        end_time: "09:00".to_string(),
                    }),
                    allowed_modes: vec!["transit".to_string()],
                }],
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0.len(), 1);
        assert_eq!(response.0[0].allowed_modes, vec!["transit".to_string()]);
        let window = response.0[0].time_window.as_ref().unwrap();
        assert_eq!(window.start_time, "07:00");
    }

    #[tokio::test]
    async fn set_access_rules_with_unrecognized_mode_returns_400() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, _cross_section_id, lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = set_access_rules(
            State(state),
            Path(lane_id),
            Json(SetAccessRulesRequest {
                rules: vec![AccessRuleRequest {
                    time_window: None,
                    allowed_modes: vec!["spaceship".to_string()],
                }],
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }
}
```

- [ ] **Step 2: Register the module and routes**

In `crates/server/src/web/mod.rs`, add the module declaration alphabetically:

```rust
mod corridor_api;
mod handlers;
mod lane_editor_api;
pub mod middleware;
mod osm_import;
mod remix_api;
```

Add these routes to `build_router`, after the existing `/api/remixes/:remix_id/corridors/import` route and before `.nest_service("/builder", ...)`:

```rust
        .route(
            "/api/corridors/:corridor_id/cross-sections",
            get(lane_editor_api::list_cross_sections),
        )
        .route(
            "/api/corridors/:corridor_id/cross-sections/:cross_section_id/label",
            axum::routing::patch(lane_editor_api::update_label),
        )
        .route(
            "/api/cross-sections/:cross_section_id/lanes",
            get(lane_editor_api::list_lanes).post(lane_editor_api::insert_lane),
        )
        .route(
            "/api/lanes/:lane_id",
            axum::routing::patch(lane_editor_api::update_lane).delete(lane_editor_api::delete_lane),
        )
        .route(
            "/api/lanes/:lane_id/access-rules",
            axum::routing::put(lane_editor_api::set_access_rules),
        )
```

- [ ] **Step 3: Run the tests**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-server lane_editor_api::tests`
Expected: all 12 tests PASS.

- [ ] **Step 4: Verify the workspace still builds**

Run: `cargo build --workspace`
Expected: succeeds.

- [ ] **Step 5: Commit**

```bash
git add crates/server/src/web/lane_editor_api.rs crates/server/src/web/mod.rs
git commit -m "feat(corridor-design): add cross-section label and lane CRUD JSON API"
```

---

## Task 4: E2E spec (written first, failing)

**Files:**
- Create: `e2e/tests/builder-lane-editor.spec.ts`

**Interfaces:**
- Consumes: `ensureRegionHasBoundingBox`, `withDb` (existing, `e2e/tests/helpers/db.ts`).

- [ ] **Step 1: Write the spec**

Create `e2e/tests/builder-lane-editor.spec.ts`. Uses a Playwright fixture (`test.extend`), not a shared `beforeAll`/`afterAll` pair, so each test gets its own corridor/cross-section/lane rows — this suite's tests mutate and delete lanes, so sharing one seeded row across tests (as `builder-osm-import.spec.ts`'s read-only fixture does) would make tests order- and parallelism-dependent:

```typescript
import { test as base, expect, type Page } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

type Fixtures = {
  seededCrossSection: { remixId: number; corridorId: number; crossSectionId: number };
};

const test = base.extend<Fixtures>({
  seededCrossSection: async ({}, use, testInfo) => {
    await ensureRegionHasBoundingBox();
    let remixId = 0;
    let corridorId = 0;
    let crossSectionId = 0;

    await withDb(async (client) => {
      const remixResult = await client.query(
        `INSERT INTO remixes (name, region_id) VALUES ($1, 1) RETURNING id`,
        [`Lane Editor Test Remix ${testInfo.testId}`]
      );
      remixId = remixResult.rows[0].id;

      const corridorResult = await client.query(
        `INSERT INTO corridors (name, geometry_source, remix_id) VALUES ($1, 'manual', $2) RETURNING id`,
        [`Lane Editor Test Corridor ${testInfo.testId}`, remixId]
      );
      corridorId = corridorResult.rows[0].id;

      const crossSectionResult = await client.query(
        `INSERT INTO cross_sections (corridor_id, position, lat, lon, label) VALUES ($1, 0, 45.500, -73.600, 'Main St @ 5th') RETURNING id`,
        [corridorId]
      );
      crossSectionId = crossSectionResult.rows[0].id;

      await client.query(
        `INSERT INTO lanes (cross_section_id, position, lane_type, width_meters, direction) VALUES ($1, 1, 'travel', 3.0, 'forward')`,
        [crossSectionId]
      );
    });

    await use({ remixId, corridorId, crossSectionId });

    await withDb(async (client) => {
      await client.query(
        `DELETE FROM lane_access_rules WHERE lane_id IN (SELECT id FROM lanes WHERE cross_section_id = $1)`,
        [crossSectionId]
      );
      await client.query(`DELETE FROM lanes WHERE cross_section_id = $1`, [crossSectionId]);
      await client.query(`DELETE FROM cross_sections WHERE corridor_id = $1`, [corridorId]);
      await client.query(`DELETE FROM corridors WHERE id = $1`, [corridorId]);
      await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
    });
  },
});

/**
 * Navigates to the corridor page and clicks the one seeded cross-section
 * (at lon -73.6, lat 45.5). Written before the WASM UI for it exists
 * (Task 5), so every test here fails today for the correct reason, matching
 * this repo's established precedent.
 */
async function selectFirstCrossSection(page: Page, corridorId: number, remixId: number) {
  await page.goto(`/builder/remix/${remixId}/corridor/${corridorId}`);
  await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);
  const px = await page.evaluate(() => {
    const point = (window as any).__corridorBuilderMap.project([-73.6, 45.5]);
    return { x: point.x, y: point.y };
  });
  await page.locator('.maplibregl-canvas').click({ position: px });
}

test.describe('Corridor Design: lane editor', () => {
  test('selecting a cross-section shows its label and lane diagram, editing the label persists', async ({
    page,
    seededCrossSection,
  }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);

    await expect(page.getByLabel('Cross-section label')).toHaveValue('Main St @ 5th');
    await expect(page.getByText('Travel')).toBeVisible();

    await page.getByLabel('Cross-section label').fill('Main St @ 5th Ave');
    await page.getByLabel('Cross-section label').blur();
    await expect(page.getByLabel('Cross-section label')).toHaveValue('Main St @ 5th Ave');

    // Reload to confirm the edit actually persisted server-side, not just in local state.
    await selectFirstCrossSection(page, corridorId, remixId);
    await expect(page.getByLabel('Cross-section label')).toHaveValue('Main St @ 5th Ave');
  });

  test('clicking a lane opens its edit panel; editing width, type, and direction persist', async ({
    page,
    seededCrossSection,
  }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);

    await page.getByText('Travel').click();
    await expect(page.getByLabel('Width (meters)')).toHaveValue('3');

    await page.getByLabel('Width (meters)').fill('3.5');
    await page.getByLabel('Width (meters)').blur();
    await expect(page.getByLabel('Width (meters)')).toHaveValue('3.5');

    await page.getByLabel('Lane type').selectOption('turn');
    await expect(page.getByText('Turn')).toBeVisible();

    await page.getByLabel('Direction').selectOption('backward');
    await expect(page.getByLabel('Direction')).toHaveValue('backward');
  });

  test('inserting a lane via a gap control adds it to the diagram', async ({ page, seededCrossSection }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);

    await expect(page.locator('.xs-lane')).toHaveCount(1);
    await page.getByLabel('Add lane at start').click();
    await expect(page.locator('.xs-lane')).toHaveCount(2);
  });

  test('removing a lane deletes it from the diagram', async ({ page, seededCrossSection }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);

    await page.getByText('Travel').click();
    await page.getByLabel('Remove lane').click();
    await expect(page.locator('.xs-lane')).toHaveCount(0);
  });

  test('adding and removing an access rule persists', async ({ page, seededCrossSection }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);
    await page.getByText('Travel').click();

    await expect(page.getByLabel('Allowed modes')).toHaveCount(1);
    await page.getByText('+ Add time window').click();
    await expect(page.getByLabel('Allowed modes')).toHaveCount(2);

    await page.getByLabel('Allowed modes').nth(1).fill('transit,emergency');
    await page.getByLabel('Allowed modes').nth(1).blur();
    await expect(page.getByLabel('Allowed modes').nth(1)).toHaveValue('transit,emergency');

    await page.getByLabel('Remove access rule').nth(1).click();
    await expect(page.getByLabel('Allowed modes')).toHaveCount(1);
  });
});
```

- [ ] **Step 2: Confirm it fails for the right reason**

With the dev server running (per the environment setup pattern established in the manual-trace and OSM-import plans — Postgres via `mobilispect-pg`, `mobilispect-server` started in the background):

```bash
cd e2e && npx playwright test builder-lane-editor --project=chromium --list
npx playwright test builder-lane-editor --project=chromium
```

Expected: all 5 tests discovered with no parse errors; all fail because `page.waitForFunction(() => window.__corridorBuilderMap !== undefined)` times out — the corridor page is still the "Corridor editor coming soon" placeholder with no MapLibre map at all.

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/builder-lane-editor.spec.ts
git commit -m "test(corridor-design): add failing E2E spec for the lane editor"
```

---

## Task 5: WASM UI — corridor page becomes the lane editor

**Files:**
- Modify: `crates/corridor_builder_web/src/pages/corridor.rs`
- Modify: `crates/corridor_builder_web/src/api.rs`
- Modify: `crates/corridor_builder_web/index.html` (diagram CSS)

**Interfaces:**
- Consumes: all 7 endpoints from Task 3 (`GET/PATCH` cross-sections and label, `GET/POST` lanes, `PATCH/DELETE` a lane, `PUT` access-rules); `crate::maplibre::{Map, expose_map_for_e2e_tests}` (existing).
- Produces: the real `CorridorPage` component, replacing the placeholder.

Note on naming: `crates/corridor_builder_web/src/api.rs` already has a `pub struct CrossSectionResponse` (used by `add_manual_point`'s response, fields `id, position, lat, lon` — no `label`/`version`, since the manual-trace endpoint doesn't return those). This task's new cross-section list response has a different shape (`id, position, label, lat, lon, version`), so it gets a distinctly-named type, `CrossSectionSummary`, rather than colliding with or overloading the existing one.

- [ ] **Step 1: Add API client functions**

In `crates/corridor_builder_web/src/api.rs`, add after the existing `import_corridor` function:

```rust
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct CrossSectionSummary {
    pub id: i64,
    #[allow(dead_code)]
    pub position: f64,
    pub label: Option<String>,
    pub lat: f64,
    pub lon: f64,
    pub version: i32,
}

pub async fn list_cross_sections(corridor_id: i64) -> Result<Vec<CrossSectionSummary>, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/corridors/{corridor_id}/cross-sections"
    )))
    .await
}

#[derive(Debug, Clone, Serialize)]
struct UpdateLabelRequest {
    label: Option<String>,
    expected_version: i32,
}

pub async fn update_cross_section_label(
    corridor_id: i64,
    cross_section_id: i64,
    label: Option<String>,
    expected_version: i32,
) -> Result<CrossSectionSummary, String> {
    let request = gloo_net::http::Request::patch(&format!(
        "{API_BASE}/corridors/{corridor_id}/cross-sections/{cross_section_id}/label"
    ))
    .json(&UpdateLabelRequest {
        label,
        expected_version,
    })
    .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TimeWindowValue {
    pub days: String,
    pub start_time: String,
    pub end_time: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct AccessRuleValue {
    pub time_window: Option<TimeWindowValue>,
    pub allowed_modes: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct LaneResponse {
    pub id: i64,
    pub position: f64,
    pub lane_type: String,
    pub width_meters: f64,
    pub direction: String,
    pub access_rules: Vec<AccessRuleValue>,
}

pub async fn list_lanes(cross_section_id: i64) -> Result<Vec<LaneResponse>, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/cross-sections/{cross_section_id}/lanes"
    )))
    .await
}

#[derive(Debug, Clone, Serialize)]
struct UpdateLaneRequest {
    lane_type: String,
    width_meters: f64,
    direction: String,
}

pub async fn update_lane(
    lane_id: i64,
    lane_type: String,
    width_meters: f64,
    direction: String,
) -> Result<LaneResponse, String> {
    let request = gloo_net::http::Request::patch(&format!("{API_BASE}/lanes/{lane_id}"))
        .json(&UpdateLaneRequest {
            lane_type,
            width_meters,
            direction,
        })
        .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, Serialize)]
struct InsertLaneRequest {
    lane_type: String,
    width_meters: f64,
    direction: String,
    neighbor_before_position: Option<f64>,
    neighbor_after_position: Option<f64>,
}

pub async fn insert_lane(
    cross_section_id: i64,
    lane_type: String,
    width_meters: f64,
    direction: String,
    neighbor_before_position: Option<f64>,
    neighbor_after_position: Option<f64>,
) -> Result<LaneResponse, String> {
    let request = gloo_net::http::Request::post(&format!(
        "{API_BASE}/cross-sections/{cross_section_id}/lanes"
    ))
    .json(&InsertLaneRequest {
        lane_type,
        width_meters,
        direction,
        neighbor_before_position,
        neighbor_after_position,
    })
    .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

/// No response body (`204 No Content`) -- doesn't go through `send_and_decode`,
/// which always tries to JSON-decode a success response.
pub async fn delete_lane(lane_id: i64) -> Result<(), String> {
    let response = gloo_net::http::Request::delete(&format!("{API_BASE}/lanes/{lane_id}"))
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if response.ok() {
        Ok(())
    } else {
        let body: serde_json::Value = response.json().await.unwrap_or_default();
        Err(body["error"]
            .as_str()
            .unwrap_or("request failed")
            .to_string())
    }
}

#[derive(Debug, Clone, Serialize)]
struct SetAccessRulesRequest {
    rules: Vec<AccessRuleValue>,
}

pub async fn set_access_rules(
    lane_id: i64,
    rules: Vec<AccessRuleValue>,
) -> Result<Vec<AccessRuleValue>, String> {
    let request = gloo_net::http::Request::put(&format!("{API_BASE}/lanes/{lane_id}/access-rules"))
        .json(&SetAccessRulesRequest { rules })
        .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}
```

`gloo-net` 0.7 (this crate's pinned version) provides `get`/`post`/`put`/`patch`/`delete` associated functions on `gloo_net::http::Request`, matching every HTTP method Task 3's API uses.

- [ ] **Step 2: Add the lane-diagram CSS**

`crates/corridor_builder_web/index.html` has no separate stylesheet — all styling is the inline `<style>` block (`.field`, `.card`, `.chip`, etc., using this file's own `--ink-*`/`--line`/`--civic-*` variable names). Add the diagram's classes to that same block, right after the existing `.setup-sub` rule:

```css
          .setup-sub { color: var(--ink-500); font-size: 0.9rem; margin-bottom: 2rem; }
          .xs-diagram { display: flex; height: 90px; border-radius: 6px; overflow: hidden; border: 1px solid var(--line); }
          .xs-lane {
            display: flex; flex-direction: column; align-items: center; justify-content: center;
            font-size: 11px; font-weight: 600; color: #fff; text-align: center; padding: 2px;
            cursor: pointer; position: relative;
          }
          .xs-lane:hover { outline: 2px solid var(--ink-900); outline-offset: -2px; }
          .xs-lane .w { font-size: 9px; font-weight: 400; opacity: 0.85; }
          .xs-add {
            display: flex; align-items: center; justify-content: center; width: 28px;
            background: repeating-linear-gradient(45deg, var(--surface-muted), var(--surface-muted) 4px, var(--surface) 4px, var(--surface) 8px);
            color: var(--ink-500); font-size: 18px; cursor: pointer; border: none;
          }
          .access-rule { display: flex; gap: 6px; align-items: center; margin-bottom: 6px; }
          .access-rule input { flex: 1; }
```

(Only the first line, `.setup-sub { ... }`, already exists — it's shown here purely as the anchor to insert after.)

- [ ] **Step 3: Write the corridor page**

Replace the entire contents of `crates/corridor_builder_web/src/pages/corridor.rs`:

```rust
use wasm_bindgen::prelude::*;
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;
use crate::maplibre::Map;

#[derive(Properties, PartialEq)]
pub struct CorridorPageProps {
    pub remix_id: i64,
    pub corridor_id: i64,
}

#[component]
pub fn CorridorPage(props: &CorridorPageProps) -> Html {
    let remix_id = props.remix_id;
    let corridor_id = props.corridor_id;

    // `selected_cross_section_id` is set from the mini-map's long-lived native
    // click Closure (registered once via `map.on("click", ...)` + `.forget()`),
    // but only ever *written*, never read-then-computed-from inside that
    // closure -- so a plain `UseStateHandle` is safe here, unlike
    // `pages/import_osm.rs`'s selection state (which the click closure both
    // reads AND writes, and so needs `Rc<RefCell<...>>` -- see that file's
    // state-management comment for the full explanation of the hazard this
    // page doesn't have).
    let selected_cross_section_id = use_state(|| None::<i64>);
    let cross_sections = use_state(|| Vec::<api::CrossSectionSummary>::new());
    let lanes = use_state(|| Vec::<api::LaneResponse>::new());
    let selected_lane_id = use_state(|| None::<i64>);
    let error = use_state(|| None::<String>);

    // Mounts the mini-map once, fetches the corridor's cross-sections, and
    // renders them as a clickable point layer.
    {
        let cross_sections = cross_sections.clone();
        let selected_cross_section_id = selected_cross_section_id.clone();
        let error = error.clone();
        use_effect_with((), move |()| {
            let options = to_js_value(&serde_json::json!({
                "container": "corridor-map",
                "style": osm_raster_style(),
                "center": [-73.6, 45.5],
                "zoom": 15,
            }));
            if let Ok(options) = options {
                let map = Map::new(&options);
                crate::maplibre::expose_map_for_e2e_tests(&map);

                let load_map = map.clone();
                let load_cross_sections = cross_sections.clone();
                let load_error = error.clone();
                wasm_bindgen_futures::spawn_local(async move {
                    match api::list_cross_sections(corridor_id).await {
                        Ok(fetched) => {
                            render_cross_sections_layer(&load_map, &fetched);
                            load_cross_sections.set(fetched);
                        }
                        Err(e) => load_error.set(Some(e)),
                    }
                });

                let click_map = map.clone();
                let click_selected = selected_cross_section_id.clone();
                let onclick = Closure::wrap(Box::new(move |event: JsValue| {
                    if let Some(id) = extract_clicked_cross_section_id(&click_map, &event) {
                        click_selected.set(Some(id));
                    }
                }) as Box<dyn FnMut(JsValue)>);
                map.on("click", &onclick);
                onclick.forget();
            }
            || ()
        });
    }

    // Fetches the selected cross-section's lanes whenever the selection changes.
    {
        let lanes = lanes.clone();
        let selected_lane_id = selected_lane_id.clone();
        let error = error.clone();
        use_effect_with(*selected_cross_section_id, move |selected_id| {
            if let Some(cross_section_id) = *selected_id {
                let lanes = lanes.clone();
                let selected_lane_id = selected_lane_id.clone();
                let error = error.clone();
                selected_lane_id.set(None);
                wasm_bindgen_futures::spawn_local(async move {
                    match api::list_lanes(cross_section_id).await {
                        Ok(fetched) => lanes.set(fetched),
                        Err(e) => error.set(Some(e)),
                    }
                });
            }
            || ()
        });
    }

    let selected_cross_section = cross_sections
        .iter()
        .find(|cs| Some(cs.id) == *selected_cross_section_id)
        .cloned();

    let on_label_blur = {
        let cross_sections = cross_sections.clone();
        let selected_cross_section_id = selected_cross_section_id.clone();
        let error = error.clone();
        Callback::from(move |e: FocusEvent| {
            let Some(cross_section_id) = *selected_cross_section_id else {
                return;
            };
            let Some(current) = cross_sections.iter().find(|cs| cs.id == cross_section_id).cloned()
            else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlInputElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let cross_sections = cross_sections.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::update_cross_section_label(
                    corridor_id,
                    cross_section_id,
                    Some(value),
                    current.version,
                )
                .await
                {
                    Ok(updated) => {
                        let mut next: Vec<api::CrossSectionSummary> = (*cross_sections).clone();
                        if let Some(entry) = next.iter_mut().find(|cs| cs.id == updated.id) {
                            *entry = updated;
                        }
                        cross_sections.set(next);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let selected_lane = lanes
        .iter()
        .find(|l| Some(l.id) == *selected_lane_id)
        .cloned();

    let on_width_blur = {
        let lanes = lanes.clone();
        let selected_lane_id = selected_lane_id.clone();
        let error = error.clone();
        Callback::from(move |e: FocusEvent| {
            let Some(lane_id) = *selected_lane_id else {
                return;
            };
            let Some(current) = lanes.iter().find(|l| l.id == lane_id).cloned() else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlInputElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let Ok(width_meters) = value.parse::<f64>() else {
                return;
            };
            let lanes = lanes.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::update_lane(
                    lane_id,
                    current.lane_type.clone(),
                    width_meters,
                    current.direction.clone(),
                )
                .await
                {
                    Ok(updated) => {
                        let mut next: Vec<api::LaneResponse> = (*lanes).clone();
                        if let Some(entry) = next.iter_mut().find(|l| l.id == updated.id) {
                            *entry = updated;
                        }
                        lanes.set(next);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let on_type_change = {
        let lanes = lanes.clone();
        let selected_lane_id = selected_lane_id.clone();
        let error = error.clone();
        Callback::from(move |e: Event| {
            let Some(lane_id) = *selected_lane_id else {
                return;
            };
            let Some(current) = lanes.iter().find(|l| l.id == lane_id).cloned() else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let lanes = lanes.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::update_lane(lane_id, value, current.width_meters, current.direction.clone()).await {
                    Ok(updated) => {
                        let mut next: Vec<api::LaneResponse> = (*lanes).clone();
                        if let Some(entry) = next.iter_mut().find(|l| l.id == updated.id) {
                            *entry = updated;
                        }
                        lanes.set(next);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let on_direction_change = {
        let lanes = lanes.clone();
        let selected_lane_id = selected_lane_id.clone();
        let error = error.clone();
        Callback::from(move |e: Event| {
            let Some(lane_id) = *selected_lane_id else {
                return;
            };
            let Some(current) = lanes.iter().find(|l| l.id == lane_id).cloned() else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let lanes = lanes.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::update_lane(lane_id, current.lane_type.clone(), current.width_meters, value).await {
                    Ok(updated) => {
                        let mut next: Vec<api::LaneResponse> = (*lanes).clone();
                        if let Some(entry) = next.iter_mut().find(|l| l.id == updated.id) {
                            *entry = updated;
                        }
                        lanes.set(next);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let on_insert_lane = {
        let lanes = lanes.clone();
        let selected_cross_section_id = selected_cross_section_id.clone();
        let error = error.clone();
        Callback::from(move |(before, after): (Option<f64>, Option<f64>)| {
            let Some(cross_section_id) = *selected_cross_section_id else {
                return;
            };
            let lanes = lanes.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::insert_lane(cross_section_id, "travel".to_string(), 3.0, "forward".to_string(), before, after).await {
                    Ok(_) => match api::list_lanes(cross_section_id).await {
                        Ok(fetched) => lanes.set(fetched),
                        Err(e) => error.set(Some(e)),
                    },
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let on_remove_lane = {
        let lanes = lanes.clone();
        let selected_lane_id = selected_lane_id.clone();
        let error = error.clone();
        Callback::from(move |lane_id: i64| {
            let lanes = lanes.clone();
            let selected_lane_id = selected_lane_id.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::delete_lane(lane_id).await {
                    Ok(()) => {
                        let next: Vec<api::LaneResponse> =
                            lanes.iter().filter(|l| l.id != lane_id).cloned().collect();
                        lanes.set(next);
                        selected_lane_id.set(None);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    // Every access-rule control (add/remove a rule, edit one of its fields)
    // rebuilds the lane's full rule list client-side and immediately persists
    // it via `set_access_rules` -- access rules have no per-rule `id` in the
    // domain model, so whole-list replace (matching the repository/API
    // layer's own shape) is simpler than tracking per-rule identity in the UI.
    let persist_access_rules = {
        let lanes = lanes.clone();
        let error = error.clone();
        Callback::from(move |(lane_id, rules): (i64, Vec<api::AccessRuleValue>)| {
            // An edited `days` field of "" means "always active" -- normalize
            // back to `time_window: None` before sending, rather than sending
            // a half-filled time window.
            let normalized: Vec<api::AccessRuleValue> = rules
                .into_iter()
                .map(|rule| api::AccessRuleValue {
                    time_window: rule.time_window.filter(|w| !w.days.trim().is_empty()),
                    allowed_modes: rule.allowed_modes,
                })
                .collect();
            let lanes = lanes.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::set_access_rules(lane_id, normalized).await {
                    Ok(updated_rules) => {
                        let mut next: Vec<api::LaneResponse> = (*lanes).clone();
                        if let Some(entry) = next.iter_mut().find(|l| l.id == lane_id) {
                            entry.access_rules = updated_rules;
                        }
                        lanes.set(next);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
        })
    };

    let on_add_time_window = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |_: MouseEvent| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let mut rules = lane.access_rules.clone();
            rules.push(api::AccessRuleValue {
                time_window: Some(api::TimeWindowValue {
                    days: "weekdays".to_string(),
                    start_time: "07:00".to_string(),
                    end_time: "09:00".to_string(),
                }),
                allowed_modes: vec![],
            });
            persist_access_rules.emit((lane.id, rules));
        })
    };

    let on_remove_access_rule = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |rule_index: usize| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let rules: Vec<api::AccessRuleValue> = lane
                .access_rules
                .iter()
                .enumerate()
                .filter(|(i, _)| *i != rule_index)
                .map(|(_, r)| r.clone())
                .collect();
            persist_access_rules.emit((lane.id, rules));
        })
    };

    let on_rule_days_blur = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let mut rules = lane.access_rules.clone();
            if let Some(rule) = rules.get_mut(rule_index) {
                let (_, start, end) = rule
                    .time_window
                    .clone()
                    .map(|w| (w.days, w.start_time, w.end_time))
                    .unwrap_or_default();
                rule.time_window = Some(api::TimeWindowValue { days: value, start_time: start, end_time: end });
            }
            persist_access_rules.emit((lane.id, rules));
        })
    };

    let on_rule_start_time_blur = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let mut rules = lane.access_rules.clone();
            if let Some(rule) = rules.get_mut(rule_index) {
                let (days, _, end) = rule
                    .time_window
                    .clone()
                    .map(|w| (w.days, w.start_time, w.end_time))
                    .unwrap_or_default();
                rule.time_window = Some(api::TimeWindowValue { days, start_time: value, end_time: end });
            }
            persist_access_rules.emit((lane.id, rules));
        })
    };

    let on_rule_end_time_blur = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let mut rules = lane.access_rules.clone();
            if let Some(rule) = rules.get_mut(rule_index) {
                let (days, start, _) = rule
                    .time_window
                    .clone()
                    .map(|w| (w.days, w.start_time, w.end_time))
                    .unwrap_or_default();
                rule.time_window = Some(api::TimeWindowValue { days, start_time: start, end_time: value });
            }
            persist_access_rules.emit((lane.id, rules));
        })
    };

    let on_rule_modes_blur = {
        let selected_lane = selected_lane.clone();
        let persist_access_rules = persist_access_rules.clone();
        Callback::from(move |(rule_index, value): (usize, String)| {
            let Some(lane) = &selected_lane else {
                return;
            };
            let mut rules = lane.access_rules.clone();
            if let Some(rule) = rules.get_mut(rule_index) {
                rule.allowed_modes = value
                    .split(',')
                    .map(|s| s.trim().to_string())
                    .filter(|s| !s.is_empty())
                    .collect();
            }
            persist_access_rules.emit((lane.id, rules));
        })
    };

    html! {
        <div class="builder-region-map">
            <div id="corridor-map" style="width: 100%; height: 100vh;"></div>
            <div class="setup-card" style="position:absolute; top:16px; right:16px; z-index:10; width:360px; max-height: calc(100vh - 32px); overflow-y: auto;">
                if let Some(err) = &*error {
                    <div class="alert alert--err">{ err }</div>
                }
                if let Some(cs) = &selected_cross_section {
                    <label class="field-label" for="cross-section-label">{ "Cross-section label" }</label>
                    <input class="field" id="cross-section-label" type="text" value={cs.label.clone().unwrap_or_default()} onblur={on_label_blur} />

                    <div class="xs-diagram" style="margin-top:1rem;">
                        { insert_button("Add lane at start", None, lanes.first().map(|l| l.position), on_insert_lane.clone()) }
                        { for lanes.iter().enumerate().map(|(i, lane)| {
                            let lane_id = lane.id;
                            let next_position = lanes.get(i + 1).map(|l| l.position);
                            let onclick = {
                                let selected_lane_id = selected_lane_id.clone();
                                Callback::from(move |_: MouseEvent| selected_lane_id.set(Some(lane_id)))
                            };
                            html! {
                                <>
                                    <div
                                        class="xs-lane"
                                        onclick={onclick}
                                        style={format!("flex: {} 0 auto; background:{};", lane.width_meters, lane_color(&lane.lane_type))}
                                    >
                                        { lane_type_label(&lane.lane_type) }
                                        <span class="w">{ format!("{}m", lane.width_meters) }</span>
                                    </div>
                                    { insert_button(&format!("Add lane after {}", lane_type_label(&lane.lane_type)), Some(lane.position), next_position, on_insert_lane.clone()) }
                                </>
                            }
                        }) }
                    </div>

                    if let Some(lane) = &selected_lane {
                        <div style="margin-top:1rem;">
                            <label class="field-label" for="lane-width">{ "Width (meters)" }</label>
                            <input class="field" id="lane-width" type="text" value={lane.width_meters.to_string()} onblur={on_width_blur} />

                            <label class="field-label" for="lane-type" style="margin-top:0.75rem;">{ "Lane type" }</label>
                            <select class="field" id="lane-type" onchange={on_type_change}>
                                { for LANE_TYPES.iter().map(|(value, label)| html! {
                                    <option value={*value} selected={lane.lane_type == *value}>{ *label }</option>
                                }) }
                            </select>

                            <label class="field-label" for="lane-direction" style="margin-top:0.75rem;">{ "Direction" }</label>
                            <select class="field" id="lane-direction" onchange={on_direction_change}>
                                { for LANE_DIRECTIONS.iter().map(|(value, label)| html! {
                                    <option value={*value} selected={lane.direction == *value}>{ *label }</option>
                                }) }
                            </select>

                            <p class="field-label" style="margin-top:0.75rem;">{ "Access rules" }</p>
                            { for lane.access_rules.iter().enumerate().map(|(i, rule)| {
                                let days = rule.time_window.as_ref().map(|w| w.days.clone()).unwrap_or_default();
                                let start_time = rule.time_window.as_ref().map(|w| w.start_time.clone()).unwrap_or_default();
                                let end_time = rule.time_window.as_ref().map(|w| w.end_time.clone()).unwrap_or_default();
                                let modes = rule.allowed_modes.join(",");

                                let days_onblur = { let f = on_rule_days_blur.clone(); on_field_blur(move |v| f.emit((i, v))) };
                                let start_onblur = { let f = on_rule_start_time_blur.clone(); on_field_blur(move |v| f.emit((i, v))) };
                                let end_onblur = { let f = on_rule_end_time_blur.clone(); on_field_blur(move |v| f.emit((i, v))) };
                                let modes_onblur = { let f = on_rule_modes_blur.clone(); on_field_blur(move |v| f.emit((i, v))) };
                                let remove_onclick = {
                                    let on_remove_access_rule = on_remove_access_rule.clone();
                                    Callback::from(move |_: MouseEvent| on_remove_access_rule.emit(i))
                                };

                                html! {
                                    <div class="access-rule">
                                        <input class="field" aria-label="Days" placeholder="days (blank = always)" value={days} onblur={days_onblur} />
                                        <input class="field" aria-label="Start time" placeholder="HH:MM" value={start_time} onblur={start_onblur} />
                                        <input class="field" aria-label="End time" placeholder="HH:MM" value={end_time} onblur={end_onblur} />
                                        <input class="field" aria-label="Allowed modes" placeholder="car,transit,..." value={modes} onblur={modes_onblur} />
                                        <button class="btn" aria-label="Remove access rule" onclick={remove_onclick}>{ "✕" }</button>
                                    </div>
                                }
                            }) }
                            <button class="btn" onclick={on_add_time_window}>{ "+ Add time window" }</button>

                            <div style="margin-top:0.75rem;">
                                <button class="btn" aria-label="Remove lane" onclick={{
                                    let lane_id = lane.id;
                                    let on_remove_lane = on_remove_lane.clone();
                                    Callback::from(move |_: MouseEvent| on_remove_lane.emit(lane_id))
                                }}>{ "Remove lane" }</button>
                            </div>
                        </div>
                    }
                } else {
                    <p>{ "Click a point on the map to select a cross-section." }</p>
                }
                <div style="margin-top:1rem;">
                    <Link<Route> classes="chip" to={Route::RegionMap { remix_id }}>{ "Back to map" }</Link<Route>>
                </div>
            </div>
        </div>
    }
}

const LANE_TYPES: &[(&str, &str)] = &[
    ("travel", "Travel"),
    ("turn", "Turn"),
    ("transit", "Transit"),
    ("queue_jump", "Queue Jump"),
    ("cycle_lane", "Cycle Lane"),
    ("cycle_track", "Cycle Track"),
    ("parking", "Parking"),
    ("sidewalk", "Sidewalk"),
    ("median", "Median"),
    ("buffer", "Buffer"),
];

const LANE_DIRECTIONS: &[(&str, &str)] = &[
    ("forward", "Forward"),
    ("backward", "Backward"),
    ("both", "Both"),
    ("none", "None"),
];

fn lane_type_label(lane_type: &str) -> &'static str {
    LANE_TYPES
        .iter()
        .find(|(value, _)| *value == lane_type)
        .map(|(_, label)| *label)
        .unwrap_or("Lane")
}

/// Colors match the cross-section diagram mockup approved during the original
/// corridor-design brainstorming (Travel/Sidewalk/Parking/CycleLane/Median);
/// the remaining five types use the Lumina design system's existing palette
/// (`DESIGN.md`) rather than inventing new colors.
fn lane_color(lane_type: &str) -> &'static str {
    match lane_type {
        "travel" => "#1D4E89",       // oxford-500
        "turn" => "#163A67",         // oxford-600
        "transit" => "#C8463A",      // cinnabar-500
        "queue_jump" => "#A83530",   // cinnabar-600
        "cycle_lane" => "#3D9A6B",   // sage
        "cycle_track" => "#2E7A54",  // sage, darker
        "parking" => "#6b6b8f",
        "sidewalk" => "#9a9a9a",
        "median" => "#C8A050",
        "buffer" => "#C8C4BC",       // cream-400
        _ => "#888480",
    }
}

/// Renders one "+" gap-insert control. `before`/`after` are the flanking
/// lanes' positions (`None` at either end of the sequence) -- passed straight
/// through to `insert_lane`, which resolves them via `assign_position` on the
/// server.
fn insert_button(
    label: &str,
    before: Option<f64>,
    after: Option<f64>,
    on_insert_lane: Callback<(Option<f64>, Option<f64>)>,
) -> Html {
    let onclick = Callback::from(move |_: MouseEvent| on_insert_lane.emit((before, after)));
    html! {
        <button class="xs-add" aria-label={label.to_string()} onclick={onclick}>{ "+" }</button>
    }
}

/// Wraps a `String -> ()` closure as an `onblur` handler that reads the
/// blurred `<input>`'s value. Used for the access-rule fields, where every
/// field shares the same "read the input, call back with (index, value)"
/// shape.
fn on_field_blur(f: impl Fn(String) + 'static) -> Callback<FocusEvent> {
    Callback::from(move |e: FocusEvent| {
        let value = e
            .target_dyn_into::<web_sys::HtmlInputElement>()
            .map(|el| el.value())
            .unwrap_or_default();
        f(value);
    })
}

fn extract_clicked_cross_section_id(map: &Map, event: &JsValue) -> Option<i64> {
    let point = js_sys::Reflect::get(event, &"point".into()).ok()?;
    let options = js_sys::Object::new();
    let layers = js_sys::Array::of1(&"cross-section-points".into());
    js_sys::Reflect::set(&options, &"layers".into(), &layers).ok()?;

    let features = map.query_rendered_features(&point, &options);
    if features.length() == 0 {
        return None;
    }
    let feature = features.get(0);
    let properties = js_sys::Reflect::get(&feature, &"properties".into()).ok()?;
    js_sys::Reflect::get(&properties, &"cross_section_id".into())
        .ok()
        .and_then(|v| v.as_f64())
        .map(|v| v as i64)
}

fn render_cross_sections_layer(map: &Map, cross_sections: &[api::CrossSectionSummary]) {
    let features: Vec<serde_json::Value> = cross_sections
        .iter()
        .map(|cs| {
            serde_json::json!({
                "type": "Feature",
                "properties": { "cross_section_id": cs.id },
                "geometry": { "type": "Point", "coordinates": [cs.lon, cs.lat] },
            })
        })
        .collect();
    let collection = serde_json::json!({ "type": "FeatureCollection", "features": features });

    if let Ok(source) = to_js_value(&serde_json::json!({ "type": "geojson", "data": collection })) {
        map.add_source("cross-section-points", &source);
    }
    if let Ok(layer) = to_js_value(&serde_json::json!({
        "id": "cross-section-points",
        "type": "circle",
        "source": "cross-section-points",
        "paint": { "circle-radius": 8, "circle-color": "#C8463A" }
    })) {
        map.add_layer(&layer);
    }
}

fn to_js_value<T: serde::Serialize>(value: &T) -> Result<JsValue, String> {
    let json = serde_json::to_string(value).map_err(|e| e.to_string())?;
    js_sys::JSON::parse(&json).map_err(|e| format!("{e:?}"))
}

fn osm_raster_style() -> serde_json::Value {
    serde_json::json!({
        "version": 8,
        "sources": {
            "osm": {
                "type": "raster",
                "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                "tileSize": 256,
                "attribution": "© OpenStreetMap contributors"
            }
        },
        "layers": [
            { "id": "osm-tiles", "type": "raster", "source": "osm" }
        ]
    })
}
```

- [ ] **Step 4: Build and verify against the E2E spec**

```bash
cd crates/corridor_builder_web && cargo fmt --check && cargo clippy --target wasm32-unknown-unknown -- -D warnings && trunk build && cd ../..
```

Expected: all clean.

With Postgres running and `mobilispect-pg` up:

```bash
export MOBILISPECT_DATABASE_URL=postgres://mobilispect:mobilispect@localhost:5433/mobilispect
dotenvx run -- cargo run --bin mobilispect-server > /tmp/mobilispect-server.log 2>&1 &
cd e2e && npx playwright test builder-lane-editor --project=chromium
```

Expected: all 5 tests in `builder-lane-editor.spec.ts` pass. Also re-run the full `builder-*.spec.ts` suite to confirm no regressions: `npx playwright test builder- --project=chromium`.

- [ ] **Step 5: Commit**

```bash
git add crates/corridor_builder_web/src crates/corridor_builder_web/index.html
git commit -m "feat(corridor-design): replace corridor placeholder with the full lane editor"
```

---

## Task 6: Full verification pass

**Files:** none (verification only).

- [ ] **Step 1: Rust workspace**

```bash
cargo build --workspace
cargo clippy -p mobilispect-core -p mobilispect-server --all-targets -- -D warnings
cargo fmt --all -- --check
DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core -p mobilispect-server --no-fail-fast
```

Expected: all succeed cleanly on the crates this plan touches. `add_cross_section_*`/`reorder_cross_sections_*` (repository.rs) remain failing (`unimplemented!()`) — confirmed out of scope, a separate future slice (REQ-004/005). Unscoped `cargo clippy --workspace`/`cargo nextest run --workspace` will still show pre-existing, unrelated failures in `crates/worker/` — also out of scope.

- [ ] **Step 2: WASM crate**

```bash
cd crates/corridor_builder_web
cargo fmt --check
cargo clippy --target wasm32-unknown-unknown -- -D warnings
trunk build
```

Expected: all clean.

- [ ] **Step 3: Full E2E suite**

```bash
cd crates/corridor_builder_web && trunk build && cd ../..
export MOBILISPECT_DATABASE_URL=postgres://mobilispect:mobilispect@localhost:5433/mobilispect
export OVERPASS_BASE_URL=http://localhost:19999
dotenvx run -- cargo run --bin mobilispect-server &
cd e2e && npx playwright test
```

Expected: all `builder-*.spec.ts` files pass across chromium/firefox/webkit, including `builder-lane-editor.spec.ts`.

- [ ] **Step 4: Scope check**

```bash
git diff $(git merge-base main HEAD) HEAD --stat
```

Confirm the file list matches this plan's tasks: `crates/core/src/corridor_design/{mod,repository,edit,position}.rs`, `crates/server/src/web/{lane_editor_api,mod}.rs`, `e2e/tests/builder-lane-editor.spec.ts`, `crates/corridor_builder_web/{index.html,src/pages/corridor.rs,src/api.rs}`, plus this plan's own design-spec/plan documents — and nothing unexpected.

No commit for this task — verification only. If anything fails, fix it in the relevant earlier task's files and re-run.

---

## Summary

After all 6 tasks: an analyst can open any corridor (created via manual trace or OSM import), click a cross-section on the mini-map, view and correct its descriptive label, and edit its lane arrangement as a to-scale diagram — inserting a lane in any gap, removing a lane, changing a lane's type/width/direction, and editing its time-windowed access rules — with every change saved immediately. Cross-section add/reorder (REQ-004/005) and intersection treatments (bus stops, etc.) remain separate, already-identified future slices.

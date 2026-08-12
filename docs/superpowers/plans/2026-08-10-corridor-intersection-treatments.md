# Corridor Intersection Treatments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `/builder/remix/:remix_id/intersection/:cross_section_id` placeholder page with a real form for bus-gate control and turn-conflict classification, and add a bus-stop platform select to the existing lane editor's cross-section side panel.

**Architecture:** Follows this codebase's established Corridor Design vertical-slice shape exactly: a new pure-domain-types module (`intersection.rs`, mirroring `lanes.rs`), new repository functions in the existing `repository.rs` (mirroring the lane CRUD functions added by the lane-editor plan), new JSON endpoints in the existing `lane_editor_api.rs`, and two WASM UI changes — `IntersectionPage` becomes a real form, and `CorridorPage`'s side panel gains one more select. `bus_gate`/`turn_conflict` live in a new one-row-per-treated-cross-section `intersection_treatments` table (most cross-sections never have one); `bus_stop` is a single nullable column directly on `cross_sections` (not worth a whole table). Both are whole-record replace on write, matching this codebase's existing `set_lane_access_rules` precedent — no optimistic-concurrency version check, since (unlike the label/lane-fields editing this plan builds on) nothing here has a documented same-tick multi-field edit race to guard against: two native `<select>` "change" events can never land in the same browser task the way a text-input blur and a button click can.

**Tech Stack:** Same as the rest of this codebase — Rust/Axum/sqlx (compile-time-checked queries) on the server, Yew/wasm-bindgen/MapLibre on the WASM frontend, Playwright for E2E.

## Global Constraints

- Migration file: `crates/core/migrations/027_intersection_treatments.sql` (next sequential number after 026). Do not renumber or edit any existing migration file.
- Schema exactly as specified in `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`'s "Data Model / Migrations" section for `intersection_treatments` and `cross_sections.bus_stop` (reproduced verbatim in Task 2 below).
- No mocks in tests — integration tests use real Postgres via `testcontainers` (`crate::db::test_utils::setup()`), per `.claude/rules/testing.md`.
- Functional Core / Imperative Shell: `intersection.rs`'s enums/struct and their `as_db_str`/`from_db_str` conversions are pure, no I/O. All I/O lives in `repository.rs` and the Axum handlers.
- sqlx queries must be compile-time checked (`query!`/`query_as!`), backed by the committed `.sqlx/` offline cache (this repo sets `SQLX_OFFLINE = "true"` in `.cargo/config.toml`, so a stale cache breaks `cargo build` for everyone, not just at CI).
- ID newtypes: no new ID type is needed. `intersection_treatments.cross_section_id` is both primary key and foreign key — the existing `CrossSectionId` (from `crates/core/src/ids.rs`) is reused, exactly as the design doc's schema implies (no `id BIGINT GENERATED ALWAYS AS IDENTITY` column on that table).
- Askama templates are not used here — this whole feature is server JSON API + WASM (Yew) UI, matching the rest of the corridor builder.
- UI classes/colors: reuse this codebase's existing `.field`, `.field-label`, `.setup-card`, `.setup-wrap`, `.alert alert--err`, `.chip` classes exactly as `corridor.rs`/`intersection.rs` already use them today — do not invent new classes or inline colors outside `DESIGN.md`'s tokens.
- Do not touch `crates/core/src/corridor_design/{position,geometry,attribution,lanes}.rs`, `crates/corridor_builder_web/src/pages/{region_map,import_osm,manual_trace,landing}.rs`, or anything under `crates/worker/` — out of scope for this plan.

---

## Task 1: Intersection domain types (`intersection.rs`)

**Files:**
- Create: `crates/core/src/corridor_design/intersection.rs`
- Modify: `crates/core/src/corridor_design/mod.rs` (add `pub mod intersection;`)

**Interfaces:**
- Consumes: `crate::ids::CrossSectionId` (existing).
- Produces: `intersection::{BusGate, TurnConflict, BusStop, IntersectionTreatment}` — Task 2's repository functions and Task 3's JSON API both consume these. `BusGate`/`TurnConflict`/`BusStop` each expose `as_db_str(self) -> &'static str` and `from_db_str(s: &str) -> Option<Self>`, mirroring `lanes.rs`'s `LaneType`/`LaneDirection`/`AccessMode` exactly.

- [ ] **Step 1: Write the failing tests**

Create `crates/core/src/corridor_design/intersection.rs` with just the test module first:

```rust
//! Intersection treatment domain types: an endpoint cross-section may carry an
//! optional bus-gate control and turn-conflict classification; any
//! cross-section may carry an optional bus-stop platform type. See
//! `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`'s
//! "Intersection Treatments" section.

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bus_gate_db_str_round_trips_all_variants() {
        for gate in [BusGate::SignalControlled, BusGate::YieldControlled] {
            assert_eq!(BusGate::from_db_str(gate.as_db_str()), Some(gate));
        }
    }

    #[test]
    fn bus_gate_from_db_str_rejects_unknown_value() {
        assert_eq!(BusGate::from_db_str("bogus"), None);
    }

    #[test]
    fn turn_conflict_db_str_round_trips_all_variants() {
        for conflict in [
            TurnConflict::IndirectLeftViaAlternative,
            TurnConflict::IndirectLeftWithinIntersection,
            TurnConflict::RightInRightOut,
            TurnConflict::DeadEndLateralStreet,
        ] {
            assert_eq!(TurnConflict::from_db_str(conflict.as_db_str()), Some(conflict));
        }
    }

    #[test]
    fn turn_conflict_from_db_str_rejects_unknown_value() {
        assert_eq!(TurnConflict::from_db_str("bogus"), None);
    }

    #[test]
    fn bus_stop_db_str_round_trips_all_variants() {
        for stop in [BusStop::BusBulb, BusStop::SignalProtectedPlatform] {
            assert_eq!(BusStop::from_db_str(stop.as_db_str()), Some(stop));
        }
    }

    #[test]
    fn bus_stop_from_db_str_rejects_unknown_value() {
        assert_eq!(BusStop::from_db_str("bogus"), None);
    }

    #[test]
    fn intersection_treatment_carries_cross_section_id_and_both_optional_fields() {
        let treatment = IntersectionTreatment {
            cross_section_id: crate::ids::CrossSectionId::from(42),
            bus_gate: Some(BusGate::SignalControlled),
            turn_conflict: None,
        };
        assert_eq!(treatment.cross_section_id, crate::ids::CrossSectionId::from(42));
        assert_eq!(treatment.bus_gate, Some(BusGate::SignalControlled));
        assert_eq!(treatment.turn_conflict, None);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cargo test -p mobilispect-core corridor_design::intersection:: 2>&1 | tail -20`

Expected: FAIL to compile — `BusGate`, `TurnConflict`, `BusStop`, `IntersectionTreatment` are not defined yet.

- [ ] **Step 3: Implement the domain types**

Add above the `#[cfg(test)]` block in `crates/core/src/corridor_design/intersection.rs`:

```rust
use crate::ids::CrossSectionId;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BusGate {
    SignalControlled,
    YieldControlled,
}

impl BusGate {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            BusGate::SignalControlled => "signal_controlled",
            BusGate::YieldControlled => "yield_controlled",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "signal_controlled" => Some(BusGate::SignalControlled),
            "yield_controlled" => Some(BusGate::YieldControlled),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TurnConflict {
    IndirectLeftViaAlternative,
    IndirectLeftWithinIntersection,
    RightInRightOut,
    DeadEndLateralStreet,
}

impl TurnConflict {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            TurnConflict::IndirectLeftViaAlternative => "indirect_left_via_alternative",
            TurnConflict::IndirectLeftWithinIntersection => "indirect_left_within_intersection",
            TurnConflict::RightInRightOut => "right_in_right_out",
            TurnConflict::DeadEndLateralStreet => "dead_end_lateral_street",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "indirect_left_via_alternative" => Some(TurnConflict::IndirectLeftViaAlternative),
            "indirect_left_within_intersection" => {
                Some(TurnConflict::IndirectLeftWithinIntersection)
            }
            "right_in_right_out" => Some(TurnConflict::RightInRightOut),
            "dead_end_lateral_street" => Some(TurnConflict::DeadEndLateralStreet),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BusStop {
    BusBulb,
    SignalProtectedPlatform,
}

impl BusStop {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            BusStop::BusBulb => "bus_bulb",
            BusStop::SignalProtectedPlatform => "signal_protected_platform",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "bus_bulb" => Some(BusStop::BusBulb),
            "signal_protected_platform" => Some(BusStop::SignalProtectedPlatform),
            _ => None,
        }
    }
}

/// A persisted intersection treatment, as returned from the repository. Every
/// field but `cross_section_id` is optional -- both are independently
/// clearable via the intersection editor's "None" option, and a
/// cross-section with no treatment configured yet has no row at all (see
/// `repository::get_intersection_treatment`, which synthesizes an all-`None`
/// value in that case rather than requiring a row to exist first).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct IntersectionTreatment {
    pub cross_section_id: CrossSectionId,
    pub bus_gate: Option<BusGate>,
    pub turn_conflict: Option<TurnConflict>,
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cargo test -p mobilispect-core corridor_design::intersection::`

Expected: PASS, 7 tests.

- [ ] **Step 5: Wire the module into `mod.rs`**

In `crates/core/src/corridor_design/mod.rs`, change:

```rust
pub mod attribution;
pub mod edit;
pub mod geometry;
pub mod lanes;
pub mod lanes_from_osm;
pub mod position;
pub mod repository;
```

to:

```rust
pub mod attribution;
pub mod edit;
pub mod geometry;
pub mod intersection;
pub mod lanes;
pub mod lanes_from_osm;
pub mod position;
pub mod repository;
```

- [ ] **Step 6: Confirm the crate still builds**

Run: `cargo build -p mobilispect-core`

Expected: clean build.

- [ ] **Step 7: Commit**

```bash
git add crates/core/src/corridor_design/intersection.rs crates/core/src/corridor_design/mod.rs
git commit -m "feat(corridor-design): add intersection treatment domain types"
```

---

## Task 2: Migration 027 + `CrossSection.bus_stop` + repository functions

**Files:**
- Create: `crates/core/migrations/027_intersection_treatments.sql`
- Modify: `crates/core/src/corridor_design/mod.rs` (add `bus_stop` field to `CrossSection`)
- Modify: `crates/core/src/corridor_design/edit.rs` (update the `make_cross_section` test helper)
- Modify: `crates/core/src/corridor_design/repository.rs` (extend 3 existing functions, add 3 new ones)

**Interfaces:**
- Consumes: `intersection::{BusGate, TurnConflict, BusStop, IntersectionTreatment}` (Task 1).
- Produces:
  - `CrossSection.bus_stop: Option<intersection::BusStop>` — Task 3's `CrossSectionResponse` reads this.
  - `repository::get_intersection_treatment(pool, cross_section_id) -> Result<IntersectionTreatment, anyhow::Error>`
  - `repository::set_intersection_treatment(pool, cross_section_id, bus_gate: Option<BusGate>, turn_conflict: Option<TurnConflict>) -> Result<IntersectionTreatment, anyhow::Error>`
  - `repository::update_cross_section_bus_stop(pool, cross_section_id, bus_stop: Option<BusStop>) -> Result<CrossSection, anyhow::Error>`
  All three are consumed by Task 3's JSON API handlers.

- [ ] **Step 1: Write the migration**

Create `crates/core/migrations/027_intersection_treatments.sql`:

```sql
-- migrations/027_intersection_treatments.sql
-- Corridor Segment Editor: intersection treatments (bus gate, turn-conflict
-- type) and a cross-section's optional bus-stop platform type. See
-- docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md's
-- "Intersection Treatments" section.

ALTER TABLE cross_sections
    ADD COLUMN bus_stop TEXT CHECK (bus_stop IN ('bus_bulb', 'signal_protected_platform'));

CREATE TABLE intersection_treatments (
    cross_section_id  BIGINT PRIMARY KEY REFERENCES cross_sections(id) ON DELETE CASCADE,
    bus_gate          TEXT CHECK (bus_gate IN ('signal_controlled', 'yield_controlled')),
    turn_conflict     TEXT CHECK (turn_conflict IN (
                          'indirect_left_via_alternative', 'indirect_left_within_intersection',
                          'right_in_right_out', 'dead_end_lateral_street'
                      ))
);
```

- [ ] **Step 2: Add `bus_stop` to the `CrossSection` struct**

In `crates/core/src/corridor_design/mod.rs`, change:

```rust
/// A persisted cross-section, as returned from the repository.
#[derive(Debug, Clone, PartialEq, sqlx::FromRow)]
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
    /// Optimistic-concurrency counter (migration 024's `cross_sections.version`
    /// column, `DEFAULT 1`). Bumped on every successful `update_cross_section_label`
    /// call.
    pub version: i32,
}
```

to:

```rust
/// A persisted cross-section, as returned from the repository.
#[derive(Debug, Clone, PartialEq, sqlx::FromRow)]
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
    /// Optimistic-concurrency counter (migration 024's `cross_sections.version`
    /// column, `DEFAULT 1`). Bumped on every successful `update_cross_section_label`
    /// call.
    pub version: i32,
    /// Bus-stop platform type, editable from the lane editor's side panel
    /// (migration 027's `cross_sections.bus_stop` column). `None` until an
    /// analyst sets one.
    pub bus_stop: Option<intersection::BusStop>,
}
```

- [ ] **Step 3: Update the `edit.rs` test helper**

In `crates/core/src/corridor_design/edit.rs`, `make_cross_section` currently ends with:

```rust
            label: Some(label.to_string()),
            version: 1,
        }
    }
```

Change to:

```rust
            label: Some(label.to_string()),
            version: 1,
            bus_stop: None,
        }
    }
```

- [ ] **Step 4: Run the crate to verify it now fails to compile for the right reason**

Run: `cargo build -p mobilispect-core 2>&1 | tail -40`

Expected: FAIL — `repository.rs`'s three existing `CrossSection { ... }` construction sites (`get_corridor_cross_sections`'s map closure, `add_cross_section`, `update_cross_section_label`) are missing the new `bus_stop` field (`E0063: missing field 'bus_stop'`).

- [ ] **Step 5: Write the failing repository tests**

Add to `crates/core/src/corridor_design/repository.rs`'s `#[cfg(test)] mod tests` block, after the existing `update_cross_section_label`-related tests (search for the end of that section — the last test using `update_cross_section_label` before the file's closing `}`):

```rust
    // --- Intersection treatments (bus gate, turn conflict, bus stop) ---

    use crate::corridor_design::intersection::{BusGate, BusStop, IntersectionTreatment, TurnConflict};

    /// Seeds a corridor with a single bare cross-section (no lanes), returning
    /// its id. Mirrors `seed_bare_cross_section` used elsewhere in this test
    /// module for the lane tests, scoped down to what the intersection tests
    /// need: just one cross-section to attach a treatment / bus stop to.
    async fn seed_cross_section_for_intersection_tests(pool: &sqlx::PgPool) -> CrossSectionId {
        let remix_id = seed_remix(pool).await;
        let corridor_id: i64 = sqlx::query_scalar(
            "INSERT INTO corridors (name, geometry_source, remix_id) VALUES ($1, 'manual', $2) RETURNING id",
        )
        .bind("Intersection Test Corridor")
        .bind(remix_id.as_i64())
        .fetch_one(pool)
        .await
        .unwrap();
        let cross_section_id: i64 = sqlx::query_scalar(
            "INSERT INTO cross_sections (corridor_id, position, lat, lon) VALUES ($1, 0, 45.50, -73.60) RETURNING id",
        )
        .bind(corridor_id)
        .fetch_one(pool)
        .await
        .unwrap();
        CrossSectionId::from(cross_section_id)
    }

    #[tokio::test]
    async fn get_intersection_treatment_returns_all_none_when_no_row_exists() {
        let td = test_utils::setup().await;
        let db = td.db;
        let cross_section_id = seed_cross_section_for_intersection_tests(&db.pool).await;

        let treatment = get_intersection_treatment(&db.pool, cross_section_id)
            .await
            .expect("get_intersection_treatment should succeed even with no row yet");

        assert_eq!(
            treatment,
            IntersectionTreatment {
                cross_section_id,
                bus_gate: None,
                turn_conflict: None,
            }
        );
    }

    #[tokio::test]
    async fn set_intersection_treatment_persists_both_fields() {
        let td = test_utils::setup().await;
        let db = td.db;
        let cross_section_id = seed_cross_section_for_intersection_tests(&db.pool).await;

        let saved = set_intersection_treatment(
            &db.pool,
            cross_section_id,
            Some(BusGate::SignalControlled),
            Some(TurnConflict::RightInRightOut),
        )
        .await
        .expect("set_intersection_treatment should succeed");

        assert_eq!(saved.bus_gate, Some(BusGate::SignalControlled));
        assert_eq!(saved.turn_conflict, Some(TurnConflict::RightInRightOut));

        let reloaded = get_intersection_treatment(&db.pool, cross_section_id)
            .await
            .expect("get_intersection_treatment should succeed");
        assert_eq!(reloaded, saved);
    }

    #[tokio::test]
    async fn set_intersection_treatment_twice_overwrites_not_appends() {
        let td = test_utils::setup().await;
        let db = td.db;
        let cross_section_id = seed_cross_section_for_intersection_tests(&db.pool).await;

        set_intersection_treatment(
            &db.pool,
            cross_section_id,
            Some(BusGate::SignalControlled),
            Some(TurnConflict::RightInRightOut),
        )
        .await
        .unwrap();

        // Second call clears turn_conflict and changes bus_gate -- an upsert,
        // not an insert that would violate the table's PRIMARY KEY.
        let saved = set_intersection_treatment(
            &db.pool,
            cross_section_id,
            Some(BusGate::YieldControlled),
            None,
        )
        .await
        .expect("set_intersection_treatment should succeed on a second call for the same cross-section");

        assert_eq!(saved.bus_gate, Some(BusGate::YieldControlled));
        assert_eq!(saved.turn_conflict, None);

        let row_count: i64 =
            sqlx::query_scalar("SELECT COUNT(*) FROM intersection_treatments WHERE cross_section_id = $1")
                .bind(cross_section_id.as_i64())
                .fetch_one(&db.pool)
                .await
                .unwrap();
        assert_eq!(row_count, 1, "the second call should overwrite the one row, not add a second");
    }

    #[tokio::test]
    async fn set_intersection_treatment_for_nonexistent_cross_section_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;

        let result = set_intersection_treatment(
            &db.pool,
            CrossSectionId::from(999_999_i64),
            Some(BusGate::SignalControlled),
            None,
        )
        .await;

        assert!(result.is_err());
    }

    #[tokio::test]
    async fn update_cross_section_bus_stop_persists_and_clears() {
        let td = test_utils::setup().await;
        let db = td.db;
        let cross_section_id = seed_cross_section_for_intersection_tests(&db.pool).await;

        let updated = update_cross_section_bus_stop(&db.pool, cross_section_id, Some(BusStop::BusBulb))
            .await
            .expect("update_cross_section_bus_stop should succeed");
        assert_eq!(updated.bus_stop, Some(BusStop::BusBulb));

        let cleared = update_cross_section_bus_stop(&db.pool, cross_section_id, None)
            .await
            .expect("update_cross_section_bus_stop should succeed clearing the value");
        assert_eq!(cleared.bus_stop, None);
    }

    #[tokio::test]
    async fn update_cross_section_bus_stop_for_nonexistent_cross_section_returns_err() {
        let td = test_utils::setup().await;
        let db = td.db;

        let result =
            update_cross_section_bus_stop(&db.pool, CrossSectionId::from(999_999_i64), Some(BusStop::BusBulb))
                .await;

        assert!(result.is_err());
    }

    #[tokio::test]
    async fn get_corridor_cross_sections_includes_bus_stop() {
        let td = test_utils::setup().await;
        let db = td.db;
        let cross_section_id = seed_cross_section_for_intersection_tests(&db.pool).await;
        update_cross_section_bus_stop(&db.pool, cross_section_id, Some(BusStop::SignalProtectedPlatform))
            .await
            .unwrap();

        let corridor_id: i64 = sqlx::query_scalar("SELECT corridor_id FROM cross_sections WHERE id = $1")
            .bind(cross_section_id.as_i64())
            .fetch_one(&db.pool)
            .await
            .unwrap();

        let cross_sections = get_corridor_cross_sections(&db.pool, CorridorId::from(corridor_id))
            .await
            .unwrap();

        assert_eq!(cross_sections.len(), 1);
        assert_eq!(cross_sections[0].bus_stop, Some(BusStop::SignalProtectedPlatform));
    }
```

- [ ] **Step 6: Run the new tests to verify they fail for the right reason**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core corridor_design::repository::tests::get_intersection_treatment corridor_design::repository::tests::set_intersection_treatment corridor_design::repository::tests::update_cross_section_bus_stop corridor_design::repository::tests::get_corridor_cross_sections_includes_bus_stop 2>&1 | tail -40`

Expected: FAIL to compile — `get_intersection_treatment`, `set_intersection_treatment`, `update_cross_section_bus_stop` are not defined yet, and the earlier `CrossSection { ... }` construction sites are still missing `bus_stop`.

- [ ] **Step 7: Extend the three existing `CrossSection`-constructing functions**

In `crates/core/src/corridor_design/repository.rs`, change `get_corridor_cross_sections`:

```rust
/// Fetches all cross-sections for a corridor, ordered by `position`.
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

to:

```rust
/// Fetches all cross-sections for a corridor, ordered by `position`.
pub async fn get_corridor_cross_sections(
    pool: &sqlx::PgPool,
    corridor_id: CorridorId,
) -> Result<Vec<CrossSection>, anyhow::Error> {
    let rows = sqlx::query!(
        r#"SELECT id, corridor_id, position::float8 AS "position!", lat, lon,
                  osm_way_id, osm_node_id, label, version, bus_stop
           FROM cross_sections
           WHERE corridor_id = $1
           ORDER BY position"#,
        corridor_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;

    let mut cross_sections = Vec::with_capacity(rows.len());
    for row in rows {
        let bus_stop = decode_bus_stop(row.bus_stop)?;
        cross_sections.push(CrossSection {
            id: CrossSectionId::from(row.id),
            corridor_id: CorridorId::from(row.corridor_id),
            position: row.position,
            lat: row.lat,
            lon: row.lon,
            osm_way_id: row.osm_way_id,
            osm_node_id: row.osm_node_id,
            label: row.label,
            version: row.version,
            bus_stop,
        });
    }
    Ok(cross_sections)
}

/// Shared by every function in this file that reads `cross_sections.bus_stop`
/// -- decodes the raw `TEXT` column into the domain enum, or `None` when the
/// column is `NULL` (no bus stop configured yet, the common case).
fn decode_bus_stop(
    raw: Option<String>,
) -> Result<Option<crate::corridor_design::intersection::BusStop>, anyhow::Error> {
    raw.map(|s| {
        crate::corridor_design::intersection::BusStop::from_db_str(&s)
            .ok_or_else(|| anyhow::anyhow!("unknown bus_stop value: {s}"))
    })
    .transpose()
}
```

Change `add_cross_section`'s final `Ok(CrossSection { ... })`:

```rust
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
    })
}
```

to:

```rust
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
        bus_stop: None,
    })
}
```

Change `update_cross_section_label`:

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

to:

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
                     osm_way_id, osm_node_id, label, version, bus_stop"#,
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
    let bus_stop = decode_bus_stop(row.bus_stop)?;

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
        bus_stop,
    })
}
```

- [ ] **Step 8: Add the three new repository functions**

Add to `crates/core/src/corridor_design/repository.rs`, directly after `update_cross_section_label` and before the `#[cfg(test)]` block:

```rust
/// Fetches an intersection treatment for `cross_section_id`. Returns an
/// all-`None` value (not an error) when no row exists yet -- a cross-section
/// with no bus gate or turn-conflict configured is the common case, not an
/// exceptional one (see `intersection::IntersectionTreatment`'s doc comment).
pub async fn get_intersection_treatment(
    pool: &sqlx::PgPool,
    cross_section_id: CrossSectionId,
) -> Result<crate::corridor_design::intersection::IntersectionTreatment, anyhow::Error> {
    use crate::corridor_design::intersection::{BusGate, IntersectionTreatment, TurnConflict};

    let row = sqlx::query!(
        "SELECT bus_gate, turn_conflict FROM intersection_treatments WHERE cross_section_id = $1",
        cross_section_id.as_i64(),
    )
    .fetch_optional(pool)
    .await?;

    let (bus_gate_str, turn_conflict_str) = match row {
        Some(row) => (row.bus_gate, row.turn_conflict),
        None => (None, None),
    };

    let bus_gate = bus_gate_str
        .map(|s| BusGate::from_db_str(&s).ok_or_else(|| anyhow::anyhow!("unknown bus_gate value: {s}")))
        .transpose()?;
    let turn_conflict = turn_conflict_str
        .map(|s| {
            TurnConflict::from_db_str(&s).ok_or_else(|| anyhow::anyhow!("unknown turn_conflict value: {s}"))
        })
        .transpose()?;

    Ok(IntersectionTreatment {
        cross_section_id,
        bus_gate,
        turn_conflict,
    })
}

/// Upserts `cross_section_id`'s intersection treatment. `bus_gate`/
/// `turn_conflict = None` clears that field (not "leave unchanged") -- the
/// whole row is replaced each call, matching this file's established
/// `set_lane_access_rules` whole-record-replace precedent. Returns an error
/// if `cross_section_id` does not reference an existing cross-section (the
/// table's foreign key would otherwise surface this as an opaque constraint
/// violation).
pub async fn set_intersection_treatment(
    pool: &sqlx::PgPool,
    cross_section_id: CrossSectionId,
    bus_gate: Option<crate::corridor_design::intersection::BusGate>,
    turn_conflict: Option<crate::corridor_design::intersection::TurnConflict>,
) -> Result<crate::corridor_design::intersection::IntersectionTreatment, anyhow::Error> {
    let exists = sqlx::query_scalar!(
        r#"SELECT EXISTS(SELECT 1 FROM cross_sections WHERE id = $1) AS "exists!""#,
        cross_section_id.as_i64(),
    )
    .fetch_one(pool)
    .await?;
    if !exists {
        anyhow::bail!("cross-section {cross_section_id} does not exist");
    }

    let bus_gate_str = bus_gate.map(|g| g.as_db_str());
    let turn_conflict_str = turn_conflict.map(|c| c.as_db_str());

    sqlx::query!(
        r#"INSERT INTO intersection_treatments (cross_section_id, bus_gate, turn_conflict)
           VALUES ($1, $2, $3)
           ON CONFLICT (cross_section_id) DO UPDATE
           SET bus_gate = EXCLUDED.bus_gate, turn_conflict = EXCLUDED.turn_conflict"#,
        cross_section_id.as_i64(),
        bus_gate_str,
        turn_conflict_str,
    )
    .execute(pool)
    .await?;

    Ok(crate::corridor_design::intersection::IntersectionTreatment {
        cross_section_id,
        bus_gate,
        turn_conflict,
    })
}

/// Sets (or clears, with `None`) a cross-section's bus-stop platform type.
/// Unlike `update_cross_section_label`, this has no optimistic-concurrency
/// story -- `bus_stop` is edited from a single dedicated `<select>` with no
/// same-tick concurrent-edit surface the way the label/lane fields have (see
/// this plan's Architecture note). Returns an error if `cross_section_id`
/// does not exist.
pub async fn update_cross_section_bus_stop(
    pool: &sqlx::PgPool,
    cross_section_id: CrossSectionId,
    bus_stop: Option<crate::corridor_design::intersection::BusStop>,
) -> Result<CrossSection, anyhow::Error> {
    let bus_stop_str = bus_stop.map(|b| b.as_db_str());
    let row = sqlx::query!(
        r#"UPDATE cross_sections
           SET bus_stop = $1
           WHERE id = $2
           RETURNING id, corridor_id, position::float8 AS "position!", lat, lon,
                     osm_way_id, osm_node_id, label, version, bus_stop"#,
        bus_stop_str,
        cross_section_id.as_i64(),
    )
    .fetch_optional(pool)
    .await?;

    let row = row.ok_or_else(|| anyhow::anyhow!("cross-section {cross_section_id} not found"))?;
    let bus_stop = decode_bus_stop(row.bus_stop)?;

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
        bus_stop,
    })
}
```

- [ ] **Step 9: Regenerate the sqlx offline query cache**

This step introduces new `sqlx::query!`/`query_scalar!` macros, and this repo's `.cargo/config.toml` sets `SQLX_OFFLINE = "true"` globally — `cargo build` will fail for everyone until the `.sqlx/` cache has entries for them. `mobilispect-pg` (the persistent dev Postgres on port 5433) needs migration 027 applied before `cargo sqlx prepare` can see the new schema.

```bash
docker ps --format '{{.Names}}' | grep -q '^mobilispect-pg$' || docker run -d --name mobilispect-pg -e POSTGRES_USER=mobilispect -e POSTGRES_PASSWORD=mobilispect -e POSTGRES_DB=mobilispect -p 5433:5432 postgres:16
export MOBILISPECT_DATABASE_URL=postgres://mobilispect:mobilispect@localhost:5433/mobilispect
dotenvx run -- cargo run --bin mobilispect-server &
SERVER_PID=$!
sleep 3
kill $SERVER_PID
DATABASE_URL=postgres://mobilispect:mobilispect@localhost:5433/mobilispect cargo sqlx prepare --workspace
```

Expected: the server's brief startup applies migration 027 (via `Database::migrate`, `crates/core/src/db/mod.rs`); `cargo sqlx prepare` then regenerates `.sqlx/*.json`, adding entries for this task's new/changed queries and leaving all other entries untouched.

- [ ] **Step 10: Run the tests to verify they pass**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-core corridor_design::repository::tests --no-fail-fast`

Expected: PASS — every test in `repository.rs`'s test module, including this task's 7 new tests. (`add_cross_section_*`/`reorder_cross_sections_*` tests remain passing as they were before this task — this task does not touch that logic.)

- [ ] **Step 11: Confirm the workspace still builds**

Run: `cargo build --workspace`

Expected: clean build (confirms the regenerated `.sqlx` cache is complete and every crate compiles against the new `CrossSection.bus_stop` field).

- [ ] **Step 12: Commit**

```bash
git add crates/core/migrations/027_intersection_treatments.sql crates/core/src/corridor_design/mod.rs crates/core/src/corridor_design/edit.rs crates/core/src/corridor_design/repository.rs .sqlx
git commit -m "feat(corridor-design): add intersection_treatments table, cross_sections.bus_stop, and their repository functions"
```

---

## Task 3: JSON API — intersection treatment and bus-stop endpoints

**Files:**
- Modify: `crates/server/src/web/lane_editor_api.rs`
- Modify: `crates/server/src/web/mod.rs` (register 2 new routes)

**Interfaces:**
- Consumes: `repository::{get_intersection_treatment, set_intersection_treatment, update_cross_section_bus_stop}` (Task 2), `intersection::{BusGate, TurnConflict, BusStop}` (Task 1).
- Produces:
  - `GET /api/cross-sections/:cross_section_id/intersection-treatment` → `{ bus_gate: Option<String>, turn_conflict: Option<String> }`
  - `PUT /api/cross-sections/:cross_section_id/intersection-treatment` (body: same shape) → same response shape
  - `PATCH /api/cross-sections/:cross_section_id/bus-stop` (body: `{ bus_stop: Option<String> }`) → `CrossSectionResponse` (extended with `bus_stop`)
  These are what Task 5's WASM `api.rs` client functions call.

- [ ] **Step 1: Write the failing tests**

Add to `crates/server/src/web/lane_editor_api.rs`'s `#[cfg(test)] mod tests` block, after the existing `set_access_rules_with_unrecognized_mode_returns_400` test (the module's last test today, just before the closing `}`):

```rust
    // --- Intersection treatments (bus gate, turn conflict, bus stop) ---

    #[tokio::test]
    async fn get_intersection_treatment_returns_all_none_for_untreated_cross_section() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = get_intersection_treatment(State(state), Path(cross_section_id))
            .await
            .unwrap();

        assert_eq!(response.0.bus_gate, None);
        assert_eq!(response.0.turn_conflict, None);
    }

    #[tokio::test]
    async fn set_intersection_treatment_happy_path_persists_and_reads_back() {
        let (state, td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = set_intersection_treatment(
            State(state.clone()),
            Path(cross_section_id),
            Json(SetIntersectionTreatmentRequest {
                bus_gate: Some("signal_controlled".to_string()),
                turn_conflict: Some("right_in_right_out".to_string()),
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0.bus_gate.as_deref(), Some("signal_controlled"));
        assert_eq!(response.0.turn_conflict.as_deref(), Some("right_in_right_out"));

        // Independent read-back, matching this file's `update_lane_happy_path`
        // precedent -- proves the DB actually holds it, not just the handler's
        // echoed response.
        let persisted = repository::get_intersection_treatment(
            &td.db.pool,
            CrossSectionId::from(cross_section_id),
        )
        .await
        .unwrap();
        assert_eq!(
            persisted.bus_gate,
            Some(mobilispect_core::corridor_design::intersection::BusGate::SignalControlled)
        );
    }

    #[tokio::test]
    async fn set_intersection_treatment_with_unrecognized_bus_gate_returns_400() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = set_intersection_treatment(
            State(state),
            Path(cross_section_id),
            Json(SetIntersectionTreatmentRequest {
                bus_gate: Some("spaceship".to_string()),
                turn_conflict: None,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn update_bus_stop_happy_path_persists() {
        let (state, td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = update_bus_stop(
            State(state),
            Path(cross_section_id),
            Json(UpdateBusStopRequest {
                bus_stop: Some("bus_bulb".to_string()),
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0.bus_stop.as_deref(), Some("bus_bulb"));

        let persisted = repository::get_corridor_cross_sections(&td.db.pool, {
            let corridor_id: i64 =
                sqlx::query_scalar("SELECT corridor_id FROM cross_sections WHERE id = $1")
                    .bind(cross_section_id)
                    .fetch_one(&td.db.pool)
                    .await
                    .unwrap();
            CorridorId::from(corridor_id)
        })
        .await
        .unwrap();
        assert_eq!(
            persisted[0].bus_stop,
            Some(mobilispect_core::corridor_design::intersection::BusStop::BusBulb)
        );
    }

    #[tokio::test]
    async fn update_bus_stop_with_unrecognized_value_returns_400() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = update_bus_stop(
            State(state),
            Path(cross_section_id),
            Json(UpdateBusStopRequest {
                bus_stop: Some("spaceship".to_string()),
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn list_cross_sections_includes_bus_stop_field() {
        let (state, _td) = test_state().await;
        let (_remix_id, corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;
        update_bus_stop(
            State(state.clone()),
            Path(cross_section_id),
            Json(UpdateBusStopRequest {
                bus_stop: Some("signal_protected_platform".to_string()),
            }),
        )
        .await
        .unwrap();

        let response = list_cross_sections(State(state), Path(corridor_id))
            .await
            .unwrap();

        assert_eq!(response.0[0].bus_stop.as_deref(), Some("signal_protected_platform"));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-server lane_editor_api::tests 2>&1 | tail -40`

Expected: FAIL to compile — `get_intersection_treatment`, `set_intersection_treatment`, `SetIntersectionTreatmentRequest`, `update_bus_stop`, `UpdateBusStopRequest` are not defined yet, and `CrossSectionResponse` has no `bus_stop` field.

- [ ] **Step 3: Extend `CrossSectionResponse` and its two existing builders**

In `crates/server/src/web/lane_editor_api.rs`, change:

```rust
#[derive(Debug, serde::Serialize)]
pub struct CrossSectionResponse {
    pub id: i64,
    pub position: f64,
    pub label: Option<String>,
    pub lat: f64,
    pub lon: f64,
    pub version: i32,
}
```

to:

```rust
#[derive(Debug, serde::Serialize)]
pub struct CrossSectionResponse {
    pub id: i64,
    pub position: f64,
    pub label: Option<String>,
    pub lat: f64,
    pub lon: f64,
    pub version: i32,
    pub bus_stop: Option<String>,
}
```

In `list_cross_sections`, change:

```rust
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
```

to:

```rust
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
                bus_stop: cs.bus_stop.map(|b| b.as_db_str().to_string()),
            })
            .collect(),
    ))
```

In `update_label`, change its final `Ok(Json(CrossSectionResponse { ... }))`:

```rust
    Ok(Json(CrossSectionResponse {
        id: updated.id.as_i64(),
        position: updated.position,
        label: updated.label,
        lat: updated.lat,
        lon: updated.lon,
        version: updated.version,
    }))
}
```

to:

```rust
    Ok(Json(CrossSectionResponse {
        id: updated.id.as_i64(),
        position: updated.position,
        label: updated.label,
        lat: updated.lat,
        lon: updated.lon,
        version: updated.version,
        bus_stop: updated.bus_stop.map(|b| b.as_db_str().to_string()),
    }))
}
```

- [ ] **Step 4: Add the intersection-treatment and bus-stop imports and handlers**

Change the top-of-file imports:

```rust
use mobilispect_core::corridor_design::edit::validate_label;
use mobilispect_core::corridor_design::lanes::{
    AccessMode, Lane, LaneDirection, LaneType, TimeWindow, TimedAccessRule,
};
use mobilispect_core::corridor_design::position::{Neighbors, assign_position};
use mobilispect_core::corridor_design::repository;
use mobilispect_core::ids::{CorridorId, CrossSectionId, LaneId};
```

to:

```rust
use mobilispect_core::corridor_design::edit::validate_label;
use mobilispect_core::corridor_design::intersection::{BusGate, BusStop, TurnConflict};
use mobilispect_core::corridor_design::lanes::{
    AccessMode, Lane, LaneDirection, LaneType, TimeWindow, TimedAccessRule,
};
use mobilispect_core::corridor_design::position::{Neighbors, assign_position};
use mobilispect_core::corridor_design::repository;
use mobilispect_core::ids::{CorridorId, CrossSectionId, LaneId};
```

Add the following at the end of the file, directly before the `#[cfg(test)]` block:

```rust
// --- Intersection treatments ---

#[derive(Debug, serde::Serialize)]
pub struct IntersectionTreatmentResponse {
    pub bus_gate: Option<String>,
    pub turn_conflict: Option<String>,
}

fn to_intersection_treatment_response(
    treatment: mobilispect_core::corridor_design::intersection::IntersectionTreatment,
) -> IntersectionTreatmentResponse {
    IntersectionTreatmentResponse {
        bus_gate: treatment.bus_gate.map(|g| g.as_db_str().to_string()),
        turn_conflict: treatment.turn_conflict.map(|c| c.as_db_str().to_string()),
    }
}

/// `GET /api/cross-sections/:cross_section_id/intersection-treatment`
pub async fn get_intersection_treatment(
    State(state): State<AppState>,
    Path(cross_section_id): Path<i64>,
) -> Result<Json<IntersectionTreatmentResponse>, ApiError> {
    let treatment = repository::get_intersection_treatment(
        &state.db.pool,
        CrossSectionId::from(cross_section_id),
    )
    .await
    .map_err(|e| internal_error("get_intersection_treatment", e))?;
    Ok(Json(to_intersection_treatment_response(treatment)))
}

fn parse_bus_gate(raw: &Option<String>) -> Result<Option<BusGate>, ApiError> {
    raw.as_deref()
        .map(|s| BusGate::from_db_str(s).ok_or_else(|| bad_request("unrecognized bus_gate")))
        .transpose()
}

fn parse_turn_conflict(raw: &Option<String>) -> Result<Option<TurnConflict>, ApiError> {
    raw.as_deref()
        .map(|s| TurnConflict::from_db_str(s).ok_or_else(|| bad_request("unrecognized turn_conflict")))
        .transpose()
}

#[derive(Debug, serde::Deserialize)]
pub struct SetIntersectionTreatmentRequest {
    pub bus_gate: Option<String>,
    pub turn_conflict: Option<String>,
}

/// `PUT /api/cross-sections/:cross_section_id/intersection-treatment`
pub async fn set_intersection_treatment(
    State(state): State<AppState>,
    Path(cross_section_id): Path<i64>,
    Json(req): Json<SetIntersectionTreatmentRequest>,
) -> Result<Json<IntersectionTreatmentResponse>, ApiError> {
    let bus_gate = parse_bus_gate(&req.bus_gate)?;
    let turn_conflict = parse_turn_conflict(&req.turn_conflict)?;

    let treatment = repository::set_intersection_treatment(
        &state.db.pool,
        CrossSectionId::from(cross_section_id),
        bus_gate,
        turn_conflict,
    )
    .await
    .map_err(|e| internal_error("set_intersection_treatment", e))?;

    Ok(Json(to_intersection_treatment_response(treatment)))
}

#[derive(Debug, serde::Deserialize)]
pub struct UpdateBusStopRequest {
    pub bus_stop: Option<String>,
}

fn parse_bus_stop(raw: &Option<String>) -> Result<Option<BusStop>, ApiError> {
    raw.as_deref()
        .map(|s| BusStop::from_db_str(s).ok_or_else(|| bad_request("unrecognized bus_stop")))
        .transpose()
}

/// `PATCH /api/cross-sections/:cross_section_id/bus-stop`
pub async fn update_bus_stop(
    State(state): State<AppState>,
    Path(cross_section_id): Path<i64>,
    Json(req): Json<UpdateBusStopRequest>,
) -> Result<Json<CrossSectionResponse>, ApiError> {
    let bus_stop = parse_bus_stop(&req.bus_stop)?;

    let updated = repository::update_cross_section_bus_stop(
        &state.db.pool,
        CrossSectionId::from(cross_section_id),
        bus_stop,
    )
    .await
    .map_err(|e| internal_error("update_bus_stop", e))?;

    Ok(Json(CrossSectionResponse {
        id: updated.id.as_i64(),
        position: updated.position,
        label: updated.label,
        lat: updated.lat,
        lon: updated.lon,
        version: updated.version,
        bus_stop: updated.bus_stop.map(|b| b.as_db_str().to_string()),
    }))
}
```

- [ ] **Step 5: Register the two new routes**

In `crates/server/src/web/mod.rs`, change:

```rust
        .route(
            "/api/lanes/:lane_id/access-rules",
            axum::routing::put(lane_editor_api::set_access_rules),
        )
        .nest_service(
```

to:

```rust
        .route(
            "/api/lanes/:lane_id/access-rules",
            axum::routing::put(lane_editor_api::set_access_rules),
        )
        .route(
            "/api/cross-sections/:cross_section_id/intersection-treatment",
            get(lane_editor_api::get_intersection_treatment)
                .put(lane_editor_api::set_intersection_treatment),
        )
        .route(
            "/api/cross-sections/:cross_section_id/bus-stop",
            axum::routing::patch(lane_editor_api::update_bus_stop),
        )
        .nest_service(
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `DOCKER_HOST=unix:///Users/alex/.docker/run/docker.sock cargo nextest run -p mobilispect-server lane_editor_api::tests --no-fail-fast`

Expected: PASS — every test in the module, including this task's 6 new tests.

- [ ] **Step 7: Confirm the workspace still builds and lints clean**

```bash
cargo build --workspace
cargo clippy -p mobilispect-core -p mobilispect-server --all-targets -- -D warnings
cargo fmt --all -- --check
```

Expected: all clean.

- [ ] **Step 8: Commit**

```bash
git add crates/server/src/web/lane_editor_api.rs crates/server/src/web/mod.rs
git commit -m "feat(corridor-design): add intersection-treatment and bus-stop JSON endpoints"
```

---

## Task 4: E2E specs (written first, failing)

**Files:**
- Create: `e2e/tests/builder-intersection-treatment.spec.ts`
- Modify: `e2e/tests/builder-lane-editor.spec.ts` (add one bus-stop test to the existing fixture)

**Interfaces:**
- Consumes: `./helpers/db`'s `ensureRegionHasBoundingBox`/`withDb` (existing, used identically to `builder-lane-editor.spec.ts`).
- Produces: nothing consumed by a later task — these specs are the acceptance test for Task 5's WASM UI.

- [ ] **Step 1: Write the new intersection-treatment spec**

Create `e2e/tests/builder-intersection-treatment.spec.ts`:

```typescript
import { test as base, expect } from '@playwright/test';
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
        [`Intersection Test Remix ${testInfo.testId}`]
      );
      remixId = remixResult.rows[0].id;

      const corridorResult = await client.query(
        `INSERT INTO corridors (name, geometry_source, remix_id) VALUES ($1, 'manual', $2) RETURNING id`,
        [`Intersection Test Corridor ${testInfo.testId}`, remixId]
      );
      corridorId = corridorResult.rows[0].id;

      const crossSectionResult = await client.query(
        `INSERT INTO cross_sections (corridor_id, position, lat, lon) VALUES ($1, 0, 45.500, -73.600) RETURNING id`,
        [corridorId]
      );
      crossSectionId = crossSectionResult.rows[0].id;
    });

    await use({ remixId, corridorId, crossSectionId });

    await withDb(async (client) => {
      await client.query(`DELETE FROM intersection_treatments WHERE cross_section_id = $1`, [crossSectionId]);
      await client.query(`DELETE FROM cross_sections WHERE corridor_id = $1`, [corridorId]);
      await client.query(`DELETE FROM corridors WHERE id = $1`, [corridorId]);
      await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
    });
  },
});

test.describe('Corridor Design: intersection treatment editor', () => {
  test('setting bus gate and turn-conflict type persists across reload', async ({
    page,
    seededCrossSection,
  }) => {
    const { remixId, crossSectionId } = seededCrossSection;
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);

    await expect(page.getByLabel('Bus gate')).toHaveValue('');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('');

    await page.getByLabel('Bus gate').selectOption('signal_controlled');
    await expect(page.getByLabel('Bus gate')).toHaveValue('signal_controlled');

    await page.getByLabel('Turn-conflict type').selectOption('right_in_right_out');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('right_in_right_out');

    // Reload to confirm both edits persisted server-side, not just in local state.
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);
    await expect(page.getByLabel('Bus gate')).toHaveValue('signal_controlled');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('right_in_right_out');
  });

  test('clearing a previously-set field back to None persists', async ({ page, seededCrossSection }) => {
    const { remixId, crossSectionId } = seededCrossSection;
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);

    await page.getByLabel('Bus gate').selectOption('yield_controlled');
    await expect(page.getByLabel('Bus gate')).toHaveValue('yield_controlled');

    await page.getByLabel('Bus gate').selectOption('');
    await expect(page.getByLabel('Bus gate')).toHaveValue('');

    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);
    await expect(page.getByLabel('Bus gate')).toHaveValue('');
  });

  test('back to map link returns to the region map', async ({ page, seededCrossSection }) => {
    const { remixId, crossSectionId } = seededCrossSection;
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);

    await page.getByText('Back to map').click();
    await expect(page).toHaveURL(`/builder/remix/${remixId}`);
  });
});
```

- [ ] **Step 2: Add the bus-stop test to the existing lane-editor spec**

In `e2e/tests/builder-lane-editor.spec.ts`, add the following test at the end of the `test.describe('Corridor Design: lane editor', ...)` block, after the `'two access-rule edits fired in the same tick both persist'` test and before the block's closing `});`:

```typescript

  test('setting the bus stop select persists across reload', async ({ page, seededCrossSection }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);

    await expect(page.getByLabel('Bus stop')).toHaveValue('');
    await page.getByLabel('Bus stop').selectOption('bus_bulb');
    await expect(page.getByLabel('Bus stop')).toHaveValue('bus_bulb');

    // Reload to confirm the edit actually persisted server-side, not just in local state.
    await selectFirstCrossSection(page, corridorId, remixId);
    await expect(page.getByLabel('Bus stop')).toHaveValue('bus_bulb');
  });
```

- [ ] **Step 3: Run both specs to verify they fail for the right reason**

```bash
cd crates/corridor_builder_web && trunk build && cd ../..
export MOBILISPECT_DATABASE_URL=postgres://mobilispect:mobilispect@localhost:5433/mobilispect
dotenvx run -- cargo run --bin mobilispect-server &
cd e2e && npx playwright test builder-intersection-treatment.spec.ts builder-lane-editor.spec.ts -g "bus stop|bus gate|turn-conflict|Back to map"
```

Expected: FAIL — `IntersectionPage` still renders "Intersection editor coming soon." with no `Bus gate`/`Turn-conflict type` selects (`getByLabel` finds nothing), and `CorridorPage`'s side panel has no `Bus stop` select yet.

- [ ] **Step 4: Commit**

```bash
git add e2e/tests/builder-intersection-treatment.spec.ts e2e/tests/builder-lane-editor.spec.ts
git commit -m "test(corridor-design): add failing E2E specs for intersection treatments and bus stop"
```

---

## Task 5: WASM UI — real intersection page + bus-stop select

**Files:**
- Modify: `crates/corridor_builder_web/src/api.rs`
- Modify: `crates/corridor_builder_web/src/pages/intersection.rs`
- Modify: `crates/corridor_builder_web/src/pages/corridor.rs`

**Interfaces:**
- Consumes: `GET`/`PUT /api/cross-sections/:id/intersection-treatment`, `PATCH /api/cross-sections/:id/bus-stop` (Task 3).
- Produces: nothing consumed by a later task — this is the plan's final production-code task.

- [ ] **Step 1: Add the API client functions**

In `crates/corridor_builder_web/src/api.rs`, add `bus_stop: Option<String>` to the existing `CrossSectionSummary`:

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
    pub bus_stop: Option<String>,
}
```

Add at the end of the file:

```rust
#[derive(Debug, Clone, Serialize)]
struct UpdateBusStopRequest {
    bus_stop: Option<String>,
}

pub async fn update_bus_stop(
    cross_section_id: i64,
    bus_stop: Option<String>,
) -> Result<CrossSectionSummary, String> {
    let request = gloo_net::http::Request::patch(&format!(
        "{API_BASE}/cross-sections/{cross_section_id}/bus-stop"
    ))
    .json(&UpdateBusStopRequest { bus_stop })
    .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct IntersectionTreatmentValue {
    pub bus_gate: Option<String>,
    pub turn_conflict: Option<String>,
}

pub async fn get_intersection_treatment(cross_section_id: i64) -> Result<IntersectionTreatmentValue, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/cross-sections/{cross_section_id}/intersection-treatment"
    )))
    .await
}

#[derive(Debug, Clone, Serialize)]
struct SetIntersectionTreatmentRequest {
    bus_gate: Option<String>,
    turn_conflict: Option<String>,
}

pub async fn set_intersection_treatment(
    cross_section_id: i64,
    bus_gate: Option<String>,
    turn_conflict: Option<String>,
) -> Result<IntersectionTreatmentValue, String> {
    let request = gloo_net::http::Request::put(&format!(
        "{API_BASE}/cross-sections/{cross_section_id}/intersection-treatment"
    ))
    .json(&SetIntersectionTreatmentRequest { bus_gate, turn_conflict })
    .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}
```

- [ ] **Step 2: Rewrite `IntersectionPage`**

Replace the entire contents of `crates/corridor_builder_web/src/pages/intersection.rs` with:

```rust
use wasm_bindgen::prelude::*;
use yew::prelude::*;
use yew_router::prelude::*;

use crate::api;
use crate::app::Route;

#[derive(Properties, PartialEq)]
pub struct IntersectionPageProps {
    pub remix_id: i64,
    pub cross_section_id: i64,
}

const BUS_GATES: &[(&str, &str)] = &[
    ("", "None"),
    ("signal_controlled", "Signal controlled"),
    ("yield_controlled", "Yield controlled"),
];

const TURN_CONFLICTS: &[(&str, &str)] = &[
    ("", "None"),
    ("indirect_left_via_alternative", "Indirect left via alternative"),
    (
        "indirect_left_within_intersection",
        "Indirect left within intersection",
    ),
    ("right_in_right_out", "Right-in / right-out"),
    ("dead_end_lateral_street", "Dead-end lateral street"),
];

/// `true` when `current` (a `None`/`Some(String)` field's live value) matches
/// this `<option>`'s raw `value` attribute -- `value == ""` stands for
/// `current == None` throughout this page, matching the `<select>`'s own
/// "None" option.
fn is_selected(current: &Option<String>, value: &str) -> bool {
    match current {
        Some(c) => c == value,
        None => value.is_empty(),
    }
}

fn to_option(value: String) -> Option<String> {
    if value.is_empty() { None } else { Some(value) }
}

#[component]
pub fn IntersectionPage(props: &IntersectionPageProps) -> Html {
    let remix_id = props.remix_id;
    let cross_section_id = props.cross_section_id;

    let bus_gate = use_state(|| None::<String>);
    let turn_conflict = use_state(|| None::<String>);
    let error = use_state(|| None::<String>);
    let loaded = use_state(|| false);

    {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let error = error.clone();
        let loaded = loaded.clone();
        use_effect_with(cross_section_id, move |cross_section_id| {
            let cross_section_id = *cross_section_id;
            let bus_gate = bus_gate.clone();
            let turn_conflict = turn_conflict.clone();
            let error = error.clone();
            let loaded = loaded.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::get_intersection_treatment(cross_section_id).await {
                    Ok(fetched) => {
                        bus_gate.set(fetched.bus_gate);
                        turn_conflict.set(fetched.turn_conflict);
                        loaded.set(true);
                    }
                    Err(e) => error.set(Some(e)),
                }
            });
            || ()
        });
    }

    // Two <select> `change` events can never land in the same browser task
    // the way a text-input blur and a button click can (see
    // `pages/corridor.rs`'s write-queue comment for that hazard) -- a native
    // <select>'s dropdown is modal, so persisting each change immediately
    // from `use_state`'s latest value, with no write-queue/live-ref
    // machinery, can't drop a same-tick sibling edit here.
    let on_bus_gate_change = {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let error = error.clone();
        Callback::from(move |e: Event| {
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let new_bus_gate = to_option(value);
            bus_gate.set(new_bus_gate.clone());
            let turn_conflict_value = (*turn_conflict).clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                if let Err(e) =
                    api::set_intersection_treatment(cross_section_id, new_bus_gate, turn_conflict_value).await
                {
                    error.set(Some(e));
                }
            });
        })
    };

    let on_turn_conflict_change = {
        let bus_gate = bus_gate.clone();
        let turn_conflict = turn_conflict.clone();
        let error = error.clone();
        Callback::from(move |e: Event| {
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let new_turn_conflict = to_option(value);
            turn_conflict.set(new_turn_conflict.clone());
            let bus_gate_value = (*bus_gate).clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                if let Err(e) =
                    api::set_intersection_treatment(cross_section_id, bus_gate_value, new_turn_conflict).await
                {
                    error.set(Some(e));
                }
            });
        })
    };

    html! {
        <div class="setup-wrap">
            <div class="setup-card">
                if let Some(err) = &*error {
                    <div class="alert alert--err">{ err }</div>
                }
                if *loaded {
                    <label class="field-label" for="bus-gate">{ "Bus gate" }</label>
                    <select class="field" id="bus-gate" onchange={on_bus_gate_change}>
                        { for BUS_GATES.iter().map(|(value, label)| html! {
                            <option value={*value} selected={is_selected(&bus_gate, value)}>{ *label }</option>
                        }) }
                    </select>

                    <label class="field-label" for="turn-conflict" style="margin-top:0.75rem;">{ "Turn-conflict type" }</label>
                    <select class="field" id="turn-conflict" onchange={on_turn_conflict_change}>
                        { for TURN_CONFLICTS.iter().map(|(value, label)| html! {
                            <option value={*value} selected={is_selected(&turn_conflict, value)}>{ *label }</option>
                        }) }
                    </select>
                } else {
                    <p>{ "Loading…" }</p>
                }
                <div style="margin-top:1rem;">
                    <Link<Route> classes="chip" to={Route::RegionMap { remix_id }}>{ "Back to map" }</Link<Route>>
                </div>
            </div>
        </div>
    }
}
```

- [ ] **Step 3: Add the bus-stop select to `CorridorPage`'s side panel**

In `crates/corridor_builder_web/src/pages/corridor.rs`, add a new callback right after the existing `on_label_blur` definition (before `let selected_lane = ...`):

```rust
    let on_bus_stop_change = {
        let cross_sections = cross_sections.clone();
        let selected_cross_section_id = selected_cross_section_id.clone();
        let error = error.clone();
        Callback::from(move |e: Event| {
            let Some(cross_section_id) = *selected_cross_section_id else {
                return;
            };
            let value = e
                .target_dyn_into::<web_sys::HtmlSelectElement>()
                .map(|el| el.value())
                .unwrap_or_default();
            let bus_stop = if value.is_empty() { None } else { Some(value) };
            let cross_sections = cross_sections.clone();
            let error = error.clone();
            wasm_bindgen_futures::spawn_local(async move {
                match api::update_bus_stop(cross_section_id, bus_stop).await {
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
```

Change the `html!` block's cross-section-label markup:

```rust
                if let Some(cs) = &selected_cross_section {
                    <label class="field-label" for="cross-section-label">{ "Cross-section label" }</label>
                    <input class="field" id="cross-section-label" type="text" value={cs.label.clone().unwrap_or_default()} onblur={on_label_blur} />

                    <div class="xs-diagram" style="margin-top:1rem;">
```

to:

```rust
                if let Some(cs) = &selected_cross_section {
                    <label class="field-label" for="cross-section-label">{ "Cross-section label" }</label>
                    <input class="field" id="cross-section-label" type="text" value={cs.label.clone().unwrap_or_default()} onblur={on_label_blur} />

                    <label class="field-label" for="cross-section-bus-stop" style="margin-top:0.75rem;">{ "Bus stop" }</label>
                    <select class="field" id="cross-section-bus-stop" onchange={on_bus_stop_change}>
                        { for BUS_STOPS.iter().map(|(value, label)| html! {
                            <option value={*value} selected={cs.bus_stop.as_deref() == (if value.is_empty() { None } else { Some(*value) })}>{ *label }</option>
                        }) }
                    </select>

                    <div class="xs-diagram" style="margin-top:1rem;">
```

Add the `BUS_STOPS` constant next to the existing `LANE_TYPES`/`LANE_DIRECTIONS` constants:

```rust
const LANE_DIRECTIONS: &[(&str, &str)] = &[
    ("forward", "Forward"),
    ("backward", "Backward"),
    ("both", "Both"),
    ("none", "None"),
];
```

to:

```rust
const LANE_DIRECTIONS: &[(&str, &str)] = &[
    ("forward", "Forward"),
    ("backward", "Backward"),
    ("both", "Both"),
    ("none", "None"),
];

const BUS_STOPS: &[(&str, &str)] = &[
    ("", "None"),
    ("bus_bulb", "Bus bulb"),
    ("signal_protected_platform", "Signal-protected platform"),
];
```

- [ ] **Step 4: Build the WASM crate**

```bash
cd crates/corridor_builder_web
cargo fmt --all -- --check
cargo clippy --target wasm32-unknown-unknown -- -D warnings
trunk build
cd ../..
```

Expected: all clean.

- [ ] **Step 5: Run the E2E specs to verify they now pass**

```bash
export MOBILISPECT_DATABASE_URL=postgres://mobilispect:mobilispect@localhost:5433/mobilispect
dotenvx run -- cargo run --bin mobilispect-server &
cd e2e && npx playwright test builder-intersection-treatment.spec.ts builder-lane-editor.spec.ts
```

Expected: PASS — all of `builder-intersection-treatment.spec.ts` (Task 4's new file) and all of `builder-lane-editor.spec.ts` (including this task's new bus-stop test), across chromium/firefox/webkit.

- [ ] **Step 6: Commit**

```bash
git add crates/corridor_builder_web/src/api.rs crates/corridor_builder_web/src/pages/intersection.rs crates/corridor_builder_web/src/pages/corridor.rs
git commit -m "feat(corridor-design): replace intersection placeholder with a real form, add bus-stop select to the lane editor"
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

Expected: all succeed cleanly on the crates this plan touches. Unscoped `cargo clippy --workspace`/`cargo nextest run --workspace` may still show pre-existing, unrelated results in `crates/worker/` — out of scope for this plan.

- [ ] **Step 2: WASM crate**

```bash
cd crates/corridor_builder_web
cargo fmt --check
cargo clippy --target wasm32-unknown-unknown -- -D warnings
trunk build
cd ../..
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

Expected: all `builder-*.spec.ts` files pass across chromium/firefox/webkit, including the two new/modified specs from Task 4.

- [ ] **Step 4: Scope check**

```bash
git diff $(git merge-base main HEAD) HEAD --stat
```

Confirm the file list matches this plan's tasks: `crates/core/migrations/027_intersection_treatments.sql`, `crates/core/src/corridor_design/{mod,edit,repository,intersection}.rs`, `crates/server/src/web/{lane_editor_api,mod}.rs`, `crates/corridor_builder_web/src/{api,pages/intersection,pages/corridor}.rs`, `e2e/tests/{builder-intersection-treatment,builder-lane-editor}.spec.ts`, `.sqlx/*.json`, plus this plan's own design-spec/plan documents — and nothing unexpected.

No commit for this task — verification only. If anything fails, fix it in the relevant earlier task's files and re-run.

---

## Summary

After all 6 tasks: an analyst can click a corridor's endpoint cross-section on the region map (or navigate there directly) and set its bus-gate control and turn-conflict classification through a real form, replacing the "coming soon" placeholder. Any cross-section's bus-stop platform type (bus bulb or signal-protected platform) can be set from the existing lane editor's side panel, alongside its label and lane arrangement. This closes out every section of `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md` — cross-section sequence, cross-section/lane editing, and intersection treatments are now all implemented.

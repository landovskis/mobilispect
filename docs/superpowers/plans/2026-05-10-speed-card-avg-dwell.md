# Speed Card: Avg Dwell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an avg actual dwell stat (seconds per stop, last 28 days) to the Speed Card.

**Architecture:** Pre-compute `avg_dwell_secs` in `compute_route_speed_daily` and store it in `route_speed_daily`. The existing `route_speed_by_day_type` CTE aggregates it into `RouteSpeedDayType`, which flows through `build_speed_cards` into `RouteSpeedCard`. A new display method formats it as "23s". A fourth stat in `speed_card.html` renders it neutral.

**Tech Stack:** Rust, sqlx 0.8, Askama, PostgreSQL (generated column `dwell_secs` already exists on `stop_time_events` via migration 003)

---

## File Map

| File | What changes |
|------|-------------|
| `migrations/008_avg_dwell_speed_daily.sql` | New: add nullable `avg_dwell_secs` column to `route_speed_daily` |
| `src/speed/card.rs` | Add `avg_dwell_secs` field to `RouteSpeedCard`; add `avg_dwell_number()`/`avg_dwell_unit()` methods; update `build_speed_cards` to average the field; update all test helpers/literals |
| `src/speed/mod.rs` | Add `avg_dwell_secs` to `RouteSpeedDayType`; update `route_speed_by_day_type` CTE; add dwell query to `compute_route_speed_daily`; update INSERT; update `make_row` test helper |
| `src/web/handlers.rs` | Add `avg_dwell_secs: None` to all four `RouteSpeedCard` test helpers |
| `templates/speed_card.html` | Add fourth stat between Actual and Avg stop spacing |

---

### Task 1: Create migration

**Files:**
- Create: `migrations/008_avg_dwell_speed_daily.sql`

- [ ] **Step 1: Create the migration file**

```sql
-- migrations/008_avg_dwell_speed_daily.sql
ALTER TABLE route_speed_daily ADD COLUMN avg_dwell_secs DOUBLE PRECISION;
```

- [ ] **Step 2: Commit**

```bash
git add migrations/008_avg_dwell_speed_daily.sql
git commit -m "feat(migrations): add avg_dwell_secs to route_speed_daily"
```

---

### Task 2: Add `avg_dwell_secs` to `RouteSpeedCard` + display methods

**Files:**
- Modify: `src/speed/card.rs`
- Modify: `src/web/handlers.rs`

- [ ] **Step 1: Write the failing tests**

Add these tests inside `#[cfg(test)] mod tests` in `src/speed/card.rs`, after the existing `actual_speed_variant_empty_when_no_classification` test:

```rust
fn card_with_dwell(secs: Option<f64>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: "1".into(),
        long_name: "Route 1".into(),
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        avg_stop_spacing_m: None,
        avg_dwell_secs: secs,
        classification: None,
    }
}

#[test]
fn avg_dwell_number_none() {
    assert_eq!(card_with_dwell(None).avg_dwell_number(), "—");
}

#[test]
fn avg_dwell_number_whole_seconds() {
    assert_eq!(card_with_dwell(Some(23.0)).avg_dwell_number(), "23");
}

#[test]
fn avg_dwell_number_rounds() {
    assert_eq!(card_with_dwell(Some(23.7)).avg_dwell_number(), "24");
}

#[test]
fn avg_dwell_unit_none() {
    assert_eq!(card_with_dwell(None).avg_dwell_unit(), "");
}

#[test]
fn avg_dwell_unit_some() {
    assert_eq!(card_with_dwell(Some(30.0)).avg_dwell_unit(), "s");
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test avg_dwell_number avg_dwell_unit
```

Expected: compilation error — `struct RouteSpeedCard has no field named avg_dwell_secs`

- [ ] **Step 3: Add `avg_dwell_secs` field to `RouteSpeedCard` struct**

In `src/speed/card.rs`, add the field after `avg_stop_spacing_m`:

```rust
pub struct RouteSpeedCard {
    pub idx: usize,
    pub agency_name: String,
    pub agency_id: String,
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub avg_scheduled_speed_mps: Option<f64>,
    pub avg_actual_speed_mps: Option<f64>,
    pub avg_stop_spacing_m: Option<f64>,
    pub avg_dwell_secs: Option<f64>,
    pub classification: Option<RouteClass>,
}
```

- [ ] **Step 4: Add display methods to `RouteSpeedCard` impl**

After the existing `actual_speed_variant()` method in the `impl RouteSpeedCard` block:

```rust
pub fn avg_dwell_number(&self) -> String {
    match self.avg_dwell_secs {
        None => "—".to_string(),
        Some(s) => format!("{:.0}", s),
    }
}

pub fn avg_dwell_unit(&self) -> &'static str {
    match self.avg_dwell_secs {
        None => "",
        Some(_) => "s",
    }
}
```

- [ ] **Step 5: Add `avg_dwell_secs: None` stub to `build_speed_cards`**

In the `cards.push(RouteSpeedCard { ... })` call inside `build_speed_cards`, add after `avg_stop_spacing_m`:

```rust
cards.push(RouteSpeedCard {
    idx: card_idx,
    agency_name,
    agency_id: first.agency_id.clone(),
    route_id: first.route_id.clone(),
    short_name: first.short_name.clone(),
    long_name: first.long_name.clone(),
    avg_scheduled_speed_mps,
    avg_actual_speed_mps,
    avg_stop_spacing_m,
    avg_dwell_secs: None,
    classification,
});
```

- [ ] **Step 6: Update all `RouteSpeedCard` struct literals in tests to add `avg_dwell_secs: None`**

In `src/speed/card.rs`, update the following test helpers to add `avg_dwell_secs: None` after `avg_stop_spacing_m`:

**`card_with_spacing`:**
```rust
fn card_with_spacing(m: Option<f64>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: "1".into(),
        long_name: "Route 1".into(),
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        avg_stop_spacing_m: m,
        avg_dwell_secs: None,
        classification: None,
    }
}
```

**`local_card_with_scheduled`:**
```rust
fn local_card_with_scheduled(mps: Option<f64>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: "1".into(),
        long_name: "Route 1".into(),
        avg_scheduled_speed_mps: mps,
        avg_actual_speed_mps: None,
        avg_stop_spacing_m: None,
        avg_dwell_secs: None,
        classification: Some(RouteClass::Local),
    }
}
```

**`local_card_with_actual`:**
```rust
fn local_card_with_actual(mps: Option<f64>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: "1".into(),
        long_name: "Route 1".into(),
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: mps,
        avg_stop_spacing_m: None,
        avg_dwell_secs: None,
        classification: Some(RouteClass::Local),
    }
}
```

Also add `avg_dwell_secs: None` to each of the four inline `RouteSpeedCard { ... }` struct literals in these tests (all in the same file, all have `avg_stop_spacing_m: None`):
- `scheduled_speed_display_formats_kmh_one_decimal`
- `scheduled_speed_display_dash_when_none`
- `actual_speed_display_formats_kmh_one_decimal`
- `actual_speed_display_dash_when_none`
- `scheduled_speed_variant_empty_for_non_local_routes`
- `actual_speed_variant_empty_for_non_local_routes`
- `actual_speed_variant_empty_when_no_classification`

In each, add `avg_dwell_secs: None,` on a new line after `avg_stop_spacing_m: None,`.

- [ ] **Step 7: Update all four `RouteSpeedCard` helpers in `src/web/handlers.rs`**

Add `avg_dwell_secs: None,` after `avg_stop_spacing_m` in each of:

**`card`:**
```rust
fn card(short_name: &str, scheduled: f64, actual: Option<f64>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        avg_scheduled_speed_mps: Some(scheduled),
        avg_actual_speed_mps: actual,
        avg_stop_spacing_m: None,
        avg_dwell_secs: None,
        classification: None,
    }
}
```

**`card_no_scheduled`:**
```rust
fn card_no_scheduled(short_name: &str) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        avg_stop_spacing_m: None,
        avg_dwell_secs: None,
        classification: None,
    }
}
```

**`card_with_spacing`:**
```rust
fn card_with_spacing(short_name: &str, spacing: Option<f64>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        avg_stop_spacing_m: spacing,
        avg_dwell_secs: None,
        classification: None,
    }
}
```

**`card_with_class`:**
```rust
fn card_with_class(short_name: &str, class: Option<RouteClass>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        avg_stop_spacing_m: None,
        avg_dwell_secs: None,
        classification: class,
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

```bash
cargo test
```

Expected: all tests pass including the new `avg_dwell_number_*` and `avg_dwell_unit_*` tests.

- [ ] **Step 9: Commit**

```bash
git add src/speed/card.rs src/web/handlers.rs
git commit -m "feat(speed): add avg_dwell_secs field and display methods to RouteSpeedCard"
```

---

### Task 3: Add `avg_dwell_secs` to `RouteSpeedDayType` and `route_speed_by_day_type`

This task updates the struct and query together — `sqlx::FromRow` requires the query to return every non-optional field in the struct.

**Files:**
- Modify: `src/speed/mod.rs`
- Modify: `src/speed/card.rs` (test helper + literals)

- [ ] **Step 1: Write the failing integration tests**

Add these two tests to the `#[cfg(test)] mod tests` block in `src/speed/mod.rs`, after the `route_speed_by_day_type_uses_persisted_avg_stop_spacing` test:

```rust
#[tokio::test]
async fn route_speed_by_day_type_aggregates_avg_dwell_secs() {
    let td = test_utils::setup().await;
    let db = td.db;

    sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
        .execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO route_speed
         (agency_id, route_id, direction_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at)
         VALUES ('0', 'R1', 0, 8.0, 500.0, 1, '2026-01-01T00:00:00Z')",
    )
    .execute(&db.pool).await.unwrap();
    // Two weekday rows within the 28-day window with avg_dwell_secs
    sqlx::query(
        "INSERT INTO route_speed_daily
         (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, avg_dwell_secs, computed_at)
         VALUES
           ('0', 'R1', (CURRENT_DATE - INTERVAL '2 days')::TEXT, 0, 5.0, 10, 25.0, '2026-01-01T00:00:00Z'),
           ('0', 'R1', (CURRENT_DATE - INTERVAL '3 days')::TEXT, 0, 5.0, 10, 35.0, '2026-01-01T00:00:00Z')",
    )
    .execute(&db.pool).await.unwrap();

    let rows = route_speed_by_day_type(&db, None).await.unwrap();
    assert_eq!(rows.len(), 1);
    let dwell = rows[0].avg_dwell_secs.expect("expected avg_dwell_secs to be Some");
    assert!(
        (dwell - 30.0).abs() < 0.01,
        "expected avg dwell ~30.0, got {dwell}"
    );
}

#[tokio::test]
async fn route_speed_by_day_type_avg_dwell_secs_null_when_no_dwell_data() {
    let td = test_utils::setup().await;
    let db = td.db;

    sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
        .execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO route_speed
         (agency_id, route_id, direction_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at)
         VALUES ('0', 'R1', 0, 8.0, 500.0, 1, '2026-01-01T00:00:00Z')",
    )
    .execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO route_speed_daily
         (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, avg_dwell_secs, computed_at)
         VALUES ('0', 'R1', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 0, 5.0, 10, NULL, '2026-01-01T00:00:00Z')",
    )
    .execute(&db.pool).await.unwrap();

    let rows = route_speed_by_day_type(&db, None).await.unwrap();
    assert_eq!(rows.len(), 1);
    assert!(rows[0].avg_dwell_secs.is_none());
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test route_speed_by_day_type_aggregates_avg_dwell route_speed_by_day_type_avg_dwell_secs_null
```

Expected: compilation error — `RouteSpeedDayType` has no field `avg_dwell_secs` (or column not found at runtime if it compiles).

- [ ] **Step 3: Add `avg_dwell_secs` to `RouteSpeedDayType` struct**

In `src/speed/mod.rs`, add the field to `RouteSpeedDayType` after `avg_stop_spacing_m`:

```rust
#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct RouteSpeedDayType {
    pub agency_id: String,
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub direction_id: i64,
    pub weekday_speed_mps: Option<f64>,
    pub saturday_speed_mps: Option<f64>,
    pub sunday_speed_mps: Option<f64>,
    pub actual_weekday_speed_mps: Option<f64>,
    pub actual_saturday_speed_mps: Option<f64>,
    pub actual_sunday_speed_mps: Option<f64>,
    pub last_stop_name: Option<String>,
    pub avg_stop_spacing_m: Option<f64>,
    pub avg_dwell_secs: Option<f64>,
}
```

- [ ] **Step 4: Update `base_sql` in `route_speed_by_day_type`**

In `src/speed/mod.rs`, find `route_speed_by_day_type`. In the `actual_by_day_type` CTE, add `AVG(avg_dwell_secs) AS avg_dwell_secs` after `actual_sunday_speed_mps`:

```
            AVG(CASE WHEN EXTRACT(DOW FROM service_date::date) = 0
                     THEN actual_speed_mps END) AS actual_sunday_speed_mps,
            AVG(avg_dwell_secs) AS avg_dwell_secs
        FROM route_speed_daily
```

And in the final `SELECT`, add `act.avg_dwell_secs` after `act.actual_sunday_speed_mps`:

```
          act.actual_weekday_speed_mps,
          act.actual_saturday_speed_mps,
          act.actual_sunday_speed_mps,
          act.avg_dwell_secs,
          lsn.stop_name AS last_stop_name,
```

- [ ] **Step 5: Update `make_row` test helper in `src/speed/card.rs`**

Add `avg_dwell_secs: None` after `avg_stop_spacing_m: None` in the helper:

```rust
fn make_row(
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
    weekday: Option<f64>,
) -> RouteSpeedDayType {
    RouteSpeedDayType {
        agency_id: agency_id.to_string(),
        route_id: route_id.to_string(),
        short_name: route_id.to_string(),
        long_name: format!("Route {route_id}"),
        direction_id,
        weekday_speed_mps: weekday,
        saturday_speed_mps: None,
        sunday_speed_mps: None,
        actual_weekday_speed_mps: None,
        actual_saturday_speed_mps: None,
        actual_sunday_speed_mps: None,
        last_stop_name: None,
        avg_stop_spacing_m: None,
        avg_dwell_secs: None,
    }
}
```

Also add `avg_dwell_secs: None` to each full inline `RouteSpeedDayType { ... }` literal in `src/speed/card.rs` tests (the ones in `build_speed_cards_computes_avg_scheduled_speed`, `build_speed_cards_avg_scheduled_uses_all_day_types`, `build_speed_cards_computes_avg_actual_speed`). Each has `last_stop_name: None, avg_stop_spacing_m: None,` — add `avg_dwell_secs: None,` after that.

- [ ] **Step 6: Run tests to verify they pass**

```bash
cargo test route_speed_by_day_type_aggregates_avg_dwell route_speed_by_day_type_avg_dwell_secs_null
```

Expected: both new integration tests pass.

- [ ] **Step 7: Run full test suite**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/speed/mod.rs src/speed/card.rs
git commit -m "feat(speed): add avg_dwell_secs to RouteSpeedDayType and route_speed_by_day_type query"
```

---

### Task 4: Update `build_speed_cards` to average `avg_dwell_secs`

**Files:**
- Modify: `src/speed/card.rs`

- [ ] **Step 1: Write the failing tests**

Add these tests to `#[cfg(test)] mod tests` in `src/speed/card.rs`, after the `avg_dwell_unit_some` test:

```rust
#[test]
fn build_speed_cards_carries_avg_dwell_secs() {
    let mut row = make_row("stm", "R1", 0, Some(8.0));
    row.avg_dwell_secs = Some(30.0);
    let cards = build_speed_cards(vec![row], &HashMap::new());
    assert_eq!(cards[0].avg_dwell_secs, Some(30.0));
}

#[test]
fn build_speed_cards_averages_avg_dwell_across_directions() {
    // direction 0: 20s, direction 1: 40s → avg 30s
    let mut row0 = make_row("stm", "R1", 0, Some(8.0));
    row0.avg_dwell_secs = Some(20.0);
    let mut row1 = make_row("stm", "R1", 1, Some(7.0));
    row1.avg_dwell_secs = Some(40.0);
    let cards = build_speed_cards(vec![row0, row1], &HashMap::new());
    let dwell = cards[0].avg_dwell_secs.unwrap();
    assert!((dwell - 30.0).abs() < 0.001, "expected 30.0, got {dwell}");
}

#[test]
fn build_speed_cards_avg_dwell_none_when_all_none() {
    let row = make_row("stm", "R1", 0, None);
    let cards = build_speed_cards(vec![row], &HashMap::new());
    assert!(cards[0].avg_dwell_secs.is_none());
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test build_speed_cards_carries_avg_dwell build_speed_cards_averages_avg_dwell build_speed_cards_avg_dwell_none
```

Expected: `build_speed_cards_carries_avg_dwell_secs` fails — card returns `None` instead of `Some(30.0)`.

- [ ] **Step 3: Update `build_speed_cards` to average `avg_dwell_secs`**

In `src/speed/card.rs`, inside `build_speed_cards`, replace the stub `avg_dwell_secs: None` with:

```rust
let avg_dwell_secs = avg_speeds(route_rows.iter().map(|r| r.avg_dwell_secs));
```

And reference it in the push:

```rust
cards.push(RouteSpeedCard {
    idx: card_idx,
    agency_name,
    agency_id: first.agency_id.clone(),
    route_id: first.route_id.clone(),
    short_name: first.short_name.clone(),
    long_name: first.long_name.clone(),
    avg_scheduled_speed_mps,
    avg_actual_speed_mps,
    avg_stop_spacing_m,
    avg_dwell_secs,
    classification,
});
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cargo test build_speed_cards_carries_avg_dwell build_speed_cards_averages_avg_dwell build_speed_cards_avg_dwell_none
```

Expected: all three pass.

- [ ] **Step 5: Run full test suite**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/speed/card.rs
git commit -m "feat(speed): average avg_dwell_secs across directions in build_speed_cards"
```

---

### Task 5: Compute and persist `avg_dwell_secs` in `compute_route_speed_daily`

**Files:**
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Write the failing integration test**

Add this test to `#[cfg(test)] mod tests` in `src/speed/mod.rs`, after the `route_speed_by_day_type_avg_dwell_secs_null_when_no_dwell_data` test:

```rust
#[tokio::test]
async fn compute_route_speed_daily_stores_avg_dwell_secs() {
    let td = test_utils::setup().await;
    let db = td.db;
    let agency = test_agency();

    sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0','S1','Stop 1',45.50,-73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0','S2','Stop 2',45.51,-73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S1',1,'08:00:00','08:00:00')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S2',2,'08:10:00','08:10:00')")
        .execute(&db.pool).await.unwrap();
    // S1: arrive 1000, depart 1030 → dwell_secs = 30
    // S2: arrive 1700, depart 1720 → dwell_secs = 20
    // avg = 25.0
    sqlx::query(
        "INSERT INTO stop_time_events
         (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix)
         VALUES ('0','2026-04-01T08:00:00Z','T1','S1',1,1000,1030)",
    )
    .execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO stop_time_events
         (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix)
         VALUES ('0','2026-04-01T08:10:00Z','T1','S2',2,1700,1720)",
    )
    .execute(&db.pool).await.unwrap();

    let service_date = chrono::NaiveDate::from_ymd_opt(2026, 4, 1).unwrap();
    compute_route_speed_daily(&db, &agency, service_date)
        .await
        .unwrap();

    let dwell: Option<f64> = sqlx::query_scalar(
        "SELECT avg_dwell_secs FROM route_speed_daily WHERE agency_id = '0' AND route_id = 'R1'",
    )
    .fetch_one(&db.pool)
    .await
    .unwrap();

    let dwell = dwell.expect("expected avg_dwell_secs to be Some");
    assert!(
        (dwell - 25.0).abs() < 0.01,
        "expected avg dwell ~25.0 (30+20)/2, got {dwell}"
    );
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cargo test compute_route_speed_daily_stores_avg_dwell
```

Expected: test passes at the `compute` call but `avg_dwell_secs` column is NULL (returns `None`) because the computation hasn't been added yet.

- [ ] **Step 3: Add dwell query inside the `combos` loop in `compute_route_speed_daily`**

In `src/speed/mod.rs`, inside `compute_route_speed_daily`, add the dwell query after the `if trip_speeds.is_empty() { continue; }` guard and before the INSERT. The `avg_dwell_secs` value is computed independently of trip_speeds (it may be Some even when used alongside speed):

```rust
        let avg_dwell_secs: Option<f64> = sqlx::query_scalar(
            "SELECT AVG(ste.dwell_secs)::DOUBLE PRECISION
             FROM stop_time_events ste
             JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
             WHERE ste.agency_id = $1
               AND t.route_id = $2
               AND COALESCE(t.direction_id, 0) = $3
               AND ste.observed_at::TIMESTAMPTZ::DATE = $4::DATE
               AND ste.dwell_secs > 0",
        )
        .bind(&agency_id)
        .bind(route_id)
        .bind(direction_id)
        .bind(&date_str)
        .fetch_one(&db.pool)
        .await?;
```

- [ ] **Step 4: Update the INSERT to include `avg_dwell_secs`**

Replace the existing `sqlx::query!` INSERT in `compute_route_speed_daily`:

```rust
        sqlx::query!(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, avg_dwell_secs, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
             ON CONFLICT (agency_id, route_id, service_date, direction_id) DO UPDATE SET
               actual_speed_mps = EXCLUDED.actual_speed_mps,
               trip_count = EXCLUDED.trip_count,
               avg_dwell_secs = EXCLUDED.avg_dwell_secs,
               computed_at = EXCLUDED.computed_at",
            &agency_id,
            route_id,
            date_str,
            direction_id,
            avg_speed,
            trip_count,
            avg_dwell_secs as Option<f64>,
            now,
        )
        .execute(&db.pool)
        .await?;
```

Note: `sqlx::query!` performs compile-time checking. If the build fails with a schema error, run `cargo sqlx prepare` with a running database to regenerate the offline query cache.

- [ ] **Step 5: Run test to verify it passes**

```bash
cargo test compute_route_speed_daily_stores_avg_dwell
```

Expected: test passes — `avg_dwell_secs ≈ 25.0`.

- [ ] **Step 6: Run full test suite**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/speed/mod.rs
git commit -m "feat(speed): compute and persist avg_dwell_secs in compute_route_speed_daily"
```

---

### Task 6: Add avg dwell stat to speed card template

**Files:**
- Modify: `templates/speed_card.html`

- [ ] **Step 1: Add the stat to the template**

In `templates/speed_card.html`, in the stat row div, add the new stat between Actual and Avg stop spacing:

```html
{% import "macros.html" as ui %}
<a href="/routes/{{ card.agency_id }}/{{ card.route_id }}/speed"
   style="text-decoration: none; color: inherit; display: block;">
<div class="card" style="padding:1rem 1.25rem;">
  <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:1rem;">
    <div>
      <div class="route-id">{{ card.agency_name }} {{ card.short_name }}</div>
      <div style="margin-top:0.15rem;color:var(--ink-500);font-size:0.82rem;">{{ card.long_name }}</div>
    </div>
    {% if let Some(class) = card.classification %}
    {{ ui::badge(variant="neutral", label=class.label()) }}
    {% endif %}
  </div>
  <div style="margin-top:0.75rem;display:flex;gap:1.5rem;border-top:1px solid var(--line-soft);padding-top:0.75rem;">
    <div>{{ ui::stat(label="Scheduled", value=card.avg_scheduled_speed_kmh_display(), unit=" km/h", variant=card.scheduled_speed_variant()) }}</div>
    <div>{{ ui::stat(label="Actual", value=card.avg_actual_speed_kmh_display(), unit=" km/h", variant=card.actual_speed_variant()) }}</div>
    <div>{{ ui::stat(label="Avg dwell", value=card.avg_dwell_number(), unit=card.avg_dwell_unit(), variant="neutral") }}</div>
    <div style="margin-left:auto;">{{ ui::stat(label="Avg stop spacing", value=card.avg_stop_spacing_number(), unit=card.avg_stop_spacing_unit(), variant=card.avg_stop_spacing_variant()) }}</div>
  </div>
</div>
</a>
```

- [ ] **Step 2: Build to verify template compiles**

```bash
cargo build
```

Expected: success. Askama compiles templates at build time — any type mismatch in `card.avg_dwell_number()` or `card.avg_dwell_unit()` shows here.

- [ ] **Step 3: Run full test suite**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add templates/speed_card.html
git commit -m "feat(speed): add avg dwell stat to speed card"
```
# Speed Detail: Direction Card Per Route Variant — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the one-card-per-direction view on the speed detail page with one card per route variant, each labelled "First Stop → Last Stop", with a primary/trip-count badge; speed trend data is tracked per variant rather than per direction.

**Architecture:** Add `variant_id TEXT NOT NULL DEFAULT ''` to `route_speed_daily` (extending the PK), update `compute_route_speed_daily` to group trips by variant, add `route_speed_trend_by_variant`, update `route_stop_spacings` to return all variants (not just primary), update the handler and template. Existing overview/scorecard queries use `AVG()` grouped by direction and are unaffected.

**Tech Stack:** Rust 2024, sqlx 0.8 (offline mode — `.sqlx/` cache must be regenerated after changing `sqlx::query!` macros), Askama templates, PostgreSQL.

---

## File Map

| File | What changes |
|------|-------------|
| `migrations/008_route_speed_daily_variant.sql` | New — adds `variant_id` column + extends PK |
| `src/speed/mod.rs` | Update `StopSpacingEntry`, `DirectionStopSpacings`, `build_direction_spacings`, `route_stop_spacings` SQL; add `VariantSpeedTrend`, `SpeedTrendVariantRow`, `build_variant_trends`, `route_speed_trend_by_variant`; update `compute_route_speed_daily` |
| `src/web/handlers.rs` | Add fields to `RouteSpeedDetailDirection`; update `route_speed_detail` handler to use new functions; update imports |
| `templates/route_speed_detail.html` | Update direction card header — label + badge |
| `.sqlx/` | Regenerate after changing the `sqlx::query!` INSERT in Task 5 |

---

### Task 1: Migration 008 — add `variant_id` to `route_speed_daily`

**Files:**
- Create: `migrations/008_route_speed_daily_variant.sql`

- [ ] **Step 1: Write the failing test**

Add this test inside the `#[cfg(test)] mod tests` block in `src/speed/mod.rs`, near the existing `compute_route_speed_daily_stores_actual_speed` test:

```rust
#[tokio::test]
async fn route_speed_daily_has_variant_id_column() {
    let td = test_utils::setup().await;
    // After migration 008, inserting a row with a non-empty variant_id must succeed.
    let result = sqlx::query(
        "INSERT INTO route_speed_daily
         (agency_id, route_id, service_date, direction_id, variant_id, actual_speed_mps, trip_count, computed_at)
         VALUES ('0', 'R1', '2026-01-01', 0, 'VAR1', 5.0, 1, 'now')",
    )
    .execute(&td.db.pool)
    .await;
    assert!(result.is_ok(), "expected insert with variant_id to succeed: {:?}", result.err());
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cargo test route_speed_daily_has_variant_id_column
```

Expected: `FAILED` — column `variant_id` does not exist (runtime error from sqlx).

- [ ] **Step 3: Create the migration file**

Create `migrations/008_route_speed_daily_variant.sql`:

```sql
ALTER TABLE route_speed_daily ADD COLUMN variant_id TEXT NOT NULL DEFAULT '';

ALTER TABLE route_speed_daily DROP CONSTRAINT route_speed_daily_pkey;

ALTER TABLE route_speed_daily ADD PRIMARY KEY (agency_id, route_id, service_date, direction_id, variant_id);
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cargo test route_speed_daily_has_variant_id_column
```

Expected: `ok`.

- [ ] **Step 5: Confirm all other tests still pass**

```bash
cargo test
```

Expected: all pass. The new `DEFAULT ''` means existing rows and existing INSERT queries continue to work (they just leave `variant_id` as `''`). The only test that was previously failing (`route_stop_spacings_returns_correct_order_and_distances`) will still fail — that is fixed in Task 3.

- [ ] **Step 6: Commit**

```bash
git add migrations/008_route_speed_daily_variant.sql src/speed/mod.rs
git commit -m "feat: add variant_id column to route_speed_daily"
```

---

### Task 2: Update `StopSpacingEntry`, `DirectionStopSpacings`, `build_direction_spacings`

**Files:**
- Modify: `src/speed/mod.rs`

The goal of this task is to evolve the Rust data structures so they carry `variant_id`, `is_primary`, `trip_count` and format `direction_name` as `"First Stop → Last Stop"`. No SQL changes yet.

- [ ] **Step 1: Write the failing tests**

Add these tests inside the `#[cfg(test)] mod tests` block in `src/speed/mod.rs`:

```rust
#[test]
fn build_direction_spacings_formats_direction_name_as_first_to_last() {
    let rows = vec![
        StopSpacingEntry {
            variant_id: "VAR1".into(),
            direction_id: 0,
            is_primary: true,
            trip_count: 5,
            to_stop_name: "First Stop".into(),
            distance_m: None,
            is_first: true,
            is_last: false,
        },
        StopSpacingEntry {
            variant_id: "VAR1".into(),
            direction_id: 0,
            is_primary: true,
            trip_count: 5,
            to_stop_name: "Last Stop".into(),
            distance_m: Some(500.0),
            is_first: false,
            is_last: true,
        },
    ];
    let result = build_direction_spacings(rows);
    assert_eq!(result.len(), 1);
    assert_eq!(result[0].direction_name, "First Stop → Last Stop");
}

#[test]
fn build_direction_spacings_carries_variant_fields() {
    let rows = vec![
        StopSpacingEntry {
            variant_id: "VARABC".into(),
            direction_id: 1,
            is_primary: false,
            trip_count: 3,
            to_stop_name: "A".into(),
            distance_m: None,
            is_first: true,
            is_last: false,
        },
        StopSpacingEntry {
            variant_id: "VARABC".into(),
            direction_id: 1,
            is_primary: false,
            trip_count: 3,
            to_stop_name: "B".into(),
            distance_m: Some(300.0),
            is_first: false,
            is_last: true,
        },
    ];
    let result = build_direction_spacings(rows);
    assert_eq!(result[0].variant_id, "VARABC");
    assert_eq!(result[0].direction_id, 1);
    assert!(!result[0].is_primary);
    assert_eq!(result[0].trip_count, 3);
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test build_direction_spacings_formats_direction_name build_direction_spacings_carries_variant_fields
```

Expected: compile error — `StopSpacingEntry` has no field `variant_id`, `is_primary`, or `trip_count`; `DirectionStopSpacings` has no `variant_id` etc.

- [ ] **Step 3: Update `StopSpacingEntry`**

In `src/speed/mod.rs`, find the `StopSpacingEntry` struct (around line 143) and replace it:

```rust
#[derive(sqlx::FromRow)]
struct StopSpacingEntry {
    variant_id: String,
    direction_id: i64,
    is_primary: bool,
    trip_count: i64,
    to_stop_name: String,
    distance_m: Option<f64>,
    is_first: bool,
    is_last: bool,
}
```

- [ ] **Step 4: Update `DirectionStopSpacings`**

Find the `DirectionStopSpacings` struct (around line 131) and replace it:

```rust
pub struct DirectionStopSpacings {
    pub direction_id: i64,
    pub variant_id: String,
    pub is_primary: bool,
    pub trip_count: i64,
    /// "First Stop → Last Stop"
    pub direction_name: String,
    pub first_stop_name: String,
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
}
```

- [ ] **Step 5: Update `build_direction_spacings`**

Find `fn build_direction_spacings` (around line 174). Replace the entire function body with a version that groups by `variant_id` instead of `direction_id` and builds `direction_name` as `"{first} → {last}"`:

```rust
fn build_direction_spacings(rows: Vec<StopSpacingEntry>) -> Vec<DirectionStopSpacings> {
    let mut result: Vec<DirectionStopSpacings> = Vec::new();
    let mut i = 0;
    while i < rows.len() {
        let variant_id = rows[i].variant_id.clone();
        let end = rows[i..]
            .iter()
            .position(|r| r.variant_id != variant_id)
            .map(|p| i + p)
            .unwrap_or(rows.len());
        let dir_rows = &rows[i..end];

        let first_stop_name = dir_rows
            .iter()
            .find(|r| r.is_first)
            .map(|r| r.to_stop_name.clone())
            .unwrap_or_default();

        let last_stop_name = dir_rows
            .iter()
            .find(|r| r.is_last)
            .map(|r| r.to_stop_name.clone())
            .unwrap_or_default();

        let direction_name = format!("{first_stop_name} → {last_stop_name}");

        let distances: Vec<(String, f64)> = dir_rows
            .iter()
            .filter_map(|r| r.distance_m.map(|d| (r.to_stop_name.clone(), d)))
            .collect();

        let avg_spacing_m = if distances.is_empty() {
            0.0
        } else {
            distances.iter().map(|(_, d)| d).sum::<f64>() / distances.len() as f64
        };

        let max_dist = distances.iter().map(|(_, d)| *d).fold(0.0_f64, f64::max);
        let threshold = avg_spacing_m * 1.5;

        let (range_min, range_max) = if avg_spacing_m < 500.0 {
            (300.0, 500.0)
        } else if avg_spacing_m < 1500.0 {
            (500.0, 1500.0)
        } else {
            (1500.0, 5000.0)
        };

        let spacings = distances
            .into_iter()
            .map(|(name, dist)| {
                let spacing_status = if dist < range_min {
                    "below"
                } else if dist > range_max {
                    "above"
                } else {
                    "in_range"
                };
                StopSpacing {
                    to_stop_name: name,
                    distance_m: dist,
                    is_outlier: dist > threshold,
                    width_px: ((dist / max_dist.max(1.0)) * 200.0) as u32,
                    spacing_status: spacing_status.to_string(),
                }
            })
            .collect();

        let first_row = &dir_rows[0];
        result.push(DirectionStopSpacings {
            direction_id: first_row.direction_id,
            variant_id: variant_id.clone(),
            is_primary: first_row.is_primary,
            trip_count: first_row.trip_count,
            direction_name,
            first_stop_name,
            avg_spacing_m,
            spacings,
        });
        i = end;
    }
    result
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cargo test build_direction_spacings
```

Expected: all `build_direction_spacings_*` tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/speed/mod.rs
git commit -m "refactor: add variant_id/is_primary/trip_count to DirectionStopSpacings, format direction_name as first→last"
```

---

### Task 3: Update `route_stop_spacings` SQL — return all variants

**Files:**
- Modify: `src/speed/mod.rs`

This task also fixes the existing broken test `route_stop_spacings_returns_correct_order_and_distances` (it was missing route_variants seed data).

- [ ] **Step 1: Fix and extend the existing broken test**

Find `route_stop_spacings_returns_correct_order_and_distances` (around line 2618). Replace the entire test body with a version that seeds `route_variants` and `route_variant_stops`, and asserts the new fields and direction_name format:

```rust
#[tokio::test]
async fn route_stop_spacings_returns_correct_order_and_distances() {
    let td = test_utils::setup().await;
    let db = &td.db;

    sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
        .execute(&db.pool).await.unwrap();

    // S1 and S2 are 0.01° apart in latitude ≈ 1111 m; S2 and S3 are 0.001° ≈ 111 m
    sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'First Stop',  45.500, -73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Middle Stop', 45.510, -73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0', 'S3', 'Terminus',    45.511, -73.50)")
        .execute(&db.pool).await.unwrap();

    // One variant, direction 0, is_primary=true, trip_count=5
    sqlx::query(
        "INSERT INTO route_variants (agency_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary)
         VALUES ('0', 'VAR1', 'R1', 0, 3, 5, true)",
    ).execute(&db.pool).await.unwrap();

    sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 1, 'S1')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 2, 'S2')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 3, 'S3')")
        .execute(&db.pool).await.unwrap();

    let directions = route_stop_spacings(db, "0", "R1").await.unwrap();

    assert_eq!(directions.len(), 1, "one variant expected");
    let dir = &directions[0];
    assert_eq!(dir.variant_id, "VAR1");
    assert!(dir.is_primary);
    assert_eq!(dir.trip_count, 5);
    assert_eq!(dir.first_stop_name, "First Stop");
    assert_eq!(dir.direction_name, "First Stop → Terminus");
    assert_eq!(dir.spacings.len(), 2, "two segments (S1→S2, S2→S3)");
    assert_eq!(dir.spacings[0].to_stop_name, "Middle Stop");
    assert_eq!(dir.spacings[1].to_stop_name, "Terminus");
    assert!(
        (dir.spacings[0].distance_m - 1111.0).abs() < 50.0,
        "S1→S2 should be ~1111 m, got {}",
        dir.spacings[0].distance_m
    );
    assert!(
        (dir.spacings[1].distance_m - 111.0).abs() < 10.0,
        "S2→S3 should be ~111 m, got {}",
        dir.spacings[1].distance_m
    );
    assert!(dir.spacings[0].is_outlier, "S1→S2 should be flagged as outlier");
    assert!(!dir.spacings[1].is_outlier, "S2→S3 should not be an outlier");
}
```

- [ ] **Step 2: Write a new test for multiple variants ordered by trip_count**

Add this test after the one above:

```rust
#[tokio::test]
async fn route_stop_spacings_returns_all_variants_ordered_by_trip_count_desc() {
    let td = test_utils::setup().await;
    let db = &td.db;

    sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
        .execute(&db.pool).await.unwrap();

    sqlx::query("INSERT INTO stops VALUES ('0', 'SA', 'Alpha',  45.500, -73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0', 'SB', 'Beta',   45.505, -73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0', 'SC', 'Gamma',  45.510, -73.50)")
        .execute(&db.pool).await.unwrap();

    // VAR2 has fewer trips than VAR1 — should come second even though lexicographically first
    sqlx::query(
        "INSERT INTO route_variants (agency_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary)
         VALUES ('0', 'VAR1', 'R1', 0, 3, 10, true),
                ('0', 'VAR2', 'R1', 0, 2,  3, false)",
    ).execute(&db.pool).await.unwrap();

    // VAR1: SA → SB → SC
    sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 1, 'SA')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 2, 'SB')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 3, 'SC')")
        .execute(&db.pool).await.unwrap();

    // VAR2: SA → SC (short turn, skips SB)
    sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR2', 1, 'SA')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR2', 2, 'SC')")
        .execute(&db.pool).await.unwrap();

    let directions = route_stop_spacings(db, "0", "R1").await.unwrap();

    assert_eq!(directions.len(), 2, "two variants expected");
    // VAR1 first (trip_count=10 > 3)
    assert_eq!(directions[0].variant_id, "VAR1");
    assert!(directions[0].is_primary);
    assert_eq!(directions[0].trip_count, 10);
    assert_eq!(directions[0].direction_name, "Alpha → Gamma");
    assert_eq!(directions[0].spacings.len(), 2);
    // VAR2 second
    assert_eq!(directions[1].variant_id, "VAR2");
    assert!(!directions[1].is_primary);
    assert_eq!(directions[1].trip_count, 3);
    assert_eq!(directions[1].direction_name, "Alpha → Gamma");
    assert_eq!(directions[1].spacings.len(), 1);
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
cargo test route_stop_spacings_returns_correct route_stop_spacings_returns_all_variants
```

Expected: both fail — `direction_name` format is wrong and SQL still filters to primary only.

- [ ] **Step 4: Update the SQL in `route_stop_spacings`**

Find `pub async fn route_stop_spacings` (around line 323) in `src/speed/mod.rs`. Replace the entire SQL string (the `sqlx::query_as(` call and its string literal, keeping the `.bind(...)` lines and the rest of the function):

```rust
let rows: Vec<StopSpacingEntry> = sqlx::query_as(
    "WITH all_variants AS (
        SELECT variant_id, direction_id, is_primary, trip_count
        FROM route_variants
        WHERE agency_id = $1 AND route_id = $2
    ),
    ordered AS (
        SELECT
            av.variant_id,
            av.direction_id,
            av.is_primary,
            av.trip_count,
            s.stop_name,
            s.stop_lat, s.stop_lon,
            ROW_NUMBER() OVER (PARTITION BY av.variant_id ORDER BY rvs.stop_sequence) AS rn,
            COUNT(*)    OVER (PARTITION BY av.variant_id)                              AS total_stops
        FROM all_variants av
        JOIN route_variant_stops rvs ON rvs.agency_id = $1 AND rvs.variant_id = av.variant_id
        JOIN stops s ON s.agency_id = $1 AND s.stop_id = rvs.stop_id
    ),
    with_prev AS (
        SELECT
            variant_id, direction_id, is_primary, trip_count,
            stop_name, rn, total_stops, stop_lat, stop_lon,
            LAG(stop_lat) OVER (PARTITION BY variant_id ORDER BY rn) AS prev_lat,
            LAG(stop_lon) OVER (PARTITION BY variant_id ORDER BY rn) AS prev_lon
        FROM ordered
    )
    SELECT
        variant_id,
        direction_id,
        is_primary,
        trip_count,
        stop_name AS to_stop_name,
        CASE WHEN prev_lat IS NOT NULL THEN
            2 * 6371000 * asin(sqrt(
                power(sin((stop_lat - prev_lat) * pi() / 180.0 / 2.0), 2) +
                cos(prev_lat * pi() / 180.0) * cos(stop_lat * pi() / 180.0) *
                power(sin((stop_lon - prev_lon) * pi() / 180.0 / 2.0), 2)
            ))
        END AS distance_m,
        (rn = 1)           AS is_first,
        (rn = total_stops) AS is_last
    FROM with_prev
    ORDER BY trip_count DESC, variant_id, rn",
)
.bind(&agency_id)
.bind(route_id)
.fetch_all(&db.pool)
.await?;
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cargo test route_stop_spacings
```

Expected: all `route_stop_spacings_*` tests pass (including the previously broken one).

- [ ] **Step 6: Run all tests**

```bash
cargo test
```

Expected: all pass. The `compute_route_speed` function also uses route_variants — it is read-only and unaffected by this change.

- [ ] **Step 7: Commit**

```bash
git add src/speed/mod.rs
git commit -m "feat: route_stop_spacings returns all variants ordered by trip_count desc"
```

---

### Task 4: Add `VariantSpeedTrend` and `route_speed_trend_by_variant`

**Files:**
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Write the failing unit tests**

Add these unit tests inside the `#[cfg(test)] mod tests` block, near the existing `build_direction_trends_*` tests:

```rust
#[test]
fn build_variant_trends_buckets_weekday_saturday_sunday() {
    // 2024-01-01 = Monday, 2024-01-06 = Saturday, 2024-01-07 = Sunday
    let rows = vec![
        SpeedTrendVariantRow {
            variant_id: "VAR1".into(),
            service_date: "2024-01-01".into(),
            actual_speed_mps: 5.0,
            scheduled_speed_mps: Some(6.0),
        },
        SpeedTrendVariantRow {
            variant_id: "VAR1".into(),
            service_date: "2024-01-06".into(),
            actual_speed_mps: 6.0,
            scheduled_speed_mps: Some(7.0),
        },
        SpeedTrendVariantRow {
            variant_id: "VAR1".into(),
            service_date: "2024-01-07".into(),
            actual_speed_mps: 7.0,
            scheduled_speed_mps: Some(8.0),
        },
    ];
    let result = build_variant_trends(rows);
    assert_eq!(result.len(), 1);
    assert_eq!(result[0].variant_id, "VAR1");
    assert_eq!(result[0].weekday, vec![("2024-01-01".to_string(), 5.0, Some(6.0))]);
    assert_eq!(result[0].saturday, vec![("2024-01-06".to_string(), 6.0, Some(7.0))]);
    assert_eq!(result[0].sunday, vec![("2024-01-07".to_string(), 7.0, Some(8.0))]);
}

#[test]
fn build_variant_trends_groups_two_variants() {
    let rows = vec![
        SpeedTrendVariantRow {
            variant_id: "VAR1".into(),
            service_date: "2024-01-01".into(),
            actual_speed_mps: 5.0,
            scheduled_speed_mps: Some(6.0),
        },
        SpeedTrendVariantRow {
            variant_id: "VAR2".into(),
            service_date: "2024-01-01".into(),
            actual_speed_mps: 4.0,
            scheduled_speed_mps: Some(5.0),
        },
    ];
    let result = build_variant_trends(rows);
    assert_eq!(result.len(), 2);
    // sorted by variant_id
    assert_eq!(result[0].variant_id, "VAR1");
    assert_eq!(result[1].variant_id, "VAR2");
    assert_eq!(result[0].weekday[0].1, 5.0);
    assert_eq!(result[1].weekday[0].1, 4.0);
}
```

- [ ] **Step 2: Write the failing integration test**

Add this integration test near the existing `route_speed_trend_by_direction_groups_and_buckets` test:

```rust
#[tokio::test]
async fn route_speed_trend_by_variant_groups_and_buckets() {
    use chrono::{Datelike, Duration, Local};

    fn last_monday(offset_weeks: i64) -> chrono::NaiveDate {
        let today = Local::now().naive_local().date();
        let days_from_monday = today.weekday().num_days_from_monday() as i64;
        today - Duration::days(days_from_monday + offset_weeks * 7)
    }
    fn fmt(d: chrono::NaiveDate) -> String {
        d.format("%Y-%m-%d").to_string()
    }

    let monday = last_monday(1);
    let weekday_date = fmt(monday);
    let saturday_date = fmt(monday + Duration::days(5));

    let td = test_utils::setup().await;
    let db = &td.db;

    sqlx::query(
        "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
         VALUES ('0', 'R1', 0, 6.0, 1, 'now')",
    ).execute(&db.pool).await.unwrap();

    for (date, variant_id, speed) in [
        (weekday_date.as_str(), "VAR1", 5.0_f64),
        (saturday_date.as_str(), "VAR1", 6.0),
        (weekday_date.as_str(), "VAR2", 4.0),
    ] {
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, variant_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', $1, 0, $2, $3, 1, 'now')",
        )
        .bind(date)
        .bind(variant_id)
        .bind(speed)
        .execute(&db.pool)
        .await
        .unwrap();
    }

    let trends = route_speed_trend_by_variant(db, "0", "R1", 28).await.unwrap();

    assert_eq!(trends.len(), 2, "two variants expected");
    assert_eq!(trends[0].variant_id, "VAR1");
    assert_eq!(trends[0].weekday.len(), 1);
    assert!((trends[0].weekday[0].1 - 5.0).abs() < 0.01);
    assert_eq!(trends[0].saturday.len(), 1);
    assert!((trends[0].saturday[0].1 - 6.0).abs() < 0.01);
    assert_eq!(trends[1].variant_id, "VAR2");
    assert_eq!(trends[1].weekday.len(), 1);
    assert!((trends[1].weekday[0].1 - 4.0).abs() < 0.01);
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
cargo test build_variant_trends route_speed_trend_by_variant
```

Expected: compile errors — `SpeedTrendVariantRow`, `build_variant_trends`, and `route_speed_trend_by_variant` are not defined.

- [ ] **Step 4: Add the new structs and functions to `src/speed/mod.rs`**

Add these immediately after the existing `build_direction_trends_with_scheduled` function (around line 319):

```rust
pub struct VariantSpeedTrend {
    pub variant_id: String,
    pub weekday: Vec<(String, f64, Option<f64>)>,
    pub saturday: Vec<(String, f64, Option<f64>)>,
    pub sunday: Vec<(String, f64, Option<f64>)>,
}

#[derive(sqlx::FromRow)]
struct SpeedTrendVariantRow {
    variant_id: String,
    service_date: String,
    actual_speed_mps: f64,
    scheduled_speed_mps: Option<f64>,
}

fn build_variant_trends(rows: Vec<SpeedTrendVariantRow>) -> Vec<VariantSpeedTrend> {
    use chrono::Datelike;
    use std::str::FromStr;

    let mut map: std::collections::HashMap<
        String,
        (
            Vec<(String, f64, Option<f64>)>,
            Vec<(String, f64, Option<f64>)>,
            Vec<(String, f64, Option<f64>)>,
        ),
    > = std::collections::HashMap::new();

    for row in rows {
        let dow = chrono::NaiveDate::from_str(&row.service_date)
            .map(|d| d.weekday().num_days_from_sunday())
            .unwrap_or(1);
        let entry = map.entry(row.variant_id).or_default();
        let point = (row.service_date, row.actual_speed_mps, row.scheduled_speed_mps);
        match dow {
            0 => entry.2.push(point), // Sunday
            6 => entry.1.push(point), // Saturday
            _ => entry.0.push(point), // Weekday
        }
    }

    let mut result: Vec<VariantSpeedTrend> = map
        .into_iter()
        .map(|(variant_id, (weekday, saturday, sunday))| VariantSpeedTrend {
            variant_id,
            weekday,
            saturday,
            sunday,
        })
        .collect();
    result.sort_by(|a, b| a.variant_id.cmp(&b.variant_id));
    result
}

pub async fn route_speed_trend_by_variant(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    days: i64,
) -> Result<Vec<VariantSpeedTrend>> {
    let rows: Vec<SpeedTrendVariantRow> = sqlx::query_as(
        "SELECT d.variant_id,
                d.service_date,
                d.actual_speed_mps,
                r.scheduled_speed_mps
         FROM route_speed_daily d
         LEFT JOIN route_speed r ON r.agency_id = d.agency_id
           AND r.route_id = d.route_id AND r.direction_id = d.direction_id
         WHERE d.agency_id = $1
           AND d.route_id = $2
           AND d.variant_id != ''
           AND d.service_date >= (CURRENT_DATE - $3::INT * INTERVAL '1 day')::TEXT
         ORDER BY d.variant_id, d.service_date",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(days)
    .fetch_all(&db.pool)
    .await?;

    Ok(build_variant_trends(rows))
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cargo test build_variant_trends route_speed_trend_by_variant
```

Expected: all pass.

- [ ] **Step 6: Run all tests**

```bash
cargo test
```

Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add src/speed/mod.rs
git commit -m "feat: add VariantSpeedTrend and route_speed_trend_by_variant"
```

---

### Task 5: Update `compute_route_speed_daily` to group by variant_id

**Files:**
- Modify: `src/speed/mod.rs`
- Update: `.sqlx/` cache (requires running local database)

**Important:** `compute_route_speed_daily` uses `sqlx::query!` (compile-time checked). After changing the INSERT, you must regenerate the `.sqlx/` cache. To do this you need a running local Postgres: run `./dev.sh` in a separate terminal first, then regenerate.

- [ ] **Step 1: Write the failing test**

Find the test `compute_route_speed_daily_stores_actual_speed` (around line 1436). Add this new test immediately after it:

```rust
#[tokio::test]
async fn compute_route_speed_daily_stores_variant_id() {
    let td = test_utils::setup().await;
    let db = td.db;

    sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
        .execute(&db.pool).await.unwrap();
    // Trip T1 with variant_id set
    sqlx::query(
        "INSERT INTO trips (agency_id, trip_id, route_id, service_id, direction_id, variant_id)
         VALUES ('0', 'T1', 'R1', 'WD', 0, 'VAR1')",
    ).execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Stop 1', 45.50, -73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Stop 2', 45.51, -73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')",
    ).execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')",
    ).execute(&db.pool).await.unwrap();

    let t_s1: i64 = 1767225600;
    let t_s2: i64 = t_s1 + 900;
    sqlx::query(
        "INSERT INTO stop_time_events
         (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
         VALUES ('0', '2026-01-01T08:00:00Z', 'T1', 'S1', 1, $1)",
    ).bind(t_s1).execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO stop_time_events
         (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
         VALUES ('0', '2026-01-01T08:15:00Z', 'T1', 'S2', 2, $1)",
    ).bind(t_s2).execute(&db.pool).await.unwrap();

    let date = chrono::NaiveDate::from_ymd_opt(2026, 1, 1).unwrap();
    compute_route_speed_daily(&db, &test_agency(), date).await.unwrap();

    let variant_id: (String,) = sqlx::query_as(
        "SELECT variant_id FROM route_speed_daily WHERE route_id = 'R1' AND service_date = '2026-01-01'",
    )
    .fetch_one(&db.pool)
    .await
    .unwrap();
    assert_eq!(variant_id.0, "VAR1");
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cargo test compute_route_speed_daily_stores_variant_id
```

Expected: `FAILED` — the stored `variant_id` is `''` (the DEFAULT), not `'VAR1'`.

- [ ] **Step 3: Update the combo-discovery query in `compute_route_speed_daily`**

In `src/speed/mod.rs`, find `pub async fn compute_route_speed_daily` (around line 655). Replace the combo-discovery query and its destructuring:

Old combo query (around line 665):
```rust
let combos: Vec<(String, i64)> = sqlx::query_as(
    "SELECT DISTINCT t.route_id, COALESCE(t.direction_id, 0) as direction_id
     FROM stop_time_events ste
     JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
     WHERE ste.agency_id = $1 AND ste.observed_at::TIMESTAMPTZ::DATE = $2::DATE
       AND ste.arrival_time_unix IS NOT NULL",
)
.bind(&agency_id)
.bind(&date_str)
.fetch_all(&db.pool)
.await?;
```

Replace with:
```rust
let combos: Vec<(String, i64, String)> = sqlx::query_as(
    "SELECT DISTINCT
         t.route_id,
         COALESCE(t.direction_id, 0) AS direction_id,
         COALESCE(t.variant_id, '')  AS variant_id
     FROM stop_time_events ste
     JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
     WHERE ste.agency_id = $1 AND ste.observed_at::TIMESTAMPTZ::DATE = $2::DATE
       AND ste.arrival_time_unix IS NOT NULL",
)
.bind(&agency_id)
.bind(&date_str)
.fetch_all(&db.pool)
.await?;
```

Change the loop header from:
```rust
for (route_id, direction_id) in &combos {
```
to:
```rust
for (route_id, direction_id, variant_id) in &combos {
```

- [ ] **Step 4: Update the trips query to filter by variant_id**

In the same function, find the trips query (around line 678). Replace it:

```rust
let trips: Vec<(String,)> = sqlx::query_as(
    "SELECT DISTINCT ste.trip_id
     FROM stop_time_events ste
     JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
     WHERE t.agency_id = $1 AND t.route_id = $2
       AND COALESCE(t.variant_id, '') = $3
       AND ste.observed_at::TIMESTAMPTZ::DATE = $4::DATE
       AND ste.arrival_time_unix IS NOT NULL",
)
.bind(&agency_id)
.bind(route_id)
.bind(variant_id)
.bind(&date_str)
.fetch_all(&db.pool)
.await?;
```

- [ ] **Step 5: Update the INSERT to include variant_id**

Find the `sqlx::query!` INSERT (around line 741). Replace it:

```rust
sqlx::query!(
    "INSERT INTO route_speed_daily
     (agency_id, route_id, service_date, direction_id, variant_id, actual_speed_mps, trip_count, computed_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
     ON CONFLICT (agency_id, route_id, service_date, direction_id, variant_id) DO UPDATE SET
       actual_speed_mps = EXCLUDED.actual_speed_mps,
       trip_count = EXCLUDED.trip_count,
       computed_at = EXCLUDED.computed_at",
    &agency_id,
    route_id,
    date_str,
    direction_id,
    variant_id,
    avg_speed,
    trip_count,
    now,
)
```

- [ ] **Step 6: Regenerate the `.sqlx/` cache**

The `sqlx::query!` macro requires compile-time schema. Start the local DB, then prepare:

```bash
# Terminal 1 — start the local DB
./dev.sh

# Terminal 2 — regenerate cache
dotenvx run -- cargo sqlx prepare
```

Expected: `.sqlx/` directory is updated with a new or modified query JSON file.

- [ ] **Step 7: Run test to verify it passes**

```bash
cargo test compute_route_speed_daily_stores_variant_id
```

Expected: `ok`.

- [ ] **Step 8: Run all tests**

```bash
cargo test
```

Expected: all pass.

- [ ] **Step 9: Commit**

```bash
git add src/speed/mod.rs .sqlx/
git commit -m "feat: compute_route_speed_daily groups trips by variant_id"
```

---

### Task 6: Update handler — `RouteSpeedDetailDirection` and `route_speed_detail`

**Files:**
- Modify: `src/web/handlers.rs`

- [ ] **Step 1: Update imports at the top of `src/web/handlers.rs`**

Find the `use crate::speed` block (around line 15). Replace it:

```rust
use crate::speed::{
    RouteClass, RouteSpeedCard, RouteSpeedSummary, StopSpacing,
    VariantSpeedTrend, build_speed_cards, classify_by_spacing,
    route_speed_by_day_type, route_speed_summary, route_speed_trend_by_variant,
    route_stop_spacings,
};
```

(Removed: `route_speed_trend_by_direction`. Added: `VariantSpeedTrend`, `route_speed_trend_by_variant`.)

- [ ] **Step 2: Update `RouteSpeedDetailDirection`**

Find the `struct RouteSpeedDetailDirection` (around line 23). Replace it:

```rust
struct RouteSpeedDetailDirection {
    pub variant_id: String,
    pub direction_name: String,
    pub first_stop_name: String,
    pub is_primary: bool,
    pub trip_count: i64,
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
    pub weekday_chart_id: String,
    pub saturday_chart_id: String,
    pub sunday_chart_id: String,
    pub weekday_json: String,
    pub saturday_json: String,
    pub sunday_json: String,
}
```

- [ ] **Step 3: Add badge helper methods to `RouteSpeedDetailDirection`**

Add an `impl` block immediately after the struct (before `impl RouteSpeedDetailDirection` that already exists):

Find the existing `impl RouteSpeedDetailDirection` block. Add these two methods alongside `avg_spacing_display` and `avg_spacing_status_class`:

```rust
pub fn direction_badge_label(&self) -> String {
    if self.is_primary {
        format!("Primary · {} trips", self.trip_count)
    } else {
        format!("{} trips", self.trip_count)
    }
}

pub fn direction_badge_variant(&self) -> &'static str {
    if self.is_primary { "oxford" } else { "neutral" }
}
```

- [ ] **Step 4: Update `route_speed_detail` handler body**

Find the `route_speed_detail` async function (around line 93). In that function:

**4a.** Change the `tokio::join!` call (around line 122) from:

```rust
let (spacings_res, trends_res) = tokio::join!(
    route_stop_spacings(&state.db, &agency_id, &route_id),
    route_speed_trend_by_direction(&state.db, &agency_id, &route_id, 28),
);
```

To:

```rust
let (spacings_res, trends_res) = tokio::join!(
    route_stop_spacings(&state.db, &agency_id, &route_id),
    route_speed_trend_by_variant(&state.db, &agency_id, &route_id, 28),
);
```

**4b.** Change the error message (around line 132) from:

```rust
tracing::error!("route_speed_trend_by_direction failed for {agency_id}/{route_id}: {e}");
```

To:

```rust
tracing::error!("route_speed_trend_by_variant failed for {agency_id}/{route_id}: {e}");
```

**4c.** Update the `directions` construction (around line 144). Replace the `.map(|(i, spacing)| { ... })` closure:

```rust
let directions: Vec<RouteSpeedDetailDirection> = spacings
    .into_iter()
    .enumerate()
    .map(|(i, spacing)| {
        let trend = trends
            .iter()
            .find(|t| t.variant_id == spacing.variant_id);
        let (weekday, saturday, sunday) = trend
            .map(|t| (t.weekday.clone(), t.saturday.clone(), t.sunday.clone()))
            .unwrap_or_default();
        RouteSpeedDetailDirection {
            variant_id: spacing.variant_id,
            direction_name: spacing.direction_name,
            first_stop_name: spacing.first_stop_name,
            is_primary: spacing.is_primary,
            trip_count: spacing.trip_count,
            avg_spacing_m: spacing.avg_spacing_m,
            spacings: spacing.spacings,
            weekday_chart_id: format!("weekday-{i}"),
            saturday_chart_id: format!("saturday-{i}"),
            sunday_chart_id: format!("sunday-{i}"),
            weekday_json: trend_to_json(weekday),
            saturday_json: trend_to_json(saturday),
            sunday_json: trend_to_json(sunday),
        }
    })
    .collect();
```

- [ ] **Step 5: Build to verify**

```bash
cargo build
```

Expected: success. Askama and the Rust type system both validate at build time. Fix any remaining compile errors.

- [ ] **Step 6: Commit**

```bash
git add src/web/handlers.rs
git commit -m "feat: route_speed_detail handler uses variant-level trend and spacings"
```

---

### Task 7: Update `route_speed_detail.html` template

**Files:**
- Modify: `templates/route_speed_detail.html`

- [ ] **Step 1: Update the direction card header**

Find the `.direction-header` div (around line 57):

```html
<div class="direction-header">→ {{ direction.direction_name }}</div>
```

Replace with:

```html
<div class="direction-header" style="display:flex;justify-content:space-between;align-items:center;gap:0.5rem;">
  <span>{{ direction.direction_name }}</span>
  {{ ui::badge(variant=direction.direction_badge_variant(), label=direction.direction_badge_label()) }}
</div>
```

- [ ] **Step 2: Build to verify template compiles**

```bash
cargo build
```

Expected: success. Askama compiles the template at build time — any missing method or wrong type shows here. Fix any errors before continuing.

- [ ] **Step 3: Run all tests**

```bash
cargo test
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add templates/route_speed_detail.html
git commit -m "feat: speed detail direction card shows first→last label and variant badge"
```

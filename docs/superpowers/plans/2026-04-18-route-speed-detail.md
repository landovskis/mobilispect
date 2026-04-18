# Route Speed Detail Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a route speed detail page at `/routes/:agency_id/:route_id/speed` showing per-direction stop spacing strips and 28-day speed trend charts broken out by weekday / Saturday / Sunday.

**Architecture:** On-the-fly SQL computation — two new query functions in `src/speed/mod.rs` (stop spacings via LAG window, speed trend from `route_speed_daily`), a new Axum handler + Askama template, and a card link added to `speed_card.html`. No schema changes.

**Tech Stack:** Rust, Axum 0.7, Askama 0.15, sqlx 0.8 (Postgres), Chart.js v4 (CDN), testcontainers for integration tests.

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `src/speed/card.rs` | Add `agency_id`, `route_id` fields to `RouteSpeedCard` |
| Modify | `src/speed/mod.rs` | Add `StopSpacing`, `DirectionStopSpacings`, `DirectionSpeedTrend` structs; add `build_direction_spacings()`, `build_direction_trends()` helpers; add `route_stop_spacings()`, `route_speed_trend_by_direction()` query functions |
| Modify | `src/web/mod.rs` | Extract `build_router()` helper; register new route before existing `:route_id` route |
| Modify | `src/web/handlers.rs` | Add `RouteSpeedDetailDirection` struct, `trend_to_json()` helper, `route_speed_detail()` handler |
| Create | `templates/route_speed_detail.html` | Detail page template — stop strip + Chart.js trend per direction |
| Modify | `templates/speed_card.html` | Wrap card in `<a>` link to detail page |

---

## Task 1: Add `agency_id` + `route_id` to `RouteSpeedCard`

**Files:**
- Modify: `src/speed/card.rs`

- [ ] **Step 1: Write the failing test**

Add to the `#[cfg(test)] mod tests` block in `src/speed/card.rs`:

```rust
#[test]
fn build_speed_cards_carries_agency_id_and_route_id() {
    let rows = vec![make_row("stm", "R99", 0, None)];
    let names = HashMap::new();
    let cards = build_speed_cards(rows, &names);
    assert_eq!(cards[0].agency_id, "stm");
    assert_eq!(cards[0].route_id, "R99");
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
cargo test build_speed_cards_carries_agency_id_and_route_id
```

Expected: compile error — `agency_id` and `route_id` are not fields on `RouteSpeedCard`.

- [ ] **Step 3: Add fields to `RouteSpeedCard` and populate in `build_speed_cards`**

In `src/speed/card.rs`, add fields to the struct after `long_name`:

```rust
pub struct RouteSpeedCard {
    pub idx: usize,
    pub agency_name: String,
    pub agency_id: String,
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub charts: Vec<DirectionSpeedChart>,
    pub avg_scheduled_speed_mps: Option<f64>,
    pub avg_actual_speed_mps: Option<f64>,
}
```

In `build_speed_cards`, update the `cards.push(RouteSpeedCard { ... })` call:

```rust
cards.push(RouteSpeedCard {
    idx: card_idx,
    agency_name,
    agency_id: first.agency_id.clone(),
    route_id: first.route_id.clone(),
    short_name: first.short_name.clone(),
    long_name: first.long_name.clone(),
    charts,
    avg_scheduled_speed_mps,
    avg_actual_speed_mps,
});
```

Also update the test helper `card()` in `src/web/handlers.rs` (the function in `mod tests`) to include the new fields:

```rust
fn card(short_name: &str, scheduled: f64, actual: Option<f64>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        charts: vec![],
        avg_scheduled_speed_mps: Some(scheduled),
        avg_actual_speed_mps: actual,
    }
}

fn card_no_scheduled(short_name: &str) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        charts: vec![],
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

```bash
cargo test build_speed_cards_carries_agency_id_and_route_id
```

Expected: `test build_speed_cards_carries_agency_id_and_route_id ... ok`

Also run all card tests to confirm nothing regressed:

```bash
cargo test --lib speed::card
```

Expected: all existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/speed/card.rs src/web/handlers.rs
git commit -m "feat: add agency_id and route_id fields to RouteSpeedCard"
```

---

## Task 2: Stop spacing structs + `build_direction_spacings()` + unit tests

**Files:**
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Write the failing tests**

Add to `mod tests` in `src/speed/mod.rs`:

```rust
#[test]
fn build_direction_spacings_flags_outliers() {
    // distances: [100, 100, 300] → avg = 166.7, threshold = 1.5 × 166.7 = 250
    // only the 300m segment is an outlier
    let rows = vec![
        StopSpacingRow { direction_id: 0, to_stop_name: "A".into(), distance_m: None,        is_first: true,  is_last: false },
        StopSpacingRow { direction_id: 0, to_stop_name: "B".into(), distance_m: Some(100.0), is_first: false, is_last: false },
        StopSpacingRow { direction_id: 0, to_stop_name: "C".into(), distance_m: Some(100.0), is_first: false, is_last: false },
        StopSpacingRow { direction_id: 0, to_stop_name: "D".into(), distance_m: Some(300.0), is_first: false, is_last: true  },
    ];
    let result = build_direction_spacings(rows);
    assert_eq!(result.len(), 1);
    let s = &result[0].spacings;
    assert!(!s[0].is_outlier, "100m should not be an outlier");
    assert!(!s[1].is_outlier, "100m should not be an outlier");
    assert!(s[2].is_outlier, "300m > 250 threshold should be an outlier");
}

#[test]
fn build_direction_spacings_sets_first_and_direction_names() {
    let rows = vec![
        StopSpacingRow { direction_id: 0, to_stop_name: "Origin".into(),  distance_m: None,        is_first: true,  is_last: false },
        StopSpacingRow { direction_id: 0, to_stop_name: "Middle".into(),  distance_m: Some(200.0), is_first: false, is_last: false },
        StopSpacingRow { direction_id: 0, to_stop_name: "Terminal".into(), distance_m: Some(200.0), is_first: false, is_last: true  },
    ];
    let result = build_direction_spacings(rows);
    assert_eq!(result[0].first_stop_name, "Origin");
    assert_eq!(result[0].direction_name, "Terminal");
}

#[test]
fn build_direction_spacings_groups_two_directions() {
    let rows = vec![
        StopSpacingRow { direction_id: 0, to_stop_name: "A".into(), distance_m: None,        is_first: true,  is_last: false },
        StopSpacingRow { direction_id: 0, to_stop_name: "B".into(), distance_m: Some(300.0), is_first: false, is_last: true  },
        StopSpacingRow { direction_id: 1, to_stop_name: "X".into(), distance_m: None,        is_first: true,  is_last: false },
        StopSpacingRow { direction_id: 1, to_stop_name: "Y".into(), distance_m: Some(400.0), is_first: false, is_last: true  },
    ];
    let result = build_direction_spacings(rows);
    assert_eq!(result.len(), 2);
    assert_eq!(result[0].direction_id, 0);
    assert_eq!(result[1].direction_id, 1);
}

#[test]
fn build_direction_spacings_width_px_max_is_200() {
    let rows = vec![
        StopSpacingRow { direction_id: 0, to_stop_name: "A".into(), distance_m: None,         is_first: true,  is_last: false },
        StopSpacingRow { direction_id: 0, to_stop_name: "B".into(), distance_m: Some(100.0),  is_first: false, is_last: false },
        StopSpacingRow { direction_id: 0, to_stop_name: "C".into(), distance_m: Some(1000.0), is_first: false, is_last: true  },
    ];
    let result = build_direction_spacings(rows);
    let spacings = &result[0].spacings;
    assert_eq!(spacings[1].width_px, 200, "max distance should map to 200px");
    assert!(spacings[0].width_px < 200, "smaller distance should map to less than 200px");
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

```bash
cargo test build_direction_spacings
```

Expected: compile errors — `StopSpacingRow`, `build_direction_spacings`, `StopSpacing`, `DirectionStopSpacings` not defined.

- [ ] **Step 3: Add the structs and `build_direction_spacings()` to `src/speed/mod.rs`**

Add these after the `haversine_meters` and `parse_time_secs` functions (around line 115), before `compute_route_speed`:

```rust
/// A single stop-to-stop segment along a route direction.
pub struct StopSpacing {
    pub to_stop_name: String,
    /// Haversine distance from the previous stop to this stop, in metres.
    pub distance_m: f64,
    /// True when `distance_m > 1.5 × avg_spacing_m` for this direction.
    pub is_outlier: bool,
    /// Pixel width for the strip segment (scaled so the longest segment = 200px).
    pub width_px: u32,
}

/// All stop spacings for one direction of a route.
pub struct DirectionStopSpacings {
    pub direction_id: i64,
    /// Name of the terminal (last) stop — used as the direction label.
    pub direction_name: String,
    /// Name of the first stop — rendered as the leftmost dot in the strip.
    pub first_stop_name: String,
    /// Mean distance between consecutive stops, in metres.
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
}

/// Raw row returned by the stop spacings SQL query.
#[derive(sqlx::FromRow)]
struct StopSpacingRow {
    direction_id: i64,
    to_stop_name: String,
    distance_m: Option<f64>,
    is_first: bool,
    is_last: bool,
}

impl StopSpacing {
    pub fn distance_display(&self) -> String {
        if self.distance_m >= 1000.0 {
            format!("{:.1} km", self.distance_m / 1000.0)
        } else {
            format!("{:.0} m", self.distance_m)
        }
    }
}

fn build_direction_spacings(rows: Vec<StopSpacingRow>) -> Vec<DirectionStopSpacings> {
    let mut result: Vec<DirectionStopSpacings> = Vec::new();
    let mut i = 0;
    while i < rows.len() {
        let dir_id = rows[i].direction_id;
        let end = rows[i..]
            .iter()
            .position(|r| r.direction_id != dir_id)
            .map(|p| i + p)
            .unwrap_or(rows.len());
        let dir_rows = &rows[i..end];

        let first_stop_name = dir_rows
            .iter()
            .find(|r| r.is_first)
            .map(|r| r.to_stop_name.clone())
            .unwrap_or_default();

        let direction_name = dir_rows
            .iter()
            .find(|r| r.is_last)
            .map(|r| r.to_stop_name.clone())
            .unwrap_or_default();

        let distances: Vec<(String, f64)> = dir_rows
            .iter()
            .filter_map(|r| r.distance_m.map(|d| (r.to_stop_name.clone(), d)))
            .collect();

        let avg_spacing_m = if distances.is_empty() {
            0.0
        } else {
            distances.iter().map(|(_, d)| d).sum::<f64>() / distances.len() as f64
        };

        let max_dist = distances
            .iter()
            .map(|(_, d)| *d)
            .fold(0.0_f64, f64::max);
        let threshold = avg_spacing_m * 1.5;

        let spacings = distances
            .into_iter()
            .map(|(name, dist)| StopSpacing {
                to_stop_name: name,
                distance_m: dist,
                is_outlier: dist > threshold,
                width_px: ((dist / max_dist.max(1.0)) * 200.0) as u32,
            })
            .collect();

        result.push(DirectionStopSpacings {
            direction_id: dir_id,
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

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
cargo test build_direction_spacings
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/speed/mod.rs src/speed/card.rs
git commit -m "feat: add StopSpacing structs and build_direction_spacings helper"
```

---

## Task 3: `route_stop_spacings()` SQL query + integration test

**Files:**
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Write the failing integration test**

Add to `mod tests` in `src/speed/mod.rs`:

```rust
#[tokio::test]
async fn route_stop_spacings_returns_correct_order_and_distances() {
    let td = test_utils::setup().await;
    let db = &td.db;

    sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '1', 'Route 1', 3)")
        .execute(&db.pool).await.unwrap();
    // direction 0 trip with 3 stops in sequence order
    sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Terminus')")
        .execute(&db.pool).await.unwrap();
    // S1 and S2 are 0.01° apart in latitude ≈ 1111 m; S2 and S3 are 0.001° ≈ 111 m
    sqlx::query("INSERT INTO stops VALUES ('test', 'S1', 'First Stop',  45.500, -73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('test', 'S2', 'Middle Stop', 45.510, -73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('test', 'S3', 'Terminus',    45.511, -73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S1', 1, '08:00:00', '08:00:00')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S2', 2, '08:05:00', '08:05:00')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S3', 3, '08:07:00', '08:07:00')")
        .execute(&db.pool).await.unwrap();

    let directions = route_stop_spacings(db, "test", "R1").await.unwrap();

    assert_eq!(directions.len(), 1, "one direction expected");
    let dir = &directions[0];
    assert_eq!(dir.first_stop_name, "First Stop");
    assert_eq!(dir.direction_name, "Terminus");
    assert_eq!(dir.spacings.len(), 2, "two segments (S1→S2, S2→S3)");
    assert_eq!(dir.spacings[0].to_stop_name, "Middle Stop");
    assert_eq!(dir.spacings[1].to_stop_name, "Terminus");
    // S1→S2: ~0.01° lat ≈ 1111 m; allow ±50 m tolerance
    assert!(
        (dir.spacings[0].distance_m - 1111.0).abs() < 50.0,
        "S1→S2 should be ~1111 m, got {}",
        dir.spacings[0].distance_m
    );
    // S2→S3: ~0.001° lat ≈ 111 m
    assert!(
        (dir.spacings[1].distance_m - 111.0).abs() < 10.0,
        "S2→S3 should be ~111 m, got {}",
        dir.spacings[1].distance_m
    );
    // avg ≈ 611 m, threshold ≈ 917 m → S1→S2 (1111 m) is an outlier
    assert!(dir.spacings[0].is_outlier, "S1→S2 should be flagged as outlier");
    assert!(!dir.spacings[1].is_outlier, "S2→S3 should not be an outlier");
}

#[tokio::test]
async fn route_stop_spacings_returns_empty_for_unknown_route() {
    let td = test_utils::setup().await;
    let result = route_stop_spacings(&td.db, "test", "NONEXISTENT").await.unwrap();
    assert!(result.is_empty());
}
```

- [ ] **Step 2: Run to confirm they fail**

```bash
cargo test route_stop_spacings
```

Expected: compile error — `route_stop_spacings` not defined.

- [ ] **Step 3: Add `route_stop_spacings()` to `src/speed/mod.rs`**

Add after `build_direction_spacings` (before `compute_route_speed`):

```rust
/// Fetch per-stop spacing data for a single route, grouped by direction.
/// Returns one `DirectionStopSpacings` per direction. Empty if route has no trips.
pub async fn route_stop_spacings(
    db: &Database,
    agency_id: &str,
    route_id: &str,
) -> Result<Vec<DirectionStopSpacings>> {
    let rows: Vec<StopSpacingRow> = sqlx::query_as(
        "WITH rep_trip AS (
            SELECT DISTINCT ON (COALESCE(direction_id, 0))
                trip_id, COALESCE(direction_id, 0) AS direction_id
            FROM trips
            WHERE agency_id = $1 AND route_id = $2
            ORDER BY COALESCE(direction_id, 0), trip_id
        ),
        ordered AS (
            SELECT
                rt.direction_id,
                s.stop_name,
                s.stop_lat, s.stop_lon,
                ROW_NUMBER() OVER (PARTITION BY rt.direction_id ORDER BY ss.stop_sequence) AS rn,
                COUNT(*)    OVER (PARTITION BY rt.direction_id)                            AS total_stops
            FROM rep_trip rt
            JOIN scheduled_stops ss ON ss.agency_id = $1 AND ss.trip_id = rt.trip_id
            JOIN stops s ON s.agency_id = $1 AND s.stop_id = ss.stop_id
        ),
        with_prev AS (
            SELECT
                direction_id, stop_name, rn, total_stops, stop_lat, stop_lon,
                LAG(stop_lat) OVER (PARTITION BY direction_id ORDER BY rn) AS prev_lat,
                LAG(stop_lon) OVER (PARTITION BY direction_id ORDER BY rn) AS prev_lon
            FROM ordered
        )
        SELECT
            direction_id,
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
        ORDER BY direction_id, rn",
    )
    .bind(agency_id)
    .bind(route_id)
    .fetch_all(&db.pool)
    .await?;

    Ok(build_direction_spacings(rows))
}
```

- [ ] **Step 4: Run and confirm tests pass**

```bash
cargo test route_stop_spacings
```

Expected: both tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/speed/mod.rs
git commit -m "feat: add route_stop_spacings query function"
```

---

## Task 4: Speed trend structs + `build_direction_trends()` + unit tests

**Files:**
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Write the failing tests**

Add to `mod tests` in `src/speed/mod.rs`:

```rust
#[test]
fn build_direction_trends_buckets_weekday_saturday_sunday() {
    // 2024-01-01 = Monday (weekday), 2024-01-06 = Saturday, 2024-01-07 = Sunday
    let rows = vec![
        SpeedTrendRow { direction_id: 0, service_date: "2024-01-01".into(), actual_speed_mps: 5.0 },
        SpeedTrendRow { direction_id: 0, service_date: "2024-01-06".into(), actual_speed_mps: 6.0 },
        SpeedTrendRow { direction_id: 0, service_date: "2024-01-07".into(), actual_speed_mps: 7.0 },
    ];
    let result = build_direction_trends(rows);
    assert_eq!(result.len(), 1);
    let t = &result[0];
    assert_eq!(t.weekday,  vec![("2024-01-01".to_string(), 5.0)]);
    assert_eq!(t.saturday, vec![("2024-01-06".to_string(), 6.0)]);
    assert_eq!(t.sunday,   vec![("2024-01-07".to_string(), 7.0)]);
}

#[test]
fn build_direction_trends_groups_two_directions() {
    let rows = vec![
        SpeedTrendRow { direction_id: 0, service_date: "2024-01-01".into(), actual_speed_mps: 5.0 },
        SpeedTrendRow { direction_id: 1, service_date: "2024-01-01".into(), actual_speed_mps: 4.0 },
    ];
    let result = build_direction_trends(rows);
    assert_eq!(result.len(), 2);
    // sorted by direction_id
    assert_eq!(result[0].direction_id, 0);
    assert_eq!(result[1].direction_id, 1);
    assert_eq!(result[0].weekday[0].1, 5.0);
    assert_eq!(result[1].weekday[0].1, 4.0);
}
```

- [ ] **Step 2: Run to confirm they fail**

```bash
cargo test build_direction_trends
```

Expected: compile error — `SpeedTrendRow`, `build_direction_trends`, `DirectionSpeedTrend` not defined.

- [ ] **Step 3: Add structs and `build_direction_trends()` to `src/speed/mod.rs`**

Add after `build_direction_spacings` (before `route_stop_spacings`):

```rust
/// Speed trend data for one direction of a route.
pub struct DirectionSpeedTrend {
    pub direction_id: i64,
    /// (service_date as "YYYY-MM-DD", actual_speed_mps)
    pub weekday: Vec<(String, f64)>,
    pub saturday: Vec<(String, f64)>,
    pub sunday: Vec<(String, f64)>,
}

/// Raw row returned by the speed trend SQL query.
#[derive(sqlx::FromRow)]
struct SpeedTrendRow {
    direction_id: i64,
    service_date: String,
    actual_speed_mps: f64,
}

fn build_direction_trends(rows: Vec<SpeedTrendRow>) -> Vec<DirectionSpeedTrend> {
    use chrono::Datelike;
    use std::str::FromStr;

    let mut map: std::collections::HashMap<
        i64,
        (Vec<(String, f64)>, Vec<(String, f64)>, Vec<(String, f64)>),
    > = std::collections::HashMap::new();

    for row in rows {
        // num_days_from_sunday(): 0 = Sunday, 1 = Monday, …, 6 = Saturday
        let dow = chrono::NaiveDate::from_str(&row.service_date)
            .map(|d| d.weekday().num_days_from_sunday())
            .unwrap_or(1);
        let entry = map.entry(row.direction_id).or_default();
        let point = (row.service_date, row.actual_speed_mps);
        match dow {
            0 => entry.2.push(point), // Sunday
            6 => entry.1.push(point), // Saturday
            _ => entry.0.push(point), // Weekday
        }
    }

    let mut result: Vec<DirectionSpeedTrend> = map
        .into_iter()
        .map(|(direction_id, (weekday, saturday, sunday))| DirectionSpeedTrend {
            direction_id,
            weekday,
            saturday,
            sunday,
        })
        .collect();
    result.sort_by_key(|t| t.direction_id);
    result
}
```

- [ ] **Step 4: Run and confirm tests pass**

```bash
cargo test build_direction_trends
```

Expected: both tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/speed/mod.rs
git commit -m "feat: add DirectionSpeedTrend struct and build_direction_trends helper"
```

---

## Task 5: `route_speed_trend_by_direction()` SQL query + integration test

**Files:**
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Write the failing integration test**

Add to `mod tests`:

```rust
#[tokio::test]
async fn route_speed_trend_by_direction_groups_and_buckets() {
    let td = test_utils::setup().await;
    let db = &td.db;

    // 2024-01-01 = Monday (weekday), 2024-01-06 = Saturday, 2024-01-07 = Sunday
    for (date, dir, speed) in [
        ("2024-01-01", 0_i64, 5.0_f64),
        ("2024-01-06", 0, 6.0),
        ("2024-01-07", 0, 7.0),
        ("2024-01-01", 1, 4.0),
    ] {
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('test', 'R1', $1, $2, $3, 1, 'now')",
        )
        .bind(date)
        .bind(dir)
        .bind(speed)
        .execute(&db.pool)
        .await
        .unwrap();
    }

    let trends = route_speed_trend_by_direction(db, "test", "R1", 365).await.unwrap();
    assert_eq!(trends.len(), 2, "two directions");

    let dir0 = trends.iter().find(|t| t.direction_id == 0).unwrap();
    assert_eq!(dir0.weekday.len(), 1);
    assert_eq!(dir0.weekday[0].0, "2024-01-01");
    assert!((dir0.weekday[0].1 - 5.0).abs() < 0.001);
    assert_eq!(dir0.saturday.len(), 1);
    assert_eq!(dir0.sunday.len(), 1);

    let dir1 = trends.iter().find(|t| t.direction_id == 1).unwrap();
    assert_eq!(dir1.weekday.len(), 1);
    assert!(dir1.saturday.is_empty());
    assert!(dir1.sunday.is_empty());
}

#[tokio::test]
async fn route_speed_trend_by_direction_returns_empty_when_no_data() {
    let td = test_utils::setup().await;
    let result = route_speed_trend_by_direction(&td.db, "test", "R1", 28).await.unwrap();
    assert!(result.is_empty());
}
```

- [ ] **Step 2: Run to confirm they fail**

```bash
cargo test route_speed_trend_by_direction
```

Expected: compile error — `route_speed_trend_by_direction` not defined.

- [ ] **Step 3: Add `route_speed_trend_by_direction()` to `src/speed/mod.rs`**

Add after `route_stop_spacings`:

```rust
/// Fetch per-day actual speed for a single route, grouped by direction and day type.
/// `days` controls how many days back to look (use 28 to match other data windows).
pub async fn route_speed_trend_by_direction(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    days: i64,
) -> Result<Vec<DirectionSpeedTrend>> {
    let rows: Vec<SpeedTrendRow> = sqlx::query_as(
        "SELECT direction_id, service_date, actual_speed_mps
         FROM route_speed_daily
         WHERE agency_id = $1
           AND route_id = $2
           AND service_date::date >= (CURRENT_DATE - $3::INT * INTERVAL '1 day')
         ORDER BY direction_id, service_date",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(days)
    .fetch_all(&db.pool)
    .await?;

    Ok(build_direction_trends(rows))
}
```

Also export the new public symbols from `src/speed/mod.rs` by adding to the `pub use card::...` line or the existing exports. Add these exports near the top of `mod.rs` (alongside the existing `pub use card::...`):

```rust
pub use self::{
    route_stop_spacings, route_speed_trend_by_direction,
    DirectionSpeedTrend, DirectionStopSpacings, StopSpacing,
};
```

Or simply ensure the functions and structs are `pub` (they already are from Step 3 and Step 4).

- [ ] **Step 4: Run and confirm tests pass**

```bash
cargo test route_speed_trend_by_direction
```

Expected: both tests pass.

Also run the full test suite to check nothing is broken:

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/speed/mod.rs
git commit -m "feat: add route_speed_trend_by_direction query function"
```

---

## Task 6: Handler + routing + E2E tests

**Files:**
- Modify: `src/web/mod.rs`
- Modify: `src/web/handlers.rs`

- [ ] **Step 1: Extract `build_router()` from `serve()` in `src/web/mod.rs`**

Replace the current `serve` function body with:

```rust
pub fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/", get(handlers::dashboard))
        .route("/report", get(handlers::report))
        .route("/speed", get(handlers::speed_page))
        .route("/scorecard", get(handlers::scorecard))
        // /speed route registered BEFORE bare :route_id to avoid shadowing
        .route("/routes/:agency_id/:route_id/speed", get(handlers::route_speed_detail))
        .route("/routes/:agency_id/:route_id", get(handlers::route_detail))
        .route("/hotspots", get(handlers::hotspots))
        .route("/api/routes", get(handlers::api_routes))
        .route("/api/routes/speed", get(handlers::api_route_speed))
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

pub async fn serve(db: &Database, config: &Config) -> Result<()> {
    let state = AppState {
        db: db.clone(),
        config: config.clone(),
    };
    let app = build_router(state);
    let listener = tokio::net::TcpListener::bind(&config.bind_address).await?;
    info!("Dashboard available at http://{}", config.bind_address);
    axum::serve(listener, app).await?;
    Ok(())
}
```

- [ ] **Step 2: Write the failing E2E tests in `src/web/handlers.rs`**

Add a new `mod e2e_tests` block at the bottom of `src/web/handlers.rs` (after the existing `mod tests`):

```rust
#[cfg(test)]
mod e2e_tests {
    use super::*;
    use crate::config::{AgencyConfig, Config};
    use crate::db::test_utils;
    use crate::web::build_router;
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use tower::ServiceExt;

    fn test_config() -> Config {
        Config {
            agencies: vec![AgencyConfig {
                slug: "test".to_string(),
                name: "Test Agency".to_string(),
                gtfs_static_url: String::new(),
                gtfs_rt_vehicle_positions_url: String::new(),
                gtfs_rt_trip_updates_url: String::new(),
                gtfs_api_key: None,
                agency_utc_offset: "-04:00".to_string(),
            }],
            database_url: String::new(),
            poll_interval_secs: 30,
            bind_address: "0.0.0.0:3000".to_string(),
            on_time_early_threshold_secs: -60,
            on_time_late_threshold_secs: 300,
            retention_days: 30,
        }
    }

    #[tokio::test]
    async fn route_speed_detail_returns_200_with_direction_name() {
        let td = test_utils::setup().await;
        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '1', 'Route 1', 3)")
            .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Downtown')")
            .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S1', 'Main St',  45.50, -73.50)")
            .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S2', 'Downtown', 45.51, -73.50)")
            .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S1', 1, '08:00:00', '08:00:00')")
            .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S2', 2, '08:10:00', '08:10:00')")
            .execute(&td.db.pool).await.unwrap();

        let state = AppState { db: td.db, config: test_config() };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/routes/test/R1/speed")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);

        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024).await.unwrap();
        let html = String::from_utf8(bytes.to_vec()).unwrap();
        assert!(html.contains("Downtown"), "HTML should contain terminal stop name 'Downtown'");
        assert!(html.contains("Route 1"), "HTML should contain route long name");
    }

    #[tokio::test]
    async fn route_speed_detail_returns_404_for_unknown_route() {
        let td = test_utils::setup().await;
        let state = AppState { db: td.db, config: test_config() };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/routes/test/NONEXISTENT/speed")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }
}
```

- [ ] **Step 3: Run to confirm E2E tests fail**

```bash
cargo test e2e_tests
```

Expected: compile errors — `route_speed_detail` handler and `build_router` not available as expected.

- [ ] **Step 4: Add `RouteSpeedDetailDirection`, `trend_to_json`, and `route_speed_detail` to `src/web/handlers.rs`**

Update the imports at the top of `handlers.rs` to add the new speed symbols:

```rust
use crate::speed::{
    DirectionStopSpacings, RouteSpeedCard, RouteSpeedSummary, StopSpacing,
    build_speed_cards, route_speed_by_day_type, route_speed_summary,
    route_stop_spacings, route_speed_trend_by_direction,
};
```

Add these new types and functions before the `dashboard` handler:

```rust
struct RouteSpeedDetailDirection {
    pub chart_id: String,
    pub direction_name: String,
    pub first_stop_name: String,
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
    pub weekday_json: String,
    pub saturday_json: String,
    pub sunday_json: String,
}

impl RouteSpeedDetailDirection {
    pub fn avg_spacing_display(&self) -> String {
        if self.avg_spacing_m >= 1000.0 {
            format!("{:.1} km", self.avg_spacing_m / 1000.0)
        } else {
            format!("{:.0} m", self.avg_spacing_m)
        }
    }
}

#[derive(Template)]
#[template(path = "route_speed_detail.html")]
struct RouteSpeedDetailTemplate {
    short_name: String,
    long_name: String,
    agency_id: String,
    directions: Vec<RouteSpeedDetailDirection>,
}

fn trend_to_json(points: Vec<(String, f64)>) -> String {
    #[derive(serde::Serialize)]
    struct TrendPoint {
        date: String,
        speed_kmh: f64,
    }
    let pts: Vec<TrendPoint> = points
        .into_iter()
        .map(|(date, mps)| TrendPoint {
            date,
            speed_kmh: (mps * 3.6 * 10.0).round() / 10.0,
        })
        .collect();
    serde_json::to_string(&pts).unwrap_or_default()
}

pub async fn route_speed_detail(
    State(state): State<AppState>,
    axum::extract::Path((agency_id, route_id)): axum::extract::Path<(String, String)>,
) -> axum::response::Response {
    use axum::response::IntoResponse;

    let route_info: Option<(String, String)> = sqlx::query_as(
        "SELECT short_name, long_name FROM routes WHERE agency_id = $1 AND route_id = $2",
    )
    .bind(&agency_id)
    .bind(&route_id)
    .fetch_optional(&state.db.pool)
    .await
    .unwrap_or(None);

    let (short_name, long_name) = match route_info {
        Some(r) => r,
        None => {
            return (
                axum::http::StatusCode::NOT_FOUND,
                Html("<h1>Not Found</h1>".to_string()),
            )
                .into_response()
        }
    };

    let (spacings_res, trends_res) = tokio::join!(
        route_stop_spacings(&state.db, &agency_id, &route_id),
        route_speed_trend_by_direction(&state.db, &agency_id, &route_id, 28),
    );

    let spacings = spacings_res.unwrap_or_default();
    let trends = trends_res.unwrap_or_default();

    if spacings.is_empty() {
        return (
            axum::http::StatusCode::NOT_FOUND,
            Html("<h1>Not Found</h1>".to_string()),
        )
            .into_response();
    }

    let directions: Vec<RouteSpeedDetailDirection> = spacings
        .into_iter()
        .enumerate()
        .map(|(i, spacing)| {
            let trend = trends.iter().find(|t| t.direction_id == spacing.direction_id);
            let (weekday, saturday, sunday) = trend
                .map(|t| (t.weekday.clone(), t.saturday.clone(), t.sunday.clone()))
                .unwrap_or_default();
            RouteSpeedDetailDirection {
                chart_id: format!("dir-{i}"),
                direction_name: spacing.direction_name,
                first_stop_name: spacing.first_stop_name,
                avg_spacing_m: spacing.avg_spacing_m,
                spacings: spacing.spacings,
                weekday_json: trend_to_json(weekday),
                saturday_json: trend_to_json(saturday),
                sunday_json: trend_to_json(sunday),
            }
        })
        .collect();

    let tmpl = RouteSpeedDetailTemplate {
        short_name,
        long_name,
        agency_id,
        directions,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
    .into_response()
}
```

Also add `use sqlx;` to the imports at the top of `handlers.rs` if not already present (sqlx is already used elsewhere via the db pool).

- [ ] **Step 5: Run E2E tests and confirm they pass**

```bash
cargo test e2e_tests
```

Expected: both tests pass.

Then run the full suite:

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/web/mod.rs src/web/handlers.rs
git commit -m "feat: add route_speed_detail handler and register /routes/:agency_id/:route_id/speed"
```

---

## Task 7: Create `templates/route_speed_detail.html`

**Files:**
- Create: `templates/route_speed_detail.html`

Note: Askama compiles templates at build time. If the template file is missing, `cargo build` will fail. There is no isolated "write failing test" step for the template itself — the E2E test from Task 6 already covers it. Create the template, then run the build to verify it compiles.

- [ ] **Step 1: Create `templates/route_speed_detail.html`**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Mobilispect — Route {{ short_name }} Speed Detail</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
           background: #f5f5f5; color: #222; }
    header { background: #1a1a2e; color: white; padding: 1rem 2rem;
             display: flex; justify-content: space-between; align-items: center; }
    header h1 { font-size: 1.4rem; font-weight: 600; }
    header a { color: #aaa; font-size: 0.85rem; text-decoration: none; }
    header a:hover { color: white; }
    .container { max-width: 960px; margin: 2rem auto; padding: 0 1rem; }
    .direction-card { background: white; border-radius: 8px;
                      box-shadow: 0 1px 3px rgba(0,0,0,0.08);
                      margin-bottom: 2rem; overflow: hidden; }
    .direction-header { background: #ecf0f1; padding: 0.75rem 1.5rem;
                        font-weight: 600; font-size: 0.95rem; color: #2c3e50;
                        border-bottom: 1px solid #dde; }
    .direction-body { padding: 1.25rem 1.5rem; }
    .section-label { font-size: 0.7rem; text-transform: uppercase;
                     letter-spacing: 0.08em; color: #888;
                     margin-bottom: 0.75rem; font-weight: 600; }
    /* Stop strip */
    .stop-strip-wrap { overflow-x: auto; padding-bottom: 0.5rem; margin-bottom: 0.5rem; }
    .stop-strip { display: flex; align-items: flex-start; min-width: max-content; padding: 4px 0; }
    .stop-node { display: flex; flex-direction: column; align-items: center; width: 56px; flex-shrink: 0; }
    .stop-dot { width: 10px; height: 10px; border-radius: 50%;
                background: #2980b9; margin-top: 6px; flex-shrink: 0; }
    .stop-dot.terminal { background: #27ae60; }
    .stop-name { font-size: 9px; color: #555; text-align: center;
                 margin-top: 5px; line-height: 1.3; max-width: 52px; word-break: break-word; }
    .stop-seg { display: flex; flex-direction: column; align-items: center;
                justify-content: flex-start; padding-top: 10px; flex-shrink: 0; }
    .seg-line { height: 3px; border-radius: 2px; background: #2980b9; }
    .seg-line.outlier { background: #e67e22; height: 5px; }
    .seg-label { font-size: 9px; color: #888; margin-top: 3px; white-space: nowrap; }
    .seg-label.outlier { color: #e67e22; font-weight: 600; }
    .strip-caption { font-size: 0.75rem; color: #888; margin-bottom: 1.25rem; }
    /* Chart */
    .section-divider { height: 1px; background: #eef; margin: 1.25rem 0; }
    canvas { width: 100% !important; }
    .chart-legend { display: flex; gap: 1rem; margin-top: 0.5rem; flex-wrap: wrap; }
    .legend-item { display: flex; align-items: center; gap: 0.3rem;
                   font-size: 0.75rem; color: #555; }
    .legend-swatch { width: 20px; height: 3px; border-radius: 2px; }
    footer { text-align: center; padding: 2rem; color: #aaa; font-size: 0.8rem; }
  </style>
</head>
<body>
  <header>
    <h1>Route {{ short_name }} — {{ long_name }}</h1>
    <a href="/speed?agency={{ agency_id }}">← Back to speed overview</a>
  </header>

  <div class="container">
    {% for direction in &directions %}
    <div class="direction-card">
      <div class="direction-header">→ {{ direction.direction_name }}</div>
      <div class="direction-body">

        <div class="section-label">Stop spacing</div>
        <div class="stop-strip-wrap">
          <div class="stop-strip">
            <div class="stop-node">
              <div class="stop-dot"></div>
              <div class="stop-name">{{ direction.first_stop_name }}</div>
            </div>
            {% for spacing in &direction.spacings %}
            <div class="stop-seg" style="width: {{ spacing.width_px }}px;">
              <div class="seg-line{% if spacing.is_outlier %} outlier{% endif %}"
                   style="width: {{ spacing.width_px }}px;"></div>
              <div class="seg-label{% if spacing.is_outlier %} outlier{% endif %}">
                {{ spacing.distance_display() }}{% if spacing.is_outlier %} !{% endif %}
              </div>
            </div>
            <div class="stop-node">
              <div class="stop-dot{% if loop.last %} terminal{% endif %}"></div>
              <div class="stop-name">{{ spacing.to_stop_name }}</div>
            </div>
            {% endfor %}
          </div>
        </div>
        <div class="strip-caption">
          Average spacing: {{ direction.avg_spacing_display() }} · Orange segments are 1.5× above average
        </div>

        <div class="section-divider"></div>

        <div class="section-label">Speed trend — last 28 days</div>
        <canvas id="{{ direction.chart_id }}" height="80"></canvas>
        <div class="chart-legend">
          <div class="legend-item">
            <div class="legend-swatch" style="background:#2980b9"></div> Weekday
          </div>
          <div class="legend-item">
            <div class="legend-swatch" style="background:#27ae60"></div> Saturday
          </div>
          <div class="legend-item">
            <div class="legend-swatch" style="background:#e67e22"></div> Sunday
          </div>
        </div>

      </div>
    </div>
    {% endfor %}
  </div>

  <footer>Mobilispect · Route {{ short_name }} Speed Detail</footer>

  <script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
  <script>
  {% for direction in &directions %}
  (function() {
    const weekday  = {{ direction.weekday_json|safe }};
    const saturday = {{ direction.saturday_json|safe }};
    const sunday   = {{ direction.sunday_json|safe }};

    const lineOpts = (color, dash) => ({
      borderColor: color,
      backgroundColor: 'transparent',
      tension: 0.3,
      pointRadius: 2,
      spanGaps: true,
      borderDash: dash || [],
    });

    // Merge all dates for the x-axis labels
    const allDates = [...new Set([
      ...weekday.map(d => d.date),
      ...saturday.map(d => d.date),
      ...sunday.map(d => d.date),
    ])].sort();

    function toSparse(points) {
      const map = Object.fromEntries(points.map(p => [p.date, p.speed_kmh]));
      return allDates.map(d => map[d] ?? null);
    }

    new Chart(document.getElementById('{{ direction.chart_id }}'), {
      type: 'line',
      data: {
        labels: allDates,
        datasets: [
          { label: 'Weekday',  data: toSparse(weekday),  ...lineOpts('#2980b9') },
          { label: 'Saturday', data: toSparse(saturday), ...lineOpts('#27ae60', [5, 3]) },
          { label: 'Sunday',   data: toSparse(sunday),   ...lineOpts('#e67e22', [2, 3]) },
        ],
      },
      options: {
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: false, title: { display: true, text: 'km/h' } },
          x: { ticks: { maxTicksLimit: 8 } },
        },
      },
    });
  })();
  {% endfor %}
  </script>
</body>
</html>
```

- [ ] **Step 2: Build to confirm template compiles**

```bash
cargo build
```

Expected: build succeeds. If Askama reports a template error, fix the offending expression and rebuild.

- [ ] **Step 3: Run the full test suite**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add templates/route_speed_detail.html
git commit -m "feat: add route_speed_detail.html template"
```

---

## Task 8: Link speed cards to the detail page

**Files:**
- Modify: `templates/speed_card.html`

- [ ] **Step 1: Wrap the card in an anchor tag**

Replace the entire contents of `templates/speed_card.html` with:

```html
<a href="/routes/{{ card.agency_id }}/{{ card.route_id }}/speed"
   style="text-decoration: none; color: inherit; display: block;">
<div class="card">
  <div class="route-num">{{ card.agency_name }} {{ card.short_name }}</div>
  <div class="route-name">{{ card.long_name }}</div>
  <div class="chart-row">
    {% for chart in &card.charts %}
    <div class="chart-col">
      <div class="chart-title">{{ chart.title }}</div>
      <canvas id="{{ chart.chart_id }}" height="160"></canvas>
      <div class="stop-spacing">Avg stop spacing: {{ chart.avg_stop_spacing_display() }}</div>
    </div>
    {% endfor %}
  </div>
</div>
</a>
<script>
{% for chart in &card.charts %}
new Chart(document.getElementById('{{ chart.chart_id }}'), {
  type: 'bar',
  data: {
    labels: ['Weekday', 'Saturday', 'Sunday'],
    datasets: {{ chart.chart_json|safe }}
  },
  options: {
    responsive: true,
    plugins: {
      legend: { position: 'top', labels: { boxWidth: 10, font: { size: 11 } } },
      title: { display: true, text: 'Speed (km/h)' }
    },
    scales: {
      y: { beginAtZero: true, title: { display: true, text: 'km/h' }, ticks: { font: { size: 10 } } },
      x: { ticks: { font: { size: 10 } } }
    }
  }
});
{% endfor %}
</script>
```

- [ ] **Step 2: Build to confirm template compiles**

```bash
cargo build
```

Expected: build succeeds.

- [ ] **Step 3: Run the full test suite**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add templates/speed_card.html
git commit -m "feat: link speed cards to route speed detail page"
```

---

## Done

At this point:
- `/speed` cards are clickable and navigate to `/routes/:agency_id/:route_id/speed`
- The detail page shows per-direction stop spacing strips with proportional widths and outlier highlighting
- The detail page shows per-direction 28-day speed trend line charts with separate lines for weekday / Saturday / Sunday
- All unit, integration, and E2E tests pass

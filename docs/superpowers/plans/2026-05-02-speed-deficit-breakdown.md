# Speed Deficit Breakdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Speed factors" waterfall chart to the route speed detail page that decomposes the gap between scheduled and actual speed into dwell time excess, bunching, and running time loss.

**Architecture:** New `src/speed/breakdown.rs` holds all computation logic (private query helpers + public `compute_speed_deficit_breakdown`). The handler calls this once per direction and attaches the result to `RouteSpeedDetailDirection`. The template renders a Chart.js horizontal floating-bar waterfall.

**Tech Stack:** Rust/Axum, sqlx (Postgres), Askama templates, Chart.js 4 (already loaded on page)

---

### Task 1: Create breakdown module and data types

**Files:**
- Create: `src/speed/breakdown.rs`
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Write the failing tests**

Add at the bottom of `src/speed/breakdown.rs` (create the file first with just the test module):

```rust
// src/speed/breakdown.rs

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deficit_factor_delta_kmh_converts_mps() {
        let f = DeficitFactor {
            label: "Test",
            delta_mps: -1.0,
            from_mps: 5.0,
            to_mps: 4.0,
            detail: "test".to_string(),
        };
        assert!((f.delta_kmh() - (-3.6)).abs() < 0.001);
        assert!((f.from_kmh() - 18.0).abs() < 0.001);
        assert!((f.to_kmh() - 14.4).abs() < 0.001);
    }

    #[test]
    fn breakdown_has_deficit_true_when_gap_exceeds_threshold() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 6.0,
            actual_speed_mps: 4.5,
            factors: vec![],
            unexplained_mps: 1.5,
        };
        assert!(bd.has_deficit());
    }

    #[test]
    fn breakdown_has_deficit_false_when_gap_below_threshold() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 5.0,
            actual_speed_mps: 4.95,
            factors: vec![],
            unexplained_mps: 0.05,
        };
        assert!(!bd.has_deficit());
    }

    #[test]
    fn breakdown_scheduled_and_actual_kmh_conversion() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 5.0,
            actual_speed_mps: 4.0,
            factors: vec![],
            unexplained_mps: 1.0,
        };
        assert!((bd.scheduled_speed_kmh() - 18.0).abs() < 0.001);
        assert!((bd.actual_speed_kmh() - 14.4).abs() < 0.001);
    }

    #[test]
    fn chart_json_is_valid_json_with_labels_and_datasets() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 5.0,
            actual_speed_mps: 4.0,
            factors: vec![DeficitFactor {
                label: "Dwell time at stops",
                delta_mps: -0.5,
                from_mps: 5.0,
                to_mps: 4.5,
                detail: "avg 30 s/stop".to_string(),
            }],
            unexplained_mps: 0.5,
        };
        let json = bd.chart_json();
        let v: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert!(v["labels"].is_array());
        assert!(v["datasets"].is_array());
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cargo test breakdown
```

Expected: compile error — types not found.

- [ ] **Step 3: Implement the structs and methods**

Replace the file with:

```rust
// src/speed/breakdown.rs

use anyhow::Result;
use crate::db::Database;

pub struct DeficitFactor {
    pub label: &'static str,
    pub delta_mps: f64,
    pub from_mps: f64,
    pub to_mps: f64,
    pub detail: String,
}

impl DeficitFactor {
    pub fn delta_kmh(&self) -> f64 { self.delta_mps * 3.6 }
    pub fn from_kmh(&self) -> f64 { self.from_mps * 3.6 }
    pub fn to_kmh(&self) -> f64 { self.to_mps * 3.6 }
}

pub struct SpeedDeficitBreakdown {
    pub scheduled_speed_mps: f64,
    pub actual_speed_mps: f64,
    pub factors: Vec<DeficitFactor>,
    pub unexplained_mps: f64,
}

impl SpeedDeficitBreakdown {
    pub fn scheduled_speed_kmh(&self) -> f64 { self.scheduled_speed_mps * 3.6 }
    pub fn actual_speed_kmh(&self) -> f64 { self.actual_speed_mps * 3.6 }

    pub fn has_deficit(&self) -> bool {
        self.scheduled_speed_mps > self.actual_speed_mps + 0.1
    }

    pub fn chart_json(&self) -> String {
        let mut labels: Vec<serde_json::Value> = vec![serde_json::json!("Scheduled")];
        let mut data: Vec<serde_json::Value> =
            vec![serde_json::json!([0.0, self.scheduled_speed_kmh()])];
        let mut colors: Vec<serde_json::Value> = vec![serde_json::json!("#2980b9")];

        for factor in &self.factors {
            labels.push(serde_json::json!(format!("− {}", factor.label)));
            data.push(serde_json::json!([factor.to_kmh(), factor.from_kmh()]));
            let color = match factor.label {
                "Dwell time at stops" => "#e74c3c",
                "Bunching" => "#27ae60",
                _ => "#e67e22",
            };
            colors.push(serde_json::json!(color));
        }

        if self.unexplained_mps.abs() > 0.05 {
            let from_kmh = self
                .factors
                .last()
                .map(|f| f.to_kmh())
                .unwrap_or_else(|| self.scheduled_speed_kmh());
            labels.push(serde_json::json!("− Other"));
            data.push(serde_json::json!([self.actual_speed_kmh(), from_kmh]));
            colors.push(serde_json::json!("#aaaaaa"));
        }

        labels.push(serde_json::json!("Actual"));
        data.push(serde_json::json!([0.0, self.actual_speed_kmh()]));
        colors.push(serde_json::json!("#e67e22"));

        serde_json::to_string(&serde_json::json!({
            "labels": labels,
            "datasets": [{
                "data": data,
                "backgroundColor": colors,
                "borderWidth": 0,
            }]
        }))
        .unwrap_or_default()
    }
}

pub async fn compute_speed_deficit_breakdown(
    _db: &Database,
    _agency_id: &str,
    _route_id: &str,
    _direction_id: i64,
    _days: i64,
) -> Result<Option<SpeedDeficitBreakdown>> {
    Ok(None)
}

#[cfg(test)]
mod tests {
    use super::*;
    // ... (tests from step 1) ...
}
```

- [ ] **Step 4: Wire into mod.rs**

In `src/speed/mod.rs`, add after the existing `pub mod card;` line:

```rust
pub mod breakdown;
pub use breakdown::{DeficitFactor, SpeedDeficitBreakdown, compute_speed_deficit_breakdown};
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
cargo test breakdown
```

Expected: all 5 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/speed/breakdown.rs src/speed/mod.rs
git commit -m "feat(breakdown): add SpeedDeficitBreakdown structs and chart_json"
```

---

### Task 2: Scheduled timings query

**Files:**
- Modify: `src/speed/breakdown.rs`
- Modify: `src/speed/mod.rs` (visibility changes)

- [ ] **Step 1: Write the failing integration test**

Add to the bottom of `src/speed/breakdown.rs`, inside the `#[cfg(test)] mod tests` block:

```rust
    #[cfg(test)]
    mod integration {
        use crate::db::test_utils;
        use super::*;

        #[tokio::test]
        async fn fetch_scheduled_timings_returns_distance_and_dwell() {
            let td = test_utils::setup().await;
            let db = &td.db;

            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            // Two stops ~1112 m apart (0.01° longitude at equator ≈ 1112 m)
            sqlx::query("INSERT INTO stops VALUES ('a0', 'S1', 'A', 0.0, 0.0)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO stops VALUES ('a0', 'S2', 'B', 0.0, 0.01)")
                .execute(&db.pool).await.unwrap();
            // 30 s dwell at S1, 10 min total trip
            sqlx::query("INSERT INTO scheduled_stops VALUES ('a0', 'T1', 'S1', 1, '08:00:00', '08:00:30')")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('a0', 'T1', 'S2', 2, '08:10:00', '08:10:00')")
                .execute(&db.pool).await.unwrap();

            let result = fetch_scheduled_timings(db, "a0", "R1", 0).await.unwrap();
            assert!(result.is_some());
            let t = result.unwrap();
            assert!((t.route_distance_m - 1112.0).abs() < 50.0);
            assert!((t.scheduled_dwell_secs - 30.0).abs() < 1.0);
            assert!((t.scheduled_duration_secs - 600.0).abs() < 1.0);
            assert_eq!(t.num_stops, 2);
        }

        #[tokio::test]
        async fn fetch_scheduled_timings_returns_none_for_unknown_route() {
            let td = test_utils::setup().await;
            let result = fetch_scheduled_timings(&td.db, "x", "NONE", 0).await.unwrap();
            assert!(result.is_none());
        }
    }
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cargo test fetch_scheduled_timings
```

Expected: compile error — `fetch_scheduled_timings` not found.

- [ ] **Step 3: Make haversine_meters and parse_time_secs accessible**

In `src/speed/mod.rs`, change the function signatures from:

```rust
fn haversine_meters(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
```

and

```rust
fn parse_time_secs(s: &str) -> Option<u32> {
```

to:

```rust
pub(super) fn haversine_meters(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
```

```rust
pub(super) fn parse_time_secs(s: &str) -> Option<u32> {
```

- [ ] **Step 4: Implement fetch_scheduled_timings**

Add to `src/speed/breakdown.rs` (before the `pub async fn compute_speed_deficit_breakdown` stub):

```rust
struct ScheduledTimings {
    route_distance_m: f64,
    scheduled_duration_secs: f64,
    scheduled_dwell_secs: f64,
    num_stops: usize,
}

#[derive(sqlx::FromRow)]
struct ScheduledStopRow {
    stop_lat: f64,
    stop_lon: f64,
    arrival_time: String,
    departure_time: String,
}

async fn fetch_scheduled_timings(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
) -> Result<Option<ScheduledTimings>> {
    let rows: Vec<ScheduledStopRow> = sqlx::query_as(
        "WITH rep_trip AS (
            SELECT DISTINCT ON (1) trip_id
            FROM trips
            WHERE agency_id = $1 AND route_id = $2 AND COALESCE(direction_id, 0) = $3
            ORDER BY 1, trip_id
        )
        SELECT s.stop_lat, s.stop_lon, ss.arrival_time, ss.departure_time
        FROM rep_trip rt
        JOIN scheduled_stops ss ON ss.agency_id = $1 AND ss.trip_id = rt.trip_id
        JOIN stops s ON s.agency_id = $1 AND s.stop_id = ss.stop_id
        ORDER BY ss.stop_sequence",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(direction_id)
    .fetch_all(&db.pool)
    .await?;

    if rows.len() < 2 {
        return Ok(None);
    }

    let route_distance_m: f64 = rows
        .windows(2)
        .map(|w| {
            super::haversine_meters(w[0].stop_lat, w[0].stop_lon, w[1].stop_lat, w[1].stop_lon)
        })
        .sum();

    if route_distance_m < 1.0 {
        return Ok(None);
    }

    let scheduled_dwell_secs: f64 = rows
        .iter()
        .filter_map(|r| {
            let arr = super::parse_time_secs(&r.arrival_time)?;
            let dep = super::parse_time_secs(&r.departure_time)?;
            if dep >= arr {
                Some((dep - arr) as f64)
            } else {
                None
            }
        })
        .sum();

    let first_secs = super::parse_time_secs(&rows.first().unwrap().arrival_time);
    let last_secs = super::parse_time_secs(&rows.last().unwrap().arrival_time);
    let scheduled_duration_secs = match (first_secs, last_secs) {
        (Some(f), Some(l)) if l > f => (l - f) as f64,
        _ => return Ok(None),
    };

    Ok(Some(ScheduledTimings {
        route_distance_m,
        scheduled_duration_secs,
        scheduled_dwell_secs,
        num_stops: rows.len(),
    }))
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
cargo test fetch_scheduled_timings
```

Expected: both tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/speed/breakdown.rs src/speed/mod.rs
git commit -m "feat(breakdown): add fetch_scheduled_timings query"
```

---

### Task 3: Actual timings query

**Files:**
- Modify: `src/speed/breakdown.rs`

- [ ] **Step 1: Write the failing integration test**

Add to the `integration` submodule in `src/speed/breakdown.rs`:

```rust
        #[tokio::test]
        async fn fetch_actual_timings_averages_dwell_and_duration_across_trips() {
            let td = test_utils::setup().await;
            let db = &td.db;

            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T2', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();

            // T1: two stop events, arrival_time_unix span = 300s, dwell = 60s
            // T2: two stop events, arrival_time_unix span = 240s, dwell = 40s
            // avg duration = 270s, avg dwell = 50s
            let now_epoch: i64 = chrono::Utc::now().timestamp();
            let obs = chrono::Utc::now().to_rfc3339();

            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S1',1,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T1").bind(now_epoch).bind(now_epoch + 60)
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S2',2,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T1").bind(now_epoch + 300).bind(now_epoch + 300)
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S1',1,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T2").bind(now_epoch).bind(now_epoch + 40)
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S2',2,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T2").bind(now_epoch + 240).bind(now_epoch + 240)
            .execute(&db.pool).await.unwrap();

            let result = fetch_actual_timings(db, "a0", "R1", 0, 1).await.unwrap();
            assert!(result.is_some());
            let t = result.unwrap();
            assert!((t.avg_dwell_secs - 50.0).abs() < 5.0);
            assert!((t.avg_duration_secs - 270.0).abs() < 5.0);
            assert_eq!(t.trip_count, 2);
        }

        #[tokio::test]
        async fn fetch_actual_timings_returns_none_when_no_data() {
            let td = test_utils::setup().await;
            let result = fetch_actual_timings(&td.db, "x", "NONE", 0, 28).await.unwrap();
            assert!(result.is_none());
        }
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cargo test fetch_actual_timings
```

Expected: compile error — `fetch_actual_timings` not found.

- [ ] **Step 3: Implement fetch_actual_timings**

Add to `src/speed/breakdown.rs` (before `compute_speed_deficit_breakdown`):

```rust
struct ActualTimings {
    avg_dwell_secs: f64,
    avg_duration_secs: f64,
    trip_count: i64,
}

#[derive(sqlx::FromRow)]
struct ActualTimingsRow {
    avg_dwell_secs: Option<f64>,
    avg_duration_secs: Option<f64>,
    trip_count: i64,
}

async fn fetch_actual_timings(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
    days: i64,
) -> Result<Option<ActualTimings>> {
    let row: ActualTimingsRow = sqlx::query_as(
        "SELECT
             AVG(total_dwell)::DOUBLE PRECISION        AS avg_dwell_secs,
             AVG(total_duration)::DOUBLE PRECISION     AS avg_duration_secs,
             COUNT(*)::BIGINT                          AS trip_count
         FROM (
             SELECT
                 ste.trip_id,
                 SUM(CASE WHEN ste.dwell_secs >= 0 THEN ste.dwell_secs ELSE 0 END) AS total_dwell,
                 (MAX(ste.arrival_time_unix) - MIN(ste.arrival_time_unix))          AS total_duration
             FROM stop_time_events ste
             JOIN trips t ON t.agency_id = ste.agency_id AND t.trip_id = ste.trip_id
             WHERE ste.agency_id = $1
               AND t.route_id = $2
               AND COALESCE(t.direction_id, 0) = $3
               AND ste.arrival_time_unix IS NOT NULL
               AND ste.arrival_time_unix >
                       EXTRACT(EPOCH FROM NOW() - $4::INT * INTERVAL '1 day')::BIGINT
             GROUP BY ste.trip_id
             HAVING COUNT(*) >= 2
                AND MAX(ste.arrival_time_unix) > MIN(ste.arrival_time_unix)
         ) AS trip_agg",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(direction_id)
    .bind(days)
    .fetch_one(&db.pool)
    .await?;

    match (row.avg_dwell_secs, row.avg_duration_secs) {
        (Some(dwell), Some(duration)) if row.trip_count > 0 && duration > 0.0 => {
            Ok(Some(ActualTimings {
                avg_dwell_secs: dwell,
                avg_duration_secs: duration,
                trip_count: row.trip_count,
            }))
        }
        _ => Ok(None),
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
cargo test fetch_actual_timings
```

Expected: both tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/speed/breakdown.rs
git commit -m "feat(breakdown): add fetch_actual_timings query"
```

---

### Task 4: Bunching fraction query

**Files:**
- Modify: `src/speed/breakdown.rs`

- [ ] **Step 1: Write the failing integration test**

Add to the `integration` submodule in `src/speed/breakdown.rs`:

```rust
        #[tokio::test]
        async fn fetch_bunching_fraction_detects_co_located_trips() {
            let td = test_utils::setup().await;
            let db = &td.db;

            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T2', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();

            // Two vehicles at stop_sequence 1 within 30 seconds of each other
            let t1 = chrono::Utc::now().to_rfc3339();
            let t2 = (chrono::Utc::now() + chrono::TimeDelta::seconds(30)).to_rfc3339();

            sqlx::query(
                "INSERT INTO vehicle_positions (agency_id, observed_at, trip_id, vehicle_id, latitude, longitude, stop_sequence) VALUES ($1,$2,$3,$4,0,0,1)"
            )
            .bind("a0").bind(&t1).bind("T1").bind("V1")
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO vehicle_positions (agency_id, observed_at, trip_id, vehicle_id, latitude, longitude, stop_sequence) VALUES ($1,$2,$3,$4,0,0,1)"
            )
            .bind("a0").bind(&t2).bind("T2").bind("V2")
            .execute(&db.pool).await.unwrap();

            let fraction = fetch_bunching_fraction(db, "a0", "R1", 0, 1).await.unwrap();
            assert!(fraction > 0.0, "expected bunching > 0, got {fraction}");
            assert!(fraction <= 1.0);
        }

        #[tokio::test]
        async fn fetch_bunching_fraction_returns_zero_when_no_vehicle_data() {
            let td = test_utils::setup().await;
            let fraction = fetch_bunching_fraction(&td.db, "x", "NONE", 0, 28).await.unwrap();
            assert_eq!(fraction, 0.0);
        }

        #[tokio::test]
        async fn fetch_bunching_fraction_returns_zero_when_vehicles_far_apart_in_time() {
            let td = test_utils::setup().await;
            let db = &td.db;

            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T2', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();

            // Two vehicles at same stop but 5 minutes apart — not bunched
            let t1 = chrono::Utc::now().to_rfc3339();
            let t2 = (chrono::Utc::now() + chrono::TimeDelta::seconds(300)).to_rfc3339();

            sqlx::query(
                "INSERT INTO vehicle_positions (agency_id, observed_at, trip_id, vehicle_id, latitude, longitude, stop_sequence) VALUES ($1,$2,$3,$4,0,0,1)"
            )
            .bind("a0").bind(&t1).bind("T1").bind("V1")
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO vehicle_positions (agency_id, observed_at, trip_id, vehicle_id, latitude, longitude, stop_sequence) VALUES ($1,$2,$3,$4,0,0,1)"
            )
            .bind("a0").bind(&t2).bind("T2").bind("V2")
            .execute(&db.pool).await.unwrap();

            let fraction = fetch_bunching_fraction(db, "a0", "R1", 0, 1).await.unwrap();
            assert_eq!(fraction, 0.0);
        }
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cargo test fetch_bunching_fraction
```

Expected: compile error — `fetch_bunching_fraction` not found.

- [ ] **Step 3: Implement fetch_bunching_fraction**

Add to `src/speed/breakdown.rs` (before `compute_speed_deficit_breakdown`):

```rust
#[derive(sqlx::FromRow)]
struct BunchingRow {
    bunched_count: f64,
    total_count: Option<f64>,
}

async fn fetch_bunching_fraction(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
    days: i64,
) -> Result<f64> {
    let row: BunchingRow = sqlx::query_as(
        "WITH route_trips AS (
             SELECT vp.trip_id,
                    vp.stop_sequence,
                    EXTRACT(EPOCH FROM vp.observed_at::TIMESTAMPTZ)::BIGINT AS epoch_secs
             FROM vehicle_positions vp
             JOIN trips t ON t.agency_id = vp.agency_id AND t.trip_id = vp.trip_id
             WHERE vp.agency_id = $1
               AND t.route_id = $2
               AND COALESCE(t.direction_id, 0) = $3
               AND vp.stop_sequence IS NOT NULL
               AND vp.observed_at::TIMESTAMPTZ >= NOW() - $4::INT * INTERVAL '1 day'
         ),
         bunched_trips AS (
             SELECT DISTINCT a.trip_id
             FROM route_trips a
             JOIN route_trips b
                 ON a.stop_sequence = b.stop_sequence
                AND a.trip_id != b.trip_id
                AND ABS(a.epoch_secs - b.epoch_secs) < 60
         )
         SELECT
             COUNT(DISTINCT b.trip_id)::DOUBLE PRECISION    AS bunched_count,
             NULLIF(COUNT(DISTINCT r.trip_id), 0)::DOUBLE PRECISION AS total_count
         FROM route_trips r
         LEFT JOIN bunched_trips b ON b.trip_id = r.trip_id",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(direction_id)
    .bind(days)
    .fetch_one(&db.pool)
    .await?;

    Ok(match row.total_count {
        Some(total) if total > 0.0 => row.bunched_count / total,
        _ => 0.0,
    })
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
cargo test fetch_bunching_fraction
```

Expected: all three tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/speed/breakdown.rs
git commit -m "feat(breakdown): add fetch_bunching_fraction query"
```

---

### Task 5: Wire up compute_speed_deficit_breakdown

**Files:**
- Modify: `src/speed/breakdown.rs`

- [ ] **Step 1: Write the failing integration test**

Add to the `integration` submodule in `src/speed/breakdown.rs`:

```rust
        #[tokio::test]
        async fn compute_breakdown_returns_none_when_no_actual_data() {
            let td = test_utils::setup().await;
            let db = &td.db;

            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO stops VALUES ('a0', 'S1', 'A', 0.0, 0.0)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO stops VALUES ('a0', 'S2', 'B', 0.0, 0.01)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('a0', 'T1', 'S1', 1, '08:00:00', '08:00:00')")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('a0', 'T1', 'S2', 2, '08:10:00', '08:10:00')")
                .execute(&db.pool).await.unwrap();

            // No stop_time_events inserted
            let result =
                compute_speed_deficit_breakdown(db, "a0", "R1", 0, 28).await.unwrap();
            assert!(result.is_none());
        }

        #[tokio::test]
        async fn compute_breakdown_produces_negative_dwell_delta_when_actual_dwell_exceeds_scheduled() {
            let td = test_utils::setup().await;
            let db = &td.db;

            // Scheduled: 2 stops ~1112 m apart, 0 s dwell, 600 s total
            // Actual: 1 trip, 120 s dwell (>> 0 s scheduled), 700 s total
            // Expected: dwell factor has negative delta_mps

            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO stops VALUES ('a0', 'S1', 'A', 0.0, 0.0)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO stops VALUES ('a0', 'S2', 'B', 0.0, 0.01)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('a0', 'T1', 'S1', 1, '08:00:00', '08:00:00')")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('a0', 'T1', 'S2', 2, '08:10:00', '08:10:00')")
                .execute(&db.pool).await.unwrap();

            let now_epoch: i64 = chrono::Utc::now().timestamp();
            let obs = chrono::Utc::now().to_rfc3339();
            // arrival 0, depart 120 (120 s dwell), arrival at S2 at +700
            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S1',1,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T1").bind(now_epoch).bind(now_epoch + 120)
            .execute(&db.pool).await.unwrap();
            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S2',2,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T1").bind(now_epoch + 700).bind(now_epoch + 700)
            .execute(&db.pool).await.unwrap();

            let result =
                compute_speed_deficit_breakdown(db, "a0", "R1", 0, 28).await.unwrap();
            assert!(result.is_some());
            let bd = result.unwrap();
            let dwell_factor = bd.factors.iter().find(|f| f.label == "Dwell time at stops").unwrap();
            assert!(dwell_factor.delta_mps < 0.0, "dwell delta should be negative");
        }
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cargo test compute_breakdown
```

Expected: both tests fail (function returns `None` always — stub from Task 1).

- [ ] **Step 3: Implement compute_speed_deficit_breakdown**

Replace the stub implementation in `src/speed/breakdown.rs`:

```rust
pub async fn compute_speed_deficit_breakdown(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
    days: i64,
) -> Result<Option<SpeedDeficitBreakdown>> {
    let (scheduled_res, actual_res, bunching_res) = tokio::join!(
        fetch_scheduled_timings(db, agency_id, route_id, direction_id),
        fetch_actual_timings(db, agency_id, route_id, direction_id, days),
        fetch_bunching_fraction(db, agency_id, route_id, direction_id, days),
    );

    let scheduled = match scheduled_res? {
        Some(s) => s,
        None => return Ok(None),
    };
    let actual = match actual_res? {
        Some(a) => a,
        None => return Ok(None),
    };
    let bunching_fraction = bunching_res.unwrap_or(0.0);

    let d = scheduled.route_distance_m;
    let t_sched = scheduled.scheduled_duration_secs;
    let scheduled_dwell = scheduled.scheduled_dwell_secs;
    let scheduled_running = (t_sched - scheduled_dwell).max(0.0);

    let actual_dwell = actual.avg_dwell_secs;
    let actual_running = (actual.avg_duration_secs - actual_dwell).max(0.0);

    let dwell_excess = (actual_dwell - scheduled_dwell).max(0.0);
    let running_excess = (actual_running - scheduled_running).max(0.0);

    // Attribute up to all dwell_excess to bunching, capped at what bunching could explain
    let bunching_dwell = (bunching_fraction * 15.0).min(dwell_excess);
    let pure_dwell_excess = dwell_excess - bunching_dwell;

    let scheduled_speed = d / t_sched;
    let speed_after_dwell = d / (t_sched + pure_dwell_excess).max(1.0);
    let speed_after_bunching = d / (t_sched + dwell_excess).max(1.0);
    let speed_after_running = d / (t_sched + dwell_excess + running_excess).max(1.0);
    let actual_speed = d / actual.avg_duration_secs.max(1.0);

    let scheduled_running_nonzero = scheduled_running.max(1.0);

    let factors = vec![
        DeficitFactor {
            label: "Dwell time at stops",
            delta_mps: speed_after_dwell - scheduled_speed,
            from_mps: scheduled_speed,
            to_mps: speed_after_dwell,
            detail: format!(
                "avg {:.0} s/stop across {} stops",
                actual_dwell / scheduled.num_stops as f64,
                scheduled.num_stops
            ),
        },
        DeficitFactor {
            label: "Bunching",
            delta_mps: speed_after_bunching - speed_after_dwell,
            from_mps: speed_after_dwell,
            to_mps: speed_after_bunching,
            detail: format!("{:.0}% of trips bunched", bunching_fraction * 100.0),
        },
        DeficitFactor {
            label: "Running time loss",
            delta_mps: speed_after_running - speed_after_bunching,
            from_mps: speed_after_bunching,
            to_mps: speed_after_running,
            detail: format!(
                "running time {:.0}% above schedule",
                running_excess / scheduled_running_nonzero * 100.0
            ),
        },
    ];

    let unexplained_mps = actual_speed - speed_after_running;

    Ok(Some(SpeedDeficitBreakdown {
        scheduled_speed_mps: scheduled_speed,
        actual_speed_mps: actual_speed,
        factors,
        unexplained_mps,
    }))
}
```

Also add `use tokio;` at the top of the file (tokio::join! macro requires it):
```rust
use anyhow::Result;
use crate::db::Database;
```
(tokio is already in scope via Cargo.toml; the macro is available without explicit import.)

- [ ] **Step 4: Run tests to confirm they pass**

```bash
cargo test breakdown
```

Expected: all tests pass (Tasks 1–5).

- [ ] **Step 5: Commit**

```bash
git add src/speed/breakdown.rs
git commit -m "feat(breakdown): implement compute_speed_deficit_breakdown"
```

---

### Task 6: Handler integration

**Files:**
- Modify: `src/web/handlers.rs`

- [ ] **Step 1: Write the failing E2E test**

Add to the `e2e_tests` module in `src/web/handlers.rs`:

```rust
    #[tokio::test]
    async fn route_speed_detail_includes_speed_factors_section_when_actual_data_present() {
        let td = test_utils::setup().await;
        let db = &td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Downtown')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Main St',  45.50, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Downtown', 45.51, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')")
            .execute(&db.pool).await.unwrap();

        // Insert actual stop_time_events so breakdown has data
        let now_epoch: i64 = chrono::Utc::now().timestamp();
        let obs = chrono::Utc::now().to_rfc3339();
        sqlx::query(
            "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S1',1,$4,$5)"
        )
        .bind("0").bind(&obs).bind("T1").bind(now_epoch).bind(now_epoch + 30)
        .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S2',2,$4,$5)"
        )
        .bind("0").bind(&obs).bind("T1").bind(now_epoch + 700).bind(now_epoch + 700)
        .execute(&db.pool).await.unwrap();

        let state = AppState { db: td.db, config: test_config() };
        let app = build_router(state);
        let response = app
            .oneshot(
                Request::builder()
                    .uri("/speed/0/R1")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let body = String::from_utf8(
            axum::body::to_bytes(response.into_body(), usize::MAX)
                .await
                .unwrap()
                .to_vec(),
        )
        .unwrap();
        assert!(body.contains("Speed factors"), "expected 'Speed factors' section in HTML");
    }
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cargo test route_speed_detail_includes_speed_factors
```

Expected: FAIL — "Speed factors" not in body (breakdown not wired up yet).

- [ ] **Step 3: Add breakdown field to RouteSpeedDetailDirection**

In `src/web/handlers.rs`, update the struct:

```rust
use crate::speed::{
    DeficitFactor, RouteClass, RouteSpeedCard, RouteSpeedSummary, SpeedDeficitBreakdown,
    StopSpacing, build_speed_cards, classify_by_spacing, compute_speed_deficit_breakdown,
    route_speed_by_day_type, route_speed_summary, route_speed_trend_by_direction,
    route_stop_spacings,
};
```

Add `breakdown_chart_id` and `breakdown` to the struct:

```rust
struct RouteSpeedDetailDirection {
    pub direction_name: String,
    pub first_stop_name: String,
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
    pub weekday_chart_id: String,
    pub saturday_chart_id: String,
    pub sunday_chart_id: String,
    pub weekday_json: String,
    pub saturday_json: String,
    pub sunday_json: String,
    pub breakdown_chart_id: String,
    pub breakdown: Option<SpeedDeficitBreakdown>,
}
```

- [ ] **Step 4: Update the direction test helper**

In the `mod tests` block of `handlers.rs`, update the `direction()` helper (around line 779):

```rust
    fn direction(avg_spacing_m: f64) -> RouteSpeedDetailDirection {
        RouteSpeedDetailDirection {
            direction_name: String::new(),
            first_stop_name: String::new(),
            avg_spacing_m,
            spacings: vec![],
            weekday_chart_id: String::new(),
            saturday_chart_id: String::new(),
            sunday_chart_id: String::new(),
            weekday_json: String::new(),
            saturday_json: String::new(),
            sunday_json: String::new(),
            breakdown_chart_id: String::new(),
            breakdown: None,
        }
    }
```

- [ ] **Step 5: Update the route_speed_detail handler**

Replace the section of `route_speed_detail` from `let (spacings_res, trends_res)` through `let directions: Vec<...>` with:

```rust
    let (spacings_res, trends_res) = tokio::join!(
        route_stop_spacings(&state.db, &agency_id, &route_id),
        route_speed_trend_by_direction(&state.db, &agency_id, &route_id, 28),
    );

    let spacings = spacings_res.unwrap_or_else(|e| {
        tracing::error!("route_stop_spacings failed for {agency_id}/{route_id}: {e}");
        vec![]
    });
    let trends = trends_res.unwrap_or_else(|e| {
        tracing::error!("route_speed_trend_by_direction failed for {agency_id}/{route_id}: {e}");
        vec![]
    });

    if spacings.is_empty() {
        return (
            axum::http::StatusCode::NOT_FOUND,
            Html("<h1>Not Found</h1>".to_string()),
        )
            .into_response();
    }

    let mut breakdowns: Vec<Option<SpeedDeficitBreakdown>> = Vec::new();
    for spacing in &spacings {
        let bd = compute_speed_deficit_breakdown(
            &state.db,
            &agency_id,
            &route_id,
            spacing.direction_id,
            28,
        )
        .await
        .unwrap_or_else(|e| {
            tracing::error!("breakdown failed for {agency_id}/{route_id}: {e}");
            None
        });
        breakdowns.push(bd);
    }

    let directions: Vec<RouteSpeedDetailDirection> = spacings
        .into_iter()
        .zip(breakdowns)
        .enumerate()
        .map(|(i, (spacing, breakdown))| {
            let trend = trends
                .iter()
                .find(|t| t.direction_id == spacing.direction_id);
            let (weekday, saturday, sunday) = trend
                .map(|t| (t.weekday.clone(), t.saturday.clone(), t.sunday.clone()))
                .unwrap_or_default();
            RouteSpeedDetailDirection {
                direction_name: spacing.direction_name,
                first_stop_name: spacing.first_stop_name,
                avg_spacing_m: spacing.avg_spacing_m,
                spacings: spacing.spacings,
                weekday_chart_id: format!("weekday-{i}"),
                saturday_chart_id: format!("saturday-{i}"),
                sunday_chart_id: format!("sunday-{i}"),
                weekday_json: trend_to_json(weekday),
                saturday_json: trend_to_json(saturday),
                sunday_json: trend_to_json(sunday),
                breakdown_chart_id: format!("breakdown-{i}"),
                breakdown,
            }
        })
        .collect();
```

- [ ] **Step 6: Run all tests to confirm they pass**

```bash
cargo test
```

Expected: all tests pass including the new E2E test.

- [ ] **Step 7: Commit**

```bash
git add src/web/handlers.rs
git commit -m "feat(breakdown): wire breakdown into route speed detail handler"
```

---

### Task 7: Template waterfall chart

**Files:**
- Modify: `templates/route_speed_detail.html`

- [ ] **Step 1: Confirm E2E test currently fails for "Speed factors" content**

The test from Task 6 Step 1 passes if "Speed factors" is already present — but it won't be until the template is updated. Verify by checking the test still references the right string:

```bash
cargo test route_speed_detail_includes_speed_factors
```

If the test passes already (it shouldn't), re-read the test to confirm it checks template content.

- [ ] **Step 2: Add the waterfall section to the template**

In `templates/route_speed_detail.html`, add the following CSS inside the `<style>` block in `{% block extra_head %}`, after the existing `.chart-legend` rule:

```css
.breakdown-detail { font-size: 0.72rem; color: var(--ink-500); margin-top: 0.25rem; display: flex; flex-wrap: wrap; gap: 0.75rem; }
.breakdown-detail-item { display: flex; align-items: center; gap: 0.3rem; }
.breakdown-swatch { width: 10px; height: 10px; border-radius: 2px; flex-shrink: 0; }
```

- [ ] **Step 3: Add the waterfall HTML inside the direction loop**

In `templates/route_speed_detail.html`, inside the `{% for direction in &directions %}` loop, after the closing `</div>` of the speed-charts-row section and before the `</div>` that closes `direction-body`, add:

```html
      {% if let Some(bd) = &direction.breakdown %}
      {% if bd.has_deficit() %}
      <div class="section-divider"></div>
      <div class="section-label">Speed factors</div>
      <canvas id="{{ direction.breakdown_chart_id }}" height="160"></canvas>
      <div class="breakdown-detail">
        {% for factor in &bd.factors %}
        <span class="breakdown-detail-item">
          <span class="breakdown-swatch" style="background:{% if factor.label == "Dwell time at stops" %}#e74c3c{% elif factor.label == "Bunching" %}#27ae60{% else %}#e67e22{% endif %};"></span>
          {{ factor.label }}: {{ factor.detail }}
        </span>
        {% endfor %}
      </div>
      {% endif %}
      {% endif %}
```

- [ ] **Step 4: Add the Chart.js rendering script**

In `templates/route_speed_detail.html`, inside `{% block extra_scripts %}`, after the existing `{% for direction in &directions %}` block that calls `renderSpeedChart`, add a second loop:

```html
{% for direction in &directions %}
{% if let Some(bd) = &direction.breakdown %}
{% if bd.has_deficit() %}
(function() {
  const bdData = {{ bd.chart_json()|safe }};
  const ctx = document.getElementById('{{ direction.breakdown_chart_id }}');
  if (!ctx) return;
  new Chart(ctx, {
    type: 'bar',
    data: bdData,
    options: {
      indexAxis: 'y',
      responsive: true,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: function(ctx) {
              const v = ctx.raw;
              if (!Array.isArray(v)) return '';
              return (Math.abs(v[1] - v[0])).toFixed(1) + ' km/h';
            }
          }
        }
      },
      scales: {
        x: { min: 0, title: { display: true, text: 'km/h' }, ticks: { font: { size: 10 } } },
        y: { ticks: { font: { size: 11 } } },
      },
    },
  });
})();
{% endif %}
{% endif %}
{% endfor %}
```

- [ ] **Step 5: Build to confirm template compiles**

```bash
cargo build
```

Expected: clean build (Askama validates templates at compile time).

- [ ] **Step 6: Run all tests**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add templates/route_speed_detail.html
git commit -m "feat(breakdown): add speed factors waterfall chart to route detail page"
```

---

## Self-Review

**Spec coverage:**
- ✅ Three factors: dwell, bunching, running time loss
- ✅ `src/speed/breakdown.rs` with `DeficitFactor` and `SpeedDeficitBreakdown`
- ✅ `compute_speed_deficit_breakdown(db, agency_id, route_id, direction_id, days)` function
- ✅ Returns `None` when no actual data
- ✅ Waterfall chart on route speed detail page using Chart.js floating bars
- ✅ One chart per direction
- ✅ Section hidden when `breakdown` is `None`
- ✅ `unexplained_mps` field computed and rendered when > 0.05 m/s
- ✅ 28-day window
- ✅ Bunching uses `vehicle_positions.stop_sequence`, 60 s threshold, 15 s estimated extra dwell
- ✅ Route distance via haversine (reuses existing `haversine_meters`)
- ✅ Unit tests for struct methods and chart_json
- ✅ Integration tests for all three query helpers and full function
- ✅ E2E test for handler + template

**Placeholder scan:** No TBDs or "implement later" present. All code is complete.

**Type consistency:** `DeficitFactor` fields (`label`, `delta_mps`, `from_mps`, `to_mps`, `detail`) used consistently across Tasks 1, 5, 6, and 7. `SpeedDeficitBreakdown` used consistently in Tasks 1, 5, 6, and 7. `breakdown_chart_id` added in Task 6 Step 3 and referenced in Task 7 Step 3.

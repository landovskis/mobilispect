# Montreal Metropolitan Area Coverage — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Mobilispect to monitor all 15 Montreal-region transit agencies and add inline agency labels plus per-agency filtering to all list views.

**Architecture:** The config layer, DB schema, and poll loop already support multiple agencies. This plan adds the remaining exo env-var entries, adds an `agency_filter` parameter to three DB query functions, and threads an agency filter bar + inline agency names through the dashboard, scorecard, speed, and report templates.

**Tech Stack:** Rust, Axum, Askama templates, SQLite via sqlx, GTFS / GTFS-RT

---

## File Map

| File | Change |
|------|--------|
| `.env.example` | Add exo indices 5–14 |
| `src/metrics/mod.rs` | `route_summary` and `scorecard_routes` gain `agency_filter: Option<&str>` |
| `src/speed/mod.rs` | `route_speed_summary` gains `agency_filter: Option<&str>` |
| `src/web/handlers.rs` | All page handlers gain agency_names map, agencies list, active_agency, and `?agency` query param |
| `templates/dashboard.html` | Agency name inline with route number; filter bar |
| `templates/scorecard.html` | Agency name inline with route number; filter bar |
| `templates/speed.html` | Agency name inline with route number; filter bar |
| `templates/report.html` | Agency name inline with route number (no filter bar) |

---

## Task 1: Complete `.env.example` with exo bus sector indices 5–14

**Files:**
- Modify: `.env.example`

- [ ] **Step 1: Add the 10 missing exo bus sectors after the existing exo-citso block**

All bus sectors share the same GTFS-RT URLs as exo-trains.

In `.env.example`, after the `AGENCY_4_UTC_OFFSET=-04:00` line (end of exo-citso block), add:

```
# AGENCY_5_SLUG=exo-citla
# AGENCY_5_NAME=exo (Laval)
# AGENCY_5_GTFS_STATIC_URL=https://exo.quebec/xdata/citla/google_transit.zip
# AGENCY_5_GTFS_RT_VEHICLE_POSITIONS_URL=https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb
# AGENCY_5_GTFS_RT_TRIP_UPDATES_URL=https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb
# AGENCY_5_UTC_OFFSET=-04:00
#
# AGENCY_6_SLUG=exo-citpi
# AGENCY_6_NAME=exo (Presqu'île)
# AGENCY_6_GTFS_STATIC_URL=https://exo.quebec/xdata/citpi/google_transit.zip
# AGENCY_6_GTFS_RT_VEHICLE_POSITIONS_URL=https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb
# AGENCY_6_GTFS_RT_TRIP_UPDATES_URL=https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb
# AGENCY_6_UTC_OFFSET=-04:00
#
# AGENCY_7_SLUG=exo-citsv
# AGENCY_7_NAME=exo (Sorel-Varennes)
# AGENCY_7_GTFS_STATIC_URL=https://exo.quebec/xdata/citsv/google_transit.zip
# AGENCY_7_GTFS_RT_VEHICLE_POSITIONS_URL=https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb
# AGENCY_7_GTFS_RT_TRIP_UPDATES_URL=https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb
# AGENCY_7_UTC_OFFSET=-04:00
#
# AGENCY_8_SLUG=exo-citvr
# AGENCY_8_NAME=exo (Vallée-du-Richelieu)
# AGENCY_8_GTFS_STATIC_URL=https://exo.quebec/xdata/citvr/google_transit.zip
# AGENCY_8_GTFS_RT_VEHICLE_POSITIONS_URL=https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb
# AGENCY_8_GTFS_RT_TRIP_UPDATES_URL=https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb
# AGENCY_8_UTC_OFFSET=-04:00
#
# AGENCY_9_SLUG=exo-citcrc
# AGENCY_9_NAME=exo (Chambly-Richelieu-Carignan)
# AGENCY_9_GTFS_STATIC_URL=https://exo.quebec/xdata/citcrc/google_transit.zip
# AGENCY_9_GTFS_RT_VEHICLE_POSITIONS_URL=https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb
# AGENCY_9_GTFS_RT_TRIP_UPDATES_URL=https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb
# AGENCY_9_UTC_OFFSET=-04:00
#
# AGENCY_10_SLUG=exo-citrous
# AGENCY_10_NAME=exo (Roussillon)
# AGENCY_10_GTFS_STATIC_URL=https://exo.quebec/xdata/citrous/google_transit.zip
# AGENCY_10_GTFS_RT_VEHICLE_POSITIONS_URL=https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb
# AGENCY_10_GTFS_RT_TRIP_UPDATES_URL=https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb
# AGENCY_10_UTC_OFFSET=-04:00
#
# AGENCY_11_SLUG=exo-citlr
# AGENCY_11_NAME=exo (Le Richelain)
# AGENCY_11_GTFS_STATIC_URL=https://exo.quebec/xdata/citlr/google_transit.zip
# AGENCY_11_GTFS_RT_VEHICLE_POSITIONS_URL=https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb
# AGENCY_11_GTFS_RT_TRIP_UPDATES_URL=https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb
# AGENCY_11_UTC_OFFSET=-04:00
#
# AGENCY_12_SLUG=exo-mrclm
# AGENCY_12_NAME=exo (Laurentides-Mirabel)
# AGENCY_12_GTFS_STATIC_URL=https://exo.quebec/xdata/mrclm/google_transit.zip
# AGENCY_12_GTFS_RT_VEHICLE_POSITIONS_URL=https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb
# AGENCY_12_GTFS_RT_TRIP_UPDATES_URL=https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb
# AGENCY_12_UTC_OFFSET=-04:00
#
# AGENCY_13_SLUG=exo-mrclasso
# AGENCY_13_NAME=exo (Assomption)
# AGENCY_13_GTFS_STATIC_URL=https://exo.quebec/xdata/mrclasso/google_transit.zip
# AGENCY_13_GTFS_RT_VEHICLE_POSITIONS_URL=https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb
# AGENCY_13_GTFS_RT_TRIP_UPDATES_URL=https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb
# AGENCY_13_UTC_OFFSET=-04:00
#
# AGENCY_14_SLUG=exo-lrrs
# AGENCY_14_NAME=exo (Haut-Saint-Laurent)
# AGENCY_14_GTFS_STATIC_URL=https://exo.quebec/xdata/lrrs/google_transit.zip
# AGENCY_14_GTFS_RT_VEHICLE_POSITIONS_URL=https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb
# AGENCY_14_GTFS_RT_TRIP_UPDATES_URL=https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb
# AGENCY_14_UTC_OFFSET=-04:00
```

- [ ] **Step 2: Commit**

```bash
git add .env.example
git commit -m "config: add all Montreal exo bus sector agencies (indices 5-14)"
```

---

## Task 2: Add `agency_filter` to `route_summary`

**Files:**
- Modify: `src/metrics/mod.rs`

- [ ] **Step 1: Write the failing test**

In `src/metrics/mod.rs`, inside the `#[cfg(test)] mod tests` block, add after the existing tests:

```rust
#[tokio::test]
async fn route_summary_filters_by_agency() {
    let db = test_db().await;
    // Insert routes for two agencies
    sqlx::query!("INSERT INTO routes VALUES ('stm', 'R1', '15', 'Papineau', 3)")
        .execute(&db.pool).await.unwrap();
    sqlx::query!("INSERT INTO routes VALUES ('rtl', 'R2', '10', 'Longueuil', 3)")
        .execute(&db.pool).await.unwrap();
    // Insert route_daily for both
    sqlx::query!(
        "INSERT INTO route_daily (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
         VALUES ('stm', 'R1', '2026-01-01', 80.0, 60.0, 10, 12, '2026-01-01T12:00:00Z')"
    ).execute(&db.pool).await.unwrap();
    sqlx::query!(
        "INSERT INTO route_daily (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
         VALUES ('rtl', 'R2', '2026-01-01', 70.0, 90.0, 8, 10, '2026-01-01T12:00:00Z')"
    ).execute(&db.pool).await.unwrap();

    // All agencies
    let all = route_summary(&db, 30, None).await.unwrap();
    assert_eq!(all.len(), 2);

    // Filter to stm only
    let stm = route_summary(&db, 30, Some("stm")).await.unwrap();
    assert_eq!(stm.len(), 1);
    assert_eq!(stm[0].agency_id, "stm");

    // Filter to rtl only
    let rtl = route_summary(&db, 30, Some("rtl")).await.unwrap();
    assert_eq!(rtl.len(), 1);
    assert_eq!(rtl[0].agency_id, "rtl");
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cargo test route_summary_filters_by_agency -- --nocapture
```

Expected: compile error — `route_summary` called with wrong number of arguments.

- [ ] **Step 3: Update `route_summary` signature and implementation**

In `src/metrics/mod.rs`, replace the existing `route_summary` function:

```rust
/// Fetch route performance summary for the dashboard (last N days).
/// If `agency_filter` is Some, only returns routes for that agency.
pub async fn route_summary(db: &Database, days: i64, agency_filter: Option<&str>) -> Result<Vec<RouteSummary>> {
    let rows: Vec<RouteSummary> = match agency_filter {
        None => sqlx::query_as(
            "SELECT
               rd.agency_id,
               rd.route_id,
               r.short_name,
               r.long_name,
               ROUND(AVG(rd.on_time_pct), 1) as avg_on_time_pct,
               ROUND(AVG(rd.avg_delay_secs), 0) as avg_delay_secs,
               SUM(rd.trips_run) as trips_run,
               SUM(rd.trips_total) as trips_total,
               COUNT(rd.service_date) as days_measured
             FROM route_daily rd
             JOIN routes r ON rd.agency_id = r.agency_id AND rd.route_id = r.route_id
             WHERE rd.service_date >= DATE('now', '-' || ? || ' days')
             GROUP BY rd.agency_id, rd.route_id, r.short_name, r.long_name
             ORDER BY rd.agency_id, CAST(r.short_name AS INTEGER), r.short_name",
        )
        .bind(days)
        .fetch_all(&db.pool)
        .await?,

        Some(agency) => sqlx::query_as(
            "SELECT
               rd.agency_id,
               rd.route_id,
               r.short_name,
               r.long_name,
               ROUND(AVG(rd.on_time_pct), 1) as avg_on_time_pct,
               ROUND(AVG(rd.avg_delay_secs), 0) as avg_delay_secs,
               SUM(rd.trips_run) as trips_run,
               SUM(rd.trips_total) as trips_total,
               COUNT(rd.service_date) as days_measured
             FROM route_daily rd
             JOIN routes r ON rd.agency_id = r.agency_id AND rd.route_id = r.route_id
             WHERE rd.service_date >= DATE('now', '-' || ? || ' days')
               AND rd.agency_id = ?
             GROUP BY rd.agency_id, rd.route_id, r.short_name, r.long_name
             ORDER BY rd.agency_id, CAST(r.short_name AS INTEGER), r.short_name",
        )
        .bind(days)
        .bind(agency)
        .fetch_all(&db.pool)
        .await?,
    };
    Ok(rows)
}
```

- [ ] **Step 4: Fix the two call sites that now have the wrong arity**

In `src/web/handlers.rs`, update both calls:

```rust
// dashboard handler:
let routes = route_summary(&state.db, period_days, None).await.unwrap_or_default();

// report handler:
let routes = route_summary(&state.db, period_days, None).await.unwrap_or_default();
```

Also fix `api_routes`:
```rust
route_summary(&state.db, days, None).await.map(axum::Json)...
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cargo test route_summary_filters_by_agency -- --nocapture
```

Expected: PASS

- [ ] **Step 6: Run the full test suite**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/metrics/mod.rs src/web/handlers.rs
git commit -m "feat: add agency_filter param to route_summary"
```

---

## Task 3: Add `agency_filter` to `scorecard_routes`

**Files:**
- Modify: `src/metrics/mod.rs`

- [ ] **Step 1: Write the failing test**

In the `#[cfg(test)] mod tests` block of `src/metrics/mod.rs`, add:

```rust
#[tokio::test]
async fn scorecard_routes_filters_by_agency() {
    let db = test_db().await;
    sqlx::query!("INSERT INTO routes VALUES ('stm', 'R1', '15', 'Papineau', 3)")
        .execute(&db.pool).await.unwrap();
    sqlx::query!("INSERT INTO routes VALUES ('rtl', 'R2', '10', 'Longueuil', 3)")
        .execute(&db.pool).await.unwrap();
    sqlx::query!(
        "INSERT INTO route_daily (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
         VALUES ('stm', 'R1', '2026-01-01', 80.0, 60.0, 10, 12, '2026-01-01T12:00:00Z')"
    ).execute(&db.pool).await.unwrap();
    sqlx::query!(
        "INSERT INTO route_daily (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
         VALUES ('rtl', 'R2', '2026-01-01', 70.0, 90.0, 8, 10, '2026-01-01T12:00:00Z')"
    ).execute(&db.pool).await.unwrap();

    let all = scorecard_routes(&db, 30, None).await.unwrap();
    assert_eq!(all.len(), 2);

    let stm = scorecard_routes(&db, 30, Some("stm")).await.unwrap();
    assert_eq!(stm.len(), 1);
    assert_eq!(stm[0].agency_id, "stm");
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cargo test scorecard_routes_filters_by_agency -- --nocapture
```

Expected: compile error — wrong number of arguments.

- [ ] **Step 3: Update `scorecard_routes` signature and implementation**

Replace the existing `scorecard_routes` function in `src/metrics/mod.rs`:

```rust
/// Fetch per-route scorecard data: on-time % and speed deficit averaged over specified days.
/// If `agency_filter` is Some, only returns routes for that agency.
pub async fn scorecard_routes(db: &Database, days: i64, agency_filter: Option<&str>) -> Result<Vec<ScorecardRoute>> {
    let rows: Vec<ScorecardRoute> = match agency_filter {
        None => sqlx::query_as(
            "SELECT
               ot.agency_id,
               ot.route_id,
               r.short_name,
               r.long_name,
               ot.avg_on_time_pct,
               sp.speed_vs_scheduled_pct
             FROM (
               SELECT agency_id, route_id, ROUND(AVG(on_time_pct), 1) AS avg_on_time_pct
               FROM route_daily
               WHERE service_date >= DATE('now', '-' || ? || ' days')
               GROUP BY agency_id, route_id
             ) ot
             JOIN routes r ON r.agency_id = ot.agency_id AND r.route_id = ot.route_id
             LEFT JOIN (
               SELECT rs.agency_id, rs.route_id,
                 ROUND(AVG(
                   CASE WHEN rs.scheduled_speed_mps > 0 AND rsd.avg_actual IS NOT NULL
                     THEN (rs.scheduled_speed_mps - rsd.avg_actual) / rs.scheduled_speed_mps * 100.0
                     ELSE NULL END
                 ), 1) AS speed_vs_scheduled_pct
               FROM route_speed rs
               LEFT JOIN (
                 SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) AS avg_actual
                 FROM route_speed_daily
                 WHERE service_date >= DATE('now', '-' || ? || ' days')
                 GROUP BY agency_id, route_id, direction_id
               ) rsd ON rsd.agency_id = rs.agency_id AND rsd.route_id = rs.route_id AND rsd.direction_id = rs.direction_id
               GROUP BY rs.agency_id, rs.route_id
             ) sp ON sp.agency_id = ot.agency_id AND sp.route_id = ot.route_id
             ORDER BY ot.agency_id, CAST(r.short_name AS INTEGER), r.short_name",
        )
        .bind(days)
        .bind(days)
        .fetch_all(&db.pool)
        .await?,

        Some(agency) => sqlx::query_as(
            "SELECT
               ot.agency_id,
               ot.route_id,
               r.short_name,
               r.long_name,
               ot.avg_on_time_pct,
               sp.speed_vs_scheduled_pct
             FROM (
               SELECT agency_id, route_id, ROUND(AVG(on_time_pct), 1) AS avg_on_time_pct
               FROM route_daily
               WHERE service_date >= DATE('now', '-' || ? || ' days')
                 AND agency_id = ?
               GROUP BY agency_id, route_id
             ) ot
             JOIN routes r ON r.agency_id = ot.agency_id AND r.route_id = ot.route_id
             LEFT JOIN (
               SELECT rs.agency_id, rs.route_id,
                 ROUND(AVG(
                   CASE WHEN rs.scheduled_speed_mps > 0 AND rsd.avg_actual IS NOT NULL
                     THEN (rs.scheduled_speed_mps - rsd.avg_actual) / rs.scheduled_speed_mps * 100.0
                     ELSE NULL END
                 ), 1) AS speed_vs_scheduled_pct
               FROM route_speed rs
               LEFT JOIN (
                 SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) AS avg_actual
                 FROM route_speed_daily
                 WHERE service_date >= DATE('now', '-' || ? || ' days')
                 GROUP BY agency_id, route_id, direction_id
               ) rsd ON rsd.agency_id = rs.agency_id AND rsd.route_id = rs.route_id AND rsd.direction_id = rs.direction_id
               GROUP BY rs.agency_id, rs.route_id
             ) sp ON sp.agency_id = ot.agency_id AND sp.route_id = ot.route_id
             ORDER BY ot.agency_id, CAST(r.short_name AS INTEGER), r.short_name",
        )
        .bind(days)
        .bind(agency)
        .bind(days)
        .fetch_all(&db.pool)
        .await?,
    };
    Ok(rows)
}
```

- [ ] **Step 4: Fix the call site in `src/web/handlers.rs`**

```rust
// scorecard handler:
let routes = scorecard_routes(&state.db, period_days, None).await.unwrap_or_default();
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cargo test scorecard_routes_filters_by_agency -- --nocapture
```

Expected: PASS

- [ ] **Step 6: Run the full test suite**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/metrics/mod.rs src/web/handlers.rs
git commit -m "feat: add agency_filter param to scorecard_routes"
```

---

## Task 4: Add `agency_filter` to `route_speed_summary`

**Files:**
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Write the failing test**

In the `#[cfg(test)] mod tests` block of `src/speed/mod.rs`, add:

```rust
#[tokio::test]
async fn route_speed_summary_filters_by_agency() {
    let db = test_db().await;
    sqlx::query!("INSERT INTO routes VALUES ('stm', 'R1', '15', 'Papineau', 3)")
        .execute(&db.pool).await.unwrap();
    sqlx::query!("INSERT INTO routes VALUES ('rtl', 'R2', '10', 'Longueuil', 3)")
        .execute(&db.pool).await.unwrap();
    sqlx::query!(
        "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
         VALUES ('stm', 'R1', 0, 5.5, 10, '2026-01-01T00:00:00Z')"
    ).execute(&db.pool).await.unwrap();
    sqlx::query!(
        "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
         VALUES ('rtl', 'R2', 0, 6.0, 8, '2026-01-01T00:00:00Z')"
    ).execute(&db.pool).await.unwrap();

    let all = route_speed_summary(&db, None).await.unwrap();
    assert_eq!(all.len(), 2);

    let stm = route_speed_summary(&db, Some("stm")).await.unwrap();
    assert_eq!(stm.len(), 1);
    assert_eq!(stm[0].agency_id, "stm");
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cargo test route_speed_summary_filters_by_agency -- --nocapture
```

Expected: compile error — wrong number of arguments.

- [ ] **Step 3: Update `route_speed_summary` signature and implementation**

Replace the existing `route_speed_summary` function in `src/speed/mod.rs`:

```rust
/// Fetch scheduled, live, and historical actual speed for all routes.
/// Live speed: average from vehicle positions in the last hour.
/// Actual speed: average from route_speed_daily over the last 7 days.
/// If `agency_filter` is Some, only returns routes for that agency.
pub async fn route_speed_summary(db: &Database, agency_filter: Option<&str>) -> Result<Vec<RouteSpeedSummary>> {
    let rows: Vec<RouteSpeedSummary> = match agency_filter {
        None => sqlx::query_as(
            "SELECT
               rs.agency_id,
               rs.route_id,
               r.short_name,
               r.long_name,
               rs.direction_id,
               rs.scheduled_speed_mps,
               rs.trip_count,
               live.avg_live_speed as live_speed_mps,
               hist.avg_actual_speed as actual_speed_mps
             FROM route_speed rs
             JOIN routes r ON rs.agency_id = r.agency_id AND rs.route_id = r.route_id
             LEFT JOIN (
               SELECT t.agency_id, t.route_id, AVG(vp.speed) as avg_live_speed
               FROM vehicle_positions vp
               JOIN trips t ON t.trip_id = vp.trip_id AND t.agency_id = vp.agency_id
               WHERE vp.speed IS NOT NULL
                 AND vp.observed_at >= datetime('now', '-1 hour')
               GROUP BY t.agency_id, t.route_id
             ) live ON live.agency_id = rs.agency_id AND live.route_id = rs.route_id
             LEFT JOIN (
               SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) as avg_actual_speed
               FROM route_speed_daily
               WHERE service_date >= DATE('now', '-7 days')
               GROUP BY agency_id, route_id, direction_id
             ) hist ON hist.agency_id = rs.agency_id AND hist.route_id = rs.route_id AND hist.direction_id = rs.direction_id
             ORDER BY rs.agency_id, CAST(r.short_name AS INTEGER), r.short_name, rs.direction_id",
        )
        .fetch_all(&db.pool)
        .await?,

        Some(agency) => sqlx::query_as(
            "SELECT
               rs.agency_id,
               rs.route_id,
               r.short_name,
               r.long_name,
               rs.direction_id,
               rs.scheduled_speed_mps,
               rs.trip_count,
               live.avg_live_speed as live_speed_mps,
               hist.avg_actual_speed as actual_speed_mps
             FROM route_speed rs
             JOIN routes r ON rs.agency_id = r.agency_id AND rs.route_id = r.route_id
             LEFT JOIN (
               SELECT t.agency_id, t.route_id, AVG(vp.speed) as avg_live_speed
               FROM vehicle_positions vp
               JOIN trips t ON t.trip_id = vp.trip_id AND t.agency_id = vp.agency_id
               WHERE vp.speed IS NOT NULL
                 AND vp.observed_at >= datetime('now', '-1 hour')
               GROUP BY t.agency_id, t.route_id
             ) live ON live.agency_id = rs.agency_id AND live.route_id = rs.route_id
             LEFT JOIN (
               SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) as avg_actual_speed
               FROM route_speed_daily
               WHERE service_date >= DATE('now', '-7 days')
               GROUP BY agency_id, route_id, direction_id
             ) hist ON hist.agency_id = rs.agency_id AND hist.route_id = rs.route_id AND hist.direction_id = rs.direction_id
             WHERE rs.agency_id = ?
             ORDER BY rs.agency_id, CAST(r.short_name AS INTEGER), r.short_name, rs.direction_id",
        )
        .bind(agency)
        .fetch_all(&db.pool)
        .await?,
    };
    Ok(rows)
}
```

- [ ] **Step 4: Fix the call site in `src/web/handlers.rs`**

```rust
// speed_page handler:
let speeds = route_speed_summary(&state.db, None).await.unwrap_or_default();

// api_route_speed handler:
route_speed_summary(&state.db, None).await.map(axum::Json)...
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cargo test route_speed_summary_filters_by_agency -- --nocapture
```

Expected: PASS

- [ ] **Step 6: Run the full test suite**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/speed/mod.rs src/web/handlers.rs
git commit -m "feat: add agency_filter param to route_speed_summary"
```

---

## Task 5: Dashboard — agency labels + filter bar

**Files:**
- Modify: `src/web/handlers.rs`
- Modify: `templates/dashboard.html`

- [ ] **Step 1: Update `DashboardTemplate` and `dashboard` handler in `src/web/handlers.rs`**

Replace the existing `DashboardTemplate` struct and `dashboard` function:

```rust
#[derive(Deserialize)]
pub struct AgencyFilterParams {
    agency: Option<String>,
}

#[derive(Template)]
#[template(path = "dashboard.html")]
struct DashboardTemplate {
    routes: Vec<RouteSummary>,
    period_days: i64,
    agencies: Vec<(String, String)>,
    agency_names: std::collections::HashMap<String, String>,
    active_agency: String,
}

pub async fn dashboard(
    State(state): State<AppState>,
    Query(params): Query<AgencyFilterParams>,
) -> Html<String> {
    let period_days: i64 = 7;
    let active_agency = params.agency.unwrap_or_default();
    let filter = if active_agency.is_empty() { None } else { Some(active_agency.as_str()) };
    let routes = route_summary(&state.db, period_days, filter).await.unwrap_or_default();
    let agencies: Vec<(String, String)> = state.config.agencies.iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> = agencies.iter().cloned().collect();
    let tmpl = DashboardTemplate { routes, period_days, agencies, agency_names, active_agency };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
}
```

- [ ] **Step 2: Update `templates/dashboard.html`**

Add filter bar CSS inside the `<style>` block (before `footer {`):

```css
    .filter-bar { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 1.5rem; }
    .filter-bar a { padding: 0.35rem 0.75rem; border-radius: 20px; background: white;
                    border: 1px solid #ddd; font-size: 0.85rem; text-decoration: none;
                    color: #555; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
    .filter-bar a:hover { border-color: #999; color: #222; }
    .filter-bar a.active { background: #1a1a2e; color: white; border-color: #1a1a2e; }
```

Add filter bar HTML between the `</div>` (closing `.summary-bar`) and the `<table>`:

```html
    <div class="filter-bar">
      <a href="/" class="{% if active_agency.is_empty() %}active{% endif %}">All</a>
      {% for (slug, name) in &agencies %}
      <a href="/?agency={{ slug }}" class="{% if active_agency == slug %}active{% endif %}">{{ name }}</a>
      {% endfor %}
    </div>
```

In the route table body, update the route cell to show agency name inline. Replace:

```html
          <td><span class="route-id">{{ route.short_name }}</span></td>
```

with:

```html
          <td><span class="route-id">{% if let Some(n) = agency_names.get(&route.agency_id) %}{{ n }} {% endif %}{{ route.short_name }}</span></td>
```

Update the footer to remove the STM-specific text. Replace:

```html
  <footer>Mobilispect · Last 7 days · Data: STM GTFS-RT</footer>
```

with:

```html
  <footer>Mobilispect · Last 7 days</footer>
```

- [ ] **Step 3: Build to verify it compiles**

```bash
cargo build
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add src/web/handlers.rs templates/dashboard.html
git commit -m "feat: add agency filter bar and labels to dashboard"
```

---

## Task 6: Scorecard — agency labels + filter bar

**Files:**
- Modify: `src/web/handlers.rs`
- Modify: `templates/scorecard.html`

- [ ] **Step 1: Update `ScorecardTemplate` and `scorecard` handler in `src/web/handlers.rs`**

Add `agencies`, `agency_names`, and `active_agency` fields to `ScorecardTemplate`:

```rust
#[derive(Template)]
#[template(path = "scorecard.html")]
struct ScorecardTemplate {
    routes: Vec<ScorecardRoute>,
    benchmarks: Vec<Benchmark>,
    floor_pct: f64,
    ceiling_pct: f64,
    floor_city: String,
    ceiling_city: String,
    routes_meeting_floor: usize,
    worst_gap: Option<f64>,
    period_days: i64,
    generated_at: String,
    agencies: Vec<(String, String)>,
    agency_names: std::collections::HashMap<String, String>,
    active_agency: String,
}
```

Replace the `scorecard` handler:

```rust
pub async fn scorecard(
    State(state): State<AppState>,
    Query(params): Query<AgencyFilterParams>,
) -> Html<String> {
    let period_days: i64 = 7;
    let active_agency = params.agency.unwrap_or_default();
    let filter = if active_agency.is_empty() { None } else { Some(active_agency.as_str()) };
    let benchmarks = load_benchmarks(&state.db).await.unwrap_or_default();
    let routes = scorecard_routes(&state.db, period_days, filter).await.unwrap_or_default();

    let floor_pct = benchmarks.first().map(|b| b.on_time_pct).unwrap_or(89.0);
    let floor_speed = benchmarks.first().map(|b| b.speed_vs_scheduled_pct).unwrap_or(3.0);
    let ceiling_pct = benchmarks.last().map(|b| b.on_time_pct).unwrap_or(96.0);
    let floor_city = benchmarks.first().map(|b| b.city.clone()).unwrap_or_else(|| "Helsinki".to_string());
    let ceiling_city = benchmarks.last().map(|b| b.city.clone()).unwrap_or_else(|| "Tokyo".to_string());

    let routes_meeting_floor = routes.iter().filter(|r| {
        r.avg_on_time_pct.map_or(false, |p| p >= floor_pct)
            && r.speed_vs_scheduled_pct.map_or(true, |s| s <= floor_speed)
    }).count();

    let worst_gap = routes.iter()
        .filter_map(|r| r.on_time_gap_vs(floor_pct))
        .reduce(f64::min);

    let generated_at = Utc::now().format("%Y-%m-%d %H:%M UTC").to_string();

    let agencies: Vec<(String, String)> = state.config.agencies.iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> = agencies.iter().cloned().collect();

    let tmpl = ScorecardTemplate {
        routes, benchmarks, floor_pct, ceiling_pct, floor_city, ceiling_city,
        routes_meeting_floor, worst_gap, period_days, generated_at,
        agencies, agency_names, active_agency,
    };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
}
```

- [ ] **Step 2: Update `templates/scorecard.html`**

Add the same filter bar CSS inside `<style>` (before `footer {` or at end of style block):

```css
    .filter-bar { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 1.5rem; }
    .filter-bar a { padding: 0.35rem 0.75rem; border-radius: 20px; background: white;
                    border: 1px solid #ddd; font-size: 0.85rem; text-decoration: none;
                    color: #555; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
    .filter-bar a:hover { border-color: #999; color: #222; }
    .filter-bar a.active { background: #1a1a2e; color: white; border-color: #1a1a2e; }
```

Add filter bar HTML between the `.note` div and the `<table>`:

```html
    <div class="filter-bar">
      <a href="/scorecard" class="{% if active_agency.is_empty() %}active{% endif %}">All</a>
      {% for (slug, name) in &agencies %}
      <a href="/scorecard?agency={{ slug }}" class="{% if active_agency == slug %}active{% endif %}">{{ name }}</a>
      {% endfor %}
    </div>
```

Find the route cell in the scorecard table (the cell showing `route.short_name`) and update it to include the agency name. The exact cell will look like:

```html
<td class="route-id">
```

or similar. Update the route name cell to:

```html
          <td class="route-id">{% if let Some(n) = agency_names.get(&route.agency_id) %}{{ n }} {% endif %}{{ route.short_name }}</td>
```

- [ ] **Step 3: Build to verify it compiles**

```bash
cargo build
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add src/web/handlers.rs templates/scorecard.html
git commit -m "feat: add agency filter bar and labels to scorecard"
```

---

## Task 7: Speed page — agency labels + filter bar

**Files:**
- Modify: `src/web/handlers.rs`
- Modify: `templates/speed.html`

- [ ] **Step 1: Update `SpeedTemplate` and `speed_page` handler in `src/web/handlers.rs`**

```rust
#[derive(Template)]
#[template(path = "speed.html")]
struct SpeedTemplate {
    speeds: Vec<RouteSpeedSummary>,
    agencies: Vec<(String, String)>,
    agency_names: std::collections::HashMap<String, String>,
    active_agency: String,
}

pub async fn speed_page(
    State(state): State<AppState>,
    Query(params): Query<AgencyFilterParams>,
) -> Html<String> {
    let active_agency = params.agency.unwrap_or_default();
    let filter = if active_agency.is_empty() { None } else { Some(active_agency.as_str()) };
    let speeds = route_speed_summary(&state.db, filter).await.unwrap_or_default();
    let agencies: Vec<(String, String)> = state.config.agencies.iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> = agencies.iter().cloned().collect();
    let tmpl = SpeedTemplate { speeds, agencies, agency_names, active_agency };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
}
```

- [ ] **Step 2: Update `templates/speed.html`**

Add filter bar CSS inside `<style>`:

```css
    .filter-bar { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 1.5rem; }
    .filter-bar a { padding: 0.35rem 0.75rem; border-radius: 20px; background: white;
                    border: 1px solid #ddd; font-size: 0.85rem; text-decoration: none;
                    color: #555; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
    .filter-bar a:hover { border-color: #999; color: #222; }
    .filter-bar a.active { background: #1a1a2e; color: white; border-color: #1a1a2e; }
```

Add filter bar HTML between the `.note` div and the `<table>`:

```html
    <div class="filter-bar">
      <a href="/speed" class="{% if active_agency.is_empty() %}active{% endif %}">All</a>
      {% for (slug, name) in &agencies %}
      <a href="/speed?agency={{ slug }}" class="{% if active_agency == slug %}active{% endif %}">{{ name }}</a>
      {% endfor %}
    </div>
```

Find the route cell in the speed table (the cell showing `speed.short_name`) and update it to include the agency name:

```html
          <td><span class="route-id">{% if let Some(n) = agency_names.get(&speed.agency_id) %}{{ n }} {% endif %}{{ speed.short_name }}</span><br><span class="dir">{{ speed.direction_label() }}</span></td>
```

- [ ] **Step 3: Build to verify it compiles**

```bash
cargo build
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add src/web/handlers.rs templates/speed.html
git commit -m "feat: add agency filter bar and labels to speed page"
```

---

## Task 8: Report — agency labels (no filter bar)

**Files:**
- Modify: `src/web/handlers.rs`
- Modify: `templates/report.html`

- [ ] **Step 1: Update `ReportTemplate` and `report` handler in `src/web/handlers.rs`**

```rust
#[derive(Template)]
#[template(path = "report.html")]
struct ReportTemplate {
    routes: Vec<RouteSummary>,
    period_days: i64,
    generated_at: String,
    agency_names: std::collections::HashMap<String, String>,
}

pub async fn report(State(state): State<AppState>) -> Html<String> {
    let period_days: i64 = 7;
    let routes = route_summary(&state.db, period_days, None).await.unwrap_or_default();
    let generated_at = Utc::now().format("%Y-%m-%d %H:%M UTC").to_string();
    let agency_names: std::collections::HashMap<String, String> = state.config.agencies.iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let tmpl = ReportTemplate { routes, period_days, generated_at, agency_names };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
}
```

- [ ] **Step 2: Update the route cell in `templates/report.html`**

Find the cell rendering the route short name (similar to `{{ route.short_name }}`) and update it to:

```html
{% if let Some(n) = agency_names.get(&route.agency_id) %}{{ n }} {% endif %}{{ route.short_name }}
```

- [ ] **Step 3: Build and run the full test suite**

```bash
cargo build && cargo test
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add src/web/handlers.rs templates/report.html
git commit -m "feat: add agency labels to print report"
```

# Scheduled Speed by Day Type Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compute weekday/Saturday/Sunday scheduled speeds from static GTFS calendar data so the speed cards show correct day-type averages immediately after a GTFS load, with no dependency on accumulated real-world observations.

**Architecture:** Add a `calendar` table (mapping `service_id` to days-of-week) and a `route_speed_day_type` table (one row per route+direction+day_type). The GTFS static loader populates `calendar`; a new `compute_route_speed_by_day_type` function reads `trips JOIN calendar JOIN scheduled_stops` to compute and store scheduled speeds per day type; `route_speed_by_day_type` is updated to query `route_speed_day_type` instead of `route_speed_hourly`.

**Tech Stack:** Rust, sqlx 0.8 (runtime `sqlx::query` — no compile-time macros for new tables), PostgreSQL, gtfs-structures 0.26, testcontainers for integration tests.

---

## File Map

| File | Change |
|------|--------|
| `migrations/002_calendar.sql` | Create — new `calendar` and `route_speed_day_type` tables |
| `src/gtfs/static_feed.rs` | Modify — add `load_calendar`, call it inside the GTFS load transaction, delete stale rows on reload |
| `src/speed/mod.rs` | Modify — add `compute_route_speed_by_day_type`; update `route_speed_by_day_type` query |
| `src/bin/worker.rs` | Modify — call `compute_route_speed_by_day_type` after GTFS load |

---

### Task 1: Migration — add `calendar` and `route_speed_day_type` tables

**Files:**
- Create: `migrations/002_calendar.sql`

- [ ] **Step 1: Write the migration**

```sql
-- migrations/002_calendar.sql

CREATE TABLE calendar (
    agency_id   TEXT    NOT NULL,
    service_id  TEXT    NOT NULL,
    monday      BOOLEAN NOT NULL,
    tuesday     BOOLEAN NOT NULL,
    wednesday   BOOLEAN NOT NULL,
    thursday    BOOLEAN NOT NULL,
    friday      BOOLEAN NOT NULL,
    saturday    BOOLEAN NOT NULL,
    sunday      BOOLEAN NOT NULL,
    PRIMARY KEY (agency_id, service_id)
);

CREATE TABLE route_speed_day_type (
    agency_id            TEXT             NOT NULL,
    route_id             TEXT             NOT NULL,
    direction_id         BIGINT           NOT NULL,
    day_type             TEXT             NOT NULL,  -- 'weekday', 'saturday', 'sunday'
    scheduled_speed_mps  DOUBLE PRECISION NOT NULL,
    trip_count           BIGINT           NOT NULL,
    computed_at          TEXT             NOT NULL,
    PRIMARY KEY (agency_id, route_id, direction_id, day_type)
);
```

- [ ] **Step 2: Verify the migration applies cleanly**

```bash
cargo sqlx migrate run --database-url postgres://mobilispect:mobilispect@localhost:5433/mobilispect
```

Expected: `Applied 002_calendar.sql` with no errors. Integration tests also apply migrations via `sqlx::migrate!()` so this is automatically tested in Tasks 2–4.

- [ ] **Step 3: Commit**

```bash
git add migrations/002_calendar.sql
git commit -m "feat: add calendar and route_speed_day_type tables"
```

---

### Task 2: Load calendar data in the GTFS static loader

**Files:**
- Modify: `src/gtfs/static_feed.rs`

- [ ] **Step 1: Write the failing test**

Add to `src/gtfs/static_feed.rs` inside `#[cfg(test)] mod tests`:

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;
    use chrono::NaiveDate;
    use gtfs_structures::Calendar;
    use std::collections::HashMap;

    #[tokio::test]
    async fn load_calendar_inserts_service_day_flags() {
        let td = test_utils::setup().await;
        let db = td.db;
        let mut tx = db.pool.begin().await.unwrap();

        let mut calendar: HashMap<String, Calendar> = HashMap::new();
        calendar.insert(
            "WD".to_string(),
            Calendar {
                id: "WD".to_string(),
                monday: true,
                tuesday: true,
                wednesday: true,
                thursday: true,
                friday: true,
                saturday: false,
                sunday: false,
                start_date: NaiveDate::from_ymd_opt(2026, 1, 1).unwrap(),
                end_date: NaiveDate::from_ymd_opt(2026, 12, 31).unwrap(),
            },
        );
        calendar.insert(
            "SAT".to_string(),
            Calendar {
                id: "SAT".to_string(),
                monday: false,
                tuesday: false,
                wednesday: false,
                thursday: false,
                friday: false,
                saturday: true,
                sunday: false,
                start_date: NaiveDate::from_ymd_opt(2026, 1, 1).unwrap(),
                end_date: NaiveDate::from_ymd_opt(2026, 12, 31).unwrap(),
            },
        );

        load_calendar(&mut tx, "stm", &calendar).await.unwrap();
        tx.commit().await.unwrap();

        let rows: Vec<(String, bool, bool, bool)> = sqlx::query_as(
            "SELECT service_id, monday, saturday, sunday FROM calendar WHERE agency_id = 'stm' ORDER BY service_id",
        )
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 2);
        let wd = rows.iter().find(|r| r.0 == "WD").unwrap();
        assert!(wd.1, "WD monday should be true");
        assert!(!wd.2, "WD saturday should be false");
        let sat = rows.iter().find(|r| r.0 == "SAT").unwrap();
        assert!(sat.2, "SAT saturday should be true");
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

```bash
cargo test load_calendar_inserts_service_day_flags 2>&1
```

Expected: compile error — `load_calendar` is not defined.

- [ ] **Step 3: Implement `load_calendar`**

Add this function to `src/gtfs/static_feed.rs` (before the `get_stored_version` function):

```rust
async fn load_calendar(
    tx: &mut Tx<'_>,
    agency_id: &str,
    calendar: &std::collections::HashMap<String, gtfs_structures::Calendar>,
) -> Result<()> {
    for cal in calendar.values() {
        sqlx::query(
            "INSERT INTO calendar
             (agency_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
             ON CONFLICT (agency_id, service_id) DO UPDATE SET
               monday    = EXCLUDED.monday,
               tuesday   = EXCLUDED.tuesday,
               wednesday = EXCLUDED.wednesday,
               thursday  = EXCLUDED.thursday,
               friday    = EXCLUDED.friday,
               saturday  = EXCLUDED.saturday,
               sunday    = EXCLUDED.sunday",
        )
        .bind(agency_id)
        .bind(&cal.id)
        .bind(cal.monday)
        .bind(cal.tuesday)
        .bind(cal.wednesday)
        .bind(cal.thursday)
        .bind(cal.friday)
        .bind(cal.saturday)
        .bind(cal.sunday)
        .execute(&mut **tx)
        .await?;
    }
    info!("Loaded {} calendar entries", calendar.len());
    Ok(())
}
```

Wire it into `load_if_needed`: add a `DELETE` before the existing deletes, and call `load_calendar` at the end of the transaction:

```rust
// In the delete block (add this line alongside the existing deletes):
sqlx::query("DELETE FROM calendar WHERE agency_id = $1")
    .bind(slug)
    .execute(&mut *tx)
    .await?;

// After load_scheduled_stops (add this line):
load_calendar(&mut tx, slug, &gtfs.calendar).await?;
```

The full delete block should look like:
```rust
sqlx::query("DELETE FROM scheduled_stops WHERE agency_id = $1").bind(slug).execute(&mut *tx).await?;
sqlx::query("DELETE FROM trips WHERE agency_id = $1").bind(slug).execute(&mut *tx).await?;
sqlx::query("DELETE FROM stops WHERE agency_id = $1").bind(slug).execute(&mut *tx).await?;
sqlx::query("DELETE FROM routes WHERE agency_id = $1").bind(slug).execute(&mut *tx).await?;
sqlx::query("DELETE FROM calendar WHERE agency_id = $1").bind(slug).execute(&mut *tx).await?;
```

The full load block should look like:
```rust
load_routes(&mut tx, slug, &gtfs).await?;
load_trips(&mut tx, slug, &gtfs).await?;
load_stops(&mut tx, slug, &gtfs).await?;
load_scheduled_stops(&mut tx, slug, &gtfs).await?;
load_calendar(&mut tx, slug, &gtfs.calendar).await?;
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
cargo test load_calendar_inserts_service_day_flags 2>&1
```

Expected: `test ... ok`

- [ ] **Step 5: Commit**

```bash
git add src/gtfs/static_feed.rs
git commit -m "feat: load calendar data from GTFS static feed"
```

---

### Task 3: Implement `compute_route_speed_by_day_type`

**Files:**
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Write the failing test**

Add to the `#[cfg(test)] mod tests` block in `src/speed/mod.rs`:

```rust
#[tokio::test]
async fn compute_route_speed_by_day_type_stores_speed_per_day_type() {
    let td = test_utils::setup().await;
    let db = td.db;

    // Route R1 with outbound trips for three service types.
    sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '1', 'Route 1', 3)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO trips VALUES ('test', 'T_WD',  'R1', 'WD',  0, 'Dest')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO trips VALUES ('test', 'T_SAT', 'R1', 'SAT', 0, 'Dest')")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO trips VALUES ('test', 'T_SUN', 'R1', 'SUN', 0, 'Dest')")
        .execute(&db.pool).await.unwrap();

    // Calendar: WD = Mon-Fri, SAT = Saturday only, SUN = Sunday only.
    sqlx::query(
        "INSERT INTO calendar VALUES ('test','WD', true,true,true,true,true,false,false)"
    ).execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO calendar VALUES ('test','SAT',false,false,false,false,false,true,false)"
    ).execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO calendar VALUES ('test','SUN',false,false,false,false,false,false,true)"
    ).execute(&db.pool).await.unwrap();

    // Two stops ~1111 m apart.
    sqlx::query("INSERT INTO stops VALUES ('test','S1','Stop 1',45.50,-73.50)")
        .execute(&db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('test','S2','Stop 2',45.51,-73.50)")
        .execute(&db.pool).await.unwrap();

    // WD: 10 min → ~1.852 m/s; SAT: 20 min → ~0.926 m/s; SUN: 15 min → ~1.235 m/s
    for (trip_id, arr) in [("T_WD", "08:10:00"), ("T_SAT", "08:20:00"), ("T_SUN", "08:15:00")] {
        sqlx::query(&format!(
            "INSERT INTO scheduled_stops VALUES ('test','{trip_id}','S1',1,'08:00:00','08:00:00')"
        )).execute(&db.pool).await.unwrap();
        sqlx::query(&format!(
            "INSERT INTO scheduled_stops VALUES ('test','{trip_id}','S2',2,'{arr}','{arr}')"
        )).execute(&db.pool).await.unwrap();
    }

    compute_route_speed_by_day_type(&db, &test_agency()).await.unwrap();

    let rows: Vec<(String, f64, i64)> = sqlx::query_as(
        "SELECT day_type, scheduled_speed_mps, trip_count
         FROM route_speed_day_type
         WHERE agency_id = 'test' AND route_id = 'R1' AND direction_id = 0
         ORDER BY day_type",
    )
    .fetch_all(&db.pool)
    .await
    .unwrap();

    assert_eq!(rows.len(), 3, "expected one row per day type");

    let sat = rows.iter().find(|r| r.0 == "saturday").unwrap();
    let sun = rows.iter().find(|r| r.0 == "sunday").unwrap();
    let wd  = rows.iter().find(|r| r.0 == "weekday").unwrap();

    assert_eq!(wd.2,  1, "weekday trip_count");
    assert_eq!(sat.2, 1, "saturday trip_count");
    assert_eq!(sun.2, 1, "sunday trip_count");

    assert!((wd.1  - 1.852).abs() < 0.05, "weekday speed ~1.852 m/s, got {}", wd.1);
    assert!((sat.1 - 0.926).abs() < 0.05, "saturday speed ~0.926 m/s, got {}", sat.1);
    assert!((sun.1 - 1.235).abs() < 0.05, "sunday speed ~1.235 m/s, got {}", sun.1);
}
```

- [ ] **Step 2: Run to confirm it fails**

```bash
cargo test compute_route_speed_by_day_type_stores_speed_per_day_type 2>&1
```

Expected: compile error — `compute_route_speed_by_day_type` is not defined.

- [ ] **Step 3: Implement `compute_route_speed_by_day_type`**

Add this function to `src/speed/mod.rs` immediately after `compute_route_speed` (around line 201):

```rust
/// Compute scheduled average speed per route+direction broken down by day type
/// (weekday / saturday / sunday) using GTFS calendar data.
/// Stores results in `route_speed_day_type`.
pub async fn compute_route_speed_by_day_type(
    db: &Database,
    agency: &AgencyConfig,
) -> Result<()> {
    let now = Utc::now().to_rfc3339();
    let agency_id = &agency.slug;

    let combos: Vec<(String, i64)> = sqlx::query_as(
        "SELECT DISTINCT t.route_id, COALESCE(t.direction_id, 0) as direction_id
         FROM trips t
         JOIN scheduled_stops ss ON ss.trip_id = t.trip_id AND ss.agency_id = t.agency_id
         WHERE t.agency_id = $1
         GROUP BY t.route_id, t.direction_id
         HAVING COUNT(ss.stop_sequence) >= 2",
    )
    .bind(agency_id)
    .fetch_all(&db.pool)
    .await?;

    for (route_id, direction_id) in &combos {
        let trips: Vec<(String, String)> = sqlx::query_as(
            "SELECT t.trip_id, t.service_id FROM trips t
             WHERE t.agency_id = $1 AND t.route_id = $2 AND COALESCE(t.direction_id, 0) = $3",
        )
        .bind(agency_id)
        .bind(route_id)
        .bind(direction_id)
        .fetch_all(&db.pool)
        .await?;

        // day_type -> Vec<speed_mps>
        let mut day_speeds: std::collections::HashMap<&'static str, Vec<f64>> =
            std::collections::HashMap::new();

        for (trip_id, service_id) in &trips {
            // Look up which day types this service covers.
            let cal: Option<(bool, bool, bool, bool, bool, bool, bool)> = sqlx::query_as(
                "SELECT monday, tuesday, wednesday, thursday, friday, saturday, sunday
                 FROM calendar WHERE agency_id = $1 AND service_id = $2",
            )
            .bind(agency_id)
            .bind(service_id)
            .fetch_optional(&db.pool)
            .await?;

            let Some((mon, tue, wed, thu, fri, sat, sun)) = cal else {
                continue; // no calendar entry → skip
            };

            // Compute trip speed from scheduled stop times.
            let stops: Vec<(f64, f64, String)> = sqlx::query_as(
                "SELECT s.stop_lat, s.stop_lon, ss.arrival_time
                 FROM scheduled_stops ss
                 JOIN stops s ON s.stop_id = ss.stop_id AND s.agency_id = ss.agency_id
                 WHERE ss.agency_id = $1 AND ss.trip_id = $2
                 ORDER BY ss.stop_sequence",
            )
            .bind(agency_id)
            .bind(trip_id)
            .fetch_all(&db.pool)
            .await?;

            if stops.len() < 2 {
                continue;
            }

            let total_distance_m: f64 = stops
                .windows(2)
                .map(|w| haversine_meters(w[0].0, w[0].1, w[1].0, w[1].1))
                .sum();

            let first_secs = parse_time_secs(&stops.first().unwrap().2);
            let last_secs = parse_time_secs(&stops.last().unwrap().2);
            let duration_secs = match (first_secs, last_secs) {
                (Some(f), Some(l)) if l > f => (l - f) as f64,
                _ => continue,
            };

            if total_distance_m <= 0.0 || duration_secs <= 0.0 {
                continue;
            }

            let speed = total_distance_m / duration_secs;

            if mon || tue || wed || thu || fri {
                day_speeds.entry("weekday").or_default().push(speed);
            }
            if sat {
                day_speeds.entry("saturday").or_default().push(speed);
            }
            if sun {
                day_speeds.entry("sunday").or_default().push(speed);
            }
        }

        for (day_type, speeds) in &day_speeds {
            let avg_speed = speeds.iter().sum::<f64>() / speeds.len() as f64;
            let trip_count = speeds.len() as i64;
            sqlx::query(
                "INSERT INTO route_speed_day_type
                 (agency_id, route_id, direction_id, day_type, scheduled_speed_mps, trip_count, computed_at)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)
                 ON CONFLICT (agency_id, route_id, direction_id, day_type) DO UPDATE SET
                   scheduled_speed_mps = EXCLUDED.scheduled_speed_mps,
                   trip_count          = EXCLUDED.trip_count,
                   computed_at         = EXCLUDED.computed_at",
            )
            .bind(agency_id.as_str())
            .bind(route_id.as_str())
            .bind(direction_id)
            .bind(*day_type)
            .bind(avg_speed)
            .bind(trip_count)
            .bind(now.as_str())
            .execute(&db.pool)
            .await?;
        }
    }

    Ok(())
}
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
cargo test compute_route_speed_by_day_type_stores_speed_per_day_type 2>&1
```

Expected: `test ... ok`

- [ ] **Step 5: Commit**

```bash
git add src/speed/mod.rs
git commit -m "feat: compute scheduled speed per day type from GTFS calendar"
```

---

### Task 4: Update `route_speed_by_day_type` to read from schedule

**Files:**
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Write the failing test**

Add to `#[cfg(test)] mod tests` in `src/speed/mod.rs`:

```rust
#[tokio::test]
async fn route_speed_by_day_type_returns_scheduled_speeds_from_calendar() {
    let td = test_utils::setup().await;
    let db = td.db;

    sqlx::query("INSERT INTO routes VALUES ('test','R1','1','Route 1',3)")
        .execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO route_speed VALUES ('test','R1',0,10.0,5,'2026-01-01T00:00:00Z')",
    )
    .execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO route_speed_day_type
         (agency_id,route_id,direction_id,day_type,scheduled_speed_mps,trip_count,computed_at)
         VALUES ('test','R1',0,'weekday',8.0,10,'2026-01-01T00:00:00Z')",
    )
    .execute(&db.pool).await.unwrap();
    sqlx::query(
        "INSERT INTO route_speed_day_type
         (agency_id,route_id,direction_id,day_type,scheduled_speed_mps,trip_count,computed_at)
         VALUES ('test','R1',0,'saturday',6.0,5,'2026-01-01T00:00:00Z')",
    )
    .execute(&db.pool).await.unwrap();

    let rows = route_speed_by_day_type(&db, Some("test")).await.unwrap();

    assert_eq!(rows.len(), 1);
    let r = &rows[0];
    assert!((r.weekday_speed_mps.unwrap() - 8.0).abs() < 0.01,
        "weekday speed should be 8.0, got {:?}", r.weekday_speed_mps);
    assert!((r.saturday_speed_mps.unwrap() - 6.0).abs() < 0.01,
        "saturday speed should be 6.0, got {:?}", r.saturday_speed_mps);
    assert!(r.sunday_speed_mps.is_none(), "sunday should be None when no row exists");
}
```

- [ ] **Step 2: Run to confirm it fails**

```bash
cargo test route_speed_by_day_type_returns_scheduled_speeds_from_calendar 2>&1
```

Expected: test fails — the current query reads `route_speed_hourly`, not `route_speed_day_type`, so `weekday_speed_mps` comes back as `None` even though a row exists.

- [ ] **Step 3: Rewrite the `route_speed_by_day_type` query**

In `src/speed/mod.rs`, replace the entire `route_speed_by_day_type` function body with:

```rust
pub async fn route_speed_by_day_type(
    db: &Database,
    agency_filter: Option<&str>,
) -> Result<Vec<RouteSpeedDayType>> {
    let base_sql = "SELECT
          rs.agency_id,
          rs.route_id,
          r.short_name,
          r.long_name,
          rs.direction_id,
          wd.scheduled_speed_mps  AS weekday_speed_mps,
          sat.scheduled_speed_mps AS saturday_speed_mps,
          sun.scheduled_speed_mps AS sunday_speed_mps
        FROM route_speed rs
        JOIN routes r ON r.agency_id = rs.agency_id AND r.route_id = rs.route_id
        LEFT JOIN route_speed_day_type wd
          ON wd.agency_id = rs.agency_id AND wd.route_id = rs.route_id
         AND wd.direction_id = rs.direction_id AND wd.day_type = 'weekday'
        LEFT JOIN route_speed_day_type sat
          ON sat.agency_id = rs.agency_id AND sat.route_id = rs.route_id
         AND sat.direction_id = rs.direction_id AND sat.day_type = 'saturday'
        LEFT JOIN route_speed_day_type sun
          ON sun.agency_id = rs.agency_id AND sun.route_id = rs.route_id
         AND sun.direction_id = rs.direction_id AND sun.day_type = 'sunday'";

    let order_sql = "ORDER BY rs.agency_id,
          CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
          r.short_name, rs.direction_id";

    let rows = match agency_filter {
        None => {
            sqlx::query_as(&format!("{base_sql} {order_sql}"))
                .fetch_all(&db.pool)
                .await?
        }
        Some(agency) => {
            sqlx::query_as(&format!("{base_sql} WHERE rs.agency_id = $1 {order_sql}"))
                .bind(agency)
                .fetch_all(&db.pool)
                .await?
        }
    };
    Ok(rows)
}
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
cargo test route_speed_by_day_type_returns_scheduled_speeds_from_calendar 2>&1
```

Expected: `test ... ok`

- [ ] **Step 5: Run all speed tests to confirm nothing regressed**

```bash
cargo test speed:: 2>&1
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/speed/mod.rs
git commit -m "feat: route_speed_by_day_type reads from scheduled data instead of observed hourly"
```

---

### Task 5: Wire `compute_route_speed_by_day_type` into the worker

**Files:**
- Modify: `src/bin/worker.rs`

- [ ] **Step 1: Add the call after `compute_route_speed`**

In `src/bin/worker.rs`, update the static load block to also call the new function:

```rust
let result = async {
    gtfs::static_feed::load_if_needed(&db, &agency).await?;
    speed::compute_route_speed(&db, &agency).await?;
    speed::compute_route_speed_by_day_type(&db, &agency).await?;
    info!("Computed scheduled speed (all day types) for agency: {}", agency.name);
    Ok(())
}
.await;
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
cargo build --bin mobilispect-worker 2>&1
```

Expected: `Finished` with no errors.

- [ ] **Step 3: Commit**

```bash
git add src/bin/worker.rs
git commit -m "feat: compute scheduled speed by day type on GTFS load"
```

---

### Task 6: Verify end-to-end in the dev database

- [ ] **Step 1: Apply migration to dev DB**

```bash
cargo sqlx migrate run --database-url postgres://mobilispect:mobilispect@localhost:5433/mobilispect
```

- [ ] **Step 2: Force a GTFS reload**

Delete the stored feed version so the worker re-downloads the feed:

```bash
docker exec mobilispect-pg psql -U mobilispect mobilispect \
  -c "DELETE FROM feed_info WHERE key LIKE 'gtfs_static_version_%';"
```

- [ ] **Step 3: Restart the worker and wait for it to finish loading**

```bash
# In dev.sh terminal, the worker auto-restarts on rebuild.
# Or manually:
cargo run --bin mobilispect-worker
```

Wait for the log line `Computed scheduled speed (all day types) for agency: STM`.

- [ ] **Step 4: Confirm weekday speeds are present**

```bash
docker exec mobilispect-pg psql -U mobilispect mobilispect -c "
SELECT route_id, day_type, ROUND((scheduled_speed_mps * 3.6)::NUMERIC, 1) AS speed_kmh, trip_count
FROM route_speed_day_type
WHERE agency_id = 'stm'
ORDER BY route_id, day_type
LIMIT 15;"
```

Expected: rows with `day_type` = `weekday`, `saturday`, `sunday` and non-null `speed_kmh` values.

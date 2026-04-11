# SQLite → Postgres Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SQLite with Postgres so the HTTP server and background worker can run as separate Railway services sharing a single database.

**Architecture:** All existing SQLite-specific SQL (`?` placeholders, `datetime()`, `strftime()`, `PRAGMA`, `INSERT OR REPLACE`) is replaced with Postgres equivalents. Migrations are rewritten as a single clean Postgres schema. Tests switch from in-memory SQLite to `#[sqlx::test]` against a real Postgres instance. The Docker build continues to use `SQLX_OFFLINE=true` with a regenerated `.sqlx/` cache.

**Tech Stack:** Rust, sqlx 0.8 with `postgres` feature, Postgres 16, Railway Postgres plugin

---

## SQL Translation Reference

Use this table throughout all tasks:

| SQLite | Postgres |
|---|---|
| `?` (nth param) | `$N` (1-indexed) |
| `datetime('now', '-N hours')` | `NOW() - INTERVAL 'N hours'` |
| `datetime('now', '-N days')` | `NOW() - INTERVAL 'N days'` |
| `datetime('now', printf('-%d days', ?))` | `NOW() - ($N::INT * INTERVAL '1 day')` |
| `datetime('now', '-' \|\| ? \|\| ' days')` | `NOW() - ($N::INT * INTERVAL '1 day')` |
| `DATE('now', '-N days')` | `CURRENT_DATE - INTERVAL 'N days'` |
| `DATE('now', '-' \|\| ? \|\| ' days')` | `(CURRENT_DATE - $N::INT * INTERVAL '1 day')::TEXT` |
| `DATE(text_col)` (ISO timestamp text) | `text_col::TIMESTAMPTZ::DATE` |
| `DATE(text_col)` (YYYY-MM-DD text) | `text_col::DATE` |
| `strftime('%s', expr)` | `EXTRACT(EPOCH FROM (expr)::TIMESTAMPTZ)::BIGINT` |
| `strftime('%w', date_expr)` | `EXTRACT(DOW FROM date_expr::DATE)::INT` |
| `strftime('%Y-%m-%d %H', datetime('now', '-90 days'))` | `TO_CHAR(NOW() - INTERVAL '90 days', 'YYYY-MM-DD HH24')` |
| `printf('%02d', n)` | `LPAD(n::TEXT, 2, '0')` |
| `substr(s, 1, N)` | `SUBSTRING(s, 1, N)` |
| `CAST(x AS INTEGER)` | `x::BIGINT` |
| `INSERT OR REPLACE INTO tbl (pk, ...) VALUES (?, ...)` | `INSERT INTO tbl (pk, ...) VALUES ($1, ...) ON CONFLICT (pk_cols) DO UPDATE SET col = EXCLUDED.col, ...` |
| `INSERT OR IGNORE INTO tbl ...` | `INSERT INTO tbl ... ON CONFLICT DO NOTHING` |
| `CAST(r.short_name AS INTEGER)` in ORDER BY | `CASE WHEN r.short_name ~ '^\d+$' THEN r.short_name::INTEGER ELSE NULL END` |
| `INTEGER PRIMARY KEY AUTOINCREMENT` | `BIGSERIAL PRIMARY KEY` |
| `REAL` column | `DOUBLE PRECISION` column |
| `INTEGER` column used as `i64` in Rust | `BIGINT` column |
| `PRAGMA ...` | remove entirely |

---

## Files Modified

| File | Change |
|---|---|
| `Cargo.toml` | `sqlite` → `postgres` in sqlx features |
| `src/db/mod.rs` | `SqlitePool` → `PgPool`, remove `create_if_missing` |
| `migrations/` | Replace all 7 files with single `001_schema.sql` |
| `src/gtfs/static_feed.rs` | Remove PRAGMAs, fix Tx type, numbered placeholders, upserts |
| `src/gtfs/realtime.rs` | `$N` placeholders in two `query!` macros |
| `src/maintenance/mod.rs` | Fix interval arithmetic in DELETE queries |
| `src/metrics/mod.rs` | Fix all SQL: placeholders, intervals, strftime, upserts |
| `src/speed/mod.rs` | Fix all SQL: placeholders, intervals, strftime, upserts, tests |
| `.sqlx/` | Delete old files, regenerate with `cargo sqlx prepare` |
| `Dockerfile` | Remove SQLite volume comment, update `DATABASE_URL` default |

---

## Task 1: Start a local Postgres instance

**Files:** none (local environment only)

- [ ] **Step 1: Start Postgres via Docker**

```bash
docker run -d --name mobilispect-pg \
  -e POSTGRES_USER=mobilispect \
  -e POSTGRES_PASSWORD=mobilispect \
  -e POSTGRES_DB=mobilispect \
  -p 5432:5432 \
  postgres:16
```

Expected: container starts and `docker ps` shows it running.

- [ ] **Step 2: Set DATABASE_URL for development**

```bash
export DATABASE_URL="postgres://mobilispect:mobilispect@localhost:5432/mobilispect"
export SQLX_OFFLINE=false
```

Add these to your shell session for all subsequent tasks.

---

## Task 2: Switch sqlx driver in Cargo.toml and db/mod.rs

**Files:**
- Modify: `Cargo.toml`
- Modify: `src/db/mod.rs`

- [ ] **Step 1: Update Cargo.toml**

In `Cargo.toml`, change the sqlx dependency from:
```toml
sqlx = { version = "0.8", features = ["runtime-tokio", "sqlite", "chrono", "migrate"] }
```
to:
```toml
sqlx = { version = "0.8", features = ["runtime-tokio", "postgres", "chrono", "migrate"] }
```

- [ ] **Step 2: Rewrite src/db/mod.rs**

Replace the entire file contents:
```rust
use anyhow::Result;
use sqlx::postgres::{PgConnectOptions, PgPoolOptions};
use sqlx::PgPool;
use std::str::FromStr;

#[derive(Clone, Debug)]
pub struct Database {
    pub pool: PgPool,
}

impl Database {
    pub async fn connect(database_url: &str) -> Result<Self> {
        let options = PgConnectOptions::from_str(database_url)?;
        let pool = PgPoolOptions::new()
            .max_connections(5)
            .connect_with(options)
            .await?;
        Ok(Self { pool })
    }

    pub async fn migrate(&self) -> Result<()> {
        sqlx::migrate!("./migrations").run(&self.pool).await?;
        Ok(())
    }
}
```

- [ ] **Step 3: Verify it compiles (expected to fail on SQL syntax, not on types)**

```bash
cargo check 2>&1 | head -40
```

Expected: errors about SQL syntax or missing database, not about `PgPool` type mismatches.

---

## Task 3: Replace migrations with a single Postgres schema

**Files:**
- Delete: `migrations/001_initial.sql` through `migrations/007_route_speed_hourly.sql`
- Create: `migrations/001_schema.sql`

The new migration contains the complete final schema in Postgres types. Since we're starting fresh, there is no data to preserve.

- [ ] **Step 1: Delete all existing migration files**

```bash
rm /Users/alex/src/mobilispect/migrations/001_initial.sql
rm /Users/alex/src/mobilispect/migrations/002_arrival_time_unix.sql
rm /Users/alex/src/mobilispect/migrations/003_route_speed.sql
rm /Users/alex/src/mobilispect/migrations/004_route_speed_daily.sql
rm /Users/alex/src/mobilispect/migrations/005_benchmarks.sql
rm /Users/alex/src/mobilispect/migrations/006_agency_id.sql
rm /Users/alex/src/mobilispect/migrations/007_route_speed_hourly.sql
```

- [ ] **Step 2: Create migrations/001_schema.sql**

```sql
-- Static GTFS tables (the "plan")

CREATE TABLE routes (
    agency_id       TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    short_name      TEXT NOT NULL,
    long_name       TEXT NOT NULL,
    route_type      BIGINT NOT NULL,
    PRIMARY KEY (agency_id, route_id)
);

CREATE TABLE trips (
    agency_id       TEXT NOT NULL,
    trip_id         TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    service_id      TEXT NOT NULL,
    direction_id    BIGINT,
    trip_headsign   TEXT,
    PRIMARY KEY (agency_id, trip_id)
);
CREATE INDEX idx_trips_route ON trips(agency_id, route_id);

CREATE TABLE stops (
    agency_id       TEXT NOT NULL,
    stop_id         TEXT NOT NULL,
    stop_name       TEXT NOT NULL,
    stop_lat        DOUBLE PRECISION NOT NULL,
    stop_lon        DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (agency_id, stop_id)
);

CREATE TABLE scheduled_stops (
    agency_id       TEXT NOT NULL,
    trip_id         TEXT NOT NULL,
    stop_id         TEXT NOT NULL,
    stop_sequence   BIGINT NOT NULL,
    arrival_time    TEXT NOT NULL,
    departure_time  TEXT NOT NULL,
    PRIMARY KEY (agency_id, trip_id, stop_sequence)
);
CREATE INDEX idx_scheduled_stops_trip ON scheduled_stops(agency_id, trip_id);

-- GTFS-RT tables (the "reality")

CREATE TABLE vehicle_positions (
    id              BIGSERIAL PRIMARY KEY,
    agency_id       TEXT NOT NULL,
    observed_at     TEXT NOT NULL,
    trip_id         TEXT,
    vehicle_id      TEXT,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    bearing         DOUBLE PRECISION,
    speed           DOUBLE PRECISION,
    current_status  TEXT,
    stop_sequence   BIGINT
);
CREATE INDEX idx_vehicle_positions_trip   ON vehicle_positions(trip_id, observed_at);
CREATE INDEX idx_vehicle_positions_time   ON vehicle_positions(observed_at);
CREATE INDEX idx_vehicle_positions_agency ON vehicle_positions(agency_id);

CREATE TABLE stop_time_events (
    id                  BIGSERIAL PRIMARY KEY,
    agency_id           TEXT NOT NULL,
    observed_at         TEXT NOT NULL,
    trip_id             TEXT NOT NULL,
    stop_id             TEXT NOT NULL,
    stop_sequence       BIGINT,
    arrival_delay       BIGINT,
    departure_delay     BIGINT,
    arrival_time_unix   BIGINT,
    departure_time_unix BIGINT
);
CREATE INDEX idx_stop_time_events_trip   ON stop_time_events(trip_id, observed_at);
CREATE INDEX idx_stop_time_events_time   ON stop_time_events(observed_at);
CREATE INDEX idx_stop_time_events_agency ON stop_time_events(agency_id);

-- Performance tables (derived / computed)

CREATE TABLE trip_results (
    agency_id       TEXT NOT NULL,
    trip_id         TEXT NOT NULL,
    service_date    TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    on_time         BIGINT NOT NULL DEFAULT 0,
    avg_delay_secs  DOUBLE PRECISION,
    max_delay_secs  DOUBLE PRECISION,
    completed       BIGINT NOT NULL DEFAULT 0,
    computed_at     TEXT NOT NULL,
    PRIMARY KEY (agency_id, trip_id, service_date)
);

CREATE TABLE route_daily (
    agency_id       TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    service_date    TEXT NOT NULL,
    on_time_pct     DOUBLE PRECISION NOT NULL,
    avg_delay_secs  DOUBLE PRECISION,
    trips_run       BIGINT NOT NULL DEFAULT 0,
    trips_total     BIGINT NOT NULL DEFAULT 0,
    computed_at     TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, service_date)
);
CREATE INDEX idx_route_daily_date ON route_daily(service_date);

CREATE TABLE route_speed (
    agency_id            TEXT NOT NULL,
    route_id             TEXT NOT NULL,
    direction_id         BIGINT NOT NULL,
    scheduled_speed_mps  DOUBLE PRECISION NOT NULL,
    trip_count           BIGINT NOT NULL,
    computed_at          TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, direction_id)
);

CREATE TABLE route_speed_daily (
    agency_id         TEXT NOT NULL,
    route_id          TEXT NOT NULL,
    service_date      TEXT NOT NULL,
    direction_id      BIGINT NOT NULL,
    actual_speed_mps  DOUBLE PRECISION NOT NULL,
    trip_count        BIGINT NOT NULL,
    computed_at       TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, service_date, direction_id)
);
CREATE INDEX idx_route_speed_daily_date ON route_speed_daily(service_date);

CREATE TABLE route_speed_hourly (
    agency_id         TEXT NOT NULL,
    route_id          TEXT NOT NULL,
    direction_id      BIGINT NOT NULL,
    hour_utc          TEXT NOT NULL,
    actual_speed_mps  DOUBLE PRECISION NOT NULL,
    trip_count        BIGINT NOT NULL,
    computed_at       TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, direction_id, hour_utc)
);
CREATE INDEX idx_route_speed_hourly_hour ON route_speed_hourly(hour_utc);

-- Feed metadata

CREATE TABLE feed_info (
    key         TEXT PRIMARY KEY,
    value       TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

-- Benchmarks

CREATE TABLE benchmarks (
    id                     BIGINT PRIMARY KEY,
    system_name            TEXT NOT NULL UNIQUE,
    city                   TEXT NOT NULL,
    on_time_pct            DOUBLE PRECISION NOT NULL,
    speed_vs_scheduled_pct DOUBLE PRECISION NOT NULL,
    source_url             TEXT NOT NULL,
    year                   INTEGER NOT NULL
);

INSERT INTO benchmarks (id, system_name, city, on_time_pct, speed_vs_scheduled_pct, source_url, year) VALUES
  (1, 'Helsinki (HSL)',          'Helsinki',  89.0, 3.0, 'https://www.hsl.fi/en/hsl/statistics-and-research', 2023),
  (2, 'Zurich (ZVV)',            'Zurich',    92.0, 1.8, 'https://www.zvv.ch/zvv/en/about-zvv/facts-and-figures.html', 2023),
  (3, 'Singapore (SBS Transit)', 'Singapore', 92.0, 2.0, 'https://www.lta.gov.sg/content/ltagov/en/getting_around/public_transport/bus.html', 2023),
  (4, 'Tokyo (Toei Bus)',        'Tokyo',     96.0, 1.5, 'https://www.kotsu.metro.tokyo.jp/eng/services/bus.html', 2023);
```

- [ ] **Step 3: Apply the migration**

```bash
cargo sqlx migrate run
```

Expected: `Applied 1/migrate 001_schema` with no errors.

---

## Task 4: Update src/gtfs/static_feed.rs

**Files:**
- Modify: `src/gtfs/static_feed.rs`

Changes: remove PRAGMA statements (SQLite-only), change `Tx` type alias to Postgres, switch `INSERT OR REPLACE` to Postgres upserts, switch `?` to numbered `$N` placeholders in dynamic batch inserts, add `pg_placeholders` helper.

- [ ] **Step 1: Remove PRAGMA statements**

In `load_if_needed`, remove these three lines:
```rust
sqlx::query("PRAGMA journal_mode = WAL").execute(&db.pool).await?;
sqlx::query("PRAGMA synchronous = OFF").execute(&db.pool).await?;
sqlx::query("PRAGMA cache_size = -64000").execute(&db.pool).await?; // 64 MB cache
```

Also remove this line after `tx.commit()`:
```rust
sqlx::query("PRAGMA synchronous = NORMAL").execute(&db.pool).await?;
```

And remove the comment `// Enable bulk-load optimisations for this session` and `// Restore safe sync mode after bulk load`.

- [ ] **Step 2: Change the Tx type alias**

Change:
```rust
type Tx<'a> = sqlx::Transaction<'a, sqlx::Sqlite>;
```
to:
```rust
type Tx<'a> = sqlx::Transaction<'a, sqlx::Postgres>;
```

- [ ] **Step 3: Add the pg_placeholders helper**

Add this function before `load_routes`:
```rust
/// Generate Postgres-style numbered placeholders for a bulk INSERT.
/// E.g. pg_placeholders(2, 3) → "($1,$2,$3),($4,$5,$6)"
fn pg_placeholders(rows: usize, cols: usize) -> String {
    (0..rows)
        .map(|r| {
            let params = (0..cols)
                .map(|c| format!("${}", r * cols + c + 1))
                .collect::<Vec<_>>()
                .join(",");
            format!("({params})")
        })
        .collect::<Vec<_>>()
        .join(",")
}
```

- [ ] **Step 4: Fix load_routes**

Replace the existing `load_routes` function body (the `for chunk in rows.chunks(CHUNK)` loop) with:
```rust
    for chunk in rows.chunks(CHUNK) {
        let placeholders = pg_placeholders(chunk.len(), 5);
        let sql = format!(
            "INSERT INTO routes (agency_id, route_id, short_name, long_name, route_type) VALUES {placeholders}
             ON CONFLICT (agency_id, route_id) DO UPDATE SET
               short_name = EXCLUDED.short_name,
               long_name = EXCLUDED.long_name,
               route_type = EXCLUDED.route_type"
        );
        let mut q = sqlx::query(&sql);
        for (aid, id, short, long, rt) in chunk {
            q = q.bind(aid).bind(id).bind(short).bind(long).bind(rt);
        }
        q.execute(&mut **tx).await?;
    }
```

- [ ] **Step 5: Fix load_trips**

Replace the `for chunk in rows.chunks(CHUNK)` loop in `load_trips`:
```rust
    for chunk in rows.chunks(CHUNK) {
        let placeholders = pg_placeholders(chunk.len(), 6);
        let sql = format!(
            "INSERT INTO trips (agency_id, trip_id, route_id, service_id, direction_id, trip_headsign) VALUES {placeholders}
             ON CONFLICT (agency_id, trip_id) DO UPDATE SET
               route_id = EXCLUDED.route_id,
               service_id = EXCLUDED.service_id,
               direction_id = EXCLUDED.direction_id,
               trip_headsign = EXCLUDED.trip_headsign"
        );
        let mut q = sqlx::query(&sql);
        for (aid, id, route, svc, dir, head) in chunk {
            q = q.bind(aid).bind(id).bind(route).bind(svc).bind(dir).bind(head);
        }
        q.execute(&mut **tx).await?;
    }
```

- [ ] **Step 6: Fix load_stops**

Replace the `for chunk in rows.chunks(CHUNK)` loop in `load_stops`:
```rust
    for chunk in rows.chunks(CHUNK) {
        let placeholders = pg_placeholders(chunk.len(), 5);
        let sql = format!(
            "INSERT INTO stops (agency_id, stop_id, stop_name, stop_lat, stop_lon) VALUES {placeholders}
             ON CONFLICT (agency_id, stop_id) DO UPDATE SET
               stop_name = EXCLUDED.stop_name,
               stop_lat = EXCLUDED.stop_lat,
               stop_lon = EXCLUDED.stop_lon"
        );
        let mut q = sqlx::query(&sql);
        for (aid, id, name, lat, lon) in chunk {
            q = q.bind(aid).bind(id).bind(name).bind(lat).bind(lon);
        }
        q.execute(&mut **tx).await?;
    }
```

- [ ] **Step 7: Fix load_scheduled_stops**

Replace the `for (i, chunk) in rows.chunks(CHUNK).enumerate()` loop in `load_scheduled_stops`:
```rust
    let total = rows.len();
    for (i, chunk) in rows.chunks(CHUNK).enumerate() {
        let placeholders = pg_placeholders(chunk.len(), 6);
        let sql = format!(
            "INSERT INTO scheduled_stops \
             (agency_id, trip_id, stop_id, stop_sequence, arrival_time, departure_time) VALUES {placeholders}
             ON CONFLICT (agency_id, trip_id, stop_sequence) DO UPDATE SET
               stop_id = EXCLUDED.stop_id,
               arrival_time = EXCLUDED.arrival_time,
               departure_time = EXCLUDED.departure_time"
        );
        let mut q = sqlx::query(&sql);
        for (aid, tid, sid, seq, arr, dep) in chunk {
            q = q.bind(aid).bind(tid).bind(sid).bind(seq).bind(arr).bind(dep);
        }
        q.execute(&mut **tx).await?;

        let done = (i + 1) * CHUNK;
        if done % 100_000 < CHUNK {
            info!("  scheduled_stops: {}/{} rows", done.min(total), total);
        }
    }
```

- [ ] **Step 8: Fix get_stored_version and set_stored_version**

`get_stored_version` — change `?` to `$1`:
```rust
    let row = sqlx::query!(
        "SELECT value FROM feed_info WHERE key = $1",
        key,
    )
    .fetch_optional(&db.pool)
    .await?;
```

`set_stored_version` — change to Postgres upsert with `$1`, `$2`, `$3`:
```rust
    sqlx::query!(
        "INSERT INTO feed_info (key, value, updated_at) VALUES ($1, $2, $3)
         ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at",
        key,
        version,
        now,
    )
    .execute(&db.pool)
    .await?;
```

Also remove the `CHUNK` comment that mentions SQLite parameter limits:
Change:
```rust
/// Rows to bundle per INSERT statement. SQLite limit is 999 params (32766 in recent builds).
/// 6 params per scheduled_stop row → 500 rows = 3000 params, well within limits.
const CHUNK: usize = 500;
```
to:
```rust
/// Rows to bundle per INSERT statement.
const CHUNK: usize = 500;
```

- [ ] **Step 9: Verify src/gtfs/static_feed.rs compiles**

```bash
cargo check 2>&1 | grep "static_feed"
```

Expected: no errors mentioning `static_feed.rs`.

---

## Task 5: Update src/gtfs/realtime.rs

**Files:**
- Modify: `src/gtfs/realtime.rs`

Changes: `?` → `$N` in two `query!` macros.

- [ ] **Step 1: Fix store_vehicle_positions INSERT**

Change:
```rust
        sqlx::query!(
            "INSERT INTO vehicle_positions
             (agency_id, observed_at, trip_id, vehicle_id, latitude, longitude, bearing, speed, current_status, stop_sequence)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            agency_id,
            observed_at,
            trip_id,
            vehicle_id,
            lat,
            lon,
            bearing,
            speed,
            status,
            stop_seq,
        )
```
to:
```rust
        sqlx::query!(
            "INSERT INTO vehicle_positions
             (agency_id, observed_at, trip_id, vehicle_id, latitude, longitude, bearing, speed, current_status, stop_sequence)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)",
            agency_id,
            observed_at,
            trip_id,
            vehicle_id,
            lat,
            lon,
            bearing,
            speed,
            status,
            stop_seq,
        )
```

- [ ] **Step 2: Fix store_trip_updates INSERT**

Change:
```rust
            sqlx::query!(
                "INSERT INTO stop_time_events
                 (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_delay, departure_delay,
                  arrival_time_unix, departure_time_unix)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
```
to:
```rust
            sqlx::query!(
                "INSERT INTO stop_time_events
                 (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_delay, departure_delay,
                  arrival_time_unix, departure_time_unix)
                 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)",
```

- [ ] **Step 3: Verify realtime.rs compiles**

```bash
cargo check 2>&1 | grep "realtime"
```

Expected: no errors mentioning `realtime.rs`.

---

## Task 6: Update src/maintenance/mod.rs

**Files:**
- Modify: `src/maintenance/mod.rs`

Changes: replace `datetime()/printf()` interval arithmetic with Postgres equivalents, `?` → `$1`.

- [ ] **Step 1: Fix the stop_time_events DELETE**

Change:
```rust
        match sqlx::query!(
            "DELETE FROM stop_time_events WHERE observed_at < datetime('now', printf('-%d days', ?))",
            days
        )
```
to:
```rust
        match sqlx::query!(
            "DELETE FROM stop_time_events WHERE observed_at::TIMESTAMPTZ < NOW() - ($1::BIGINT * INTERVAL '1 day')",
            days
        )
```

- [ ] **Step 2: Fix the vehicle_positions DELETE**

Change:
```rust
        match sqlx::query!(
            "DELETE FROM vehicle_positions WHERE observed_at < datetime('now', printf('-%d days', ?))",
            days
        )
```
to:
```rust
        match sqlx::query!(
            "DELETE FROM vehicle_positions WHERE observed_at::TIMESTAMPTZ < NOW() - ($1::BIGINT * INTERVAL '1 day')",
            days
        )
```

- [ ] **Step 3: Verify maintenance/mod.rs compiles**

```bash
cargo check 2>&1 | grep "maintenance"
```

Expected: no errors mentioning `maintenance/mod.rs`.

---

## Task 7: Update src/metrics/mod.rs

**Files:**
- Modify: `src/metrics/mod.rs`

This file has the most complex SQL transformations. Work through each query systematically.

- [ ] **Step 1: Fix the trips SELECT in compute_route_daily**

Change (around line 69):
```rust
    let trips = sqlx::query!(
        "SELECT DISTINCT t.trip_id, t.route_id
         FROM trips t
         JOIN stop_time_events ste ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
         WHERE t.agency_id = ? AND DATE(ste.observed_at) = ?",
        agency_id,
        date_str,
    )
```
to:
```rust
    let trips = sqlx::query!(
        "SELECT DISTINCT t.trip_id, t.route_id
         FROM trips t
         JOIN stop_time_events ste ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
         WHERE t.agency_id = $1 AND ste.observed_at::TIMESTAMPTZ::DATE::TEXT = $2",
        agency_id,
        date_str,
    )
```

- [ ] **Step 2: Fix the delays query in compute_route_daily**

This is the complex `strftime('%s', ...)` and `printf('%02d', ...)` query. The query is passed to `sqlx::query_as::<_, (Option<i64>,)>` and uses `.bind()`. Replace the SQL string (lines ~88-107):

```rust
        let delays: Vec<i64> = sqlx::query_as::<_, (Option<i64>,)>(
            "SELECT
               CAST(COALESCE(
                 ste.arrival_delay,
                 CASE WHEN ste.arrival_time_unix IS NOT NULL
                   THEN ste.arrival_time_unix -
                        EXTRACT(EPOCH FROM (
                          $1 || 'T' ||
                          CASE WHEN SUBSTRING(ss.arrival_time, 1, 2)::INTEGER >= 24
                            THEN LPAD((SUBSTRING(ss.arrival_time, 1, 2)::INTEGER - 24)::TEXT, 2, '0')
                                 || SUBSTRING(ss.arrival_time, 3)
                            ELSE ss.arrival_time
                          END || $2
                        )::TIMESTAMPTZ)::BIGINT
                   ELSE NULL
                 END
               ) AS BIGINT) as delay
             FROM stop_time_events ste
             JOIN scheduled_stops ss
               ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id AND ss.agency_id = ste.agency_id
             WHERE ste.trip_id = $3 AND ste.agency_id = $4 AND ste.observed_at::TIMESTAMPTZ::DATE::TEXT = $5
               AND (ste.arrival_delay IS NOT NULL OR ste.arrival_time_unix IS NOT NULL)",
        )
        .bind(&date_str)
        .bind(offset)
        .bind(&trip.trip_id)
        .bind(agency_id)
        .bind(&date_str)
        .fetch_all(&db.pool)
        .await?
```

- [ ] **Step 3: Fix INSERT OR REPLACE INTO trip_results**

Change:
```rust
        sqlx::query!(
            "INSERT OR REPLACE INTO trip_results
             (agency_id, trip_id, service_date, route_id, on_time, avg_delay_secs, max_delay_secs, completed, computed_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)",
            agency_id,
            trip.trip_id,
            date_str,
            trip.route_id,
            on_time_flag,
            avg_delay,
            max_delay,
            now,
        )
```
to:
```rust
        sqlx::query!(
            "INSERT INTO trip_results
             (agency_id, trip_id, service_date, route_id, on_time, avg_delay_secs, max_delay_secs, completed, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, 1, $8)
             ON CONFLICT (agency_id, trip_id, service_date) DO UPDATE SET
               route_id = EXCLUDED.route_id,
               on_time = EXCLUDED.on_time,
               avg_delay_secs = EXCLUDED.avg_delay_secs,
               max_delay_secs = EXCLUDED.max_delay_secs,
               completed = EXCLUDED.completed,
               computed_at = EXCLUDED.computed_at",
            agency_id,
            trip.trip_id,
            date_str,
            trip.route_id,
            on_time_flag,
            avg_delay,
            max_delay,
            now,
        )
```

- [ ] **Step 4: Fix the routes/trip_results aggregation queries**

Change the `DISTINCT route_id` query (around line 148):
```rust
    let routes: Vec<(String,)> = sqlx::query_as(
        "SELECT DISTINCT route_id FROM trip_results WHERE agency_id = $1 AND service_date = $2",
    )
    .bind(agency_id)
    .bind(&date_str)
```

Change the aggregation query (around line 157):
```rust
        let row: (i64, i64, f64, i64) = sqlx::query_as(
            "SELECT
               COUNT(*) as trips_run,
               COALESCE(SUM(on_time), 0) as on_time_count,
               COALESCE(AVG(avg_delay_secs), 0.0) as avg_delay,
               (SELECT COUNT(*) FROM trips WHERE agency_id = $1 AND route_id = $2) as trips_total
             FROM trip_results
             WHERE agency_id = $3 AND route_id = $4 AND service_date = $5",
        )
        .bind(agency_id)
        .bind(route_id)
        .bind(agency_id)
        .bind(route_id)
        .bind(&date_str)
```

- [ ] **Step 5: Fix INSERT OR REPLACE INTO route_daily**

Change:
```rust
        sqlx::query!(
            "INSERT OR REPLACE INTO route_daily
             (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
```
to:
```rust
        sqlx::query!(
            "INSERT INTO route_daily
             (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
             ON CONFLICT (agency_id, route_id, service_date) DO UPDATE SET
               on_time_pct = EXCLUDED.on_time_pct,
               avg_delay_secs = EXCLUDED.avg_delay_secs,
               trips_run = EXCLUDED.trips_run,
               trips_total = EXCLUDED.trips_total,
               computed_at = EXCLUDED.computed_at",
```

- [ ] **Step 6: Fix stop_hotspots query**

This query has `strftime('%s', ...)`, `printf('%02d', ...)`, `DATE(ste.observed_at)`, and `datetime('now', '-' || ? || ' days')`.

Replace the SQL string in `stop_hotspots`:
```rust
    let rows: Vec<StopHotspot> = sqlx::query_as(
        "SELECT
           s.stop_id,
           s.stop_name,
           s.stop_lat,
           s.stop_lon,
           ROUND(AVG(CAST(COALESCE(
             ste.arrival_delay,
             CASE WHEN ste.arrival_time_unix IS NOT NULL
               THEN ste.arrival_time_unix - EXTRACT(EPOCH FROM (
                 ste.observed_at::TIMESTAMPTZ::DATE::TEXT || 'T' ||
                 CASE WHEN SUBSTRING(ss.arrival_time, 1, 2)::INTEGER >= 24
                   THEN LPAD((SUBSTRING(ss.arrival_time, 1, 2)::INTEGER - 24)::TEXT, 2, '0')
                        || SUBSTRING(ss.arrival_time, 3)
                   ELSE ss.arrival_time
                 END || $1
               )::TIMESTAMPTZ)::BIGINT
               ELSE NULL
             END
           ) AS DOUBLE PRECISION)), 0) as avg_delay_secs,
           COUNT(*) as observation_count
         FROM stop_time_events ste
         JOIN scheduled_stops ss
           ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id AND ss.agency_id = ste.agency_id
         JOIN stops s ON s.stop_id = ste.stop_id AND s.agency_id = ste.agency_id
         WHERE ste.observed_at::TIMESTAMPTZ >= NOW() - ($2::BIGINT * INTERVAL '1 day')
           AND (ste.arrival_delay IS NOT NULL OR ste.arrival_time_unix IS NOT NULL)
         GROUP BY s.stop_id, s.stop_name, s.stop_lat, s.stop_lon
         HAVING COUNT(*) >= 5
         ORDER BY avg_delay_secs DESC
         LIMIT $3",
    )
    .bind(offset)
    .bind(days)
    .bind(limit)
```

- [ ] **Step 7: Fix route_trend query**

Change the route lookup (around line 339):
```rust
    let route: Option<(String, String)> = sqlx::query_as(
        "SELECT short_name, long_name FROM routes WHERE agency_id = $1 AND route_id = $2",
    )
    .bind(agency_id)
    .bind(route_id)
```

Change the daily points query (around line 353). Replace `DATE('now', '-' || ? || ' days')` with Postgres equivalent:
```rust
    let points: Vec<(String, Option<f64>, Option<f64>, Option<f64>)> = sqlx::query_as(
        "SELECT
           rd.service_date,
           rd.on_time_pct,
           rd.avg_delay_secs,
           AVG(rsd.actual_speed_mps) as actual_speed_mps
         FROM route_daily rd
         LEFT JOIN route_speed_daily rsd
           ON rsd.agency_id = rd.agency_id AND rsd.route_id = rd.route_id AND rsd.service_date = rd.service_date
         WHERE rd.agency_id = $1 AND rd.route_id = $2
           AND rd.service_date >= (CURRENT_DATE - $3::INT * INTERVAL '1 day')::TEXT
         GROUP BY rd.service_date, rd.on_time_pct, rd.avg_delay_secs
         ORDER BY rd.service_date",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(days)
```

- [ ] **Step 8: Fix route_summary queries (both None and Some branches)**

Both branches of `route_summary` use `DATE('now', '-' || ? || ' days')`. Replace in both:

For the `None` branch:
```rust
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
             WHERE rd.service_date >= (CURRENT_DATE - $1::INT * INTERVAL '1 day')::TEXT
             GROUP BY rd.agency_id, rd.route_id, r.short_name, r.long_name
             ORDER BY rd.agency_id,
               CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
               r.short_name",
        )
        .bind(days)
```

For the `Some(agency)` branch:
```rust
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
             WHERE rd.service_date >= (CURRENT_DATE - $1::INT * INTERVAL '1 day')::TEXT
               AND rd.agency_id = $2
             GROUP BY rd.agency_id, rd.route_id, r.short_name, r.long_name
             ORDER BY rd.agency_id,
               CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
               r.short_name",
        )
        .bind(days)
        .bind(agency)
```

- [ ] **Step 9: Fix scorecard_routes queries (both branches)**

Both branches use `DATE('now', '-' || ? || ' days')`. Replace in both (the pattern appears twice per branch, for `route_daily` and `route_speed_daily`):

For the `None` branch:
```rust
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
               WHERE service_date >= (CURRENT_DATE - $1::INT * INTERVAL '1 day')::TEXT
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
                 WHERE service_date >= (CURRENT_DATE - $2::INT * INTERVAL '1 day')::TEXT
                 GROUP BY agency_id, route_id, direction_id
               ) rsd ON rsd.agency_id = rs.agency_id AND rsd.route_id = rs.route_id AND rsd.direction_id = rs.direction_id
               GROUP BY rs.agency_id, rs.route_id
             ) sp ON sp.agency_id = ot.agency_id AND sp.route_id = ot.route_id
             ORDER BY ot.agency_id,
               CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
               r.short_name",
        )
        .bind(days)
        .bind(days)
```

For the `Some(agency)` branch — find and read `src/metrics/mod.rs` lines ~592-640 for the exact SQL, then apply the same substitutions: `DATE('now', '-' || ? || ' days')` → `(CURRENT_DATE - $N::INT * INTERVAL '1 day')::TEXT`, and fix `CAST(r.short_name AS INTEGER)` → `CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST`. The `agency` filter moves to `$3`.

- [ ] **Step 10: Verify src/metrics/mod.rs compiles**

```bash
cargo check 2>&1 | grep "metrics"
```

Expected: no errors mentioning `metrics/mod.rs`.

---

## Task 8: Update src/speed/mod.rs

**Files:**
- Modify: `src/speed/mod.rs`

Changes: fix all SQL queries (placeholders, SQLite functions, upserts) and update tests.

- [ ] **Step 1: Fix INSERT OR REPLACE in compute_route_speed**

Change (around line 174):
```rust
        sqlx::query!(
            "INSERT OR REPLACE INTO route_speed
             (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES (?, ?, ?, ?, ?, ?)",
```
to:
```rust
        sqlx::query!(
            "INSERT INTO route_speed
             (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6)
             ON CONFLICT (agency_id, route_id, direction_id) DO UPDATE SET
               scheduled_speed_mps = EXCLUDED.scheduled_speed_mps,
               trip_count = EXCLUDED.trip_count,
               computed_at = EXCLUDED.computed_at",
```

- [ ] **Step 2: Fix INSERT OR REPLACE in compute_route_speed_daily**

Change (around line 276):
```rust
        sqlx::query!(
            "INSERT OR REPLACE INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)",
```
to:
```rust
        sqlx::query!(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             ON CONFLICT (agency_id, route_id, service_date, direction_id) DO UPDATE SET
               actual_speed_mps = EXCLUDED.actual_speed_mps,
               trip_count = EXCLUDED.trip_count,
               computed_at = EXCLUDED.computed_at",
```

- [ ] **Step 3: Fix datetime() in compute_route_speed_hourly**

Fix the `combos` query (around line 376):
```rust
    let combos: Vec<(String, i64)> = sqlx::query_as(
        "SELECT DISTINCT t.route_id, COALESCE(t.direction_id, 0) as direction_id
         FROM stop_time_events ste
         JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
         WHERE ste.agency_id = $1 AND ste.arrival_time_unix IS NOT NULL
           AND ste.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '4 hours'",
    )
    .bind(agency_id)
```

Fix the `trips` query (around line 388):
```rust
        let trips: Vec<(String,)> = sqlx::query_as(
            "SELECT DISTINCT ste.trip_id
             FROM stop_time_events ste
             JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
             WHERE t.agency_id = $1 AND t.route_id = $2
               AND COALESCE(t.direction_id, 0) = $3
               AND ste.arrival_time_unix IS NOT NULL
               AND ste.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '4 hours'",
        )
        .bind(agency_id)
        .bind(route_id)
        .bind(direction_id)
```

- [ ] **Step 4: Fix INSERT OR REPLACE in compute_route_speed_hourly**

Change the `sqlx::query(...)` call (around line 451):
```rust
            sqlx::query(
                "INSERT INTO route_speed_hourly
                 (agency_id, route_id, direction_id, hour_utc, actual_speed_mps, trip_count, computed_at)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)
                 ON CONFLICT (agency_id, route_id, direction_id, hour_utc) DO UPDATE SET
                   actual_speed_mps = EXCLUDED.actual_speed_mps,
                   trip_count = EXCLUDED.trip_count,
                   computed_at = EXCLUDED.computed_at",
            )
```

- [ ] **Step 5: Fix route_speed_summary — datetime() in live speed subquery**

Both branches (None and Some) have `vp.observed_at >= datetime('now', '-1 hour')` and `service_date >= DATE('now', '-7 days')`. Fix in both branches:

```
vp.observed_at >= datetime('now', '-1 hour')
```
→
```
vp.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '1 hour'
```

```
service_date >= DATE('now', '-7 days')
```
→
```
service_date >= (CURRENT_DATE - INTERVAL '7 days')::TEXT
```

Also fix `CAST(r.short_name AS INTEGER)` in ORDER BY → `CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST`.

The full SQL for the `None` branch becomes:
```sql
SELECT
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
    AND vp.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '1 hour'
  GROUP BY t.agency_id, t.route_id
) live ON live.agency_id = rs.agency_id AND live.route_id = rs.route_id
LEFT JOIN (
  SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) as avg_actual_speed
  FROM route_speed_daily
  WHERE service_date >= (CURRENT_DATE - INTERVAL '7 days')::TEXT
  GROUP BY agency_id, route_id, direction_id
) hist ON hist.agency_id = rs.agency_id AND hist.route_id = rs.route_id AND hist.direction_id = rs.direction_id
ORDER BY rs.agency_id,
  CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
  r.short_name, rs.direction_id
```

Apply the same changes to the `Some(agency)` branch (adds `WHERE rs.agency_id = $1`).

- [ ] **Step 6: Fix route_speed_by_day_type — strftime('%w', ...) and strftime('%Y-%m-%d %H', ...)**

Both branches (None and Some) use:
- `strftime('%w', substr(rsh.hour_utc, 1, 10))` → `EXTRACT(DOW FROM SUBSTRING(rsh.hour_utc, 1, 10)::DATE)::INT`
- `rsh.hour_utc >= strftime('%Y-%m-%d %H', datetime('now', '-90 days'))` → `rsh.hour_utc >= TO_CHAR(NOW() - INTERVAL '90 days', 'YYYY-MM-DD HH24')`

The full SQL for the `None` branch becomes:
```sql
SELECT
  rs.agency_id,
  rs.route_id,
  r.short_name,
  r.long_name,
  rs.direction_id,
  AVG(CASE WHEN EXTRACT(DOW FROM SUBSTRING(rsh.hour_utc, 1, 10)::DATE)::INT IN (1,2,3,4,5) THEN rsh.actual_speed_mps END) as weekday_speed_mps,
  AVG(CASE WHEN EXTRACT(DOW FROM SUBSTRING(rsh.hour_utc, 1, 10)::DATE)::INT = 6 THEN rsh.actual_speed_mps END) as saturday_speed_mps,
  AVG(CASE WHEN EXTRACT(DOW FROM SUBSTRING(rsh.hour_utc, 1, 10)::DATE)::INT = 0 THEN rsh.actual_speed_mps END) as sunday_speed_mps
FROM route_speed rs
JOIN routes r ON r.agency_id = rs.agency_id AND r.route_id = rs.route_id
LEFT JOIN route_speed_hourly rsh
  ON rsh.agency_id = rs.agency_id AND rsh.route_id = rs.route_id AND rsh.direction_id = rs.direction_id
  AND rsh.hour_utc >= TO_CHAR(NOW() - INTERVAL '90 days', 'YYYY-MM-DD HH24')
GROUP BY rs.agency_id, rs.route_id, rs.direction_id
ORDER BY rs.agency_id,
  CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
  r.short_name, rs.direction_id
```

Apply same changes to `Some(agency)` branch (adds `WHERE rs.agency_id = $1`).

- [ ] **Step 7: Verify src/speed/mod.rs compiles (non-test)**

```bash
cargo check 2>&1 | grep "speed"
```

Expected: only test-related errors remain (the tests still reference SQLite).

---

## Task 9: Update tests in src/speed/mod.rs

**Files:**
- Modify: `src/speed/mod.rs` (the `#[cfg(test)]` block at the bottom)

The tests switch from in-memory SQLite to `#[sqlx::test]` which spins up a real Postgres database per test, runs migrations, then cleans up.

- [ ] **Step 1: Remove the test_db() helper and SqlitePoolOptions import**

In the `#[cfg(test)]` block, remove:
```rust
    use sqlx::sqlite::SqlitePoolOptions;
```
and remove the entire `test_db()` function:
```rust
    async fn test_db() -> Database {
        let pool = SqlitePoolOptions::new()
            .max_connections(1)
            .connect("sqlite::memory:")
            .await
            .unwrap();
        let db = Database { pool };
        db.migrate().await.unwrap();
        db
    }
```

Add this import at the top of the test module:
```rust
    use sqlx::PgPool;
```

- [ ] **Step 2: Update the compute_route_speed test**

Change the test signature and body from using `test_db()` to receiving a `PgPool`:
```rust
    #[sqlx::test]
    async fn compute_route_speed_stores_result_for_simple_route(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S1', 1, '08:00:00', '08:00:00')"
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S2', 2, '08:10:00', '08:10:00')"
        ).execute(&db.pool).await.unwrap();

        compute_route_speed(&db, &test_agency()).await.unwrap();

        let row: (f64, i64) = sqlx::query_as(
            "SELECT scheduled_speed_mps, trip_count FROM route_speed WHERE route_id = 'R1' AND direction_id = 0"
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();

        let (speed, count) = row;
        assert!((speed - 1.852).abs() < 0.05, "expected ~1.852 m/s, got {speed}");
        assert_eq!(count, 1);
    }
```

Note: `sqlx::test` runs migrations automatically, so the `Database { pool }` has a complete schema.

- [ ] **Step 3: Update route_speed_summary_returns_route_names**

```rust
    #[sqlx::test]
    async fn route_speed_summary_returns_route_names(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '42', 'The Answer', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed VALUES ('test', 'R1', 0, 10.0, 5, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert_eq!(summary[0].short_name, "42");
        assert_eq!(summary[0].long_name, "The Answer");
        assert_eq!(summary[0].direction_id, 0);
        assert_eq!(summary[0].scheduled_speed_mps, 10.0);
        assert_eq!(summary[0].trip_count, 5);
    }
```

- [ ] **Step 4: Update route_speed_summary_includes_live_speed_from_recent_vehicles**

```rust
    #[sqlx::test]
    async fn route_speed_summary_includes_live_speed_from_recent_vehicles(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '10', 'Route 10', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed VALUES ('test', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        // Vehicle active now with speed 15 m/s.
        sqlx::query(
            "INSERT INTO vehicle_positions
             (agency_id, observed_at, trip_id, latitude, longitude, speed)
             VALUES ('test', NOW()::TEXT, 'T1', 45.5, -73.5, 15.0)"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        let live = summary[0].live_speed_mps.expect("expected live speed");
        assert!((live - 15.0).abs() < 0.01, "expected 15.0 m/s, got {live}");
    }
```

- [ ] **Step 5: Update route_speed_summary_live_speed_is_none_when_no_recent_vehicles**

```rust
    #[sqlx::test]
    async fn route_speed_summary_live_speed_is_none_when_no_recent_vehicles(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '10', 'Route 10', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed VALUES ('test', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        // Vehicle last seen 2 hours ago — outside the 1-hour window.
        sqlx::query(
            "INSERT INTO vehicle_positions
             (agency_id, observed_at, trip_id, latitude, longitude, speed)
             VALUES ('test', (NOW() - INTERVAL '2 hours')::TEXT, 'T1', 45.5, -73.5, 15.0)"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert!(summary[0].live_speed_mps.is_none(), "expected None for stale vehicle");
    }
```

- [ ] **Step 6: Update compute_route_speed_daily_stores_actual_speed**

```rust
    #[sqlx::test]
    async fn compute_route_speed_daily_stores_actual_speed(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S1', 1, '08:00:00', '08:00:00')"
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S2', 2, '08:10:00', '08:10:00')"
        ).execute(&db.pool).await.unwrap();

        let t_s1: i64 = 1767225600;
        let t_s2: i64 = t_s1 + 900;
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
             VALUES ('test', '2026-01-01T08:00:00Z', 'T1', 'S1', 1, $1)"
        ).bind(t_s1).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
             VALUES ('test', '2026-01-01T08:15:00Z', 'T1', 'S2', 2, $1)"
        ).bind(t_s2).execute(&db.pool).await.unwrap();

        let date = chrono::NaiveDate::from_ymd_opt(2026, 1, 1).unwrap();
        compute_route_speed_daily(&db, &test_agency(), date).await.unwrap();

        let row: (f64, i64) = sqlx::query_as(
            "SELECT actual_speed_mps, trip_count
             FROM route_speed_daily
             WHERE route_id = 'R1' AND service_date = '2026-01-01' AND direction_id = 0"
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();

        let (speed, count) = row;
        assert!((speed - 1.235).abs() < 0.05, "expected ~1.235 m/s, got {speed}");
        assert_eq!(count, 1);
    }
```

- [ ] **Step 7: Update remaining sqlx::test tests**

For `route_speed_summary_includes_actual_speed_from_history`:
```rust
    #[sqlx::test]
    async fn route_speed_summary_includes_actual_speed_from_history(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '99', 'Route 99', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed VALUES ('test', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('test', 'R1', (CURRENT_DATE - 1)::TEXT, 0, 6.5, 10, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        let actual = summary[0].actual_speed_mps.expect("expected actual speed");
        assert!((actual - 6.5).abs() < 0.01, "expected 6.5 m/s, got {actual}");
    }
```

For `route_speed_summary_actual_speed_is_none_when_no_history`:
```rust
    #[sqlx::test]
    async fn route_speed_summary_actual_speed_is_none_when_no_history(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '99', 'Route 99', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed VALUES ('test', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert!(summary[0].actual_speed_mps.is_none(), "expected None without history");
    }
```

For `route_speed_summary_filters_by_agency`:
```rust
    #[sqlx::test]
    async fn route_speed_summary_filters_by_agency(pool: PgPool) {
        let db = Database { pool };
        sqlx::query("INSERT INTO routes VALUES ('stm', 'R1', '15', 'Papineau', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO routes VALUES ('rtl', 'R2', '10', 'Longueuil', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ('stm', 'R1', 0, 5.5, 10, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ('rtl', 'R2', 0, 6.0, 8, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let all = route_speed_summary(&db, None).await.unwrap();
        assert_eq!(all.len(), 2);

        let stm = route_speed_summary(&db, Some("stm")).await.unwrap();
        assert_eq!(stm.len(), 1);
        assert_eq!(stm[0].agency_id, "stm");

        let rtl = route_speed_summary(&db, Some("rtl")).await.unwrap();
        assert_eq!(rtl.len(), 1);
        assert_eq!(rtl[0].agency_id, "rtl");
    }
```

- [ ] **Step 8: Run all tests**

```bash
cargo test 2>&1 | tail -30
```

Expected: all tests pass. The `#[sqlx::test]` tests will automatically connect to the Postgres instance (from `DATABASE_URL`), create a temporary database, run migrations, execute the test, and drop the database.

---

## Task 10: Regenerate the .sqlx offline query cache

**Files:**
- Delete: `.sqlx/query-*.json` (all 11 existing files)
- Create: new `.sqlx/query-*.json` files via `cargo sqlx prepare`

This is required because the Dockerfile uses `SQLX_OFFLINE=true` to avoid needing a live database during the Docker build.

- [ ] **Step 1: Install sqlx-cli if not already installed**

```bash
cargo install sqlx-cli --no-default-features --features postgres 2>&1 | tail -5
```

Expected: `sqlx-cli` installed (or already up-to-date).

- [ ] **Step 2: Delete the old SQLite cache files**

```bash
rm /Users/alex/src/mobilispect/.sqlx/query-*.json
```

- [ ] **Step 3: Regenerate with Postgres**

With `DATABASE_URL` and `SQLX_OFFLINE=false` set (from Task 1 Step 2):

```bash
cargo sqlx prepare
```

Expected: new `.sqlx/query-*.json` files created. The command should complete without errors.

- [ ] **Step 4: Verify the cache is valid**

```bash
SQLX_OFFLINE=true cargo check
```

Expected: compiles successfully without a database connection.

---

## Task 11: Update the Dockerfile

**Files:**
- Modify: `Dockerfile`

Remove the SQLite volume comment, update the default `DATABASE_URL`.

- [ ] **Step 1: Update the Dockerfile**

Change:
```dockerfile
# /data is used for the SQLite database file. Mount a Railway volume at /data
# via the Railway dashboard — do not use the VOLUME directive (banned by Railway).

# Default to an absolute path inside the /data volume so the SQLite file
# survives container restarts when a Railway volume is mounted there.
ENV DATABASE_URL=sqlite:///data/mobilispect.db
```
to:
```dockerfile
# DATABASE_URL must be set at runtime to a Postgres connection string.
# On Railway, reference the Postgres plugin: postgres://$PGUSER:$PGPASSWORD@$PGHOST:$PGPORT/$PGDATABASE
ENV DATABASE_URL=""
```

Also remove the `RUN mkdir -p /data` line (no longer needed for SQLite).

- [ ] **Step 2: Verify Docker build**

```bash
docker build -t mobilispect:test .
```

Expected: build succeeds. The binary is built with `SQLX_OFFLINE=true` using the new Postgres cache.

---

## Task 12: Final smoke test

- [ ] **Step 1: Run the full test suite one more time**

```bash
cargo test 2>&1 | tail -20
```

Expected: all tests pass, no failures.

- [ ] **Step 2: Run the app locally against Postgres**

```bash
GTFS_STATIC_URL=... GTFS_RT_VEHICLE_POSITIONS_URL=... GTFS_RT_TRIP_UPDATES_URL=... cargo run
```

Expected: app starts, runs migrations, loads GTFS. No SQLite-related panics or errors.

- [ ] **Step 3: Commit**

```bash
git add Cargo.toml Cargo.lock src/db/mod.rs src/gtfs/static_feed.rs src/gtfs/realtime.rs \
        src/maintenance/mod.rs src/metrics/mod.rs src/speed/mod.rs \
        migrations/ .sqlx/ Dockerfile
git commit -m "feat: migrate from SQLite to Postgres

Replaces SQLite with Postgres to enable splitting the HTTP server and
background worker into separate Railway services.

- Switch sqlx driver: sqlite → postgres
- Rewrite all migrations as a single Postgres-compatible schema
- Replace SQLite functions: datetime(), strftime(), printf(), PRAGMA
- Replace INSERT OR REPLACE with ON CONFLICT ... DO UPDATE
- Switch ? placeholders to $N numbered parameters
- Update tests to use #[sqlx::test] with a real Postgres instance
- Regenerate .sqlx offline query cache for Docker builds"
```
# Dwell Time Recording Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `dwell_secs` generated column to `stop_time_events` that automatically records the seconds a vehicle dwells at each stop.

**Architecture:** A single SQL migration adds `dwell_secs BIGINT GENERATED ALWAYS AS (departure_time_unix - arrival_time_unix) STORED` to `stop_time_events`. Postgres computes and stores the value whenever a row is inserted or updated — no Rust code changes required. The column is NULL when either timestamp is NULL, and may be negative if feed data is bad (stored as-is).

**Tech Stack:** PostgreSQL 12+ (generated columns), sqlx 0.8 (migrations), testcontainers + Rust async tests.

---

## File Structure

- **Create:** `migrations/003_dwell_time.sql` — SQL migration adding the generated column
- **Modify:** `src/gtfs/realtime.rs` — add `#[cfg(test)]` module with two integration tests

---

### Task 1: Migration + integration tests

**Files:**
- Create: `migrations/003_dwell_time.sql`
- Modify: `src/gtfs/realtime.rs` (append test module)

---

- [ ] **Step 1: Write the failing tests**

Append the following test module to the end of `src/gtfs/realtime.rs`:

```rust
#[cfg(test)]
mod tests {
    use crate::db::test_utils;

    #[tokio::test]
    async fn dwell_secs_computed_from_timestamps() {
        let test_db = test_utils::setup().await;
        let pool = &test_db.db.pool;

        sqlx::query!(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id,
              arrival_time_unix, departure_time_unix)
             VALUES ($1, $2, $3, $4, $5, $6)",
            "agency-1",
            "2026-04-19T10:00:00Z",
            "trip-1",
            "stop-1",
            1000_i64,
            1045_i64,
        )
        .execute(pool)
        .await
        .unwrap();

        let row = sqlx::query!(
            "SELECT dwell_secs FROM stop_time_events WHERE trip_id = $1",
            "trip-1"
        )
        .fetch_one(pool)
        .await
        .unwrap();

        assert_eq!(row.dwell_secs, Some(45));
    }

    #[tokio::test]
    async fn dwell_secs_is_null_when_arrival_missing() {
        let test_db = test_utils::setup().await;
        let pool = &test_db.db.pool;

        sqlx::query!(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id,
              arrival_time_unix, departure_time_unix)
             VALUES ($1, $2, $3, $4, $5, $6)",
            "agency-2",
            "2026-04-19T10:00:00Z",
            "trip-2",
            "stop-2",
            None::<i64>,
            1045_i64,
        )
        .execute(pool)
        .await
        .unwrap();

        let row = sqlx::query!(
            "SELECT dwell_secs FROM stop_time_events WHERE trip_id = $1",
            "trip-2"
        )
        .fetch_one(pool)
        .await
        .unwrap();

        assert_eq!(row.dwell_secs, None);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test dwell_secs
```

Expected: compile error or test failure — `dwell_secs` column does not exist yet.

- [ ] **Step 3: Create the migration**

Create `migrations/003_dwell_time.sql` with this exact content:

```sql
-- migrations/003_dwell_time.sql

ALTER TABLE stop_time_events
  ADD COLUMN dwell_secs BIGINT
    GENERATED ALWAYS AS (departure_time_unix - arrival_time_unix) STORED;
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cargo test dwell_secs
```

Expected output (both tests pass):

```
test gtfs::realtime::tests::dwell_secs_computed_from_timestamps ... ok
test gtfs::realtime::tests::dwell_secs_is_null_when_arrival_missing ... ok
```

If you see a sqlx compile-time query error about `dwell_secs` not being in the offline query cache, run:

```bash
DATABASE_URL=postgres://postgres:postgres@127.0.0.1:5432/postgres cargo sqlx prepare
```

(The testcontainers-based tests don't need this — only if the project uses `sqlx::query!` macros that are compiled against a live DB snapshot. Check whether `SQLX_OFFLINE=true` is set in `.cargo/config.toml` or the environment.)

- [ ] **Step 5: Run full test suite to check for regressions**

```bash
cargo test
```

Expected: all tests pass, no regressions.

- [ ] **Step 6: Commit**

```bash
git add migrations/003_dwell_time.sql src/gtfs/realtime.rs
git commit -m "feat: record dwell_secs as generated column on stop_time_events"
```

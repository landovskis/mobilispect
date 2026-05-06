# Dwell Time Recording — Design Spec

**Date:** 2026-04-19

## Goal

Record the actual dwell time (seconds a vehicle spends at each stop) for every individual trip-stop occurrence, so downstream queries can analyse boarding/alighting delays and stop-level performance.

## Background

`stop_time_events` already stores `arrival_time_unix` and `departure_time_unix` as BIGINT Unix epoch seconds, populated at ingestion time from GTFS-RT TripUpdate `StopTimeEvent` messages. Dwell time is the difference between those two values and is therefore fully derivable from data already present in the table.

## Approach

Add a PostgreSQL generated column:

```sql
ALTER TABLE stop_time_events
  ADD COLUMN dwell_secs BIGINT
    GENERATED ALWAYS AS (departure_time_unix - arrival_time_unix) STORED;
```

### Why a generated column

- Zero Rust code changes — no modifications to `store_trip_updates` or any query
- Always consistent: Postgres recomputes the value from the source columns, so it can never be out of sync
- Immediately queryable like any regular column
- Populated for all existing rows when the migration runs

### Null handling

When either `arrival_time_unix` or `departure_time_unix` is NULL, `dwell_secs` is NULL (standard Postgres null arithmetic). This is correct: if we don't know one of the timestamps we cannot compute dwell.

### Negative values

If the GTFS-RT feed contains bad data where `departure_time_unix < arrival_time_unix`, `dwell_secs` will be negative. We store it faithfully and leave filtering to query authors.

## Schema

**File:** `migrations/002_dwell_time.sql`

```sql
ALTER TABLE stop_time_events
  ADD COLUMN dwell_secs BIGINT
    GENERATED ALWAYS AS (departure_time_unix - arrival_time_unix) STORED;
```

No other files are created or modified.

## Testing

Integration test using testcontainers + real Postgres:

1. Insert a `stop_time_events` row with `arrival_time_unix = 1000` and `departure_time_unix = 1045` → assert `dwell_secs = 45`
2. Insert a row with `arrival_time_unix = NULL` → assert `dwell_secs IS NULL`

Test lives in `src/gtfs/realtime.rs` (alongside existing ingestion tests) or a new `tests/dwell_time.rs` integration test file.

## Out of Scope

- No new HTTP endpoints or UI changes
- No scheduled dwell time (that would require parsing GTFS static `arrival_time`/`departure_time` text fields separately)
- No aggregation queries (those are future work)

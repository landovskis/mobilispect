# First-Launch Region Setup

**Date:** 2026-06-03  
**Status:** Approved

## Summary

On first launch (empty DB), every request redirects to a `/setup` page where the operator enters a city name. The server queries Transitland to discover all GTFS feeds for that city, stores them in the DB, and redirects to the main dashboard. Region and feeds are no longer defined in `config.toml`; they live exclusively in the database.

## Domain Context

- **Bounded context(s):** Configuration / Feed Management
- **Aggregates touched:** Region, Feed (canonical entity tables from migration 011)
- **New ubiquitous language terms:** *discovered feed* — a feed found via Transitland city-name search and stored in the DB, as opposed to a manually configured feed

## Architecture

### What changes

| Component | Change |
|-----------|--------|
| `config.toml` | Remove `[region]` and all `[[region.networks.*]]` blocks |
| `Config` struct | Drop `region: RegionConfig` and `feeds: Vec<FeedConfig>`; remove `RegionConfig`, `NetworkConfig`, `FeedConfig` types from `crates/core/src/config.rs` |
| `AppState` (server) | Add `region: Arc<RwLock<Option<String>>>` and `setup_state: Arc<Mutex<SetupState>>` |
| Axum router | Add `from_fn` middleware on all non-setup routes; add `GET /setup`, `POST /setup`, `GET /setup/status` |
| `TransitlandClient` | Move from `crates/worker` to `crates/core`; add `discover_feeds_for_city(city: &str) -> Result<Vec<DiscoveredFeed>>` |
| Worker main loop | Replace `config.feeds` iteration with `load_feeds(pool)` DB query each tick |
| Migration 016 | Add `transitland_onestop_id TEXT UNIQUE` and `name TEXT` columns to `feeds` |
| All page handlers | Replace `state.config.region.name` reads with `state.region.read().await` |

### What stays the same

`config.toml` continues to hold: `database_url`, `bind_address`, `poll_interval_secs`, `on_time_early/late_threshold_secs`, `retention_days`, `worker_health_bind_address`, `transitland_api_key`.

## Setup Flow

### Guard middleware

A `tower::from_fn` layer wraps all routes except `/setup` and `/setup/status`. It reads `state.region` — if `None`, it returns a `303 See Other` redirect to `/setup`. Once setup completes the field is set to `Some(city_name)` and all subsequent requests pass through without a DB query.

### GET `/setup`

Returns a plain HTML form (no state required):
- Text input: city name
- Submit button: "Find feeds"

### POST `/setup` (form body: `city_name`)

1. If `setup_state` is already `Running`, return the polling page without spawning a second task.
2. Spawn a `tokio::task`:
   a. Call `TransitlandClient::discover_feeds_for_city(city)`.
   b. Open a DB transaction; insert into `regions` (id=1, name=city, timezone from first operator), `networks` (id=1, region_id=1, name=city), `feeds` (sequential IDs), `network_feeds`.
   c. On success, write `Some(city)` to `state.region` and mark `setup_state` as `Done(Ok(city))`.
   d. On failure, mark `setup_state` as `Done(Err(message))`.
3. Set `setup_state` to `Running(handle)`.
4. Return the polling page immediately (htmx polls `/setup/status` every 1 s).

### `SetupState` (shared state for in-flight task)

```rust
pub enum SetupState {
    Idle,
    Running(tokio::task::JoinHandle<Result<String>>),  // String = city name
    Done(Result<String, String>),                       // Ok(city) or Err(message)
}
```

Wrapped in `Arc<Mutex<SetupState>>` in `AppState`.

### GET `/setup/status`

Inspects `setup_state`:

| State | Response |
|-------|----------|
| `Running` (task still live) | Return "Searching…" htmx fragment; htmx re-polls in 1 s |
| `Done(Ok(_))` | Respond with `HX-Redirect: /`; htmx navigates browser to dashboard |
| `Done(Err(msg))` | Return error fragment with city pre-filled; reset state to `Idle` |

## Transitland Feed Discovery

`discover_feeds_for_city(city: &str) -> Result<Vec<DiscoveredFeed>>`:

1. `GET /operators.json?city_name={city}&per_page=50` — collect operator records. Each record contains one or more feed onestop IDs and a `timezone`.
2. Deduplicate feed onestop IDs (multiple operators can share a feed).
3. For each unique feed ID: `GET /feeds.json?onestop_id={id}&per_page=1` — extract `urls.static_current`, `urls.realtime_vehicle_positions`, `urls.realtime_trip_updates`.
4. Skip any feed missing `urls.static_current`.
5. Return assembled `Vec<DiscoveredFeed>`.

```rust
pub struct DiscoveredFeed {
    pub onestop_id: String,
    pub name: String,
    pub gtfs_static_url: String,
    pub gtfs_rt_vehicle_positions_url: Option<String>,
    pub gtfs_rt_trip_updates_url: Option<String>,
    pub timezone: String,
}
```

## Worker Changes

### `crates/core/src/db/feeds.rs` — new function

```rust
pub async fn load_feeds(pool: &PgPool) -> Result<Vec<DbFeed>>
```

Queries:
```sql
SELECT id, name, gtfs_static_url,
       gtfs_rt_vehicle_positions_url, gtfs_rt_trip_updates_url,
       transitland_onestop_id
FROM feeds
```

`DbFeed` is the runtime equivalent of the removed `FeedConfig`:

```rust
pub struct DbFeed {
    pub id: i64,
    pub name: Option<String>,
    pub gtfs_static_url: String,
    pub gtfs_rt_vehicle_positions_url: Option<String>,
    pub gtfs_rt_trip_updates_url: Option<String>,
    pub transitland_onestop_id: Option<String>,
}
```

### Worker poll loop

```
loop:
  feeds = load_feeds(pool).await
  if feeds.is_empty():
    log "No feeds configured yet — waiting for setup"
    sleep(poll_interval)
    continue
  for feed in feeds:
    ingest(feed)
  sleep(poll_interval)
```

## Error Handling

| Scenario | Behaviour |
|----------|-----------|
| Transitland returns zero operators for city | Task fails with "No feeds found for '{city}'" message; setup status shows error with form pre-filled |
| Transitland HTTP error (network, 5xx) | Task fails; error message surfaced in setup UI |
| DB transaction fails mid-write | Transaction rolls back; `region` stays `None`; error shown; user can retry |
| Worker `load_feeds` query fails | Log error, sleep, retry next tick |
| Setup POST while task already running | Return polling page without spawning second task |

## Migration 016

```sql
ALTER TABLE feeds
    ADD COLUMN name TEXT,
    ADD COLUMN transitland_onestop_id TEXT UNIQUE;
```

The `name` column is nullable (existing rows have no name). The `transitland_onestop_id` unique constraint prevents duplicate feed discovery on retry.

## Testing

| Test | Type | Covers |
|------|------|--------|
| `discover_feeds_for_city` — city with multiple feeds | Unit (wiremock) | Happy path; deduplication |
| `discover_feeds_for_city` — city with no feeds | Unit (wiremock) | Returns empty vec |
| `discover_feeds_for_city` — feed missing static URL | Unit (wiremock) | Feed is skipped |
| `discover_feeds_for_city` — HTTP error | Unit (wiremock) | Error propagates |
| Setup POST happy path | Integration (testcontainers) | `regions`/`feeds` tables populated; `state.region` is `Some` |
| Middleware guard — no region | Unit | Redirects to `/setup` |
| Middleware guard — region set | Unit | Request passes through |
| `load_feeds` — empty table | Integration (testcontainers) | Returns empty vec |
| `load_feeds` — populated table | Integration (testcontainers) | Returns correct `DbFeed` rows |
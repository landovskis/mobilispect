# First-Launch Region Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On first launch (empty DB), redirect all requests to `/setup`, let the operator enter a city name, discover GTFS feeds via Transitland, store them in the DB, then redirect to the dashboard — eliminating `[region]` from `config.toml` entirely.

**Architecture:** An Axum `from_fn_with_state` middleware guard redirects to `/setup` when `AppState.region` is `None`. `POST /setup` spawns a Tokio task that calls `TransitlandClient::discover_feeds_for_city`, writes to the DB, and signals completion via shared `SetupState`. `GET /setup/status` uses htmx polling to check progress and issues `HX-Redirect: /` on success. The worker replaces `config.feeds` iteration with a `load_feeds(&pool)` DB query each tick.

**Tech Stack:** Rust 2024, Axum 0.7, sqlx 0.8 (`query!`/`query_as!`), Askama templates, htmx 2.0.10, reqwest 0.12, wiremock 0.6 (tests), testcontainers (integration tests).

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `crates/core/migrations/016_feeds_discovery_columns.sql` | Create | Add `name`, `transitland_onestop_id`, `gtfs_api_key`, `timezone` to `feeds` |
| `crates/core/src/transitland/mod.rs` | Create | Move from worker; add `discover_feeds_for_city` |
| `crates/core/src/lib.rs` | Modify | Expose `pub mod transitland` |
| `crates/core/Cargo.toml` | Modify | Add `reqwest` dep; add `wiremock` dev-dep |
| `crates/worker/src/transitland/mod.rs` | Delete | Re-exported from core now |
| `crates/worker/src/main.rs` | Modify | Update import; replace `config.feeds` with `load_feeds` |
| `crates/worker/src/feed_ingestion/static_feed.rs` | Modify | Update import |
| `crates/worker/src/feed_ingestion/realtime.rs` | Modify | Update import |
| `crates/worker/src/pipeline.rs` | Modify | Update import; update test helper |
| `crates/worker/src/maintenance/mod.rs` | Modify | Update import; accept `&[FeedConfig]` + `retention_days` directly |
| `crates/core/src/db/feeds.rs` | Create | `DbFeed`, `load_feeds`, `store_discovered_feeds` |
| `crates/core/src/db/mod.rs` | Modify | Expose `pub mod feeds` |
| `crates/core/src/config.rs` | Modify | Remove region TOML types + `region`/`feeds` from `Config`; keep `FeedConfig`; add `From<DbFeed> for FeedConfig` |
| `crates/server/src/web/mod.rs` | Modify | `AppState` + `SetupState`; init `region` from DB; updated router |
| `crates/server/src/web/middleware.rs` | Create | `require_region_configured` |
| `crates/server/src/web/handlers.rs` | Modify | Setup handlers; update `region_name` reads |
| `crates/server/templates/pages/setup.html` | Create | Setup form page |
| `crates/server/templates/pages/setup_progress.html` | Create | Searching + error htmx page |
| `config.toml` | Modify | Remove `[region]` section |

---

## Task 1: Migration 016 — Feeds Discovery Columns

**Files:**
- Create: `crates/core/migrations/016_feeds_discovery_columns.sql`

- [ ] **Step 1: Write the migration**

```sql
-- migrations/016_feeds_discovery_columns.sql
-- Add columns needed for Transitland-discovered feeds.
-- name: human-readable feed name
-- transitland_onestop_id: unique Transitland feed ID (prevents duplicate discovery)
-- gtfs_api_key: optional GTFS-RT authentication key (configured post-setup)
-- timezone: IANA timezone (e.g. "America/Toronto"), replaces per-feed agency_utc_offset

ALTER TABLE feeds
    ADD COLUMN name                  TEXT,
    ADD COLUMN transitland_onestop_id TEXT UNIQUE,
    ADD COLUMN gtfs_api_key           TEXT,
    ADD COLUMN timezone               TEXT NOT NULL DEFAULT 'UTC';
```

- [ ] **Step 2: Verify it compiles (sqlx checks schema at compile time)**

```bash
cargo build
```

Expected: no errors. sqlx offline cache may need refreshing if compile-time queries fail — run `cargo sqlx prepare` from the workspace root.

- [ ] **Step 3: Commit**

```bash
git add crates/core/migrations/016_feeds_discovery_columns.sql
git commit -m "feat(db): add name, transitland_onestop_id, gtfs_api_key, timezone to feeds"
```

---

## Task 2: Move TransitlandClient to `crates/core`

The server's setup handler needs `TransitlandClient`. Moving it to core makes it available to both crates without circular dependencies.

**Files:**
- Modify: `crates/core/Cargo.toml`
- Create: `crates/core/src/transitland/mod.rs`
- Modify: `crates/core/src/lib.rs`
- Delete: `crates/worker/src/transitland/mod.rs`
- Modify: `crates/worker/src/main.rs` (import line only)
- Modify: `crates/worker/src/feed_ingestion/static_feed.rs` (import line only)

- [ ] **Step 1: Add reqwest + wiremock to core**

In `crates/core/Cargo.toml`, add under `[dependencies]`:

```toml
reqwest = { version = "0.12", features = ["json"] }
```

And under `[dev-dependencies]`:

```toml
wiremock = "0.6"
```

- [ ] **Step 2: Create `crates/core/src/transitland/mod.rs`**

Copy the entire contents of `crates/worker/src/transitland/mod.rs` verbatim into `crates/core/src/transitland/mod.rs`. The module uses only `crate::ids::*` which exists in core.

- [ ] **Step 3: Expose the module from core**

In `crates/core/src/lib.rs`, add:

```rust
pub mod transitland;
```

- [ ] **Step 4: Delete the worker's transitland module**

```bash
rm crates/worker/src/transitland/mod.rs
rmdir crates/worker/src/transitland
```

- [ ] **Step 5: Update worker imports**

In `crates/worker/src/main.rs`, remove `pub mod transitland;` and change:
```rust
use mobilispect_core::transitland::TransitlandClient;
```
(remove the local `transitland` module declaration and update the `let transitland = ...` line to use the core import directly — the call site `transitland::TransitlandClient::new(...)` becomes `TransitlandClient::new(...)`).

In `crates/worker/src/feed_ingestion/static_feed.rs`, change:
```rust
use crate::transitland::TransitlandClient;
```
to:
```rust
use mobilispect_core::transitland::TransitlandClient;
```

- [ ] **Step 6: Verify it builds and tests pass**

```bash
cargo build
cargo nextest run
```

Expected: green. Existing wiremock tests for `resolve_agency`, `resolve_route`, `resolve_stop` now live in core and still pass.

- [ ] **Step 7: Commit**

```bash
git add crates/core/Cargo.toml crates/core/src/transitland/ crates/core/src/lib.rs \
        crates/worker/src/main.rs crates/worker/src/feed_ingestion/static_feed.rs
git commit -m "refactor(transitland): move TransitlandClient from worker to core"
```

---

## Task 3: Add `discover_feeds_for_city` to TransitlandClient

**Files:**
- Modify: `crates/core/src/transitland/mod.rs`

The method makes two calls to Transitland v2 REST:
1. `GET /operators.json?city_name={city}&per_page=50` → operator records with feed onestop IDs and timezone
2. For each unique feed ID: `GET /feeds.json?onestop_id={id}&per_page=1` → GTFS URLs

- [ ] **Step 1: Write four failing tests**

Append to the `#[cfg(test)]` block in `crates/core/src/transitland/mod.rs`:

```rust
// ── discover_feeds_for_city ───────────────────────────────────────────────────

#[tokio::test]
async fn discover_feeds_returns_feeds_for_known_city() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/operators.json"))
        .and(query_param("city_name", "Montreal"))
        .and(query_param("per_page", "50"))
        .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
            "operators": [{
                "name": "STM",
                "timezone": "America/Toronto",
                "feeds": [{"onestop_id": "f-f25d-stm"}]
            }]
        })))
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(path("/feeds.json"))
        .and(query_param("onestop_id", "f-f25d-stm"))
        .and(query_param("per_page", "1"))
        .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
            "feeds": [{
                "onestop_id": "f-f25d-stm",
                "name": "STM GTFS",
                "urls": {
                    "static_current": "https://stm.info/gtfs.zip",
                    "realtime_vehicle_positions": "https://stm.info/vp.pb",
                    "realtime_trip_updates": "https://stm.info/tu.pb"
                }
            }]
        })))
        .mount(&server)
        .await;

    let client = client_for(&server);
    let feeds = client.discover_feeds_for_city("Montreal").await.unwrap();
    assert_eq!(feeds.len(), 1);
    assert_eq!(feeds[0].onestop_id, "f-f25d-stm");
    assert_eq!(feeds[0].gtfs_static_url, "https://stm.info/gtfs.zip");
    assert_eq!(feeds[0].timezone, "America/Toronto");
    assert_eq!(feeds[0].gtfs_rt_vehicle_positions_url.as_deref(), Some("https://stm.info/vp.pb"));
}

#[tokio::test]
async fn discover_feeds_returns_empty_for_unknown_city() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/operators.json"))
        .and(query_param("city_name", "Nowhere"))
        .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
            "operators": []
        })))
        .mount(&server)
        .await;

    let client = client_for(&server);
    let feeds = client.discover_feeds_for_city("Nowhere").await.unwrap();
    assert!(feeds.is_empty());
}

#[tokio::test]
async fn discover_feeds_skips_feed_with_no_static_url() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/operators.json"))
        .and(query_param("city_name", "TestCity"))
        .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
            "operators": [{
                "name": "Op",
                "timezone": "UTC",
                "feeds": [{"onestop_id": "f-abc-op"}]
            }]
        })))
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(path("/feeds.json"))
        .and(query_param("onestop_id", "f-abc-op"))
        .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
            "feeds": [{"onestop_id": "f-abc-op", "name": "Op", "urls": {}}]
        })))
        .mount(&server)
        .await;

    let client = client_for(&server);
    let feeds = client.discover_feeds_for_city("TestCity").await.unwrap();
    assert!(feeds.is_empty());
}

#[tokio::test]
async fn discover_feeds_propagates_http_error() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/operators.json"))
        .respond_with(ResponseTemplate::new(500))
        .mount(&server)
        .await;

    let client = client_for(&server);
    let result = client.discover_feeds_for_city("Montreal").await;
    assert!(result.is_err());
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo nextest run discover_feeds
```

Expected: FAIL — method `discover_feeds_for_city` not found.

- [ ] **Step 3: Add `DiscoveredFeed` struct and serde response types**

In `crates/core/src/transitland/mod.rs`, before `impl TransitlandClient`, add:

```rust
/// A GTFS feed discovered via the Transitland city-name search.
pub struct DiscoveredFeed {
    pub onestop_id: String,
    pub name: String,
    pub gtfs_static_url: String,
    pub gtfs_rt_vehicle_positions_url: Option<String>,
    pub gtfs_rt_trip_updates_url: Option<String>,
    pub timezone: String,
}

#[derive(serde::Deserialize)]
struct OperatorsResponse {
    operators: Vec<OperatorRecord>,
}

#[derive(serde::Deserialize)]
struct OperatorRecord {
    name: String,
    timezone: Option<String>,
    feeds: Vec<FeedRef>,
}

#[derive(serde::Deserialize)]
struct FeedRef {
    onestop_id: String,
}

#[derive(serde::Deserialize)]
struct FeedsResponse {
    feeds: Vec<FeedRecord>,
}

#[derive(serde::Deserialize)]
struct FeedRecord {
    onestop_id: String,
    name: Option<String>,
    urls: Option<FeedUrls>,
}

#[derive(serde::Deserialize)]
struct FeedUrls {
    static_current: Option<String>,
    realtime_vehicle_positions: Option<String>,
    realtime_trip_updates: Option<String>,
}
```

- [ ] **Step 4: Implement `discover_feeds_for_city`**

Inside `impl TransitlandClient`, add:

```rust
/// Search Transitland for all GTFS feeds serving `city`.
/// Returns an empty vec if no operators are found.
/// Feeds missing a static GTFS URL are skipped.
pub async fn discover_feeds_for_city(&self, city: &str) -> anyhow::Result<Vec<DiscoveredFeed>> {
    // Step 1: find operators in city
    let url = format!("{}/operators.json", self.base_url);
    let request = self
        .http
        .get(&url)
        .query(&[("city_name", city), ("per_page", "50")]);
    let request = self.apply_auth(request);
    let body: OperatorsResponse = request.send().await?.error_for_status()?.json().await?;

    // Collect unique feed onestop IDs with their operator timezone
    let mut feed_ids: Vec<(String, String)> = Vec::new(); // (onestop_id, timezone)
    let mut seen = std::collections::HashSet::new();
    for op in &body.operators {
        let tz = op.timezone.clone().unwrap_or_else(|| "UTC".to_string());
        for f in &op.feeds {
            if seen.insert(f.onestop_id.clone()) {
                feed_ids.push((f.onestop_id.clone(), tz.clone()));
            }
        }
    }

    // Step 2: fetch URLs for each unique feed
    let mut discovered = Vec::new();
    for (feed_id, timezone) in feed_ids {
        let url = format!("{}/feeds.json", self.base_url);
        let request = self
            .http
            .get(&url)
            .query(&[("onestop_id", &feed_id), ("per_page", "1")]);
        let request = self.apply_auth(request);
        let body: FeedsResponse = request.send().await?.error_for_status()?.json().await?;
        if let Some(record) = body.feeds.into_iter().next() {
            let urls = record.urls.unwrap_or(FeedUrls {
                static_current: None,
                realtime_vehicle_positions: None,
                realtime_trip_updates: None,
            });
            if let Some(static_url) = urls.static_current {
                discovered.push(DiscoveredFeed {
                    onestop_id: record.onestop_id,
                    name: record.name.unwrap_or_else(|| feed_id.clone()),
                    gtfs_static_url: static_url,
                    gtfs_rt_vehicle_positions_url: urls.realtime_vehicle_positions,
                    gtfs_rt_trip_updates_url: urls.realtime_trip_updates,
                    timezone,
                });
            }
        }
    }
    Ok(discovered)
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cargo nextest run discover_feeds
```

Expected: 4 tests pass.

- [ ] **Step 6: Commit**

```bash
git add crates/core/src/transitland/mod.rs
git commit -m "feat(transitland): add discover_feeds_for_city"
```

---

## Task 4: Add `DbFeed`, `load_feeds`, `store_discovered_feeds`

**Files:**
- Create: `crates/core/src/db/feeds.rs`
- Modify: `crates/core/src/db/mod.rs`

- [ ] **Step 1: Write failing integration tests**

Create `crates/core/src/db/feeds.rs` with tests only:

```rust
use sqlx::PgPool;
use anyhow::Result;

pub struct DbFeed {
    pub id: i64,
    pub name: Option<String>,
    pub gtfs_static_url: String,
    pub gtfs_rt_vehicle_positions_url: Option<String>,
    pub gtfs_rt_trip_updates_url: Option<String>,
    pub transitland_onestop_id: Option<String>,
    pub gtfs_api_key: Option<String>,
    pub timezone: String,
}

pub async fn load_feeds(pool: &PgPool) -> Result<Vec<DbFeed>> {
    todo!()
}

pub async fn store_discovered_feeds(
    pool: &PgPool,
    city: &str,
    feeds: &[crate::transitland::DiscoveredFeed],
) -> Result<()> {
    todo!()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;
    use crate::transitland::DiscoveredFeed;

    #[tokio::test]
    async fn load_feeds_returns_empty_when_table_is_empty() {
        let td = test_utils::setup().await;
        let feeds = load_feeds(&td.db.pool).await.unwrap();
        assert!(feeds.is_empty());
    }

    #[tokio::test]
    async fn load_feeds_returns_stored_feed() {
        let td = test_utils::setup().await;
        sqlx::query!(
            "INSERT INTO feeds (id, gtfs_static_url, name, transitland_onestop_id, timezone)
             VALUES (1, 'https://example.com/gtfs.zip', 'Test Feed', 'f-test', 'America/Toronto')"
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let feeds = load_feeds(&td.db.pool).await.unwrap();
        assert_eq!(feeds.len(), 1);
        assert_eq!(feeds[0].id, 1);
        assert_eq!(feeds[0].gtfs_static_url, "https://example.com/gtfs.zip");
        assert_eq!(feeds[0].name.as_deref(), Some("Test Feed"));
        assert_eq!(feeds[0].timezone, "America/Toronto");
    }

    #[tokio::test]
    async fn store_discovered_feeds_inserts_region_network_and_feeds() {
        let td = test_utils::setup().await;
        let discovered = vec![DiscoveredFeed {
            onestop_id: "f-f25d-stm".to_string(),
            name: "STM".to_string(),
            gtfs_static_url: "https://stm.info/gtfs.zip".to_string(),
            gtfs_rt_vehicle_positions_url: Some("https://stm.info/vp.pb".to_string()),
            gtfs_rt_trip_updates_url: None,
            timezone: "America/Toronto".to_string(),
        }];

        store_discovered_feeds(&td.db.pool, "Montreal", &discovered).await.unwrap();

        let region_count: i64 = sqlx::query_scalar!("SELECT COUNT(*) FROM regions")
            .fetch_one(&td.db.pool)
            .await
            .unwrap()
            .unwrap();
        assert_eq!(region_count, 1);

        let feed = load_feeds(&td.db.pool).await.unwrap();
        assert_eq!(feed.len(), 1);
        assert_eq!(feed[0].transitland_onestop_id.as_deref(), Some("f-f25d-stm"));
        assert_eq!(feed[0].timezone, "America/Toronto");
    }

    #[tokio::test]
    async fn store_discovered_feeds_is_idempotent_on_retry() {
        let td = test_utils::setup().await;
        let discovered = vec![DiscoveredFeed {
            onestop_id: "f-f25d-stm".to_string(),
            name: "STM".to_string(),
            gtfs_static_url: "https://stm.info/gtfs.zip".to_string(),
            gtfs_rt_vehicle_positions_url: None,
            gtfs_rt_trip_updates_url: None,
            timezone: "America/Toronto".to_string(),
        }];

        store_discovered_feeds(&td.db.pool, "Montreal", &discovered).await.unwrap();
        // Calling again should not fail or duplicate
        store_discovered_feeds(&td.db.pool, "Montreal", &discovered).await.unwrap();

        let count: i64 = sqlx::query_scalar!("SELECT COUNT(*) FROM feeds")
            .fetch_one(&td.db.pool)
            .await
            .unwrap()
            .unwrap();
        assert_eq!(count, 1);
    }
}
```

- [ ] **Step 2: Run to verify tests fail**

```bash
cargo nextest run db::feeds
```

Expected: FAIL — `todo!()` panics.

- [ ] **Step 3: Implement `load_feeds`**

Replace the `todo!()` in `load_feeds`:

```rust
pub async fn load_feeds(pool: &PgPool) -> Result<Vec<DbFeed>> {
    Ok(sqlx::query_as!(
        DbFeed,
        r#"SELECT id, name, gtfs_static_url,
                  gtfs_rt_vehicle_positions_url, gtfs_rt_trip_updates_url,
                  transitland_onestop_id, gtfs_api_key, timezone
           FROM feeds"#
    )
    .fetch_all(pool)
    .await?)
}
```

- [ ] **Step 4: Implement `store_discovered_feeds`**

Replace the `todo!()`:

```rust
pub async fn store_discovered_feeds(
    pool: &PgPool,
    city: &str,
    feeds: &[crate::transitland::DiscoveredFeed],
) -> Result<()> {
    let mut tx = pool.begin().await?;

    sqlx::query!(
        "INSERT INTO regions (id, name, timezone)
         VALUES (1, $1, $2)
         ON CONFLICT (id) DO UPDATE SET name = $1, timezone = $2",
        city,
        feeds.first().map(|f| f.timezone.as_str()).unwrap_or("UTC"),
    )
    .execute(&mut *tx)
    .await?;

    sqlx::query!(
        "INSERT INTO networks (id, region_id, name)
         VALUES (1, 1, $1)
         ON CONFLICT (id) DO UPDATE SET name = $1",
        city,
    )
    .execute(&mut *tx)
    .await?;

    let max_id: i64 = sqlx::query_scalar!("SELECT COALESCE(MAX(id), 0) FROM feeds")
        .fetch_one(&mut *tx)
        .await?;

    for (i, feed) in feeds.iter().enumerate() {
        let new_id = max_id + 1 + i as i64;
        let rows = sqlx::query!(
            "INSERT INTO feeds (id, name, gtfs_static_url,
                                gtfs_rt_vehicle_positions_url, gtfs_rt_trip_updates_url,
                                transitland_onestop_id, timezone)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             ON CONFLICT (transitland_onestop_id) DO NOTHING",
            new_id,
            feed.name,
            feed.gtfs_static_url,
            feed.gtfs_rt_vehicle_positions_url,
            feed.gtfs_rt_trip_updates_url,
            feed.onestop_id,
            feed.timezone,
        )
        .execute(&mut *tx)
        .await?;

        let actual_id = if rows.rows_affected() == 0 {
            sqlx::query_scalar!(
                "SELECT id FROM feeds WHERE transitland_onestop_id = $1",
                feed.onestop_id
            )
            .fetch_one(&mut *tx)
            .await?
        } else {
            new_id
        };

        sqlx::query!(
            "INSERT INTO network_feeds (network_id, feed_id) VALUES (1, $1)
             ON CONFLICT DO NOTHING",
            actual_id,
        )
        .execute(&mut *tx)
        .await?;
    }

    tx.commit().await?;
    Ok(())
}
```

- [ ] **Step 5: Expose from `crates/core/src/db/mod.rs`**

Add to the end of `mod.rs`:

```rust
pub mod feeds;
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cargo nextest run db::feeds
```

Expected: 4 tests pass.

- [ ] **Step 7: Commit**

```bash
git add crates/core/src/db/feeds.rs crates/core/src/db/mod.rs
git commit -m "feat(db): add DbFeed, load_feeds, store_discovered_feeds"
```

---

## Task 5: Strip Region Loading from Config; Add `From<DbFeed> for FeedConfig`

**Files:**
- Modify: `crates/core/src/config.rs`

Remove TOML-specific region types and the `region`/`feeds` fields from `Config`. `FeedConfig` and `AgencyConfig` stay — they remain valid domain types used throughout the worker. Add a `From<DbFeed> for FeedConfig` impl so the worker can convert DB rows without changing all function signatures.

- [ ] **Step 1: Remove region types and config fields**

In `crates/core/src/config.rs`:

Delete these types entirely: `RegionConfig`, `NetworkConfig`, `TomlRegionConfig`, `TomlNetworkConfig`, `TomlFeedConfig`.

Remove from `Config`:
- `pub region: RegionConfig`
- `pub feeds: Vec<FeedConfig>`

Remove from `TomlConfig`:
- `region: TomlRegionConfig`

Remove from `Config::from_toml_str_with_env`:
- The entire `let networks = ...` block
- The entire `let feeds = ...` block
- The `region = RegionConfig { ... }` construction
- The `feeds,` and `region,` fields in the `Ok(Self { ... })` expression
- The `if file.region.networks.is_empty() { bail!(...) }` guard

- [ ] **Step 2: Add `From<DbFeed> for FeedConfig`**

At the bottom of `crates/core/src/config.rs`, add:

```rust
impl From<crate::db::feeds::DbFeed> for FeedConfig {
    fn from(f: crate::db::feeds::DbFeed) -> Self {
        Self {
            id: f.id as u32,
            name: f.name.unwrap_or_else(|| f.id.to_string()),
            gtfs_static_url: f.gtfs_static_url,
            gtfs_rt_vehicle_positions_url: f.gtfs_rt_vehicle_positions_url,
            gtfs_rt_trip_updates_url: f.gtfs_rt_trip_updates_url,
            gtfs_api_key: f.gtfs_api_key,
            agency_utc_offset: "UTC".to_string(),
            transitland_feed_id: f.transitland_onestop_id,
        }
    }
}
```

Note: `agency_utc_offset` is set to `"UTC"` as a placeholder. If the field is used for time zone arithmetic it can be derived from `f.timezone` using the `chrono-tz` crate in a follow-up; for now it is retained for signature compatibility.

- [ ] **Step 3: Update config tests**

In the `#[cfg(test)]` block, remove all tests that reference `config.region`, `config.feeds`, `RegionConfig`, or `NetworkConfig` field names. Tests that verify DB URL, poll interval, bind address, and threshold defaults can stay. The `loads_toml_config_and_resolves_dotenvx_secret_env_refs` test may need its region-related assertions removed.

- [ ] **Step 4: Update `config.toml`**

Remove the `[region]` block and all `[[region.networks]]` / `[[region.networks.feeds]]` blocks:

```toml
database_url_env = "MOBILISPECT_DATABASE_URL"
poll_interval_secs = 30
bind_address = "0.0.0.0:3000"
on_time_early_threshold_secs = -60
on_time_late_threshold_secs = 300
retention_days = 30
# transitland_api_key_env = "TRANSITLAND_API_KEY"
```

- [ ] **Step 5: Fix compilation errors**

The server's `handlers.rs` references `state.config.region.name` — it won't compile yet. Temporarily add a placeholder:

In each handler that has `region_name: state.config.region.name.clone()`, change to:
```rust
region_name: String::new(),
```
This is a temporary scaffold; Task 12 replaces these with the real AppState read.

Also fix any other compilation errors from the type removal (e.g. worker files that built `Config` objects in tests — update those test helpers to not include region/feeds).

- [ ] **Step 6: Build and test**

```bash
cargo build
cargo nextest run
```

Expected: compiles and all tests pass.

- [ ] **Step 7: Commit**

```bash
git add crates/core/src/config.rs config.toml
git commit -m "feat(config): remove region/feeds TOML loading; add From<DbFeed> for FeedConfig"
```

---

## Task 6: Update `AppState` with Region and SetupState

**Files:**
- Modify: `crates/server/src/web/mod.rs`

- [ ] **Step 1: Add types and update AppState**

Replace the contents of `crates/server/src/web/mod.rs` with:

```rust
use anyhow::Result;
use axum::{Router, routing::get};
use std::sync::Arc;
use tokio::sync::RwLock;
use tower_http::trace::TraceLayer;
use tracing::info;

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;

mod handlers;
pub mod middleware;

/// State for the in-flight setup background task.
pub enum SetupState {
    Idle,
    Running,
    Done { city: String },
    Failed { message: String },
}

#[derive(Clone)]
pub struct AppState {
    pub db: Database,
    pub config: Config,
    /// The configured region name; `None` until setup completes.
    pub region: Arc<RwLock<Option<String>>>,
    pub setup_state: Arc<tokio::sync::Mutex<SetupState>>,
}

pub fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/", get(handlers::speed_page))
        .route("/speed", get(handlers::speed_page))
        .route("/schedule", get(handlers::frequency_page))
        .route("/frequency", get(handlers::frequency_page))
        .route("/schedule/:feed_id/:route_id", get(handlers::schedule_detail))
        .route("/routes/:agency_id/:route_id/speed", get(handlers::route_speed_detail))
        .route("/routes/:agency_id/:route_id", get(handlers::route_detail))
        .route("/api/routes", get(handlers::api_routes))
        .route("/api/routes/speed", get(handlers::api_route_speed))
        .route("/health", get(handlers::health_check))
        .layer(axum::middleware::from_fn_with_state(
            state.clone(),
            middleware::require_region_configured,
        ))
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

pub async fn serve(db: &Database, config: &Config) -> Result<()> {
    // Check DB for existing region on startup
    let region_name: Option<String> =
        sqlx::query_scalar!("SELECT name FROM regions LIMIT 1")
            .fetch_optional(&db.pool)
            .await?;

    if let Some(ref name) = region_name {
        info!("Region '{}' already configured", name);
    } else {
        info!("No region configured — first-launch setup required");
    }

    let state = AppState {
        db: db.clone(),
        config: config.clone(),
        region: Arc::new(RwLock::new(region_name)),
        setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
    };

    // Setup routes are NOT behind the middleware guard
    let setup_router = Router::new()
        .route("/setup", get(handlers::setup_page).post(handlers::setup_submit))
        .route("/setup/status", get(handlers::setup_status))
        .with_state(state.clone());

    let app = Router::new()
        .merge(build_router(state))
        .merge(setup_router);

    let listener = tokio::net::TcpListener::bind(&config.bind_address).await?;
    info!("Dashboard available at http://{}", config.bind_address);
    axum::serve(listener, app).await?;
    Ok(())
}
```

Note: `sqlx` must be added to `crates/server/Cargo.toml` if not already present for the startup query.

- [ ] **Step 2: Add sqlx to server dependencies (if needed)**

In `crates/server/Cargo.toml`, the `[dev-dependencies]` already have sqlx. Add it to `[dependencies]`:

```toml
sqlx = { version = "0.8", features = ["runtime-tokio", "postgres"] }
```

- [ ] **Step 3: Build (handlers won't compile yet — that's expected)**

```bash
cargo build 2>&1 | grep "^error" | head -20
```

Expect errors only about missing handlers (`setup_page`, `setup_submit`, `setup_status`) — everything else should resolve.

- [ ] **Step 4: Commit (partial compile, stub handlers added in next tasks)**

```bash
git add crates/server/src/web/mod.rs crates/server/Cargo.toml
git commit -m "feat(server): add AppState region+setup_state, restructure serve()"
```

---

## Task 7: Setup Middleware

**Files:**
- Create: `crates/server/src/web/middleware.rs`

- [ ] **Step 1: Write the middleware**

```rust
use axum::{
    body::Body,
    extract::State,
    http::Request,
    middleware::Next,
    response::{IntoResponse, Redirect, Response},
};

use crate::web::AppState;

pub async fn require_region_configured(
    State(state): State<AppState>,
    request: Request<Body>,
    next: Next,
) -> Response {
    let configured = state.region.read().await.is_some();
    if !configured {
        return Redirect::to("/setup").into_response();
    }
    next.run(request).await
}
```

- [ ] **Step 2: Build**

```bash
cargo build
```

Expected: no new errors from the middleware module.

- [ ] **Step 3: Commit**

```bash
git add crates/server/src/web/middleware.rs
git commit -m "feat(server): add require_region_configured middleware"
```

---

## Task 8: Setup Templates

**Files:**
- Create: `crates/server/templates/pages/setup.html`
- Create: `crates/server/templates/pages/setup_progress.html`

- [ ] **Step 1: Create the setup form page**

`crates/server/templates/pages/setup.html`:

```html
{% extends "layouts/base.html" %}
{% block title %}Mobilispect — Setup{% endblock %}
{% block region_name %}{% endblock %}
{% block extra_head %}
<script src="https://cdn.jsdelivr.net/npm/htmx.org@2.0.10/dist/htmx.min.js"
        integrity="sha384-H5SrcfygHmAuTDZphMHqBJLc3FhssKjG7w/CeCpFReSfwBWDTKpkzPP8c+cLsK+V"
        crossorigin="anonymous" defer></script>
<style>
.setup-wrap {
  max-width: 480px;
  margin: 6rem auto 0;
  padding: 0 1rem;
}
.setup-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 2.5rem 2rem;
  box-shadow: var(--shadow);
}
.setup-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 2.2rem;
  font-weight: 300;
  color: var(--ink-900);
  margin-bottom: 0.4rem;
}
.setup-sub {
  color: var(--ink-500);
  font-size: 0.9rem;
  margin-bottom: 2rem;
}
.field-label {
  display: block;
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--ink-500);
  letter-spacing: 0.05em;
  margin-bottom: 6px;
}
.field {
  width: 100%;
  font-family: 'Jost', sans-serif;
  font-size: 0.875rem;
  border: 1.5px solid var(--line);
  border-radius: 6px;
  padding: 0.6rem 0.875rem;
  background: var(--surface);
  color: var(--ink-900);
  margin-bottom: 1.5rem;
}
.field:focus {
  outline: none;
  border-color: var(--civic-red);
  box-shadow: 0 0 0 3px rgba(200,70,58,0.12);
}
.btn-submit {
  width: 100%;
  padding: 0.75rem;
  background: var(--link-blue);
  color: white;
  border: none;
  border-radius: 8px;
  font-family: 'Jost', sans-serif;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
}
.btn-submit:hover { background: var(--link-blue-hover); }
{% if error %}
.setup-error {
  background: var(--civic-red-bg);
  color: var(--civic-red);
  border: 1px solid var(--civic-red);
  border-radius: 6px;
  padding: 0.75rem 1rem;
  font-size: 0.875rem;
  margin-bottom: 1.25rem;
}
{% endif %}
</style>
{% endblock %}

{% block content %}
<div class="setup-wrap">
  <div class="setup-card">
    <h1 class="setup-title">Welcome to Mobilispect</h1>
    <p class="setup-sub">Enter your city to discover transit feeds automatically.</p>
    {% if error %}
    <div class="setup-error">{{ error }}</div>
    {% endif %}
    <form action="/setup" method="post">
      <label class="field-label" for="city">City or metro area</label>
      <input class="field" id="city" name="city_name" type="text"
             placeholder="e.g. Montreal" value="{{ prefill }}" required autofocus>
      <button class="btn-submit" type="submit">Find feeds</button>
    </form>
  </div>
</div>
{% endblock %}
```

- [ ] **Step 2: Create the progress page**

`crates/server/templates/pages/setup_progress.html`:

```html
{% extends "layouts/base.html" %}
{% block title %}Mobilispect — Setting Up{% endblock %}
{% block region_name %}{% endblock %}
{% block extra_head %}
<script src="https://cdn.jsdelivr.net/npm/htmx.org@2.0.10/dist/htmx.min.js"
        integrity="sha384-H5SrcfygHmAuTDZphMHqBJLc3FhssKjG7w/CeCpFReSfwBWDTKpkzPP8c+cLsK+V"
        crossorigin="anonymous" defer></script>
<style>
.setup-wrap { max-width: 480px; margin: 6rem auto 0; padding: 0 1rem; }
.setup-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 2.5rem 2rem;
  box-shadow: var(--shadow);
  text-align: center;
}
.setup-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 2.2rem;
  font-weight: 300;
  color: var(--ink-900);
  margin-bottom: 1.5rem;
}
@keyframes spin { to { transform: rotate(360deg); } }
.spinner {
  width: 40px; height: 40px;
  border: 3px solid var(--line);
  border-top-color: var(--link-blue);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 1.5rem;
}
.setup-city {
  font-family: 'Fira Code', monospace;
  color: var(--civic-red);
  font-size: 1.1rem;
}
.setup-sub { color: var(--ink-500); font-size: 0.85rem; margin-top: 0.5rem; }
</style>
{% endblock %}

{% block content %}
<div class="setup-wrap">
  <div class="setup-card"
       hx-get="/setup/status"
       hx-trigger="every 1s"
       hx-target="this"
       hx-swap="outerHTML">
    <h1 class="setup-title">Searching Transitland…</h1>
    <div class="spinner"></div>
    <p class="setup-city">{{ city }}</p>
    <p class="setup-sub">Discovering transit feeds for your city.</p>
  </div>
</div>
{% endblock %}
```

- [ ] **Step 3: Build**

```bash
cargo build
```

Expected: Askama will report errors only if the template variables (`error`, `prefill`, `city`) don't match handler structs — those are added in Task 9.

- [ ] **Step 4: Commit**

```bash
git add crates/server/templates/pages/setup.html crates/server/templates/pages/setup_progress.html
git commit -m "feat(templates): add setup form and progress pages"
```

---

## Task 9: Setup Handlers

**Files:**
- Modify: `crates/server/src/web/handlers.rs`

- [ ] **Step 1: Add template structs at the top of handlers.rs**

After the existing `use` statements, add:

```rust
use axum::Form;
use axum::http::{HeaderMap, HeaderValue, StatusCode};
use mobilispect_core::db::feeds::store_discovered_feeds;
use mobilispect_core::transitland::TransitlandClient;
use crate::web::SetupState;
```

Add these template structs near the other `#[derive(Template)]` structs:

```rust
#[derive(Template)]
#[template(path = "pages/setup.html")]
struct SetupFormTemplate {
    error: Option<String>,
    prefill: String,
}

#[derive(Template)]
#[template(path = "pages/setup_progress.html")]
struct SetupProgressTemplate {
    city: String,
}
```

- [ ] **Step 2: Add the form deserializer**

```rust
#[derive(Deserialize)]
pub struct SetupForm {
    city_name: String,
}
```

- [ ] **Step 3: Implement the three handlers**

```rust
pub async fn setup_page() -> impl IntoResponse {
    Html(
        SetupFormTemplate {
            error: None,
            prefill: String::new(),
        }
        .render()
        .unwrap_or_default(),
    )
}

pub async fn setup_submit(
    State(state): State<AppState>,
    Form(form): Form<SetupForm>,
) -> impl IntoResponse {
    let city = form.city_name.trim().to_string();

    // If already running, just show the progress page
    {
        let setup = state.setup_state.lock().await;
        if matches!(*setup, SetupState::Running) {
            return Html(
                SetupProgressTemplate { city: city.clone() }
                    .render()
                    .unwrap_or_default(),
            )
            .into_response();
        }
    }

    // Mark as running
    *state.setup_state.lock().await = SetupState::Running;

    // Spawn background task
    let pool = state.db.pool.clone();
    let api_key = state.config.transitland_api_key.clone();
    let setup_state = state.setup_state.clone();
    let city_clone = city.clone();
    tokio::spawn(async move {
        let client = TransitlandClient::new(api_key);
        let result = async {
            let feeds = client.discover_feeds_for_city(&city_clone).await?;
            if feeds.is_empty() {
                anyhow::bail!("No feeds found for '{city_clone}' — try a different city name");
            }
            store_discovered_feeds(&pool, &city_clone, &feeds).await?;
            anyhow::Ok(city_clone.clone())
        }
        .await;

        let mut setup = setup_state.lock().await;
        *setup = match result {
            Ok(city) => SetupState::Done { city },
            Err(e) => SetupState::Failed { message: e.to_string() },
        };
    });

    Html(
        SetupProgressTemplate { city }
            .render()
            .unwrap_or_default(),
    )
    .into_response()
}

pub async fn setup_status(State(state): State<AppState>) -> impl IntoResponse {
    let mut setup = state.setup_state.lock().await;
    match &*setup {
        SetupState::Idle | SetupState::Running => {
            // Return the polling card so htmx replaces it and re-polls
            Html(r#"<div class="setup-card"
                         hx-get="/setup/status"
                         hx-trigger="every 1s"
                         hx-target="this"
                         hx-swap="outerHTML">
                       <h1 class="setup-title">Searching Transitland…</h1>
                       <div class="spinner"></div>
                       <p class="setup-sub">Discovering transit feeds for your city.</p>
                    </div>"#)
                .into_response()
        }
        SetupState::Done { city } => {
            // Write region into shared state so the middleware lets future requests through
            *state.region.write().await = Some(city.clone());
            *setup = SetupState::Idle;
            let mut headers = HeaderMap::new();
            headers.insert("HX-Redirect", HeaderValue::from_static("/"));
            (StatusCode::OK, headers, Html(String::new())).into_response()
        }
        SetupState::Failed { message } => {
            let msg = message.clone();
            *setup = SetupState::Idle;
            // Return to setup form with error message
            Html(
                SetupFormTemplate {
                    error: Some(msg),
                    prefill: String::new(),
                }
                .render()
                .unwrap_or_default(),
            )
            .into_response()
        }
    }
}
```

- [ ] **Step 4: Build**

```bash
cargo build
```

Expected: clean build.

- [ ] **Step 5: Commit**

```bash
git add crates/server/src/web/handlers.rs
git commit -m "feat(server): add setup_page, setup_submit, setup_status handlers"
```

---

## Task 10: Update All `region_name` Reads in Page Handlers

**Files:**
- Modify: `crates/server/src/web/handlers.rs`

All non-setup handlers currently have `region_name: String::new()` from Task 5. Replace each with the real value read from `AppState.region`. Since the middleware guarantees `region` is `Some` for these handlers, the unwrap is safe.

- [ ] **Step 1: Add a helper to extract region name**

In the handler file, add a private helper:

```rust
async fn region_name(state: &AppState) -> String {
    state
        .region
        .read()
        .await
        .clone()
        .unwrap_or_default()
}
```

- [ ] **Step 2: Replace all `region_name: String::new()` occurrences**

For every handler that constructs a template struct with `region_name`, change:
```rust
region_name: String::new(),
```
to:
```rust
region_name: region_name(&state).await,
```

Run a search to find all occurrences:
```bash
grep -n "String::new()" crates/server/src/web/handlers.rs
```

- [ ] **Step 3: Build and verify**

```bash
cargo build
```

Expected: clean build.

- [ ] **Step 4: Commit**

```bash
git add crates/server/src/web/handlers.rs
git commit -m "feat(server): read region_name from AppState instead of config"
```

---

## Task 11: Update Worker to Load Feeds from DB

**Files:**
- Modify: `crates/worker/src/main.rs`
- Modify: `crates/worker/src/maintenance/mod.rs`

- [ ] **Step 1: Update worker main.rs**

Replace the `config.feeds` iteration in `crates/worker/src/main.rs`. At the top, add:

```rust
use mobilispect_core::db::feeds::load_feeds;
use mobilispect_core::config::FeedConfig;
```

Replace the feed loading section. The current code is:
```rust
info!(
    "Mobilispect worker starting — {} feed(s) configured",
    config.feeds.len()
);
// ... and `for agency in &config.feeds { ... }` loop
```

Change to:

```rust
let db_feeds = load_feeds(&db.pool).await?;
if db_feeds.is_empty() {
    tracing::warn!("No feeds in DB — waiting for first-launch setup before ingesting");
}
let feeds: Vec<FeedConfig> = db_feeds.into_iter().map(FeedConfig::from).collect();

info!(
    "Mobilispect worker starting — {} feed(s) in DB",
    feeds.len()
);

// ... replace `for agency in &config.feeds` with `for agency in &feeds`
```

Also update the `maintenance::backfill_daily_metrics` call — currently it takes `&config`. Since `config.feeds` no longer exists, update the maintenance signature (see next step) to accept `&[FeedConfig]` and `u32` directly:

```rust
maintenance::backfill_daily_metrics(&db, &feeds, config.retention_days, 7).await;
```

And the RT poll loop stays the same (iterates `loaded` which is a `Vec<FeedConfig>`).

The maintenance retention loop still uses `config.retention_days` and `config.worker_health_bind_address` — pass those directly or keep passing `&config` (since `Config` no longer has feeds/region it's fine).

- [ ] **Step 2: Update `maintenance::backfill_daily_metrics` signature**

In `crates/worker/src/maintenance/mod.rs`, change the function signature from:

```rust
pub async fn backfill_daily_metrics(db: &Database, config: &Config, days: u32) {
    // ...
    for agency in &config.feeds {
```

to:

```rust
pub async fn backfill_daily_metrics(db: &Database, feeds: &[mobilispect_core::config::FeedConfig], days: u32) {
    // ...
    for agency in feeds {
```

Remove the `use mobilispect_core::config::Config;` import if it was only used for `config.feeds`. Keep the `use mobilispect_core::config::Config;` if `retention_loop` still takes `&Config` for `retention_days`.

Update the retention loop to use `config.retention_days` directly (it still takes `&Config` since that struct still has `retention_days`).

Also update the test helpers in `maintenance/mod.rs` that constructed `Config { region: RegionConfig { ... }, feeds: vec![...] }` — replace with just the fields that still exist, or remove region/feeds construction entirely.

- [ ] **Step 3: Build and test**

```bash
cargo build
cargo nextest run
```

Expected: clean.

- [ ] **Step 4: Commit**

```bash
git add crates/worker/src/main.rs crates/worker/src/maintenance/mod.rs
git commit -m "feat(worker): load feeds from DB instead of config"
```

---

## Self-Review Checklist

**Spec coverage:**

| Spec requirement | Task |
|-----------------|------|
| Migration 016 for feed discovery columns | Task 1 |
| Move TransitlandClient to core | Task 2 |
| `discover_feeds_for_city` with tests | Task 3 |
| `DbFeed`, `load_feeds`, `store_discovered_feeds` with tests | Task 4 |
| Remove region/feeds from Config | Task 5 |
| Remove `[region]` from config.toml | Task 5 |
| AppState with `region` + `setup_state` | Task 6 |
| Middleware guard (redirect to /setup when no region) | Task 7 |
| Setup templates | Task 8 |
| GET/POST /setup + GET /setup/status handlers | Task 9 |
| Router wiring (setup routes outside middleware) | Task 6 (serve function) |
| Worker reads from DB each poll | Task 11 |
| Page handlers use region from AppState | Task 10 |

**Gaps found and addressed:**

- The `SetupProgressTemplate` card in `setup_status` returns raw HTML inline rather than re-rendering the Askama template. This is intentional — htmx replaces just the `div.setup-card`, not the whole page, so the full-page template is only used for the initial POST response.
- `serve()` in Task 6 uses `sqlx::query_scalar!` directly — this requires `sqlx` in `[dependencies]` (not just dev-dependencies) of the server crate. Noted in Task 6 Step 2.
- The `region_name` helper in Task 10 is `async` because `RwLock::read()` is async. All handler call sites must `await` it.
- `maintenance::retention_loop` still takes `&Config` (for `retention_days`) — no change needed there since `Config` still has that field.
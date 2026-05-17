# Workspace Crate Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the single `mobilispect` Cargo package into a workspace with three crates — `mobilispect-core`, `mobilispect-server`, `mobilispect-worker` — so each binary compiles independently with only its own dependencies.

**Architecture:** A shared library crate (`mobilispect-core`) holds all domain logic (config, db, speed, metrics, frequency). Two binary crates (`mobilispect-server` and `mobilispect-worker`) depend on core and each own the modules exclusive to them (`web/` and `gtfs/ + maintenance/` respectively). No new behaviour is introduced — this is a pure structural migration.

**Tech Stack:** Cargo workspaces, sqlx offline mode (`cargo sqlx prepare --workspace`), Askama template resolution via `CARGO_MANIFEST_DIR`, prost-build in worker.

---

## File Map

**Created:**
- `crates/core/Cargo.toml`
- `crates/core/src/lib.rs`
- `crates/server/Cargo.toml`
- `crates/server/src/main.rs`
- `crates/worker/Cargo.toml`
- `crates/worker/src/main.rs`
- `crates/worker/build.rs`

**Moved (source → destination):**
- `src/config.rs` → `crates/core/src/config.rs`
- `src/db/` → `crates/core/src/db/`
- `src/speed/` → `crates/core/src/speed/`
- `src/metrics/` → `crates/core/src/metrics/`
- `src/frequency/` → `crates/core/src/frequency/`
- `migrations/` → `crates/core/migrations/`
- `src/web/` → `crates/server/src/web/`
- `templates/` → `crates/server/templates/`
- `src/gtfs/` → `crates/worker/src/gtfs/`
- `src/maintenance/` → `crates/worker/src/maintenance/`
- `proto/` → `crates/worker/proto/`

**Modified:**
- `Cargo.toml` → replaced with workspace manifest
- `crates/core/src/db/mod.rs` → expose `test_utils` under feature flag
- `crates/server/src/web/mod.rs` → update `use crate::` → `use mobilispect_core::`
- `crates/server/src/web/handlers.rs` → update `use crate::` → `use mobilispect_core::` (except `use crate::web::`)
- `crates/worker/src/gtfs/realtime.rs` → update `use crate::` → `use mobilispect_core::`
- `crates/worker/src/gtfs/static_feed.rs` → update `use crate::` → `use mobilispect_core::`
- `crates/worker/src/maintenance/mod.rs` → update `use crate::` → `use mobilispect_core::`

**Deleted (after new crates verified):**
- `src/` (entire directory)
- `build.rs` (root level)
- `migrations/` (root level)
- `templates/` (root level)
- `proto/` (root level)
- `.sqlx/` (root level — regenerated per-crate)

**Dockerfile:** No changes needed — `cargo build --release` builds all workspace members; binary output paths are unchanged.

---

## Task 1: Workspace Manifest + Crate Scaffolds

**Files:**
- Modify: `Cargo.toml`
- Create: `crates/core/Cargo.toml`, `crates/core/src/lib.rs`
- Create: `crates/server/Cargo.toml`, `crates/server/src/main.rs`
- Create: `crates/worker/Cargo.toml`, `crates/worker/src/main.rs`

- [ ] **Step 1: Replace root `Cargo.toml` with workspace manifest**

```toml
[workspace]
members = ["crates/core", "crates/server", "crates/worker"]
resolver = "2"
```

- [ ] **Step 2: Create `crates/core/Cargo.toml`**

```toml
[package]
name = "mobilispect-core"
version = "0.1.0"
edition = "2024"

[lib]
name = "mobilispect_core"

[features]
test-utils = ["dep:testcontainers", "dep:testcontainers-modules"]

[dependencies]
tokio = { version = "1", features = ["full"] }
sqlx = { version = "0.8", features = ["runtime-tokio", "postgres", "chrono", "migrate"] }
chrono = { version = "0.4", features = ["serde"] }
serde = { version = "1", features = ["derive"] }
serde_json = "1"
toml = "0.8"
tracing = "0.1"
anyhow = "1"
thiserror = "2"
bytes = "1"
testcontainers = { version = "0.21", optional = true }
testcontainers-modules = { version = "0.9", features = ["postgres"], optional = true }

[dev-dependencies]
testcontainers = "0.21"
testcontainers-modules = { version = "0.9", features = ["postgres"] }
tokio = { version = "1", features = ["full"] }
```

- [ ] **Step 3: Create stub `crates/core/src/lib.rs`**

```rust
// populated in Task 2
```

- [ ] **Step 4: Create `crates/server/Cargo.toml`**

```toml
[package]
name = "mobilispect-server"
version = "0.1.0"
edition = "2024"

[[bin]]
name = "mobilispect-server"
path = "src/main.rs"

[dependencies]
mobilispect-core = { path = "../core" }
tokio = { version = "1", features = ["full"] }
axum = { version = "0.7", features = ["macros"] }
tower = "0.5"
tower-http = { version = "0.6", features = ["fs", "trace"] }
askama = { version = "0.15", features = ["derive"] }
tracing = "0.1"
tracing-subscriber = { version = "0.3", features = ["env-filter"] }
anyhow = "1"

[dev-dependencies]
mobilispect-core = { path = "../core", features = ["test-utils"] }
tokio = { version = "1", features = ["full"] }
```

- [ ] **Step 5: Create stub `crates/server/src/main.rs`**

```rust
fn main() {}
```

- [ ] **Step 6: Create `crates/worker/Cargo.toml`**

```toml
[package]
name = "mobilispect-worker"
version = "0.1.0"
edition = "2024"

[[bin]]
name = "mobilispect-worker"
path = "src/main.rs"

[dependencies]
mobilispect-core = { path = "../core" }
tokio = { version = "1", features = ["full"] }
reqwest = { version = "0.12", features = ["gzip", "json"] }
gtfs-structures = "0.26"
prost = "0.13"
sha2 = "0.10"
hex = "0.4"
tracing = "0.1"
tracing-subscriber = { version = "0.3", features = ["env-filter"] }
anyhow = "1"
bytes = "1"

[dev-dependencies]
mobilispect-core = { path = "../core", features = ["test-utils"] }
tokio = { version = "1", features = ["full"] }

[build-dependencies]
prost-build = "0.13"
```

- [ ] **Step 7: Create stub `crates/worker/src/main.rs`**

```rust
fn main() {}
```

- [ ] **Step 8: Verify workspace is valid**

```bash
cargo check --workspace
```

Expected: all three stubs compile. Ignore "unused" warnings.

- [ ] **Step 9: Commit**

```bash
git add Cargo.toml crates/
git commit -m "chore: scaffold cargo workspace with core/server/worker crates"
```

---

## Task 2: Populate mobilispect-core

**Files:**
- Create: `crates/core/src/lib.rs`, `crates/core/src/config.rs`, `crates/core/src/db/`, `crates/core/src/speed/`, `crates/core/src/metrics/`, `crates/core/src/frequency/`, `crates/core/migrations/`
- Modify: `crates/core/src/db/mod.rs` (expose test_utils under feature flag)

- [ ] **Step 1: Copy shared module sources into core**

```bash
cp src/config.rs crates/core/src/config.rs
cp -r src/db crates/core/src/db
cp -r src/speed crates/core/src/speed
cp -r src/metrics crates/core/src/metrics
cp -r src/frequency crates/core/src/frequency
cp -r migrations crates/core/migrations
```

- [ ] **Step 2: Replace stub `crates/core/src/lib.rs`**

```rust
pub mod config;
pub mod db;
pub mod frequency;
pub mod metrics;
pub mod speed;
```

- [ ] **Step 3: Update `test_utils` visibility in `crates/core/src/db/mod.rs`**

Find this line:
```rust
#[cfg(test)]
pub mod test_utils {
```

Replace with:
```rust
#[cfg(any(test, feature = "test-utils"))]
pub mod test_utils {
```

This makes `test_utils` available to dependent crates that activate `features = ["test-utils"]` in their dev-dependencies, while still compiling during core's own `cargo test` without needing the feature.

- [ ] **Step 4: Verify core builds and tests pass**

```bash
dotenvx run -- cargo build -p mobilispect-core
dotenvx run -- cargo test -p mobilispect-core
```

Expected: builds clean, all existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add crates/core/
git commit -m "chore: populate mobilispect-core with shared domain modules"
```

---

## Task 3: Populate mobilispect-server

**Files:**
- Create: `crates/server/src/main.rs`, `crates/server/src/web/`, `crates/server/templates/`
- Modify: `crates/server/src/web/mod.rs`, `crates/server/src/web/handlers.rs`

- [ ] **Step 1: Copy web module and templates into server crate**

```bash
cp -r src/web crates/server/src/web
cp -r templates crates/server/templates
```

- [ ] **Step 2: Replace stub `crates/server/src/main.rs`**

```rust
use anyhow::Result;
use tracing_subscriber::EnvFilter;

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;

mod web;

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("mobilispect=info".parse()?))
        .init();

    let config = Config::load()?;
    let db = Database::connect(&config.database_url).await?;
    db.migrate().await?;

    web::serve(&db, &config).await?;

    Ok(())
}
```

- [ ] **Step 3: Update `crates/server/src/web/mod.rs` — fix cross-crate imports**

Find and replace these two imports at the top:
```rust
use crate::config::Config;
use crate::db::Database;
```
With:
```rust
use mobilispect_core::config::Config;
use mobilispect_core::db::Database;
```

All other lines in `web/mod.rs` (including `mod handlers;`, `AppState`, `build_router`, `serve`) are unchanged.

- [ ] **Step 4: Update `crates/server/src/web/handlers.rs` — fix cross-crate imports**

Replace all occurrences of `use crate::frequency::` with `use mobilispect_core::frequency::`.
Replace all occurrences of `use crate::metrics::` with `use mobilispect_core::metrics::`.
Replace all occurrences of `use crate::speed::` with `use mobilispect_core::speed::`.

Leave these unchanged (they reference types within the server crate itself):
- `use crate::web::AppState;`
- `use crate::web::build_router;`

In the `#[cfg(test)]` block, replace:
- `use crate::config::` → `use mobilispect_core::config::`
- `use crate::db::test_utils` → `use mobilispect_core::db::test_utils`

- [ ] **Step 5: Verify server builds and tests pass**

```bash
dotenvx run -- cargo build -p mobilispect-server
dotenvx run -- cargo test -p mobilispect-server
```

Expected: builds clean, all handler tests pass.

- [ ] **Step 6: Commit**

```bash
git add crates/server/
git commit -m "chore: populate mobilispect-server with web module and templates"
```

---

## Task 4: Populate mobilispect-worker

**Files:**
- Create: `crates/worker/src/main.rs`, `crates/worker/src/gtfs/`, `crates/worker/src/maintenance/`, `crates/worker/proto/`, `crates/worker/build.rs`
- Modify: `crates/worker/src/gtfs/realtime.rs`, `crates/worker/src/gtfs/static_feed.rs`, `crates/worker/src/maintenance/mod.rs`

- [ ] **Step 1: Copy worker-only sources into worker crate**

```bash
cp -r src/gtfs crates/worker/src/gtfs
cp -r src/maintenance crates/worker/src/maintenance
cp -r proto crates/worker/proto
cp build.rs crates/worker/build.rs
```

- [ ] **Step 2: Replace stub `crates/worker/src/main.rs`**

```rust
use anyhow::Result;
use tracing::{info, warn};
use tracing_subscriber::EnvFilter;

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;
use mobilispect_core::speed;

mod gtfs;
mod maintenance;

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("mobilispect=info".parse()?))
        .init();

    let config = Config::load()?;
    let db = Database::connect(&config.database_url).await?;

    info!(
        "Mobilispect worker starting — {} agency/agencies configured",
        config.agencies.len()
    );

    let mut set: tokio::task::JoinSet<(mobilispect_core::config::AgencyConfig, Result<()>)> =
        tokio::task::JoinSet::new();
    for agency in &config.agencies {
        let db = db.clone();
        let agency = agency.clone();
        set.spawn(async move {
            info!("Loading static GTFS for agency: {}", agency.name);
            let result = async {
                gtfs::static_feed::load_if_needed(&db, &agency).await?;
                speed::compute_route_speed(&db, &agency).await?;
                speed::compute_route_speed_by_day_type(&db, &agency).await?;
                info!(
                    "Computed scheduled speed (all day types) for agency: {}",
                    agency.name
                );
                Ok(())
            }
            .await;
            (agency, result)
        });
    }

    let mut loaded = Vec::new();
    while let Some(res) = set.join_next().await {
        let (agency, result) = res?;
        match result {
            Ok(()) => loaded.push(agency),
            Err(e) => warn!(
                "Skipping {}: failed to load static GTFS: {e:#}",
                agency.name
            ),
        }
    }

    for agency in loaded {
        let db_rt = db.clone();
        let poll_interval = config.poll_interval_secs;
        tokio::spawn(async move {
            loop {
                gtfs::realtime::poll_loop(&db_rt, &agency, poll_interval).await;
                warn!(agency = %agency.name, "RT poll loop exited unexpectedly, restarting in 30s");
                tokio::time::sleep(std::time::Duration::from_secs(30)).await;
            }
        });
    }

    let db_maint = db.clone();
    let config_maint = config.clone();
    tokio::spawn(async move {
        loop {
            maintenance::retention_loop(&db_maint, &config_maint).await;
            warn!("Maintenance loop exited unexpectedly, restarting in 30s");
            tokio::time::sleep(std::time::Duration::from_secs(30)).await;
        }
    });

    std::future::pending::<()>().await;

    Ok(())
}
```

- [ ] **Step 3: Update `crates/worker/src/gtfs/realtime.rs` — fix cross-crate imports**

Replace:
```rust
use crate::config::AgencyConfig;
use crate::db::Database;
use crate::speed::compute_route_speed_hourly;
```
With:
```rust
use mobilispect_core::config::AgencyConfig;
use mobilispect_core::db::Database;
use mobilispect_core::speed::compute_route_speed_hourly;
```

In the `#[cfg(test)]` block, replace:
```rust
use crate::db::test_utils;
```
With:
```rust
use mobilispect_core::db::test_utils;
```

- [ ] **Step 4: Update `crates/worker/src/gtfs/static_feed.rs` — fix cross-crate imports**

Replace:
```rust
use crate::config::AgencyConfig;
use crate::db::Database;
```
With:
```rust
use mobilispect_core::config::AgencyConfig;
use mobilispect_core::db::Database;
```

In the `#[cfg(test)]` block, replace:
```rust
use crate::db::test_utils;
```
With:
```rust
use mobilispect_core::db::test_utils;
```

- [ ] **Step 5: Update `crates/worker/src/maintenance/mod.rs` — fix cross-crate imports**

Replace:
```rust
use crate::config::Config;
use crate::db::Database;
use crate::metrics::compute_route_daily;
use crate::speed::compute_route_speed_daily;
```
With:
```rust
use mobilispect_core::config::Config;
use mobilispect_core::db::Database;
use mobilispect_core::metrics::compute_route_daily;
use mobilispect_core::speed::compute_route_speed_daily;
```

- [ ] **Step 6: Verify worker builds and tests pass**

```bash
dotenvx run -- cargo build -p mobilispect-worker
dotenvx run -- cargo test -p mobilispect-worker
```

Expected: builds clean (prost-build compiles the proto), all gtfs tests pass.

- [ ] **Step 7: Commit**

```bash
git add crates/worker/
git commit -m "chore: populate mobilispect-worker with gtfs and maintenance modules"
```

---

## Task 5: Remove Old Root-Level Source Structure

At this point all three crates compile and test cleanly. The old source tree is now dead code.

- [ ] **Step 1: Delete old source directories and files**

```bash
rm -rf src/
rm -f build.rs
rm -rf migrations/
rm -rf templates/
rm -rf proto/
```

- [ ] **Step 2: Verify full workspace build still passes**

```bash
dotenvx run -- cargo build --workspace
dotenvx run -- cargo test --workspace
```

Expected: builds clean, all tests pass, no references to the deleted paths.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: remove old root-level source tree after workspace migration"
```

---

## Task 6: Regenerate sqlx Offline Cache

The `.sqlx/` directory at the repo root is no longer in the right location. sqlx resolves query cache files relative to each crate's `CARGO_MANIFEST_DIR`. `gtfs/` queries belong in `crates/worker/.sqlx/`; core queries belong in `crates/core/.sqlx/`. Run `cargo sqlx prepare --workspace` to regenerate everything in the correct locations.

Requires a live database — use the local dev Postgres started by `dev.sh`.

- [ ] **Step 1: Start local Postgres if not already running**

```bash
./dev.sh
```

Wait for the Docker container to start, then Ctrl-C once both watchers are running (the DB keeps running).

- [ ] **Step 2: Remove stale root-level sqlx cache**

```bash
rm -rf .sqlx/
```

- [ ] **Step 3: Regenerate cache for all workspace crates**

```bash
dotenvx run -- cargo sqlx prepare --workspace
```

Expected: creates `crates/core/.sqlx/` and `crates/worker/.sqlx/` (server has no direct `query!` calls). Each directory contains JSON files, one per `query!` macro site.

- [ ] **Step 4: Verify offline build works**

```bash
SQLX_OFFLINE=true cargo build --workspace
```

Expected: builds cleanly without a live database.

- [ ] **Step 5: Commit the new cache locations**

```bash
git add crates/core/.sqlx/ crates/worker/.sqlx/
git commit -m "chore: regenerate sqlx offline query cache in per-crate locations"
```

---

## Task 7: Final Verification

- [ ] **Step 1: Run the full test suite**

```bash
dotenvx run -- cargo test --workspace
```

Expected: all tests pass, output is clean.

- [ ] **Step 2: Verify binary names are unchanged**

```bash
dotenvx run -- cargo build --release --workspace
ls target/release/mobilispect-server target/release/mobilispect-worker
```

Expected: both binaries present.

- [ ] **Step 3: Confirm dev.sh still works**

```bash
./dev.sh
```

Expected: both `cargo watch` processes start without error (binary names in dev.sh are unchanged).

- [ ] **Step 4: Confirm Dockerfile still builds**

```bash
docker build -t mobilispect-test .
```

Expected: image builds successfully. The Dockerfile's `cargo build --release` builds all workspace members; `SQLX_OFFLINE=true` picks up the per-crate `.sqlx/` directories; binary COPY paths are unchanged.

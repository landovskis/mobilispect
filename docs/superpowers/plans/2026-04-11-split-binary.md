# Split Binary: HTTP Server and Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the single `mobilispect` binary into `mobilispect-server` (HTTP server) and `mobilispect-worker` (GTFS init, RT polling, maintenance) for OS-level fault isolation.

**Architecture:** All existing modules move into `src/lib.rs` as a shared library crate. Two thin entry points in `src/bin/server.rs` and `src/bin/worker.rs` import from it. `Cargo.toml` gets two `[[bin]]` entries replacing the current one.

**Tech Stack:** Rust, Cargo, Tokio, axum, sqlx

---

## File Map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `src/lib.rs` | Declares all shared modules as `pub mod` |
| Create | `src/bin/server.rs` | HTTP server entry point |
| Create | `src/bin/worker.rs` | GTFS init + RT polling + maintenance entry point |
| Modify | `Cargo.toml` | Replace single `[[bin]]` with two entries |
| Delete | `src/main.rs` | Replaced by `src/bin/server.rs` and `src/bin/worker.rs` |

---

### Task 1: Create `src/lib.rs`

**Files:**
- Create: `src/lib.rs`

- [ ] **Step 1: Create `src/lib.rs`**

```rust
pub mod config;
pub mod db;
pub mod gtfs;
pub mod maintenance;
pub mod metrics;
pub mod speed;
pub mod web;
```

- [ ] **Step 2: Verify it compiles**

The existing `src/main.rs` still has `mod config; mod db; ...` — there will be a conflict. That's expected; we fix it in the next task. For now just check the file is saved correctly.

---

### Task 2: Create `src/bin/server.rs`

**Files:**
- Create: `src/bin/server.rs`

- [ ] **Step 1: Create the directory and file**

```bash
mkdir -p src/bin
```

- [ ] **Step 2: Write `src/bin/server.rs`**

```rust
use anyhow::Result;
use tracing_subscriber::EnvFilter;

use mobilispect::config::Config;
use mobilispect::db::Database;
use mobilispect::web;

#[tokio::main]
async fn main() -> Result<()> {
    dotenvy::dotenv().ok();

    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("mobilispect=info".parse()?))
        .init();

    let config = Config::from_env()?;
    let db = Database::connect(&config.database_url).await?;
    db.migrate().await?;

    web::serve(&db, &config).await?;

    Ok(())
}
```

---

### Task 3: Create `src/bin/worker.rs`

**Files:**
- Create: `src/bin/worker.rs`

- [ ] **Step 1: Write `src/bin/worker.rs`**

```rust
use anyhow::Result;
use tracing::{info, warn};
use tracing_subscriber::EnvFilter;

use mobilispect::config::Config;
use mobilispect::db::Database;
use mobilispect::{gtfs, maintenance, speed};

#[tokio::main]
async fn main() -> Result<()> {
    dotenvy::dotenv().ok();

    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("mobilispect=info".parse()?))
        .init();

    let config = Config::from_env()?;
    let db = Database::connect(&config.database_url).await?;
    db.migrate().await?;

    info!(
        "Mobilispect worker starting — {} agency/agencies configured",
        config.agencies.len()
    );

    // Load static GTFS for all agencies in parallel. Failed agencies are logged
    // and skipped; the process continues with the remaining agencies.
    let mut set: tokio::task::JoinSet<(mobilispect::config::AgencyConfig, Result<()>)> =
        tokio::task::JoinSet::new();
    for agency in &config.agencies {
        let db = db.clone();
        let agency = agency.clone();
        set.spawn(async move {
            info!("Loading static GTFS for agency: {}", agency.name);
            let result = async {
                gtfs::static_feed::load_if_needed(&db, &agency).await?;
                speed::compute_route_speed(&db, &agency).await?;
                info!("Computed scheduled speed for agency: {}", agency.name);
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
            Err(e) => warn!("Skipping {}: failed to load static GTFS: {e:#}", agency.name),
        }
    }

    // Start a GTFS-RT poll loop for each successfully loaded agency.
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

    // Start background task: data retention cleanup once per day
    let db_maint = db.clone();
    let config_maint = config.clone();
    tokio::spawn(async move {
        loop {
            maintenance::retention_loop(&db_maint, &config_maint).await;
            warn!("Maintenance loop exited unexpectedly, restarting in 30s");
            tokio::time::sleep(std::time::Duration::from_secs(30)).await;
        }
    });

    // Block forever — the spawned tasks do the work
    std::future::pending::<()>().await;

    Ok(())
}
```

---

### Task 4: Update `Cargo.toml` and delete `src/main.rs`

**Files:**
- Modify: `Cargo.toml`
- Delete: `src/main.rs`

- [ ] **Step 1: Replace the `[[bin]]` section in `Cargo.toml`**

Find and replace:
```toml
[[bin]]
name = "mobilispect"
path = "src/main.rs"
```

With:
```toml
[[bin]]
name = "mobilispect-server"
path = "src/bin/server.rs"

[[bin]]
name = "mobilispect-worker"
path = "src/bin/worker.rs"
```

- [ ] **Step 2: Delete `src/main.rs`**

```bash
rm src/main.rs
```

- [ ] **Step 3: Verify the project compiles**

```bash
cargo build 2>&1
```

Expected: successful build producing two binaries at `target/debug/mobilispect-server` and `target/debug/mobilispect-worker`.

If there are `use crate::` errors in any module: all existing modules use `crate::` to reference sibling modules. Since they are now part of the library crate (declared in `lib.rs`), `crate::` still resolves correctly to the library root — no changes to module internals are needed.

- [ ] **Step 4: Confirm both binaries exist**

```bash
ls target/debug/mobilispect-server target/debug/mobilispect-worker
```

Expected: both paths print without error.

- [ ] **Step 5: Commit**

```bash
git add src/lib.rs src/bin/server.rs src/bin/worker.rs Cargo.toml
git rm src/main.rs
git commit -m "feat: split mobilispect binary into mobilispect-server and mobilispect-worker"
```

---

### Task 5: Update the Dockerfile

**Files:**
- Modify: `Dockerfile`

The Dockerfile currently copies only `mobilispect`. Both binaries are now built by `cargo build --release`; update to copy both.

- [ ] **Step 1: Update the copy step and entrypoint in `Dockerfile`**

Find:
```dockerfile
COPY --from=builder /build/target/release/mobilispect /usr/local/bin/mobilispect
```

Replace with:
```dockerfile
COPY --from=builder /build/target/release/mobilispect-server /usr/local/bin/mobilispect-server
COPY --from=builder /build/target/release/mobilispect-worker /usr/local/bin/mobilispect-worker
```

Find:
```dockerfile
EXPOSE 3000

ENTRYPOINT ["/usr/local/bin/mobilispect"]
```

Replace with:
```dockerfile
EXPOSE 3000

# Default to server; override with: docker run <image> mobilispect-worker
ENTRYPOINT ["/usr/local/bin/mobilispect-server"]
```

- [ ] **Step 2: Commit**

```bash
git add Dockerfile
git commit -m "chore: update Dockerfile for split server/worker binaries"
```

# Pipeline Hooks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow each vertical slice in `crates/core` to contribute its own post-load hooks to the GTFS static and GTFS-RT pipelines, eliminating hard-coded slice calls from `main.rs` and `realtime.rs`.

**Architecture:** Each slice exposes two optional async hook functions — `on_static_loaded` (runs after a full GTFS static import) and `on_realtime_polled` (runs after each RT poll). A new `crates/worker/src/pipeline.rs` module is the single place that assembles and sequences these hooks. `main.rs` calls `pipeline::run_static_hooks`; `realtime.rs` calls `pipeline::run_realtime_hooks`. Adding a new slice's hook requires only editing `pipeline.rs`.

**Tech Stack:** Rust 2024, Tokio, sqlx 0.8 (Postgres), testcontainers for integration tests

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `crates/core/src/speed/mod.rs` | Add `on_static_loaded` and `on_realtime_polled` wrappers + tests |
| Create | `crates/worker/src/pipeline.rs` | Assembles all slice hooks; exposes `run_static_hooks` and `run_realtime_hooks` |
| Modify | `crates/worker/src/main.rs` | Declare `mod pipeline`; replace explicit speed calls with `pipeline::run_static_hooks` |
| Modify | `crates/worker/src/gtfs/realtime.rs` | Replace `compute_route_speed_hourly` call with `pipeline::run_realtime_hooks` |

---

## Task 1: Add `on_static_loaded` to the speed slice

**Files:**
- Modify: `crates/core/src/speed/mod.rs` (add near the bottom, before `#[cfg(test)]`)

The `on_static_loaded` hook wraps the two post-static-load speed computations that currently live in `main.rs`: `compute_route_speed` and `compute_route_speed_by_day_type`. When the DB is empty these both return `Ok(())` with no rows processed.

- [ ] **Step 1: Write the failing test**

Add inside the existing `#[cfg(test)] mod tests` block at the bottom of `crates/core/src/speed/mod.rs`:

```rust
#[tokio::test]
async fn on_static_loaded_succeeds_on_empty_db() {
    let td = test_utils::setup().await;
    let agency = test_agency();
    on_static_loaded(&td.db, &agency).await.unwrap();
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cargo test -p mobilispect-core on_static_loaded_succeeds_on_empty_db
```

Expected: compile error — `on_static_loaded` not found.

- [ ] **Step 3: Write minimal implementation**

Add this function to `crates/core/src/speed/mod.rs`, just above the `#[cfg(test)]` block:

```rust
pub async fn on_static_loaded(db: &Database, agency: &AgencyConfig) -> Result<()> {
    compute_route_speed(db, agency).await?;
    compute_route_speed_by_day_type(db, agency).await?;
    Ok(())
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cargo test -p mobilispect-core on_static_loaded_succeeds_on_empty_db
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add crates/core/src/speed/mod.rs
git commit -m "feat(speed): add on_static_loaded pipeline hook"
```

---

## Task 2: Add `on_realtime_polled` to the speed slice

**Files:**
- Modify: `crates/core/src/speed/mod.rs` (add just below `on_static_loaded`)

The `on_realtime_polled` hook wraps `compute_route_speed_hourly`, which currently is called directly inside `realtime::poll_once`. On an empty DB it returns `Ok(())`.

- [ ] **Step 1: Write the failing test**

Add inside the existing `#[cfg(test)] mod tests` block in `crates/core/src/speed/mod.rs`:

```rust
#[tokio::test]
async fn on_realtime_polled_succeeds_on_empty_db() {
    let td = test_utils::setup().await;
    let agency = test_agency();
    on_realtime_polled(&td.db, &agency).await.unwrap();
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cargo test -p mobilispect-core on_realtime_polled_succeeds_on_empty_db
```

Expected: compile error — `on_realtime_polled` not found.

- [ ] **Step 3: Write minimal implementation**

Add immediately after `on_static_loaded` in `crates/core/src/speed/mod.rs`:

```rust
pub async fn on_realtime_polled(db: &Database, agency: &AgencyConfig) -> Result<()> {
    compute_route_speed_hourly(db, agency).await
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cargo test -p mobilispect-core on_realtime_polled_succeeds_on_empty_db
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add crates/core/src/speed/mod.rs
git commit -m "feat(speed): add on_realtime_polled pipeline hook"
```

---

## Task 3: Create `crates/worker/src/pipeline.rs`

**Files:**
- Create: `crates/worker/src/pipeline.rs`

This module is the single registry for pipeline hooks. It imports each slice's hook and calls them in sequence. Adding a future slice hook means adding one import and one `await?` line here.

- [ ] **Step 1: Write the failing test**

Create `crates/worker/src/pipeline.rs` with the test only (implementation comes in Step 3):

```rust
use anyhow::Result;
use mobilispect_core::config::AgencyConfig;
use mobilispect_core::db::Database;
use mobilispect_core::speed;

pub async fn run_static_hooks(db: &Database, agency: &AgencyConfig) -> Result<()> {
    todo!()
}

pub async fn run_realtime_hooks(db: &Database, agency: &AgencyConfig) -> Result<()> {
    todo!()
}

#[cfg(test)]
mod tests {
    use super::*;
    use mobilispect_core::config::AgencyConfig;
    use mobilispect_core::db::test_utils;

    fn test_agency() -> AgencyConfig {
        AgencyConfig {
            id: 0,
            name: "Test Agency".to_string(),
            gtfs_static_url: String::new(),
            gtfs_rt_vehicle_positions_url: None,
            gtfs_rt_trip_updates_url: None,
            gtfs_api_key: None,
            agency_utc_offset: "-04:00".to_string(),
        }
    }

    #[tokio::test]
    async fn run_static_hooks_succeeds_on_empty_db() {
        let td = test_utils::setup().await;
        run_static_hooks(&td.db, &test_agency()).await.unwrap();
    }

    #[tokio::test]
    async fn run_realtime_hooks_succeeds_on_empty_db() {
        let td = test_utils::setup().await;
        run_realtime_hooks(&td.db, &test_agency()).await.unwrap();
    }
}
```

Also add `mod pipeline;` to `crates/worker/src/main.rs` (in the `mod` declarations section, alongside `mod gtfs;` and `mod maintenance;`).

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test -p mobilispect-worker pipeline
```

Expected: FAIL — panics at `todo!()`

- [ ] **Step 3: Write minimal implementation**

Replace the `todo!()` bodies in `crates/worker/src/pipeline.rs`:

```rust
pub async fn run_static_hooks(db: &Database, agency: &AgencyConfig) -> Result<()> {
    speed::on_static_loaded(db, agency).await?;
    Ok(())
}

pub async fn run_realtime_hooks(db: &Database, agency: &AgencyConfig) -> Result<()> {
    speed::on_realtime_polled(db, agency).await?;
    Ok(())
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cargo test -p mobilispect-worker pipeline
```

Expected: PASS (both tests)

- [ ] **Step 5: Commit**

```bash
git add crates/worker/src/pipeline.rs crates/worker/src/main.rs
git commit -m "feat(worker): add pipeline hook registry"
```

---

## Task 4: Wire pipeline into `main.rs` and `realtime.rs`

**Files:**
- Modify: `crates/worker/src/main.rs:34-38` — replace explicit speed calls
- Modify: `crates/worker/src/gtfs/realtime.rs:6-8,49` — replace `compute_route_speed_hourly` call

This task removes the direct slice knowledge from `main.rs` and `realtime.rs`. No new logic — just moving call sites.

- [ ] **Step 1: Update `main.rs`**

In `crates/worker/src/main.rs`, the current block (approximately lines 33–42):

```rust
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
```

Replace with:

```rust
let result = async {
    gtfs::static_feed::load_if_needed(&db, &agency).await?;
    pipeline::run_static_hooks(&db, &agency).await?;
    info!(
        "Static import complete for agency: {}",
        agency.name
    );
    Ok(())
}
.await;
```

Also remove the `use mobilispect_core::speed;` import from `main.rs` (it's no longer needed there).

- [ ] **Step 2: Update `realtime.rs`**

In `crates/worker/src/gtfs/realtime.rs`, at the top, remove the import:

```rust
use mobilispect_core::speed::compute_route_speed_hourly;
```

In `poll_once` (around line 49), replace:

```rust
compute_route_speed_hourly(db, agency).await?;
```

with:

```rust
crate::pipeline::run_realtime_hooks(db, agency).await?;
```

- [ ] **Step 3: Build to verify no compile errors**

```bash
cargo build -p mobilispect-worker
```

Expected: compiles cleanly, no unused import warnings.

- [ ] **Step 4: Run all tests**

```bash
cargo test
```

Expected: all tests pass, no regressions.

- [ ] **Step 5: Commit**

```bash
git add crates/worker/src/main.rs crates/worker/src/gtfs/realtime.rs
git commit -m "refactor(worker): route post-import hooks through pipeline registry"
```

---

## Self-Review

**Spec coverage:**
- ✅ Each slice contributes hooks via `on_static_loaded` / `on_realtime_polled`
- ✅ `pipeline.rs` is the single assembly point
- ✅ `main.rs` and `realtime.rs` no longer reference slice functions directly
- ✅ Adding a future slice = one import + one `await?` in `pipeline.rs`

**Placeholder scan:** None found.

**Type consistency:** `Database`, `AgencyConfig`, `Result<()>` — same types throughout all tasks.

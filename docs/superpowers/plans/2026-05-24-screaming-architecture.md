# Screaming Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the four core module groupings so the top-level structure reveals what the system does (transit performance monitoring) rather than what it's built on.

**Architecture:** This is a pure rename refactor — no logic changes, no new tests. Each directory rename updates import paths in dependent files. The three-crate structure (core / server / worker) is preserved; we reorganize within each crate.

**Tech Stack:** Rust workspace, `cargo build -p <crate>` for incremental verification.

---

## File Map

### Files renamed (directory moves)
| From | To |
|---|---|
| `crates/core/src/metrics/` | `crates/core/src/on_time_performance/` |
| `crates/core/src/speed/` | `crates/core/src/speed_analysis/` |
| `crates/core/src/frequency/` | `crates/core/src/service_frequency/` |
| `crates/worker/src/gtfs/` | `crates/worker/src/feed_ingestion/` |

### Files modified
| File | What changes |
|---|---|
| `crates/core/src/lib.rs` | `pub mod` declarations updated |
| `crates/core/src/speed_analysis/card.rs` | Internal `crate::speed::` → `crate::speed_analysis::` |
| `crates/core/src/speed_analysis/detail.rs` | Internal `crate::speed::` → `crate::speed_analysis::` |
| `crates/server/src/web/handlers.rs` | All three `mobilispect_core::` import paths updated |
| `crates/worker/src/main.rs` | `mod gtfs` → `mod feed_ingestion`, all call sites |
| `crates/worker/src/pipeline.rs` | `use mobilispect_core::speed` → `use mobilispect_core::speed_analysis` |
| `CLAUDE.md` | Module structure diagram updated |

---

## Task 1: Rename core domain directories and fix internal references

**Files:**
- Move: `crates/core/src/metrics/` → `crates/core/src/on_time_performance/`
- Move: `crates/core/src/speed/` → `crates/core/src/speed_analysis/`
- Move: `crates/core/src/frequency/` → `crates/core/src/service_frequency/`
- Modify: `crates/core/src/lib.rs`
- Modify: `crates/core/src/speed_analysis/card.rs` (after move)
- Modify: `crates/core/src/speed_analysis/detail.rs` (after move)

- [ ] **Step 1: Move the three directories**

```bash
mv crates/core/src/metrics crates/core/src/on_time_performance
mv crates/core/src/speed crates/core/src/speed_analysis
mv crates/core/src/frequency crates/core/src/service_frequency
```

- [ ] **Step 2: Update `crates/core/src/lib.rs`**

Replace the entire file with:

```rust
pub mod ids;
pub use ids::{AgencyId, DirectionId, RouteId, ServiceId, StopId, TripId, VariantId, VehicleId};

pub mod config;
pub mod db;
pub mod on_time_performance;
pub mod service_frequency;
pub mod speed_analysis;
```

- [ ] **Step 3: Fix internal reference in `crates/core/src/speed_analysis/card.rs`**

Line 2 currently reads:
```rust
use crate::speed::RouteSpeedDayType;
```

Change it to:
```rust
use crate::speed_analysis::RouteSpeedDayType;
```

- [ ] **Step 4: Fix internal reference in `crates/core/src/speed_analysis/detail.rs`**

Line 2 currently reads:
```rust
use crate::speed::{DirectionStopSpacings, StopSpacing, VariantSpeedTrend};
```

Change it to:
```rust
use crate::speed_analysis::{DirectionStopSpacings, StopSpacing, VariantSpeedTrend};
```

- [ ] **Step 5: Verify core compiles**

```bash
cargo build -p mobilispect-core
```

Expected: no errors. If you see `use of undeclared crate or module`, check that all three `pub mod` names in `lib.rs` match the directory names exactly.

- [ ] **Step 6: Commit**

```bash
git add crates/core/src/
git commit -m "refactor: rename core domain modules to scream intent

metrics/ → on_time_performance/
speed/ → speed_analysis/
frequency/ → service_frequency/"
```

---

## Task 2: Update server import paths

**Files:**
- Modify: `crates/server/src/web/handlers.rs` (lines 11–19)

- [ ] **Step 1: Update the three import lines in handlers.rs**

Lines 11–19 currently read:
```rust
use mobilispect_core::frequency::{RouteHeadwayRow, route_headways};
use mobilispect_core::ids::{AgencyId, RouteId};
use mobilispect_core::metrics::{RouteSummary, RouteTrend, route_summary, route_trend};
use mobilispect_core::speed::{
    RouteClass, RouteSpeedCard, RouteSpeedDetailDirection, RouteSpeedSummary, assign_indices,
    build_detail_directions, build_speed_cards, classify_by_spacing, fetch_route_info,
    filter_speed_cards, route_speed_by_day_type, route_speed_summary, route_speed_trend_by_variant,
    route_stop_spacings, sort_speed_cards,
};
```

Replace with:
```rust
use mobilispect_core::service_frequency::{RouteHeadwayRow, route_headways};
use mobilispect_core::ids::{AgencyId, RouteId};
use mobilispect_core::on_time_performance::{RouteSummary, RouteTrend, route_summary, route_trend};
use mobilispect_core::speed_analysis::{
    RouteClass, RouteSpeedCard, RouteSpeedDetailDirection, RouteSpeedSummary, assign_indices,
    build_detail_directions, build_speed_cards, classify_by_spacing, fetch_route_info,
    filter_speed_cards, route_speed_by_day_type, route_speed_summary, route_speed_trend_by_variant,
    route_stop_spacings, sort_speed_cards,
};
```

- [ ] **Step 2: Verify server compiles**

```bash
cargo build -p mobilispect-server
```

Expected: no errors. Any `unresolved import` pointing at `mobilispect_core::metrics`, `::speed`, or `::frequency` means a stale reference was missed — grep for them:

```bash
grep -r "mobilispect_core::metrics\|mobilispect_core::speed\b\|mobilispect_core::frequency" crates/server/
```

- [ ] **Step 3: Commit**

```bash
git add crates/server/src/web/handlers.rs
git commit -m "refactor: update server imports to renamed core modules"
```

---

## Task 3: Rename worker feed ingestion and update worker imports

**Files:**
- Move: `crates/worker/src/gtfs/` → `crates/worker/src/feed_ingestion/`
- Modify: `crates/worker/src/main.rs`
- Modify: `crates/worker/src/pipeline.rs`

- [ ] **Step 1: Move the directory**

```bash
mv crates/worker/src/gtfs crates/worker/src/feed_ingestion
```

- [ ] **Step 2: Update `crates/worker/src/main.rs`**

Line 7 currently reads:
```rust
mod gtfs;
```

Change to:
```rust
mod feed_ingestion;
```

Line 33 currently reads:
```rust
gtfs::static_feed::load_if_needed(&db, &agency).await?;
```

Change to:
```rust
feed_ingestion::static_feed::load_if_needed(&db, &agency).await?;
```

Line 60 currently reads:
```rust
gtfs::realtime::poll_loop(&db_rt, &agency, poll_interval).await;
```

Change to:
```rust
feed_ingestion::realtime::poll_loop(&db_rt, &agency, poll_interval).await;
```

- [ ] **Step 3: Update `crates/worker/src/pipeline.rs`**

Line 4 currently reads:
```rust
use mobilispect_core::speed;
```

Change to:
```rust
use mobilispect_core::speed_analysis;
```

Line 7 currently reads:
```rust
    speed::on_static_loaded(db, agency).await?;
```

Change to:
```rust
    speed_analysis::on_static_loaded(db, agency).await?;
```

Line 12 currently reads:
```rust
    speed::on_realtime_polled(db, agency).await?;
```

Change to:
```rust
    speed_analysis::on_realtime_polled(db, agency).await?;
```

- [ ] **Step 4: Verify worker compiles**

```bash
cargo build -p mobilispect-worker
```

Expected: no errors. If you see `unresolved module gtfs`, confirm the directory was renamed correctly:

```bash
ls crates/worker/src/
```

Expected output includes `feed_ingestion/` and does NOT include `gtfs/`.

- [ ] **Step 5: Commit**

```bash
git add crates/worker/src/
git commit -m "refactor: rename worker gtfs/ to feed_ingestion/"
```

---

## Task 4: Full verification and update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md` (Structure section)

- [ ] **Step 1: Run full test suite**

```bash
cargo test
```

Expected: all tests pass. If integration tests spin up Postgres containers they may take ~30s. Any failure here indicates a missed import path — check the error message for the module name.

- [ ] **Step 2: Run clippy**

```bash
cargo clippy
```

Expected: no new warnings. Ignore pre-existing warnings if any.

- [ ] **Step 3: Update CLAUDE.md structure diagram**

In `CLAUDE.md`, find the Structure section. The `crates/core/src/` tree currently shows:

```
      metrics/     # Query functions (on-time %, delays, hotspots, scorecard)
      speed/
        mod.rs     # Scheduled/actual speed computation
        card.rs    # RouteSpeedCard builder
      frequency/   # Headway computation
```

Replace with:

```
      on_time_performance/  # On-time %, delay classification, route trends
        on_time.rs          # classify_trip_delays, compute_route_daily
        route_summary.rs    # RouteSummary query
        trend.rs            # RouteTrend, route_trend query
      speed_analysis/       # Scheduled/actual speed computation
        mod.rs              # Speed queries and computation hooks
        card.rs             # RouteSpeedCard builder
        detail.rs           # Per-direction stop spacing detail
      service_frequency/    # Headway / frequency computation
```

Also update the `crates/worker/src/` tree. Currently shows:

```
      gtfs/
        static_feed.rs # GTFS zip download + upsert
        realtime.rs    # GTFS-RT protobuf polling
```

Replace with:

```
      feed_ingestion/
        static_feed.rs # GTFS zip download + upsert
        realtime.rs    # GTFS-RT protobuf polling
```

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md structure for screaming architecture rename"
```

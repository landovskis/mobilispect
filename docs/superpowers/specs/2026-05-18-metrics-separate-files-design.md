# Design: Separate File Per Metric

**Date:** 2026-05-18
**Status:** Approved

## Problem

`crates/core/src/metrics/mod.rs` is a single 1278-line file containing five distinct metric concerns. Each concern has its own types, DB query functions, display helpers, and tests. The file has grown large enough that navigating and modifying individual metrics requires scrolling past unrelated code.

## Goal

Split `metrics/mod.rs` into one file per metric concern. No change to the public API — all callers continue using `mobilispect_core::metrics::*` paths unchanged.

## File Layout

```
crates/core/src/metrics/
  mod.rs            ← thin: mod declarations + pub use X::* for each sub-module
  on_time.rs        ← TripResult, classify_trip_delays, compute_route_daily
  route_summary.rs  ← RouteSummary, route_summary
  hotspots.rs       ← StopHotspot, stop_hotspots
  trend.rs          ← DailyTrendPoint, RouteTrend, route_trend
  scorecard.rs      ← Benchmark, ScorecardRoute, load_benchmarks, scorecard_routes
```

### mod.rs (after)

```rust
mod on_time;
mod route_summary;
mod hotspots;
mod trend;
mod scorecard;

pub use on_time::*;
pub use route_summary::*;
pub use hotspots::*;
pub use trend::*;
pub use scorecard::*;
```

## Content Assignment

| File | Types | Functions | Tests |
|------|-------|-----------|-------|
| `on_time.rs` | `TripResult` | `classify_trip_delays`, `compute_route_daily` | classify_trip_delays unit tests, compute_route_daily integration tests |
| `route_summary.rs` | `RouteSummary` | `route_summary` | route_summary filter tests |
| `hotspots.rs` | `StopHotspot` | `stop_hotspots` | (none currently) |
| `trend.rs` | `DailyTrendPoint`, `RouteTrend` | `route_trend` | route_trend integration tests, speed_change_pct unit tests |
| `scorecard.rs` | `Benchmark`, `ScorecardRoute` | `load_benchmarks`, `scorecard_routes` | scorecard unit + integration tests |

Each file has its own `use` block — no shared imports between sub-modules.

## Public API Stability

The `pub use X::*` re-exports in `mod.rs` mean the following existing imports remain valid without any change:

```rust
// handlers.rs
use mobilispect_core::metrics::{
    Benchmark, RouteSummary, RouteTrend, ScorecardRoute, StopHotspot,
    load_benchmarks, route_summary, route_trend, scorecard_routes, stop_hotspots,
};

// maintenance/mod.rs
use mobilispect_core::metrics::compute_route_daily;
```

## Testing

No new tests are needed — this is a pure file reorganisation. All existing tests move with their related code. After the split, `cargo test` must pass with no failures.

## Implementation Order

1. Create `on_time.rs` — move `TripResult`, `classify_trip_delays`, `compute_route_daily` + their tests
2. Create `route_summary.rs` — move `RouteSummary`, `route_summary` + tests
3. Create `hotspots.rs` — move `StopHotspot`, `stop_hotspots`
4. Create `trend.rs` — move `DailyTrendPoint`, `RouteTrend`, `route_trend` + tests
5. Create `scorecard.rs` — move `Benchmark`, `ScorecardRoute`, `load_benchmarks`, `scorecard_routes` + tests
6. Replace `mod.rs` content with the thin re-export layer
7. Verify `cargo test` passes

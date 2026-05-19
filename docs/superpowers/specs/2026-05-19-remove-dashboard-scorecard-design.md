# Remove Dashboard and Scorecard Features

## Summary

Delete the dashboard (dead handler + `/report` page) and scorecard (`/scorecard` page) features entirely. No replacement. Both silently swallowed DB errors, the scorecard used hardcoded Helsinki/Tokyo benchmark fallbacks that masked missing data, and the dashboard handler was already dead code with no route.

## Scope

**Delete:**
- `crates/core/src/metrics/scorecard.rs` — `ScorecardRoute`, `Benchmark`, `load_benchmarks`, `scorecard_routes` are only used by the scorecard handler
- `crates/server/templates/dashboard.html`
- `crates/server/templates/report.html`
- `crates/server/templates/scorecard.html`

**Edit:**
- `crates/core/src/metrics/mod.rs` — remove `mod scorecard;` and `pub use scorecard::*;`
- `crates/server/src/web/mod.rs` — remove `.route("/report", ...)` and `.route("/scorecard", ...)`
- `crates/server/src/web/handlers.rs`:
  - Remove `DashboardTemplate` struct and `dashboard` function (dead code — no route)
  - Remove `ReportTemplate` struct and `report` function
  - Remove `ScorecardTemplate` struct and `scorecard` function
  - Remove `Benchmark`, `ScorecardRoute`, `load_benchmarks`, `scorecard_routes` from imports

## What stays

- `crates/core/src/metrics/route_summary.rs` — still used by `/api/routes`
- `crates/core/src/metrics/trend.rs` — still used by `route_detail`
- `RouteSummary`, `route_summary`, `RouteTrend`, `route_trend` imports in handlers.rs

## Out of scope

- No migrations — scorecard queries existing `route_daily` and `benchmarks` tables shared with other features; the tables themselves are retained

## Verification

`cargo build` must pass with no references to `DashboardTemplate`, `ReportTemplate`, `ScorecardTemplate`, `dashboard`, `report`, `scorecard` (handler functions), `ScorecardRoute`, `Benchmark`, `load_benchmarks`, `scorecard_routes` remaining.

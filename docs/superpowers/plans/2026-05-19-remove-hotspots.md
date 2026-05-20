# Remove Hotspots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete the stop delay hotspots feature and all its entry points.

**Architecture:** Pure deletion — no new code, no migrations. Remove the core module, the route, the handler, and the template.

**Tech Stack:** Rust, Axum, Askama

---

### Task 1: Remove the core module

**Files:**
- Delete: `crates/core/src/metrics/hotspots.rs`
- Modify: `crates/core/src/metrics/mod.rs`

- [ ] **Step 1: Delete the hotspots module file**

```bash
rm crates/core/src/metrics/hotspots.rs
```

- [ ] **Step 2: Remove the module declaration and re-export from metrics/mod.rs**

Current `crates/core/src/metrics/mod.rs`:
```rust
mod on_time;
pub use on_time::*;

mod route_summary;
pub use route_summary::*;

mod hotspots;
pub use hotspots::*;

mod trend;
pub use trend::*;

mod scorecard;
pub use scorecard::*;
```

After edit:
```rust
mod on_time;
pub use on_time::*;

mod route_summary;
pub use route_summary::*;

mod trend;
pub use trend::*;

mod scorecard;
pub use scorecard::*;
```

- [ ] **Step 3: Verify core compiles**

```bash
cargo build -p mobilispect-core
```

Expected: Compiles successfully with no errors.

- [ ] **Step 4: Commit**

```bash
git add crates/core/src/metrics/mod.rs
git rm crates/core/src/metrics/hotspots.rs
git commit -m "feat(metrics): remove hotspots module"
```

---

### Task 2: Remove the server route, handler, and template

**Files:**
- Modify: `crates/server/src/web/mod.rs`
- Modify: `crates/server/src/web/handlers.rs`
- Delete: `crates/server/templates/hotspots.html`

- [ ] **Step 1: Remove the `/hotspots` route from the router**

In `crates/server/src/web/mod.rs`, remove this line:
```rust
        .route("/hotspots", get(handlers::hotspots))
```

The `build_router` function should look like:
```rust
pub fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/", get(handlers::speed_page))
        .route("/report", get(handlers::report))
        .route("/speed", get(handlers::speed_page))
        .route("/scorecard", get(handlers::scorecard))
        .route("/frequency", get(handlers::frequency_page))
        // /speed route registered BEFORE bare :route_id to avoid shadowing
        .route(
            "/routes/:agency_id/:route_id/speed",
            get(handlers::route_speed_detail),
        )
        .route("/routes/:agency_id/:route_id", get(handlers::route_detail))
        .route("/api/routes", get(handlers::api_routes))
        .route("/api/routes/speed", get(handlers::api_route_speed))
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}
```

- [ ] **Step 2: Remove the HotspotsTemplate struct and hotspots handler from handlers.rs**

Remove these lines from `crates/server/src/web/handlers.rs` (lines 207–236):
```rust
#[derive(Template)]
#[template(path = "hotspots.html")]
struct HotspotsTemplate {
    region_name: String,
    hotspots: Vec<StopHotspot>,
    hotspots_json: String,
    period_days: i64,
}

pub async fn hotspots(State(state): State<AppState>) -> Html<String> {
    let period_days: i64 = 7;
    // All monitored agencies share the same UTC offset (Montreal area), so using the
    // first agency's offset for time-bucketing is correct in practice. If agencies
    // from different timezones are ever added, this should be revisited.
    let agency = &state.config.agencies[0];
    let hotspots = stop_hotspots(&state.db, agency, period_days, 100)
        .await
        .unwrap_or_default();
    let hotspots_json = serde_json::to_string(&hotspots).unwrap_or_default();
    let tmpl = HotspotsTemplate {
        region_name: state.config.region.name.clone(),
        hotspots,
        hotspots_json,
        period_days,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
}
```

- [ ] **Step 3: Remove StopHotspot and stop_hotspots from the metrics import in handlers.rs**

Current import (lines 12–15):
```rust
use mobilispect_core::metrics::{
    Benchmark, RouteSummary, RouteTrend, ScorecardRoute, StopHotspot, load_benchmarks,
    route_summary, route_trend, scorecard_routes, stop_hotspots,
};
```

After edit:
```rust
use mobilispect_core::metrics::{
    Benchmark, RouteSummary, RouteTrend, ScorecardRoute, load_benchmarks,
    route_summary, route_trend, scorecard_routes,
};
```

- [ ] **Step 4: Delete the template**

```bash
rm crates/server/templates/hotspots.html
```

- [ ] **Step 5: Verify full workspace builds**

```bash
cargo build
```

Expected: Compiles successfully with no errors. No references to `StopHotspot`, `stop_hotspots`, or `hotspots.html` should remain.

- [ ] **Step 6: Verify no stray references**

```bash
grep -r "hotspot" crates/ --include="*.rs" --include="*.html"
```

Expected: No output.

- [ ] **Step 7: Commit**

```bash
git add crates/server/src/web/mod.rs crates/server/src/web/handlers.rs
git rm crates/server/templates/hotspots.html
git commit -m "feat(server): remove hotspots route, handler, and template"
```

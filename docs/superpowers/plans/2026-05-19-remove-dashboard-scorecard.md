# Remove Dashboard and Scorecard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete the dashboard (dead handler + `/report` page) and scorecard (`/scorecard` page) and their supporting core module.

**Architecture:** Pure deletion — no new code, no migrations. Remove the core scorecard module, the two routes, three handlers/templates, and their dead-code companion.

**Tech Stack:** Rust, Axum, Askama

---

### Task 1: Remove the scorecard core module

**Files:**
- Delete: `crates/core/src/metrics/scorecard.rs`
- Modify: `crates/core/src/metrics/mod.rs`

- [ ] **Step 1: Delete the scorecard module file**

```bash
rm crates/core/src/metrics/scorecard.rs
```

- [ ] **Step 2: Remove the module declaration and re-export from metrics/mod.rs**

Current `crates/core/src/metrics/mod.rs`:
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

After edit:
```rust
mod on_time;
pub use on_time::*;

mod route_summary;
pub use route_summary::*;

mod trend;
pub use trend::*;
```

- [ ] **Step 3: Verify core compiles**

```bash
cargo build -p mobilispect-core
```

Expected: Compiles successfully. (The server crate will not compile yet — that is expected.)

- [ ] **Step 4: Commit**

```bash
git add crates/core/src/metrics/mod.rs
git rm crates/core/src/metrics/scorecard.rs
git commit -m "feat(metrics): remove scorecard module"
```

---

### Task 2: Remove routes from the router

**Files:**
- Modify: `crates/server/src/web/mod.rs`

- [ ] **Step 1: Remove the `/report` and `/scorecard` routes**

Current `build_router` in `crates/server/src/web/mod.rs`:
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

After edit:
```rust
pub fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/", get(handlers::speed_page))
        .route("/speed", get(handlers::speed_page))
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

- [ ] **Step 2: Commit**

```bash
git add crates/server/src/web/mod.rs
git commit -m "feat(server): remove /report and /scorecard routes"
```

---

### Task 3: Remove handlers, template structs, imports, and templates

**Files:**
- Modify: `crates/server/src/web/handlers.rs`
- Delete: `crates/server/templates/dashboard.html`
- Delete: `crates/server/templates/report.html`
- Delete: `crates/server/templates/scorecard.html`

- [ ] **Step 1: Remove Benchmark, ScorecardRoute, load_benchmarks, scorecard_routes from the metrics import**

Current import (lines 12–15 of `crates/server/src/web/handlers.rs`):
```rust
use mobilispect_core::metrics::{
    Benchmark, RouteSummary, RouteTrend, ScorecardRoute, StopHotspot, load_benchmarks,
    route_summary, route_trend, scorecard_routes, stop_hotspots,
};
```

After edit (also removing hotspot items per the parallel hotspots removal plan):
```rust
use mobilispect_core::metrics::{
    RouteSummary, RouteTrend, load_benchmarks, route_summary, route_trend,
};
```

Wait — `load_benchmarks` is only used by `scorecard`. After the scorecard handler is removed it becomes unused. Remove it too:

```rust
use mobilispect_core::metrics::{RouteSummary, RouteTrend, route_summary, route_trend};
```

- [ ] **Step 2: Remove DashboardTemplate, dashboard handler**

Remove the following block from `crates/server/src/web/handlers.rs` (the dead-code dashboard handler):

```rust
#[derive(Template)]
#[template(path = "dashboard.html")]
struct DashboardTemplate {
    region_name: String,
    routes: Vec<RouteSummary>,
    period_days: i64,
    agencies: Vec<(String, String)>,
    agency_names: std::collections::HashMap<String, String>,
    active_agency: String,
}

pub async fn dashboard(
    State(state): State<AppState>,
    Query(params): Query<AgencyFilterParams>,
) -> Html<String> {
    let period_days: i64 = 7;
    let active_agency = params.agency.unwrap_or_default();
    let agency_filter: Option<AgencyId> = if active_agency.is_empty() {
        None
    } else {
        Some(AgencyId::from(active_agency.clone()))
    };
    let routes = route_summary(&state.db, period_days, agency_filter.as_ref())
        .await
        .unwrap_or_default();
    let agencies: Vec<(String, String)> = state
        .config
        .agencies
        .iter()
        .map(|a| (a.id.to_string(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();
    let tmpl = DashboardTemplate {
        region_name: state.config.region.name.clone(),
        routes,
        period_days,
        agencies,
        agency_names,
        active_agency,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
}
```

- [ ] **Step 3: Remove ReportTemplate, report handler**

Remove the following block from `crates/server/src/web/handlers.rs`:

```rust
#[derive(Template)]
#[template(path = "report.html")]
struct ReportTemplate {
    region_name: String,
    routes: Vec<RouteSummary>,
    period_days: i64,
    generated_at: String,
    agency_names: std::collections::HashMap<String, String>,
}

pub async fn report(State(state): State<AppState>) -> Html<String> {
    let period_days: i64 = 7;
    let routes = route_summary(&state.db, period_days, None)
        .await
        .unwrap_or_default();
    let generated_at = Utc::now().format("%Y-%m-%d %H:%M UTC").to_string();
    let agency_names: std::collections::HashMap<String, String> = state
        .config
        .agencies
        .iter()
        .map(|a| (a.id.to_string(), a.name.clone()))
        .collect();
    let tmpl = ReportTemplate {
        region_name: state.config.region.name.clone(),
        routes,
        period_days,
        generated_at,
        agency_names,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
}
```

- [ ] **Step 4: Remove ScorecardTemplate, scorecard handler**

Remove the following block from `crates/server/src/web/handlers.rs` (lines 393–487):

```rust
#[derive(Template)]
#[template(path = "scorecard.html")]
struct ScorecardTemplate {
    region_name: String,
    routes: Vec<ScorecardRoute>,
    benchmarks: Vec<Benchmark>,
    floor_pct: f64,
    ceiling_pct: f64,
    floor_city: String,
    ceiling_city: String,
    routes_meeting_floor: usize,
    worst_gap: Option<f64>,
    period_days: i64,
    generated_at: String,
    agencies: Vec<(String, String)>,
    agency_names: std::collections::HashMap<String, String>,
    active_agency: String,
}

pub async fn scorecard(
    State(state): State<AppState>,
    Query(params): Query<AgencyFilterParams>,
) -> Html<String> {
    let period_days: i64 = 7;
    let active_agency = params.agency.unwrap_or_default();
    let agency_filter: Option<AgencyId> = if active_agency.is_empty() {
        None
    } else {
        Some(AgencyId::from(active_agency.clone()))
    };
    let benchmarks = load_benchmarks(&state.db).await.unwrap_or_default();
    let routes = scorecard_routes(&state.db, period_days, agency_filter.as_ref())
        .await
        .unwrap_or_default();

    let floor_pct = benchmarks.first().map(|b| b.on_time_pct).unwrap_or(89.0);
    let floor_speed = benchmarks
        .first()
        .map(|b| b.speed_vs_scheduled_pct)
        .unwrap_or(3.0);
    let ceiling_pct = benchmarks.last().map(|b| b.on_time_pct).unwrap_or(96.0);
    let floor_city = benchmarks
        .first()
        .map(|b| b.city.clone())
        .unwrap_or_else(|| "Helsinki".to_string());
    let ceiling_city = benchmarks
        .last()
        .map(|b| b.city.clone())
        .unwrap_or_else(|| "Tokyo".to_string());

    let routes_meeting_floor = routes
        .iter()
        .filter(|r| {
            r.avg_on_time_pct.map_or(false, |p| p >= floor_pct)
                && r.speed_vs_scheduled_pct.map_or(true, |s| s <= floor_speed)
        })
        .count();

    let worst_gap = routes
        .iter()
        .filter_map(|r| r.on_time_gap_vs(floor_pct))
        .reduce(f64::min);

    let generated_at = Utc::now().format("%Y-%m-%d %H:%M UTC").to_string();

    let agencies: Vec<(String, String)> = state
        .config
        .agencies
        .iter()
        .map(|a| (a.id.to_string(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();

    let tmpl = ScorecardTemplate {
        region_name: state.config.region.name.clone(),
        routes,
        benchmarks,
        floor_pct,
        ceiling_pct,
        floor_city,
        ceiling_city,
        routes_meeting_floor,
        worst_gap,
        period_days,
        generated_at,
        agencies,
        agency_names,
        active_agency,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
}
```

- [ ] **Step 5: Delete the three template files**

```bash
git rm crates/server/templates/dashboard.html
git rm crates/server/templates/report.html
git rm crates/server/templates/scorecard.html
```

- [ ] **Step 6: Verify the full workspace compiles**

```bash
cargo build
```

Expected: Compiles with no errors.

- [ ] **Step 7: Verify no stray references remain**

```bash
grep -rn "DashboardTemplate\|ReportTemplate\|ScorecardTemplate\|scorecard_routes\|load_benchmarks\|ScorecardRoute\|Benchmark" crates/ --include="*.rs"
```

Expected: No output.

- [ ] **Step 8: Commit**

```bash
git add crates/server/src/web/handlers.rs
git commit -m "feat(server): remove dashboard, report, and scorecard handlers and templates"
```

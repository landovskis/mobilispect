# Scorecard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `/scorecard` page that benchmarks each route against Tokyo, Singapore, Zurich, and Helsinki on on-time % and speed vs scheduled.

**Architecture:** New `benchmarks` table seeded via migration. New `Benchmark` + `ScorecardRoute` types and two read-only query functions in `src/metrics/mod.rs`. New handler in `src/web/handlers.rs`. New Askama template `templates/scorecard.html`. No new writes at runtime.

**Tech Stack:** Rust, SQLite (sqlx), Askama templates, same stack as all other pages.

---

## File Map

| Action | File | What changes |
|---|---|---|
| Create | `migrations/005_benchmarks.sql` | New table + 4 seed rows |
| Modify | `src/metrics/mod.rs` | Add `Benchmark`, `ScorecardRoute`, `load_benchmarks()`, `scorecard_routes()` |
| Modify | `src/web/handlers.rs` | Add `scorecard` handler and `ScorecardTemplate` |
| Modify | `src/web/mod.rs` | Register `/scorecard` route |
| Create | `templates/scorecard.html` | Full scorecard page |
| Modify | `templates/dashboard.html` | Add "Scorecard ↗" to nav |
| Modify | `templates/speed.html` | Add "Scorecard ↗" to nav |
| Modify | `templates/hotspots.html` | Add "Scorecard ↗" to nav |
| Modify | `templates/report.html` | Add "Scorecard ↗" to nav |

---

## Task 1: Migration — benchmarks table

**Files:**
- Create: `migrations/005_benchmarks.sql`

- [ ] **Step 1: Create the migration file**

```sql
-- migrations/005_benchmarks.sql

CREATE TABLE IF NOT EXISTS benchmarks (
    id                     INTEGER PRIMARY KEY,
    system_name            TEXT NOT NULL,
    city                   TEXT NOT NULL,
    on_time_pct            REAL NOT NULL,
    speed_vs_scheduled_pct REAL NOT NULL,  -- deficit: positive = slower than schedule
    source_url             TEXT NOT NULL,
    year                   INTEGER NOT NULL
);

INSERT INTO benchmarks (system_name, city, on_time_pct, speed_vs_scheduled_pct, source_url, year) VALUES
  ('Helsinki (HSL)',         'Helsinki',  89.0, 3.0, 'https://www.hsl.fi/en/hsl/statistics-and-research', 2023),
  ('Zurich (ZVV)',           'Zurich',    92.0, 1.8, 'https://www.zvv.ch/zvv/en/about-zvv/facts-and-figures.html', 2023),
  ('Singapore (SBS Transit)','Singapore', 92.0, 2.0, 'https://www.lta.gov.sg/content/ltagov/en/getting_around/public_transport/bus.html', 2023),
  ('Tokyo (Toei Bus)',       'Tokyo',     96.0, 1.5, 'https://www.kotsu.metro.tokyo.jp/eng/services/bus.html', 2023);
```

- [ ] **Step 2: Verify the migration applies cleanly**

```bash
cargo test
```

Expected: all existing tests pass (the migration runs in `test_db()` via `db.migrate()`).

- [ ] **Step 3: Commit**

```bash
git add migrations/005_benchmarks.sql
git commit -m "feat: add benchmarks table seeded with global high-performer data"
```

---

## Task 2: Data types and queries

**Files:**
- Modify: `src/metrics/mod.rs`

Add two new structs and two new async functions. Append all new code after the closing `}` of the existing `route_summary` function (before the `#[cfg(test)]` block).

- [ ] **Step 1: Write the failing tests**

In `src/metrics/mod.rs`, inside `#[cfg(test)] mod tests { ... }`, add these tests after the existing ones:

```rust
    // ── Benchmark tests ──────────────────────────────────────────────────────

    #[tokio::test]
    async fn load_benchmarks_returns_all_seeded_rows() {
        let db = test_db().await;
        let benchmarks = load_benchmarks(&db).await.unwrap();
        assert_eq!(benchmarks.len(), 4);
        // Ordered ASC by on_time_pct: Helsinki first, Tokyo last.
        assert_eq!(benchmarks[0].city, "Helsinki");
        assert_eq!(benchmarks[3].city, "Tokyo");
        assert!((benchmarks[0].on_time_pct - 89.0).abs() < 0.01);
        assert!((benchmarks[3].on_time_pct - 96.0).abs() < 0.01);
    }

    // ── ScorecardRoute tests ──────────────────────────────────────────────────

    fn make_scorecard_route(on_time: Option<f64>, speed: Option<f64>) -> ScorecardRoute {
        ScorecardRoute {
            route_id: "R1".into(),
            short_name: "45".into(),
            long_name: "PAPINEAU".into(),
            avg_on_time_pct: on_time,
            speed_vs_scheduled_pct: speed,
        }
    }

    #[test]
    fn on_time_gap_vs_returns_positive_when_route_beats_benchmark() {
        let r = make_scorecard_route(Some(93.0), None);
        let gap = r.on_time_gap_vs(89.0).unwrap();
        assert!((gap - 4.0).abs() < 0.01);
    }

    #[test]
    fn on_time_gap_vs_returns_negative_when_route_below_benchmark() {
        let r = make_scorecard_route(Some(71.0), None);
        let gap = r.on_time_gap_vs(89.0).unwrap();
        assert!((gap - (-18.0)).abs() < 0.01);
    }

    #[test]
    fn on_time_gap_vs_returns_none_without_data() {
        let r = make_scorecard_route(None, None);
        assert!(r.on_time_gap_vs(89.0).is_none());
    }

    #[test]
    fn on_time_gap_display_positive_shows_plus_prefix() {
        let r = make_scorecard_route(Some(93.0), None);
        assert_eq!(r.on_time_gap_display(89.0), "+4pp");
    }

    #[test]
    fn on_time_gap_display_negative_shows_no_plus() {
        let r = make_scorecard_route(Some(71.0), None);
        assert_eq!(r.on_time_gap_display(89.0), "-18pp");
    }

    #[test]
    fn on_time_gap_display_no_data_shows_dash() {
        let r = make_scorecard_route(None, None);
        assert_eq!(r.on_time_gap_display(89.0), "—");
    }

    #[test]
    fn status_label_world_class_at_or_above_ceiling() {
        let r = make_scorecard_route(Some(96.0), None);
        assert_eq!(r.status_label(89.0, 96.0), "World class");
    }

    #[test]
    fn status_label_competitive_between_floor_and_ceiling() {
        let r = make_scorecard_route(Some(91.0), None);
        assert_eq!(r.status_label(89.0, 96.0), "Competitive");
    }

    #[test]
    fn status_label_below_all_under_floor() {
        let r = make_scorecard_route(Some(71.0), None);
        assert_eq!(r.status_label(89.0, 96.0), "Below all");
    }

    #[test]
    fn status_label_no_data_when_none() {
        let r = make_scorecard_route(None, None);
        assert_eq!(r.status_label(89.0, 96.0), "No data");
    }

    #[test]
    fn speed_display_shows_slower_when_positive_deficit() {
        let r = make_scorecard_route(None, Some(12.0));
        assert_eq!(r.speed_display(), "12% slower");
    }

    #[test]
    fn speed_display_shows_on_pace_within_one_pct() {
        let r = make_scorecard_route(None, Some(0.5));
        assert_eq!(r.speed_display(), "On pace");
    }

    #[test]
    fn speed_display_shows_faster_when_negative_deficit() {
        let r = make_scorecard_route(None, Some(-3.0));
        assert_eq!(r.speed_display(), "3% faster");
    }

    #[test]
    fn speed_display_dash_without_data() {
        let r = make_scorecard_route(None, None);
        assert_eq!(r.speed_display(), "—");
    }

    #[tokio::test]
    async fn scorecard_routes_returns_per_route_summary() {
        let db = test_db().await;
        sqlx::query!("INSERT INTO routes VALUES ('R1', '45', 'PAPINEAU', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query!(
            "INSERT INTO route_daily
             (route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('R1', date('now', '-1 day'), 72.5, 120.0, 45, 50, '2026-01-01T12:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let routes = scorecard_routes(&db).await.unwrap();

        assert_eq!(routes.len(), 1);
        assert_eq!(routes[0].route_id, "R1");
        assert_eq!(routes[0].short_name, "45");
        let pct = routes[0].avg_on_time_pct.unwrap();
        assert!((pct - 72.5).abs() < 0.1);
    }

    #[tokio::test]
    async fn scorecard_routes_includes_speed_deficit_when_available() {
        let db = test_db().await;
        sqlx::query!("INSERT INTO routes VALUES ('R1', '45', 'PAPINEAU', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query!(
            "INSERT INTO route_daily
             (route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('R1', date('now', '-1 day'), 72.5, 120.0, 45, 50, '2026-01-01T12:00:00Z')"
        ).execute(&db.pool).await.unwrap();
        // scheduled: 10.0 m/s, actual: 8.0 m/s → deficit = 20%
        sqlx::query!(
            "INSERT INTO route_speed VALUES ('R1', 0, 10.0, 5, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();
        sqlx::query!(
            "INSERT INTO route_speed_daily
             (route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('R1', date('now', '-1 day'), 0, 8.0, 5, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let routes = scorecard_routes(&db).await.unwrap();

        assert_eq!(routes.len(), 1);
        let deficit = routes[0].speed_vs_scheduled_pct.unwrap();
        assert!((deficit - 20.0).abs() < 0.5, "expected ~20%, got {deficit}");
    }

    #[tokio::test]
    async fn scorecard_routes_speed_is_none_when_no_speed_data() {
        let db = test_db().await;
        sqlx::query!("INSERT INTO routes VALUES ('R1', '45', 'PAPINEAU', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query!(
            "INSERT INTO route_daily
             (route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('R1', date('now', '-1 day'), 72.5, 120.0, 45, 50, '2026-01-01T12:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let routes = scorecard_routes(&db).await.unwrap();

        assert_eq!(routes.len(), 1);
        assert!(routes[0].speed_vs_scheduled_pct.is_none());
    }
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cargo test 2>&1 | grep -E "error|FAILED|load_benchmarks|scorecard"
```

Expected: compile errors — `load_benchmarks`, `ScorecardRoute`, `scorecard_routes` not defined.

- [ ] **Step 3: Add the structs and functions to `src/metrics/mod.rs`**

Insert the following block immediately before the `#[cfg(test)]` line:

```rust
/// A global reference transit system used for benchmarking.
#[derive(Debug, sqlx::FromRow, Serialize, Clone)]
pub struct Benchmark {
    pub id: i64,
    pub system_name: String,
    pub city: String,
    pub on_time_pct: f64,
    pub speed_vs_scheduled_pct: f64,
    pub source_url: String,
    pub year: i32,
}

/// Per-route data for the scorecard page.
#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct ScorecardRoute {
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub avg_on_time_pct: Option<f64>,
    /// Speed deficit: (scheduled − actual) / scheduled × 100. Positive = slower than schedule.
    pub speed_vs_scheduled_pct: Option<f64>,
}

impl ScorecardRoute {
    /// Percentage-point delta vs a benchmark's on-time %. Positive = better than benchmark.
    pub fn on_time_gap_vs(&self, benchmark_pct: f64) -> Option<f64> {
        Some(self.avg_on_time_pct? - benchmark_pct)
    }

    /// "+Xpp", "-Xpp", or "—".
    pub fn on_time_gap_display(&self, benchmark_pct: f64) -> String {
        match self.on_time_gap_vs(benchmark_pct) {
            None => "—".to_string(),
            Some(g) if g >= 0.0 => format!("+{g:.0}pp"),
            Some(g) => format!("{g:.0}pp"),
        }
    }

    /// CSS class for gap cell: "gap-pos" (green), "gap-neg" (red), or "".
    pub fn on_time_gap_class(&self, benchmark_pct: f64) -> &'static str {
        match self.on_time_gap_vs(benchmark_pct) {
            Some(g) if g >= 0.0 => "gap-pos",
            Some(_) => "gap-neg",
            None => "",
        }
    }

    /// "World class" / "Competitive" / "Below all" / "No data".
    /// floor_pct = Helsinki (89.0), ceiling_pct = Tokyo (96.0).
    pub fn status_label(&self, floor_pct: f64, ceiling_pct: f64) -> &'static str {
        match self.avg_on_time_pct {
            Some(p) if p >= ceiling_pct => "World class",
            Some(p) if p >= floor_pct => "Competitive",
            Some(_) => "Below all",
            None => "No data",
        }
    }

    /// CSS badge class: "green" / "yellow" / "red" / "none".
    pub fn status_class(&self, floor_pct: f64, ceiling_pct: f64) -> &'static str {
        match self.avg_on_time_pct {
            Some(p) if p >= ceiling_pct => "green",
            Some(p) if p >= floor_pct => "yellow",
            Some(_) => "red",
            None => "none",
        }
    }

    /// "X% slower" / "X% faster" / "On pace" / "—".
    pub fn speed_display(&self) -> String {
        match self.speed_vs_scheduled_pct {
            None => "—".to_string(),
            Some(d) if d > 1.0 => format!("{d:.0}% slower"),
            Some(d) if d < -1.0 => format!("{:.0}% faster", d.abs()),
            Some(_) => "On pace".to_string(),
        }
    }

    /// CSS class for speed cell: "slower" / "faster" / "onpace".
    pub fn speed_class(&self) -> &'static str {
        match self.speed_vs_scheduled_pct {
            Some(d) if d > 1.0 => "slower",
            Some(d) if d < -1.0 => "faster",
            _ => "onpace",
        }
    }

    /// On-time % as string, or "—".
    pub fn on_time_display(&self) -> String {
        match self.avg_on_time_pct {
            Some(p) => format!("{p:.1}%"),
            None => "—".to_string(),
        }
    }
}

/// Load all benchmarks ordered by on_time_pct ASC (weakest first, Helsinki → Tokyo).
pub async fn load_benchmarks(db: &Database) -> Result<Vec<Benchmark>> {
    let rows: Vec<Benchmark> = sqlx::query_as(
        "SELECT id, system_name, city, on_time_pct, speed_vs_scheduled_pct, source_url, year
         FROM benchmarks
         ORDER BY on_time_pct ASC",
    )
    .fetch_all(&db.pool)
    .await?;
    Ok(rows)
}

/// Fetch per-route scorecard data: on-time % and speed deficit averaged over last 7 days.
pub async fn scorecard_routes(db: &Database) -> Result<Vec<ScorecardRoute>> {
    let rows: Vec<ScorecardRoute> = sqlx::query_as(
        "SELECT
           ot.route_id,
           r.short_name,
           r.long_name,
           ot.avg_on_time_pct,
           sp.speed_vs_scheduled_pct
         FROM (
           SELECT route_id, ROUND(AVG(on_time_pct), 1) AS avg_on_time_pct
           FROM route_daily
           WHERE service_date >= DATE('now', '-7 days')
           GROUP BY route_id
         ) ot
         JOIN routes r ON r.route_id = ot.route_id
         LEFT JOIN (
           SELECT rs.route_id,
             ROUND(AVG(
               CASE WHEN rs.scheduled_speed_mps > 0 AND rsd.avg_actual IS NOT NULL
                 THEN (rs.scheduled_speed_mps - rsd.avg_actual) / rs.scheduled_speed_mps * 100.0
                 ELSE NULL END
             ), 1) AS speed_vs_scheduled_pct
           FROM route_speed rs
           LEFT JOIN (
             SELECT route_id, direction_id, AVG(actual_speed_mps) AS avg_actual
             FROM route_speed_daily
             WHERE service_date >= DATE('now', '-7 days')
             GROUP BY route_id, direction_id
           ) rsd ON rsd.route_id = rs.route_id AND rsd.direction_id = rs.direction_id
           GROUP BY rs.route_id
         ) sp ON sp.route_id = ot.route_id
         ORDER BY CAST(r.short_name AS INTEGER), r.short_name",
    )
    .fetch_all(&db.pool)
    .await?;
    Ok(rows)
}
```

- [ ] **Step 4: Run tests**

```bash
cargo test 2>&1 | grep -E "FAILED|ok|error\[" | head -40
```

Expected: all new tests pass. Zero compile errors.

- [ ] **Step 5: Commit**

```bash
git add src/metrics/mod.rs
git commit -m "feat: add Benchmark, ScorecardRoute types and scorecard_routes query"
```

---

## Task 3: Handler and route registration

**Files:**
- Modify: `src/web/handlers.rs`
- Modify: `src/web/mod.rs`

- [ ] **Step 1: Add the handler to `src/web/handlers.rs`**

Add the following after the `speed_page` function (around line 113):

```rust
#[derive(Template)]
#[template(path = "scorecard.html")]
struct ScorecardTemplate {
    routes: Vec<crate::metrics::ScorecardRoute>,
    benchmarks: Vec<crate::metrics::Benchmark>,
    floor_pct: f64,
    ceiling_pct: f64,
    routes_meeting_floor: usize,
    worst_gap: Option<f64>,
    period_days: i64,
    generated_at: String,
}

pub async fn scorecard(State(state): State<AppState>) -> Html<String> {
    let period_days: i64 = 7;
    let benchmarks = crate::metrics::load_benchmarks(&state.db).await.unwrap_or_default();
    let routes = crate::metrics::scorecard_routes(&state.db).await.unwrap_or_default();

    let floor_pct = benchmarks.first().map(|b| b.on_time_pct).unwrap_or(89.0);
    let floor_speed = benchmarks.first().map(|b| b.speed_vs_scheduled_pct).unwrap_or(3.0);
    let ceiling_pct = benchmarks.last().map(|b| b.on_time_pct).unwrap_or(96.0);

    let routes_meeting_floor = routes.iter().filter(|r| {
        r.avg_on_time_pct.map_or(false, |p| p >= floor_pct)
            && r.speed_vs_scheduled_pct.map_or(true, |s| s <= floor_speed)
    }).count();

    let worst_gap = routes.iter()
        .filter_map(|r| r.on_time_gap_vs(floor_pct))
        .reduce(f64::min);

    let generated_at = Utc::now().format("%Y-%m-%d %H:%M UTC").to_string();

    let tmpl = ScorecardTemplate {
        routes,
        benchmarks,
        floor_pct,
        ceiling_pct,
        routes_meeting_floor,
        worst_gap,
        period_days,
        generated_at,
    };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
}
```

Note: `load_benchmarks` and `scorecard_routes` are already imported via `crate::metrics` — no new `use` statement needed since we qualify them inline. Alternatively, add them to the existing `use crate::metrics::...` import at the top of the file:

Change line 7 from:
```rust
use crate::metrics::{compute_route_daily, route_summary, route_trend, stop_hotspots, RouteSummary, RouteTrend, StopHotspot};
```
to:
```rust
use crate::metrics::{compute_route_daily, load_benchmarks, route_summary, route_trend, scorecard_routes, stop_hotspots, Benchmark, RouteSummary, RouteTrend, ScorecardRoute, StopHotspot};
```

Then simplify the handler to use the imported names directly (remove the `crate::metrics::` qualifications above).

- [ ] **Step 2: Register the route in `src/web/mod.rs`**

In the `Router::new()` chain, add `.route("/scorecard", get(handlers::scorecard))` after the `/speed` route:

```rust
    let app = Router::new()
        .route("/", get(handlers::dashboard))
        .route("/report", get(handlers::report))
        .route("/speed", get(handlers::speed_page))
        .route("/scorecard", get(handlers::scorecard))
        .route("/routes/:route_id", get(handlers::route_detail))
        .route("/hotspots", get(handlers::hotspots))
        .route("/compute", get(handlers::compute))
        .route("/api/routes", get(handlers::api_routes))
        .route("/api/routes/speed", get(handlers::api_route_speed))
        .layer(TraceLayer::new_for_http())
        .with_state(state);
```

- [ ] **Step 3: Create a minimal placeholder template so the project compiles**

```html
<!-- templates/scorecard.html — placeholder until Task 4 -->
<!DOCTYPE html>
<html><body><p>Scorecard coming soon.</p></body></html>
```

- [ ] **Step 4: Build to confirm no compile errors**

```bash
cargo build 2>&1 | grep -E "^error"
```

Expected: no output (clean build).

- [ ] **Step 5: Commit**

```bash
git add src/web/handlers.rs src/web/mod.rs templates/scorecard.html
git commit -m "feat: add scorecard handler and /scorecard route"
```

---

## Task 4: Scorecard template

**Files:**
- Modify: `templates/scorecard.html` (replace placeholder)

- [ ] **Step 1: Replace `templates/scorecard.html` with the full template**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Mobilispect — Global Scorecard</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
           background: #f5f5f5; color: #222; }
    header { background: #1a1a2e; color: white; padding: 1rem 2rem;
             display: flex; justify-content: space-between; align-items: center; }
    header h1 { font-size: 1.4rem; font-weight: 600; }
    header a { color: #aaa; font-size: 0.85rem; text-decoration: none; }
    header a:hover { color: white; }
    .container { max-width: 1200px; margin: 2rem auto; padding: 0 1rem; }
    .summary-bar { display: flex; gap: 1rem; margin-bottom: 1.5rem; }
    .stat { background: white; border-radius: 8px; padding: 1rem 1.5rem;
            flex: 1; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
    .stat .label { font-size: 0.75rem; color: #888; text-transform: uppercase;
                   letter-spacing: 0.05em; margin-bottom: 0.25rem; }
    .stat .value { font-size: 1.8rem; font-weight: 700; }
    .note { background: white; border-radius: 8px; padding: 0.75rem 1rem;
            margin-bottom: 1.5rem; font-size: 0.85rem; color: #555;
            box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
    table { width: 100%; border-collapse: collapse; background: white;
            border-radius: 8px; overflow: hidden;
            box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
    thead { background: #f0f0f0; }
    th { padding: 0.75rem 1rem; text-align: left; font-size: 0.8rem;
         text-transform: uppercase; letter-spacing: 0.05em; color: #555; }
    td { padding: 0.75rem 1rem; border-top: 1px solid #eee; font-size: 0.9rem; }
    tr:hover td { background: #fafafa; }
    .route-id { font-family: monospace; font-weight: 600; font-size: 0.95rem; }
    .badge { display: inline-block; padding: 0.2rem 0.6rem; border-radius: 12px;
             font-size: 0.75rem; font-weight: 600; }
    .green  { background: #d4edda; color: #155724; }
    .yellow { background: #fff3cd; color: #856404; }
    .red    { background: #f8d7da; color: #721c24; }
    .none   { background: #e9ecef; color: #6c757d; }
    .gap-pos { color: #27ae60; font-weight: 600; }
    .gap-neg { color: #c0392b; font-weight: 600; }
    .slower  { color: #c0392b; }
    .faster  { color: #27ae60; }
    .onpace  { color: #555; }
    .bench-header { background: #e8f0fe; }
    .footnotes { margin-top: 2rem; font-size: 0.78rem; color: #888; }
    .footnotes a { color: #888; }
    footer { text-align: center; padding: 2rem; color: #aaa; font-size: 0.8rem; }
    @media print {
      header nav, footer { display: none; }
      body { background: white; }
      @page { margin: 1.5cm; }
    }
  </style>
</head>
<body>
  <header>
    <h1>Global Scorecard</h1>
    <nav style="display:flex;gap:1.5rem;">
      <a href="/">← Dashboard</a>
      <a href="/speed">Route speeds ↗</a>
      <a href="/hotspots">Delay hotspots ↗</a>
      <a href="/report">Print report ↗</a>
    </nav>
  </header>
  <div class="container">
    <div class="summary-bar">
      <div class="stat">
        <div class="label">Routes monitored</div>
        <div class="value">{{ routes.len() }}</div>
      </div>
      <div class="stat">
        <div class="label">Meeting Helsinki floor</div>
        <div class="value">{{ routes_meeting_floor }}</div>
      </div>
      <div class="stat">
        <div class="label">Worst gap vs Helsinki</div>
        <div class="value">
          {% if let Some(g) = worst_gap %}
            {% if g >= 0.0 %}+{{ g|round }}pp{% else %}{{ g|round }}pp{% endif %}
          {% else %}—{% endif %}
        </div>
      </div>
    </div>

    <div class="note">
      Columns show percentage-point gap between each route's on-time % and each world-class system.
      <strong>Helsinki (89%)</strong> is the floor — the weakest world-class benchmark.
      <strong>Tokyo (96%)</strong> is the ceiling.
      Period: last {{ period_days }} days · Generated {{ generated_at }}
    </div>

    <table>
      <thead>
        <tr>
          <th>Route</th>
          <th>Name</th>
          <th>On-time %</th>
          <th>Speed vs sched</th>
          {% for bench in benchmarks %}
          <th class="bench-header">vs {{ bench.city }}</th>
          {% endfor %}
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {% for route in routes %}
        <tr>
          <td><a href="/routes/{{ route.route_id }}" style="text-decoration:none;"><span class="route-id">{{ route.short_name }}</span></a></td>
          <td>{{ route.long_name }}</td>
          <td>{{ route.on_time_display() }}</td>
          <td><span class="{{ route.speed_class() }}">{{ route.speed_display() }}</span></td>
          {% for bench in benchmarks %}
          <td><span class="{{ route.on_time_gap_class(bench.on_time_pct) }}">{{ route.on_time_gap_display(bench.on_time_pct) }}</span></td>
          {% endfor %}
          <td><span class="badge {{ route.status_class(floor_pct, ceiling_pct) }}">{{ route.status_label(floor_pct, ceiling_pct) }}</span></td>
        </tr>
        {% endfor %}
      </tbody>
    </table>

    <div class="footnotes">
      <strong>Benchmark sources:</strong>
      {% for bench in benchmarks %}
        {{ bench.system_name }} · {{ bench.on_time_pct }}% on-time · {{ bench.year }} · <a href="{{ bench.source_url }}" target="_blank">source</a>{% if !loop.last %} &nbsp;·&nbsp; {% endif %}
      {% endfor %}
    </div>
  </div>
  <footer>Mobilispect · Last {{ period_days }} days · Data: STM GTFS-RT</footer>
</body>
</html>
```

- [ ] **Step 2: Build to confirm the template compiles**

```bash
cargo build 2>&1 | grep -E "^error"
```

Expected: no output.

- [ ] **Step 3: Smoke-test in browser**

```bash
cargo run
```

Open `http://localhost:3000/scorecard`. Verify:
- Summary bar shows 3 stats
- Table has columns: Route, Name, On-time %, Speed vs sched, vs Helsinki, vs Zurich, vs Singapore, vs Tokyo, Status
- Rows appear for all routes with data
- Gap values are colored (green = positive, red = negative)
- Status badges show correct labels

- [ ] **Step 4: Commit**

```bash
git add templates/scorecard.html
git commit -m "feat: implement scorecard template with global benchmark gap columns"
```

---

## Task 5: Add Scorecard nav link to existing templates

**Files:**
- Modify: `templates/dashboard.html`
- Modify: `templates/speed.html`
- Modify: `templates/hotspots.html`
- Modify: `templates/report.html`

- [ ] **Step 1: Add to `templates/dashboard.html`**

In the `<nav>` block, add `<a href="/scorecard">Scorecard ↗</a>`. The nav currently reads:

```html
    <nav style="display:flex;gap:1.5rem;">
      <a href="/speed">Route speeds ↗</a>
      <a href="/hotspots">Delay hotspots ↗</a>
      <a href="/report">Print report ↗</a>
    </nav>
```

Change to:

```html
    <nav style="display:flex;gap:1.5rem;">
      <a href="/speed">Route speeds ↗</a>
      <a href="/hotspots">Delay hotspots ↗</a>
      <a href="/scorecard">Scorecard ↗</a>
      <a href="/report">Print report ↗</a>
    </nav>
```

- [ ] **Step 2: Add to `templates/speed.html`**

Current nav:

```html
    <nav style="display:flex;gap:1.5rem;">
      <a href="/">← Dashboard</a>
      <a href="/hotspots">Delay hotspots ↗</a>
      <a href="/report">Print report ↗</a>
    </nav>
```

Change to:

```html
    <nav style="display:flex;gap:1.5rem;">
      <a href="/">← Dashboard</a>
      <a href="/hotspots">Delay hotspots ↗</a>
      <a href="/scorecard">Scorecard ↗</a>
      <a href="/report">Print report ↗</a>
    </nav>
```

- [ ] **Step 3: Add to `templates/hotspots.html`**

Current nav:

```html
      <a href="/">Dashboard</a>
      <a href="/report">Report</a>
```

Change to:

```html
      <a href="/">Dashboard</a>
      <a href="/scorecard">Scorecard</a>
      <a href="/report">Report</a>
```

- [ ] **Step 4: Add to `templates/report.html`**

The report template has no nav currently. Add a print-hidden nav bar after the `<body>` tag:

After `<body>`:
```html
  <p class="no-print" style="margin-bottom:1rem; font-family:sans-serif; font-size:0.85rem; color:#555;">
    Use your browser's Print function (Cmd+P / Ctrl+P) to save as PDF. &nbsp;·&nbsp;
    <a href="/scorecard">View global scorecard</a>
  </p>
```

(This replaces the existing `<p class="no-print" ...>` line — just add the scorecard link to it.)

The existing line reads:
```html
  <p class="no-print" style="margin-bottom:1rem; font-family:sans-serif; font-size:0.85rem; color:#555;">
    Use your browser's Print function (Cmd+P / Ctrl+P) to save as PDF.
  </p>
```

Change to:
```html
  <p class="no-print" style="margin-bottom:1rem; font-family:sans-serif; font-size:0.85rem; color:#555;">
    Use your browser's Print function (Cmd+P / Ctrl+P) to save as PDF. &nbsp;·&nbsp;
    <a href="/scorecard">View global scorecard</a>
  </p>
```

- [ ] **Step 5: Build and verify**

```bash
cargo build 2>&1 | grep -E "^error"
```

Expected: no output.

- [ ] **Step 6: Run all tests**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add templates/dashboard.html templates/speed.html templates/hotspots.html templates/report.html
git commit -m "feat: add Scorecard nav link to all pages"
```

---

## Self-Review Checklist

- [x] **Migration** — `005_benchmarks.sql` creates table and seeds 4 systems (Task 1)
- [x] **Benchmark struct** — loaded via `load_benchmarks()`, ordered ASC (Task 2)
- [x] **ScorecardRoute struct** — `on_time_gap_vs`, `on_time_gap_display`, `on_time_gap_class`, `status_label`, `status_class`, `speed_display`, `speed_class`, `on_time_display` (Task 2)
- [x] **scorecard_routes query** — on-time from `route_daily`, speed deficit from `route_speed` + `route_speed_daily`, last 7 days (Task 2)
- [x] **Handler** — computes floor/ceiling from benchmarks, routes_meeting_floor count, worst_gap (Task 3)
- [x] **Route registered** — `/scorecard` in `web/mod.rs` (Task 3)
- [x] **Template summary bar** — routes monitored, routes meeting floor, worst gap (Task 4)
- [x] **Template main table** — dynamic benchmark columns via loop, gap coloring, status badge, route link (Task 4)
- [x] **Template footnotes** — benchmark citations with source URLs (Task 4)
- [x] **Nav links** — all 4 existing templates updated (Task 5)
- [x] **No placeholders** — all code is complete and concrete
- [x] **Type consistency** — `ScorecardRoute` defined in Task 2, used identically in Tasks 3 and 4; `Benchmark` same

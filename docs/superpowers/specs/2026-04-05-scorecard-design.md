# Scorecard Feature Design

**Date:** 2026-04-05
**Status:** Approved

## Summary

A standalone `/scorecard` page that benchmarks each monitored route against global high-performing transit systems (Tokyo, Singapore, Zurich, Helsinki) on two metrics: on-time % and speed vs scheduled. Designed for advocacy organizations to produce citation-ready comparisons.

---

## Data Model

### New `benchmarks` table

```sql
CREATE TABLE benchmarks (
    id                    INTEGER PRIMARY KEY,
    system_name           TEXT NOT NULL,   -- e.g. "Tokyo (Toei Bus)"
    city                  TEXT NOT NULL,
    on_time_pct           REAL NOT NULL,
    speed_vs_scheduled_pct REAL NOT NULL,  -- deficit: positive = slower than schedule (matches RouteSpeedSummary.speed_deficit_pct)
    source_url            TEXT NOT NULL,
    year                  INTEGER NOT NULL
);
```

Seeded via migration with these four systems (weakest to strongest on on-time %):

| System | City | On-time % | Speed vs sched | Year |
|---|---|---|---|---|
| Helsinki (HSL) | Helsinki | 89.0 | 3.0 | 2023 |
| Zurich (ZVV) | Zurich | 92.0 | 1.8 | 2023 |
| Singapore (SBS Transit) | Singapore | 92.0 | 2.0 | 2023 |
| Tokyo (Toei Bus) | Tokyo | 96.0 | 1.5 | 2023 |

Helsinki is the "floor" — the weakest world-class benchmark. Routes beating Helsinki on both metrics are considered competitive.

No new columns needed on existing tables. Speed ratio is computed at query time from `route_speed_daily.actual_speed_mps` and the scheduled speed already available in `route_speed_daily`.

---

## Backend

### New struct: `ScorecardRoute` (in `src/metrics/mod.rs`)

```rust
pub struct ScorecardRoute {
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub avg_on_time_pct: Option<f64>,
    pub speed_vs_scheduled_pct: Option<f64>,  // deficit: (scheduled - actual) / scheduled * 100; positive = slower
}

impl ScorecardRoute {
    /// Delta vs a given benchmark's on-time %. Positive = better than benchmark.
    pub fn on_time_gap_vs(&self, benchmark_pct: f64) -> Option<f64>;

    /// "below-all", "competitive" (beats Helsinki), or "world-class" (beats Tokyo)
    pub fn status_class(&self, benchmarks: &[Benchmark]) -> &'static str;
    pub fn status_label(&self, benchmarks: &[Benchmark]) -> &'static str;
}
```

### New struct: `Benchmark` (in `src/metrics/mod.rs`)

```rust
#[derive(Debug, sqlx::FromRow, serde::Serialize)]
pub struct Benchmark {
    pub id: i64,
    pub system_name: String,
    pub city: String,
    pub on_time_pct: f64,
    pub speed_vs_scheduled_pct: f64,
    pub source_url: String,
    pub year: i32,
}
```

### New function: `scorecard_data` (in `src/metrics/mod.rs`)

Two queries:
1. Load all benchmarks: `SELECT * FROM benchmarks ORDER BY on_time_pct ASC`
2. Aggregate routes (last N days) joining `route_daily` + `route_speed_daily` to produce `ScorecardRoute` rows — same window as dashboard (7 days).

### New handler: `scorecard` (in `src/web/handlers.rs`)

`GET /scorecard` — loads benchmarks + route actuals, renders `scorecard.html`. No writes.

```rust
#[derive(Template)]
#[template(path = "scorecard.html")]
struct ScorecardTemplate {
    routes: Vec<ScorecardRoute>,
    benchmarks: Vec<Benchmark>,
    period_days: i64,
    generated_at: String,
}
```

Route added in `src/web/mod.rs` alongside existing routes.

---

## Frontend: `templates/scorecard.html`

### Summary bar (3 stats)

- Routes monitored
- Routes meeting Helsinki floor (on-time ≥ 89% **and** speed gap ≥ −3%)
- Worst gap (largest negative delta vs Helsinki on-time %)

### Main table

One row per route, sorted numerically by `short_name`:

| Route | Name | On-time % | Speed vs sched | vs Helsinki | vs Zurich | vs Singapore | vs Tokyo | Status |
|---|---|---|---|---|---|---|---|---|

- Gap columns (`vs X`): percentage-point delta on on-time only. `+Xpp` green, `−Xpp` red.
- Speed vs sched: single column, colored by magnitude (same classes as `speed.html`: `slower` / `faster` / `onpace`).
- Status badge: `Below all` (red) / `Competitive` (amber) / `World class` (green).
- Route name links to `/routes/{id}`.

### Footer

Lists each benchmark system with city, year, and a link to `source_url`. Example:
*Helsinki (HSL) · 89% on-time · 2023 · [source]*

### Navigation

"Scorecard ↗" added to the header nav on all existing pages (dashboard, speed, hotspots, report).

### Print

Standard browser print via Cmd+P — no separate print stylesheet needed (table is already print-legible).

---

## Migration

New migration file `migrations/005_benchmarks.sql`:

```sql
CREATE TABLE IF NOT EXISTS benchmarks (
    id                     INTEGER PRIMARY KEY,
    system_name            TEXT NOT NULL,
    city                   TEXT NOT NULL,
    on_time_pct            REAL NOT NULL,
    speed_vs_scheduled_pct REAL NOT NULL,
    source_url             TEXT NOT NULL,
    year                   INTEGER NOT NULL
);

INSERT INTO benchmarks (system_name, city, on_time_pct, speed_vs_scheduled_pct, source_url, year) VALUES
  ('Helsinki (HSL)',        'Helsinki',   89.0, 3.0, 'https://www.hsl.fi/en/hsl/statistics-and-research', 2023),
  ('Zurich (ZVV)',          'Zurich',     92.0, 1.8, 'https://www.zvv.ch/zvv/en/about-zvv/facts-and-figures.html', 2023),
  ('Singapore (SBS Transit)','Singapore', 92.0, 2.0, 'https://www.lta.gov.sg/content/ltagov/en/getting_around/public_transport/bus.html', 2023),
  ('Tokyo (Toei Bus)',      'Tokyo',      96.0, 1.5, 'https://www.kotsu.metro.tokyo.jp/eng/services/bus.html', 2023);
```

Applied automatically at startup via the existing `db.migrate()` call.

---

## Out of Scope

- Admin UI to edit benchmarks (update via SQL directly for now)
- Equity/neighbourhood layer
- Historical trend of gap over time
- CSV export

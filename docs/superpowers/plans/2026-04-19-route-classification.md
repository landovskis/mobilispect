# Route Classification by Stop Spacing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Classify each route as Slow Bus / Local Bus / Rapid / Express by average stop spacing, and display a colour-coded badge on the speed card and route speed detail page.

**Architecture:** Add a `RouteClass` enum and `classify_by_spacing` pure function to `src/speed/card.rs`; derive `classification: Option<RouteClass>` in `build_speed_cards` from the stop spacing already present on each `DirectionSpeedChart`. In the detail handler, compute the per-route mean from the already-fetched direction spacings and pass `classification` to the template. No new SQL queries required — both pages already have the stop spacing data they need.

**Tech Stack:** Rust 2024, Askama 0.15 templates, sqlx 0.8, Axum 0.7, testcontainers (integration tests).

---

## File Map

| File | Change |
|------|--------|
| `src/speed/card.rs` | Add `RouteClass` enum, `classify_by_spacing`, `RouteSpeedCard.classification` |
| `src/speed/mod.rs` | Export `RouteClass` and `classify_by_spacing` |
| `src/web/handlers.rs` | Add `classification` to `RouteSpeedDetailTemplate`; compute in handler |
| `templates/speed_card.html` | Wrap header in `.route-header` flex div; add badge |
| `templates/speed.html` | Add `.route-header` and `.badge--*` CSS |
| `templates/route_speed_detail.html` | Add badge to page header; add badge CSS |

---

### Task 1: `RouteClass` enum, `classify_by_spacing`, and `RouteSpeedCard.classification`

**Files:**
- Modify: `src/speed/card.rs`
- Modify: `src/speed/mod.rs:9` (update `pub use`)

- [ ] **Step 1: Write failing unit tests**

Add to `src/speed/card.rs` inside `#[cfg(test)] mod tests`:

```rust
#[test]
fn classify_slow_bus_below_300() {
    assert_eq!(classify_by_spacing(0.0), RouteClass::SlowBus);
    assert_eq!(classify_by_spacing(299.9), RouteClass::SlowBus);
}

#[test]
fn classify_local_bus_300_to_500() {
    assert_eq!(classify_by_spacing(300.0), RouteClass::LocalBus);
    assert_eq!(classify_by_spacing(499.9), RouteClass::LocalBus);
}

#[test]
fn classify_rapid_500_to_1500() {
    assert_eq!(classify_by_spacing(500.0), RouteClass::Rapid);
    assert_eq!(classify_by_spacing(1499.9), RouteClass::Rapid);
}

#[test]
fn classify_express_at_1500_and_above() {
    assert_eq!(classify_by_spacing(1500.0), RouteClass::Express);
    assert_eq!(classify_by_spacing(9999.0), RouteClass::Express);
}

#[test]
fn route_class_label() {
    assert_eq!(RouteClass::SlowBus.label(), "Slow Bus");
    assert_eq!(RouteClass::LocalBus.label(), "Local Bus");
    assert_eq!(RouteClass::Rapid.label(), "Rapid");
    assert_eq!(RouteClass::Express.label(), "Express");
}

#[test]
fn route_class_css_class() {
    assert_eq!(RouteClass::SlowBus.css_class(), "slow-bus");
    assert_eq!(RouteClass::LocalBus.css_class(), "local-bus");
    assert_eq!(RouteClass::Rapid.css_class(), "rapid");
    assert_eq!(RouteClass::Express.css_class(), "express");
}

#[test]
fn build_speed_cards_sets_classification_from_stop_spacing() {
    // avg spacing of 620 m → Rapid
    let mut row = make_row("stm", "R1", 0, Some(8.0));
    row.avg_stop_spacing_m = Some(620.0);
    let cards = build_speed_cards(vec![row], &HashMap::new());
    assert_eq!(cards[0].classification, Some(RouteClass::Rapid));
}

#[test]
fn build_speed_cards_classification_is_none_when_no_spacing_data() {
    // make_row sets avg_stop_spacing_m = None
    let rows = vec![make_row("stm", "R1", 0, Some(8.0))];
    let cards = build_speed_cards(rows, &HashMap::new());
    assert!(cards[0].classification.is_none());
}

#[test]
fn build_speed_cards_classification_averages_across_directions() {
    // direction 0: 400 m (Local Bus), direction 1: 600 m (Rapid) → avg 500 m → Rapid
    let mut row0 = make_row("stm", "R1", 0, Some(8.0));
    row0.avg_stop_spacing_m = Some(400.0);
    let mut row1 = make_row("stm", "R1", 1, Some(7.0));
    row1.avg_stop_spacing_m = Some(600.0);
    let cards = build_speed_cards(vec![row0, row1], &HashMap::new());
    assert_eq!(cards[0].classification, Some(RouteClass::Rapid));
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test classify_slow_bus_below_300 classify_rapid_500_to_1500 route_class_label build_speed_cards_sets_classification 2>&1 | head -30
```

Expected: compile errors (`classify_by_spacing` not found, `RouteClass` not found).

- [ ] **Step 3: Add `RouteClass` enum and `classify_by_spacing`**

Add the following to `src/speed/card.rs` immediately before `pub struct RouteSpeedCard`:

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RouteClass {
    SlowBus,
    LocalBus,
    Rapid,
    Express,
}

impl RouteClass {
    pub fn label(&self) -> &'static str {
        match self {
            RouteClass::SlowBus => "Slow Bus",
            RouteClass::LocalBus => "Local Bus",
            RouteClass::Rapid => "Rapid",
            RouteClass::Express => "Express",
        }
    }

    pub fn css_class(&self) -> &'static str {
        match self {
            RouteClass::SlowBus => "slow-bus",
            RouteClass::LocalBus => "local-bus",
            RouteClass::Rapid => "rapid",
            RouteClass::Express => "express",
        }
    }
}

pub fn classify_by_spacing(avg_m: f64) -> RouteClass {
    if avg_m < 300.0 {
        RouteClass::SlowBus
    } else if avg_m < 500.0 {
        RouteClass::LocalBus
    } else if avg_m < 1500.0 {
        RouteClass::Rapid
    } else {
        RouteClass::Express
    }
}
```

- [ ] **Step 4: Add `classification` field to `RouteSpeedCard`**

In `src/speed/card.rs`, the `RouteSpeedCard` struct currently ends with `avg_actual_speed_mps`. Add one field after it:

```rust
pub struct RouteSpeedCard {
    pub idx: usize,
    pub agency_name: String,
    pub agency_id: String,
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub charts: Vec<DirectionSpeedChart>,
    pub avg_scheduled_speed_mps: Option<f64>,
    pub avg_actual_speed_mps: Option<f64>,
    pub classification: Option<RouteClass>,
}
```

- [ ] **Step 5: Compute `classification` in `build_speed_cards`**

In `build_speed_cards`, the computation block currently reads (around line 105):

```rust
let avg_scheduled_speed_mps = avg_speeds(route_rows.iter().flat_map(|r| { ... }));
let avg_actual_speed_mps = avg_speeds(route_rows.iter().flat_map(|r| { ... }));
cards.push(RouteSpeedCard {
    idx: card_idx,
    ...
    avg_actual_speed_mps,
});
```

Add the classification computation and the new field. The full updated block:

```rust
let avg_scheduled_speed_mps = avg_speeds(route_rows.iter().flat_map(|r| {
    [
        r.weekday_speed_mps,
        r.saturday_speed_mps,
        r.sunday_speed_mps,
    ]
}));
let avg_actual_speed_mps = avg_speeds(route_rows.iter().flat_map(|r| {
    [
        r.actual_weekday_speed_mps,
        r.actual_saturday_speed_mps,
        r.actual_sunday_speed_mps,
    ]
}));
let avg_spacing_m = avg_speeds(charts.iter().map(|c| c.avg_stop_spacing_m));
let classification = avg_spacing_m.map(classify_by_spacing);
cards.push(RouteSpeedCard {
    idx: card_idx,
    agency_name,
    agency_id: first.agency_id.clone(),
    route_id: first.route_id.clone(),
    short_name: first.short_name.clone(),
    long_name: first.long_name.clone(),
    charts,
    avg_scheduled_speed_mps,
    avg_actual_speed_mps,
    classification,
});
```

- [ ] **Step 6: Export from `src/speed/mod.rs`**

Replace line 9 of `src/speed/mod.rs`:

```rust
pub use card::{DirectionSpeedChart, RouteClass, RouteSpeedCard, build_speed_cards, classify_by_spacing};
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
cargo test --lib 2>&1 | tail -20
```

Expected: all tests pass including the new ones.

- [ ] **Step 8: Commit**

```bash
git add src/speed/card.rs src/speed/mod.rs
git commit -m "feat: add RouteClass enum and classify_by_spacing; derive classification on RouteSpeedCard"
```

---

### Task 2: Speed card badge (template + CSS)

**Files:**
- Modify: `templates/speed_card.html`
- Modify: `templates/speed.html`

- [ ] **Step 1: Write failing E2E test**

Add to `src/web/handlers.rs` inside `mod e2e_tests`:

```rust
#[tokio::test]
async fn speed_page_shows_classification_badge_for_rapid_route() {
    let td = test_utils::setup().await;
    // R1: two stops ~1111 m apart (0.01° lat). Single segment → avg = 1111 m → Rapid.
    sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '1', 'Route 1', 3)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Terminus')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('test', 'S1', 'First',   45.500, -73.50)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('test', 'S2', 'Terminus', 45.510, -73.50)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S1', 1, '08:00:00', '08:00:00')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S2', 2, '08:10:00', '08:10:00')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO route_speed VALUES ('test', 'R1', 0, 8.0, 1, '2026-01-01T00:00:00Z')")
        .execute(&td.db.pool).await.unwrap();

    let state = AppState { db: td.db, config: test_config() };
    let app = build_router(state);
    let response = app
        .oneshot(Request::builder().uri("/speed?agency=test").body(Body::empty()).unwrap())
        .await.unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024).await.unwrap();
    let html = String::from_utf8(bytes.to_vec()).unwrap();
    assert!(html.contains("Rapid"), "speed page HTML should contain 'Rapid' badge text");
    assert!(html.contains("badge--rapid"), "speed page HTML should contain 'badge--rapid' CSS class");
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cargo test speed_page_shows_classification_badge 2>&1 | tail -15
```

Expected: FAIL — template renders but no badge text (template not updated yet).

- [ ] **Step 3: Update `templates/speed_card.html` to add badge**

Replace the entire file contents:

```html
<a href="/routes/{{ card.agency_id }}/{{ card.route_id }}/speed"
   style="text-decoration: none; color: inherit; display: block;">
<div class="card">
  <div class="route-header">
    <div>
      <div class="route-num">{{ card.agency_name }} {{ card.short_name }}</div>
      <div class="route-name">{{ card.long_name }}</div>
    </div>
    {% if let Some(class) = card.classification %}
    <span class="badge badge--{{ class.css_class() }}">{{ class.label() }}</span>
    {% endif %}
  </div>
  <div class="chart-row">
    {% for chart in &card.charts %}
    <div class="chart-col">
      <div class="chart-title">{{ chart.title }}</div>
      <canvas id="{{ chart.chart_id }}" height="160"></canvas>
      <div class="stop-spacing">Avg stop spacing: {{ chart.avg_stop_spacing_display() }}</div>
    </div>
    {% endfor %}
  </div>
</div>
</a>
<script>
{% for chart in &card.charts %}
new Chart(document.getElementById('{{ chart.chart_id }}'), {
  type: 'bar',
  data: {
    labels: ['Weekday', 'Saturday', 'Sunday'],
    datasets: {{ chart.chart_json|safe }}
  },
  options: {
    responsive: true,
    plugins: {
      legend: { position: 'top', labels: { boxWidth: 10, font: { size: 11 } } },
      title: { display: true, text: 'Speed (km/h)' }
    },
    scales: {
      y: { beginAtZero: true, title: { display: true, text: 'km/h' }, ticks: { font: { size: 10 } } },
      x: { ticks: { font: { size: 10 } } }
    }
  }
});
{% endfor %}
</script>
```

- [ ] **Step 4: Add badge CSS to `templates/speed.html`**

In `templates/speed.html`, inside the `<style>` block, add after the `.no-data` rule (after line 36, before `</style>`):

```css
    .route-header { display: flex; justify-content: space-between; align-items: flex-start; }
    .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 0.7rem;
             font-weight: 600; letter-spacing: 0.04em; text-transform: uppercase; white-space: nowrap; }
    .badge--slow-bus  { background: #fef9e7; color: #b7950b; border: 1px solid #f9e79f; }
    .badge--local-bus { background: #e8f4fd; color: #2471a3; border: 1px solid #aed6f1; }
    .badge--rapid     { background: #eafaf1; color: #1e8449; border: 1px solid #a9dfbf; }
    .badge--express   { background: #f5eef8; color: #7d3c98; border: 1px solid #d7bde2; }
```

- [ ] **Step 5: Run test to verify it passes**

```bash
cargo test speed_page_shows_classification_badge 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 6: Run full test suite**

```bash
cargo test 2>&1 | tail -10
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add templates/speed_card.html templates/speed.html src/web/handlers.rs
git commit -m "feat: show route classification badge on speed card"
```

---

### Task 3: Detail page classification badge

**Files:**
- Modify: `src/web/handlers.rs`
- Modify: `templates/route_speed_detail.html`

- [ ] **Step 1: Write failing E2E test**

Add to `src/web/handlers.rs` inside `mod e2e_tests` (alongside the existing detail tests):

```rust
#[tokio::test]
async fn route_speed_detail_shows_classification_badge() {
    let td = test_utils::setup().await;
    // Two stops ~1111 m apart → avg spacing 1111 m → Rapid
    sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '1', 'Route 1', 3)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Downtown')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('test', 'S1', 'Main St',  45.50, -73.50)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('test', 'S2', 'Downtown', 45.51, -73.50)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S1', 1, '08:00:00', '08:00:00')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S2', 2, '08:10:00', '08:10:00')")
        .execute(&td.db.pool).await.unwrap();

    let state = AppState { db: td.db, config: test_config() };
    let app = build_router(state);
    let response = app
        .oneshot(Request::builder().uri("/routes/test/R1/speed").body(Body::empty()).unwrap())
        .await.unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024).await.unwrap();
    let html = String::from_utf8(bytes.to_vec()).unwrap();
    assert!(html.contains("Rapid"), "detail page HTML should contain 'Rapid' badge text");
    assert!(html.contains("badge--rapid"), "detail page HTML should contain 'badge--rapid' CSS class");
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cargo test route_speed_detail_shows_classification_badge 2>&1 | tail -15
```

Expected: FAIL — HTML renders but no badge.

- [ ] **Step 3: Add `RouteClass` import to `handlers.rs`**

In `src/web/handlers.rs`, update the speed import block (currently lines 14–18):

```rust
use crate::speed::{
    RouteClass, RouteSpeedCard, RouteSpeedSummary, StopSpacing,
    build_speed_cards, classify_by_spacing, route_speed_by_day_type, route_speed_summary,
    route_stop_spacings, route_speed_trend_by_direction,
};
```

- [ ] **Step 4: Add `classification` to `RouteSpeedDetailTemplate`**

In `src/web/handlers.rs`, the struct currently (lines 44–49):

```rust
#[derive(Template)]
#[template(path = "route_speed_detail.html")]
struct RouteSpeedDetailTemplate {
    short_name: String,
    long_name: String,
    agency_id: String,
    directions: Vec<RouteSpeedDetailDirection>,
    classification: Option<RouteClass>,
}
```

- [ ] **Step 5: Compute classification and pass to template**

In `route_speed_detail` handler, the current template instantiation (around line 139) is:

```rust
let tmpl = RouteSpeedDetailTemplate {
    short_name,
    long_name,
    agency_id,
    directions,
};
```

Replace it with:

```rust
let avg_spacing_m: Option<f64> = {
    let vals: Vec<f64> = directions.iter().map(|d| d.avg_spacing_m).collect();
    if vals.is_empty() {
        None
    } else {
        Some(vals.iter().sum::<f64>() / vals.len() as f64)
    }
};
let classification = avg_spacing_m.map(classify_by_spacing);
let tmpl = RouteSpeedDetailTemplate {
    short_name,
    long_name,
    agency_id,
    directions,
    classification,
};
```

- [ ] **Step 6: Update `templates/route_speed_detail.html` — add badge CSS**

In `templates/route_speed_detail.html`, inside the `<style>` block, add after the `footer` rule (before `</style>`):

```css
    .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 0.7rem;
             font-weight: 600; letter-spacing: 0.04em; text-transform: uppercase; white-space: nowrap; }
    .badge--slow-bus  { background: #fef9e7; color: #b7950b; border: 1px solid #f9e79f; }
    .badge--local-bus { background: #e8f4fd; color: #2471a3; border: 1px solid #aed6f1; }
    .badge--rapid     { background: #eafaf1; color: #1e8449; border: 1px solid #a9dfbf; }
    .badge--express   { background: #f5eef8; color: #7d3c98; border: 1px solid #d7bde2; }
```

- [ ] **Step 7: Update `templates/route_speed_detail.html` — add badge to header**

In `templates/route_speed_detail.html`, replace the `<header>` block (lines 55–58):

```html
  <header>
    <h1>Route {{ short_name }} — {{ long_name }}</h1>
    <a href="/speed?agency={{ agency_id }}">← Back to speed overview</a>
  </header>
```

With:

```html
  <header>
    <h1>Route {{ short_name }} — {{ long_name }}</h1>
    <div style="display:flex;align-items:center;gap:1rem;">
      {% if let Some(class) = classification %}
      <span class="badge badge--{{ class.css_class() }}">{{ class.label() }}</span>
      {% endif %}
      <a href="/speed?agency={{ agency_id }}">← Back to speed overview</a>
    </div>
  </header>
```

- [ ] **Step 8: Run test to verify it passes**

```bash
cargo test route_speed_detail_shows_classification_badge 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 9: Run full test suite**

```bash
cargo test 2>&1 | tail -10
```

Expected: all tests pass.

- [ ] **Step 10: Commit**

```bash
git add src/web/handlers.rs templates/route_speed_detail.html
git commit -m "feat: show route classification badge on route speed detail page"
```

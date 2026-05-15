# Speed Card: Stats Instead of Chart

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the per-day-type Chart.js bar chart on each speed card with two stat numbers — one for average scheduled speed and one for average actual speed.

**Architecture:** The data already exists on `RouteSpeedCard` (`avg_scheduled_speed_mps`, `avg_actual_speed_mps`). We add display-format methods, move `avg_stop_spacing_m` and its helpers directly onto `RouteSpeedCard` (removing the now-unnecessary `RouteSpeedChart` wrapper), then update the template to render stats instead of a canvas.

**Tech Stack:** Rust (card.rs helper methods), Askama HTML templates, existing `spacing-stat-*` CSS classes

---

## File Map

| File | What changes |
|------|-------------|
| `src/speed/card.rs` | Remove `RouteSpeedChart` struct, `combined_datasets`, `speed_kmh_json`; add speed display methods and spacing helpers directly to `RouteSpeedCard`; update `build_speed_cards` |
| `src/web/handlers.rs` | Update test helpers: remove `empty_chart()`, inline `avg_stop_spacing_m: None` |
| `templates/speed_card.html` | Replace canvas + `<script>` with two stat rows; update `card.chart.*` refs to `card.*` |
| `templates/speed.html` | Remove Chart.js CDN `<script>` (no longer needed; `route_speed_detail.html` has its own) |

---

### Task 1: Add speed display methods to `RouteSpeedCard`

**Files:**
- Modify: `src/speed/card.rs`

- [ ] **Step 1: Write the failing tests**

Add these tests inside the existing `#[cfg(test)] mod tests` block in `src/speed/card.rs`:

```rust
#[test]
fn scheduled_speed_display_formats_kmh_one_decimal() {
    // 10.0 m/s = 36.0 km/h
    let card = RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: "1".into(),
        long_name: "Route 1".into(),
        chart: RouteSpeedChart { chart_id: String::new(), chart_json: "[]".into(), avg_stop_spacing_m: None },
        avg_scheduled_speed_mps: Some(10.0),
        avg_actual_speed_mps: None,
        classification: None,
    };
    assert_eq!(card.avg_scheduled_speed_kmh_display(), "36.0");
}

#[test]
fn scheduled_speed_display_dash_when_none() {
    let card = RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: "1".into(),
        long_name: "Route 1".into(),
        chart: RouteSpeedChart { chart_id: String::new(), chart_json: "[]".into(), avg_stop_spacing_m: None },
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        classification: None,
    };
    assert_eq!(card.avg_scheduled_speed_kmh_display(), "—");
}

#[test]
fn actual_speed_display_formats_kmh_one_decimal() {
    // 5.0 m/s = 18.0 km/h
    let card = RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: "1".into(),
        long_name: "Route 1".into(),
        chart: RouteSpeedChart { chart_id: String::new(), chart_json: "[]".into(), avg_stop_spacing_m: None },
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: Some(5.0),
        classification: None,
    };
    assert_eq!(card.avg_actual_speed_kmh_display(), "18.0");
}

#[test]
fn actual_speed_display_dash_when_none() {
    let card = RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: "1".into(),
        long_name: "Route 1".into(),
        chart: RouteSpeedChart { chart_id: String::new(), chart_json: "[]".into(), avg_stop_spacing_m: None },
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        classification: None,
    };
    assert_eq!(card.avg_actual_speed_kmh_display(), "—");
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test scheduled_speed_display actual_speed_display
```

Expected: error `no method named avg_scheduled_speed_kmh_display`

- [ ] **Step 3: Add display methods to `RouteSpeedCard` impl**

After the closing `}` of the `RouteSpeedChart` impl block (around line 33), add a new impl block:

```rust
impl RouteSpeedCard {
    pub fn avg_scheduled_speed_kmh_display(&self) -> String {
        fmt_speed_kmh(self.avg_scheduled_speed_mps)
    }

    pub fn avg_actual_speed_kmh_display(&self) -> String {
        fmt_speed_kmh(self.avg_actual_speed_mps)
    }
}

fn fmt_speed_kmh(mps: Option<f64>) -> String {
    match mps {
        None => "—".to_string(),
        Some(s) => format!("{:.1}", s * 3.6),
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cargo test scheduled_speed_display actual_speed_display
```

Expected: 4 tests pass

- [ ] **Step 5: Commit**

```bash
git add src/speed/card.rs
git commit -m "feat: add speed km/h display methods to RouteSpeedCard"
```

---

### Task 2: Move spacing onto `RouteSpeedCard`, remove `RouteSpeedChart`

**Files:**
- Modify: `src/speed/card.rs`
- Modify: `src/web/handlers.rs`

- [ ] **Step 1: Update existing spacing tests to target `RouteSpeedCard` directly**

In `src/speed/card.rs`, replace the `chart_with_spacing` helper and all tests that use it. Find and replace the helper and the 9 spacing tests (`spacing_class_*`, `spacing_number_*`, `spacing_unit_*`) with these versions that construct `RouteSpeedCard` instead of `RouteSpeedChart`:

```rust
fn card_with_spacing(m: Option<f64>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: "1".into(),
        long_name: "Route 1".into(),
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        avg_stop_spacing_m: m,
        classification: None,
    }
}

#[test]
fn spacing_class_none_when_no_data() {
    assert_eq!(card_with_spacing(None).avg_stop_spacing_class(), "none");
}

#[test]
fn spacing_class_local_below_500m() {
    assert_eq!(card_with_spacing(Some(300.0)).avg_stop_spacing_class(), "local");
}

#[test]
fn spacing_class_rapid_500_to_1500m() {
    assert_eq!(card_with_spacing(Some(800.0)).avg_stop_spacing_class(), "rapid");
}

#[test]
fn spacing_class_express_1500m_and_above() {
    assert_eq!(card_with_spacing(Some(2000.0)).avg_stop_spacing_class(), "express");
}

#[test]
fn spacing_number_none() {
    assert_eq!(card_with_spacing(None).avg_stop_spacing_number(), "—");
}

#[test]
fn spacing_number_metres() {
    assert_eq!(card_with_spacing(Some(342.0)).avg_stop_spacing_number(), "342");
}

#[test]
fn spacing_number_kilometres() {
    assert_eq!(card_with_spacing(Some(1200.0)).avg_stop_spacing_number(), "1.2");
}

#[test]
fn spacing_unit_none() {
    assert_eq!(card_with_spacing(None).avg_stop_spacing_unit(), "");
}

#[test]
fn spacing_unit_metres() {
    assert_eq!(card_with_spacing(Some(342.0)).avg_stop_spacing_unit(), "m");
}

#[test]
fn spacing_unit_kilometres() {
    assert_eq!(card_with_spacing(Some(1200.0)).avg_stop_spacing_unit(), "km");
}
```

Also update these tests to remove the `chart:` field and replace `card.chart.avg_stop_spacing_m` with `card.avg_stop_spacing_m`:

- `build_speed_cards_chart_carries_avg_stop_spacing` → rename to `build_speed_cards_carries_avg_stop_spacing`, assert `cards[0].avg_stop_spacing_m`
- `build_speed_cards_chart_averages_stop_spacing_across_directions` → rename to `build_speed_cards_averages_stop_spacing_across_directions`, assert `cards[0].avg_stop_spacing_m`

Delete these tests (chart IDs no longer exist):
- `build_speed_cards_produces_one_chart_per_route`
- `build_speed_cards_chart_ids_are_unique_across_routes`
- `combined_datasets_includes_scheduled_and_actual`
- `combined_datasets_averages_directions_per_day_type`

- [ ] **Step 2: Run tests to confirm they fail with the right error**

```bash
cargo test
```

Expected: compilation errors — `no field chart`, `struct RouteSpeedCard has no field named chart`, etc. (The tests we just wrote reference the new structure; the implementation hasn't caught up yet.)

- [ ] **Step 3: Restructure `card.rs` — remove `RouteSpeedChart`, update `RouteSpeedCard`, move helpers**

Replace the entire contents of `src/speed/card.rs` above the `#[cfg(test)]` block with:

```rust
use crate::speed::RouteSpeedDayType;
use std::collections::HashMap;

pub struct RouteSpeedCard {
    pub idx: usize,
    pub agency_name: String,
    pub agency_id: String,
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub avg_scheduled_speed_mps: Option<f64>,
    pub avg_actual_speed_mps: Option<f64>,
    pub avg_stop_spacing_m: Option<f64>,
    pub classification: Option<RouteClass>,
}

impl RouteSpeedCard {
    pub fn avg_scheduled_speed_kmh_display(&self) -> String {
        fmt_speed_kmh(self.avg_scheduled_speed_mps)
    }

    pub fn avg_actual_speed_kmh_display(&self) -> String {
        fmt_speed_kmh(self.avg_actual_speed_mps)
    }

    pub fn avg_stop_spacing_class(&self) -> &'static str {
        match self.avg_stop_spacing_m {
            None => "none",
            Some(m) => classify_by_spacing(m).css_class(),
        }
    }

    pub fn avg_stop_spacing_number(&self) -> String {
        match self.avg_stop_spacing_m {
            None => "—".to_string(),
            Some(m) if m >= 1000.0 => format!("{:.1}", m / 1000.0),
            Some(m) => format!("{:.0}", m),
        }
    }

    pub fn avg_stop_spacing_unit(&self) -> &'static str {
        match self.avg_stop_spacing_m {
            None => "",
            Some(m) if m >= 1000.0 => "km",
            Some(_) => "m",
        }
    }
}

fn fmt_speed_kmh(mps: Option<f64>) -> String {
    match mps {
        None => "—".to_string(),
        Some(s) => format!("{:.1}", s * 3.6),
    }
}

/// Returns the unweighted mean of all `Some` values in `iter`, or `None` if all are `None`.
fn avg_speeds(iter: impl Iterator<Item = Option<f64>>) -> Option<f64> {
    let (sum, count) = iter
        .flatten()
        .fold((0.0_f64, 0usize), |(s, n), v| (s + v, n + 1));
    (count > 0).then(|| sum / count as f64)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RouteClass {
    Local,
    Rapid,
    Express,
}

impl RouteClass {
    pub fn label(&self) -> &'static str {
        match self {
            RouteClass::Local => "Local",
            RouteClass::Rapid => "Rapid",
            RouteClass::Express => "Express",
        }
    }

    pub fn speed_range(&self) -> &'static str {
        match self {
            RouteClass::Local => "12-18 km/h",
            RouteClass::Rapid => "18-25 km/h",
            RouteClass::Express => ">25 km/h",
        }
    }

    pub fn css_class(&self) -> &'static str {
        match self {
            RouteClass::Local => "local",
            RouteClass::Rapid => "rapid",
            RouteClass::Express => "express",
        }
    }
}

pub fn classify_by_spacing(avg_m: f64) -> RouteClass {
    if avg_m < 500.0 {
        RouteClass::Local
    } else if avg_m < 1500.0 {
        RouteClass::Rapid
    } else {
        RouteClass::Express
    }
}

pub fn build_speed_cards(
    rows: Vec<RouteSpeedDayType>,
    agency_names: &HashMap<String, String>,
) -> Vec<RouteSpeedCard> {
    let mut cards: Vec<RouteSpeedCard> = Vec::new();
    for route_rows in rows.chunk_by(|a, b| a.agency_id == b.agency_id && a.route_id == b.route_id) {
        let first = &route_rows[0];
        let agency_name = agency_names
            .get(&first.agency_id)
            .cloned()
            .unwrap_or_else(|| first.agency_id.clone());
        let card_idx = cards.len();
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
        let avg_stop_spacing_m = avg_speeds(route_rows.iter().map(|r| r.avg_stop_spacing_m));
        let classification = avg_stop_spacing_m.map(classify_by_spacing);
        cards.push(RouteSpeedCard {
            idx: card_idx,
            agency_name,
            agency_id: first.agency_id.clone(),
            route_id: first.route_id.clone(),
            short_name: first.short_name.clone(),
            long_name: first.long_name.clone(),
            avg_scheduled_speed_mps,
            avg_actual_speed_mps,
            avg_stop_spacing_m,
            classification,
        });
    }
    cards
}
```

- [ ] **Step 4: Update `handlers.rs` test helpers**

In `src/web/handlers.rs`, replace the test helpers that use `RouteSpeedChart`:

Find and replace:
```rust
use crate::speed::{RouteSpeedCard, RouteSpeedChart};

fn empty_chart() -> RouteSpeedChart {
    RouteSpeedChart {
        chart_id: String::new(),
        chart_json: "[]".into(),
        avg_stop_spacing_m: None,
    }
}

fn card(short_name: &str, scheduled: f64, actual: Option<f64>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        chart: empty_chart(),
        avg_scheduled_speed_mps: Some(scheduled),
        avg_actual_speed_mps: actual,
        classification: None,
    }
}

fn card_no_scheduled(short_name: &str) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        chart: empty_chart(),
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        classification: None,
    }
}
```

With:
```rust
use crate::speed::RouteSpeedCard;

fn card(short_name: &str, scheduled: f64, actual: Option<f64>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        avg_scheduled_speed_mps: Some(scheduled),
        avg_actual_speed_mps: actual,
        avg_stop_spacing_m: None,
        classification: None,
    }
}

fn card_no_scheduled(short_name: &str) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        avg_stop_spacing_m: None,
        classification: None,
    }
}
```

Also update the `card_with_class` helper (further down in `handlers.rs`) — remove `chart: empty_chart()` and add `avg_stop_spacing_m: None`:

```rust
fn card_with_class(short_name: &str, class: Option<RouteClass>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        avg_stop_spacing_m: None,
        classification: class,
    }
}
```

- [ ] **Step 5: Run all tests to verify they pass**

```bash
cargo test
```

Expected: all tests pass, no warnings about unused `RouteSpeedChart`

- [ ] **Step 6: Commit**

```bash
git add src/speed/card.rs src/web/handlers.rs
git commit -m "refactor: remove RouteSpeedChart, move spacing helpers onto RouteSpeedCard"
```

---

### Task 3: Update template and remove Chart.js

**Files:**
- Modify: `templates/speed_card.html`
- Modify: `templates/speed.html`

- [ ] **Step 1: Replace `templates/speed_card.html`**

Replace the entire file with:

```html
<a href="/routes/{{ card.agency_id }}/{{ card.route_id }}/speed"
   style="text-decoration: none; color: inherit; display: block;">
<div class="card" style="padding:1rem 1.25rem;">
  <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:1rem;">
    <div>
      <div class="route-id">{{ card.agency_name }} {{ card.short_name }}</div>
      <div style="margin-top:0.15rem;color:var(--ink-500);font-size:0.82rem;">{{ card.long_name }}</div>
    </div>
    {% if let Some(class) = card.classification %}
    <span class="badge badge--{{ class.css_class() }}">{{ class.label() }}</span>
    {% endif %}
  </div>
  <div style="margin-top:0.75rem;display:flex;gap:1.5rem;border-top:1px solid var(--line-soft);padding-top:0.75rem;">
    <div>
      <div class="spacing-stat-label">Scheduled</div>
      <div class="spacing-stat-num">{{ card.avg_scheduled_speed_kmh_display() }}<span class="spacing-stat-unit"> km/h</span></div>
    </div>
    <div>
      <div class="spacing-stat-label">Actual</div>
      <div class="spacing-stat-num">{{ card.avg_actual_speed_kmh_display() }}<span class="spacing-stat-unit"> km/h</span></div>
    </div>
    <div style="margin-left:auto;">
      <div class="spacing-stat-label">Avg stop spacing</div>
      <div class="spacing-stat-num spacing-{{ card.avg_stop_spacing_class() }}">{{ card.avg_stop_spacing_number() }}<span class="spacing-stat-unit">{{ card.avg_stop_spacing_unit() }}</span></div>
    </div>
  </div>
</div>
</a>
```

- [ ] **Step 2: Remove Chart.js CDN from `templates/speed.html`**

In `templates/speed.html`, remove line 8:
```html
<script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
```

(`route_speed_detail.html` has its own Chart.js load and is unaffected.)

- [ ] **Step 3: Build to verify templates compile**

```bash
cargo build
```

Expected: success. Askama compiles templates at build time — any template type error shows here.

- [ ] **Step 4: Commit**

```bash
git add templates/speed_card.html templates/speed.html
git commit -m "feat: replace speed card chart with scheduled/actual stat display"
```

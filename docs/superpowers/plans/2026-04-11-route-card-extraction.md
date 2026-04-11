# RouteCard Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `RouteSpeedCard` and its builder logic from `src/web/handlers.rs` into `src/speed/card.rs`, and extract the card HTML from `templates/speed.html` into `templates/speed_card.html`, following vertical slice architecture.

**Architecture:** All speed-feature code — domain logic, view models, and template fragments — lives in the speed slice (`src/speed/` and `templates/speed_card.html`). `handlers.rs` retains only the `SpeedTemplate` struct and `speed_page` handler. The Askama `{% include %}` directive renders the card fragment inside the existing loop.

**Tech Stack:** Rust, Askama (server-side templates via `#[derive(Template)]`), Chart.js (CDN, bar chart per card), serde_json

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/speed/card.rs` | `RouteSpeedCard` struct, `build_speed_cards`, helpers |
| Modify | `src/speed/mod.rs` | Declare `pub mod card` and re-export public items |
| Modify | `src/web/handlers.rs` | Remove moved items, update import |
| Create | `templates/speed_card.html` | Card div + per-card Chart.js `<script>` |
| Modify | `templates/speed.html` | Replace inline card + script block with `{% include %}` |

---

## Task 1: Create `src/speed/card.rs` with tests

**Files:**
- Create: `src/speed/card.rs`

- [ ] **Step 1: Write the failing tests**

Add a new file `src/speed/card.rs` with unit tests first (the functions don't exist yet so it won't compile — that counts as failing):

```rust
use std::collections::HashMap;
use crate::speed::RouteSpeedDayType;

pub struct RouteSpeedCard {
    pub idx: usize,
    pub agency_name: String,
    pub short_name: String,
    pub long_name: String,
    pub chart_json: String,
}

fn speed_kmh_json(mps: Option<f64>) -> serde_json::Value {
    match mps {
        Some(s) => {
            let kmh = (s * 3.6 * 10.0).round() / 10.0;
            serde_json::Number::from_f64(kmh)
                .map(serde_json::Value::Number)
                .unwrap_or(serde_json::Value::Null)
        }
        None => serde_json::Value::Null,
    }
}

fn day_type_data(row: Option<&RouteSpeedDayType>) -> serde_json::Value {
    match row {
        Some(r) => serde_json::json!([
            speed_kmh_json(r.weekday_speed_mps),
            speed_kmh_json(r.saturday_speed_mps),
            speed_kmh_json(r.sunday_speed_mps),
        ]),
        None => serde_json::json!([null, null, null]),
    }
}

pub fn build_speed_cards(
    rows: Vec<RouteSpeedDayType>,
    agency_names: &HashMap<String, String>,
) -> Vec<RouteSpeedCard> {
    let mut cards: Vec<RouteSpeedCard> = Vec::new();
    let mut i = 0;
    while i < rows.len() {
        let agency_id = rows[i].agency_id.clone();
        let route_id = rows[i].route_id.clone();
        let mut j = i;
        while j < rows.len() && rows[j].agency_id == agency_id && rows[j].route_id == route_id {
            j += 1;
        }
        let route_rows = &rows[i..j];
        let first = &rows[i];
        let agency_name = agency_names
            .get(&first.agency_id)
            .cloned()
            .unwrap_or_else(|| first.agency_id.clone());
        let outbound = route_rows.iter().find(|r| r.direction_id == 0);
        let inbound = route_rows.iter().find(|r| r.direction_id == 1);
        let datasets = serde_json::json!([
            { "label": "Outbound", "data": day_type_data(outbound), "backgroundColor": "#2980b9" },
            { "label": "Inbound",  "data": day_type_data(inbound),  "backgroundColor": "#27ae60" },
        ]);
        cards.push(RouteSpeedCard {
            idx: cards.len(),
            agency_name,
            short_name: first.short_name.clone(),
            long_name: first.long_name.clone(),
            chart_json: serde_json::to_string(&datasets).unwrap_or_default(),
        });
        i = j;
    }
    cards
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_row(agency_id: &str, route_id: &str, direction_id: i64, weekday: Option<f64>) -> RouteSpeedDayType {
        RouteSpeedDayType {
            agency_id: agency_id.to_string(),
            route_id: route_id.to_string(),
            short_name: route_id.to_string(),
            long_name: format!("Route {route_id}"),
            direction_id,
            weekday_speed_mps: weekday,
            saturday_speed_mps: None,
            sunday_speed_mps: None,
        }
    }

    #[test]
    fn speed_kmh_json_converts_mps_to_kmh() {
        // 10 m/s = 36 km/h
        let v = speed_kmh_json(Some(10.0));
        assert_eq!(v, serde_json::json!(36.0));
    }

    #[test]
    fn speed_kmh_json_none_returns_null() {
        assert_eq!(speed_kmh_json(None), serde_json::Value::Null);
    }

    #[test]
    fn build_speed_cards_groups_directions_into_one_card() {
        let rows = vec![
            make_row("stm", "R1", 0, Some(8.0)),
            make_row("stm", "R1", 1, Some(7.5)),
        ];
        let mut names = HashMap::new();
        names.insert("stm".to_string(), "STM".to_string());
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards.len(), 1);
        assert_eq!(cards[0].agency_name, "STM");
        assert_eq!(cards[0].short_name, "R1");
        assert_eq!(cards[0].idx, 0);
    }

    #[test]
    fn build_speed_cards_assigns_sequential_idx() {
        let rows = vec![
            make_row("stm", "R1", 0, None),
            make_row("stm", "R2", 0, None),
        ];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards[0].idx, 0);
        assert_eq!(cards[1].idx, 1);
    }

    #[test]
    fn build_speed_cards_falls_back_to_agency_id_when_name_missing() {
        let rows = vec![make_row("unknown", "R1", 0, None)];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards[0].agency_name, "unknown");
    }

    #[test]
    fn build_speed_cards_empty_input_returns_empty() {
        let cards = build_speed_cards(vec![], &HashMap::new());
        assert!(cards.is_empty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail (won't compile)**

```bash
cargo test -p mobilispect 2>&1 | head -30
```

Expected: compile error — `mod card` not declared yet in `speed/mod.rs`.

- [ ] **Step 3: Declare the module in `src/speed/mod.rs`**

Add at the top of `src/speed/mod.rs`, after the existing `use` statements and before any other items:

```rust
pub mod card;
pub use card::{build_speed_cards, RouteSpeedCard};
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cargo test speed::card 2>&1
```

Expected output contains:
```
test speed::card::tests::build_speed_cards_assigns_sequential_idx ... ok
test speed::card::tests::build_speed_cards_empty_input_returns_empty ... ok
test speed::card::tests::build_speed_cards_falls_back_to_agency_id_when_name_missing ... ok
test speed::card::tests::build_speed_cards_groups_directions_into_one_card ... ok
test speed::card::tests::speed_kmh_json_converts_mps_to_kmh ... ok
test speed::card::tests::speed_kmh_json_none_returns_null ... ok
```

- [ ] **Step 5: Commit**

```bash
git add src/speed/card.rs src/speed/mod.rs
git commit -m "feat: extract RouteSpeedCard into speed::card (vertical slice)"
```

---

## Task 2: Update `handlers.rs` to use the moved types

**Files:**
- Modify: `src/web/handlers.rs`

- [ ] **Step 1: Update the import line**

In `src/web/handlers.rs`, line 8 currently reads:

```rust
use crate::speed::{route_speed_by_day_type, route_speed_summary, RouteSpeedDayType, RouteSpeedSummary};
```

Change it to:

```rust
use crate::speed::{build_speed_cards, route_speed_by_day_type, route_speed_summary, RouteSpeedCard, RouteSpeedDayType, RouteSpeedSummary};
```

- [ ] **Step 2: Delete the four moved items**

Remove these blocks entirely from `src/web/handlers.rs`:

1. Lines 103–109 — the `struct RouteSpeedCard { … }` definition  
2. Lines 119–129 — `fn speed_kmh_json(…) { … }`  
3. Lines 131–140 — `fn day_type_data(…) { … }`  
4. Lines 142–177 — `fn build_speed_cards(…) { … }`

- [ ] **Step 3: Verify it compiles**

```bash
cargo build 2>&1
```

Expected: no errors, no warnings about unused imports or dead code.

- [ ] **Step 4: Commit**

```bash
git add src/web/handlers.rs
git commit -m "refactor: remove RouteSpeedCard and builder from handlers (moved to speed::card)"
```

---

## Task 3: Extract card template fragment

**Files:**
- Create: `templates/speed_card.html`
- Modify: `templates/speed.html`

- [ ] **Step 1: Create `templates/speed_card.html`**

Create the file with this exact content (card div + per-card Chart.js initializer):

```html
<div class="card">
  <div class="route-num">{{ card.agency_name }} {{ card.short_name }}</div>
  <div class="route-name">{{ card.long_name }}</div>
  <canvas id="chart-{{ card.idx }}" height="160"></canvas>
</div>
<script>
new Chart(document.getElementById('chart-{{ card.idx }}'), {
  type: 'bar',
  data: {
    labels: ['Weekday', 'Saturday', 'Sunday'],
    datasets: {{ card.chart_json|safe }}
  },
  options: {
    responsive: true,
    plugins: { legend: { position: 'top', labels: { boxWidth: 10, font: { size: 11 } } } },
    scales: {
      y: { beginAtZero: true, title: { display: true, text: 'km/h' }, ticks: { font: { size: 10 } } },
      x: { ticks: { font: { size: 10 } } }
    }
  }
});
</script>
```

- [ ] **Step 2: Update `templates/speed.html`**

Replace the `<div class="card-grid">…</div>` block (lines 54–63) and the entire `<script>` loop block (lines 69–86) with:

```html
    {% if cards.is_empty() %}
    <p class="no-data">No speed data available yet. Data appears after the daily compute runs.</p>
    {% else %}
    <div class="card-grid">
      {% for card in &cards %}
      {% include "speed_card.html" %}
      {% endfor %}
    </div>
    {% endif %}
```

Keep the `<script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>` CDN tag — it must stay in `speed.html` above the card loop so Chart.js is loaded before any per-card `<script>` runs.

The full updated `speed.html` body section should look like:

```html
  <div class="container">
    <div class="filter-bar">
      <a href="/speed" class="{% if active_agency.is_empty() %}active{% endif %}">All</a>
      {% for (slug, name) in &agencies %}
      <a href="/speed?agency={{ slug }}" class="{% if active_agency == *slug %}active{% endif %}">{{ name }}</a>
      {% endfor %}
    </div>
    {% if cards.is_empty() %}
    <p class="no-data">No speed data available yet. Data appears after the daily compute runs.</p>
    {% else %}
    <div class="card-grid">
      {% for card in &cards %}
      {% include "speed_card.html" %}
      {% endfor %}
    </div>
    {% endif %}
  </div>
  <footer>Mobilispect · Data: STM GTFS-RT</footer>

  <script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
```

- [ ] **Step 3: Build to verify Askama compiles the templates**

```bash
cargo build 2>&1
```

Expected: no errors. Askama validates template syntax at compile time, so any Jinja-style errors will appear here.

- [ ] **Step 4: Commit**

```bash
git add templates/speed_card.html templates/speed.html
git commit -m "feat: extract speed card HTML into templates/speed_card.html"
```

---

## Task 4: Smoke-test the running app

**Files:** none (verification only)

- [ ] **Step 1: Run the full test suite**

```bash
cargo test 2>&1
```

Expected: all existing tests pass; the 6 new `speed::card` tests pass.

- [ ] **Step 2: Start the server**

```bash
cargo run --bin mobilispect-server 2>&1 &
```

Wait for the log line indicating the server is listening (typically `Listening on 0.0.0.0:3000`).

- [ ] **Step 3: Check the speed page renders**

Open `http://localhost:3000/speed` in a browser (or `curl -s http://localhost:3000/speed | grep "card-grid"`).

Expected: the page loads, `.card-grid` is present, and bar charts render for each route.

- [ ] **Step 4: Check the agency filter**

Open `http://localhost:3000/speed?agency=stm` (replace `stm` with a slug from your config).

Expected: only cards for that agency appear; the active filter pill is highlighted.

- [ ] **Step 5: Stop the server**

```bash
kill %1
```

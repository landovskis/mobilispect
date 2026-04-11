# RouteCard Extraction — Design Spec

**Date:** 2026-04-11  
**Status:** Approved

## Context

The `RouteSpeedCard` struct and its associated builder functions (`build_speed_cards`, `speed_kmh_json`, `day_type_data`) currently live in `src/web/handlers.rs`. The card HTML is inlined in `templates/speed.html`. This violates vertical slice architecture: the speed feature's view model and template fragment are scattered across the web layer instead of living inside the speed slice.

## Goal

Consolidate all RouteCard code into the speed vertical slice:
- Rust view-model and builder → `src/speed/card.rs`
- HTML card fragment → `templates/speed_card.html`

## Changes

### 1. New file: `src/speed/card.rs`

Move the following from `src/web/handlers.rs` into this new file:

- `struct RouteSpeedCard` (fields: `idx`, `agency_name`, `short_name`, `long_name`, `chart_json`)
- `fn speed_kmh_json(mps: Option<f64>) -> serde_json::Value`
- `fn day_type_data(row: Option<&RouteSpeedDayType>) -> serde_json::Value`
- `pub fn build_speed_cards(rows: Vec<RouteSpeedDayType>, agency_names: &HashMap<String, String>) -> Vec<RouteSpeedCard>`

`RouteSpeedCard` and `build_speed_cards` must be `pub`. `speed_kmh_json` and `day_type_data` remain private to the module.

### 2. Update `src/speed/mod.rs`

Add `pub mod card;` and re-export the public items:

```rust
pub mod card;
pub use card::{RouteSpeedCard, build_speed_cards};
```

### 3. Update `src/web/handlers.rs`

- Remove the four moved items.
- Add `RouteSpeedCard` and `build_speed_cards` to the existing `use crate::speed::…` import.

### 4. New file: `templates/speed_card.html`

Extract from `templates/speed.html` the card div and its Chart.js initializer into a standalone fragment:

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

### 5. Update `templates/speed.html`

Replace the inline card div and the separate `<script>` loop block with a single loop using `{% include %}`:

```html
<div class="card-grid">
  {% for card in &cards %}
  {% include "speed_card.html" %}
  {% endfor %}
</div>
```

Remove the now-redundant `<script>` block at the bottom of `speed.html` (the per-card Chart.js init moves into the include).

The top-level `<script src="...chart.umd.min.js">` CDN tag stays in `speed.html` — it's a page-level dependency, not a card concern.

## What Does Not Change

- `SpeedTemplate` struct stays in `handlers.rs` — it is the handler's response type, not a domain concern.
- `speed_page` handler logic is unchanged.
- All other templates and handlers are unaffected.

## Testing

- `cargo build` must succeed with no warnings.
- Visually verify the `/speed` page renders cards correctly (bar charts appear, agency/route names correct).
- Verify the agency filter still works.

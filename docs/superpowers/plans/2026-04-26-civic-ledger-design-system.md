# Civic Ledger Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved Civic Ledger design system and logo across Mobilispect's existing Askama templates.

**Architecture:** `templates/base.html` becomes the shared visual foundation for tokens, shell, logo, navigation, cards, tables, filters, badges, metrics, and print styles. Existing page templates are migrated in small batches to use the shared classes while preserving current routes, data bindings, Chart.js behavior, Leaflet behavior, and Askama expressions.

**Tech Stack:** Rust 2024, Axum, Askama templates, inline CSS, inline SVG logo, Chart.js, Leaflet, `cargo test`, `dotenvx run -- cargo run --bin mobilispect-server`.

---

## File Structure

- Modify `templates/base.html`: Civic Ledger tokens, logo, shared shell, shared component classes, optional title/nav/page blocks.
- Modify `templates/dashboard.html`: migrate dashboard shell, summary metrics, filters, table, badges.
- Modify `templates/scorecard.html`: migrate scorecard shell, benchmark table, note, filters, print handling.
- Modify `templates/speed.html`: migrate route speed overview shell, controls, card grid.
- Modify `templates/speed_card.html`: align route speed cards with shared card, badge, chart label classes.
- Modify `templates/hotspots.html`: align map header, sidebar, legend, table, delay colors with shared tokens.
- Modify `templates/route_detail.html`: migrate route trend page shell, callouts, chart cards.
- Modify `templates/route_speed_detail.html`: migrate route speed detail shell, stop strip, legends, classification badges.
- Modify `templates/report.html`: add Civic Ledger print header/logo treatment and align status colors.
- No Rust data model or route changes are planned.

## Task 1: Base Template Foundation

**Files:**
- Modify: `templates/base.html`

- [ ] **Step 1: Replace design tokens with Civic Ledger tokens**

In `templates/base.html`, replace the current `:root` block with:

```css
:root {
    --ink-900: #17202c;
    --ink-800: #1f2937;
    --ink-700: #334155;
    --ink-500: #627083;
    --ink-400: #7b8796;
    --paper: #f7f9fb;
    --surface: #ffffff;
    --surface-muted: #f1f5f9;
    --line: #d8dee8;
    --line-soft: #edf0f5;
    --civic-green: #2d8f67;
    --civic-green-bg: #e8f7ef;
    --civic-amber: #f0b84b;
    --civic-amber-bg: #fff4d8;
    --civic-red: #c9483f;
    --civic-red-bg: #fbe4e1;
    --link-blue: #2f6f9f;
    --link-blue-bg: #e7f1f7;
    --shadow-sm: 0 1px 2px rgb(23 32 44 / 0.05);
    --shadow: 0 8px 24px rgb(23 32 44 / 0.08);
    --radius: 8px;
}
```

- [ ] **Step 2: Replace animated logo with static Civic Ledger logo**

In `templates/base.html`, replace the contents of `.logo-icon` with:

```html
<svg width="34" height="34" viewBox="0 0 42 42" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <rect x="4" y="5" width="34" height="32" rx="7" fill="currentColor"/>
    <path d="M12 27L18 21L23 25L31 15" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M12 14H21" stroke="#8fd6bd" stroke-width="3" stroke-linecap="round"/>
    <circle cx="31" cy="15" r="3" fill="#f0b84b"/>
</svg>
```

Set `.logo-icon` to `color: var(--ink-900); background: transparent; box-shadow: none; border-radius: 8px;`.

- [ ] **Step 3: Add shared component classes**

Add these classes in `templates/base.html` after the existing common components section:

```css
.page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    gap: 1rem;
    margin-bottom: 1.5rem;
}
.page-title {
    font-size: 1.6rem;
    line-height: 1.2;
    color: var(--ink-900);
}
.page-subtitle {
    margin-top: 0.35rem;
    color: var(--ink-500);
    font-size: 0.9rem;
}
.metric-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 1rem;
    margin-bottom: 1.5rem;
}
.metric-card {
    background: var(--surface);
    border: 1px solid var(--line);
    border-radius: var(--radius);
    padding: 1rem 1.25rem;
    box-shadow: var(--shadow-sm);
}
.metric-label {
    color: var(--ink-500);
    font-size: 0.72rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.04em;
}
.metric-value {
    margin-top: 0.2rem;
    color: var(--ink-900);
    font-size: 2rem;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
}
.filter-bar,
.control-row {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    align-items: center;
}
.filter-bar {
    margin-bottom: 1.5rem;
}
.filter-label,
.control-label {
    color: var(--ink-500);
    font-size: 0.76rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.04em;
}
.filter-pill,
.filter-bar a,
.control-row a {
    display: inline-flex;
    align-items: center;
    min-height: 2rem;
    padding: 0.35rem 0.75rem;
    border: 1px solid var(--line);
    border-radius: 999px;
    background: var(--surface);
    color: var(--ink-700);
    font-size: 0.84rem;
    font-weight: 600;
    text-decoration: none;
    box-shadow: var(--shadow-sm);
}
.filter-pill:hover,
.filter-bar a:hover,
.control-row a:hover {
    border-color: var(--link-blue);
    color: var(--link-blue);
}
.filter-pill.active,
.filter-bar a.active,
.control-row a.active {
    background: var(--link-blue);
    border-color: var(--link-blue);
    color: white;
}
.data-table {
    width: 100%;
    border-collapse: collapse;
    background: var(--surface);
    border: 1px solid var(--line);
    border-radius: var(--radius);
    overflow: hidden;
    box-shadow: var(--shadow-sm);
}
.data-table th {
    padding: 0.75rem 1rem;
    text-align: left;
    color: var(--ink-500);
    background: var(--surface-muted);
    font-size: 0.74rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.04em;
}
.data-table td {
    padding: 0.75rem 1rem;
    border-top: 1px solid var(--line-soft);
    font-size: 0.9rem;
}
.data-table tr:hover td {
    background: #fbfdff;
}
.route-id {
    font-family: 'JetBrains Mono', monospace;
    font-weight: 700;
    color: var(--ink-900);
}
.status-good,
.green,
.gap-pos,
.faster,
.improve {
    color: var(--civic-green);
}
.status-watch,
.yellow,
.onpace,
.warn {
    color: #8a5a00;
}
.status-bad,
.red,
.gap-neg,
.slower,
.decline,
.poor {
    color: var(--civic-red);
}
.badge.green,
.badge.status-good {
    background: var(--civic-green-bg);
    color: var(--civic-green);
}
.badge.yellow,
.badge.status-watch {
    background: var(--civic-amber-bg);
    color: #8a5a00;
}
.badge.red,
.badge.status-bad {
    background: var(--civic-red-bg);
    color: var(--civic-red);
}
.badge.none,
.badge.status-none {
    background: var(--surface-muted);
    color: var(--ink-500);
}
.empty-state {
    padding: 3rem 1rem;
    color: var(--ink-500);
    text-align: center;
    background: var(--surface);
    border: 1px solid var(--line);
    border-radius: var(--radius);
}
@media (max-width: 720px) {
    header {
        align-items: flex-start;
        flex-direction: column;
        gap: 0.8rem;
    }
    nav {
        flex-wrap: wrap;
        gap: 0.8rem;
    }
    .container {
        padding: 1.25rem 0.8rem;
    }
    .page-header {
        align-items: flex-start;
        flex-direction: column;
    }
}
```

- [ ] **Step 4: Run template compilation tests**

Run:

```bash
cargo test
```

Expected: all tests pass. Existing warning about `avg_speed_mps` being unread is acceptable if unchanged.

- [ ] **Step 5: Commit**

```bash
git add templates/base.html
git commit -m "feat: add civic ledger design foundation"
```

## Task 2: Dashboard And Scorecard Migration

**Files:**
- Modify: `templates/dashboard.html`
- Modify: `templates/scorecard.html`

- [ ] **Step 1: Convert dashboard to extend the base shell**

Replace the top-level HTML wrapper in `templates/dashboard.html` with:

```html
{% extends "base.html" %}

{% block title %}Mobilispect — Transit Performance{% endblock %}
{% block nav_dashboard %}active{% endblock %}

{% block content %}
<div class="container">
  <div class="page-header">
    <div>
      <h1 class="page-title">Network Performance</h1>
      <p class="page-subtitle">Route accountability over the last {{ period_days }} days</p>
    </div>
  </div>

  <div class="metric-grid">
    <div class="metric-card">
      <div class="metric-label">Routes monitored</div>
      <div class="metric-value">{{ routes.len() }}</div>
    </div>
    <div class="metric-card">
      <div class="metric-label">Period</div>
      <div class="metric-value">{{ period_days }}d</div>
    </div>
  </div>

  <div class="filter-bar">
    <span class="filter-label">Agency</span>
    <a href="/" class="{% if active_agency.is_empty() %}active{% endif %}">All</a>
    {% for (slug, name) in &agencies %}
    <a href="/?agency={{ slug }}" class="{% if active_agency == *slug %}active{% endif %}">{{ name }}</a>
    {% endfor %}
  </div>

  <table class="data-table">
    <thead>
      <tr>
        <th>Route</th>
        <th>Name</th>
        <th>On-time %</th>
        <th>Avg delay</th>
        <th>Trips run</th>
        <th>Status</th>
      </tr>
    </thead>
    <tbody>
      {% for route in routes %}
      <tr>
        <td><span class="route-id">{% if let Some(n) = agency_names.get(route.agency_id.as_str()) %}{{ n }} {% endif %}{{ route.short_name }}</span></td>
        <td>{{ route.long_name }}</td>
        <td>{{ route.on_time_display() }}</td>
        <td>{{ route.delay_display() }}</td>
        <td>{% if let Some(r) = route.trips_run %}{{ r }}{% else %}0{% endif %} / {% if let Some(t) = route.trips_total %}{{ t }}{% else %}0{% endif %}</td>
        <td><span class="badge {{ route.status_class() }}">{{ route.status_label() }}</span></td>
      </tr>
      {% endfor %}
    </tbody>
  </table>
</div>
{% endblock %}
```

- [ ] **Step 2: Convert scorecard to extend the base shell**

Remove the standalone `<html>`, `<head>`, `<style>`, `<body>`, header, and footer from `templates/scorecard.html`. Keep the existing Askama table logic and wrap content with:

```html
{% extends "base.html" %}

{% block title %}Mobilispect — Global Scorecard{% endblock %}

{% block content %}
<div class="container">
  <div class="page-header">
    <div>
      <h1 class="page-title">Global Scorecard</h1>
      <p class="page-subtitle">Route performance compared with world-class transit benchmarks</p>
    </div>
  </div>
  <!-- keep existing summary metrics, note, filter, table, and footnotes here, converted to metric-grid, metric-card, filter-bar, data-table -->
</div>
{% endblock %}
```

Use these exact class conversions inside the retained scorecard markup:

```text
summary-bar -> metric-grid
stat -> metric-card
label -> metric-label
value -> metric-value
table -> table class="data-table"
filter-bar remains filter-bar
route-id remains route-id
note becomes card with inline margin-bottom removed
```

- [ ] **Step 3: Run tests**

```bash
cargo test
```

Expected: all tests pass and Askama compiles both migrated templates.

- [ ] **Step 4: Commit**

```bash
git add templates/dashboard.html templates/scorecard.html
git commit -m "feat: apply civic ledger to dashboard scorecard"
```

## Task 3: Route Speed Pages

**Files:**
- Modify: `templates/speed.html`
- Modify: `templates/speed_card.html`
- Modify: `templates/route_speed_detail.html`

- [ ] **Step 1: Convert `speed.html` to base shell**

Use `base.html` blocks and keep Chart.js in `extra_scripts`:

```html
{% extends "base.html" %}

{% block title %}Mobilispect — Route Speeds{% endblock %}
{% block nav_speed %}active{% endblock %}

{% block extra_head %}
<script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
{% endblock %}

{% block content %}
<div class="container">
  <div class="page-header">
    <div>
      <h1 class="page-title">Route Speeds</h1>
      <p class="page-subtitle">Scheduled and actual speed evidence by route</p>
    </div>
  </div>
  <!-- keep existing controls and cards loop, converted to shared classes -->
</div>
{% endblock %}
```

Convert `.controls` to a stacked block using existing `.control-row`, and remove duplicate CSS already supplied by `base.html`.

- [ ] **Step 2: Align speed card styles**

In `templates/speed_card.html`, keep current data bindings and chart canvases. Use these class names for visible wrappers:

```html
<div class="card speed-card">
  <div class="route-header">
    <div>
      <div class="route-id">{{ card.route_label }}</div>
      <div class="route-name">{{ card.route_name }}</div>
    </div>
    {% if let Some(classification) = card.classification %}
    <span class="badge badge--{{ classification.css_class() }}">{{ classification.label() }}</span>
    {% endif %}
  </div>
  <!-- keep existing chart rows and canvas IDs -->
</div>
```

If the current field names differ, preserve the existing Askama expressions and only change classes.

- [ ] **Step 3: Convert `route_speed_detail.html` to base shell**

Wrap with:

```html
{% extends "base.html" %}

{% block title %}Mobilispect — Route {{ short_name }} Speed Detail{% endblock %}

{% block extra_head %}
<script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
{% endblock %}

{% block content %}
<div class="container route-speed-detail">
  <div class="page-header">
    <div>
      <h1 class="page-title">Route {{ short_name }} — {{ long_name }}</h1>
      <p class="page-subtitle">Stop spacing and speed trend evidence</p>
    </div>
    <div style="display:flex;align-items:center;gap:1rem;flex-wrap:wrap;">
      {% if let Some(class) = classification %}
      <span class="badge badge--{{ class.css_class() }}">{{ class.label() }}</span>
      {% endif %}
      <a class="filter-pill" href="/speed?agency={{ agency_id }}">Back to speed overview</a>
    </div>
  </div>
  <!-- keep existing direction loop, stop strip, charts, and script -->
</div>
{% endblock %}
```

Move the existing `<script>` block into `{% block extra_scripts %}`.

- [ ] **Step 4: Add speed-specific CSS to `extra_head` only for unique layouts**

Keep only layout classes that are not shared:

```css
.card-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.speed-card { padding: 1rem 1.25rem; }
.route-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }
.route-name { margin-top: 0.15rem; color: var(--ink-500); font-size: 0.82rem; }
.chart-row { display: flex; gap: 0.75rem; }
.chart-col { flex: 1; min-width: 0; }
.chart-title { margin-bottom: 0.25rem; color: var(--ink-700); font-size: 0.8rem; font-weight: 700; text-align: center; }
.direction-card { margin-bottom: 2rem; overflow: hidden; }
.direction-header { padding: 0.75rem 1.25rem; border-bottom: 1px solid var(--line); background: var(--surface-muted); color: var(--ink-700); font-weight: 700; }
.direction-body { padding: 1.25rem; }
.section-label { margin-bottom: 0.75rem; color: var(--ink-500); font-size: 0.72rem; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; }
.stop-strip-wrap { overflow-x: auto; padding-bottom: 0.5rem; margin-bottom: 0.5rem; }
.stop-strip { display: flex; align-items: flex-start; min-width: max-content; padding: 4px 0; }
.stop-node { display: flex; flex-direction: column; align-items: center; width: 56px; flex-shrink: 0; }
.stop-dot { width: 10px; height: 10px; border-radius: 50%; background: var(--link-blue); margin-top: 6px; flex-shrink: 0; }
.stop-dot.terminal { background: var(--civic-green); }
.stop-name { margin-top: 5px; max-width: 52px; color: var(--ink-500); font-size: 9px; line-height: 1.3; text-align: center; word-break: break-word; }
.stop-seg { display: flex; flex-direction: column; align-items: center; justify-content: flex-start; padding-top: 10px; flex-shrink: 0; min-width: 24px; }
.seg-line { height: 3px; border-radius: 2px; background: var(--link-blue); min-width: 4px; }
.seg-line.outlier, .seg-line.slow { background: var(--civic-amber); height: 5px; }
.seg-label { margin-top: 3px; color: var(--ink-500); font-size: 9px; white-space: nowrap; }
.seg-label.outlier, .seg-label.slow { color: #8a5a00; font-weight: 700; }
.strip-caption { margin-bottom: 1.25rem; color: var(--ink-500); font-size: 0.75rem; }
.section-divider { height: 1px; background: var(--line-soft); margin: 1.25rem 0; }
.chart-legend { display: flex; gap: 1rem; margin-top: 0.5rem; flex-wrap: wrap; }
.legend-item { display: flex; align-items: center; gap: 0.3rem; color: var(--ink-500); font-size: 0.75rem; }
.legend-swatch { width: 20px; height: 3px; border-radius: 2px; }
@media (max-width: 900px) { .card-grid { grid-template-columns: 1fr; } .chart-row { flex-direction: column; } }
```

- [ ] **Step 5: Run tests**

```bash
cargo test
```

Expected: all tests pass and all speed templates compile.

- [ ] **Step 6: Commit**

```bash
git add templates/speed.html templates/speed_card.html templates/route_speed_detail.html
git commit -m "feat: apply civic ledger to speed views"
```

## Task 4: Hotspots And Route Detail

**Files:**
- Modify: `templates/hotspots.html`
- Modify: `templates/route_detail.html`

- [ ] **Step 1: Align hotspot colors and shell**

Keep `hotspots.html` standalone if full-height Leaflet layout is simpler, but replace header and color tokens with Civic Ledger values:

```css
body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: var(--paper); color: var(--ink-900); display: flex; flex-direction: column; height: 100vh; }
header { background: var(--surface); color: var(--ink-900); border-bottom: 1px solid var(--line); padding: 0.85rem 1.5rem; display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; }
header h1 { font-size: 1.1rem; font-weight: 700; }
header nav a { color: var(--ink-500); font-size: 0.85rem; font-weight: 600; text-decoration: none; margin-left: 1.25rem; }
header nav a:hover { color: var(--link-blue); }
.sidebar { width: 360px; overflow-y: auto; background: var(--surface); border-left: 1px solid var(--line); flex-shrink: 0; }
.sidebar-header { padding: 1rem; border-bottom: 1px solid var(--line); background: var(--surface-muted); }
.legend { display: flex; gap: 0.75rem; flex-wrap: wrap; padding: 0.75rem 1rem; border-bottom: 1px solid var(--line); background: var(--surface); font-size: 0.75rem; }
```

Update the delay colors in CSS, legend markup, and JavaScript:

```javascript
function delayColor(d) {
  if (d === null) return '#2d8f67';
  if (d >= 300) return '#c9483f';
  if (d >= 60)  return '#f0b84b';
  if (d > 0)    return '#f0b84b';
  return '#2d8f67';
}
```

- [ ] **Step 2: Convert route detail to base shell**

Wrap `route_detail.html` with:

```html
{% extends "base.html" %}

{% block title %}Mobilispect — Route {{ trend.short_name }}{% endblock %}

{% block extra_head %}
<script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
{% endblock %}

{% block content %}
<div class="container">
  <div class="page-header">
    <div>
      <h1 class="page-title">Route {{ trend.short_name }} — {{ trend.long_name }}</h1>
      <p class="page-subtitle">Speed and on-time trend evidence</p>
    </div>
    <a class="filter-pill" href="/">Back to dashboard</a>
  </div>
  <!-- keep existing callout metrics and chart cards, converted to metric-grid and card -->
</div>
{% endblock %}
```

Move the existing Chart.js initialization into `{% block extra_scripts %}` and use these chart colors:

```javascript
new Chart(document.getElementById('speedChart'), {
  type: 'line',
  data: { labels, datasets: [{ label: 'Speed km/h', data: speeds, ...lineOpts('#2f6f9f') }] },
  options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: false } } },
});

new Chart(document.getElementById('ontimeChart'), {
  type: 'line',
  data: { labels, datasets: [{ label: 'On-time %', data: ontimes, ...lineOpts('#2d8f67') }] },
  options: { plugins: { legend: { display: false } }, scales: { y: { min: 0, max: 100 } } },
});
```

- [ ] **Step 3: Run tests**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add templates/hotspots.html templates/route_detail.html
git commit -m "feat: apply civic ledger to hotspot route views"
```

## Task 5: Print Report

**Files:**
- Modify: `templates/report.html`

- [ ] **Step 1: Add print logo and Civic Ledger print colors**

In `templates/report.html`, keep the existing print-focused standalone HTML but add this header block before the report title:

```html
<div class="report-brand">
  <svg class="report-mark" viewBox="0 0 42 42" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <rect x="4" y="5" width="34" height="32" rx="7" fill="#17202c"/>
    <path d="M12 27L18 21L23 25L31 15" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M12 14H21" stroke="#17202c" stroke-width="3" stroke-linecap="round"/>
    <circle cx="31" cy="15" r="3" fill="#17202c"/>
  </svg>
  <div>
    <div class="report-name">Mobilispect</div>
    <div class="report-kicker">Transit performance accountability</div>
  </div>
</div>
```

Add these styles:

```css
.report-brand { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 1.5rem; font-family: sans-serif; }
.report-mark { width: 34px; height: 34px; }
.report-name { font-size: 1rem; font-weight: 800; color: #17202c; }
.report-kicker { margin-top: 0.1rem; color: #627083; font-size: 0.75rem; }
.poor { color: #c9483f; font-weight: bold; }
.ok { color: #2d8f67; }
.warn { color: #8a5a00; }
```

- [ ] **Step 2: Run tests**

```bash
cargo test
```

Expected: all tests pass and report template compiles.

- [ ] **Step 3: Commit**

```bash
git add templates/report.html
git commit -m "feat: add civic ledger print report styling"
```

## Task 6: Browser Verification And Polish

**Files:**
- Modify only templates that fail visual verification.

- [ ] **Step 1: Start server through dotenvx**

Run:

```bash
dotenvx run -- cargo run --bin mobilispect-server
```

Expected: server starts without config or secret-loading errors.

- [ ] **Step 2: Verify key routes manually**

Open these pages:

```text
http://localhost:3000/
http://localhost:3000/scorecard
http://localhost:3000/speed
http://localhost:3000/hotspots
http://localhost:3000/report
```

Expected:

- Header logo is static and legible.
- Page layouts do not overlap at desktop width.
- Tables remain readable and dense.
- Status badges include text and state color.
- Scorecard and report feel public-accountability oriented.
- Hotspot map still fills available height.

- [ ] **Step 3: Verify mobile widths**

Use browser responsive mode around 390px width. Expected:

- Header nav wraps cleanly.
- Metric cards stack.
- Tables remain horizontally readable or preserve current overflow behavior.
- Filter controls wrap without text clipping.
- No button, badge, or page title text overlaps.

- [ ] **Step 4: Run final test suite**

```bash
cargo test
```

Expected: all tests pass.

- [ ] **Step 5: Commit polish if needed**

If visual fixes were required:

```bash
git add templates
git commit -m "fix: polish civic ledger responsive views"
```

If no fixes were required, do not create an empty commit.

## Self-Review

- Spec coverage: logo, colors, typography, shared components, page applications, accessibility, testing, and rollout are each mapped to at least one task.
- Placeholder scan: the plan contains no unresolved markers or incomplete sections.
- Type consistency: no Rust type changes are planned; template field expressions should be preserved from existing files.
- Scope check: this plan is one visual-system migration and does not include backend, metrics, or database work.

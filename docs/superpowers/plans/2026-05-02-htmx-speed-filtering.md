# HTMX Speed Page Filtering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace full-page reloads on the `/speed` filter/sort controls with HTMX partial swaps that update only the content section.

**Architecture:** Extract the controls + card grid into `speed_content.html`. The `speed_page` handler checks for the `HX-Request` header and renders either a full page (`SpeedTemplate`) or the partial only (`SpeedContentTemplate`). Every filter/sort link carries `hx-get`, `hx-target`, and `hx-push-url` attributes alongside the existing `href` so the page degrades gracefully without JavaScript.

**Tech Stack:** Rust/Axum, Askama templates, HTMX 2.x (CDN)

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `templates/speed_content.html` | **Create** | Controls + card grid partial; the HTMX swap target |
| `templates/speed.html` | **Modify** | Thin shell — adds HTMX CDN, replaces inline content with `{% include %}` |
| `src/web/handlers.rs` | **Modify** | Add `SpeedContentTemplate`; branch `speed_page` on `HX-Request` header |

---

## Task 1: Partial response — failing test + implementation

**Files:**
- Modify: `src/web/handlers.rs`
- Create: `templates/speed_content.html`
- Modify: `templates/speed.html`

- [ ] **Step 1: Write the two failing E2E tests**

Add these tests inside the existing `#[cfg(test)]` block near the bottom of `src/web/handlers.rs`, after the `speed_page_filters_by_class_local` test:

```rust
#[tokio::test]
async fn speed_page_with_hx_request_returns_fragment_not_full_page() {
    let td = test_utils::setup().await;
    let state = AppState { db: td.db, config: test_config() };
    let app = build_router(state);

    let response = app
        .oneshot(
            Request::builder()
                .uri("/speed")
                .header("hx-request", "true")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);
    let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
        .await
        .unwrap();
    let html = String::from_utf8(bytes.to_vec()).unwrap();
    assert!(
        html.contains(r#"id="speed-content""#),
        "fragment must contain the swap target div"
    );
    assert!(
        !html.contains("<html"),
        "fragment must not contain a full HTML document"
    );
}

#[tokio::test]
async fn speed_page_without_hx_request_returns_full_page() {
    let td = test_utils::setup().await;
    let state = AppState { db: td.db, config: test_config() };
    let app = build_router(state);

    let response = app
        .oneshot(
            Request::builder()
                .uri("/speed")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);
    let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
        .await
        .unwrap();
    let html = String::from_utf8(bytes.to_vec()).unwrap();
    assert!(
        html.contains("<html"),
        "full page response must contain an <html> element"
    );
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

```bash
cargo test speed_page_with_hx_request_returns_fragment_not_full_page speed_page_without_hx_request_returns_full_page 2>&1 | tail -20
```

Expected: both fail. The first fails because there is no `id="speed-content"` div; the second may pass already — that's fine, it will stay green.

- [ ] **Step 3: Create `templates/speed_content.html`**

This file contains everything that was inside `{% block content %}` in `speed.html` (the three control rows and card grid), wrapped in a `<div id="speed-content">`. The outer `<div class="container">` stays in `speed.html`.

```html
<div id="speed-content">
  <div class="control-row">
    <span class="control-label">Agency:</span>
    <a href="/speed{% if active_sort != "name" %}?sort={{ active_sort }}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}{% elif !active_class.is_empty() %}?class={{ active_class }}{% endif %}"
       class="{% if active_agency.is_empty() %}active{% endif %}">All</a>
    {% for (slug, name) in &agencies %}
    <a href="/speed?agency={{ slug }}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       class="{% if active_agency == *slug %}active{% endif %}">{{ name }}</a>
    {% endfor %}
  </div>

  <div class="control-row">
    <span class="control-label">Sort by:</span>
    <a href="/speed{% if !active_agency.is_empty() %}?agency={{ active_agency }}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}{% elif !active_class.is_empty() %}?class={{ active_class }}{% endif %}"
       class="{% if active_sort == "name" %}active{% endif %}">Name</a>
    <a href="/speed?sort=scheduled{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       class="{% if active_sort == "scheduled" %}active{% endif %}">Slowest scheduled</a>
    <a href="/speed?sort=actual{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       class="{% if active_sort == "actual" %}active{% endif %}">Slowest actual</a>
  </div>

  <div class="control-row" style="margin-bottom:1.5rem;">
    <span class="control-label">Class:</span>
    <a href="/speed{% if !active_agency.is_empty() %}?agency={{ active_agency }}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}{% elif active_sort != "name" %}?sort={{ active_sort }}{% endif %}"
       class="{% if active_class.is_empty() %}active{% endif %}">All</a>
    <a href="/speed?class=local{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       class="{% if active_class == "local" %}active{% endif %}">Local</a>
    <a href="/speed?class=rapid{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       class="{% if active_class == "rapid" %}active{% endif %}">Rapid</a>
    <a href="/speed?class=express{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       class="{% if active_class == "express" %}active{% endif %}">Express</a>
  </div>

  {% if cards.is_empty() %}
  <div class="empty-state">No speed data available yet. Data appears after the daily compute runs.</div>
  {% else %}
  <div class="card-grid">
    {% for card in &cards %}
    {% include "speed_card.html" %}
    {% endfor %}
  </div>
  {% endif %}
</div>
```

- [ ] **Step 4: Update `templates/speed.html` to include the partial**

Replace the three control rows and card grid block with a single include. The full file becomes:

```html
{% extends "base.html" %}

{% block title %}Mobilispect — Route Speeds{% endblock %}
{% block region_name %}{{ region_name }}{% endblock %}
{% block nav_speed %}active{% endblock %}

{% block extra_head %}
<script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
<style>
.controls { margin-bottom: 1.5rem; display: flex; flex-direction: column; gap: 0.4rem; }
.card-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 1rem; }
@media (max-width: 900px) { .card-grid { grid-template-columns: 1fr; } }
.chart-row { display: flex; gap: 0.75rem; }
.chart-col { flex: 1; min-width: 0; }
.chart-col:only-child { max-width: 50%; margin: 0 auto; }
.chart-title { font-size: 0.8rem; font-weight: 600; text-align: center; margin-bottom: 0.25rem; color: var(--ink-700); }
</style>
{% endblock %}

{% block content %}
<div class="container">
  <div class="page-header">
    <div>
      <h1 class="page-title">Route Speeds</h1>
      <p class="page-subtitle">Scheduled and actual speed evidence by route</p>
    </div>
  </div>
  {% include "speed_content.html" %}
</div>
{% endblock %}
```

- [ ] **Step 5: Add `SpeedContentTemplate` struct and update `speed_page` in `src/web/handlers.rs`**

Add the import for `HeaderMap` to the existing `axum` use block at the top of the file:

```rust
use axum::{
    extract::{Query, State},
    http::HeaderMap,
    response::Html,
};
```

Add the new template struct directly after the `SpeedTemplate` struct definition (around line 341):

```rust
#[derive(Template)]
#[template(path = "speed_content.html")]
struct SpeedContentTemplate {
    cards: Vec<RouteSpeedCard>,
    agencies: Vec<(String, String)>,
    active_agency: String,
    active_sort: String,
    active_class: String,
}
```

Replace the `speed_page` function signature and closing render block. The query logic (building `cards`, `active_agency`, `active_sort`, `active_class`, `agencies`) is **unchanged**. Only the function signature and the final render block change:

```rust
pub async fn speed_page(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<SpeedParams>,
) -> Html<String> {
    // ... all existing logic unchanged up to the final render ...

    if headers.contains_key("hx-request") {
        let tmpl = SpeedContentTemplate {
            cards,
            agencies,
            active_agency,
            active_sort,
            active_class,
        };
        Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
    } else {
        let tmpl = SpeedTemplate {
            region_name: state.config.region.name.clone(),
            cards,
            agencies,
            active_agency,
            active_sort,
            active_class,
        };
        Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
    }
}
```

- [ ] **Step 6: Run the target tests**

```bash
cargo test speed_page_with_hx_request_returns_fragment_not_full_page speed_page_without_hx_request_returns_full_page 2>&1 | tail -15
```

Expected: both pass.

- [ ] **Step 7: Run the full test suite**

```bash
cargo test 2>&1 | grep -E "^test result|FAILED"
```

Expected: `test result: ok. N passed; 0 failed`

- [ ] **Step 8: Commit**

```bash
git add templates/speed_content.html templates/speed.html src/web/handlers.rs
git commit -m "feat(speed): add SpeedContentTemplate for HTMX partial responses"
```

---

## Task 2: Wire up HTMX attributes and CDN

**Files:**
- Modify: `templates/speed_content.html`
- Modify: `templates/speed.html`

- [ ] **Step 1: Add `hx-*` attributes to every link in `templates/speed_content.html`**

Every `<a>` tag needs three attributes added. The value of `hx-get` is identical to `href`. Replace the content of `speed_content.html` with:

```html
<div id="speed-content">
  <div class="control-row">
    <span class="control-label">Agency:</span>
    {% set all_agency_url %}
      /speed{% if active_sort != "name" %}?sort={{ active_sort }}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}{% elif !active_class.is_empty() %}?class={{ active_class }}{% endif %}
    {% endset %}
    <a href="/speed{% if active_sort != "name" %}?sort={{ active_sort }}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}{% elif !active_class.is_empty() %}?class={{ active_class }}{% endif %}"
       hx-get="/speed{% if active_sort != "name" %}?sort={{ active_sort }}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}{% elif !active_class.is_empty() %}?class={{ active_class }}{% endif %}"
       hx-target="#speed-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_agency.is_empty() %}active{% endif %}">All</a>
    {% for (slug, name) in &agencies %}
    <a href="/speed?agency={{ slug }}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       hx-get="/speed?agency={{ slug }}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       hx-target="#speed-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_agency == *slug %}active{% endif %}">{{ name }}</a>
    {% endfor %}
  </div>

  <div class="control-row">
    <span class="control-label">Sort by:</span>
    <a href="/speed{% if !active_agency.is_empty() %}?agency={{ active_agency }}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}{% elif !active_class.is_empty() %}?class={{ active_class }}{% endif %}"
       hx-get="/speed{% if !active_agency.is_empty() %}?agency={{ active_agency }}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}{% elif !active_class.is_empty() %}?class={{ active_class }}{% endif %}"
       hx-target="#speed-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_sort == "name" %}active{% endif %}">Name</a>
    <a href="/speed?sort=scheduled{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       hx-get="/speed?sort=scheduled{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       hx-target="#speed-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_sort == "scheduled" %}active{% endif %}">Slowest scheduled</a>
    <a href="/speed?sort=actual{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       hx-get="/speed?sort=actual{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       hx-target="#speed-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_sort == "actual" %}active{% endif %}">Slowest actual</a>
  </div>

  <div class="control-row" style="margin-bottom:1.5rem;">
    <span class="control-label">Class:</span>
    <a href="/speed{% if !active_agency.is_empty() %}?agency={{ active_agency }}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}{% elif active_sort != "name" %}?sort={{ active_sort }}{% endif %}"
       hx-get="/speed{% if !active_agency.is_empty() %}?agency={{ active_agency }}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}{% elif active_sort != "name" %}?sort={{ active_sort }}{% endif %}"
       hx-target="#speed-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_class.is_empty() %}active{% endif %}">All</a>
    <a href="/speed?class=local{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       hx-get="/speed?class=local{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       hx-target="#speed-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_class == "local" %}active{% endif %}">Local</a>
    <a href="/speed?class=rapid{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       hx-get="/speed?class=rapid{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       hx-target="#speed-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_class == "rapid" %}active{% endif %}">Rapid</a>
    <a href="/speed?class=express{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       hx-get="/speed?class=express{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       hx-target="#speed-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_class == "express" %}active{% endif %}">Express</a>
  </div>

  {% if cards.is_empty() %}
  <div class="empty-state">No speed data available yet. Data appears after the daily compute runs.</div>
  {% else %}
  <div class="card-grid">
    {% for card in &cards %}
    {% include "speed_card.html" %}
    {% endfor %}
  </div>
  {% endif %}
</div>
```

Note: Askama does not support `{% set %}` — the URL expressions are repeated verbatim in `href` and `hx-get`. This is intentional and correct.

- [ ] **Step 2: Add HTMX CDN to `templates/speed.html`**

Add the HTMX script tag to the `{% block extra_head %}` block, after the Chart.js script:

```html
{% block extra_head %}
<script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/htmx.org@2.0.4/dist/htmx.min.js"></script>
<style>
.controls { margin-bottom: 1.5rem; display: flex; flex-direction: column; gap: 0.4rem; }
.card-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 1rem; }
@media (max-width: 900px) { .card-grid { grid-template-columns: 1fr; } }
.chart-row { display: flex; gap: 0.75rem; }
.chart-col { flex: 1; min-width: 0; }
.chart-col:only-child { max-width: 50%; margin: 0 auto; }
.chart-title { font-size: 0.8rem; font-weight: 600; text-align: center; margin-bottom: 0.25rem; color: var(--ink-700); }
</style>
{% endblock %}
```

- [ ] **Step 3: Run the full test suite**

```bash
cargo test 2>&1 | grep -E "^test result|FAILED"
```

Expected: `test result: ok. N passed; 0 failed`

- [ ] **Step 4: Build to confirm no template compilation errors**

```bash
cargo build 2>&1 | grep -E "^error"
```

Expected: no output (clean build).

- [ ] **Step 5: Commit**

```bash
git add templates/speed_content.html templates/speed.html
git commit -m "feat(speed): wire HTMX attributes and CDN for partial filter swaps"
```

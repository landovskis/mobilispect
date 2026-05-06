# HTMX Speed Page Filtering & Sorting

**Date:** 2026-05-02
**Status:** Approved

## Problem

Every filter and sort click on `/speed` triggers a full page reload. The server re-renders the entire document — `<head>`, nav, controls, and all route cards — even though only the card grid (and active-state on the controls) changes. This causes a visible page flash and wastes bandwidth.

## Goal

Replace full-page navigation with in-place partial swaps via HTMX. Filtering and sorting update only the content section without reloading the chrome. The page must continue to work without JavaScript (progressive enhancement).

## Architecture

### Template split

**`templates/speed_content.html`** (new)
- Contains the three control rows (Agency, Sort by, Class) and the card grid.
- Wrapped in `<div id="speed-content">` so HTMX has a stable swap target.
- Every filter/sort `<a>` tag carries three HTMX attributes alongside the existing `href`:
  - `hx-get="<same URL as href>"` — triggers a partial fetch
  - `hx-target="#speed-content"` — replaces this div with the server response
  - `hx-push-url="true"` — keeps the browser URL and back/forward buttons in sync
- The full content (controls + cards) is swapped, not just the card grid. This is required so the active-state class on control links reflects the newly selected filter.

**`templates/speed.html`** (modified)
- Becomes a thin shell: extends `base.html`, keeps the `{% block extra_head %}` with Chart.js, adds the HTMX CDN script, and renders `{% include "speed_content.html" %}` inside `.container`.

**HTMX CDN** added to `speed.html`'s `{% block extra_head %}` — scoped to only the speed page, not loaded globally.

### Handler change

`speed_page` in `src/web/handlers.rs` gains a `HeaderMap` parameter. After computing cards (identical logic to today), it branches on the presence of the `hx-request` header:

- **HX-Request present** → render `SpeedContentTemplate` (partial only)
- **HX-Request absent** → render `SpeedTemplate` (full page, unchanged)

A new `SpeedContentTemplate` struct is added:

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

`SpeedTemplate` loses `region_name` from its template (it moves to the base shell) — no, `region_name` stays in `SpeedTemplate` for the full-page path. `SpeedContentTemplate` does not need `region_name`.

## Data Flow

```
User clicks filter link
  │
  ├─ JS disabled → browser follows href → full GET → SpeedTemplate rendered
  │
  └─ JS enabled  → HTMX intercepts → partial GET (HX-Request header) →
                    SpeedContentTemplate rendered →
                    HTMX replaces #speed-content → URL updated via pushState
```

## Files Changed

| File | Change |
|------|--------|
| `templates/speed.html` | Thin shell; add HTMX CDN; `{% include "speed_content.html" %}` |
| `templates/speed_content.html` | New — controls + card grid with `hx-*` attributes |
| `src/web/handlers.rs` | Add `SpeedContentTemplate`; branch in `speed_page` on `hx-request` header |

## Testing

- Existing handler unit/integration tests remain valid (they don't send `HX-Request`).
- New E2E test: send `GET /speed?sort=actual` with `HX-Request: true` header, assert response is an HTML fragment (contains `id="speed-content"`, does not contain `<html>`).
- New E2E test: same request without header, assert full page returned (contains `<html>`).

## Out of Scope

- HTMX on any page other than `/speed`.
- Loading indicators or transition animations.
- Replacing the Chart.js charts with HTMX-driven updates.

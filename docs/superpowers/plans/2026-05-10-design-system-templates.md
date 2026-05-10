# Design System Templates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract every Lumina design system component into Askama macros in `templates/macros.html` and migrate all existing templates to use them.

**Architecture:** Single `templates/macros.html` holds all macros (badge, stat, section_label, filter_pill, metric_card, alert). Each template adds `{% import "macros.html" as ui %}` and replaces inline component HTML with macro calls. New semantic badge variants (good/bad/mixed/neutral) replace the old class-based variants. CSS for shared components moves to `base.html` so it's available globally.

**Tech Stack:** Rust 2024, Askama 0.15 (compiled templates), CSS custom properties, Tailwind-adjacent utility classes.

---

## File Map

| File | Action |
|------|--------|
| `templates/macros.html` | **Create** — 6 macros: badge, stat, section_label, filter_pill, metric_card, alert |
| `templates/base.html` | **Modify** — add badge--good/bad/mixed/neutral, spacing-stat-*, section-label, alert CSS |
| `templates/speed_card.html` | **Modify** — import macros, replace badge and stats |
| `templates/speed.html` | **Modify** — remove spacing-stat-* CSS block (moved to base.html) |
| `templates/route_speed_detail.html` | **Modify** — import macros, replace badge and section_label, remove local CSS |
| `templates/dashboard.html` | **Modify** — import macros, replace badge and metric_card |
| `templates/scorecard.html` | **Modify** — import macros, replace badge and metric_card |
| `templates/route_detail.html` | **Modify** — import macros, replace filter_pill |
| `src/speed/card.rs` | **Modify** — add `avg_stop_spacing_variant()`, `RouteClass::display_label()` |
| `src/metrics/mod.rs` | **Modify** — add `RouteSummary::badge_variant()`, `ScorecardRoute::badge_variant()` |

---

### Task 1: Add `avg_stop_spacing_variant()` to `RouteSpeedCard`

**Files:**
- Modify: `src/speed/card.rs`

The current `avg_stop_spacing_class()` returns `"none"/"local"/"rapid"/"express"` (tied to RouteClass CSS). We need `avg_stop_spacing_variant()` returning semantic `"neutral"/"bad"/"good"` for the stat macro. Logic: None → neutral; < 300m → bad (below all class lower bounds); ≥ 300m → good (Local starts at 300m, so any classified stop spacing is in-range).

- [ ] **Step 1: Write the failing tests**

Add to the `#[cfg(test)]` module in `src/speed/card.rs` (after the existing `spacing_unit_kilometres` test, around line 483):

```rust
#[test]
fn stop_spacing_variant_neutral_when_no_data() {
    assert_eq!(card_with_spacing(None).avg_stop_spacing_variant(), "neutral");
}

#[test]
fn stop_spacing_variant_bad_below_300m() {
    assert_eq!(card_with_spacing(Some(0.0)).avg_stop_spacing_variant(), "bad");
    assert_eq!(card_with_spacing(Some(299.9)).avg_stop_spacing_variant(), "bad");
}

#[test]
fn stop_spacing_variant_good_at_or_above_300m() {
    assert_eq!(card_with_spacing(Some(300.0)).avg_stop_spacing_variant(), "good");
    assert_eq!(card_with_spacing(Some(1500.0)).avg_stop_spacing_variant(), "good");
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test stop_spacing_variant 2>&1 | grep -E "FAILED|error"
```

Expected: compilation error — `avg_stop_spacing_variant` not found.

- [ ] **Step 3: Add the implementation**

Add this method to `RouteSpeedCard`'s `impl` block in `src/speed/card.rs`, after `avg_stop_spacing_unit()` (line 47):

```rust
    pub fn avg_stop_spacing_variant(&self) -> &'static str {
        match self.avg_stop_spacing_m {
            None => "neutral",
            Some(m) if m < 300.0 => "bad",
            Some(_) => "good",
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cargo test stop_spacing_variant
```

Expected: 3 tests pass, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add src/speed/card.rs
git commit -m "feat(speed): add avg_stop_spacing_variant() returning semantic good/bad/neutral"
```

---

### Task 2: Add `badge_variant()` to `RouteSummary` and `ScorecardRoute`

**Files:**
- Modify: `src/metrics/mod.rs`

The new badge macro expects semantic variant names (`"good"/"bad"/"mixed"/"neutral"`). Both `RouteSummary` and `ScorecardRoute` currently have `status_class()` returning old class names (`"green"/"yellow"/"red"/"none"`). We add `badge_variant()` alongside — the old method will be removed in Task 10 once templates are migrated.

- [ ] **Step 1: Write the failing tests**

Add to the `#[cfg(test)]` block in `src/metrics/mod.rs`. Use the existing `make_scorecard_route()` helper (line 835) for `ScorecardRoute` tests. Add a new helper for `RouteSummary`.

Add after the `status_class_is_none_without_data` test (around line 978):

```rust
// ── RouteSummary::badge_variant tests ──────────────────────────────────────

fn make_route_summary(on_time: Option<f64>) -> RouteSummary {
    RouteSummary {
        agency_id: "stm".into(),
        route_id: "R1".into(),
        short_name: "1".into(),
        long_name: "Route 1".into(),
        avg_on_time_pct: on_time,
        avg_delay_secs: None,
        trips_run: None,
        trips_total: None,
        days_measured: None,
    }
}

#[test]
fn route_summary_badge_variant_good_at_or_above_80() {
    assert_eq!(make_route_summary(Some(80.0)).badge_variant(), "good");
    assert_eq!(make_route_summary(Some(95.0)).badge_variant(), "good");
}

#[test]
fn route_summary_badge_variant_mixed_between_60_and_80() {
    assert_eq!(make_route_summary(Some(60.0)).badge_variant(), "mixed");
    assert_eq!(make_route_summary(Some(79.9)).badge_variant(), "mixed");
}

#[test]
fn route_summary_badge_variant_bad_below_60() {
    assert_eq!(make_route_summary(Some(59.9)).badge_variant(), "bad");
    assert_eq!(make_route_summary(Some(0.0)).badge_variant(), "bad");
}

#[test]
fn route_summary_badge_variant_neutral_when_no_data() {
    assert_eq!(make_route_summary(None).badge_variant(), "neutral");
}

// ── ScorecardRoute::badge_variant tests ──────────────────────────────────────

#[test]
fn scorecard_badge_variant_good_at_or_above_ceiling() {
    let r = make_scorecard_route(Some(96.0), None);
    assert_eq!(r.badge_variant(&89.0, &96.0), "good");
}

#[test]
fn scorecard_badge_variant_mixed_between_floor_and_ceiling() {
    let r = make_scorecard_route(Some(91.0), None);
    assert_eq!(r.badge_variant(&89.0, &96.0), "mixed");
}

#[test]
fn scorecard_badge_variant_bad_under_floor() {
    let r = make_scorecard_route(Some(71.0), None);
    assert_eq!(r.badge_variant(&89.0, &96.0), "bad");
}

#[test]
fn scorecard_badge_variant_neutral_when_no_data() {
    let r = make_scorecard_route(None, None);
    assert_eq!(r.badge_variant(&89.0, &96.0), "neutral");
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test badge_variant 2>&1 | grep -E "FAILED|error"
```

Expected: compilation error — `badge_variant` not found on either type.

- [ ] **Step 3: Add `RouteSummary::badge_variant()`**

In `src/metrics/mod.rs`, add after `RouteSummary::status_label()` (after line ~263):

```rust
    pub fn badge_variant(&self) -> &'static str {
        match self.avg_on_time_pct {
            Some(pct) if pct >= 80.0 => "good",
            Some(pct) if pct >= 60.0 => "mixed",
            Some(_) => "bad",
            None => "neutral",
        }
    }
```

- [ ] **Step 4: Add `ScorecardRoute::badge_variant()`**

In `src/metrics/mod.rs`, add after `ScorecardRoute::status_class()` (after line ~554):

```rust
    pub fn badge_variant(&self, floor_pct: &f64, ceiling_pct: &f64) -> &'static str {
        match self.avg_on_time_pct {
            Some(p) if p >= *ceiling_pct => "good",
            Some(p) if p >= *floor_pct => "mixed",
            Some(_) => "bad",
            None => "neutral",
        }
    }
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cargo test badge_variant
```

Expected: 8 unit tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/metrics/mod.rs
git commit -m "feat(metrics): add badge_variant() to RouteSummary and ScorecardRoute"
```

---

### Task 3: Create `templates/macros.html`

**Files:**
- Create: `templates/macros.html`

Askama compiles macros at build time. Each macro in this file is imported by templates with `{% import "macros.html" as ui %}`. Macro arguments are Rust expressions — `variant` args are `&str`, `active` arg is `bool`, other args are any `Display` type.

Note on the `stat` macro: it renders the two inner divs only (no outer wrapper), so callers keep their own `<div>` wrapper to control layout (e.g., `margin-left: auto`). This matches the current `speed_card.html` structure exactly.

- [ ] **Step 1: Create the file**

Create `templates/macros.html` with this content:

```html
{% macro badge(variant, label) %}
<span class="badge badge--{{ variant }}">{{ label }}</span>
{% endmacro badge %}

{% macro stat(label, value, unit, variant) %}
<div class="spacing-stat-label">{{ label }}</div>
<div class="spacing-stat-num{% if variant != "" %} spacing-{{ variant }}{% endif %}">{{ value }}<span class="spacing-stat-unit">{{ unit }}</span></div>
{% endmacro stat %}

{% macro section_label(text) %}
<div class="section-label">{{ text }}</div>
{% endmacro section_label %}

{% macro filter_pill(href, label, active) %}
<a class="filter-pill{% if active %} active{% endif %}" href="{{ href }}">{{ label }}</a>
{% endmacro filter_pill %}

{% macro metric_card(label, value, unit) %}
<div class="metric-card">
  <div class="metric-label">{{ label }}</div>
  <div class="metric-value">{{ value }}{{ unit }}</div>
</div>
{% endmacro metric_card %}

{% macro alert(variant, message) %}
<div class="alert alert--{{ variant }}">{{ message }}</div>
{% endmacro alert %}
```

- [ ] **Step 2: Verify it compiles**

```bash
cargo build 2>&1 | grep -E "error|warning.*unused"
```

Expected: clean build (no errors). Warnings about unused macros are fine — they'll be used in later tasks.

- [ ] **Step 3: Commit**

```bash
git add templates/macros.html
git commit -m "feat(templates): create macros.html with badge/stat/section_label/filter_pill/metric_card/alert"
```

---

### Task 4: Update `templates/base.html` CSS

**Files:**
- Modify: `templates/base.html`

Add four things to base.html's `<style>` block:
1. New badge variant classes (`badge--good/bad/mixed/neutral`) — after the existing badge--express rule at line 249
2. `spacing-stat-*` classes — these are currently in `speed.html` and will be removed from there in Task 6; add them to base.html with updated variant color names
3. `section-label` and `section-label-badge` — currently defined locally in `route_speed_detail.html` with grey color; move to base.html with the correct cinnabar color; local definition removed in Task 7
4. Alert CSS vars (in `:root`) and `.alert` classes

Do NOT remove existing old CSS yet — that's Task 10.

- [ ] **Step 1: Add badge variant classes**

In `templates/base.html`, after `.badge--express` (line 249), add:

```css
        .badge--good    { background: var(--civic-green-bg); color: var(--civic-green); }
        .badge--bad     { background: var(--civic-red-bg);   color: var(--civic-red); }
        .badge--mixed   { background: var(--civic-amber-bg); color: var(--civic-amber); }
        .badge--neutral { background: var(--link-blue-bg);   color: var(--link-blue); }
```

- [ ] **Step 2: Add spacing-stat and spacing variant classes**

In `templates/base.html`, in the `/* Specific blocks */` comment area at the end of the `<style>` block (around line 427), add:

```css
        /* Stat display — shared across speed cards and route detail */
        .spacing-stat-label { font-family:'Fira Code',monospace; font-size:0.58rem; letter-spacing:0.08em; color:var(--ink-400); text-transform:uppercase; margin-bottom:3px; }
        .spacing-stat-num   { font-family:'Cormorant Garamond',Georgia,serif; font-weight:300; font-size:1.8rem; line-height:1; }
        .spacing-stat-unit  { font-size:1rem; color:var(--ink-400); }
        .spacing-good       { color: var(--civic-green); }
        .spacing-bad        { color: var(--civic-red); }
        .spacing-mixed      { color: var(--civic-amber); }
        .spacing-neutral    { color: var(--link-blue); }
```

- [ ] **Step 3: Add section-label classes**

In the same `/* Specific blocks */` area of `base.html`, add:

```css
        /* Section label — Fira Code uppercase cinnabar */
        .section-label { font-family:'Fira Code',monospace; font-size:0.65rem; letter-spacing:0.1em; text-transform:uppercase; color:var(--civic-red); margin-bottom:0.75rem; }
        .section-label-badge { display:inline-block; background:var(--link-blue); color:white; font-size:0.68rem; padding:0.15rem 0.4rem; border-radius:3px; margin-left:0.5rem; vertical-align:middle; font-weight:600; text-transform:none; letter-spacing:0; }
        .section-label-badge.outlier { background:var(--civic-amber); }
        .section-label-badge.slow    { background:var(--civic-red); }
```

- [ ] **Step 4: Add alert CSS vars and classes**

In the `:root` block of `base.html` (after the last existing var, around line 38), add:

```css
          --al-info-bg: var(--link-blue-bg); --al-info-fg: var(--link-blue); --al-info-border: var(--link-blue);
          --al-ok-bg: var(--civic-green-bg); --al-ok-fg: var(--civic-green); --al-ok-border: var(--civic-green);
          --al-warn-bg: var(--civic-amber-bg); --al-warn-fg: var(--civic-amber); --al-warn-border: var(--civic-amber);
          --al-err-bg: var(--civic-red-bg);  --al-err-fg: var(--civic-red);  --al-err-border: var(--civic-red);
```

These vars reference other vars that already have dark-mode overrides, so no separate `html.dark` block needed.

In the `/* Specific blocks */` area, add:

```css
        .alert { padding:0.75rem 1rem; border-radius:var(--radius); border-left:3px solid; margin-bottom:1rem; font-size:0.875rem; }
        .alert--info { background:var(--al-info-bg); color:var(--al-info-fg); border-left-color:var(--al-info-border); }
        .alert--ok   { background:var(--al-ok-bg);   color:var(--al-ok-fg);   border-left-color:var(--al-ok-border); }
        .alert--warn { background:var(--al-warn-bg); color:var(--al-warn-fg); border-left-color:var(--al-warn-border); }
        .alert--err  { background:var(--al-err-bg);  color:var(--al-err-fg);  border-left-color:var(--al-err-border); }
```

- [ ] **Step 5: Build and verify**

```bash
cargo build 2>&1 | grep "error"
```

Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add templates/base.html
git commit -m "feat(css): add badge--good/bad/mixed/neutral, spacing-stat-*, section-label, and alert classes to base.html"
```

---

### Task 5: Update `templates/speed_card.html` to use macros

**Files:**
- Modify: `templates/speed_card.html`

Replace the inline badge and inline stat markup with macro calls. The `avg_stop_spacing_variant()` method (added in Task 1) drives the stat colour. Note: `avg_stop_spacing_class()` (old, returns "local"/"rapid"/"express") is still defined in Rust but no longer called after this task — it will be removed in Task 10.

The wrapping `<div style="margin-left:auto;">` around the stop spacing stat is preserved for layout.

- [ ] **Step 1: Rewrite `templates/speed_card.html`**

Replace the entire file content with:

```html
{% import "macros.html" as ui %}
<a href="/routes/{{ card.agency_id }}/{{ card.route_id }}/speed"
   style="text-decoration: none; color: inherit; display: block;">
<div class="card" style="padding:1rem 1.25rem;">
  <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:1rem;">
    <div>
      <div class="route-id">{{ card.agency_name }} {{ card.short_name }}</div>
      <div style="margin-top:0.15rem;color:var(--ink-500);font-size:0.82rem;">{{ card.long_name }}</div>
    </div>
    {% if let Some(class) = card.classification %}
    {{ ui::badge(variant="neutral", label=class.label()) }}
    {% endif %}
  </div>
  <div style="margin-top:0.75rem;display:flex;gap:1.5rem;border-top:1px solid var(--line-soft);padding-top:0.75rem;">
    <div>{{ ui::stat(label="Scheduled", value=card.avg_scheduled_speed_kmh_display(), unit=" km/h", variant="") }}</div>
    <div>{{ ui::stat(label="Actual", value=card.avg_actual_speed_kmh_display(), unit=" km/h", variant="") }}</div>
    <div style="margin-left:auto;">{{ ui::stat(label="Avg stop spacing", value=card.avg_stop_spacing_number(), unit=card.avg_stop_spacing_unit(), variant=card.avg_stop_spacing_variant()) }}</div>
  </div>
</div>
</a>
```

- [ ] **Step 2: Build and verify**

```bash
cargo build 2>&1 | grep "error"
```

Expected: no errors. The classification badge now renders as `badge--neutral` (oxford blue) instead of `badge--local/rapid/express`.

- [ ] **Step 3: Commit**

```bash
git add templates/speed_card.html
git commit -m "refactor(speed_card): use badge and stat macros"
```

---

### Task 6: Remove dead CSS from `templates/speed.html`

**Files:**
- Modify: `templates/speed.html`

After Task 5, the `spacing-stat-*` and `spacing-local/rapid/express/none` CSS classes in `speed.html` are dead — they've been replaced by the `base.html` versions (`spacing-stat-*` and `spacing-good/bad/mixed/neutral`). Remove them from the `<style>` block in `{% block extra_head %}`.

Keep: `.controls`, `.card-grid`, `.speed-loading`, `.htmx-indicator`, `@keyframes speed-loading-spin`.

- [ ] **Step 1: Remove the dead CSS block from `templates/speed.html`**

In `templates/speed.html`, remove these lines from the `<style>` block (approximately lines 13-20):

```css
.spacing-stat { text-align:center; margin-top:0.5rem; padding-top:0.5rem; border-top:1px solid var(--line-soft); }
.spacing-stat-label { font-family:'Fira Code',monospace; font-size:0.58rem; letter-spacing:0.08em; color:var(--ink-400); text-transform:uppercase; margin-bottom:3px; }
.spacing-stat-num { font-family:'Cormorant Garamond',Georgia,serif; font-weight:300; font-size:1.8rem; line-height:1; }
.spacing-stat-unit { font-size:1rem; color:var(--ink-400); }
.spacing-local   { color: var(--link-blue); }
.spacing-rapid   { color: var(--civic-green); }
.spacing-express { color: var(--express-fg); }
.spacing-none    { color: var(--ink-400); }
```

After removing, the `<style>` block should contain only `.controls`, `.card-grid`, `.speed-loading`, `.speed-loading::before`, `.htmx-indicator`, `.htmx-request.htmx-indicator`, `.htmx-request .htmx-indicator`, and `@keyframes speed-loading-spin`.

- [ ] **Step 2: Build and verify**

```bash
cargo build 2>&1 | grep "error"
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add templates/speed.html
git commit -m "refactor(speed): remove spacing-stat CSS moved to base.html"
```

---

### Task 7: Update `templates/route_speed_detail.html` — macros + RouteClass::display_label()

**Files:**
- Modify: `templates/route_speed_detail.html`
- Modify: `src/speed/card.rs` (add `RouteClass::display_label()`)

The classification badge currently renders `"Local · 12-18 km/h"` by concatenating `class.label()` and `class.speed_range()` inside the badge span. The macro takes a single `label` argument, so we add a `display_label()` method to `RouteClass` that returns the combined string.

The "Stop spacing" section label (line 64) has an inline badge — keep it as-is, just ensure `section-label-badge` CSS is now supplied by base.html (it was moved in Task 4, so removing the local definition is all that's needed). Only the simple "Speed trend" section label uses the macro.

The back link has a dynamic `agency_id` in the href — keep it as inline HTML.

The local `<style>` block in `{% block extra_head %}` defines `.section-label` with grey color. After removing it, the base.html cinnabar version applies (intentional per design spec).

- [ ] **Step 1: Write failing test for `RouteClass::display_label()`**

Add to `src/speed/card.rs` test module (after the `route_class_css_class` test, around line 391):

```rust
#[test]
fn route_class_display_label() {
    assert_eq!(RouteClass::Local.display_label(), "Local · 12-18 km/h");
    assert_eq!(RouteClass::Rapid.display_label(), "Rapid · 18-25 km/h");
    assert_eq!(RouteClass::Express.display_label(), "Express · >25 km/h");
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cargo test route_class_display_label 2>&1 | grep -E "error|FAILED"
```

Expected: compilation error — method not found.

- [ ] **Step 3: Add `RouteClass::display_label()` to `src/speed/card.rs`**

In `src/speed/card.rs`, add after `RouteClass::css_class()` (after line 95):

```rust
    pub fn display_label(&self) -> &'static str {
        match self {
            RouteClass::Local => "Local · 12-18 km/h",
            RouteClass::Rapid => "Rapid · 18-25 km/h",
            RouteClass::Express => "Express · >25 km/h",
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cargo test route_class_display_label
```

Expected: 1 test passes.

- [ ] **Step 5: Update `templates/route_speed_detail.html`**

Make these changes to `templates/route_speed_detail.html`:

**a) Add import after `{% extends "base.html" %}` (line 1):**
```
{% import "macros.html" as ui %}
```

**b) Remove the local `.section-label` and `.section-label-badge` CSS from the `<style>` block (lines 13-16):**

Remove these lines:
```css
.section-label { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-500); margin-bottom: 0.75rem; font-weight: 700; }
.section-label-badge { display: inline-block; background: var(--link-blue); color: white; font-size: 0.68rem; padding: 0.15rem 0.4rem; border-radius: 3px; margin-left: 0.5rem; vertical-align: middle; font-weight: 600; text-transform: none; letter-spacing: 0; }
.section-label-badge.outlier { background: var(--civic-amber); }
.section-label-badge.slow { background: var(--civic-red); }
```

**c) Replace the classification badge (line 52):**

Replace:
```html
      <span class="badge badge--{{ class.css_class() }}">{{ class.label() }} · {{ class.speed_range() }}</span>
```
With:
```html
      {{ ui::badge(variant="neutral", label=class.display_label()) }}
```

**d) Replace the "Speed trend" section label (line 98):**

Replace:
```html
      <div class="section-label">Speed trend — scheduled vs actual</div>
```
With:
```html
      {{ ui::section_label(text="Speed trend — scheduled vs actual") }}
```

Leave the "Stop spacing" section label (line 64) unchanged — it has an embedded inline badge that is not expressible through the simple `section_label` macro.

- [ ] **Step 6: Build and verify**

```bash
cargo build 2>&1 | grep "error"
```

Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add templates/route_speed_detail.html src/speed/card.rs
git commit -m "refactor(route_speed_detail): use badge and section_label macros, move section-label CSS to base"
```

---

### Task 8: Update `templates/dashboard.html` to use macros

**Files:**
- Modify: `templates/dashboard.html`

Replace both metric cards with the `metric_card` macro and the status badge with the `badge` macro. The filter_bar agency links have dynamic hrefs — leave them as-is.

- [ ] **Step 1: Update `templates/dashboard.html`**

**a) Add import after `{% extends "base.html" %}` (line 1):**
```
{% import "macros.html" as ui %}
```

**b) Replace the two metric cards (lines 17-25):**

Replace:
```html
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
```
With:
```html
  <div class="metric-grid">
    {{ ui::metric_card(label="Routes monitored", value=routes.len(), unit="") }}
    {{ ui::metric_card(label="Period", value=period_days, unit="d") }}
  </div>
```

**c) Replace the status badge (line 54):**

Replace:
```html
        <td><span class="badge {{ route.status_class() }}">{{ route.status_label() }}</span></td>
```
With:
```html
        <td>{{ ui::badge(variant=route.badge_variant(), label=route.status_label()) }}</td>
```

- [ ] **Step 2: Build and verify**

```bash
cargo build 2>&1 | grep "error"
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add templates/dashboard.html
git commit -m "refactor(dashboard): use badge and metric_card macros"
```

---

### Task 9: Update `templates/scorecard.html` to use macros

**Files:**
- Modify: `templates/scorecard.html`

Replace simple metric cards and status badges with macros. The "Worst gap" card (lines 24-36) has conditional content based on an `Option<&f64>` — it cannot be expressed through a single `metric_card(label, value, unit)` call; leave it as inline HTML.

- [ ] **Step 1: Update `templates/scorecard.html`**

**a) Add import after `{% extends "base.html" %}` (line 1):**
```
{% import "macros.html" as ui %}
```

**b) Replace the first two simple metric cards (lines 16-23):**

Replace:
```html
  <div class="metric-grid">
    <div class="metric-card">
      <div class="metric-label">Routes monitored</div>
      <div class="metric-value">{{ routes.len() }}</div>
    </div>
    <div class="metric-card">
      <div class="metric-label">Meeting {{ floor_city }} floor</div>
      <div class="metric-value">{{ routes_meeting_floor }}</div>
    </div>
    <div class="metric-card">
```
With:
```html
  <div class="metric-grid">
    {{ ui::metric_card(label="Routes monitored", value=routes.len(), unit="") }}
    {{ ui::metric_card(label="Meeting floor", value=routes_meeting_floor, unit="") }}
    <div class="metric-card">
```

Note: `Meeting {{ floor_city }} floor` uses a template variable in the label — it can't be passed to the macro which expects a literal. Simplify to `"Meeting floor"` (static string) as the label.

Wait — actually, macro arguments in Askama ARE template expressions, not only literals. `floor_city` is a `&str` variable in scope. Can we pass a format expression? In Askama, the argument `label=floor_city` would pass the value of `floor_city`. But `"Meeting {{ floor_city }} floor"` as a string is not valid Rust. 

Instead, keep the "Meeting floor" card inline but replace everything except that label. Revised approach:

Replace:
```html
  <div class="metric-grid">
    <div class="metric-card">
      <div class="metric-label">Routes monitored</div>
      <div class="metric-value">{{ routes.len() }}</div>
    </div>
    <div class="metric-card">
      <div class="metric-label">Meeting {{ floor_city }} floor</div>
      <div class="metric-value">{{ routes_meeting_floor }}</div>
    </div>
```
With:
```html
  <div class="metric-grid">
    {{ ui::metric_card(label="Routes monitored", value=routes.len(), unit="") }}
    <div class="metric-card">
      <div class="metric-label">Meeting {{ floor_city }} floor</div>
      <div class="metric-value">{{ routes_meeting_floor }}</div>
    </div>
```

Keep the "Worst gap" card (everything from `<div class="metric-card">` with the worst_gap conditional, through its closing `</div>`) as-is.

**c) Replace the status badge (line 77):**

Replace:
```html
        <td><span class="badge {{ route.status_class(floor_pct, ceiling_pct) }}">{{ route.status_label(floor_pct, ceiling_pct) }}</span></td>
```
With:
```html
        <td>{{ ui::badge(variant=route.badge_variant(floor_pct, ceiling_pct), label=route.status_label(floor_pct, ceiling_pct)) }}</td>
```

- [ ] **Step 2: Build and verify**

```bash
cargo build 2>&1 | grep "error"
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add templates/scorecard.html
git commit -m "refactor(scorecard): use badge and metric_card macros"
```

---

### Task 10: Update `templates/route_detail.html` to use macros

**Files:**
- Modify: `templates/route_detail.html`

The back link at line 29 uses a static href — use the `filter_pill` macro.

- [ ] **Step 1: Update `templates/route_detail.html`**

**a) Add import after `{% extends "base.html" %}` (line 1):**
```
{% import "macros.html" as ui %}
```

**b) Replace the back link (line 29):**

Replace:
```html
    <a class="filter-pill" href="/">← Back to dashboard</a>
```
With:
```html
    {{ ui::filter_pill(href="/", label="← Back to dashboard", active=false) }}
```

- [ ] **Step 2: Build and verify**

```bash
cargo build 2>&1 | grep "error"
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add templates/route_detail.html
git commit -m "refactor(route_detail): use filter_pill macro"
```

---

### Task 11: Remove dead methods and CSS

**Files:**
- Modify: `src/speed/card.rs` — remove `avg_stop_spacing_class()` and its 4 tests
- Modify: `src/metrics/mod.rs` — remove `RouteSummary::status_class()`, `ScorecardRoute::status_class()` and their tests
- Modify: `templates/base.html` — remove old badge CSS (`.badge.green`, `.badge--local`, etc.)

At this point all callers have been migrated. Removing the old methods ensures they don't silently drift from the badge variants.

- [ ] **Step 1: Remove `avg_stop_spacing_class()` from `src/speed/card.rs`**

Remove the method and its 4 tests:

Method to remove (lines 26-31):
```rust
    pub fn avg_stop_spacing_class(&self) -> &'static str {
        match self.avg_stop_spacing_m {
            None => "none",
            Some(m) => classify_by_spacing(m).css_class(),
        }
    }
```

Tests to remove (lines ~435-452):
```rust
#[test]
fn spacing_class_none_when_no_data() { ... }
#[test]
fn spacing_class_local_below_500m() { ... }
#[test]
fn spacing_class_rapid_500_to_1500m() { ... }
#[test]
fn spacing_class_express_1500m_and_above() { ... }
```

- [ ] **Step 2: Remove `RouteSummary::status_class()` from `src/metrics/mod.rs`**

Remove method (lines ~247-254):
```rust
    /// "green", "yellow", "red", or "none"
    pub fn status_class(&self) -> &'static str {
        match self.avg_on_time_pct {
            Some(pct) if pct >= 80.0 => "green",
            Some(pct) if pct >= 60.0 => "yellow",
            Some(_) => "red",
            None => "none",
        }
    }
```

`RouteSummary::status_class()` has no unit tests, so nothing to remove there.

- [ ] **Step 3: Remove `ScorecardRoute::status_class()` and its tests**

Remove method (lines ~546-554):
```rust
    /// CSS badge class: "green" / "yellow" / "red" / "none".
    pub fn status_class(&self, floor_pct: &f64, ceiling_pct: &f64) -> &'static str {
        match self.avg_on_time_pct {
            Some(p) if p >= *ceiling_pct => "green",
            Some(p) if p >= *floor_pct => "yellow",
            Some(_) => "red",
            None => "none",
        }
    }
```

Remove tests (lines ~957-978):
```rust
#[test]
fn status_class_is_green_at_or_above_ceiling() { ... }
#[test]
fn status_class_is_yellow_between_floor_and_ceiling() { ... }
#[test]
fn status_class_is_red_under_floor() { ... }
#[test]
fn status_class_is_none_without_data() { ... }
```

- [ ] **Step 4: Remove old badge CSS from `templates/base.html`**

Remove the old badge variant rules (lines ~227-249):

```css
        .badge.green, .badge.status-good {
            background-color: var(--civic-green-bg);
            color: var(--civic-green);
        }

        .badge.yellow, .badge.status-watch {
            background-color: var(--civic-amber-bg);
            color: var(--civic-amber);
        }

        .badge.red, .badge.status-bad {
            background-color: var(--civic-red-bg);
            color: var(--civic-red);
        }

        .badge.none, .badge.status-none {
            background-color: var(--surface-muted);
            color: var(--ink-500);
        }

        .badge--local  { background: var(--link-blue-bg); color: var(--link-blue); }
        .badge--rapid     { background: var(--civic-green-bg); color: var(--civic-green); }
        .badge--express   { background: var(--express-bg); color: var(--express-fg); }
```

Keep: the new `.badge--good/bad/mixed/neutral` rules added in Task 4, and the base `.badge { ... }` rule.

- [ ] **Step 5: Run all tests**

```bash
cargo test 2>&1 | tail -20
```

Expected: all previously passing tests pass. The pre-existing failures (route_speed_by_day_type_*, route_stop_spacings_*, route_speed_detail_* integration tests) should remain as before — these are not affected by this plan.

- [ ] **Step 6: Build**

```bash
cargo build 2>&1 | grep "error"
```

Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add src/speed/card.rs src/metrics/mod.rs templates/base.html
git commit -m "refactor: remove dead status_class/avg_stop_spacing_class methods and old badge CSS"
```

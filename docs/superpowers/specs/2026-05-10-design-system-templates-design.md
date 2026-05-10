# Design System Templates — Design Spec

## Goal

Extract every reusable UI component in the Lumina design system into Askama template macros, then replace all inline component HTML across existing templates with macro calls.

## Architecture

Single file `templates/macros.html` holds all macros. Each template that uses macros adds `{% import "macros.html" as ui %}` at the top. Askama imports do not propagate through includes, so every file needing macros imports directly.

No CSS token renaming — existing custom property names (`--b-cinn-bg`, etc.) are kept as-is. New semantic badge variant classes (`badge--good`, `badge--bad`, `badge--mixed`, `badge--neutral`) are added alongside existing route-class variants.

## Components

### `badge(variant, label)`

Renders `<span class="badge badge--{variant}">{label}</span>`.

Variants map to colours:
- `good` → sage green (`--b-sage-bg/fg` or CSS inline if no token exists; hex `#3D9A6B`)
- `bad` → cinnabar red (`--b-cinn-bg/fg`)
- `mixed` → saffron amber (`--b-saff-bg/fg`)
- `neutral` → oxford blue (`--b-ox-bg/fg`)

Route classification badges (Local / Rapid / Express) all use `neutral` (oxford blue).

Route status badges map:
- On-time / above target → `good`
- Mixed → `mixed`
- Below target → `bad`
- No data → `neutral`

### `stat(label, value, unit, variant)`

Renders a labelled metric block with a large number, optional unit, and semantic colour on the number.

```html
<div>
  <div class="spacing-stat-label">{label}</div>
  <div class="spacing-stat-num spacing-{variant}">{value}<span class="spacing-stat-unit">{unit}</span></div>
</div>
```

Variants: `good` (sage), `bad` (cinnabar), `mixed` (saffron), `neutral` (oxford blue). Pass `variant=""` for default ink colour.

### `section_label(text)`

Renders `<span class="section-label">{text}</span>` — Fira Code, uppercase, letter-spaced, cinnabar colour.

### `filter_pill(href, label, active)`

Renders `<a href="{href}" class="filter-pill {active_class}">{label}</a>`. When `active=true` the pill gets the active state class.

### `metric_card(label, value, unit, sublabel)`

Renders a card-style metric display used on the dashboard and scorecard:

```html
<div class="metric-card">
  <div class="metric-card__label">{label}</div>
  <div class="metric-card__value">{value}<span class="metric-card__unit">{unit}</span></div>
  <div class="metric-card__sublabel">{sublabel}</div>
</div>
```

### `alert(variant, message)`

Renders `<div class="alert alert--{variant}">{message}</div>`.

Variants: `info`, `ok`, `warn`, `err` — map to `--al-info-*`, `--al-ok-*`, `--al-warn-*`, `--al-err-*` CSS vars.

## CSS Changes (`templates/base.html`)

### New badge variant classes

```css
.badge--good    { background: var(--b-sage-bg, #d4f0e2); color: var(--b-sage-fg, #1f6e43); }
.badge--bad     { background: var(--b-cinn-bg); color: var(--b-cinn-fg); }
.badge--mixed   { background: var(--b-saff-bg); color: var(--b-saff-fg); }
.badge--neutral { background: var(--b-ox-bg);   color: var(--b-ox-fg);   }
```

Existing `.badge--local`, `.badge--rapid`, `.badge--express`, `.badge.status-*` classes remain until callers are migrated, then are removed.

### Move `spacing-stat-*` to base.html

These classes are currently defined inline in `templates/speed.html`. Move them to `base.html` so all templates that render stats can use them.

```css
.spacing-stat-label { font-family:'Fira Code',monospace; font-size:0.58rem; ... }
.spacing-stat-num   { font-family:'Cormorant Garamond',serif; font-weight:300; font-size:1.8rem; ... }
.spacing-stat-unit  { font-size:1rem; color:var(--ink-400); }
.spacing-good       { color: var(--sage, #3D9A6B); }
.spacing-bad        { color: var(--cinn, var(--link-blue)); }   /* cinnabar */
.spacing-mixed      { color: var(--saff); }
.spacing-neutral    { color: var(--ox, var(--link-blue)); }     /* oxford blue */
```

Remove from `templates/speed.html` once moved.

### Move `section-label` to base.html

Currently defined locally in `templates/route_speed_detail.html`. Move to `base.html` and delete local definition.

```css
.section-label { font-family:'Fira Code',monospace; font-size:0.65rem; letter-spacing:0.1em;
                 text-transform:uppercase; color:var(--cinn, #C8463A); }
.section-label-badge { ... }
```

### Alert CSS vars (add to base.html)

```css
--al-info-bg: ...; --al-info-fg: ...; --al-info-border: ...;
--al-ok-bg:   ...; --al-ok-fg:   ...; --al-ok-border:   ...;
--al-warn-bg: ...; --al-warn-fg: ...; --al-warn-border: ...;
--al-err-bg:  ...; --al-err-fg:  ...; --al-err-border:  ...;

.alert { ... }
.alert--info { background: var(--al-info-bg); color: var(--al-info-fg); border-left: 3px solid var(--al-info-border); }
/* etc. */
```

## Rust Changes (`src/speed/card.rs`)

### Rename `status_class()` → `badge_variant()`

`status_class()` currently returns CSS class suffixes (`"good"`, `"bad"`, `"mixed"`, `"neutral"`). Rename to `badge_variant()` — same values, new name that reflects its role as a macro argument.

Update all callers: `templates/dashboard.html`, `templates/scorecard.html`.

### Add `avg_stop_spacing_variant()`

Returns `"neutral"` / `"bad"` / `"good"` based on `avg_stop_spacing_m`:

```rust
pub fn avg_stop_spacing_variant(&self) -> &'static str {
    match self.avg_stop_spacing_m {
        None => "neutral",
        Some(m) if m < 300.0 => "bad",
        Some(_) => "good",
    }
}
```

Rationale: Local (300–500m), Rapid (500–1500m), and Express (≥1500m) form a continuous range covering all values ≥300m. Values below 300m fall outside all classes and are flagged bad. No "mixed" case exists.

## Template Call Sites

### `templates/speed_card.html`

```
{% import "macros.html" as ui %}
```

- Badge: `{{ ui::badge(variant=card.badge_variant(), label=card.classification_label()) }}`
- Scheduled stat: `{{ ui::stat(label="Scheduled", value=card.avg_scheduled_speed_kmh_display(), unit=" km/h", variant="") }}`
- Actual stat: `{{ ui::stat(label="Actual", value=card.avg_actual_speed_kmh_display(), unit=" km/h", variant="") }}`
- Stop spacing stat: `{{ ui::stat(label="Avg stop spacing", value=card.avg_stop_spacing_number(), unit=card.avg_stop_spacing_unit(), variant=card.avg_stop_spacing_variant()) }}`

### `templates/dashboard.html`

```
{% import "macros.html" as ui %}
```

- Route badge: `{{ ui::badge(variant=route.badge_variant(), label=route.status_label()) }}`
- Metric cards: `{{ ui::metric_card(label=..., value=..., unit=..., sublabel=...) }}`

### `templates/scorecard.html`

```
{% import "macros.html" as ui %}
```

- Route badge: `{{ ui::badge(variant=route.badge_variant(floor_pct, ceiling_pct), label=...) }}`
- Metric cards: `{{ ui::metric_card(...) }}`

### `templates/route_detail.html`

```
{% import "macros.html" as ui %}
```

- Back link: `{{ ui::filter_pill(href="/", label="← Back to dashboard", active=false) }}`

### `templates/route_speed_detail.html`

```
{% import "macros.html" as ui %}
```

- Section headers: `{{ ui::section_label(text="...") }}`
- Badges: `{{ ui::badge(variant=..., label=...) }}`
- Remove local `section-label` CSS block

### `templates/speed.html`

- Remove `spacing-stat-*` CSS block (moved to `base.html`)

## Testing

- Unit tests for `avg_stop_spacing_variant()` on `RouteSpeedCard`: `None→neutral`, `0.0→bad`, `299.9→bad`, `300.0→good`, `1500.0→good`
- Unit test for renamed `badge_variant()` (previously `status_class()`) — same input/output, renamed
- Existing template compilation tests continue to pass (Askama validates at build time)
- No new integration tests needed — this is presentation logic only

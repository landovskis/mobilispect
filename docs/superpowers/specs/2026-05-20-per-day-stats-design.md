# Per-Day-Type Stats Design

**Date:** 2026-05-20
**Status:** Approved

## Problem

`RouteHeadwayRow` currently computes `top_decile_headway_mins`, `max_headway_mins`, `service_start_secs`, and `service_end_secs` by pooling gaps from all day types (weekday + Saturday + Sunday) into one combined set. This makes the values misleading — a route's "max headway" could come from Sunday when the analyst is looking at weekday service.

## Goal

Compute top-decile headway, max headway, and service span separately for each day type. Drop `trip_count` from the card entirely.

## Decisions

| Question | Decision |
|---|---|
| Card layout | Per-day columns (B: three equal columns, one per active day type) |
| Missing day types | Collapse — only render columns for day types with actual service |
| `trip_count` | Drop from struct and card |

## Data Model

### Removed fields from `RouteHeadwayRow`

- `top_decile_headway_mins: Option<f64>`
- `max_headway_mins: Option<f64>`
- `service_start_secs: Option<i64>`
- `service_end_secs: Option<i64>`
- `trip_count: i64`

### Added fields (×3 day types: weekday / saturday / sunday)

```rust
pub weekday_top_decile_mins:    Option<f64>,
pub weekday_max_headway_mins:   Option<f64>,
pub weekday_service_start_secs: Option<i64>,
pub weekday_service_end_secs:   Option<i64>,

pub saturday_top_decile_mins:    Option<f64>,
pub saturday_max_headway_mins:   Option<f64>,
pub saturday_service_start_secs: Option<i64>,
pub saturday_service_end_secs:   Option<i64>,

pub sunday_top_decile_mins:    Option<f64>,
pub sunday_max_headway_mins:   Option<f64>,
pub sunday_service_start_secs: Option<i64>,
pub sunday_service_end_secs:   Option<i64>,
```

A per-day column is shown in the template if and only if the day's avg headway field is `Some`. The per-day top-decile/max/span fields will be `None` exactly when the avg headway is `None`, so no separate guard is needed.

### Kept unchanged

`agency_id`, `route_id`, `short_name`, `long_name`, `weekday_headway_mins`, `saturday_headway_mins`, `sunday_headway_mins`, `primary_headway_min()`.

## SQL

### Removed CTEs

- `all_gaps` (union of wd/sat/sun gaps)
- `gap_summary` (combined PERCENTILE_CONT + MAX across all day types)
- `service_summary` (combined MIN/MAX start/end + COUNT)

### Added CTEs

```sql
wd_gap_summary AS (
    SELECT agency_id, route_id,
        PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY gap_secs::double precision) / 60.0
            AS weekday_top_decile_mins,
        MAX(gap_secs::double precision) / 60.0 AS weekday_max_headway_mins
    FROM wd_gaps WHERE gap_secs > 0
    GROUP BY agency_id, route_id
),
sat_gap_summary AS ( -- same shape, sat_ prefix ),
sun_gap_summary AS ( -- same shape, sun_ prefix ),

wd_service AS (
    SELECT agency_id, route_id,
        MIN(start_secs) AS weekday_service_start_secs,
        MAX(end_secs)   AS weekday_service_end_secs
    FROM trip_times WHERE is_weekday
    GROUP BY agency_id, route_id
),
sat_service AS ( -- same shape, saturday_ prefix ),
sun_service AS ( -- same shape, sunday_ prefix ),
```

The final `SELECT` grows from 12 columns to 19 (adds 12 per-day columns, drops 5 old columns). The `LEFT JOIN` chain grows by 6 joins (one per new CTE). The `ORDER BY` clause is unchanged.

## Display Methods

### Removed

- `top_decile_headway_display()`, `top_decile_headway_badge_variant()`
- `max_headway_display()`, `max_headway_badge_variant()`
- `service_span_display()`
- `trip_count_display()`

### Added

```rust
// Static helper (replaces inline logic in the old service_span_display)
fn service_span(start: Option<i64>, end: Option<i64>) -> String

// Per-day display methods (×3 day types)
pub fn weekday_top_decile_display(&self) -> String   // → headway_display(weekday_top_decile_mins)
pub fn weekday_max_headway_display(&self) -> String  // → headway_display(weekday_max_headway_mins)
pub fn weekday_service_span_display(&self) -> String // → service_span(weekday_start, weekday_end)
// …saturday_ and sunday_ variants follow same pattern
```

All new methods delegate to the existing static helpers `headway_display` and the new `service_span`. No new formatting logic.

## Template (`frequency_content.html`)

The `schedule-card__stats` div (4-stat grid) and `schedule-card__badges` div (3 badges) are replaced by a single day-column group per card:

```html
<div class="schedule-card__days">
  {% if row.weekday_headway_mins.is_some() %}
  <div class="day-col">
    <div class="day-col__hdr">Weekday</div>
    <div class="day-col__avg spacing-{{ row.weekday_badge_variant() }}">
      {{ row.weekday_display() }}
    </div>
    <div class="day-col__stat">
      <span class="day-col__lbl">Top 10%</span>
      <span>{{ row.weekday_top_decile_display() }}</span>
    </div>
    <div class="day-col__stat">
      <span class="day-col__lbl">Max</span>
      <span>{{ row.weekday_max_headway_display() }}</span>
    </div>
    <div class="day-col__stat">
      <span class="day-col__lbl">Span</span>
      <span>{{ row.weekday_service_span_display() }}</span>
    </div>
  </div>
  {% endif %}
  {# same block for saturday and sunday #}
</div>
```

CSS for `.schedule-card__days`: `display: grid; grid-template-columns: repeat(auto-fit, minmax(90px, 1fr)); gap: 6px;` — collapses naturally to 1, 2, or 3 columns depending on active day types.

The `schedule-card__stats` and `schedule-card__badges` CSS class definitions (and their responsive overrides) are removed from the `<style>` block in `crates/server/templates/frequency.html`. New `.schedule-card__days`, `.day-col`, `.day-col__hdr`, `.day-col__avg`, `.day-col__stat`, `.day-col__lbl` rules are added there. The `schedule-card__badge-label` class is also removed.

## Tests

### Unit tests (`frequency/mod.rs`)

- `make_row` helper updated: removes 5 old fields, adds 12 new fields with representative values.
- Existing tests for `headway_display`, `headway_badge_variant`, `primary_headway_min` adapted to new struct shape.
- New tests:
  - `service_span_none_none_returns_dash`
  - `weekday_service_span_display_formats_correctly`
  - `saturday_service_span_display_formats_correctly`
  - `sunday_service_span_display_formats_correctly`
  - `weekday_top_decile_and_max_display`

### E2E tests (`handlers.rs`)

- `schedule_page_renders_route_schedule_cards`: assertions on `"06:00-07:00"`, `"11.0 min"`, `"20.0 min"` stay valid — values now come from weekday-scoped fields. No structural changes needed.
- New test `schedule_page_renders_saturday_column_when_saturday_service_exists`: seeds weekday + Saturday calendar rows, asserts both columns render.
- Remove assertion `html.contains("Top 10%")` from `schedule_page_uses_top_decile_headway_instead_of_minimum` and `schedule_page_computes_headways_within_each_service_id` if the label text changes in the template; update to match new label text.

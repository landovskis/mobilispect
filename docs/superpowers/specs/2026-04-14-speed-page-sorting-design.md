# Speed Page Sorting & Controls Design

Date: 2026-04-14

## Overview

Add sorting by slowest scheduled and slowest actual speed to the speed page, and reorganise the controls bar to cleanly present both filtering and sorting.

## UI — Controls Bar

The current single-row agency filter pill bar becomes two labelled rows:

```
Agency:  [All]  [STM]  [RTL]  [STL]  [exo-trains]
Sort by: [Name] [Slowest scheduled] [Slowest actual]
```

- Each row has a muted label (`Agency:` / `Sort by:`) followed by pill links.
- Pill style matches the existing filter bar (white background, dark border; active = dark fill).
- Sort links preserve the current `?agency=` param; agency links preserve the current `?sort=` param.
- Empty params are omitted from URLs (e.g. default agency = no `agency=` param).

## Data Model

Two new fields on `RouteSpeedCard` in `src/speed/card.rs`:

```rust
pub avg_scheduled_speed_mps: Option<f64>,
pub avg_actual_speed_mps: Option<f64>,
```

`avg_scheduled_speed_mps` is the mean of all non-`None` values across `weekday_speed_mps`, `saturday_speed_mps`, `sunday_speed_mps` for every direction in the route. It is `None` only if every value is `None` (should not occur in practice).

`avg_actual_speed_mps` is the same aggregation over the three `actual_*_speed_mps` fields. It is `None` when no actual speed data exists for any direction or day type.

Both fields are computed inside `build_speed_cards` — no SQL changes required.

## Sort Logic

Sort is performed on the `Vec<RouteSpeedCard>` in the handler, after `build_speed_cards` returns.

| `sort` param | Key | Tie-break | None handling |
|---|---|---|---|
| `name` (default) | SQL `short_name` order (already correct) | — | — |
| `scheduled` | `avg_scheduled_speed_mps` ascending | `short_name` | N/A (always present) |
| `actual` | `avg_actual_speed_mps` ascending | `short_name` | `None` → last |

## Handler Changes (`src/web/handlers.rs`)

Replace `AgencyFilterParams` usage in `speed_page` with a new `SpeedParams` struct:

```rust
#[derive(Deserialize)]
pub struct SpeedParams {
    agency: Option<String>,
    sort: Option<String>,   // "name" | "scheduled" | "actual"; default "name"
}
```

`SpeedTemplate` gains an `active_sort: String` field (empty string = default / name).

## Template Changes (`templates/speed.html`)

- Replace the single `.filter-bar` div with a `.controls` wrapper containing two `.control-row` divs.
- Each row: `<span class="control-label">Agency:</span>` / `<span class="control-label">Sort by:</span>` followed by pill `<a>` tags.
- Sort pills link to `?sort=scheduled&agency={{active_agency}}` etc., omitting empty params.
- Add CSS for `.controls`, `.control-row`, `.control-label`.

## Constraints

- No JS added for sorting — full server-side approach.
- No other handlers, pages, or DB queries are modified.
- Tests: unit tests for the sort key computation in `build_speed_cards`, and for the sort ordering function in the handler.

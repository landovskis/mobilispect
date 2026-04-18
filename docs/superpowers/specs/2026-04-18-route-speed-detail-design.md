# Route Speed Detail Page

**Date:** 2026-04-18  
**Status:** Approved

## Overview

Clicking a route speed card navigates to a new detail page at `/routes/:agency_id/:route_id/speed`. The page shows two sections — one per direction — each containing a stop spacing strip and a 28-day speed trend chart broken out by day type (weekday / Saturday / Sunday).

---

## URL & Navigation

- **New route:** `GET /routes/:agency_id/:route_id/speed`
- **Entry point:** Each speed card in `speed_card.html` is wrapped in `<a href="/routes/{{ card.agency_id }}/{{ card.route_id }}/speed">` (text-decoration: none; color: inherit). `RouteSpeedCard` gains two new fields: `agency_id: String` and `route_id: String`, populated from the `RouteSpeedDayType` rows that build the card.
- **Back link:** Detail page header includes a link back to `/speed?agency=<agency_id>`.
- **Not a replacement:** The existing `/routes/:agency_id/:route_id` detail page (on-time % + 7-day trend) is unchanged.

---

## Data Layer

Both query functions live in `src/speed/mod.rs`.

### `route_stop_spacings(pool, agency_id, route_id) → Vec<DirectionStopSpacings>`

Picks one representative trip per direction (the first trip by id for that route). Joins `scheduled_stops → trips → stops`, uses a `LAG` window over `stop_sequence` to pair consecutive stops, then computes haversine distance between each pair. Stops are returned in sequence order.

Outlier threshold: any segment whose `distance_m > 1.5 × avg_spacing_m` for that direction is flagged `is_outlier: true`.

```rust
pub struct StopSpacing {
    pub to_stop_name: String,
    pub distance_m: f64,
    pub is_outlier: bool,
}

pub struct DirectionStopSpacings {
    pub direction_name: String,  // terminal stop name
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
}
```

Returns an empty `Vec` if the route has no trips (handler returns 404).

### `route_speed_trend_by_direction(pool, agency_id, route_id, days: i64) → Vec<DirectionSpeedTrend>`

Queries `route_speed_daily` for the last `days` days. Bucketing: Sunday = 0, Saturday = 6, all other days = weekday. Returns one struct per direction with three time series (each a `Vec<(NaiveDate, f64)>` of speed in m/s).

Called with `days = 28` to match existing data windows.

```rust
pub struct DirectionSpeedTrend {
    pub direction_name: String,  // terminal stop name
    pub weekday: Vec<(NaiveDate, f64)>,
    pub saturday: Vec<(NaiveDate, f64)>,
    pub sunday: Vec<(NaiveDate, f64)>,
}
```

Returns an empty `Vec` if no speed data exists (handler renders page with empty charts rather than 404, since a route may have static data but no RT observations yet).

`DirectionSpeedTrend` carries `direction_id: i16` (not a name) so the handler can match it to the correct `DirectionStopSpacings` entry. The `direction_name` (terminal stop) comes from `DirectionStopSpacings` and is authoritative.

---

## Handler

New `route_speed_detail()` in `src/web/handlers.rs`:

- Accepts path params `(Path((agency_id, route_id)): Path<(String, String)>)`
- Calls `route_stop_spacings()` and `route_speed_trend_by_direction()` concurrently via `tokio::join!`
- Returns **404** if `route_stop_spacings()` returns an empty vec (unknown route / no trips)
- Merges results by `direction_id` into `Vec<RouteSpeedDetailDirection>` (one entry per direction, containing both the stop spacings and the trend time series). Directions with no speed data get empty trend vecs; directions present in trend data but absent from spacings are discarded.
- Serializes each direction's trend data to a JSON string for Chart.js (same pattern as `trend_json` in the existing route detail handler)
- Renders `RouteSpeedDetailTemplate`

```rust
pub struct RouteSpeedDetailDirection {
    pub direction_name: String,
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
    pub weekday_json: String,
    pub saturday_json: String,
    pub sunday_json: String,
}
```

---

## Router

`src/web/mod.rs`: add

```rust
.route("/routes/:agency_id/:route_id/speed", get(route_speed_detail))
```

This must be registered before (or alongside) the existing `/routes/:agency_id/:route_id` route to avoid shadowing.

---

## Template

**`templates/route_speed_detail.html`**

Structure:
```
page header (route short_name · long_name · agency, back link)
for each direction:
  <section>
    direction heading (terminal stop name)
    stop spacing strip
      - dots (blue) connected by lines proportional to distance
      - terminal stop dot in green
      - orange line (thicker) for is_outlier segments
      - distance label below each segment
    caption: "Average spacing: X m · Orange segments are 1.5× above average"
    divider
    speed trend chart (Chart.js line, 3 datasets: weekday/Saturday/Sunday)
    legend
  </section>
```

Colors follow existing project palette:
- Scheduled/weekday: `#2980b9`
- Actual/Saturday: `#27ae60`  
- Sunday / outlier: `#e67e22`

Chart.js loaded from the same CDN as `speed.html`. Trend data injected as a JSON variable per direction (same pattern as existing `trend_json`). Lines use `spanGaps: true` to handle days with no data.

---

## Tests

All tests written before implementation (Red-Green-Refactor).

### Unit tests (`src/speed/mod.rs`)

| Test | Asserts |
|------|---------|
| `test_stop_spacings_orders_stops_by_sequence` | Stops are returned in stop_sequence order, not insertion order |
| `test_stop_spacings_flags_outliers` | A segment > 1.5× avg is `is_outlier: true`; segments at or below avg are `false` |
| `test_speed_trend_buckets_day_types` | Sunday → sunday vec, Saturday → saturday vec, Mon–Fri → weekday vec |

### Integration tests (testcontainers + real Postgres)

| Test | Asserts |
|------|---------|
| `test_route_stop_spacings_distances` | Seed stops at known lat/lon, assert computed distances match expected haversine values within tolerance |
| `test_route_speed_trend_by_direction_groups` | Seed `route_speed_daily` rows across two directions and multiple day types, assert grouping and date ordering |

### E2E tests (Axum test client)

| Test | Asserts |
|------|---------|
| `test_route_speed_detail_200` | Seed minimal trips/stops/speed data, GET `/routes/:agency_id/:route_id/speed` returns 200 with direction name in HTML |
| `test_route_speed_detail_404` | GET for nonexistent route_id returns 404 |

---

## Out of Scope

- Sorting or filtering stops on the detail page
- Scheduled vs actual speed comparison on this page (the card already shows that)
- Map visualization of stop locations
- Precomputing stop spacings in the worker (can be added later if query becomes slow)

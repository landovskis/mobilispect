# Route Classification by Stop Spacing — Design

## Goal

Classify each route as Slow Bus / Local Bus / Rapid / Express based on average stop spacing, and display the classification badge on the route speed card and route speed detail page.

## Background

Stop spacing is a reliable proxy for the type of service a route provides. Closely spaced stops indicate local, slow service; widely spaced stops indicate faster, more express-oriented service. Thresholds are derived from the transit network type framework described in "Speed Matters" (Marco Chitti).

## Classification Thresholds

| Class     | Avg stop spacing |
|-----------|-----------------|
| Slow Bus  | < 300 m         |
| Local Bus | 300 – 500 m     |
| Rapid     | 500 – 1500 m    |
| Express   | ≥ 1500 m        |

Classification is per route (not per direction). The per-route avg spacing is the mean of all its directions' avg stop spacings.

A route with no stop spacing data renders no badge (classification is `None`).

## Data Model

### `RouteClass` enum (`src/speed/card.rs`)

```rust
pub enum RouteClass {
    SlowBus,
    LocalBus,
    Rapid,
    Express,
}
```

Methods:
- `label(&self) -> &'static str` — display string ("Slow Bus", "Local Bus", "Rapid", "Express")
- `css_class(&self) -> &'static str` — CSS modifier class for badge color ("slow-bus", "local-bus", "rapid", "express")

### `classify_by_spacing(avg_m: f64) -> RouteClass`

Pure function, co-located in `src/speed/card.rs`.

### `RouteSpeedCard` (`src/speed/card.rs`)

New field:
```rust
pub classification: Option<RouteClass>,
```

## Data Flow

### Speed page (card list)

1. Handler calls existing speed query to build `RouteSpeedCard` list.
2. Handler runs a new **batch stop-spacing query** — a single SQL query returning `(route_id, avg_spacing_m)` for all routes of the agency in one round-trip.
3. For each card, look up its route's avg spacing from the batch result and call `classify_by_spacing` to populate `classification`.

**New SQL query** — mirrors the approach of `route_stop_spacings` in `src/speed/mod.rs` but covers all routes for the agency in one round-trip, using the same `trips` / `scheduled_stops` / `stops` tables and haversine formula (no PostGIS):

```sql
WITH rep_trip AS (
    SELECT DISTINCT ON (route_id, COALESCE(direction_id, 0))
        route_id,
        trip_id,
        COALESCE(direction_id, 0) AS direction_id
    FROM trips
    WHERE agency_id = $1
    ORDER BY route_id, COALESCE(direction_id, 0), trip_id
),
ordered AS (
    SELECT
        rt.route_id,
        rt.direction_id,
        s.stop_lat, s.stop_lon,
        ROW_NUMBER() OVER (PARTITION BY rt.route_id, rt.direction_id ORDER BY ss.stop_sequence) AS rn
    FROM rep_trip rt
    JOIN scheduled_stops ss ON ss.agency_id = $1 AND ss.trip_id = rt.trip_id
    JOIN stops s ON s.agency_id = $1 AND s.stop_id = ss.stop_id
),
with_prev AS (
    SELECT
        route_id, direction_id, stop_lat, stop_lon,
        LAG(stop_lat) OVER (PARTITION BY route_id, direction_id ORDER BY rn) AS prev_lat,
        LAG(stop_lon) OVER (PARTITION BY route_id, direction_id ORDER BY rn) AS prev_lon
    FROM ordered
),
direction_avg AS (
    SELECT
        route_id,
        direction_id,
        AVG(
            2 * 6371000 * asin(sqrt(
                power(sin((stop_lat - prev_lat) * pi() / 180.0 / 2.0), 2) +
                cos(prev_lat * pi() / 180.0) * cos(stop_lat * pi() / 180.0) *
                power(sin((stop_lon - prev_lon) * pi() / 180.0 / 2.0), 2)
            ))
        ) AS dir_avg_spacing_m
    FROM with_prev
    WHERE prev_lat IS NOT NULL
    GROUP BY route_id, direction_id
)
SELECT route_id, AVG(dir_avg_spacing_m)::FLOAT8 AS avg_spacing_m
FROM direction_avg
GROUP BY route_id
```

### Detail page

The detail handler already calls `route_stop_spacings()` per direction (concurrently). It collects the per-direction `avg_spacing_m` values and computes their mean to get the per-route classification. No new SQL needed.

New field on `RouteSpeedDetailTemplate`:
```rust
pub classification: Option<RouteClass>,
```

## Templates

### `templates/speed_card.html`

In the card header row, add badge top-right:
```html
{% if let Some(class) = card.classification %}
<span class="badge badge--{{ class.css_class() }}">{{ class.label() }}</span>
{% endif %}
```

Header row uses `display: flex; justify-content: space-between` (already present via `.route-header`).

### `templates/route_speed_detail.html`

In the page header, same pattern:
```html
{% if let Some(class) = page.classification %}
<span class="badge badge--{{ class.css_class() }}">{{ class.label() }}</span>
{% endif %}
```

## Badge CSS

Four color variants added inline in the templates (both `speed_card.html` and `route_speed_detail.html` share the same `<style>` block pattern already used in the project):

| Class modifier     | Background | Text    | Border  |
|--------------------|------------|---------|---------|
| `badge--slow-bus`  | `#fef9e7`  | `#b7950b` | `#f9e79f` |
| `badge--local-bus` | `#e8f4fd`  | `#2471a3` | `#aed6f1` |
| `badge--rapid`     | `#eafaf1`  | `#1e8449` | `#a9dfbf` |
| `badge--express`   | `#f5eef8`  | `#7d3c98` | `#d7bde2` |

## Testing

- **Unit**: `classify_by_spacing` — all four classes, exact boundary values (300, 500, 1500)
- **Unit**: `RouteClass::label` and `RouteClass::css_class` return correct strings
- **Integration**: batch stop-spacing query returns correct avg per route; routes with no stop data return no row (handled as `None` classification)
- **E2E**: speed page HTML contains badge for at least one route; detail page HTML contains badge in header

## Files Touched

| File | Change |
|------|--------|
| `src/speed/card.rs` | Add `RouteClass` enum, `classify_by_spacing`, update `RouteSpeedCard` |
| `src/speed/mod.rs` | Add batch stop-spacing query function |
| `src/web/handlers.rs` | Call batch query on speed page; compute route classification on detail page |
| `templates/speed_card.html` | Add badge in header row |
| `templates/route_speed_detail.html` | Add badge in page header |
| `templates/base.html` (or equivalent CSS file) | Add badge color CSS classes |

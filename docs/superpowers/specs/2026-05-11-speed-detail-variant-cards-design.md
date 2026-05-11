# Speed Detail: Direction Card Per Route Variant

## Goal

Replace the current one-card-per-direction view on `route_speed_detail.html` with one card per route variant. Each variant represents a distinct stop pattern. The primary variant floats to the top; minor variants follow, ordered by trip count descending. Speed trend data is tracked per variant rather than per direction.

## Background

The `route_variants` table already stores one row per distinct stop pattern per direction, with `is_primary`, `trip_count`, and `headsign`. The `route_speed_daily` table currently tracks actual speed at `(agency_id, route_id, service_date, direction_id)` granularity — one row per direction per day. Trips have a `variant_id` column (added in migration 005).

The speed overview page (scorecard, speed cards) aggregates with `AVG()` grouped by direction — adding per-variant rows to `route_speed_daily` is safe because these queries will simply average across all variant rows.

## Design

### 1. DB Schema — Migration 008

Add `variant_id TEXT NOT NULL DEFAULT ''` to `route_speed_daily` and extend the primary key to include it.

```sql
ALTER TABLE route_speed_daily ADD COLUMN variant_id TEXT NOT NULL DEFAULT '';
ALTER TABLE route_speed_daily DROP CONSTRAINT route_speed_daily_pkey;
ALTER TABLE route_speed_daily ADD PRIMARY KEY (agency_id, route_id, service_date, direction_id, variant_id);
```

Existing rows keep `variant_id = ''` (legacy direction-level aggregates). New rows written by the worker carry real variant IDs. The existing overview/scorecard queries use `AVG()` grouped by `(route_id, direction_id)` — they average across all variant rows, including both `''` and real-variant rows. No changes required to those queries.

### 2. Worker — `compute_route_speed_daily`

Change the combo-discovery query from grouping by `(route_id, direction_id)` to `(route_id, direction_id, variant_id)` by joining `trips.variant_id`. Trips without a `variant_id` fall back to `''` via `COALESCE`.

```sql
SELECT DISTINCT
    t.route_id,
    COALESCE(t.direction_id, 0) AS direction_id,
    COALESCE(t.variant_id, '')  AS variant_id
FROM stop_time_events ste
JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
WHERE ste.agency_id = $1
  AND ste.observed_at::TIMESTAMPTZ::DATE = $2::DATE
  AND ste.arrival_time_unix IS NOT NULL
```

The per-trip speed calculation loop is unchanged. Only the grouping key and `INSERT` gain `variant_id`.

### 3. New Query — `route_speed_trend_by_variant`

Replaces `route_speed_trend_by_direction` for the detail page. Takes `(db, agency_id, route_id, days)` and returns one `VariantSpeedTrend` per variant, each containing weekday/saturday/sunday trend point vectors.

```sql
SELECT d.variant_id,
       d.service_date,
       d.actual_speed_mps,
       r.scheduled_speed_mps
FROM route_speed_daily d
LEFT JOIN route_speed r
  ON r.agency_id = d.agency_id
 AND r.route_id = d.route_id
 AND r.direction_id = d.direction_id
WHERE d.agency_id = $1
  AND d.route_id = $2
  AND d.variant_id != ''
  AND d.service_date >= (CURRENT_DATE - $3::INT * INTERVAL '1 day')::TEXT
ORDER BY d.variant_id, d.service_date
```

`variant_id != ''` excludes legacy direction-level rows.

### 4. Stop Spacings — All Variants

Modify `route_stop_spacings` to remove the `WHERE is_primary = TRUE` filter. The `primary_variant` CTE becomes `all_variants` — it selects all variants for the route, not just the primary. The window functions (`ROW_NUMBER`, `COUNT`) partition by `variant_id` instead of `direction_id`. `route_variants.is_primary` and `route_variants.trip_count` are joined in and passed through to the result rows.

`StopSpacingEntry` gains `variant_id: String`, `is_primary: bool`, `trip_count: i64`. `DirectionStopSpacings` gains the same three fields.

The direction label (`direction_name`) becomes `"{first_stop_name} → {last_stop_name}"` — both values are already derived from `is_first`/`is_last` flags in the existing Rust builder.

The outer query orders by `trip_count DESC, variant_id, rn` so variants are returned highest-trip-count first. `build_direction_spacings` processes them in that order, producing a `Vec<DirectionStopSpacings>` naturally sorted by trip count.

### 5. Handler — `route_speed_detail`

`RouteSpeedDetailDirection` gains:
- `variant_id: String`
- `is_primary: bool`
- `trip_count: i64`

Spacings are fetched via the updated `route_stop_spacings` (all variants). Trends are fetched via `route_speed_trend_by_variant`. Matching is by `variant_id`.

The direction label is already computed in `DirectionStopSpacings.direction_name` as "First → Last".

### 6. Template — `route_speed_detail.html`

Card header changes from the plain direction name to direction name + badge:

```html
<div class="direction-header">
  {{ direction.direction_name }}
  {% if direction.is_primary %}
  <span class="badge">Primary · {{ direction.trip_count }} trips</span>
  {% else %}
  <span class="badge badge--neutral">{{ direction.trip_count }} trips</span>
  {% endif %}
</div>
```

Stop spacing strip and speed trend charts are unchanged per card.

## Data Model

```
route_speed_daily
  (agency_id, route_id, service_date, direction_id, variant_id) PK
  variant_id = ''     → legacy direction-level row (pre-migration data)
  variant_id = <id>   → per-variant row (post-migration worker output)

route_variants
  (agency_id, route_id, direction_id, variant_id) PK
  is_primary, trip_count, headsign, stop_count

DirectionStopSpacings (Rust)
  variant_id, direction_id, is_primary, trip_count
  direction_name = "{first_stop_name} → {last_stop_name}"
  first_stop_name, spacings: Vec<StopSpacing>, avg_spacing_m
```

## What Doesn't Change

- Overview speed cards (`speed.html`, `speed_card.html`) — unaffected
- Scorecard and metrics pages — unaffected  
- `route_speed_by_day_type` query — averages across all rows for a direction; more rows = same average
- `route_speed_summary` query — same
- Stop strip rendering logic inside each card
- Speed chart rendering JS

## Testing

- Unit: `build_direction_spacings` updated to handle `variant_id` and `is_primary` fields
- Unit: `direction_name` formatted as "First → Last"
- Integration: `route_stop_spacings` returns all variants ordered by trip_count DESC
- Integration: `compute_route_speed_daily` writes per-variant rows
- Integration: `route_speed_trend_by_variant` returns one trend per variant, excludes `variant_id = ''` rows
- E2E: detail page renders one card per variant

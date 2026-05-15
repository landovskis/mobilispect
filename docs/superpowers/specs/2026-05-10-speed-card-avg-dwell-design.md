# Speed Card: Avg Dwell — Design Spec

**Date:** 2026-05-10

## Goal

Add an **avg dwell** stat to the Speed Card, showing the average actual time (in seconds) a vehicle spends at each stop for that route over the last 28 days.

## Background

`stop_time_events` already has a generated column `dwell_secs` (migration 003) computed as `departure_time_unix - arrival_time_unix`. The Speed Card currently shows Scheduled speed, Actual speed, and Avg stop spacing. Avg dwell is a fourth neutral stat that gives operators a sense of how much time is spent loading/unloading at stops.

## Architecture

Option A: pre-compute `avg_dwell_secs` alongside actual speed in `compute_route_speed_daily`, store it in `route_speed_daily`, and aggregate it in the `route_speed_by_day_type` display query. No runtime query cost; follows existing architecture exactly.

## Data Layer

### Migration

**File:** `migrations/008_avg_dwell_speed_daily.sql`

```sql
ALTER TABLE route_speed_daily ADD COLUMN avg_dwell_secs DOUBLE PRECISION;
```

Nullable — routes with no dwell observations store NULL.

### Worker computation

Inside `compute_route_speed_daily` (`src/speed/mod.rs`), reuse the existing `combos` loop. After computing `avg_speed` for each route+direction+date, fire one additional aggregate query:

```sql
SELECT AVG(ste.dwell_secs)
FROM stop_time_events ste
JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
WHERE ste.agency_id = $1
  AND t.route_id = $2
  AND COALESCE(t.direction_id, 0) = $3
  AND ste.observed_at::TIMESTAMPTZ::DATE = $4::DATE
  AND ste.dwell_secs > 0
```

`dwell_secs > 0` excludes negative values from malformed GTFS-RT data and zero-dwell pass-through stops. The result (possibly NULL) is bound to the `route_speed_daily` INSERT / ON CONFLICT UPDATE alongside `actual_speed_mps`.

### Display query

In `route_speed_by_day_type` (`src/speed/mod.rs`), add `AVG(avg_dwell_secs) AS avg_dwell_secs` to the `actual_by_day_type` CTE (the CTE queries `FROM route_speed_daily` with no alias) and carry it through to the final SELECT.

`RouteSpeedDayType` gains:
```rust
pub avg_dwell_secs: Option<f64>,
```

## Card layer

`RouteSpeedCard` gains:
```rust
pub avg_dwell_secs: Option<f64>,
```

Averaged across directions in `build_speed_cards` using the existing `avg_speeds` helper (same pattern as `avg_stop_spacing_m`).

Two display methods on `RouteSpeedCard`:

```rust
pub fn avg_dwell_number(&self) -> String {
    match self.avg_dwell_secs {
        None => "—".to_string(),
        Some(s) => format!("{:.0}", s),
    }
}

pub fn avg_dwell_unit(&self) -> &'static str {
    match self.avg_dwell_secs {
        None => "",
        Some(_) => "s",
    }
}
```

No variant/colour coding — always `"neutral"`.

## Template

In `templates/speed_card.html`, add a fourth stat between Actual and Avg stop spacing:

```html
<div>{{ ui::stat(label="Avg dwell", value=card.avg_dwell_number(), unit=card.avg_dwell_unit(), variant="neutral") }}</div>
```

Layout: Scheduled | Actual | Avg dwell on the left flow; Avg stop spacing pushed right with `margin-left:auto` (unchanged).

## Testing

### Unit tests (`src/speed/card.rs`)

- `avg_dwell_number_none` → `"—"`
- `avg_dwell_number_rounds_to_zero_decimal` → e.g. `Some(23.7)` → `"24"`
- `avg_dwell_unit_none` → `""`
- `avg_dwell_unit_some` → `"s"`
- `build_speed_cards_carries_avg_dwell_secs` — single direction row with `avg_dwell_secs = Some(30.0)` → card has `Some(30.0)`
- `build_speed_cards_averages_avg_dwell_across_directions` — two directions (20.0, 40.0) → card has `Some(30.0)`

### Integration tests (`src/speed/mod.rs`)

- Insert two `route_speed_daily` rows with `avg_dwell_secs = Some(25.0)` and `Some(35.0)` for weekday dates, run `route_speed_by_day_type`, assert the returned row has `avg_dwell_secs ≈ 30.0`.
- Insert a row with `avg_dwell_secs = NULL`, assert the returned row has `avg_dwell_secs = None`.

## Out of Scope

- Scheduled dwell time (would require parsing `scheduled_stops.arrival_time`/`departure_time` text fields)
- Per-day-type breakdown of dwell
- Colour coding / thresholds
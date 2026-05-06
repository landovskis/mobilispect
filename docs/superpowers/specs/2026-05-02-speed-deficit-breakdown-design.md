# Speed Deficit Breakdown — Design Spec

**Date:** 2026-05-02

## Goal

Add a "Speed factors" section to the route speed detail page that shows a waterfall chart decomposing the gap between scheduled and actual speed into three attributable causes: extra dwell time at stops, running time loss (congestion proxy), and bunching.

## Background

The route speed detail page already shows scheduled vs actual speed per direction and day type. It does not explain *why* there is a deficit. This feature adds a diagnostic breakdown so operators can see which factor accounts for the most slowdown on a given route.

## Scope

Phase 1 (this spec): fully derived from existing GTFS/GTFS-RT data — no external APIs.

Phase 2 (future): optional traffic API config key that replaces the inferred congestion factor with a measured one.

Schedule padding is explicitly out of scope for now.

## Factors

### 1. Extra dwell time

**Source:** `stop_time_events.dwell_secs` (generated column: `departure_time_unix − arrival_time_unix`) joined to `trips` for the route.

**Scheduled dwell:** `scheduled_stops.departure_time − scheduled_stops.arrival_time` (parsed from HH:MM:SS text).

**Speed impact:** For each trip, compute the dwell excess = actual total dwell − scheduled total dwell. The speed impact is the difference between actual speed and the speed the trip would have achieved with zero dwell excess (i.e. `route_distance / (actual_duration − dwell_excess)`). `route_distance` is computed via haversine between consecutive stops from `scheduled_stops` + `stops`, the same method used in `compute_route_speed`. Average this across all trips in the last 28 days.

**Detail text:** "avg Xs/stop across N stops"

### 2. Running time loss (congestion proxy)

**Source:** `stop_time_events` (actual inter-stop travel time = next `arrival_time_unix` − current `departure_time_unix`) compared against `scheduled_stops` inter-stop times.

**Speed impact:** Running excess = actual running time − scheduled running time per trip. Speed impact = actual speed vs speed achievable if running time matched schedule (`route_distance / (actual_duration − running_excess)`). Average across last 28 days.

**Detail text:** "running time X% above schedule"

### 3. Bunching

**Source:** `vehicle_positions`. Match vehicles to stops using `stop_sequence` where present; fall back to spatial proximity (within 50 m of the stop's lat/lon) when `stop_sequence` is NULL. For each pair of trips on the same route observed at the same stop within 60 seconds of each other, count as a bunching event. The trailing vehicle picks up uneven passenger load, adding estimated extra dwell.

**Speed impact:** Proportion of trips affected by bunching × estimated extra dwell per bunching event (use 15 s as default estimate). Convert to speed impact as above.

**Detail text:** "X% of trips bunched"

## Data Model

New file: `src/speed/breakdown.rs`

```rust
pub struct DeficitFactor {
    pub label: &'static str,
    pub delta_mps: f64,   // always <= 0
    pub detail: String,
}

pub struct SpeedDeficitBreakdown {
    pub scheduled_speed_mps: f64,
    pub actual_speed_mps: f64,
    pub factors: Vec<DeficitFactor>,
    /// Deficit not attributable to the three factors above.
    pub unexplained_mps: f64,
}
```

Public function:

```rust
pub async fn compute_speed_deficit_breakdown(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
    days: i64,
) -> Result<Option<SpeedDeficitBreakdown>>
```

Returns `None` when there is insufficient actual data (no `stop_time_events` rows for the route).

Exported from `src/speed/mod.rs`.

## Computation Window

28 days, matching the existing `route_speed_trend_by_direction` window.

## UI

**Section:** "Speed factors" added to `route_speed_detail.html` below the existing direction charts. One waterfall chart is rendered per direction (one call to `compute_speed_deficit_breakdown` per direction).

**Chart:** Chart.js floating bars (same library already loaded on the page). One row per item:

- Scheduled speed (solid blue `#2980b9`)
- − Extra dwell (red floating bar)
- − Congestion (amber floating bar)
- − Bunching (green floating bar)
- − Unexplained (grey floating bar, hidden if zero)
- Actual speed (solid orange `#e67e22`)

Detail text (avg dwell, % congestion, % bunching) shown in the chart legend or as annotations below.

**Visibility:** Section is hidden entirely when `breakdown` is `None` (no data).

## Handler

No new route. The existing route speed detail handler in `src/web/handlers.rs` gains one additional async call to `breakdown::compute_speed_deficit_breakdown()`. The result is passed into the Askama template context.

## Testing

### Unit tests (`src/speed/breakdown.rs`)

- `compute_factors_all_zero_when_actual_matches_scheduled` — actual times match schedule exactly → all `delta_mps` are 0
- `dwell_factor_scales_with_excess_dwell` — inject known dwell excess, assert expected speed impact
- `running_factor_scales_with_running_excess` — inject known running excess, assert expected speed impact
- `unexplained_equals_deficit_minus_factor_sum` — assert `unexplained_mps = deficit − sum(factors)`
- `returns_none_when_no_stop_time_events` — no rows → returns `None`

### Integration tests

Using testcontainers + real Postgres:

- Insert trips, scheduled_stops, stop_time_events with known dwell and running times → assert breakdown values match hand-computed expectations
- Insert vehicle_positions with two trips within 60 s at same stop → assert bunching factor > 0

## Out of Scope

- Schedule padding
- Traffic API integration (Phase 2)
- Day-type breakdown of the factors (weekday/Saturday/Sunday) — single 28-day average only
- Per-stop breakdown (aggregate only)

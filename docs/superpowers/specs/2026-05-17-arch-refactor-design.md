# Architecture Refactor: Functional Core / Imperative Shell / Vertical Slices

**Date:** 2026-05-17
**Status:** Approved

## Problem

Three architecture gaps identified:

1. `compute_route_daily` (metrics/mod.rs) mixes DB fetches, business logic, and DB writes in a single async loop — no pure computation layer.
2. `speed_page` handler (handlers.rs) mutates `Vec<RouteSpeedCard>` inline (filter, sort, index assignment) instead of delegating to the speed slice.
3. `RouteSpeedDetailDirection` and its display methods live in `handlers.rs`; the inline `sqlx::query_as` for route info also lives in the handler.

## Layer Contract

After this refactor, every slice follows:

```
Handler  →  slice query fn(db, ...)  →  pure computation fn(data, ...)
   ↑                                              ↑
 Axum glue                                   No IO, no async
 (extract params,                            (unit-testable with #[test])
  call slice,
  render template)
```

- **Handlers** (`web/handlers.rs`): extract Axum params, call slice API, render template. No raw `sqlx`, no business logic, no `Vec` mutation.
- **Slice query fns** (`metrics/mod.rs`, `speed/mod.rs`, `speed/detail.rs`): async, hit DB, return `Result<T>`. No presentation logic.
- **Pure computation fns** (`metrics/mod.rs`, `speed/card.rs`, `speed/detail.rs`): sync, no IO, unit-testable in isolation.

## Changes

### 1. `metrics/mod.rs` — Extract pure trip classifier

**New types and function:**

```rust
pub struct TripResult {
    pub on_time: i64,
    pub avg_delay_secs: f64,
    pub max_delay_secs: f64,
}

pub fn classify_trip_delays(
    delays: &[i64],
    early_threshold: i64,
    late_threshold: i64,
) -> TripResult
```

`compute_route_daily` restructures into explicit phases:
1. Fetch trips (async, DB)
2. For each trip: fetch delays (async, DB)
3. Call `classify_trip_delays(delays, ...)` — pure, sync
4. Write trip result (async, DB)
5. Aggregate + write route_daily (existing query, unchanged)

### 2. New file: `src/speed/detail.rs`

Moves out of `handlers.rs`:

- `RouteSpeedDetailDirection` struct + all `impl` methods
  (`avg_spacing_display`, `avg_spacing_status_class`, `direction_badge_label`, `direction_badge_variant`)
- `pub async fn fetch_route_info(db, agency_id, route_id) -> Result<Option<(String, String)>>`
  — extracts the inline `sqlx::query_as` from `route_speed_detail` handler
- `pub fn build_detail_directions(spacings, trends) -> Vec<RouteSpeedDetailDirection>`
  — pure builder, moves the `.into_iter().enumerate().map(...)` block from the handler

`speed/mod.rs` re-exports `detail::fetch_route_info` and `detail::build_detail_directions`.

### 3. `src/speed/card.rs` — Pure pipeline functions

Add pure, value-returning variants (replacing in-place mutation in handlers.rs):

```rust
pub fn sort_speed_cards(cards: Vec<RouteSpeedCard>, sort: &str) -> Vec<RouteSpeedCard>
pub fn filter_speed_cards(cards: Vec<RouteSpeedCard>, class: &str) -> Vec<RouteSpeedCard>
pub fn assign_indices(cards: Vec<RouteSpeedCard>) -> Vec<RouteSpeedCard>
pub fn parse_class(class: &str) -> Option<RouteClass>   // moves from handlers.rs
```

### 4. `web/handlers.rs` — Thin Axum glue

**`speed_page` after refactor:**
```
fetch rows → build_speed_cards → filter_speed_cards → sort_speed_cards → assign_indices → render
```
No mutation, no inline logic.

**`route_speed_detail` after refactor:**
```
fetch_route_info → 404 if None → parallel fetch spacings+trends → build_detail_directions → classify → render
```
No inline `sqlx`.

## Testing

**New unit tests (no DB, no container):**

| Function | Cases |
|---|---|
| `classify_trip_delays` | all on-time, one late, boundary threshold values, empty delays |
| `sort_speed_cards` | existing tests move from `handlers.rs` to `speed/card.rs` |
| `filter_speed_cards` | existing tests move from `handlers.rs` to `speed/card.rs` |
| `assign_indices` | sequential idx from 0 |
| `build_detail_directions` | spacings+trends zipped correctly, chart IDs generated |

**Existing tests — unchanged behaviour:**
- All `#[tokio::test]` DB integration tests in `metrics/mod.rs` continue to cover `compute_route_daily` end-to-end
- All E2E handler tests in `handlers.rs` pass — behaviour is identical, only internal structure changes
- Existing `sort_speed_cards` / `filter_speed_cards` tests in `handlers.rs` are migrated (not rewritten) to `speed/card.rs`

## Files Changed

| File | Change |
|---|---|
| `src/metrics/mod.rs` | Add `TripResult`, `classify_trip_delays`; restructure `compute_route_daily` |
| `src/speed/detail.rs` | **New file** — `RouteSpeedDetailDirection`, `fetch_route_info`, `build_detail_directions` |
| `src/speed/mod.rs` | Add `pub mod detail` + re-exports |
| `src/speed/card.rs` | Add `sort_speed_cards`, `filter_speed_cards`, `assign_indices`, `parse_class` |
| `src/web/handlers.rs` | Remove inline logic; use slice API throughout |

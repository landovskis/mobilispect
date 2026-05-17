# Architecture Refactor: Functional Core / Imperative Shell / Vertical Slices

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate pure computation from IO across the metrics and speed slices, and move all speed display types and pipeline logic out of `handlers.rs` into their owning modules.

**Architecture:** Each handler becomes thin Axum glue: extract params → call slice API → render template. Slice query functions (async, DB) are separated from pure computation functions (sync, no IO, unit-testable with `#[test]` and no database). The speed slice owns all its types.

**Tech Stack:** Rust 2024, Axum 0.7, sqlx 0.8 (compile-time queries), Askama templates, testcontainers for integration tests (existing tests unchanged).

---

## File Map

| File | Change |
|---|---|
| `src/metrics/mod.rs` | Add `TripResult` struct + `classify_trip_delays` pure fn; restructure `compute_route_daily` to call it |
| `src/speed/detail.rs` | **New** — `RouteSpeedDetailDirection` struct + methods, `trend_to_json`, `build_detail_directions` pure fn, `fetch_route_info` query fn |
| `src/speed/mod.rs` | Add `pub mod detail`; re-export `RouteSpeedDetailDirection`, `build_detail_directions`, `fetch_route_info` |
| `src/speed/card.rs` | Add `parse_class`, `filter_speed_cards`, `sort_speed_cards`, `assign_indices` (all pure, owned-value); add corresponding tests |
| `src/web/handlers.rs` | Remove `trend_to_json`, `RouteSpeedDetailDirection`, `sort_speed_cards`, `filter_speed_cards`, `parse_class` and their tests; use slice API throughout |

---

## Task 1: Pure trip classifier in `metrics/mod.rs`

**Files:**
- Modify: `src/metrics/mod.rs`

- [ ] **Step 1: Write failing tests**

Add to the `#[cfg(test)] mod tests` block at the bottom of `src/metrics/mod.rs`:

```rust
#[test]
fn classify_all_on_time_returns_one() {
    let delays = vec![0i64, 60, 120];
    let r = classify_trip_delays(&delays, -60, 300);
    assert_eq!(r.on_time, 1);
}

#[test]
fn classify_one_late_returns_zero() {
    let delays = vec![0i64, 60, 400]; // 400 > 300 late threshold
    let r = classify_trip_delays(&delays, -60, 300);
    assert_eq!(r.on_time, 0);
}

#[test]
fn classify_one_early_returns_zero() {
    let delays = vec![0i64, 60, -120]; // -120 < -60 early threshold
    let r = classify_trip_delays(&delays, -60, 300);
    assert_eq!(r.on_time, 0);
}

#[test]
fn classify_avg_delay_computed_correctly() {
    let delays = vec![100i64, 200, 300];
    let r = classify_trip_delays(&delays, -60, 300);
    assert!((r.avg_delay_secs - 200.0).abs() < 0.001);
}

#[test]
fn classify_max_delay_computed_correctly() {
    let delays = vec![100i64, 200, 300];
    let r = classify_trip_delays(&delays, -60, 300);
    assert!((r.max_delay_secs - 300.0).abs() < 0.001);
}

#[test]
fn classify_empty_delays_returns_on_time_with_zeros() {
    let r = classify_trip_delays(&[], -60, 300);
    assert_eq!(r.on_time, 1);
    assert_eq!(r.avg_delay_secs, 0.0);
    assert_eq!(r.max_delay_secs, 0.0);
}

#[test]
fn classify_late_threshold_boundary_is_inclusive() {
    let r = classify_trip_delays(&[300], -60, 300);
    assert_eq!(r.on_time, 1);
}

#[test]
fn classify_early_threshold_boundary_is_inclusive() {
    let r = classify_trip_delays(&[-60], -60, 300);
    assert_eq!(r.on_time, 1);
}
```

- [ ] **Step 2: Run tests, confirm they fail**

```bash
cargo test classify_ 2>&1 | grep -E "FAILED|error"
```

Expected: compile error — `classify_trip_delays` not defined.

- [ ] **Step 3: Add `TripResult` and `classify_trip_delays` to `src/metrics/mod.rs`**

Add immediately after the existing imports at the top of the file (before `compute_route_daily`):

```rust
/// Computed on-time and delay metrics for a single trip.
pub struct TripResult {
    pub on_time: i64,
    pub avg_delay_secs: f64,
    pub max_delay_secs: f64,
}

/// Classify a set of stop delays against on-time thresholds.
/// Returns vacuously on-time with zero delays when `delays` is empty;
/// callers should skip empty-delay trips before calling this.
pub fn classify_trip_delays(
    delays: &[i64],
    early_threshold: i64,
    late_threshold: i64,
) -> TripResult {
    if delays.is_empty() {
        return TripResult {
            on_time: 1,
            avg_delay_secs: 0.0,
            max_delay_secs: 0.0,
        };
    }
    let avg_delay_secs = delays.iter().sum::<i64>() as f64 / delays.len() as f64;
    let max_delay_secs = delays.iter().copied().max().unwrap_or(0) as f64;
    let on_time = if delays
        .iter()
        .all(|&d| d >= early_threshold && d <= late_threshold)
    {
        1
    } else {
        0
    };
    TripResult {
        on_time,
        avg_delay_secs,
        max_delay_secs,
    }
}
```

- [ ] **Step 4: Run tests, confirm they pass**

```bash
cargo test classify_
```

Expected: 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/metrics/mod.rs
git commit -m "$(cat <<'EOF'
feat(metrics): extract classify_trip_delays pure fn from compute_route_daily

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Restructure `compute_route_daily` to use `classify_trip_delays`

**Files:**
- Modify: `src/metrics/mod.rs`

No new tests needed — the existing integration tests in `metrics/mod.rs` cover `compute_route_daily` end-to-end and will verify behaviour is preserved.

- [ ] **Step 1: Run existing tests to confirm they pass before touching anything**

```bash
cargo test compute_route
```

Expected: any existing tests pass (there may be none directly named this; the worker integration tests call it).

- [ ] **Step 2: Replace the inline calculation block in `compute_route_daily`**

In `src/metrics/mod.rs`, find this block inside the `for trip in &trips {` loop (approximately lines 132–140):

```rust
        let avg_delay = delays.iter().sum::<i64>() as f64 / delays.len() as f64;
        let max_delay = delays.iter().copied().max().unwrap_or(0) as f64;
        let on_time_flag: i64 = if delays.iter().all(|&d| {
            d >= config.on_time_early_threshold_secs && d <= config.on_time_late_threshold_secs
        }) {
            1
        } else {
            0
        };
```

Replace with:

```rust
        let trip_result = classify_trip_delays(
            &delays,
            config.on_time_early_threshold_secs,
            config.on_time_late_threshold_secs,
        );
```

- [ ] **Step 3: Update the INSERT to use `trip_result` fields**

Find the `sqlx::query!` INSERT into `trip_results` immediately after. Change the bound values:

```rust
        sqlx::query!(
            "INSERT INTO trip_results
             (agency_id, trip_id, service_date, route_id, on_time, avg_delay_secs, max_delay_secs, completed, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, 1, $8)
             ON CONFLICT (agency_id, trip_id, service_date) DO UPDATE SET
               route_id = EXCLUDED.route_id,
               on_time = EXCLUDED.on_time,
               avg_delay_secs = EXCLUDED.avg_delay_secs,
               max_delay_secs = EXCLUDED.max_delay_secs,
               completed = EXCLUDED.completed,
               computed_at = EXCLUDED.computed_at",
            &agency_id,
            trip.trip_id,
            date_str,
            trip.route_id,
            trip_result.on_time,
            trip_result.avg_delay_secs,
            trip_result.max_delay_secs,
            now,
        )
        .execute(&db.pool)
        .await?;
```

- [ ] **Step 4: Build and run all tests**

```bash
cargo build && cargo test
```

Expected: all tests pass, no compiler errors.

- [ ] **Step 5: Commit**

```bash
git add src/metrics/mod.rs
git commit -m "$(cat <<'EOF'
refactor(metrics): use classify_trip_delays in compute_route_daily

Separates pure delay classification from DB IO.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Pure pipeline functions in `speed/card.rs`

**Files:**
- Modify: `src/speed/card.rs`

- [ ] **Step 1: Write failing tests**

Add to the `#[cfg(test)] mod tests` block in `src/speed/card.rs`. First add a helper at the top of the test module:

```rust
    fn make_card(short_name: &str) -> RouteSpeedCard {
        RouteSpeedCard {
            idx: 0,
            agency_name: "Agency".into(),
            agency_id: "a".into(),
            route_id: "R1".into(),
            short_name: short_name.into(),
            long_name: short_name.into(),
            avg_scheduled_speed_mps: None,
            avg_actual_speed_mps: None,
            avg_stop_spacing_m: None,
            avg_dwell_secs: None,
            classification: None,
        }
    }

    fn make_card_with_speeds(short_name: &str, scheduled: f64, actual: Option<f64>) -> RouteSpeedCard {
        RouteSpeedCard {
            avg_scheduled_speed_mps: Some(scheduled),
            avg_actual_speed_mps: actual,
            ..make_card(short_name)
        }
    }
```

Then add the tests:

```rust
    // ── parse_class ────────────────────────────────────────────────────────

    #[test]
    fn parse_class_local() {
        assert_eq!(parse_class("local"), Some(RouteClass::Local));
    }

    #[test]
    fn parse_class_rapid() {
        assert_eq!(parse_class("rapid"), Some(RouteClass::Rapid));
    }

    #[test]
    fn parse_class_express() {
        assert_eq!(parse_class("express"), Some(RouteClass::Express));
    }

    #[test]
    fn parse_class_unknown_returns_none() {
        assert_eq!(parse_class(""), None);
        assert_eq!(parse_class("bogus"), None);
    }

    // ── filter_speed_cards ─────────────────────────────────────────────────

    #[test]
    fn filter_keeps_matching_class() {
        let cards = vec![
            RouteSpeedCard { classification: Some(RouteClass::Local), ..make_card("L") },
            RouteSpeedCard { classification: Some(RouteClass::Rapid), ..make_card("R") },
        ];
        let result = filter_speed_cards(cards, "rapid");
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].short_name, "R");
    }

    #[test]
    fn filter_empty_class_keeps_all() {
        let cards = vec![
            RouteSpeedCard { classification: Some(RouteClass::Local), ..make_card("L") },
            RouteSpeedCard { classification: None, ..make_card("U") },
        ];
        let result = filter_speed_cards(cards, "");
        assert_eq!(result.len(), 2);
    }

    #[test]
    fn filter_hides_unclassified_when_class_given() {
        let cards = vec![
            RouteSpeedCard { classification: None, ..make_card("U") },
            RouteSpeedCard { classification: Some(RouteClass::Local), ..make_card("L") },
        ];
        let result = filter_speed_cards(cards, "local");
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].short_name, "L");
    }

    // ── sort_speed_cards ───────────────────────────────────────────────────

    #[test]
    fn sort_scheduled_ascending() {
        let cards = vec![make_card_with_speeds("B", 10.0, None), make_card_with_speeds("A", 5.0, None)];
        let result = sort_speed_cards(cards, "scheduled");
        assert_eq!(result[0].short_name, "A");
        assert_eq!(result[1].short_name, "B");
    }

    #[test]
    fn sort_scheduled_none_last() {
        let cards = vec![make_card("N"), make_card_with_speeds("B", 5.0, None)];
        let result = sort_speed_cards(cards, "scheduled");
        assert_eq!(result[0].short_name, "B");
        assert_eq!(result[1].short_name, "N");
    }

    #[test]
    fn sort_scheduled_ties_broken_by_name() {
        let cards = vec![make_card_with_speeds("Z", 5.0, None), make_card_with_speeds("A", 5.0, None)];
        let result = sort_speed_cards(cards, "scheduled");
        assert_eq!(result[0].short_name, "A");
    }

    #[test]
    fn sort_actual_ascending() {
        let cards = vec![make_card_with_speeds("B", 10.0, Some(8.0)), make_card_with_speeds("A", 5.0, Some(3.0))];
        let result = sort_speed_cards(cards, "actual");
        assert_eq!(result[0].short_name, "A");
    }

    #[test]
    fn sort_actual_none_last() {
        let cards = vec![make_card_with_speeds("A", 5.0, None), make_card_with_speeds("B", 10.0, Some(3.0))];
        let result = sort_speed_cards(cards, "actual");
        assert_eq!(result[0].short_name, "B");
    }

    #[test]
    fn sort_spacing_ascending() {
        let cards = vec![
            RouteSpeedCard { avg_stop_spacing_m: Some(800.0), ..make_card("B") },
            RouteSpeedCard { avg_stop_spacing_m: Some(300.0), ..make_card("A") },
        ];
        let result = sort_speed_cards(cards, "spacing");
        assert_eq!(result[0].short_name, "A");
    }

    #[test]
    fn sort_name_preserves_order() {
        let cards = vec![make_card("B"), make_card("A")];
        let result = sort_speed_cards(cards, "name");
        assert_eq!(result[0].short_name, "B");
    }

    #[test]
    fn sort_unknown_param_preserves_order() {
        let cards = vec![make_card("B"), make_card("A")];
        let result = sort_speed_cards(cards, "bogus");
        assert_eq!(result[0].short_name, "B");
    }

    // ── assign_indices ─────────────────────────────────────────────────────

    #[test]
    fn assign_indices_sets_sequential_idx_from_zero() {
        let cards = vec![
            RouteSpeedCard { idx: 99, ..make_card("A") },
            RouteSpeedCard { idx: 99, ..make_card("B") },
            RouteSpeedCard { idx: 99, ..make_card("C") },
        ];
        let result = assign_indices(cards);
        assert_eq!(result[0].idx, 0);
        assert_eq!(result[1].idx, 1);
        assert_eq!(result[2].idx, 2);
    }

    #[test]
    fn assign_indices_empty_input() {
        let result = assign_indices(vec![]);
        assert!(result.is_empty());
    }
```

- [ ] **Step 2: Run tests, confirm they fail**

```bash
cargo test -p mobilispect 2>&1 | grep -E "error\[" | head -5
```

Expected: compile error — `parse_class`, `filter_speed_cards`, `sort_speed_cards`, `assign_indices` not defined.

- [ ] **Step 3: Add the four pure functions to `src/speed/card.rs`**

Add after the existing `build_speed_cards` function and before the `#[cfg(test)]` block:

```rust
pub fn parse_class(class: &str) -> Option<RouteClass> {
    match class {
        "local" => Some(RouteClass::Local),
        "rapid" => Some(RouteClass::Rapid),
        "express" => Some(RouteClass::Express),
        _ => None,
    }
}

pub fn filter_speed_cards(cards: Vec<RouteSpeedCard>, class: &str) -> Vec<RouteSpeedCard> {
    let Some(target) = parse_class(class) else {
        return cards;
    };
    cards.into_iter().filter(|c| c.classification == Some(target)).collect()
}

pub fn sort_speed_cards(mut cards: Vec<RouteSpeedCard>, sort: &str) -> Vec<RouteSpeedCard> {
    match sort {
        "scheduled" => cards.sort_by(
            |a, b| match (a.avg_scheduled_speed_mps, b.avg_scheduled_speed_mps) {
                (Some(x), Some(y)) => x
                    .partial_cmp(&y)
                    .unwrap_or(std::cmp::Ordering::Equal)
                    .then(a.short_name.cmp(&b.short_name)),
                (Some(_), None) => std::cmp::Ordering::Less,
                (None, Some(_)) => std::cmp::Ordering::Greater,
                (None, None) => a.short_name.cmp(&b.short_name),
            },
        ),
        "actual" => cards.sort_by(
            |a, b| match (a.avg_actual_speed_mps, b.avg_actual_speed_mps) {
                (Some(x), Some(y)) => x
                    .partial_cmp(&y)
                    .unwrap_or(std::cmp::Ordering::Equal)
                    .then(a.short_name.cmp(&b.short_name)),
                (Some(_), None) => std::cmp::Ordering::Less,
                (None, Some(_)) => std::cmp::Ordering::Greater,
                (None, None) => a.short_name.cmp(&b.short_name),
            },
        ),
        "spacing" => cards.sort_by(|a, b| match (a.avg_stop_spacing_m, b.avg_stop_spacing_m) {
            (Some(x), Some(y)) => x
                .partial_cmp(&y)
                .unwrap_or(std::cmp::Ordering::Equal)
                .then(a.short_name.cmp(&b.short_name)),
            (Some(_), None) => std::cmp::Ordering::Less,
            (None, Some(_)) => std::cmp::Ordering::Greater,
            (None, None) => a.short_name.cmp(&b.short_name),
        }),
        _ => {}
    }
    cards
}

pub fn assign_indices(mut cards: Vec<RouteSpeedCard>) -> Vec<RouteSpeedCard> {
    for (i, card) in cards.iter_mut().enumerate() {
        card.idx = i;
    }
    cards
}
```

- [ ] **Step 4: Run tests, confirm new tests pass**

```bash
cargo test -p mobilispect 2>&1 | grep -E "parse_class|filter_keep|filter_empty|filter_hides|sort_|assign_"
```

Expected: all new tests pass.

- [ ] **Step 5: Confirm full test suite still passes**

```bash
cargo test
```

Expected: all tests pass (handlers.rs still has its own versions of these functions — that's fine until Task 6).

- [ ] **Step 6: Commit**

```bash
git add src/speed/card.rs
git commit -m "$(cat <<'EOF'
feat(speed): add pure pipeline fns — filter, sort, assign_indices, parse_class

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Create `src/speed/detail.rs`

**Files:**
- Create: `src/speed/detail.rs`

- [ ] **Step 1: Write failing tests in a new file**

Create `src/speed/detail.rs` with the tests only (no implementation yet):

```rust
use crate::speed::{DirectionStopSpacings, StopSpacing, VariantSpeedTrend};

pub struct RouteSpeedDetailDirection {
    pub variant_id: String,
    pub direction_name: String,
    pub first_stop_name: String,
    pub is_primary: bool,
    pub trip_count: i64,
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
    pub weekday_chart_id: String,
    pub saturday_chart_id: String,
    pub sunday_chart_id: String,
    pub weekday_json: String,
    pub saturday_json: String,
    pub sunday_json: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_spacing(variant_id: &str) -> DirectionStopSpacings {
        DirectionStopSpacings {
            direction_id: 0,
            variant_id: variant_id.to_string(),
            is_primary: true,
            trip_count: 10,
            direction_name: "A → B".to_string(),
            first_stop_name: "A".to_string(),
            avg_spacing_m: 500.0,
            spacings: vec![],
        }
    }

    fn make_trend(variant_id: &str) -> VariantSpeedTrend {
        VariantSpeedTrend {
            variant_id: variant_id.to_string(),
            weekday: vec![("2026-01-06".to_string(), 8.0, Some(9.0))],
            saturday: vec![],
            sunday: vec![],
        }
    }

    #[test]
    fn build_detail_directions_generates_chart_ids_with_index() {
        let spacings = vec![make_spacing("V1"), make_spacing("V2")];
        let dirs = build_detail_directions(spacings, vec![]);
        assert_eq!(dirs[0].weekday_chart_id, "weekday-0");
        assert_eq!(dirs[0].saturday_chart_id, "saturday-0");
        assert_eq!(dirs[0].sunday_chart_id, "sunday-0");
        assert_eq!(dirs[1].weekday_chart_id, "weekday-1");
    }

    #[test]
    fn build_detail_directions_no_matching_trend_produces_empty_json() {
        let spacings = vec![make_spacing("V1")];
        let dirs = build_detail_directions(spacings, vec![]);
        assert_eq!(dirs[0].weekday_json, "[]");
        assert_eq!(dirs[0].saturday_json, "[]");
        assert_eq!(dirs[0].sunday_json, "[]");
    }

    #[test]
    fn build_detail_directions_matching_trend_produces_non_empty_json() {
        let spacings = vec![make_spacing("V1")];
        let trends = vec![make_trend("V1")];
        let dirs = build_detail_directions(spacings, trends);
        assert_ne!(dirs[0].weekday_json, "[]");
    }

    #[test]
    fn build_detail_directions_preserves_spacing_fields() {
        let spacings = vec![make_spacing("V1")];
        let dirs = build_detail_directions(spacings, vec![]);
        assert_eq!(dirs[0].variant_id, "V1");
        assert_eq!(dirs[0].direction_name, "A → B");
        assert_eq!(dirs[0].first_stop_name, "A");
        assert!(dirs[0].is_primary);
        assert_eq!(dirs[0].trip_count, 10);
        assert!((dirs[0].avg_spacing_m - 500.0).abs() < 0.001);
    }

    #[test]
    fn build_detail_directions_ignores_trend_for_different_variant() {
        let spacings = vec![make_spacing("V1")];
        let trends = vec![make_trend("V2")]; // different variant
        let dirs = build_detail_directions(spacings, trends);
        assert_eq!(dirs[0].weekday_json, "[]");
    }

    #[test]
    fn avg_spacing_display_under_1km_shows_metres() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 342.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_display(), "342 m");
    }

    #[test]
    fn avg_spacing_display_at_or_over_1km_shows_km() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 1200.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_display(), "1.2 km");
    }

    #[test]
    fn avg_spacing_status_class_below_local_range_min_is_slow() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 200.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_status_class(), "slow");
    }

    #[test]
    fn avg_spacing_status_class_in_range_is_empty() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 400.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_status_class(), "");
    }

    #[test]
    fn avg_spacing_status_class_above_express_max_is_outlier() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 6000.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_status_class(), "outlier");
    }

    #[test]
    fn direction_badge_label_primary_includes_primary_text() {
        let d = RouteSpeedDetailDirection {
            is_primary: true, trip_count: 42,
            avg_spacing_m: 0.0, variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), spacings: vec![],
            weekday_chart_id: String::new(), saturday_chart_id: String::new(),
            sunday_chart_id: String::new(), weekday_json: String::new(),
            saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.direction_badge_label(), "Primary · 42 trips");
    }

    #[test]
    fn direction_badge_label_non_primary_shows_trip_count_only() {
        let d = RouteSpeedDetailDirection {
            is_primary: false, trip_count: 7,
            avg_spacing_m: 0.0, variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), spacings: vec![],
            weekday_chart_id: String::new(), saturday_chart_id: String::new(),
            sunday_chart_id: String::new(), weekday_json: String::new(),
            saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.direction_badge_label(), "7 trips");
    }
}
```

- [ ] **Step 2: Wire the new module into `src/speed/mod.rs`** (minimal, just enough to compile)

Add at the top of `src/speed/mod.rs`, after `pub mod card;`:

```rust
pub mod detail;
```

- [ ] **Step 3: Run tests, confirm they fail to compile**

```bash
cargo test 2>&1 | grep "error\[" | head -5
```

Expected: compile error — `build_detail_directions`, `RouteSpeedDetailDirection` methods not defined.

- [ ] **Step 4: Add the implementation above the `#[cfg(test)]` block in `src/speed/detail.rs`**

The struct definition is already there from Step 1. Insert the following between the struct definition and the `#[cfg(test)]` block:

```rust
impl RouteSpeedDetailDirection {
    pub fn avg_spacing_display(&self) -> String {
        if self.avg_spacing_m >= 1000.0 {
            format!("{:.1} km", self.avg_spacing_m / 1000.0)
        } else {
            format!("{:.0} m", self.avg_spacing_m)
        }
    }

    pub fn avg_spacing_status_class(&self) -> &str {
        let avg = self.avg_spacing_m;
        let (range_min, range_max) = if avg < 500.0 {
            (300.0, 500.0)
        } else if avg < 1500.0 {
            (500.0, 1500.0)
        } else {
            (1500.0, 5000.0)
        };
        if avg < range_min {
            "slow"
        } else if avg > range_max {
            "outlier"
        } else {
            ""
        }
    }

    pub fn direction_badge_label(&self) -> String {
        if self.is_primary {
            format!("Primary · {} trips", self.trip_count)
        } else {
            format!("{} trips", self.trip_count)
        }
    }

    pub fn direction_badge_variant(&self) -> &'static str {
        if self.is_primary { "oxford" } else { "neutral" }
    }
}

fn trend_to_json(points: Vec<(String, f64, Option<f64>)>) -> String {
    #[derive(serde::Serialize)]
    struct TrendPoint {
        date: String,
        actual_kmh: f64,
        scheduled_kmh: Option<f64>,
    }
    let pts: Vec<TrendPoint> = points
        .into_iter()
        .map(|(date, actual_mps, scheduled_mps)| TrendPoint {
            date,
            actual_kmh: (actual_mps * 3.6 * 10.0).round() / 10.0,
            scheduled_kmh: scheduled_mps.map(|s| (s * 3.6 * 10.0).round() / 10.0),
        })
        .collect();
    serde_json::to_string(&pts).unwrap_or_else(|_| "[]".to_string())
}

pub fn build_detail_directions(
    spacings: Vec<DirectionStopSpacings>,
    trends: Vec<VariantSpeedTrend>,
) -> Vec<RouteSpeedDetailDirection> {
    spacings
        .into_iter()
        .enumerate()
        .map(|(i, spacing)| {
            let trend = trends.iter().find(|t| t.variant_id == spacing.variant_id);
            let (weekday, saturday, sunday) = trend
                .map(|t| (t.weekday.clone(), t.saturday.clone(), t.sunday.clone()))
                .unwrap_or_default();
            RouteSpeedDetailDirection {
                variant_id: spacing.variant_id,
                direction_name: spacing.direction_name,
                first_stop_name: spacing.first_stop_name,
                is_primary: spacing.is_primary,
                trip_count: spacing.trip_count,
                avg_spacing_m: spacing.avg_spacing_m,
                spacings: spacing.spacings,
                weekday_chart_id: format!("weekday-{i}"),
                saturday_chart_id: format!("saturday-{i}"),
                sunday_chart_id: format!("sunday-{i}"),
                weekday_json: trend_to_json(weekday),
                saturday_json: trend_to_json(saturday),
                sunday_json: trend_to_json(sunday),
            }
        })
        .collect()
}

pub async fn fetch_route_info(
    db: &Database,
    agency_id: &str,
    route_id: &str,
) -> Result<Option<(String, String)>> {
    let row: Option<(String, String)> = sqlx::query_as(
        "SELECT short_name, long_name FROM routes WHERE agency_id = $1 AND route_id = $2",
    )
    .bind(agency_id)
    .bind(route_id)
    .fetch_optional(&db.pool)
    .await?;
    Ok(row)
}
```

Also add these imports at the top of the file:

```rust
use anyhow::Result;
use crate::db::Database;
use crate::speed::{DirectionStopSpacings, StopSpacing, VariantSpeedTrend};
```

- [ ] **Step 5: Run tests**

```bash
cargo test detail::tests
```

Expected: all detail tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/speed/detail.rs src/speed/mod.rs
git commit -m "$(cat <<'EOF'
feat(speed): add detail.rs with RouteSpeedDetailDirection, build_detail_directions, fetch_route_info

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Wire `src/speed/mod.rs` re-exports

**Files:**
- Modify: `src/speed/mod.rs`

- [ ] **Step 1: Update the re-export block in `src/speed/mod.rs`**

Find the current re-export line near the top of the file:

```rust
pub mod card;
pub use card::{RouteClass, RouteSpeedCard, build_speed_cards, classify_by_spacing};
```

Replace with:

```rust
pub mod card;
pub mod detail;
pub use card::{
    RouteClass, RouteSpeedCard, assign_indices, build_speed_cards, classify_by_spacing,
    filter_speed_cards, parse_class, sort_speed_cards,
};
pub use detail::{RouteSpeedDetailDirection, build_detail_directions, fetch_route_info};
```

Also remove the `pub mod detail;` line added in Task 4 Step 2 (it's now included above).

- [ ] **Step 2: Build and run all tests**

```bash
cargo build && cargo test
```

Expected: all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/speed/mod.rs
git commit -m "$(cat <<'EOF'
refactor(speed): re-export detail and card pipeline fns from speed/mod.rs

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Thin out `src/web/handlers.rs`

**Files:**
- Modify: `src/web/handlers.rs`

- [ ] **Step 1: Update imports**

Replace the current `use crate::speed::{...}` block in `src/web/handlers.rs`:

```rust
use crate::speed::{
    RouteClass, RouteSpeedCard, RouteSpeedSummary, RouteSpeedDetailDirection, StopSpacing,
    VariantSpeedTrend, assign_indices, build_detail_directions, build_speed_cards,
    classify_by_spacing, fetch_route_info, filter_speed_cards, parse_class,
    route_speed_by_day_type, route_speed_summary, route_speed_trend_by_variant,
    route_stop_spacings, sort_speed_cards,
};
```

- [ ] **Step 2: Delete removed functions and their test code from handlers.rs**

Remove all of the following from `src/web/handlers.rs`:
- The `fn trend_to_json(...)` function (lines ~90–106)
- The `fn sort_speed_cards(cards: &mut Vec<RouteSpeedCard>, sort: &str)` function
- The `fn parse_class(class: &str) -> Option<RouteClass>` function
- The `fn filter_speed_cards(cards: &mut Vec<RouteSpeedCard>, class: &str)` function
- The `struct RouteSpeedDetailDirection { ... }` definition
- The `impl RouteSpeedDetailDirection { ... }` block

Also remove from `#[cfg(test)] mod tests`:
- `fn card(...)` helper (used only by old sort/filter tests)
- `fn card_no_scheduled(...)` helper
- `fn card_with_spacing(...)` helper (the one in the test module, not card.rs)
- `fn card_with_class(...)` helper
- `fn direction(...)` helper
- All `sort_*` tests (they now live in speed/card.rs)
- All `filter_by_*` tests (they now live in speed/card.rs)
- All `avg_spacing_status_class_*` tests (they now live in speed/detail.rs)

- [ ] **Step 3: Replace `route_speed_detail` handler body**

Replace the body of `pub async fn route_speed_detail(...)` in `handlers.rs`:

```rust
pub async fn route_speed_detail(
    State(state): State<AppState>,
    axum::extract::Path((agency_id, route_id)): axum::extract::Path<(String, String)>,
) -> axum::response::Response {
    use axum::response::IntoResponse;

    let (short_name, long_name) = match fetch_route_info(&state.db, &agency_id, &route_id)
        .await
        .unwrap_or_else(|e| {
            tracing::error!("DB error fetching route {agency_id}/{route_id}: {e}");
            None
        }) {
        Some(r) => r,
        None => {
            return (
                axum::http::StatusCode::NOT_FOUND,
                Html("<h1>Not Found</h1>".to_string()),
            )
                .into_response();
        }
    };

    let (spacings_res, trends_res) = tokio::join!(
        route_stop_spacings(&state.db, &agency_id, &route_id),
        route_speed_trend_by_variant(&state.db, &agency_id, &route_id, 28),
    );

    let spacings = spacings_res.unwrap_or_else(|e| {
        tracing::error!("route_stop_spacings failed for {agency_id}/{route_id}: {e}");
        vec![]
    });
    let trends = trends_res.unwrap_or_else(|e| {
        tracing::error!("route_speed_trend_by_variant failed for {agency_id}/{route_id}: {e}");
        vec![]
    });

    if spacings.is_empty() {
        return (
            axum::http::StatusCode::NOT_FOUND,
            Html("<h1>Not Found</h1>".to_string()),
        )
            .into_response();
    }

    let directions = build_detail_directions(spacings, trends);
    let avg_spacing_m: Option<f64> = {
        let vals: Vec<f64> = directions.iter().map(|d| d.avg_spacing_m).collect();
        if vals.is_empty() {
            None
        } else {
            Some(vals.iter().sum::<f64>() / vals.len() as f64)
        }
    };
    let classification = avg_spacing_m.map(classify_by_spacing);

    let tmpl = RouteSpeedDetailTemplate {
        region_name: state.config.region.name.clone(),
        short_name,
        long_name,
        agency_id,
        directions,
        classification,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
    .into_response()
}
```

- [ ] **Step 4: Replace `speed_page` handler body**

Replace the body of `pub async fn speed_page(...)` in `handlers.rs`:

```rust
pub async fn speed_page(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<SpeedParams>,
) -> Html<String> {
    let agencies: Vec<(String, String)> = state
        .config
        .agencies
        .iter()
        .map(|a| (a.id.to_string(), a.name.clone()))
        .collect();
    let active_agency = params
        .agency
        .filter(|s| agencies.iter().any(|(id, _)| id == s))
        .unwrap_or_default();
    let active_sort = match params.sort.as_deref() {
        Some("scheduled") => "scheduled",
        Some("actual") => "actual",
        Some("spacing") => "spacing",
        _ => "name",
    }
    .to_string();
    let active_class = match params.class.as_deref() {
        Some("local") => "local",
        Some("rapid") => "rapid",
        Some("express") => "express",
        _ => "",
    }
    .to_string();
    let filter = if active_agency.is_empty() {
        None
    } else {
        Some(active_agency.as_str())
    };
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();

    let rows = route_speed_by_day_type(&state.db, filter)
        .await
        .unwrap_or_default();
    let cards = assign_indices(sort_speed_cards(
        filter_speed_cards(build_speed_cards(rows, &agency_names), &active_class),
        &active_sort,
    ));

    if headers.contains_key("hx-request") {
        let tmpl = SpeedContentTemplate {
            cards,
            agencies,
            active_agency,
            active_sort,
            active_class,
        };
        Html(
            tmpl.render()
                .unwrap_or_else(|e| format!("Template error: {e}")),
        )
    } else {
        let tmpl = SpeedTemplate {
            region_name: state.config.region.name.clone(),
            cards,
            agencies,
            active_agency,
            active_sort,
            active_class,
        };
        Html(
            tmpl.render()
                .unwrap_or_else(|e| format!("Template error: {e}")),
        )
    }
}
```

- [ ] **Step 5: Build and run the full test suite**

```bash
cargo build && cargo test
```

Expected: all tests pass. Fix any remaining unused import warnings with `cargo clippy --fix`.

- [ ] **Step 6: Commit**

```bash
git add src/web/handlers.rs
git commit -m "$(cat <<'EOF'
refactor(handlers): thin handlers to Axum glue only — delegate to speed slice API

Removes inline sort/filter/display logic and RouteSpeedDetailDirection
from handlers.rs; all speed slice concerns now live in speed/.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

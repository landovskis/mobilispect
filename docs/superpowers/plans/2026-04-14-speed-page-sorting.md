# Speed Page Sorting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add server-side sorting (name / slowest scheduled / slowest actual) to the speed page and reorganise the controls bar into clearly labelled Agency and Sort-by rows.

**Architecture:** Sort keys (average speed across all day types and directions) are computed in `build_speed_cards` and stored on `RouteSpeedCard`. The handler reads a `?sort=` query param and sorts the card slice in-place before rendering. The template renders two labelled pill rows; each link preserves the other dimension's current value.

**Tech Stack:** Rust, Axum, Askama templates, sqlx (no DB changes).

---

### Task 1: Add sort key fields to `RouteSpeedCard`

**Files:**
- Modify: `src/speed/card.rs`

- [ ] **Step 1: Write the failing tests**

Add inside `#[cfg(test)]` in `src/speed/card.rs`:

```rust
#[test]
fn build_speed_cards_computes_avg_scheduled_speed() {
    // Two directions, weekday only. avg = (8.0 + 6.0) / 2 = 7.0 m/s
    let rows = vec![
        RouteSpeedDayType {
            agency_id: "stm".into(), route_id: "R1".into(),
            short_name: "R1".into(), long_name: "Route R1".into(),
            direction_id: 0,
            weekday_speed_mps: Some(8.0),
            saturday_speed_mps: None, sunday_speed_mps: None,
            actual_weekday_speed_mps: None,
            actual_saturday_speed_mps: None, actual_sunday_speed_mps: None,
        },
        RouteSpeedDayType {
            agency_id: "stm".into(), route_id: "R1".into(),
            short_name: "R1".into(), long_name: "Route R1".into(),
            direction_id: 1,
            weekday_speed_mps: Some(6.0),
            saturday_speed_mps: None, sunday_speed_mps: None,
            actual_weekday_speed_mps: None,
            actual_saturday_speed_mps: None, actual_sunday_speed_mps: None,
        },
    ];
    let cards = build_speed_cards(rows, &HashMap::new());
    let avg = cards[0].avg_scheduled_speed_mps.unwrap();
    assert!((avg - 7.0).abs() < 0.001, "expected 7.0, got {avg}");
}

#[test]
fn build_speed_cards_avg_scheduled_uses_all_day_types() {
    // One direction, three day types: (9.0 + 6.0 + 3.0) / 3 = 6.0
    let rows = vec![RouteSpeedDayType {
        agency_id: "stm".into(), route_id: "R1".into(),
        short_name: "R1".into(), long_name: "Route R1".into(),
        direction_id: 0,
        weekday_speed_mps: Some(9.0),
        saturday_speed_mps: Some(6.0),
        sunday_speed_mps: Some(3.0),
        actual_weekday_speed_mps: None,
        actual_saturday_speed_mps: None, actual_sunday_speed_mps: None,
    }];
    let cards = build_speed_cards(rows, &HashMap::new());
    let avg = cards[0].avg_scheduled_speed_mps.unwrap();
    assert!((avg - 6.0).abs() < 0.001, "expected 6.0, got {avg}");
}

#[test]
fn build_speed_cards_avg_actual_is_none_when_no_actual_data() {
    let rows = vec![make_row("stm", "R1", 0, Some(8.0))];
    let cards = build_speed_cards(rows, &HashMap::new());
    assert!(cards[0].avg_actual_speed_mps.is_none());
}

#[test]
fn build_speed_cards_computes_avg_actual_speed() {
    // actual weekday = 5.0, actual saturday = 7.0 → avg = 6.0
    let rows = vec![RouteSpeedDayType {
        agency_id: "stm".into(), route_id: "R1".into(),
        short_name: "R1".into(), long_name: "Route R1".into(),
        direction_id: 0,
        weekday_speed_mps: Some(8.0),
        saturday_speed_mps: None, sunday_speed_mps: None,
        actual_weekday_speed_mps: Some(5.0),
        actual_saturday_speed_mps: Some(7.0),
        actual_sunday_speed_mps: None,
    }];
    let cards = build_speed_cards(rows, &HashMap::new());
    let avg = cards[0].avg_actual_speed_mps.unwrap();
    assert!((avg - 6.0).abs() < 0.001, "expected 6.0, got {avg}");
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test build_speed_cards_computes_avg 2>&1 | grep -E "FAILED|error"
```

Expected: compile error — fields `avg_scheduled_speed_mps` / `avg_actual_speed_mps` do not exist yet.

- [ ] **Step 3: Add the helper and new fields**

In `src/speed/card.rs`, update `RouteSpeedCard` and `build_speed_cards`:

```rust
pub struct RouteSpeedCard {
    pub idx: usize,
    pub agency_name: String,
    pub short_name: String,
    pub long_name: String,
    pub charts: Vec<DirectionSpeedChart>,
    /// Average scheduled speed across all day types and directions (m/s).
    pub avg_scheduled_speed_mps: Option<f64>,
    /// Average actual speed across all day types and directions (m/s).
    /// None when no actual speed data exists for this route.
    pub avg_actual_speed_mps: Option<f64>,
}

/// Returns the mean of all `Some` values in `iter`, or `None` if there are none.
fn avg_speeds(iter: impl Iterator<Item = Option<f64>>) -> Option<f64> {
    let vals: Vec<f64> = iter.flatten().collect();
    if vals.is_empty() {
        None
    } else {
        Some(vals.iter().sum::<f64>() / vals.len() as f64)
    }
}
```

Then inside `build_speed_cards`, replace the `cards.push(RouteSpeedCard { ... })` call:

```rust
let avg_scheduled_speed_mps = avg_speeds(route_rows.iter().flat_map(|r| {
    [r.weekday_speed_mps, r.saturday_speed_mps, r.sunday_speed_mps]
}));
let avg_actual_speed_mps = avg_speeds(route_rows.iter().flat_map(|r| {
    [r.actual_weekday_speed_mps, r.actual_saturday_speed_mps, r.actual_sunday_speed_mps]
}));
cards.push(RouteSpeedCard {
    idx: card_idx,
    agency_name,
    short_name: first.short_name.clone(),
    long_name: first.long_name.clone(),
    charts,
    avg_scheduled_speed_mps,
    avg_actual_speed_mps,
});
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cargo test --lib speed::card 2>&1 | tail -5
```

Expected: `test result: ok. N passed; 0 failed`

- [ ] **Step 5: Commit**

```bash
git add src/speed/card.rs
git commit -m "feat: add avg speed sort keys to RouteSpeedCard"
```

---

### Task 2: Add `?sort=` param and sort logic to the handler

**Files:**
- Modify: `src/web/handlers.rs`

- [ ] **Step 1: Write the failing tests**

Add a `#[cfg(test)]` module at the bottom of `src/web/handlers.rs`:

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use crate::speed::RouteSpeedCard;

    fn card(short_name: &str, scheduled: f64, actual: Option<f64>) -> RouteSpeedCard {
        RouteSpeedCard {
            idx: 0,
            agency_name: "A".into(),
            short_name: short_name.into(),
            long_name: short_name.into(),
            charts: vec![],
            avg_scheduled_speed_mps: Some(scheduled),
            avg_actual_speed_mps: actual,
        }
    }

    #[test]
    fn sort_scheduled_orders_ascending_by_scheduled_speed() {
        let mut cards = vec![card("B", 10.0, None), card("A", 5.0, None)];
        sort_speed_cards(&mut cards, "scheduled");
        assert_eq!(cards[0].short_name, "A");
        assert_eq!(cards[1].short_name, "B");
    }

    #[test]
    fn sort_actual_orders_ascending_by_actual_speed() {
        let mut cards = vec![card("B", 10.0, Some(8.0)), card("A", 5.0, Some(3.0))];
        sort_speed_cards(&mut cards, "actual");
        assert_eq!(cards[0].short_name, "A");
        assert_eq!(cards[1].short_name, "B");
    }

    #[test]
    fn sort_actual_puts_none_last() {
        let mut cards = vec![
            card("A", 5.0, None),
            card("B", 10.0, Some(3.0)),
        ];
        sort_speed_cards(&mut cards, "actual");
        assert_eq!(cards[0].short_name, "B");
        assert_eq!(cards[1].short_name, "A");
    }

    #[test]
    fn sort_name_leaves_order_unchanged() {
        let mut cards = vec![card("B", 5.0, None), card("A", 10.0, None)];
        sort_speed_cards(&mut cards, "name");
        assert_eq!(cards[0].short_name, "B");
        assert_eq!(cards[1].short_name, "A");
    }

    #[test]
    fn sort_unknown_param_leaves_order_unchanged() {
        let mut cards = vec![card("B", 5.0, None), card("A", 10.0, None)];
        sort_speed_cards(&mut cards, "bogus");
        assert_eq!(cards[0].short_name, "B");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cargo test --lib web::handlers::tests 2>&1 | grep -E "FAILED|error"
```

Expected: compile error — `sort_speed_cards`, `SpeedParams` not defined yet.

- [ ] **Step 3: Add `SpeedParams`, `sort_speed_cards`, update `SpeedTemplate` and `speed_page`**

Replace `AgencyFilterParams` in `SpeedTemplate` and `speed_page` with the following. Add `SpeedParams` near the top of the handler file (alongside the existing `AgencyFilterParams`):

```rust
#[derive(Deserialize)]
pub struct SpeedParams {
    agency: Option<String>,
    sort: Option<String>,
}
```

Add `sort_speed_cards` as a free function (not `pub`) in `handlers.rs`:

```rust
fn sort_speed_cards(cards: &mut Vec<RouteSpeedCard>, sort: &str) {
    match sort {
        "scheduled" => cards.sort_by(|a, b| {
            a.avg_scheduled_speed_mps
                .partial_cmp(&b.avg_scheduled_speed_mps)
                .unwrap_or(std::cmp::Ordering::Equal)
                .then(a.short_name.cmp(&b.short_name))
        }),
        "actual" => cards.sort_by(|a, b| {
            match (a.avg_actual_speed_mps, b.avg_actual_speed_mps) {
                (Some(x), Some(y)) => x
                    .partial_cmp(&y)
                    .unwrap_or(std::cmp::Ordering::Equal)
                    .then(a.short_name.cmp(&b.short_name)),
                (Some(_), None) => std::cmp::Ordering::Less,
                (None, Some(_)) => std::cmp::Ordering::Greater,
                (None, None) => a.short_name.cmp(&b.short_name),
            }
        }),
        _ => {} // "name" or unknown — preserve SQL order
    }
}
```

Update `SpeedTemplate`:

```rust
#[derive(Template)]
#[template(path = "speed.html")]
struct SpeedTemplate {
    cards: Vec<RouteSpeedCard>,
    agencies: Vec<(String, String)>,
    active_agency: String,
    active_sort: String,
}
```

Update `speed_page`:

```rust
pub async fn speed_page(
    State(state): State<AppState>,
    Query(params): Query<SpeedParams>,
) -> Html<String> {
    let active_agency = params.agency.unwrap_or_default();
    let active_sort = params.sort.unwrap_or_default();
    let filter = if active_agency.is_empty() {
        None
    } else {
        Some(active_agency.as_str())
    };
    let agencies: Vec<(String, String)> = state
        .config
        .agencies
        .iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();
    let rows = route_speed_by_day_type(&state.db, filter)
        .await
        .unwrap_or_default();
    let mut cards = build_speed_cards(rows, &agency_names);
    sort_speed_cards(&mut cards, &active_sort);
    let tmpl = SpeedTemplate {
        cards,
        agencies,
        active_agency,
        active_sort,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cargo test --lib web::handlers::tests 2>&1 | tail -5
```

Expected: `test result: ok. 5 passed; 0 failed`

- [ ] **Step 5: Verify the project still builds**

```bash
cargo build 2>&1 | grep -E "^error"
```

Expected: no output (clean build).

- [ ] **Step 6: Commit**

```bash
git add src/web/handlers.rs
git commit -m "feat: add sort query param and sort_speed_cards to speed handler"
```

---

### Task 3: Update the template with a two-row controls bar

**Files:**
- Modify: `templates/speed.html`

There are no unit-testable logic changes in this task — the visual output is verified by loading the page in a browser.

- [ ] **Step 1: Replace CSS for the controls bar**

In `templates/speed.html`, replace the `.filter-bar` CSS block:

```css
.controls { margin-bottom: 1.5rem; display: flex; flex-direction: column; gap: 0.4rem; }
.control-row { display: flex; flex-wrap: wrap; align-items: center; gap: 0.4rem; }
.control-label { font-size: 0.78rem; color: #888; font-weight: 500;
                 min-width: 4.5rem; text-align: right; padding-right: 0.25rem; }
.control-row a { padding: 0.3rem 0.7rem; border-radius: 20px; background: white;
                 border: 1px solid #ddd; font-size: 0.82rem; text-decoration: none;
                 color: #555; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
.control-row a:hover { border-color: #999; color: #222; }
.control-row a.active { background: #1a1a2e; color: white; border-color: #1a1a2e; }
```

- [ ] **Step 2: Replace the filter-bar HTML with the two-row controls**

Replace the `<div class="filter-bar">…</div>` block:

```html
<div class="controls">
  <div class="control-row">
    <span class="control-label">Agency:</span>
    <a href="/speed{% if !active_sort.is_empty() %}?sort={{ active_sort }}{% endif %}"
       class="{% if active_agency.is_empty() %}active{% endif %}">All</a>
    {% for (slug, name) in &agencies %}
    <a href="/speed?agency={{ slug }}{% if !active_sort.is_empty() %}&amp;sort={{ active_sort }}{% endif %}"
       class="{% if active_agency == *slug %}active{% endif %}">{{ name }}</a>
    {% endfor %}
  </div>
  <div class="control-row">
    <span class="control-label">Sort by:</span>
    <a href="/speed{% if !active_agency.is_empty() %}?agency={{ active_agency }}{% endif %}"
       class="{% if active_sort.is_empty() %}active{% endif %}">Name</a>
    <a href="/speed?sort=scheduled{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}"
       class="{% if active_sort == "scheduled" %}active{% endif %}">Slowest scheduled</a>
    <a href="/speed?sort=actual{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}"
       class="{% if active_sort == "actual" %}active{% endif %}">Slowest actual</a>
  </div>
</div>
```

- [ ] **Step 3: Build to verify template compiles**

```bash
cargo build 2>&1 | grep -E "^error"
```

Expected: no output.

- [ ] **Step 4: Run full test suite to confirm nothing regressed**

```bash
cargo test --lib 2>&1 | tail -5
```

Expected: same pass count as before this task (the disk-full container failures are pre-existing and unrelated).

- [ ] **Step 5: Commit**

```bash
git add templates/speed.html
git commit -m "feat: replace filter bar with two-row agency+sort controls on speed page"
```

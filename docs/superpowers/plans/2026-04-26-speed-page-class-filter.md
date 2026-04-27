# Speed Page: Filter by Route Class — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Class filter row (All / Local / Rapid / Express) to the speed page, backed by a `?class=` URL param that retains cards matching the selected `RouteClass` and hides unclassified routes.

**Architecture:** A new `filter_speed_cards(cards, class)` function (parallel to the existing `sort_speed_cards`) is unit-tested in isolation. The `speed_page` handler validates the param, filters, then sorts. The template gains a third `control-row` and existing rows are updated to preserve `class` when switching agency or sort.

**Tech Stack:** Rust/Axum, Askama, sqlx/Postgres testcontainers for E2E tests.

---

## File Map

| File | Change |
|---|---|
| `src/web/handlers.rs` | Add `filter_speed_cards`; extend `SpeedParams` and `SpeedTemplate`; call filter in `speed_page`; add unit + E2E tests |
| `templates/speed.html` | Add Class `control-row`; update Agency and Sort rows to preserve `class` |

---

### Task 1: `filter_speed_cards` — unit tests then implementation

**Files:**
- Modify: `src/web/handlers.rs`

- [ ] **Step 1: Write three failing unit tests**

Add the following inside the existing `mod tests` block in `src/web/handlers.rs`, after the last `sort_*` test:

```rust
fn card_with_class(short_name: &str, class: Option<RouteClass>) -> RouteSpeedCard {
    RouteSpeedCard {
        idx: 0,
        agency_name: "A".into(),
        agency_id: "a".into(),
        route_id: "R1".into(),
        short_name: short_name.into(),
        long_name: short_name.into(),
        charts: vec![],
        avg_scheduled_speed_mps: None,
        avg_actual_speed_mps: None,
        classification: class,
    }
}

#[test]
fn filter_by_class_keeps_matching_cards() {
    let mut cards = vec![
        card_with_class("L1", Some(RouteClass::Local)),
        card_with_class("R1", Some(RouteClass::Rapid)),
        card_with_class("U1", None),
    ];
    filter_speed_cards(&mut cards, "rapid");
    assert_eq!(cards.len(), 1);
    assert_eq!(cards[0].short_name, "R1");
}

#[test]
fn filter_by_class_hides_unclassified() {
    let mut cards = vec![
        card_with_class("U1", None),
        card_with_class("L1", Some(RouteClass::Local)),
    ];
    filter_speed_cards(&mut cards, "local");
    assert_eq!(cards.len(), 1);
    assert_eq!(cards[0].short_name, "L1");
}

#[test]
fn filter_by_empty_class_keeps_all() {
    let mut cards = vec![
        card_with_class("L1", Some(RouteClass::Local)),
        card_with_class("U1", None),
    ];
    filter_speed_cards(&mut cards, "");
    assert_eq!(cards.len(), 2);
}
```

`RouteClass` is already imported via `use crate::speed::RouteClass;` in the file's top-level imports; verify it's present, add if missing.

- [ ] **Step 2: Run tests — verify they fail**

```bash
cargo test filter_by_class 2>&1 | grep -E "FAILED|error"
```

Expected: compile error — `filter_speed_cards` not found.

- [ ] **Step 3: Add `filter_speed_cards` function**

Add this function immediately after `sort_speed_cards` in `src/web/handlers.rs` (around line 394):

```rust
fn filter_speed_cards(cards: &mut Vec<RouteSpeedCard>, class: &str) {
    if class.is_empty() {
        return;
    }
    cards.retain(|c| {
        c.classification
            .map(|cls| cls.css_class() == class)
            .unwrap_or(false)
    });
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
cargo test filter_by_class 2>&1 | tail -5
```

Expected: `test result: ok. 3 passed; 0 failed`

- [ ] **Step 5: Commit**

```bash
git add src/web/handlers.rs
git commit -m "feat(speed): add filter_speed_cards function"
```

---

### Task 2: Wire `?class=` param in the handler — E2E test then implementation

**Files:**
- Modify: `src/web/handlers.rs`

- [ ] **Step 1: Write the failing E2E test**

Add this test inside `mod e2e_tests` in `src/web/handlers.rs`, after the last existing `#[tokio::test]`:

```rust
#[tokio::test]
async fn speed_page_filters_by_class_local() {
    let td = test_utils::setup().await;

    // Local route: two stops ~333 m apart (0.003° lat × 111 km/°) → avg < 500 m → Local
    sqlx::query("INSERT INTO routes VALUES ('0', 'RL', 'LocalX', 'Local Route', 3)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO trips VALUES ('0', 'TL', 'RL', 'WD', 0, 'End Local')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0', 'SL1', 'Local A', 45.500, -73.50)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0', 'SL2', 'Local B', 45.503, -73.50)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('0', 'TL', 'SL1', 1, '08:00:00', '08:00:00')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('0', 'TL', 'SL2', 2, '08:05:00', '08:05:00')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO route_speed VALUES ('0', 'RL', 0, 5.0, 1, '2026-01-01T00:00:00Z')")
        .execute(&td.db.pool).await.unwrap();

    // Rapid route: two stops ~1111 m apart (0.010° lat) → avg 500–1500 m → Rapid
    sqlx::query("INSERT INTO routes VALUES ('0', 'RR', 'RapidX', 'Rapid Route', 3)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO trips VALUES ('0', 'TR', 'RR', 'WD', 0, 'End Rapid')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0', 'SR1', 'Rapid A', 45.500, -73.50)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO stops VALUES ('0', 'SR2', 'Rapid B', 45.510, -73.50)")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('0', 'TR', 'SR1', 1, '08:00:00', '08:00:00')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO scheduled_stops VALUES ('0', 'TR', 'SR2', 2, '08:10:00', '08:10:00')")
        .execute(&td.db.pool).await.unwrap();
    sqlx::query("INSERT INTO route_speed VALUES ('0', 'RR', 0, 8.0, 1, '2026-01-01T00:00:00Z')")
        .execute(&td.db.pool).await.unwrap();

    let state = AppState { db: td.db, config: test_config() };
    let app = build_router(state);

    let response = app
        .oneshot(
            Request::builder()
                .uri("/speed?class=local")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);
    let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024).await.unwrap();
    let html = String::from_utf8(bytes.to_vec()).unwrap();

    assert!(
        html.contains("LocalX"),
        "Local route should appear when filtering by class=local"
    );
    assert!(
        !html.contains("RapidX"),
        "Rapid route should be hidden when filtering by class=local"
    );
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
cargo test speed_page_filters_by_class_local 2>&1 | tail -10
```

Expected: test fails — `RapidX` still appears in HTML (param not wired up).

- [ ] **Step 3: Extend `SpeedParams`**

In `src/web/handlers.rs`, update the `SpeedParams` struct:

```rust
#[derive(Deserialize)]
pub struct SpeedParams {
    agency: Option<String>,
    sort: Option<String>,
    class: Option<String>,
}
```

- [ ] **Step 4: Add `active_class` to `SpeedTemplate`**

Update the `SpeedTemplate` struct:

```rust
#[derive(Template)]
#[template(path = "speed.html")]
struct SpeedTemplate {
    region_name: String,
    cards: Vec<RouteSpeedCard>,
    agencies: Vec<(String, String)>,
    active_agency: String,
    active_sort: String,
    active_class: String,
}
```

- [ ] **Step 5: Validate param and wire filter in `speed_page`**

In the `speed_page` function, add validation after `active_sort` is set:

```rust
let active_class = match params.class.as_deref() {
    Some("local") => "local",
    Some("rapid") => "rapid",
    Some("express") => "express",
    _ => "",
}
.to_string();
```

Then add the filter call between `build_speed_cards` and `sort_speed_cards`:

```rust
let mut cards = build_speed_cards(rows, &agency_names);
filter_speed_cards(&mut cards, &active_class);   // ← add this line
sort_speed_cards(&mut cards, &active_sort);
```

Update the `SpeedTemplate` construction to include `active_class`:

```rust
let tmpl = SpeedTemplate {
    region_name: state.config.region.name.clone(),
    cards,
    agencies,
    active_agency,
    active_sort,
    active_class,        // ← add this field
};
```

- [ ] **Step 6: Run all tests — verify they pass**

```bash
cargo test 2>&1 | grep "test result"
```

Expected: all pass (the template won't compile until Task 3, so Askama will error — that's fine, fix by adding a placeholder `active_class` variable to the template temporarily, or proceed directly to Task 3).

> **Note:** Askama compiles templates at build time. The `SpeedTemplate` struct now has `active_class` but the template doesn't use it yet — this will produce an "unused variable" Askama warning, not an error. The template *will* fail to compile if `active_class` is referenced in the template but not in the struct (forward direction). Since we're adding the field before the template reference, the build should succeed.

- [ ] **Step 7: Commit**

```bash
git add src/web/handlers.rs
git commit -m "feat(speed): wire ?class= filter param in handler"
```

---

### Task 3: Add Class control-row to template

**Files:**
- Modify: `templates/speed.html`

- [ ] **Step 1: Replace the Agency and Sort control-rows and add the Class row**

Replace the two existing `control-row` divs (Agency and Sort) and append the new Class row. The updated block (starting from line 29) should read:

```html
  <div class="control-row">
    <span class="control-label">Agency:</span>
    <a href="/speed{% if active_sort != "name" %}?sort={{ active_sort }}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}{% elif !active_class.is_empty() %}?class={{ active_class }}{% endif %}"
       class="{% if active_agency.is_empty() %}active{% endif %}">All</a>
    {% for (slug, name) in &agencies %}
    <a href="/speed?agency={{ slug }}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       class="{% if active_agency == *slug %}active{% endif %}">{{ name }}</a>
    {% endfor %}
  </div>

  <div class="control-row">
    <span class="control-label">Sort by:</span>
    <a href="/speed{% if !active_agency.is_empty() %}?agency={{ active_agency }}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}{% elif !active_class.is_empty() %}?class={{ active_class }}{% endif %}"
       class="{% if active_sort == "name" %}active{% endif %}">Name</a>
    <a href="/speed?sort=scheduled{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       class="{% if active_sort == "scheduled" %}active{% endif %}">Slowest scheduled</a>
    <a href="/speed?sort=actual{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if !active_class.is_empty() %}&amp;class={{ active_class }}{% endif %}"
       class="{% if active_sort == "actual" %}active{% endif %}">Slowest actual</a>
  </div>

  <div class="control-row" style="margin-bottom:1.5rem;">
    <span class="control-label">Class:</span>
    <a href="/speed{% if !active_agency.is_empty() %}?agency={{ active_agency }}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}{% elif active_sort != "name" %}?sort={{ active_sort }}{% endif %}"
       class="{% if active_class.is_empty() %}active{% endif %}">All</a>
    <a href="/speed?class=local{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       class="{% if active_class == "local" %}active{% endif %}">Local</a>
    <a href="/speed?class=rapid{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       class="{% if active_class == "rapid" %}active{% endif %}">Rapid</a>
    <a href="/speed?class=express{% if !active_agency.is_empty() %}&amp;agency={{ active_agency }}{% endif %}{% if active_sort != "name" %}&amp;sort={{ active_sort }}{% endif %}"
       class="{% if active_class == "express" %}active{% endif %}">Express</a>
  </div>
```

Note: the existing Sort row had `style="margin-bottom:1.5rem;"` — move that style to the new Class row (the last filter row before the card grid), and remove it from the Sort row.

- [ ] **Step 2: Build to verify template compiles**

```bash
cargo build 2>&1 | grep -E "error|warning.*unused"
```

Expected: clean build, no errors.

- [ ] **Step 3: Run all tests**

```bash
cargo test 2>&1 | grep "test result"
```

Expected: `133 passed; 0 failed` (or more if you added tests above the baseline).

- [ ] **Step 4: Commit**

```bash
git add templates/speed.html
git commit -m "feat(speed): add Class filter control-row to speed page"
```

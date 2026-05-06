# Speed Page: Filter by Route Class

Date: 2026-04-26

## Summary

Add a "Class" filter row to the speed page so users can narrow the card grid to Local, Rapid, or Express routes. Unclassified routes (no stop spacing data) are hidden whenever a class filter is active.

## Approach

Server-side filtering via a new `?class=` URL query parameter, consistent with the existing `?agency=` and `?sort=` params. No JavaScript required. Filter state is preserved in the URL (shareable, bookmarkable, back-button safe).

## Backend

### `SpeedParams`

Add `class: Option<String>` field (serde deserializes from `?class=`):

```rust
#[derive(Deserialize)]
pub struct SpeedParams {
    agency: Option<String>,
    sort: Option<String>,
    class: Option<String>,
}
```

### Validation

Validate the raw string against the three known css_class values. Anything else (absent, empty, unknown) resolves to "All" (no filter):

```rust
let active_class = match params.class.as_deref() {
    Some("local") => "local",
    Some("rapid") => "rapid",
    Some("express") => "express",
    _ => "",
}.to_string();
```

### Filtering

Extract a `filter_speed_cards(cards: &mut Vec<RouteSpeedCard>, class: &str)` function parallel to `sort_speed_cards`, so it can be unit-tested in isolation. Applied in `speed_page` after `build_speed_cards()` and before `sort_speed_cards()`, when `active_class` is non-empty:

```rust
if !active_class.is_empty() {
    cards.retain(|c| {
        c.classification
            .map(|cls| cls.css_class() == active_class)
            .unwrap_or(false)
    });
}
```

Cards with `classification: None` always evaluate to `false` and are excluded.

### `SpeedTemplate`

Add `active_class: String` field, populated from the validated value above.

## Template (`templates/speed.html`)

Add a third `control-row` below the existing Sort row:

```html
<div class="control-row" style="margin-bottom:1.5rem;">
  <span class="control-label">Class:</span>
  <a href="/speed{% ... omit class param ... %}"
     class="{% if active_class.is_empty() %}active{% endif %}">All</a>
  <a href="/speed?class=local{% ... agency + sort ... %}"
     class="{% if active_class == "local" %}active{% endif %}">Local</a>
  <a href="/speed?class=rapid{% ... agency + sort ... %}"
     class="{% if active_class == "rapid" %}active{% endif %}">Rapid</a>
  <a href="/speed?class=express{% ... agency + sort ... %}"
     class="{% if active_class == "express" %}active{% endif %}">Express</a>
</div>
```

Each link preserves the currently active `agency` and `sort` params. Switching agency or sort also preserves `class`. "All" omits `?class=` entirely (same convention as the agency "All" link).

Link URL construction follows the same conditional-append pattern already used by the Agency and Sort rows.

## Testing

### Unit tests (in `src/web/handlers.rs`)

| Test | Assertion |
|---|---|
| `filter_by_class_keeps_matching_cards` | Vec with Local, Rapid, unclassified filtered to "rapid" → only Rapid card |
| `filter_by_class_hides_unclassified` | Unclassified card excluded when class filter is active |
| `filter_by_unknown_class_keeps_all` | Unknown/empty param value → all cards returned unchanged |

Uses the `filter_speed_cards` helper described in the Backend section.

### E2E test

`speed_page_filters_by_class_local`: inserts a Local route (two stops ~270 m apart, avg spacing < 500 m → Local) and a Rapid route (two stops ~1111 m apart → Rapid), requests `/speed?class=local`, asserts the Local route short name appears and the Rapid route short name does not.

## Out of Scope

- Combining class filter with sorting is not a special case; both operate independently on the same card vec.
- No UI changes to the speed card itself.
- No database schema changes.

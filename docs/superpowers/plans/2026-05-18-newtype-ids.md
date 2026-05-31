# Newtype IDs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every primitive ID type (`String`, `i64`) with dedicated newtype wrappers so the compiler enforces that a `RouteId` can never be passed where an `AgencyId` is expected.

**Architecture:** A single `ids.rs` module in `mobilispect-core` defines all ID newtypes. Each newtype derives `sqlx::Type` with `#[sqlx(transparent)]`, so sqlx encodes/decodes them identically to their inner type — no query changes needed. Public function signatures adopt `&AgencyId` / `&RouteId` etc.; internal sqlx bindings use `.as_str()` or `.as_i64()` for typed `query!` macros.

**Tech Stack:** Rust 2024 edition, sqlx 0.8 with `#[sqlx(transparent)]`, serde with `#[serde(transparent)]`, testcontainers for integration tests.

---

## File Structure

### New files
| File | Purpose |
|------|---------|
| `crates/core/src/ids.rs` | All ID newtype definitions with traits + unit tests |

### Modified files
| File | Changes |
|------|---------|
| `crates/core/src/lib.rs` | `pub mod ids; pub use ids::*;` |
| `crates/core/src/metrics/mod.rs` | `RouteSummary`, `ScorecardRoute`, `RouteTrend`, `StopHotspot` structs; function sigs; `compute_metrics_for_date` |
| `crates/core/src/frequency/mod.rs` | `RouteHeadwayRow` struct; `route_headways` signature |
| `crates/core/src/speed/mod.rs` | `RouteSpeedSummary`, `DirectionStopSpacings`, `StopSpacingEntry`, `DirectionSpeedTrend`, `SpeedTrendRow`, `VariantSpeedTrend`, `SpeedTrendVariantRow`, `RouteSpeedDayType` structs; all public and private functions |
| `crates/core/src/speed/card.rs` | `RouteSpeedCard` struct |
| `crates/core/src/speed/detail.rs` | `RouteSpeedDetailDirection` struct; `build_detail_directions`, `fetch_route_info`; inline tests |
| `crates/worker/src/gtfs/realtime.rs` | `poll_once`, `store_vehicle_positions`, `store_trip_updates` |
| `crates/worker/src/gtfs/static_feed.rs` | `load_if_needed`, all `load_*` private functions |
| `crates/server/src/web/handlers.rs` | `RouteSpeedDetailTemplate`; `route_speed_detail` handler |

---

## Task 1: Define ID Newtypes in `crates/core/src/ids.rs`

**Files:**
- Create: `crates/core/src/ids.rs`
- Modify: `crates/core/src/lib.rs`

- [ ] **Step 1: Write the failing test**

Create `crates/core/src/ids.rs` with tests that reference the types (which don't exist yet), causing compilation failure:

```rust
use std::{fmt, ops::Deref};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn agency_id_from_str_roundtrips() {
        let id = AgencyId::from("STM");
        assert_eq!(id.as_str(), "STM");
        assert_eq!(&*id, "STM");
        assert_eq!(id, "STM");
    }

    #[test]
    fn agency_id_from_u32_converts_to_string() {
        let id = AgencyId::from(42u32);
        assert_eq!(id.as_str(), "42");
        assert_eq!(id.to_string(), "42");
    }

    #[test]
    fn route_id_display() {
        let id = RouteId::from("R1".to_string());
        assert_eq!(id.to_string(), "R1");
        assert_eq!(id, "R1");
    }

    #[test]
    fn direction_id_wraps_i64() {
        let id = DirectionId::from(0i64);
        assert_eq!(id.as_i64(), 0);
        assert_eq!(id, 0i64);
    }

    #[test]
    fn direction_id_display() {
        let id = DirectionId(1);
        assert_eq!(id.to_string(), "1");
    }

    #[test]
    fn variant_id_from_str() {
        let id = VariantId::from("abc123");
        assert_eq!(&*id, "abc123");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cargo test -p mobilispect-core ids 2>&1 | head -20
```

Expected: compilation error — `AgencyId`, `RouteId`, `DirectionId`, `VariantId` not found.

- [ ] **Step 3: Implement the ID types**

Replace the entire `crates/core/src/ids.rs` content with:

```rust
use std::{fmt, ops::Deref};

macro_rules! string_id {
    ($name:ident) => {
        #[derive(
            Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Hash,
            sqlx::Type,
            serde::Serialize, serde::Deserialize,
        )]
        #[sqlx(transparent)]
        #[serde(transparent)]
        pub struct $name(pub String);

        impl $name {
            pub fn as_str(&self) -> &str {
                &self.0
            }
        }

        impl fmt::Display for $name {
            fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                write!(f, "{}", self.0)
            }
        }

        impl From<String> for $name {
            fn from(s: String) -> Self {
                Self(s)
            }
        }

        impl From<&str> for $name {
            fn from(s: &str) -> Self {
                Self(s.to_owned())
            }
        }

        impl AsRef<str> for $name {
            fn as_ref(&self) -> &str {
                &self.0
            }
        }

        impl Deref for $name {
            type Target = str;
            fn deref(&self) -> &str {
                &self.0
            }
        }

        impl PartialEq<str> for $name {
            fn eq(&self, other: &str) -> bool {
                self.0 == other
            }
        }

        impl PartialEq<&str> for $name {
            fn eq(&self, other: &&str) -> bool {
                self.0 == *other
            }
        }

        impl PartialEq<String> for $name {
            fn eq(&self, other: &String) -> bool {
                &self.0 == other
            }
        }
    };
}

string_id!(AgencyId);
string_id!(RouteId);
string_id!(TripId);
string_id!(StopId);
string_id!(VariantId);
string_id!(ServiceId);
string_id!(VehicleId);

impl From<u32> for AgencyId {
    fn from(n: u32) -> Self {
        Self(n.to_string())
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, sqlx::Type)]
#[sqlx(transparent)]
pub struct DirectionId(pub i64);

impl DirectionId {
    pub fn as_i64(self) -> i64 {
        self.0
    }
}

impl fmt::Display for DirectionId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl From<i64> for DirectionId {
    fn from(n: i64) -> Self {
        Self(n)
    }
}

impl PartialEq<i64> for DirectionId {
    fn eq(&self, other: &i64) -> bool {
        self.0 == *other
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn agency_id_from_str_roundtrips() {
        let id = AgencyId::from("STM");
        assert_eq!(id.as_str(), "STM");
        assert_eq!(&*id, "STM");
        assert_eq!(id, "STM");
    }

    #[test]
    fn agency_id_from_u32_converts_to_string() {
        let id = AgencyId::from(42u32);
        assert_eq!(id.as_str(), "42");
        assert_eq!(id.to_string(), "42");
    }

    #[test]
    fn route_id_display() {
        let id = RouteId::from("R1".to_string());
        assert_eq!(id.to_string(), "R1");
        assert_eq!(id, "R1");
    }

    #[test]
    fn direction_id_wraps_i64() {
        let id = DirectionId::from(0i64);
        assert_eq!(id.as_i64(), 0);
        assert_eq!(id, 0i64);
    }

    #[test]
    fn direction_id_display() {
        let id = DirectionId(1);
        assert_eq!(id.to_string(), "1");
    }

    #[test]
    fn variant_id_from_str() {
        let id = VariantId::from("abc123");
        assert_eq!(&*id, "abc123");
    }
}
```

- [ ] **Step 4: Export from `crates/core/src/lib.rs`**

Add at the top of `crates/core/src/lib.rs`:

```rust
pub mod ids;
pub use ids::{AgencyId, DirectionId, RouteId, ServiceId, StopId, TripId, VariantId, VehicleId};
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cargo test -p mobilispect-core ids 2>&1
```

Expected: all 6 tests pass, no warnings.

- [ ] **Step 6: Commit**

```bash
git add crates/core/src/ids.rs crates/core/src/lib.rs
git commit -m "feat(core): introduce ID newtype module with AgencyId, RouteId, DirectionId, etc."
```

---

## Task 2: Update `crates/core/src/metrics/mod.rs`

**Files:**
- Modify: `crates/core/src/metrics/mod.rs`

Structs to update:
- `RouteTrend.route_id: String` → `RouteId`
- `RouteSummary.agency_id: String` → `AgencyId`, `.route_id: String` → `RouteId`
- `StopHotspot.stop_id: String` → `StopId`
- `ScorecardRoute.agency_id: String` → `AgencyId`, `.route_id: String` → `RouteId`

Functions to update:
- `route_trend(db, agency_id: &str, route_id: &str, ...)` → `(db, agency_id: &AgencyId, route_id: &RouteId, ...)`
- `route_summary(db, days, agency_filter: Option<&str>)` → `agency_filter: Option<&AgencyId>`
- `scorecard_routes(db, days, agency_filter: Option<&str>)` → `agency_filter: Option<&AgencyId>`
- `compute_metrics_for_date`: `agency_id = agency.id.to_string()` → `AgencyId::from(agency.id)`

- [ ] **Step 1: Update struct fields**

In `crates/core/src/metrics/mod.rs`, add the import at the top (after existing `use` lines):

```rust
use crate::ids::{AgencyId, RouteId, StopId};
```

Change `RouteTrend` (around line 17):
```rust
pub struct RouteTrend {
    pub route_id: RouteId,   // was String
    pub short_name: String,
    pub long_name: String,
    pub days: Vec<DailyTrendPoint>,
}
```

Change `RouteSummary` (around line 266):
```rust
pub struct RouteSummary {
    pub agency_id: AgencyId,  // was String
    pub route_id: RouteId,    // was String
    // ... rest unchanged
```

Change `StopHotspot` (around line 315):
```rust
pub struct StopHotspot {
    pub stop_id: StopId,   // was String
    // ... rest unchanged
```

Change `ScorecardRoute` (around line 532):
```rust
pub struct ScorecardRoute {
    pub agency_id: AgencyId,  // was String
    pub route_id: RouteId,    // was String
    // ... rest unchanged
```

- [ ] **Step 2: Run cargo check to see compilation errors**

```bash
cargo check -p mobilispect-core 2>&1 | grep "^error" | head -30
```

Expected: errors about `route_id: route_id.to_string()` in `route_trend`, bindings in `route_summary`/`scorecard_routes`, and test assertions.

- [ ] **Step 3: Update `route_trend` function signature and body**

Change the function signature (around line 396):
```rust
pub async fn route_trend(
    db: &Database,
    agency_id: &AgencyId,
    route_id: &RouteId,
    days: i64,
) -> Result<Option<RouteTrend>> {
```

Change the two `.bind` calls inside (they currently use `&agency_id` and `route_id`):
```rust
    .bind(agency_id.as_str())
    .bind(route_id.as_str())
```
(appears twice — once in the route existence check, once in the daily points query)

Change the `RouteTrend` construction (around line 453):
```rust
    Ok(Some(RouteTrend {
        route_id: route_id.clone(),  // was route_id.to_string()
        short_name,
        long_name,
        days: trend_days,
    }))
```

- [ ] **Step 4: Update `route_summary` function signature and body**

Change signature (around line 463):
```rust
pub async fn route_summary(
    db: &Database,
    days: i64,
    agency_filter: Option<&AgencyId>,
) -> Result<Vec<RouteSummary>> {
```

Change the `.bind(agency)` call in the `Some(agency)` branch:
```rust
        Some(agency) => sqlx::query_as("...")
            .bind(days)
            .bind(agency.as_str())   // was .bind(agency)
            .fetch_all(&db.pool)
            .await?,
```

- [ ] **Step 5: Update `scorecard_routes` function signature and body**

Change signature (around line 631):
```rust
pub async fn scorecard_routes(
    db: &Database,
    days: i64,
    agency_filter: Option<&AgencyId>,
) -> Result<Vec<ScorecardRoute>> {
```

Change the `.bind(agency)` call in the `Some(agency)` branch (same pattern as `route_summary`):
```rust
            .bind(agency.as_str())   // was .bind(agency)
```

- [ ] **Step 6: Update `compute_metrics_for_date` internal agency_id binding**

Around line 114, change:
```rust
    let agency_id = AgencyId::from(agency.id);  // was agency.id.to_string()
```

All existing `.bind(&agency_id)` calls in this function use the untyped `sqlx::query_as`. With `AgencyId: sqlx::Encode`, `.bind(&agency_id)` works. No other changes needed inside this function.

- [ ] **Step 7: Update existing tests that use string literals for IDs**

In the `#[cfg(test)]` block (around line 723), update each call that passes string literals:

```rust
// Change from:
route_trend(&db, "0", "NONEXISTENT", 30).await.unwrap()

// Change to:
route_trend(&db, &AgencyId::from("0"), &RouteId::from("NONEXISTENT"), 30).await.unwrap()
```

```rust
// Change from:
route_trend(&db, "0", "R1", 3650).await.unwrap().unwrap()
assert_eq!(trend.route_id, "R1");

// Change to:
route_trend(&db, &AgencyId::from("0"), &RouteId::from("R1"), 3650).await.unwrap().unwrap()
assert_eq!(trend.route_id, "R1");  // works: PartialEq<&str> is implemented
```

Note: Assertions like `assert_eq!(result.agency_id, "0")` and `assert_eq!(summary.route_id, "R1")` continue to work unchanged because `PartialEq<&str>` is implemented on all string ID types.

- [ ] **Step 8: Run tests to verify everything passes**

```bash
cargo test -p mobilispect-core metrics 2>&1
```

Expected: all tests pass (check the test count matches before/after).

- [ ] **Step 9: Commit**

```bash
git add crates/core/src/metrics/mod.rs
git commit -m "feat(metrics): use AgencyId, RouteId, StopId newtypes in structs and function signatures"
```

---

## Task 3: Update `crates/core/src/frequency/mod.rs`

**Files:**
- Modify: `crates/core/src/frequency/mod.rs`

- [ ] **Step 1: Update struct and function**

Add import:
```rust
use crate::ids::{AgencyId, DirectionId, RouteId};
```

Change `RouteHeadwayRow` fields (around line 6):
```rust
pub struct RouteHeadwayRow {
    pub agency_id: AgencyId,    // was String
    pub route_id: RouteId,      // was String
    pub short_name: String,
    pub long_name: String,
    pub direction_id: DirectionId,  // was i64
    // ... rest unchanged
}
```

Find `route_headways` function signature (around line 74) and update `agency_filter`:
```rust
pub async fn route_headways(
    db: &Database,
    agency_filter: Option<&AgencyId>,  // was Option<&str>
) -> Result<Vec<RouteHeadwayRow>> {
```

Change the `.bind(agency)` in the `Some(agency)` arm:
```rust
            .bind(agency.as_str())   // was .bind(agency)
```

- [ ] **Step 2: Run cargo check to catch any remaining errors**

```bash
cargo check -p mobilispect-core 2>&1 | grep "^error"
```

Expected: no errors in `frequency/mod.rs`.

- [ ] **Step 3: Run tests**

```bash
cargo test -p mobilispect-core frequency 2>&1
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add crates/core/src/frequency/mod.rs
git commit -m "feat(frequency): use AgencyId, RouteId, DirectionId newtypes in RouteHeadwayRow"
```

---

## Task 4: Update `crates/core/src/speed/mod.rs`

**Files:**
- Modify: `crates/core/src/speed/mod.rs`

This is the largest change. Update structs and then all functions.

- [ ] **Step 1: Add imports and update public structs**

Add at the top of the `use` block:
```rust
use crate::ids::{AgencyId, DirectionId, RouteId, VariantId};
```

Update `direction_label` signature (around line 16):
```rust
pub(crate) fn direction_label(direction_id: DirectionId) -> &'static str {
    match direction_id.as_i64() {
        0 => "Outbound",
        1 => "Inbound",
        _ => "Unknown",
    }
}
```

Update `RouteSpeedSummary` (around line 24):
```rust
pub struct RouteSpeedSummary {
    pub agency_id: AgencyId,      // was String
    pub route_id: RouteId,        // was String
    pub short_name: String,
    pub long_name: String,
    pub direction_id: DirectionId,  // was i64
    // ... rest unchanged
```

Update `DirectionStopSpacings` (around line 133):
```rust
pub struct DirectionStopSpacings {
    pub direction_id: DirectionId,  // was i64
    pub variant_id: VariantId,      // was String
    // ... rest unchanged
```

Update private `StopSpacingEntry` (around line 149):
```rust
struct StopSpacingEntry {
    variant_id: VariantId,      // was String
    direction_id: DirectionId,  // was i64
    // ... rest unchanged
```

Update `DirectionSpeedTrend` (around line 268):
```rust
pub struct DirectionSpeedTrend {
    pub direction_id: DirectionId,  // was i64
    // ... rest unchanged
```

Update private `SpeedTrendRow` (around line 278):
```rust
struct SpeedTrendRow {
    direction_id: DirectionId,  // was i64
    // ... rest unchanged
```

Update `VariantSpeedTrend` (around line 336):
```rust
pub struct VariantSpeedTrend {
    pub variant_id: VariantId,  // was String
    // ... rest unchanged
```

Update private `SpeedTrendVariantRow` (around line 343):
```rust
struct SpeedTrendVariantRow {
    variant_id: VariantId,  // was String
    // ... rest unchanged
```

Update `RouteSpeedDayType` (around line 1126):
```rust
pub struct RouteSpeedDayType {
    pub agency_id: AgencyId,      // was String
    pub route_id: RouteId,        // was String
    pub short_name: String,
    pub long_name: String,
    pub direction_id: DirectionId,  // was i64
    // ... rest unchanged
```

- [ ] **Step 2: Run cargo check to identify remaining errors**

```bash
cargo check -p mobilispect-core 2>&1 | grep "^error" | head -40
```

Expected: errors in `build_direction_spacings`, `build_direction_trends`, `build_variant_trends`, and the public async functions.

- [ ] **Step 3: Fix `build_direction_spacings`**

The function around line 183 groups rows by `variant_id`. The key type changes from `String` to `VariantId`:

```rust
fn build_direction_spacings(rows: Vec<StopSpacingEntry>) -> Vec<DirectionStopSpacings> {
    let mut result: Vec<DirectionStopSpacings> = Vec::new();
    let mut i = 0;
    while i < rows.len() {
        let variant_id = rows[i].variant_id.clone();
        let end = rows[i..]
            .iter()
            .position(|r| r.variant_id != variant_id)
            .map(|p| i + p)
            .unwrap_or(rows.len());
        // ... rest of function

        result.push(DirectionStopSpacings {
            direction_id: rows[i].direction_id,   // DirectionId is Copy
            variant_id,
            // ... rest unchanged
        });
        i = end;
    }
    result
}
```

No structural changes needed — the logic works because `VariantId: PartialEq` and `DirectionId: Copy`.

- [ ] **Step 4: Fix `build_direction_trends`**

The HashMap key changes from `i64` to `DirectionId` (around line 286):

```rust
fn build_direction_trends(rows: Vec<SpeedTrendRow>) -> Vec<DirectionSpeedTrend> {
    use chrono::Datelike;
    use std::str::FromStr;

    let mut map: std::collections::HashMap<
        DirectionId,   // was i64
        (
            Vec<(String, f64, Option<f64>)>,
            Vec<(String, f64, Option<f64>)>,
            Vec<(String, f64, Option<f64>)>,
        ),
    > = std::collections::HashMap::new();

    for row in rows {
        let dow = chrono::NaiveDate::from_str(&row.service_date)
            .map(|d| d.weekday().num_days_from_sunday())
            .unwrap_or(1);
        let entry = map.entry(row.direction_id).or_default();
        // ... rest unchanged

    let mut result: Vec<DirectionSpeedTrend> = map
        .into_iter()
        .map(|(direction_id, (weekday, saturday, sunday))| DirectionSpeedTrend {
            direction_id,   // DirectionId
            weekday,
            saturday,
            sunday,
        })
        .collect();
    result.sort_by_key(|t| t.direction_id);   // DirectionId: Ord
    result
}
```

- [ ] **Step 5: Fix `build_variant_trends`**

The HashMap key changes from `String` to `VariantId` (around line 351):

```rust
fn build_variant_trends(rows: Vec<SpeedTrendVariantRow>) -> Vec<VariantSpeedTrend> {
    use chrono::Datelike;
    use std::str::FromStr;

    let mut map: std::collections::HashMap<
        VariantId,   // was String
        (
            Vec<(String, f64, Option<f64>)>,
            Vec<(String, f64, Option<f64>)>,
            Vec<(String, f64, Option<f64>)>,
        ),
    > = std::collections::HashMap::new();

    for row in rows {
        let dow = chrono::NaiveDate::from_str(&row.service_date)
            .map(|d| d.weekday().num_days_from_sunday())
            .unwrap_or(1);
        let entry = map.entry(row.variant_id).or_default();
        // ... rest of loop body unchanged

    let mut result: Vec<VariantSpeedTrend> = map
        .into_iter()
        .map(|(variant_id, (weekday, saturday, sunday))| VariantSpeedTrend {
            variant_id,
            weekday,
            saturday,
            sunday,
        })
        .collect();
    result.sort_by_key(|t| t.variant_id.clone());   // VariantId: Ord
    result
}
```

- [ ] **Step 6: Update public async function signatures**

Update `route_speed_trend_by_variant` (around line 396):
```rust
pub async fn route_speed_trend_by_variant(
    db: &Database,
    agency_id: &AgencyId,
    route_id: &RouteId,
    days: i64,
) -> Result<Vec<VariantSpeedTrend>> {
```
Change the bind calls inside:
```rust
    .bind(agency_id.as_str())
    .bind(route_id.as_str())
```

Update `route_stop_spacings` (around line 427):
```rust
pub async fn route_stop_spacings(
    db: &Database,
    agency_id: &AgencyId,
    route_id: &RouteId,
) -> Result<Vec<DirectionStopSpacings>> {
```
Change the bind calls inside:
```rust
    .bind(agency_id.as_str())
    .bind(route_id.as_str())
```

Update `route_speed_trend_by_direction` (around line 488):
```rust
pub async fn route_speed_trend_by_direction(
    db: &Database,
    agency_id: &AgencyId,
    route_id: &RouteId,
    days: i64,
) -> Result<Vec<DirectionSpeedTrend>> {
```
Change the bind calls:
```rust
    .bind(agency_id.as_str())
    .bind(route_id.as_str())
```

Update `route_speed_summary` (around line 908):
```rust
pub async fn route_speed_summary(
    db: &Database,
    agency_filter: Option<&AgencyId>,
) -> Result<Vec<RouteSpeedSummary>> {
```
Change `.bind(agency)` in the `Some(agency)` branch:
```rust
            .bind(agency.as_str())
```

Update `route_speed_by_day_type` (around line 1146):
```rust
pub async fn route_speed_by_day_type(
    db: &Database,
    agency_filter: Option<&AgencyId>,
) -> Result<Vec<RouteSpeedDayType>> {
```
Change `.bind(agency_filter)` in the `Some` branch (find and update accordingly):
```rust
            .bind(agency_filter.as_str())
```

- [ ] **Step 7: Fix any callers of `direction_label` inside the module**

Search for calls to `direction_label` inside speed/mod.rs:
```bash
grep -n "direction_label" /Users/alex/src/mobilispect/crates/core/src/speed/mod.rs
```

Each call site passes an `i64`. After the signature change to `DirectionId`, update each to pass `DirectionId::from(value)` where `value: i64`, or just pass the `direction_id: DirectionId` field directly.

- [ ] **Step 8: Run cargo check**

```bash
cargo check -p mobilispect-core 2>&1 | grep "^error"
```

Expected: no errors from speed/mod.rs.

- [ ] **Step 9: Run tests**

```bash
cargo test -p mobilispect-core speed 2>&1
```

Expected: all tests pass.

- [ ] **Step 10: Commit**

```bash
git add crates/core/src/speed/mod.rs
git commit -m "feat(speed): use AgencyId, RouteId, DirectionId, VariantId newtypes in speed module"
```

---

## Task 5: Update `crates/core/src/speed/card.rs`

**Files:**
- Modify: `crates/core/src/speed/card.rs`

- [ ] **Step 1: Update `RouteSpeedCard` struct**

Add imports at top of `card.rs`:
```rust
use crate::ids::{AgencyId, RouteId};
```

Change `RouteSpeedCard` (around line 4):
```rust
pub struct RouteSpeedCard {
    pub idx: usize,
    pub agency_name: String,
    pub agency_id: AgencyId,  // was String
    pub route_id: RouteId,    // was String
    pub short_name: String,
    pub long_name: String,
    // ... rest unchanged
}
```

- [ ] **Step 2: Find and fix construction sites in `build_speed_cards`**

Search in card.rs for where `RouteSpeedCard` is constructed:
```bash
grep -n "RouteSpeedCard {" /Users/alex/src/mobilispect/crates/core/src/speed/card.rs
```

The struct fields `agency_id` and `route_id` come from `RouteSpeedDayType` (which now uses `AgencyId`/`RouteId` after Task 4), so the construction may just work with `.clone()`.

If any field is still `String` at a construction site, convert it: `agency_id: row.agency_id.clone()` where `row.agency_id: AgencyId` just passes through.

- [ ] **Step 3: Run cargo check and fix any remaining errors**

```bash
cargo check -p mobilispect-core 2>&1 | grep "^error"
```

Fix any type mismatch errors by cloning from the already-typed `RouteSpeedDayType` fields.

- [ ] **Step 4: Run tests**

```bash
cargo test -p mobilispect-core 2>&1 | tail -5
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add crates/core/src/speed/card.rs
git commit -m "feat(speed): use AgencyId, RouteId newtypes in RouteSpeedCard"
```

---

## Task 6: Update `crates/core/src/speed/detail.rs`

**Files:**
- Modify: `crates/core/src/speed/detail.rs`

- [ ] **Step 1: Update `RouteSpeedDetailDirection` struct**

Add imports:
```rust
use crate::ids::VariantId;
```

Change the struct (around line 3):
```rust
pub struct RouteSpeedDetailDirection {
    pub variant_id: VariantId,  // was String
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
```

- [ ] **Step 2: Update `build_detail_directions` function**

The function around line 70 constructs `RouteSpeedDetailDirection`. With `DirectionStopSpacings.variant_id: VariantId` (from Task 4), the construction should work directly:

```rust
RouteSpeedDetailDirection {
    variant_id: spacing.variant_id,  // VariantId moved out
    // ... rest unchanged
}
```

The `trend` lookup:
```rust
let trend = trends.iter().find(|t| t.variant_id == spacing.variant_id);
```
This works because `VariantId: PartialEq`.

- [ ] **Step 3: Update `fetch_route_info` function**

Change signature (around line 107):
```rust
pub async fn fetch_route_info(
    db: &crate::db::Database,
    agency_id: &AgencyId,  // was &str
    route_id: &RouteId,    // was &str
) -> anyhow::Result<Option<(String, String)>> {
```

Add import at top of detail.rs:
```rust
use crate::ids::{AgencyId, RouteId, VariantId};
```

Change the `query_as!` binding (the typed macro):
```rust
    let row = sqlx::query_as!(
        RouteInfoRow,
        "SELECT short_name, long_name FROM routes WHERE agency_id = $1 AND route_id = $2",
        agency_id.as_str(),   // was agency_id
        route_id.as_str(),    // was route_id
    )
```

- [ ] **Step 4: Update inline tests**

In the `#[cfg(test)]` block (around line 123), update `make_spacing`:

```rust
fn make_spacing(variant_id: &str) -> DirectionStopSpacings {
    DirectionStopSpacings {
        direction_id: DirectionId(0),        // was 0
        variant_id: VariantId::from(variant_id),  // was variant_id.to_string()
        is_primary: true,
        trip_count: 10,
        direction_name: "A \u{2192} B".to_string(),
        first_stop_name: "A".to_string(),
        avg_spacing_m: 500.0,
        spacings: vec![],
    }
}
```

Update `make_trend`:
```rust
fn make_trend(variant_id: &str) -> VariantSpeedTrend {
    VariantSpeedTrend {
        variant_id: VariantId::from(variant_id),  // was variant_id.to_string()
        weekday: vec![("2026-01-06".to_string(), 8.0, Some(9.0))],
        saturday: vec![],
        sunday: vec![],
    }
}
```

Update the `RouteSpeedDetailDirection` direct struct constructions in other tests (the ones that use `variant_id: String::new()`):
```rust
variant_id: VariantId::from(""),  // was String::new()
```

The `assert_eq!(dirs[0].variant_id, "V1")` assertions continue to work because `VariantId: PartialEq<&str>`.

- [ ] **Step 5: Run tests**

```bash
cargo test -p mobilispect-core speed::detail 2>&1
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add crates/core/src/speed/detail.rs
git commit -m "feat(speed): use VariantId, AgencyId, RouteId newtypes in detail module"
```

---

## Task 7: Update `crates/worker/src/gtfs/realtime.rs`

**Files:**
- Modify: `crates/worker/src/gtfs/realtime.rs`

- [ ] **Step 1: Add import and update `poll_once`**

Add import at top:
```rust
use mobilispect_core::ids::AgencyId;
```

In `poll_once` (around line 27), change:
```rust
    let agency_id = AgencyId::from(agency.id);  // was agency.id.to_string()
```

Update `store_vehicle_positions` and `store_trip_updates` calls to pass `&agency_id` (type `&AgencyId`):
```rust
    store_vehicle_positions(db, &feed_vp, &now, &agency_id).await?;
    store_trip_updates(db, &feed_tu, &now, &agency_id).await?;
```

- [ ] **Step 2: Update `store_vehicle_positions` signature and body**

Change signature (around line 68):
```rust
async fn store_vehicle_positions(
    db: &Database,
    feed: &proto::FeedMessage,
    observed_at: &str,
    agency_id: &AgencyId,   // was &str
) -> Result<usize> {
```

Change the sqlx query binding (around line 88):
```rust
        sqlx::query!(
            "INSERT INTO vehicle_positions ...",
            agency_id.as_str(),   // was agency_id
            observed_at,
            // ... rest unchanged
        )
```

- [ ] **Step 3: Update `store_trip_updates` signature and body**

Change signature (around line 110):
```rust
async fn store_trip_updates(
    db: &Database,
    feed: &proto::FeedMessage,
    observed_at: &str,
    agency_id: &AgencyId,   // was &str
) -> Result<usize> {
```

Change the sqlx query bindings inside to use `agency_id.as_str()`:
```rust
            agency_id.as_str(),   // was agency_id
```

- [ ] **Step 4: Run cargo check on worker**

```bash
cargo check -p mobilispect-worker 2>&1 | grep "^error"
```

Expected: no errors.

- [ ] **Step 5: Run worker tests**

```bash
cargo test -p mobilispect-worker 2>&1
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add crates/worker/src/gtfs/realtime.rs
git commit -m "feat(worker): use AgencyId newtype in realtime GTFS ingestor"
```

---

## Task 8: Update `crates/worker/src/gtfs/static_feed.rs`

**Files:**
- Modify: `crates/worker/src/gtfs/static_feed.rs`

- [ ] **Step 1: Add import and update `load_if_needed`**

Add import at top:
```rust
use mobilispect_core::ids::AgencyId;
```

In `load_if_needed` (around line 18), change:
```rust
    let agency_id = AgencyId::from(agency.id);  // was agency.id.to_string()
```

Update the calls to `get_stored_version`, `get_last_download`, and `load_*` functions:
```rust
    let stored_version = get_stored_version(db, &agency_id).await?;
    let last_download = get_last_download(db, &agency_id).await?;
    // ...
    sqlx::query("DELETE FROM route_variant_stops WHERE agency_id = $1")
        .bind(agency_id.as_str())
        .execute(tx.as_mut())
        .await?;
    // ... (all DELETE queries use agency_id.as_str())
    load_routes(&mut tx, &agency_id, &gtfs).await?;
    load_trips(&mut tx, &agency_id, &gtfs).await?;
    load_stops(&mut tx, &agency_id, &gtfs).await?;
    load_scheduled_stops(&mut tx, &agency_id, &gtfs).await?;
    load_variants(&mut tx, &agency_id, &gtfs).await?;
```

- [ ] **Step 2: Update `get_stored_version` and `get_last_download` signatures**

```bash
grep -n "async fn get_stored_version\|async fn get_last_download" /Users/alex/src/mobilispect/crates/worker/src/gtfs/static_feed.rs
```

Change each signature from `agency_id: &str` to `agency_id: &AgencyId` and update interior `.bind(agency_id)` to `.bind(agency_id.as_str())`.

- [ ] **Step 3: Update all `load_*` function signatures**

For each of `load_routes`, `load_trips`, `load_stops`, `load_scheduled_stops`, `load_variants` (private functions around lines 146, 180, 222, 256, 851):

Change signature:
```rust
async fn load_routes(tx: &mut Tx<'_>, agency_id: &AgencyId, gtfs: &Gtfs) -> Result<()> {
```

Inside each function, find where `agency_id` is used in tuple construction or `.bind()`:

For tuple-based bulk inserts like:
```rust
let rows: Vec<(String, String, ...)> = gtfs.routes.iter().map(|(id, r)| {
    (
        agency_id.to_string(),   // change to agency_id.as_str().to_owned() or agency_id.0.clone()
        id.clone(),
        ...
    )
}).collect();
```

Change `agency_id.to_string()` to `agency_id.0.clone()` (direct field access since the field is `pub`).

- [ ] **Step 4: Update `variant_id_for` function if it takes agency_id**

Check the function:
```bash
grep -n "fn variant_id_for\|fn ingest_variants" /Users/alex/src/mobilispect/crates/worker/src/gtfs/static_feed.rs
```

The `variant_id_for` function (around line 842) takes `&[String]` for stop_ids and returns `String`. These stop_ids are GTFS IDs (not our typed StopId since they come from the gtfs-structures library). Leave this function returning `String` (which becomes the `variant_id` column value) and convert to `VariantId` only if needed. The worker's concern is writing to DB, not returning our domain types.

- [ ] **Step 5: Run cargo check on worker**

```bash
cargo check -p mobilispect-worker 2>&1 | grep "^error"
```

Expected: no errors.

- [ ] **Step 6: Run worker tests**

```bash
cargo test -p mobilispect-worker 2>&1
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add crates/worker/src/gtfs/static_feed.rs
git commit -m "feat(worker): use AgencyId newtype in static GTFS feed ingestor"
```

---

## Task 9: Update `crates/server/src/web/handlers.rs`

**Files:**
- Modify: `crates/server/src/web/handlers.rs`

Note: URL path parameters arrive as `String` from axum. We convert at the handler boundary.

- [ ] **Step 1: Add imports**

Add to the existing `use mobilispect_core::...` block:
```rust
use mobilispect_core::ids::{AgencyId, RouteId};
```

- [ ] **Step 2: Update `RouteSpeedDetailTemplate` struct**

Change (around line 27):
```rust
struct RouteSpeedDetailTemplate {
    region_name: String,
    short_name: String,
    long_name: String,
    agency_id: AgencyId,  // was String
    directions: Vec<RouteSpeedDetailDirection>,
    classification: Option<RouteClass>,
}
```

- [ ] **Step 3: Update `route_speed_detail` handler**

The handler extracts `(agency_id, route_id): (String, String)` from the path. Convert immediately:

```rust
pub async fn route_speed_detail(
    State(state): State<AppState>,
    axum::extract::Path((agency_id, route_id)): axum::extract::Path<(String, String)>,
) -> axum::response::Response {
    use axum::response::IntoResponse;

    let agency_id = AgencyId::from(agency_id);
    let route_id = RouteId::from(route_id);

    let (short_name, long_name) = match fetch_route_info(&state.db, &agency_id, &route_id).await {
        // ... rest unchanged
```

Update `RouteSpeedDetailTemplate` construction to use `agency_id: agency_id.clone()` or just `agency_id` (moved):
```rust
    let tmpl = RouteSpeedDetailTemplate {
        region_name: ...,
        short_name,
        long_name,
        agency_id,  // moved AgencyId
        directions,
        classification,
    };
```

- [ ] **Step 4: Update all other handlers that accept `agency_filter: Option<&str>`**

Search for handler calls that pass `agency_filter`:
```bash
grep -n "agency_filter\|route_summary\|scorecard_routes\|route_speed_summary\|route_headways\|route_speed_by_day_type" /Users/alex/src/mobilispect/crates/server/src/web/handlers.rs | head -30
```

For each handler that calls a core function with `agency_filter: Option<&str>`, convert:

```rust
// Pattern: query params arrive as Option<String>
let agency_filter: Option<AgencyId> = params.agency.map(AgencyId::from);
let rows = route_summary(&state.db, days, agency_filter.as_ref()).await?;
```

The `.as_ref()` converts `Option<AgencyId>` → `Option<&AgencyId>` to match the function signature.

- [ ] **Step 5: Check if any Askama templates reference `agency_id` directly**

```bash
grep -rn "agency_id" /Users/alex/src/mobilispect/crates/server/templates/
```

If templates use `{{ agency_id }}`, they need `AgencyId` to implement `Display`. It does (via the `string_id!` macro). No template changes needed.

- [ ] **Step 6: Run cargo check on server**

```bash
cargo check -p mobilispect-server 2>&1 | grep "^error"
```

Expected: no errors.

- [ ] **Step 7: Run server tests**

```bash
cargo test -p mobilispect-server 2>&1
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add crates/server/src/web/handlers.rs
git commit -m "feat(server): use AgencyId, RouteId newtypes in web handlers"
```

---

## Task 10: Full Build Verification and sqlx Cache Check

**Files:**
- No new files

- [ ] **Step 1: Run full workspace build**

```bash
cargo build --workspace 2>&1 | grep "^error" | head -20
```

Expected: clean build, no errors.

- [ ] **Step 2: Run full test suite**

```bash
cargo test --workspace 2>&1 | tail -20
```

Expected: all tests pass. Note the test count to verify no tests were accidentally removed.

- [ ] **Step 3: Run clippy**

```bash
cargo clippy --workspace 2>&1 | grep "^error\|^warning" | head -20
```

Fix any warnings about unused imports or dead code that surfaced from the refactoring.

- [ ] **Step 4: Check if sqlx offline cache needs regeneration**

The `.sqlx/` cache records Postgres-level types, not Rust types. Since we pass `.as_str()` (still `&str` → TEXT) in all typed `query!` macros, the cache should remain valid. Verify:

```bash
SQLX_OFFLINE=true cargo check --workspace 2>&1 | grep "^error"
```

Expected: no errors. If errors appear about type mismatches in query macros, regenerate the cache:

```bash
# Only if the check above fails:
dotenvx run -- cargo sqlx prepare --workspace
```

- [ ] **Step 5: Final commit if any fixes were needed**

```bash
git add -p  # stage only changed files
git commit -m "chore: fix clippy warnings after newtype ID refactor"
```

---

## Self-Review

### Spec coverage
- [x] `AgencyId` — defined, used in all modules
- [x] `RouteId` — defined, used in metrics, speed, server
- [x] `TripId` — defined in ids.rs (worker uses TripId in DB but not in public Rust structs — this is acceptable; the DB binding uses `.as_str()` pattern from AgencyId)
- [x] `StopId` — defined, used in `StopHotspot`
- [x] `DirectionId` — defined, used in speed and frequency
- [x] `VariantId` — defined, used in speed module
- [x] `ServiceId`, `VehicleId` — defined in ids.rs for completeness; not yet used in public structs (their DB values come from protobuf and are passed directly as `&str` in sqlx)
- [x] Existing tests updated to use new types
- [x] `PartialEq<&str>` on all string IDs — test assertions `assert_eq!(id, "literal")` continue to work

### Placeholder scan
None detected — all steps show complete code.

### Type consistency
- `AgencyId` used in metrics, speed, frequency, worker, server — consistent
- `DirectionId(i64)` used wherever direction_id was i64 — consistent
- HashMap keys updated from `i64`/`String` to `DirectionId`/`VariantId` — consistent with struct field types
- Bind calls use `.as_str()` / `.as_i64()` for typed `query!` macros — consistent
- Untyped `sqlx::query_as` with `#[derive(sqlx::FromRow)]` structs decode directly into newtypes via transparent derive — consistent

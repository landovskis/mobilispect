# Per-Day-Type Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace combined top-decile/max/service-span stats in `RouteHeadwayRow` with per-day-type equivalents (weekday, saturday, sunday), and update the schedule card template to display per-day columns that collapse to only show active day types.

**Architecture:** Struct change in `frequency/mod.rs` and template change in `frequency_content.html` are atomic — Askama compiles templates at build time against the struct's public methods, so removing old methods and updating the template must happen in the same commit. SQL rewrite follows as a separate commit. Each commit leaves the full workspace compilable.

**Tech Stack:** Rust, sqlx (runtime-checked `query_as`), Askama templates, PostgreSQL window functions + `PERCENTILE_CONT`.

---

## File Map

| File | Change |
|---|---|
| `crates/core/src/frequency/mod.rs` | Struct fields, SQL query, display methods, unit tests |
| `crates/server/templates/frequency_content.html` | Card template (replace stats grid + badges with day columns) |
| `crates/server/templates/frequency.html` | CSS (remove old classes, add day-col classes) |
| `crates/server/src/web/handlers.rs` | E2E tests only — no handler code changes |

---

## Task 1: Update struct, display methods, unit tests, template, and CSS (atomic)

Askama compiles the template against the Rust struct at build time. Removing display methods and updating the template must be one commit — any split would leave the workspace uncompilable.

**Files:**
- Modify: `crates/core/src/frequency/mod.rs`
- Modify: `crates/server/templates/frequency_content.html`
- Modify: `crates/server/templates/frequency.html`

- [ ] **Step 1: Replace the `RouteHeadwayRow` struct definition**

Replace the entire struct (lines 7–21 in `crates/core/src/frequency/mod.rs`) with:

```rust
#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct RouteHeadwayRow {
    pub agency_id: AgencyId,
    pub route_id: RouteId,
    pub short_name: String,
    pub long_name: String,
    pub weekday_headway_mins: Option<f64>,
    pub saturday_headway_mins: Option<f64>,
    pub sunday_headway_mins: Option<f64>,
    pub weekday_top_decile_mins: Option<f64>,
    pub weekday_max_headway_mins: Option<f64>,
    pub weekday_service_start_secs: Option<i64>,
    pub weekday_service_end_secs: Option<i64>,
    pub saturday_top_decile_mins: Option<f64>,
    pub saturday_max_headway_mins: Option<f64>,
    pub saturday_service_start_secs: Option<i64>,
    pub saturday_service_end_secs: Option<i64>,
    pub sunday_top_decile_mins: Option<f64>,
    pub sunday_max_headway_mins: Option<f64>,
    pub sunday_service_start_secs: Option<i64>,
    pub sunday_service_end_secs: Option<i64>,
}
```

- [ ] **Step 2: Replace the entire `impl RouteHeadwayRow` block**

Replace everything from `impl RouteHeadwayRow {` through its closing `}` (lines 23–103) with:

```rust
impl RouteHeadwayRow {
    pub fn headway_display(mins: Option<f64>) -> String {
        match mins {
            None => "—".to_string(),
            Some(m) => format!("{:.1} min", m),
        }
    }

    pub fn weekday_display(&self) -> String {
        Self::headway_display(self.weekday_headway_mins)
    }

    pub fn saturday_display(&self) -> String {
        Self::headway_display(self.saturday_headway_mins)
    }

    pub fn sunday_display(&self) -> String {
        Self::headway_display(self.sunday_headway_mins)
    }

    pub fn weekday_top_decile_display(&self) -> String {
        Self::headway_display(self.weekday_top_decile_mins)
    }

    pub fn weekday_max_headway_display(&self) -> String {
        Self::headway_display(self.weekday_max_headway_mins)
    }

    pub fn saturday_top_decile_display(&self) -> String {
        Self::headway_display(self.saturday_top_decile_mins)
    }

    pub fn saturday_max_headway_display(&self) -> String {
        Self::headway_display(self.saturday_max_headway_mins)
    }

    pub fn sunday_top_decile_display(&self) -> String {
        Self::headway_display(self.sunday_top_decile_mins)
    }

    pub fn sunday_max_headway_display(&self) -> String {
        Self::headway_display(self.sunday_max_headway_mins)
    }

    pub fn weekday_service_span_display(&self) -> String {
        Self::service_span(self.weekday_service_start_secs, self.weekday_service_end_secs)
    }

    pub fn saturday_service_span_display(&self) -> String {
        Self::service_span(self.saturday_service_start_secs, self.saturday_service_end_secs)
    }

    pub fn sunday_service_span_display(&self) -> String {
        Self::service_span(self.sunday_service_start_secs, self.sunday_service_end_secs)
    }

    pub fn service_span(start: Option<i64>, end: Option<i64>) -> String {
        match (start, end) {
            (Some(s), Some(e)) => {
                format!("{}-{}", Self::time_display(s), Self::time_display(e))
            }
            _ => "—".to_string(),
        }
    }

    fn time_display(secs: i64) -> String {
        let hours = secs.div_euclid(3600);
        let minutes = secs.rem_euclid(3600).div_euclid(60);
        format!("{hours:02}:{minutes:02}")
    }

    pub fn headway_badge_variant(mins: Option<f64>) -> &'static str {
        match mins {
            None => "neutral",
            Some(m) if m < 10.0 => "good",
            Some(m) if m < 20.0 => "mixed",
            Some(_) => "bad",
        }
    }

    pub fn weekday_badge_variant(&self) -> &'static str {
        Self::headway_badge_variant(self.weekday_headway_mins)
    }

    pub fn saturday_badge_variant(&self) -> &'static str {
        Self::headway_badge_variant(self.saturday_headway_mins)
    }

    pub fn sunday_badge_variant(&self) -> &'static str {
        Self::headway_badge_variant(self.sunday_headway_mins)
    }

    pub fn primary_headway_min(&self) -> Option<f64> {
        self.weekday_headway_mins
            .or(self.saturday_headway_mins)
            .or(self.sunday_headway_mins)
    }
}
```

- [ ] **Step 3: Replace the entire `mod tests` block in `frequency/mod.rs`**

Replace everything from `#[cfg(test)]` through the final `}` (lines 283–385) with:

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn headway_display_none() {
        assert_eq!(RouteHeadwayRow::headway_display(None), "—");
    }

    #[test]
    fn headway_display_under_10() {
        assert_eq!(RouteHeadwayRow::headway_display(Some(7.5)), "7.5 min");
    }

    #[test]
    fn headway_display_10_or_more() {
        assert_eq!(RouteHeadwayRow::headway_display(Some(15.0)), "15.0 min");
    }

    #[test]
    fn headway_badge_variant_good() {
        assert_eq!(RouteHeadwayRow::headway_badge_variant(Some(0.0)), "good");
        assert_eq!(RouteHeadwayRow::headway_badge_variant(Some(9.9)), "good");
    }

    #[test]
    fn headway_badge_variant_mixed() {
        assert_eq!(RouteHeadwayRow::headway_badge_variant(Some(10.0)), "mixed");
        assert_eq!(RouteHeadwayRow::headway_badge_variant(Some(19.9)), "mixed");
    }

    #[test]
    fn headway_badge_variant_bad() {
        assert_eq!(RouteHeadwayRow::headway_badge_variant(Some(20.0)), "bad");
        assert_eq!(RouteHeadwayRow::headway_badge_variant(Some(30.0)), "bad");
    }

    #[test]
    fn headway_badge_variant_neutral() {
        assert_eq!(RouteHeadwayRow::headway_badge_variant(None), "neutral");
    }

    fn make_row(wd: Option<f64>, sat: Option<f64>, sun: Option<f64>) -> RouteHeadwayRow {
        RouteHeadwayRow {
            agency_id: AgencyId::from("a"),
            route_id: RouteId::from("r"),
            short_name: "1".to_string(),
            long_name: "Route 1".to_string(),
            weekday_headway_mins: wd,
            saturday_headway_mins: sat,
            sunday_headway_mins: sun,
            weekday_top_decile_mins: wd.map(|_| 5.0),
            weekday_max_headway_mins: wd.map(|_| 30.0),
            weekday_service_start_secs: wd.map(|_| 6 * 3600),
            weekday_service_end_secs: wd.map(|_| 23 * 3600 + 30 * 60),
            saturday_top_decile_mins: sat.map(|_| 10.0),
            saturday_max_headway_mins: sat.map(|_| 40.0),
            saturday_service_start_secs: sat.map(|_| 8 * 3600),
            saturday_service_end_secs: sat.map(|_| 22 * 3600),
            sunday_top_decile_mins: sun.map(|_| 15.0),
            sunday_max_headway_mins: sun.map(|_| 50.0),
            sunday_service_start_secs: sun.map(|_| 9 * 3600),
            sunday_service_end_secs: sun.map(|_| 21 * 3600),
        }
    }

    #[test]
    fn primary_headway_min_prefers_weekday() {
        let row = make_row(Some(8.0), Some(15.0), Some(20.0));
        assert_eq!(row.primary_headway_min(), Some(8.0));
    }

    #[test]
    fn primary_headway_min_falls_back_to_saturday() {
        let row = make_row(None, Some(15.0), Some(20.0));
        assert_eq!(row.primary_headway_min(), Some(15.0));
    }

    #[test]
    fn primary_headway_min_falls_back_to_sunday() {
        let row = make_row(None, None, Some(20.0));
        assert_eq!(row.primary_headway_min(), Some(20.0));
    }

    #[test]
    fn primary_headway_min_all_none() {
        let row = make_row(None, None, None);
        assert_eq!(row.primary_headway_min(), None);
    }

    #[test]
    fn service_span_none_none_returns_dash() {
        assert_eq!(RouteHeadwayRow::service_span(None, None), "—");
    }

    #[test]
    fn weekday_service_span_display_formats_correctly() {
        let row = make_row(Some(8.0), None, None);
        assert_eq!(row.weekday_service_span_display(), "06:00-23:30");
    }

    #[test]
    fn weekday_service_span_display_wraps_after_midnight() {
        let mut row = make_row(Some(8.0), None, None);
        row.weekday_service_end_secs = Some(25 * 3600 + 15 * 60);
        assert_eq!(row.weekday_service_span_display(), "06:00-25:15");
    }

    #[test]
    fn saturday_service_span_display_formats_correctly() {
        let row = make_row(None, Some(15.0), None);
        assert_eq!(row.saturday_service_span_display(), "08:00-22:00");
    }

    #[test]
    fn sunday_service_span_display_formats_correctly() {
        let row = make_row(None, None, Some(20.0));
        assert_eq!(row.sunday_service_span_display(), "09:00-21:00");
    }

    #[test]
    fn weekday_top_decile_and_max_display() {
        let row = make_row(Some(8.0), None, None);
        assert_eq!(row.weekday_top_decile_display(), "5.0 min");
        assert_eq!(row.weekday_max_headway_display(), "30.0 min");
    }
}
```

- [ ] **Step 4: Replace `frequency_content.html` entirely**

Write the full new content to `crates/server/templates/frequency_content.html`:

```html
<!-- HTMX target: id="freq-content" must match hx-target on every control link -->
<div id="freq-content" hx-indicator="#freq-loading">
  <div class="control-row" style="margin-bottom:1.5rem;">
    <span class="control-label">Agency:</span>
    <a href="/schedule"
       hx-get="/schedule"
       hx-target="#freq-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_agency.is_empty() %}active{% endif %}">All</a>
    {% for (slug, name) in &agencies %}
    <a href="/schedule?agency={{ slug }}"
       hx-get="/schedule?agency={{ slug }}"
       hx-target="#freq-content"
       hx-swap="outerHTML"
       hx-push-url="true"
       class="{% if active_agency == *slug %}active{% endif %}">{{ name }}</a>
    {% endfor %}
  </div>

  {% if rows.is_empty() %}
  <div class="empty-state">No schedule data available. Routes appear once GTFS schedule data has been ingested.</div>
  {% else %}
  <div class="schedule-grid">
    {% for (i, row) in rows.iter().enumerate() %}
    <div class="card schedule-card">
      <div class="schedule-card__header">
        <div>
          <div class="route-id">{{ i + 1 }}. {{ row.short_name }}</div>
          <div class="schedule-card__name">{{ row.long_name }}</div>
        </div>
      </div>
      <div class="schedule-card__days">
        {% if row.weekday_headway_mins.is_some() %}
        <div class="day-col">
          <div class="day-col__hdr">Weekday</div>
          <div class="spacing-stat-num spacing-{{ row.weekday_badge_variant() }}">{{ row.weekday_display() }}</div>
          <div class="day-col__stat"><span class="day-col__lbl">Top 10%</span><span>{{ row.weekday_top_decile_display() }}</span></div>
          <div class="day-col__stat"><span class="day-col__lbl">Max</span><span>{{ row.weekday_max_headway_display() }}</span></div>
          <div class="day-col__stat"><span class="day-col__lbl">Span</span><span>{{ row.weekday_service_span_display() }}</span></div>
        </div>
        {% endif %}
        {% if row.saturday_headway_mins.is_some() %}
        <div class="day-col">
          <div class="day-col__hdr">Saturday</div>
          <div class="spacing-stat-num spacing-{{ row.saturday_badge_variant() }}">{{ row.saturday_display() }}</div>
          <div class="day-col__stat"><span class="day-col__lbl">Top 10%</span><span>{{ row.saturday_top_decile_display() }}</span></div>
          <div class="day-col__stat"><span class="day-col__lbl">Max</span><span>{{ row.saturday_max_headway_display() }}</span></div>
          <div class="day-col__stat"><span class="day-col__lbl">Span</span><span>{{ row.saturday_service_span_display() }}</span></div>
        </div>
        {% endif %}
        {% if row.sunday_headway_mins.is_some() %}
        <div class="day-col">
          <div class="day-col__hdr">Sunday</div>
          <div class="spacing-stat-num spacing-{{ row.sunday_badge_variant() }}">{{ row.sunday_display() }}</div>
          <div class="day-col__stat"><span class="day-col__lbl">Top 10%</span><span>{{ row.sunday_top_decile_display() }}</span></div>
          <div class="day-col__stat"><span class="day-col__lbl">Max</span><span>{{ row.sunday_max_headway_display() }}</span></div>
          <div class="day-col__stat"><span class="day-col__lbl">Span</span><span>{{ row.sunday_service_span_display() }}</span></div>
        </div>
        {% endif %}
      </div>
    </div>
    {% endfor %}
  </div>
  {% endif %}
</div>
```

- [ ] **Step 5: Update CSS in `frequency.html`**

In `crates/server/templates/frequency.html`, replace the block from `.schedule-card__stats {` through the closing `}` of the `@media` block (the block spanning lines 68–96) with:

```css
.schedule-card__days {
  margin-top: 0.75rem;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(90px, 1fr));
  gap: 6px;
  border-top: 1px solid var(--line-soft);
  padding-top: 0.75rem;
}
.day-col {
  background: var(--bg-subtle, #F4F4EF);
  border-radius: 8px;
  padding: 8px 6px;
  text-align: center;
}
.day-col .spacing-stat-num {
  font-size: 1.3rem;
  margin-bottom: 4px;
}
.day-col__hdr {
  font-family: 'Fira Code', monospace;
  font-size: 0.6rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--ink-400);
  margin-bottom: 4px;
}
.day-col__stat {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 3px;
  gap: 4px;
  font-size: 0.72rem;
  color: var(--ink-500);
}
.day-col__lbl {
  font-family: 'Fira Code', monospace;
  font-size: 0.55rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--ink-400);
  white-space: nowrap;
}
@media (max-width: 720px) {
  .schedule-grid {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 6: Build to verify the whole workspace compiles**

```bash
cargo build
```

Expected: success. Both crates compile. The SQL still returns the old columns so E2E tests that hit the DB will fail at runtime with a column-not-found error — that's expected and fixed in Task 2.

- [ ] **Step 7: Run unit tests**

```bash
cargo test -p mobilispect-core frequency::
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add crates/core/src/frequency/mod.rs \
        crates/server/templates/frequency_content.html \
        crates/server/templates/frequency.html
git commit -m "refactor(frequency): per-day struct fields, display methods, and card template"
```

---

## Task 2: Rewrite SQL query

**Files:**
- Modify: `crates/core/src/frequency/mod.rs` (the `sql` string inside `route_headways`)

- [ ] **Step 1: Replace the entire `sql` string in `route_headways()`**

Replace everything from `let sql = "WITH` through the closing `";` with:

```rust
    let sql = "WITH
trip_times AS (
    SELECT
        t.agency_id,
        t.route_id,
        COALESCE(t.direction_id, 0)                              AS direction_id,
        t.trip_id,
        t.service_id,
        MIN((
            SPLIT_PART(ss.departure_time, ':', 1)::INT * 3600
          + SPLIT_PART(ss.departure_time, ':', 2)::INT * 60
          + SPLIT_PART(ss.departure_time, ':', 3)::INT
        )::BIGINT)                                               AS start_secs,
        MAX((
            SPLIT_PART(ss.departure_time, ':', 1)::INT * 3600
          + SPLIT_PART(ss.departure_time, ':', 2)::INT * 60
          + SPLIT_PART(ss.departure_time, ':', 3)::INT
        )::BIGINT)                                               AS end_secs,
        (c.monday OR c.tuesday OR c.wednesday
         OR c.thursday OR c.friday)                             AS is_weekday,
        c.saturday                                               AS is_saturday,
        c.sunday                                                 AS is_sunday
    FROM trips t
    JOIN calendar c
      ON c.agency_id = t.agency_id AND c.service_id = t.service_id
    JOIN scheduled_stops ss
      ON ss.agency_id = t.agency_id AND ss.trip_id = t.trip_id
    WHERE (c.monday OR c.tuesday OR c.wednesday OR c.thursday
           OR c.friday OR c.saturday OR c.sunday)
    GROUP BY
        t.agency_id,
        t.route_id,
        COALESCE(t.direction_id, 0),
        t.trip_id,
        t.service_id,
        is_weekday,
        c.saturday,
        c.sunday
),
wd_gaps AS (
    SELECT agency_id, route_id, direction_id,
        LEAD(start_secs) OVER (
            PARTITION BY agency_id, route_id, direction_id, service_id
            ORDER BY start_secs
        ) - start_secs AS gap_secs
    FROM trip_times
    WHERE is_weekday
),
sat_gaps AS (
    SELECT agency_id, route_id, direction_id,
        LEAD(start_secs) OVER (
            PARTITION BY agency_id, route_id, direction_id, service_id
            ORDER BY start_secs
        ) - start_secs AS gap_secs
    FROM trip_times
    WHERE is_saturday
),
sun_gaps AS (
    SELECT agency_id, route_id, direction_id,
        LEAD(start_secs) OVER (
            PARTITION BY agency_id, route_id, direction_id, service_id
            ORDER BY start_secs
        ) - start_secs AS gap_secs
    FROM trip_times
    WHERE is_sunday
),
wd_headways AS (
    SELECT agency_id, route_id,
        AVG(gap_secs::double precision) / 60.0 AS weekday_headway_mins
    FROM wd_gaps
    WHERE gap_secs > 0
    GROUP BY agency_id, route_id
),
sat_headways AS (
    SELECT agency_id, route_id,
        AVG(gap_secs::double precision) / 60.0 AS saturday_headway_mins
    FROM sat_gaps
    WHERE gap_secs > 0
    GROUP BY agency_id, route_id
),
sun_headways AS (
    SELECT agency_id, route_id,
        AVG(gap_secs::double precision) / 60.0 AS sunday_headway_mins
    FROM sun_gaps
    WHERE gap_secs > 0
    GROUP BY agency_id, route_id
),
wd_gap_summary AS (
    SELECT agency_id, route_id,
        PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY gap_secs::double precision) / 60.0
            AS weekday_top_decile_mins,
        MAX(gap_secs::double precision) / 60.0 AS weekday_max_headway_mins
    FROM wd_gaps
    WHERE gap_secs > 0
    GROUP BY agency_id, route_id
),
sat_gap_summary AS (
    SELECT agency_id, route_id,
        PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY gap_secs::double precision) / 60.0
            AS saturday_top_decile_mins,
        MAX(gap_secs::double precision) / 60.0 AS saturday_max_headway_mins
    FROM sat_gaps
    WHERE gap_secs > 0
    GROUP BY agency_id, route_id
),
sun_gap_summary AS (
    SELECT agency_id, route_id,
        PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY gap_secs::double precision) / 60.0
            AS sunday_top_decile_mins,
        MAX(gap_secs::double precision) / 60.0 AS sunday_max_headway_mins
    FROM sun_gaps
    WHERE gap_secs > 0
    GROUP BY agency_id, route_id
),
wd_service AS (
    SELECT agency_id, route_id,
        MIN(start_secs) AS weekday_service_start_secs,
        MAX(end_secs)   AS weekday_service_end_secs
    FROM trip_times
    WHERE is_weekday
    GROUP BY agency_id, route_id
),
sat_service AS (
    SELECT agency_id, route_id,
        MIN(start_secs) AS saturday_service_start_secs,
        MAX(end_secs)   AS saturday_service_end_secs
    FROM trip_times
    WHERE is_saturday
    GROUP BY agency_id, route_id
),
sun_service AS (
    SELECT agency_id, route_id,
        MIN(start_secs) AS sunday_service_start_secs,
        MAX(end_secs)   AS sunday_service_end_secs
    FROM trip_times
    WHERE is_sunday
    GROUP BY agency_id, route_id
),
route_dirs AS (
    SELECT DISTINCT
        tt.agency_id,
        tt.route_id,
        r.short_name,
        r.long_name
    FROM trip_times tt
    JOIN routes r ON r.agency_id = tt.agency_id AND r.route_id = tt.route_id
)
SELECT
    rd.agency_id,
    rd.route_id,
    rd.short_name,
    rd.long_name,
    wd.weekday_headway_mins,
    sat.saturday_headway_mins,
    sun.sunday_headway_mins,
    wgs.weekday_top_decile_mins,
    wgs.weekday_max_headway_mins,
    ws.weekday_service_start_secs,
    ws.weekday_service_end_secs,
    sgs.saturday_top_decile_mins,
    sgs.saturday_max_headway_mins,
    ss_sat.saturday_service_start_secs,
    ss_sat.saturday_service_end_secs,
    sugs.sunday_top_decile_mins,
    sugs.sunday_max_headway_mins,
    ss_sun.sunday_service_start_secs,
    ss_sun.sunday_service_end_secs
FROM route_dirs rd
LEFT JOIN wd_headways wd
  ON wd.agency_id = rd.agency_id
 AND wd.route_id  = rd.route_id
LEFT JOIN sat_headways sat
  ON sat.agency_id = rd.agency_id
 AND sat.route_id  = rd.route_id
LEFT JOIN sun_headways sun
  ON sun.agency_id = rd.agency_id
 AND sun.route_id  = rd.route_id
LEFT JOIN wd_gap_summary wgs
  ON wgs.agency_id = rd.agency_id
 AND wgs.route_id  = rd.route_id
LEFT JOIN sat_gap_summary sgs
  ON sgs.agency_id = rd.agency_id
 AND sgs.route_id  = rd.route_id
LEFT JOIN sun_gap_summary sugs
  ON sugs.agency_id = rd.agency_id
 AND sugs.route_id  = rd.route_id
LEFT JOIN wd_service ws
  ON ws.agency_id = rd.agency_id
 AND ws.route_id  = rd.route_id
LEFT JOIN sat_service ss_sat
  ON ss_sat.agency_id = rd.agency_id
 AND ss_sat.route_id  = rd.route_id
LEFT JOIN sun_service ss_sun
  ON ss_sun.agency_id = rd.agency_id
 AND ss_sun.route_id  = rd.route_id
WHERE ($1::text IS NULL OR rd.agency_id = $1)
  AND (
      wd.weekday_headway_mins IS NOT NULL
   OR sat.saturday_headway_mins IS NOT NULL
   OR sun.sunday_headway_mins IS NOT NULL
  )
ORDER BY
    rd.agency_id,
    COALESCE(
        wd.weekday_headway_mins,
        sat.saturday_headway_mins,
        sun.sunday_headway_mins
    ) ASC NULLS LAST,
    CASE WHEN rd.short_name ~ '^[0-9]+$'
         THEN rd.short_name::INTEGER ELSE NULL END NULLS LAST,
    rd.short_name";
```

- [ ] **Step 2: Run existing E2E tests**

```bash
cargo test -p mobilispect-server schedule_page_renders_route_schedule_cards
cargo test -p mobilispect-server schedule_page_uses_top_decile
cargo test -p mobilispect-server schedule_page_computes_headways
cargo test -p mobilispect-server schedule_page_combines_directions
```

Expected: all pass. The values `06:00-07:00`, `11.0 min`, and `20.0 min` are still produced by the new per-day CTEs (they are now weekday-scoped, which matches the seed data). The assertions `html.contains("Max headway")` and `html.contains("Service span")` now fail because the template now uses "Max" and "Span" — fix those in Task 4.

- [ ] **Step 3: Commit**

```bash
git add crates/core/src/frequency/mod.rs
git commit -m "feat(frequency): compute top-decile, max, and service span per day type"
```

---

## Task 3: Write failing E2E test for Saturday column rendering

**Files:**
- Modify: `crates/server/src/web/handlers.rs`

- [ ] **Step 1: Add the new test at the end of `mod e2e_tests`**

Insert before the final closing `}` of `mod e2e_tests`:

```rust
#[tokio::test]
async fn schedule_page_renders_saturday_column_when_saturday_service_exists() {
    let td = test_utils::setup().await;
    sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '10', 'Route 10', 3)")
        .execute(&td.db.pool)
        .await
        .unwrap();
    sqlx::query(
        "INSERT INTO calendar VALUES ('0', 'WD', true, true, true, true, true, false, false)",
    )
    .execute(&td.db.pool)
    .await
    .unwrap();
    sqlx::query(
        "INSERT INTO calendar VALUES ('0', 'SAT', false, false, false, false, false, true, false)",
    )
    .execute(&td.db.pool)
    .await
    .unwrap();

    for (trip_id, service_id, dep_time, arr_time) in [
        ("T1", "WD", "06:00:00", "06:30:00"),
        ("T2", "WD", "06:10:00", "06:40:00"),
        ("T3", "SAT", "09:00:00", "09:30:00"),
        ("T4", "SAT", "09:20:00", "09:50:00"),
    ] {
        sqlx::query("INSERT INTO trips VALUES ('0', $1, 'R1', $2, 0, 'Downtown')")
            .bind(trip_id)
            .bind(service_id)
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S1', 1, $2, $2)")
            .bind(trip_id)
            .bind(dep_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S2', 2, $2, $2)")
            .bind(trip_id)
            .bind(arr_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
    }

    let state = AppState {
        db: td.db,
        config: test_config(),
    };
    let app = build_router(state);

    let response = app
        .oneshot(
            Request::builder()
                .uri("/schedule")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);
    let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
        .await
        .unwrap();
    let html = String::from_utf8(bytes.to_vec()).unwrap();
    assert!(html.contains("Weekday"), "weekday column should render");
    assert!(html.contains("Saturday"), "saturday column should render");
    assert!(!html.contains("Sunday"), "sunday column should not render");
    assert!(
        html.contains("09:00-09:50"),
        "saturday service span should be 09:00-09:50"
    );
}
```

- [ ] **Step 2: Run to confirm it passes (the template already renders per-day columns from Task 1)**

```bash
cargo test -p mobilispect-server schedule_page_renders_saturday_column
```

Expected: PASS — the template from Task 1 already renders Saturday column when `saturday_headway_mins` is `Some`, and the SQL from Task 2 now provides `saturday_service_start_secs`/`saturday_service_end_secs`.

---

## Task 4: Fix broken label assertions in E2E tests

**Files:**
- Modify: `crates/server/src/web/handlers.rs`

- [ ] **Step 1: Update the two broken assertions in `schedule_page_renders_route_schedule_cards`**

Find this assertion block in `schedule_page_renders_route_schedule_cards` (look for `assert!(html.contains("Max headway"))`):

```rust
assert!(html.contains("schedule-card"));
assert!(html.contains("Top 10%"));
assert!(html.contains("Max headway"));
assert!(html.contains("Service span"));
assert!(html.contains("06:00-07:00"));
assert!(html.contains("11.0 min"));
assert!(html.contains("20.0 min"));
```

Replace with:

```rust
assert!(html.contains("schedule-card"));
assert!(html.contains("Top 10%"));
assert!(html.contains("Max"));
assert!(html.contains("Span"));
assert!(html.contains("06:00-07:00"));
assert!(html.contains("11.0 min"));
assert!(html.contains("20.0 min"));
```

- [ ] **Step 2: Run the full test suite**

```bash
cargo test -p mobilispect-core
cargo test -p mobilispect-server
```

Expected: all tests pass, no failures.

- [ ] **Step 3: Commit**

```bash
git add crates/server/src/web/handlers.rs
git commit -m "test(frequency): update label assertions for new schedule card layout"
```

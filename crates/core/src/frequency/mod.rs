use anyhow::Result;
use serde::Serialize;

use crate::db::Database;
use crate::ids::{AgencyId, RouteId};

#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct RouteHeadwayRow {
    pub agency_id: AgencyId,
    pub route_id: RouteId,
    pub short_name: String,
    pub long_name: String,
    pub weekday_headway_mins: Option<f64>,
    pub saturday_headway_mins: Option<f64>,
    pub sunday_headway_mins: Option<f64>,
    pub min_headway_mins: Option<f64>,
    pub max_headway_mins: Option<f64>,
    pub service_start_secs: Option<i64>,
    pub service_end_secs: Option<i64>,
    pub trip_count: i64,
}

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

    pub fn min_headway_display(&self) -> String {
        Self::headway_display(self.min_headway_mins)
    }

    pub fn max_headway_display(&self) -> String {
        Self::headway_display(self.max_headway_mins)
    }

    pub fn trip_count_display(&self) -> String {
        self.trip_count.to_string()
    }

    pub fn service_span_display(&self) -> String {
        match (self.service_start_secs, self.service_end_secs) {
            (Some(start), Some(end)) => {
                format!("{}-{}", Self::time_display(start), Self::time_display(end))
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

    pub fn min_headway_badge_variant(&self) -> &'static str {
        Self::headway_badge_variant(self.min_headway_mins)
    }

    pub fn max_headway_badge_variant(&self) -> &'static str {
        Self::headway_badge_variant(self.max_headway_mins)
    }

    pub fn primary_headway_min(&self) -> Option<f64> {
        self.weekday_headway_mins
            .or(self.saturday_headway_mins)
            .or(self.sunday_headway_mins)
    }
}

pub async fn route_headways(
    db: &Database,
    agency_filter: Option<&AgencyId>,
) -> Result<Vec<RouteHeadwayRow>> {
    let sql = "WITH
trip_times AS (
    SELECT
        t.agency_id,
        t.route_id,
        COALESCE(t.direction_id, 0)                              AS direction_id,
        t.trip_id,
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
        is_weekday,
        c.saturday,
        c.sunday
),
wd_gaps AS (
    SELECT agency_id, route_id, direction_id,
        LEAD(start_secs) OVER (
            PARTITION BY agency_id, route_id, direction_id
            ORDER BY start_secs
        ) - start_secs AS gap_secs
    FROM trip_times
    WHERE is_weekday
),
sat_gaps AS (
    SELECT agency_id, route_id, direction_id,
        LEAD(start_secs) OVER (
            PARTITION BY agency_id, route_id, direction_id
            ORDER BY start_secs
        ) - start_secs AS gap_secs
    FROM trip_times
    WHERE is_saturday
),
sun_gaps AS (
    SELECT agency_id, route_id, direction_id,
        LEAD(start_secs) OVER (
            PARTITION BY agency_id, route_id, direction_id
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
all_gaps AS (
    SELECT * FROM wd_gaps
    UNION ALL
    SELECT * FROM sat_gaps
    UNION ALL
    SELECT * FROM sun_gaps
),
gap_summary AS (
    SELECT agency_id, route_id,
        MIN(gap_secs::double precision) / 60.0 AS min_headway_mins,
        MAX(gap_secs::double precision) / 60.0 AS max_headway_mins
    FROM all_gaps
    WHERE gap_secs > 0
    GROUP BY agency_id, route_id
),
service_summary AS (
    SELECT agency_id, route_id,
        MIN(start_secs) AS service_start_secs,
        MAX(end_secs) AS service_end_secs,
        COUNT(*) AS trip_count
    FROM trip_times
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
    gs.min_headway_mins,
    gs.max_headway_mins,
    ss.service_start_secs,
    ss.service_end_secs,
    ss.trip_count
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
LEFT JOIN gap_summary gs
  ON gs.agency_id = rd.agency_id
 AND gs.route_id  = rd.route_id
LEFT JOIN service_summary ss
  ON ss.agency_id = rd.agency_id
 AND ss.route_id  = rd.route_id
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

    let rows = sqlx::query_as(sql)
        .bind(agency_filter.map(|a| a.as_str()))
        .fetch_all(&db.pool)
        .await?;
    Ok(rows)
}

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
            min_headway_mins: Some(5.0),
            max_headway_mins: Some(30.0),
            service_start_secs: Some(6 * 3600),
            service_end_secs: Some(23 * 3600 + 30 * 60),
            trip_count: 42,
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
    fn min_max_headway_display_formats_range() {
        let row = make_row(Some(8.0), Some(15.0), Some(20.0));
        assert_eq!(row.min_headway_display(), "5.0 min");
        assert_eq!(row.max_headway_display(), "30.0 min");
    }

    #[test]
    fn service_span_display_formats_times() {
        let row = make_row(Some(8.0), Some(15.0), Some(20.0));
        assert_eq!(row.service_span_display(), "06:00-23:30");
    }

    #[test]
    fn service_span_display_wraps_after_midnight() {
        let mut row = make_row(Some(8.0), Some(15.0), Some(20.0));
        row.service_end_secs = Some(25 * 3600 + 15 * 60);
        assert_eq!(row.service_span_display(), "06:00-25:15");
    }
}

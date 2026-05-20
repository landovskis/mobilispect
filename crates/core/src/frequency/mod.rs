use anyhow::Result;
use serde::Serialize;

use crate::db::Database;
use crate::ids::{AgencyId, DirectionId, RouteId};

#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct RouteHeadwayRow {
    pub agency_id: AgencyId,
    pub route_id: RouteId,
    pub short_name: String,
    pub long_name: String,
    pub direction_id: DirectionId,
    pub weekday_headway_mins: Option<f64>,
    pub saturday_headway_mins: Option<f64>,
    pub sunday_headway_mins: Option<f64>,
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

    pub fn direction_label(&self) -> &'static str {
        match self.direction_id.as_i64() {
            0 => "Outbound",
            1 => "Inbound",
            _ => "—",
        }
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
first_stop_dep AS (
    SELECT DISTINCT ON (ss.agency_id, ss.trip_id)
        t.agency_id,
        t.route_id,
        COALESCE(t.direction_id, 0)                              AS direction_id,
        (
            SPLIT_PART(ss.departure_time, ':', 1)::INT * 3600
          + SPLIT_PART(ss.departure_time, ':', 2)::INT * 60
          + SPLIT_PART(ss.departure_time, ':', 3)::INT
        )                                                        AS dep_secs,
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
    ORDER BY ss.agency_id, ss.trip_id, ss.stop_sequence ASC
),
wd_gaps AS (
    SELECT agency_id, route_id, direction_id,
        LEAD(dep_secs) OVER (
            PARTITION BY agency_id, route_id, direction_id
            ORDER BY dep_secs
        ) - dep_secs AS gap_secs
    FROM first_stop_dep
    WHERE is_weekday
),
sat_gaps AS (
    SELECT agency_id, route_id, direction_id,
        LEAD(dep_secs) OVER (
            PARTITION BY agency_id, route_id, direction_id
            ORDER BY dep_secs
        ) - dep_secs AS gap_secs
    FROM first_stop_dep
    WHERE is_saturday
),
sun_gaps AS (
    SELECT agency_id, route_id, direction_id,
        LEAD(dep_secs) OVER (
            PARTITION BY agency_id, route_id, direction_id
            ORDER BY dep_secs
        ) - dep_secs AS gap_secs
    FROM first_stop_dep
    WHERE is_sunday
),
route_dirs AS (
    SELECT DISTINCT
        t.agency_id,
        t.route_id,
        r.short_name,
        r.long_name,
        COALESCE(t.direction_id, 0) AS direction_id
    FROM trips t
    JOIN routes r ON r.agency_id = t.agency_id AND r.route_id = t.route_id
    JOIN calendar c ON c.agency_id = t.agency_id AND c.service_id = t.service_id
    WHERE (c.monday OR c.tuesday OR c.wednesday OR c.thursday
           OR c.friday OR c.saturday OR c.sunday)
)
SELECT
    rd.agency_id,
    rd.route_id,
    rd.short_name,
    rd.long_name,
    rd.direction_id,
    AVG(CASE WHEN wd.gap_secs > 0 THEN wd.gap_secs END) / 60.0
        AS weekday_headway_mins,
    AVG(CASE WHEN sat.gap_secs > 0 THEN sat.gap_secs END) / 60.0
        AS saturday_headway_mins,
    AVG(CASE WHEN sun.gap_secs > 0 THEN sun.gap_secs END) / 60.0
        AS sunday_headway_mins
FROM route_dirs rd
LEFT JOIN wd_gaps wd
  ON wd.agency_id = rd.agency_id
 AND wd.route_id  = rd.route_id
 AND wd.direction_id = rd.direction_id
LEFT JOIN sat_gaps sat
  ON sat.agency_id = rd.agency_id
 AND sat.route_id  = rd.route_id
 AND sat.direction_id = rd.direction_id
LEFT JOIN sun_gaps sun
  ON sun.agency_id = rd.agency_id
 AND sun.route_id  = rd.route_id
 AND sun.direction_id = rd.direction_id
WHERE ($1::text IS NULL OR rd.agency_id = $1)
GROUP BY rd.agency_id, rd.route_id, rd.short_name, rd.long_name, rd.direction_id
HAVING
    AVG(CASE WHEN wd.gap_secs  > 0 THEN wd.gap_secs  END) IS NOT NULL
 OR AVG(CASE WHEN sat.gap_secs > 0 THEN sat.gap_secs END) IS NOT NULL
 OR AVG(CASE WHEN sun.gap_secs > 0 THEN sun.gap_secs END) IS NOT NULL
ORDER BY
    rd.agency_id,
    COALESCE(
        AVG(CASE WHEN wd.gap_secs  > 0 THEN wd.gap_secs  END),
        AVG(CASE WHEN sat.gap_secs > 0 THEN sat.gap_secs END),
        AVG(CASE WHEN sun.gap_secs > 0 THEN sun.gap_secs END)
    ) / 60.0 ASC NULLS LAST,
    CASE WHEN rd.short_name ~ '^[0-9]+$'
         THEN rd.short_name::INTEGER ELSE NULL END NULLS LAST,
    rd.short_name,
    rd.direction_id";

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
            direction_id: DirectionId(0),
            weekday_headway_mins: wd,
            saturday_headway_mins: sat,
            sunday_headway_mins: sun,
        }
    }

    #[test]
    fn direction_label_outbound() {
        let row = make_row(None, None, None);
        assert_eq!(row.direction_label(), "Outbound"); // direction_id = 0 from make_row
    }

    #[test]
    fn direction_label_inbound() {
        let mut row = make_row(None, None, None);
        row.direction_id = DirectionId(1);
        assert_eq!(row.direction_label(), "Inbound");
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
}

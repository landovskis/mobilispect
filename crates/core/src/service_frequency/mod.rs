use anyhow::Result;
use serde::Serialize;

use crate::db::Database;
use crate::ids::{FeedId, RouteId};

#[derive(Debug, Serialize)]
pub struct HourBin {
    pub hour: i32,
    pub trip_count: i32,
}

pub struct RouteHourlyFrequency {
    pub short_name: String,
    pub long_name: String,
    pub weekday_bins: Vec<HourBin>,
    pub saturday_bins: Vec<HourBin>,
    pub sunday_bins: Vec<HourBin>,
}

impl RouteHourlyFrequency {
    pub fn has_weekday(&self) -> bool {
        !self.weekday_bins.is_empty()
    }

    pub fn has_saturday(&self) -> bool {
        !self.saturday_bins.is_empty()
    }

    pub fn has_sunday(&self) -> bool {
        !self.sunday_bins.is_empty()
    }

    pub fn weekday_json(&self) -> String {
        serde_json::to_string(&self.weekday_bins).unwrap_or_else(|_| "[]".to_string())
    }

    pub fn saturday_json(&self) -> String {
        serde_json::to_string(&self.saturday_bins).unwrap_or_else(|_| "[]".to_string())
    }

    pub fn sunday_json(&self) -> String {
        serde_json::to_string(&self.sunday_bins).unwrap_or_else(|_| "[]".to_string())
    }
}

#[derive(sqlx::FromRow)]
struct HourlyRow {
    day_type: Option<String>,
    hour: i32,
    trip_count: i32,
}

pub async fn route_hourly_frequency(
    db: &Database,
    feed_id: FeedId,
    route_id: &RouteId,
) -> Result<Option<RouteHourlyFrequency>> {
    let route_info = sqlx::query!(
        "SELECT short_name, long_name FROM routes WHERE onestop_id = $1",
        route_id.as_str()
    )
    .fetch_optional(&db.pool)
    .await?;

    let (short_name, long_name) = match route_info {
        Some(r) => (r.short_name, r.long_name),
        None => return Ok(None),
    };

    let sql = "WITH trip_first AS (
    SELECT
        t.trip_id,
        (svc.monday OR svc.tuesday OR svc.wednesday
         OR svc.thursday OR svc.friday) AS is_weekday,
        svc.saturday                     AS is_saturday,
        svc.sunday                       AS is_sunday,
        MIN(
            SPLIT_PART(ss.departure_time, ':', 1)::INT * 3600
          + SPLIT_PART(ss.departure_time, ':', 2)::INT * 60
          + SPLIT_PART(ss.departure_time, ':', 3)::INT
        ) AS departure_secs
    FROM trips t
    JOIN route_variants rv
      ON rv.feed_id = t.feed_id AND rv.variant_id = t.variant_id
    JOIN services svc
      ON svc.feed_id = t.feed_id AND svc.service_id = t.service_id
    JOIN scheduled_stops ss
      ON ss.feed_id = t.feed_id AND ss.trip_id = t.trip_id
    WHERE t.feed_id = $1
      AND rv.route_id = $2
      AND (svc.monday OR svc.tuesday OR svc.wednesday OR svc.thursday
           OR svc.friday OR svc.saturday OR svc.sunday)
    GROUP BY t.trip_id,
             svc.monday, svc.tuesday, svc.wednesday,
             svc.thursday, svc.friday, svc.saturday, svc.sunday
),
hourly AS (
    SELECT
        CASE
            WHEN is_weekday THEN 'weekday'
            WHEN is_saturday THEN 'saturday'
            WHEN is_sunday   THEN 'sunday'
        END AS day_type,
        departure_secs / 3600 AS hour,
        COUNT(*)::INT          AS trip_count
    FROM trip_first
    GROUP BY day_type, hour
)
SELECT day_type, hour::INT AS hour, trip_count
FROM hourly
WHERE day_type IS NOT NULL
ORDER BY day_type, hour";

    let rows: Vec<HourlyRow> = sqlx::query_as(sql)
        .bind(feed_id.as_i64())
        .bind(route_id.as_str())
        .fetch_all(&db.pool)
        .await?;

    let mut weekday_bins = Vec::new();
    let mut saturday_bins = Vec::new();
    let mut sunday_bins = Vec::new();

    for row in rows {
        let bin = HourBin {
            hour: row.hour,
            trip_count: row.trip_count,
        };
        match row.day_type.as_deref() {
            Some("weekday") => weekday_bins.push(bin),
            Some("saturday") => saturday_bins.push(bin),
            Some("sunday") => sunday_bins.push(bin),
            _ => {}
        }
    }

    Ok(Some(RouteHourlyFrequency {
        short_name,
        long_name,
        weekday_bins,
        saturday_bins,
        sunday_bins,
    }))
}

#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct RouteHeadwayRow {
    pub feed_id: FeedId,
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
        Self::service_span(
            self.weekday_service_start_secs,
            self.weekday_service_end_secs,
        )
    }

    pub fn saturday_service_span_display(&self) -> String {
        Self::service_span(
            self.saturday_service_start_secs,
            self.saturday_service_end_secs,
        )
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

pub async fn route_headways(
    db: &Database,
    feed_filter: Option<FeedId>,
) -> Result<Vec<RouteHeadwayRow>> {
    let sql = "WITH
trip_times AS (
    SELECT
        t.feed_id,
        rv.route_id,
        COALESCE(rv.direction_id, 0)                             AS direction_id,
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
        (svc.monday OR svc.tuesday OR svc.wednesday
         OR svc.thursday OR svc.friday)                          AS is_weekday,
        svc.saturday                                             AS is_saturday,
        svc.sunday                                               AS is_sunday
    FROM trips t
    JOIN route_variants rv
      ON rv.feed_id = t.feed_id AND rv.variant_id = t.variant_id
    JOIN services svc
      ON svc.feed_id = t.feed_id AND svc.service_id = t.service_id
    JOIN scheduled_stops ss
      ON ss.feed_id = t.feed_id AND ss.trip_id = t.trip_id
    WHERE (svc.monday OR svc.tuesday OR svc.wednesday OR svc.thursday
           OR svc.friday OR svc.saturday OR svc.sunday)
    GROUP BY
        t.feed_id,
        rv.route_id,
        COALESCE(rv.direction_id, 0),
        t.trip_id,
        t.service_id,
        is_weekday,
        svc.saturday,
        svc.sunday
),
wd_gaps AS (
    SELECT feed_id, route_id, direction_id,
        LEAD(start_secs) OVER (
            PARTITION BY feed_id, route_id, direction_id, service_id
            ORDER BY start_secs
        ) - start_secs AS gap_secs
    FROM trip_times
    WHERE is_weekday
),
sat_gaps AS (
    SELECT feed_id, route_id, direction_id,
        LEAD(start_secs) OVER (
            PARTITION BY feed_id, route_id, direction_id, service_id
            ORDER BY start_secs
        ) - start_secs AS gap_secs
    FROM trip_times
    WHERE is_saturday
),
sun_gaps AS (
    SELECT feed_id, route_id, direction_id,
        LEAD(start_secs) OVER (
            PARTITION BY feed_id, route_id, direction_id, service_id
            ORDER BY start_secs
        ) - start_secs AS gap_secs
    FROM trip_times
    WHERE is_sunday
),
wd_headways AS (
    SELECT feed_id, route_id,
        AVG(gap_secs::double precision) / 60.0 AS weekday_headway_mins
    FROM wd_gaps
    WHERE gap_secs > 0
    GROUP BY feed_id, route_id
),
sat_headways AS (
    SELECT feed_id, route_id,
        AVG(gap_secs::double precision) / 60.0 AS saturday_headway_mins
    FROM sat_gaps
    WHERE gap_secs > 0
    GROUP BY feed_id, route_id
),
sun_headways AS (
    SELECT feed_id, route_id,
        AVG(gap_secs::double precision) / 60.0 AS sunday_headway_mins
    FROM sun_gaps
    WHERE gap_secs > 0
    GROUP BY feed_id, route_id
),
wd_gap_summary AS (
    SELECT feed_id, route_id,
        PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY gap_secs::double precision) / 60.0
            AS weekday_top_decile_mins,
        MAX(gap_secs::double precision) / 60.0 AS weekday_max_headway_mins
    FROM wd_gaps
    WHERE gap_secs > 0
    GROUP BY feed_id, route_id
),
sat_gap_summary AS (
    SELECT feed_id, route_id,
        PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY gap_secs::double precision) / 60.0
            AS saturday_top_decile_mins,
        MAX(gap_secs::double precision) / 60.0 AS saturday_max_headway_mins
    FROM sat_gaps
    WHERE gap_secs > 0
    GROUP BY feed_id, route_id
),
sun_gap_summary AS (
    SELECT feed_id, route_id,
        PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY gap_secs::double precision) / 60.0
            AS sunday_top_decile_mins,
        MAX(gap_secs::double precision) / 60.0 AS sunday_max_headway_mins
    FROM sun_gaps
    WHERE gap_secs > 0
    GROUP BY feed_id, route_id
),
wd_service AS (
    SELECT feed_id, route_id,
        MIN(start_secs) AS weekday_service_start_secs,
        MAX(end_secs)   AS weekday_service_end_secs
    FROM trip_times
    WHERE is_weekday
    GROUP BY feed_id, route_id
),
sat_service AS (
    SELECT feed_id, route_id,
        MIN(start_secs) AS saturday_service_start_secs,
        MAX(end_secs)   AS saturday_service_end_secs
    FROM trip_times
    WHERE is_saturday
    GROUP BY feed_id, route_id
),
sun_service AS (
    SELECT feed_id, route_id,
        MIN(start_secs) AS sunday_service_start_secs,
        MAX(end_secs)   AS sunday_service_end_secs
    FROM trip_times
    WHERE is_sunday
    GROUP BY feed_id, route_id
),
route_dirs AS (
    SELECT DISTINCT
        tt.feed_id,
        tt.route_id,
        r.short_name,
        r.long_name
    FROM trip_times tt
    JOIN routes r ON r.onestop_id = tt.route_id
)
SELECT
    rd.feed_id,
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
  ON wd.feed_id  = rd.feed_id
 AND wd.route_id = rd.route_id
LEFT JOIN sat_headways sat
  ON sat.feed_id  = rd.feed_id
 AND sat.route_id = rd.route_id
LEFT JOIN sun_headways sun
  ON sun.feed_id  = rd.feed_id
 AND sun.route_id = rd.route_id
LEFT JOIN wd_gap_summary wgs
  ON wgs.feed_id  = rd.feed_id
 AND wgs.route_id = rd.route_id
LEFT JOIN sat_gap_summary sgs
  ON sgs.feed_id  = rd.feed_id
 AND sgs.route_id = rd.route_id
LEFT JOIN sun_gap_summary sugs
  ON sugs.feed_id  = rd.feed_id
 AND sugs.route_id = rd.route_id
LEFT JOIN wd_service ws
  ON ws.feed_id  = rd.feed_id
 AND ws.route_id = rd.route_id
LEFT JOIN sat_service ss_sat
  ON ss_sat.feed_id  = rd.feed_id
 AND ss_sat.route_id = rd.route_id
LEFT JOIN sun_service ss_sun
  ON ss_sun.feed_id  = rd.feed_id
 AND ss_sun.route_id = rd.route_id
WHERE ($1::BIGINT IS NULL OR rd.feed_id = $1)
  AND (
      wd.weekday_headway_mins IS NOT NULL
   OR sat.saturday_headway_mins IS NOT NULL
   OR sun.sunday_headway_mins IS NOT NULL
  )
ORDER BY
    rd.feed_id,
    COALESCE(
        wd.weekday_headway_mins,
        sat.saturday_headway_mins,
        sun.sunday_headway_mins
    ) ASC NULLS LAST,
    CASE WHEN rd.short_name ~ '^[0-9]+$'
         THEN rd.short_name::INTEGER ELSE NULL END NULLS LAST,
    rd.short_name";

    let rows = sqlx::query_as(sql)
        .bind(feed_filter.map(|f| f.as_i64()))
        .fetch_all(&db.pool)
        .await?;
    Ok(rows)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;
    use crate::ids::FeedId;

    #[tokio::test]
    async fn route_headways_returns_row_when_route_has_weekday_trips() {
        let td = test_utils::setup().await;
        let db = td.db;

        // Insert feed
        sqlx::query("INSERT INTO feeds (id, gtfs_static_url) VALUES (1, 'http://stm')")
            .execute(&db.pool)
            .await
            .unwrap();
        // Insert agency (required by FK constraint on routes.agency_id)
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('stm', 'STM')")
            .execute(&db.pool)
            .await
            .unwrap();
        // Insert route
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('r-stm-R1', 'stm', '1', 'Route 1', 3)",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        // Insert service (replaces calendar after migration 014)
        sqlx::query(
            "INSERT INTO services (feed_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday) VALUES (1, 'WD', true, true, true, true, true, false, false)",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        // Insert route variant
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (1, 'VAR1', 'r-stm-R1', 0, 1, 2, true)",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        // Two trips 30 minutes apart → 30-minute weekday headway
        sqlx::query(
            "INSERT INTO trips (feed_id, trip_id, service_id, variant_id) VALUES (1, 'T1', 'WD', 'VAR1')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO trips (feed_id, trip_id, service_id, variant_id) VALUES (1, 'T2', 'WD', 'VAR1')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        // Insert a stop for scheduled_stops FK
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S1', 'Stop 1', 45.5, -73.5)",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops (feed_id, trip_id, stop_id, stop_sequence, arrival_time, departure_time) VALUES (1, 'T1', 'S1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops (feed_id, trip_id, stop_id, stop_sequence, arrival_time, departure_time) VALUES (1, 'T2', 'S1', 1, '08:30:00', '08:30:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let rows = route_headways(&db, None).await.unwrap();
        assert_eq!(rows.len(), 1);
        assert_eq!(rows[0].route_id, "r-stm-R1");
        let headway = rows[0].weekday_headway_mins.unwrap();
        assert!(
            (headway - 30.0).abs() < 0.1,
            "expected 30 min headway, got {headway}"
        );
    }

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
            feed_id: FeedId::from(1i64),
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

    #[test]
    fn weekday_display_formats_headway() {
        let row = make_row(Some(8.0), None, None);
        assert_eq!(row.weekday_display(), "8.0 min");
    }

    #[test]
    fn saturday_display_formats_headway() {
        let row = make_row(None, Some(15.0), None);
        assert_eq!(row.saturday_display(), "15.0 min");
    }

    #[test]
    fn sunday_display_formats_headway() {
        let row = make_row(None, None, Some(20.0));
        assert_eq!(row.sunday_display(), "20.0 min");
    }

    #[test]
    fn saturday_top_decile_and_max_display() {
        let row = make_row(None, Some(15.0), None);
        assert_eq!(row.saturday_top_decile_display(), "10.0 min");
        assert_eq!(row.saturday_max_headway_display(), "40.0 min");
    }

    #[test]
    fn sunday_top_decile_and_max_display() {
        let row = make_row(None, None, Some(20.0));
        assert_eq!(row.sunday_top_decile_display(), "15.0 min");
        assert_eq!(row.sunday_max_headway_display(), "50.0 min");
    }

    #[test]
    fn weekday_badge_variant_delegates_to_headway() {
        let row = make_row(Some(8.0), None, None);
        assert_eq!(row.weekday_badge_variant(), "good");
    }

    #[test]
    fn saturday_badge_variant_delegates_to_headway() {
        let row = make_row(None, Some(15.0), None);
        assert_eq!(row.saturday_badge_variant(), "mixed");
    }

    #[test]
    fn sunday_badge_variant_delegates_to_headway() {
        let row = make_row(None, None, Some(25.0));
        assert_eq!(row.sunday_badge_variant(), "bad");
    }

    async fn setup_route(
        db: &crate::db::Database,
        feed_id: i64,
        route_id: &str,
        short_name: &str,
        long_name: &str,
    ) {
        sqlx::query(&format!(
            "INSERT INTO feeds (id, gtfs_static_url) VALUES ({feed_id}, 'http://test')"
        ))
        .execute(&db.pool)
        .await
        .unwrap();
        // Insert agency (required by FK constraint on routes.agency_id)
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('agency', 'Test Agency') ON CONFLICT DO NOTHING")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(&format!(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('{route_id}', 'agency', '{short_name}', '{long_name}', 3)"
        ))
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S1', 'Stop 1', 45.5, -73.5)",
        )
        .execute(&db.pool)
        .await
        .unwrap();
    }

    async fn insert_service(
        db: &crate::db::Database,
        feed_id: i64,
        service_id: &str,
        weekday: bool,
        saturday: bool,
        sunday: bool,
    ) {
        sqlx::query(&format!(
            "INSERT INTO services (feed_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday) VALUES ({feed_id}, '{service_id}', {weekday}, {weekday}, {weekday}, {weekday}, {weekday}, {saturday}, {sunday})"
        ))
        .execute(&db.pool)
        .await
        .unwrap();
    }

    async fn insert_trip_at_hour(
        db: &crate::db::Database,
        feed_id: i64,
        trip_id: &str,
        service_id: &str,
        variant_id: &str,
        hour: u32,
    ) {
        sqlx::query(&format!(
            "INSERT INTO trips (feed_id, trip_id, service_id, variant_id) VALUES ({feed_id}, '{trip_id}', '{service_id}', '{variant_id}')"
        ))
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(&format!(
            "INSERT INTO scheduled_stops (feed_id, trip_id, stop_id, stop_sequence, arrival_time, departure_time) VALUES ({feed_id}, '{trip_id}', 'S1', 1, '{hour:02}:00:00', '{hour:02}:00:00')"
        ))
        .execute(&db.pool)
        .await
        .unwrap();
    }

    #[tokio::test]
    async fn route_hourly_frequency_returns_none_for_unknown_route() {
        let td = test_utils::setup().await;
        let result =
            route_hourly_frequency(&td.db, FeedId::from(1i64), &RouteId::from("r-unknown"))
                .await
                .unwrap();
        assert!(result.is_none());
    }

    #[tokio::test]
    async fn route_hourly_frequency_groups_weekday_trips_by_hour() {
        let td = test_utils::setup().await;
        let db = &td.db;
        setup_route(db, 1, "r-test-R1", "1", "Route 1").await;
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (1, 'V1', 'r-test-R1', 0, 1, 3, true)",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        insert_service(db, 1, "WD", true, false, false).await;
        insert_trip_at_hour(db, 1, "T1", "WD", "V1", 8).await;
        insert_trip_at_hour(db, 1, "T2", "WD", "V1", 8).await;
        insert_trip_at_hour(db, 1, "T3", "WD", "V1", 9).await;

        let freq = route_hourly_frequency(db, FeedId::from(1i64), &RouteId::from("r-test-R1"))
            .await
            .unwrap()
            .unwrap();

        assert_eq!(freq.short_name, "1");
        assert!(freq.has_weekday());
        assert!(!freq.has_saturday());
        assert!(!freq.has_sunday());
        assert_eq!(freq.weekday_bins.len(), 2);
        assert_eq!(freq.weekday_bins[0].hour, 8);
        assert_eq!(freq.weekday_bins[0].trip_count, 2);
        assert_eq!(freq.weekday_bins[1].hour, 9);
        assert_eq!(freq.weekday_bins[1].trip_count, 1);
    }

    #[tokio::test]
    async fn route_hourly_frequency_separates_saturday_and_sunday() {
        let td = test_utils::setup().await;
        let db = &td.db;
        setup_route(db, 1, "r-test-R2", "2", "Route 2").await;
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (1, 'V2', 'r-test-R2', 0, 1, 2, true)",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        insert_service(db, 1, "SAT", false, true, false).await;
        insert_service(db, 1, "SUN", false, false, true).await;
        insert_trip_at_hour(db, 1, "T1", "SAT", "V2", 10).await;
        insert_trip_at_hour(db, 1, "T2", "SUN", "V2", 11).await;

        let freq = route_hourly_frequency(db, FeedId::from(1i64), &RouteId::from("r-test-R2"))
            .await
            .unwrap()
            .unwrap();

        assert!(!freq.has_weekday());
        assert!(freq.has_saturday());
        assert!(freq.has_sunday());
        assert_eq!(freq.saturday_bins[0].hour, 10);
        assert_eq!(freq.saturday_bins[0].trip_count, 1);
        assert_eq!(freq.sunday_bins[0].hour, 11);
        assert_eq!(freq.sunday_bins[0].trip_count, 1);
    }

    #[test]
    fn route_hourly_frequency_json_methods_serialize_bins() {
        let freq = RouteHourlyFrequency {
            short_name: "1".to_string(),
            long_name: "Route 1".to_string(),
            weekday_bins: vec![HourBin {
                hour: 8,
                trip_count: 3,
            }],
            saturday_bins: vec![],
            sunday_bins: vec![],
        };
        let json = freq.weekday_json();
        assert!(json.contains("\"hour\":8"));
        assert!(json.contains("\"trip_count\":3"));
        assert_eq!(freq.saturday_json(), "[]");
        assert_eq!(freq.sunday_json(), "[]");
    }
}

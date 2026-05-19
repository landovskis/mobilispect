mod on_time;
pub use on_time::*;

mod route_summary;
pub use route_summary::*;

use anyhow::Result;
use serde::Serialize;

/// One day of combined on-time and speed data for a route.
#[derive(Debug, Serialize)]
pub struct DailyTrendPoint {
    pub service_date: String,
    pub on_time_pct: Option<f64>,
    pub avg_delay_secs: Option<f64>,
    pub actual_speed_mps: Option<f64>,
}

/// Per-route trend data: route info + ordered daily points.
#[derive(Debug, Serialize)]
pub struct RouteTrend {
    pub route_id: RouteId,
    pub short_name: String,
    pub long_name: String,
    pub days: Vec<DailyTrendPoint>,
}

impl RouteTrend {
    /// Percentage change in speed from the first 7 days to the last 7 days.
    /// Positive = faster, negative = slower. None if fewer than 14 days with speed data.
    pub fn speed_change_pct(&self) -> Option<f64> {
        let speed_days: Vec<f64> = self
            .days
            .iter()
            .filter_map(|d| d.actual_speed_mps)
            .collect();
        if speed_days.len() < 14 {
            return None;
        }
        let n = speed_days.len();
        let first_avg = speed_days[..7].iter().sum::<f64>() / 7.0;
        let last_avg = speed_days[n - 7..].iter().sum::<f64>() / 7.0;
        if first_avg == 0.0 {
            return None;
        }
        Some((last_avg - first_avg) / first_avg * 100.0)
    }

    pub fn speed_change_display(&self) -> String {
        match self.speed_change_pct() {
            None => "Insufficient data".to_string(),
            Some(p) if p > 0.5 => format!("+{p:.1}% faster"),
            Some(p) if p < -0.5 => format!("{p:.1}% slower"),
            Some(_) => "Stable".to_string(),
        }
    }

    /// CSS class for the callout value: "decline", "improve", or "".
    pub fn speed_change_class(&self) -> &'static str {
        match self.speed_change_pct() {
            Some(p) if p < -0.5 => "decline",
            Some(p) if p > 0.5 => "improve",
            _ => "",
        }
    }
}

use crate::config::AgencyConfig;
use crate::db::Database;
use crate::ids::{AgencyId, RouteId, StopId};

/// A stop ranked by average delay — used for the hotspot map.
#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct StopHotspot {
    pub stop_id: StopId,
    pub stop_name: String,
    pub stop_lat: f64,
    pub stop_lon: f64,
    pub avg_delay_secs: Option<f64>,
    pub observation_count: i64,
}

impl StopHotspot {
    pub fn delay_display(&self) -> String {
        match self.avg_delay_secs {
            Some(d) if d > 0.0 => format!("+{d:.0}s"),
            Some(d) => format!("{d:.0}s"),
            None => "—".to_string(),
        }
    }

    /// Hex color for map circle: red ≥ 5 min, orange ≥ 1 min, yellow > 0, green otherwise.
    pub fn color(&self) -> &'static str {
        match self.avg_delay_secs {
            Some(d) if d >= 300.0 => "#dc3545",
            Some(d) if d >= 60.0 => "#fd7e14",
            Some(d) if d > 0.0 => "#ffc107",
            _ => "#28a745",
        }
    }
}

/// Fetch the worst stops by average delay over the last N days.
/// Only includes stops with at least `min_observations` events.
pub async fn stop_hotspots(
    db: &Database,
    agency: &AgencyConfig,
    days: i64,
    limit: i64,
) -> Result<Vec<StopHotspot>> {
    let offset = &agency.agency_utc_offset;
    let rows: Vec<StopHotspot> = sqlx::query_as(
        "SELECT
           s.stop_id,
           s.stop_name,
           s.stop_lat,
           s.stop_lon,
           ROUND(AVG(CAST(COALESCE(
             ste.arrival_delay,
             CASE WHEN ste.arrival_time_unix IS NOT NULL
               THEN ste.arrival_time_unix - EXTRACT(EPOCH FROM (
                 ste.observed_at::TIMESTAMPTZ::DATE::TEXT || 'T' ||
                 CASE WHEN SUBSTRING(ss.arrival_time, 1, 2)::INTEGER >= 24
                   THEN LPAD((SUBSTRING(ss.arrival_time, 1, 2)::INTEGER - 24)::TEXT, 2, '0')
                        || SUBSTRING(ss.arrival_time, 3)
                   ELSE ss.arrival_time
                 END || $1
               )::TIMESTAMPTZ)::BIGINT
               ELSE NULL
             END
           ) AS DOUBLE PRECISION))::NUMERIC, 0) as avg_delay_secs,
           COUNT(*) as observation_count
         FROM stop_time_events ste
         JOIN scheduled_stops ss
           ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id AND ss.agency_id = ste.agency_id
         JOIN stops s ON s.stop_id = ste.stop_id AND s.agency_id = ste.agency_id
         WHERE ste.observed_at::TIMESTAMPTZ >= NOW() - ($2::BIGINT * INTERVAL '1 day')
           AND (ste.arrival_delay IS NOT NULL OR ste.arrival_time_unix IS NOT NULL)
         GROUP BY s.stop_id, s.stop_name, s.stop_lat, s.stop_lon
         HAVING COUNT(*) >= 5
         ORDER BY avg_delay_secs DESC
         LIMIT $3",
    )
    .bind(offset)
    .bind(days)
    .bind(limit)
    .fetch_all(&db.pool)
    .await?;
    Ok(rows)
}

/// Fetch per-day trend data for a single route (last N days).
/// Returns None if the route doesn't exist or has no computed data.
pub async fn route_trend(
    db: &Database,
    agency_id: &AgencyId,
    route_id: &RouteId,
    days: i64,
) -> Result<Option<RouteTrend>> {
    // Verify route exists.
    let route: Option<(String, String)> = sqlx::query_as(
        "SELECT short_name, long_name FROM routes WHERE agency_id = $1 AND route_id = $2",
    )
    .bind(agency_id.as_str())
    .bind(route_id.as_str())
    .fetch_optional(&db.pool)
    .await?;

    let (short_name, long_name) = match route {
        Some(r) => r,
        None => return Ok(None),
    };

    // Daily points: LEFT JOIN speed (averaged across directions) onto on-time data.
    let points: Vec<(String, Option<f64>, Option<f64>, Option<f64>)> = sqlx::query_as(
        "SELECT
           rd.service_date,
           rd.on_time_pct,
           rd.avg_delay_secs,
           AVG(rsd.actual_speed_mps) as actual_speed_mps
         FROM route_daily rd
         LEFT JOIN route_speed_daily rsd
           ON rsd.agency_id = rd.agency_id AND rsd.route_id = rd.route_id AND rsd.service_date = rd.service_date
         WHERE rd.agency_id = $1 AND rd.route_id = $2
           AND rd.service_date >= (CURRENT_DATE - $3::INT * INTERVAL '1 day')::TEXT
         GROUP BY rd.service_date, rd.on_time_pct, rd.avg_delay_secs
         ORDER BY rd.service_date",
    )
    .bind(agency_id.as_str())
    .bind(route_id.as_str())
    .bind(days)
    .fetch_all(&db.pool)
    .await?;

    if points.is_empty() {
        return Ok(None);
    }

    let trend_days = points
        .into_iter()
        .map(
            |(service_date, on_time_pct, avg_delay_secs, actual_speed_mps)| DailyTrendPoint {
                service_date,
                on_time_pct,
                avg_delay_secs,
                actual_speed_mps,
            },
        )
        .collect();

    Ok(Some(RouteTrend {
        route_id: route_id.clone(),
        short_name,
        long_name,
        days: trend_days,
    }))
}

/// A global reference transit system used for benchmarking.
#[derive(Debug, sqlx::FromRow, Serialize, Clone)]
pub struct Benchmark {
    pub id: i64,
    pub system_name: String,
    pub city: String,
    pub on_time_pct: f64,
    pub speed_vs_scheduled_pct: f64,
    pub source_url: String,
    pub year: i32,
}

/// Per-route data for the scorecard page.
#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct ScorecardRoute {
    pub agency_id: AgencyId,
    pub route_id: RouteId,
    pub short_name: String,
    pub long_name: String,
    pub avg_on_time_pct: Option<f64>,
    /// Speed deficit: (scheduled − actual) / scheduled × 100. Positive = slower than schedule.
    pub speed_vs_scheduled_pct: Option<f64>,
}

impl ScorecardRoute {
    /// Percentage-point delta vs a benchmark's on-time %. Positive = better than benchmark.
    pub fn on_time_gap_vs(&self, benchmark_pct: f64) -> Option<f64> {
        Some(self.avg_on_time_pct? - benchmark_pct)
    }

    /// "+Xpp", "-Xpp", or "—".
    pub fn on_time_gap_display(&self, benchmark_pct: &f64) -> String {
        match self.on_time_gap_vs(*benchmark_pct) {
            None => "—".to_string(),
            Some(g) if g >= 0.0 => format!("+{g:.0}pp"),
            Some(g) => format!("{g:.0}pp"),
        }
    }

    /// CSS class for gap cell: "gap-pos" (green), "gap-neg" (red), or "".
    pub fn on_time_gap_class(&self, benchmark_pct: &f64) -> &'static str {
        match self.on_time_gap_vs(*benchmark_pct) {
            Some(g) if g >= 0.0 => "gap-pos",
            Some(_) => "gap-neg",
            None => "",
        }
    }

    /// "World class" / "Competitive" / "Below all" / "No data".
    /// floor_pct = Helsinki (89.0), ceiling_pct = Tokyo (96.0).
    pub fn status_label(&self, floor_pct: &f64, ceiling_pct: &f64) -> &'static str {
        match self.avg_on_time_pct {
            Some(p) if p >= *ceiling_pct => "World class",
            Some(p) if p >= *floor_pct => "Competitive",
            Some(_) => "Below all",
            None => "No data",
        }
    }

    pub fn badge_variant(&self, floor_pct: &f64, ceiling_pct: &f64) -> &'static str {
        match self.avg_on_time_pct {
            Some(p) if p >= *ceiling_pct => "good",
            Some(p) if p >= *floor_pct => "mixed",
            Some(_) => "bad",
            None => "neutral",
        }
    }

    /// "X% slower" / "X% faster" / "On pace" / "—".
    pub fn speed_display(&self) -> String {
        match self.speed_vs_scheduled_pct {
            None => "—".to_string(),
            Some(d) if d > 1.0 => format!("{d:.0}% slower"),
            Some(d) if d < -1.0 => format!("{:.0}% faster", d.abs()),
            Some(_) => "On pace".to_string(),
        }
    }

    /// CSS class for speed cell: "slower" / "faster" / "onpace" / "".
    pub fn speed_class(&self) -> &'static str {
        match self.speed_vs_scheduled_pct {
            Some(d) if d > 1.0 => "slower",
            Some(d) if d < -1.0 => "faster",
            Some(_) => "onpace",
            None => "",
        }
    }

    /// On-time % as string, or "—".
    pub fn on_time_display(&self) -> String {
        match self.avg_on_time_pct {
            Some(p) => format!("{p:.1}%"),
            None => "—".to_string(),
        }
    }
}

/// Load all benchmarks ordered by on_time_pct ASC (weakest first, Helsinki → Tokyo).
pub async fn load_benchmarks(db: &Database) -> Result<Vec<Benchmark>> {
    let rows: Vec<Benchmark> = sqlx::query_as(
        "SELECT id, system_name, city, on_time_pct, speed_vs_scheduled_pct, source_url, year
         FROM benchmarks
         ORDER BY on_time_pct ASC, city ASC",
    )
    .fetch_all(&db.pool)
    .await?;
    Ok(rows)
}

/// Fetch per-route scorecard data: on-time % and speed deficit averaged over specified days.
/// If `agency_filter` is Some, only returns routes for that agency.
pub async fn scorecard_routes(
    db: &Database,
    days: i64,
    agency_filter: Option<&AgencyId>,
) -> Result<Vec<ScorecardRoute>> {
    let rows: Vec<ScorecardRoute> = match agency_filter {
        None => sqlx::query_as(
            "SELECT
               ot.agency_id,
               ot.route_id,
               r.short_name,
               r.long_name,
               ot.avg_on_time_pct,
               sp.speed_vs_scheduled_pct
             FROM (
               SELECT agency_id, route_id, ROUND(AVG(on_time_pct)::NUMERIC, 1)::FLOAT8 AS avg_on_time_pct
               FROM route_daily
               WHERE service_date >= (CURRENT_DATE - $1::INT * INTERVAL '1 day')::TEXT
               GROUP BY agency_id, route_id
             ) ot
             JOIN routes r ON r.agency_id = ot.agency_id AND r.route_id = ot.route_id
             LEFT JOIN (
               SELECT rs.agency_id, rs.route_id,
                 ROUND(AVG(
                   CASE WHEN rs.scheduled_speed_mps > 0 AND rsd.avg_actual IS NOT NULL
                     THEN (rs.scheduled_speed_mps - rsd.avg_actual) / rs.scheduled_speed_mps * 100.0
                     ELSE NULL END
                 )::NUMERIC, 1)::FLOAT8 AS speed_vs_scheduled_pct
               FROM route_speed rs
               LEFT JOIN (
                 SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) AS avg_actual
                 FROM route_speed_daily
                 WHERE service_date >= (CURRENT_DATE - $2::INT * INTERVAL '1 day')::TEXT
                 GROUP BY agency_id, route_id, direction_id
               ) rsd ON rsd.agency_id = rs.agency_id AND rsd.route_id = rs.route_id AND rsd.direction_id = rs.direction_id
               GROUP BY rs.agency_id, rs.route_id
             ) sp ON sp.agency_id = ot.agency_id AND sp.route_id = ot.route_id
             ORDER BY ot.agency_id,
               CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
               r.short_name",
        )
        .bind(days)
        .bind(days)
        .fetch_all(&db.pool)
        .await?,

        Some(agency) => sqlx::query_as(
            "SELECT
               ot.agency_id,
               ot.route_id,
               r.short_name,
               r.long_name,
               ot.avg_on_time_pct,
               sp.speed_vs_scheduled_pct
             FROM (
               SELECT agency_id, route_id, ROUND(AVG(on_time_pct)::NUMERIC, 1)::FLOAT8 AS avg_on_time_pct
               FROM route_daily
               WHERE service_date >= (CURRENT_DATE - $1::INT * INTERVAL '1 day')::TEXT
                 AND agency_id = $3
               GROUP BY agency_id, route_id
             ) ot
             JOIN routes r ON r.agency_id = ot.agency_id AND r.route_id = ot.route_id
             LEFT JOIN (
               SELECT rs.agency_id, rs.route_id,
                 ROUND(AVG(
                   CASE WHEN rs.scheduled_speed_mps > 0 AND rsd.avg_actual IS NOT NULL
                     THEN (rs.scheduled_speed_mps - rsd.avg_actual) / rs.scheduled_speed_mps * 100.0
                     ELSE NULL END
                 )::NUMERIC, 1)::FLOAT8 AS speed_vs_scheduled_pct
               FROM route_speed rs
               LEFT JOIN (
                 SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) AS avg_actual
                 FROM route_speed_daily
                 WHERE service_date >= (CURRENT_DATE - $2::INT * INTERVAL '1 day')::TEXT
                 GROUP BY agency_id, route_id, direction_id
               ) rsd ON rsd.agency_id = rs.agency_id AND rsd.route_id = rs.route_id AND rsd.direction_id = rs.direction_id
               GROUP BY rs.agency_id, rs.route_id
             ) sp ON sp.agency_id = ot.agency_id AND sp.route_id = ot.route_id
             WHERE ot.agency_id = $3
             ORDER BY ot.agency_id,
               CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
               r.short_name",
        )
        .bind(days)
        .bind(days)
        .bind(agency.as_str())
        .fetch_all(&db.pool)
        .await?,
    };
    Ok(rows)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;

    #[tokio::test]
    async fn route_trend_returns_none_for_unknown_route() {
        let td = test_utils::setup().await;
        let db = td.db;
        let result = route_trend(&db, &AgencyId::from("0"), &RouteId::from("NONEXISTENT"), 30).await.unwrap();
        assert!(result.is_none());
    }

    #[tokio::test]
    async fn route_trend_returns_daily_points_with_on_time_data() {
        let td = test_utils::setup().await;
        let db = td.db;
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '45', 'PAPINEAU', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_daily
             (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('0', 'R1', '2026-01-01', 72.5, 120.0, 45, 50, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_daily
             (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('0', 'R1', '2026-01-02', 68.0, 145.0, 44, 50, '2026-01-02T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();

        let trend = route_trend(&db, &AgencyId::from("0"), &RouteId::from("R1"), 3650).await.unwrap().unwrap();

        assert_eq!(trend.route_id, "R1");
        assert_eq!(trend.short_name, "45");
        assert_eq!(trend.days.len(), 2);
        assert_eq!(trend.days[0].service_date, "2026-01-01");
        assert!((trend.days[0].on_time_pct.unwrap() - 72.5).abs() < 0.01);
    }

    #[tokio::test]
    async fn route_trend_includes_speed_when_available() {
        let td = test_utils::setup().await;
        let db = td.db;
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '45', 'PAPINEAU', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_daily
             (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('0', 'R1', '2026-01-01', 72.5, 120.0, 45, 50, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', '2026-01-01', 0, 5.5, 45, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();

        let trend = route_trend(&db, &AgencyId::from("0"), &RouteId::from("R1"), 3650).await.unwrap().unwrap();

        assert_eq!(trend.days.len(), 1);
        assert!((trend.days[0].actual_speed_mps.unwrap() - 5.5).abs() < 0.01);
    }

    #[test]
    fn speed_change_pct_returns_none_with_too_few_days() {
        let trend = RouteTrend {
            route_id: "R1".into(),
            short_name: "45".into(),
            long_name: "PAPINEAU".into(),
            days: vec![DailyTrendPoint {
                service_date: "2026-01-01".into(),
                on_time_pct: None,
                avg_delay_secs: None,
                actual_speed_mps: Some(5.0),
            }],
        };
        assert!(trend.speed_change_pct().is_none());
    }

    #[test]
    fn speed_change_pct_negative_when_route_slows_down() {
        // First 7 days: 6.0 m/s, last 7 days: 5.0 m/s → -16.7%
        let days: Vec<DailyTrendPoint> = (0..14)
            .map(|i| DailyTrendPoint {
                service_date: format!("2026-01-{:02}", i + 1),
                on_time_pct: None,
                avg_delay_secs: None,
                actual_speed_mps: Some(if i < 7 { 6.0 } else { 5.0 }),
            })
            .collect();
        let trend = RouteTrend {
            route_id: "R1".into(),
            short_name: "45".into(),
            long_name: "PAPINEAU".into(),
            days,
        };
        let pct = trend.speed_change_pct().unwrap();
        assert!((pct - (-16.667)).abs() < 0.1, "expected ~-16.7%, got {pct}");
    }

    #[test]
    fn speed_change_pct_positive_when_route_improves() {
        // First 7 days: 5.0 m/s, last 7 days: 6.0 m/s → +20%
        let days: Vec<DailyTrendPoint> = (0..14)
            .map(|i| DailyTrendPoint {
                service_date: format!("2026-01-{:02}", i + 1),
                on_time_pct: None,
                avg_delay_secs: None,
                actual_speed_mps: Some(if i < 7 { 5.0 } else { 6.0 }),
            })
            .collect();
        let trend = RouteTrend {
            route_id: "R1".into(),
            short_name: "45".into(),
            long_name: "PAPINEAU".into(),
            days,
        };
        let pct = trend.speed_change_pct().unwrap();
        assert!((pct - 20.0).abs() < 0.1, "expected ~20%, got {pct}");
    }

    // ── Benchmark tests ──────────────────────────────────────────────────────

    #[tokio::test]
    async fn load_benchmarks_returns_all_seeded_rows() {
        let td = test_utils::setup().await;
        let db = td.db;
        let benchmarks = load_benchmarks(&db).await.unwrap();
        assert_eq!(benchmarks.len(), 4);
        // Ordered ASC by on_time_pct: Helsinki first, Tokyo last.
        assert_eq!(benchmarks[0].city, "Helsinki");
        assert_eq!(benchmarks[3].city, "Tokyo");
        assert!((benchmarks[0].on_time_pct - 89.0).abs() < 0.01);
        assert!((benchmarks[3].on_time_pct - 96.0).abs() < 0.01);
        // Tie-break by city ASC: Singapore (92.0) before Zurich (92.0)
        assert_eq!(benchmarks[1].city, "Singapore");
        assert_eq!(benchmarks[2].city, "Zurich");
    }

    // ── ScorecardRoute tests ──────────────────────────────────────────────────

    fn make_scorecard_route(on_time: Option<f64>, speed: Option<f64>) -> ScorecardRoute {
        ScorecardRoute {
            agency_id: "0".into(),
            route_id: "R1".into(),
            short_name: "45".into(),
            long_name: "PAPINEAU".into(),
            avg_on_time_pct: on_time,
            speed_vs_scheduled_pct: speed,
        }
    }

    #[test]
    fn on_time_gap_vs_returns_positive_when_route_beats_benchmark() {
        let r = make_scorecard_route(Some(93.0), None);
        let gap = r.on_time_gap_vs(89.0).unwrap();
        assert!((gap - 4.0).abs() < 0.01);
    }

    #[test]
    fn on_time_gap_vs_returns_negative_when_route_below_benchmark() {
        let r = make_scorecard_route(Some(71.0), None);
        let gap = r.on_time_gap_vs(89.0).unwrap();
        assert!((gap - (-18.0)).abs() < 0.01);
    }

    #[test]
    fn on_time_gap_vs_returns_none_without_data() {
        let r = make_scorecard_route(None, None);
        assert!(r.on_time_gap_vs(89.0).is_none());
    }

    #[test]
    fn on_time_gap_display_positive_shows_plus_prefix() {
        let r = make_scorecard_route(Some(93.0), None);
        assert_eq!(r.on_time_gap_display(&89.0), "+4pp");
    }

    #[test]
    fn on_time_gap_display_negative_shows_minus_prefix() {
        let r = make_scorecard_route(Some(71.0), None);
        assert_eq!(r.on_time_gap_display(&89.0), "-18pp");
    }

    #[test]
    fn on_time_gap_display_no_data_shows_dash() {
        let r = make_scorecard_route(None, None);
        assert_eq!(r.on_time_gap_display(&89.0), "—");
    }

    #[test]
    fn status_label_world_class_at_or_above_ceiling() {
        let r = make_scorecard_route(Some(96.0), None);
        assert_eq!(r.status_label(&89.0, &96.0), "World class");
    }

    #[test]
    fn status_label_competitive_between_floor_and_ceiling() {
        let r = make_scorecard_route(Some(91.0), None);
        assert_eq!(r.status_label(&89.0, &96.0), "Competitive");
    }

    #[test]
    fn status_label_below_all_under_floor() {
        let r = make_scorecard_route(Some(71.0), None);
        assert_eq!(r.status_label(&89.0, &96.0), "Below all");
    }

    #[test]
    fn status_label_no_data_when_none() {
        let r = make_scorecard_route(None, None);
        assert_eq!(r.status_label(&89.0, &96.0), "No data");
    }

    #[test]
    fn speed_display_shows_slower_when_positive_deficit() {
        let r = make_scorecard_route(None, Some(12.0));
        assert_eq!(r.speed_display(), "12% slower");
    }

    #[test]
    fn speed_display_shows_on_pace_within_one_pct() {
        let r = make_scorecard_route(None, Some(0.5));
        assert_eq!(r.speed_display(), "On pace");
    }

    #[test]
    fn speed_display_shows_faster_when_negative_deficit() {
        let r = make_scorecard_route(None, Some(-3.0));
        assert_eq!(r.speed_display(), "3% faster");
    }

    #[test]
    fn speed_display_dash_without_data() {
        let r = make_scorecard_route(None, None);
        assert_eq!(r.speed_display(), "—");
    }

    #[test]
    fn speed_class_is_empty_string_without_data() {
        let r = make_scorecard_route(None, None);
        assert_eq!(r.speed_class(), "");
    }

    #[test]
    fn on_time_gap_class_is_gap_pos_when_route_beats_benchmark() {
        let r = make_scorecard_route(Some(93.0), None);
        assert_eq!(r.on_time_gap_class(&89.0), "gap-pos");
    }

    #[test]
    fn on_time_gap_class_is_gap_neg_when_route_below_benchmark() {
        let r = make_scorecard_route(Some(71.0), None);
        assert_eq!(r.on_time_gap_class(&89.0), "gap-neg");
    }

    #[test]
    fn on_time_gap_class_is_empty_without_data() {
        let r = make_scorecard_route(None, None);
        assert_eq!(r.on_time_gap_class(&89.0), "");
    }

    // ── ScorecardRoute::badge_variant tests ──────────────────────────────────────

    #[test]
    fn scorecard_badge_variant_good_at_or_above_ceiling() {
        let r = make_scorecard_route(Some(96.0), None);
        assert_eq!(r.badge_variant(&89.0, &96.0), "good");
    }

    #[test]
    fn scorecard_badge_variant_mixed_between_floor_and_ceiling() {
        let r = make_scorecard_route(Some(91.0), None);
        assert_eq!(r.badge_variant(&89.0, &96.0), "mixed");
    }

    #[test]
    fn scorecard_badge_variant_bad_under_floor() {
        let r = make_scorecard_route(Some(71.0), None);
        assert_eq!(r.badge_variant(&89.0, &96.0), "bad");
    }

    #[test]
    fn scorecard_badge_variant_neutral_when_no_data() {
        let r = make_scorecard_route(None, None);
        assert_eq!(r.badge_variant(&89.0, &96.0), "neutral");
    }

    #[test]
    fn speed_class_is_slower_when_large_positive_deficit() {
        let r = make_scorecard_route(None, Some(12.0));
        assert_eq!(r.speed_class(), "slower");
    }

    #[test]
    fn speed_class_is_faster_when_negative_deficit() {
        let r = make_scorecard_route(None, Some(-3.0));
        assert_eq!(r.speed_class(), "faster");
    }

    #[test]
    fn speed_class_is_onpace_within_one_pct() {
        let r = make_scorecard_route(None, Some(0.5));
        assert_eq!(r.speed_class(), "onpace");
    }

    #[tokio::test]
    async fn scorecard_routes_returns_per_route_summary() {
        let td = test_utils::setup().await;
        let db = td.db;
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '45', 'PAPINEAU', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_daily
             (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('0', 'R1', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 72.5, 120.0, 45, 50, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();

        let routes = scorecard_routes(&db, 7, None).await.unwrap();

        assert_eq!(routes.len(), 1);
        assert_eq!(routes[0].route_id, "R1");
        assert_eq!(routes[0].short_name, "45");
        let pct = routes[0].avg_on_time_pct.unwrap();
        assert!((pct - 72.5).abs() < 0.1);
    }

    #[tokio::test]
    async fn scorecard_routes_includes_speed_deficit_when_available() {
        let td = test_utils::setup().await;
        let db = td.db;
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '45', 'PAPINEAU', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_daily
             (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('0', 'R1', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 72.5, 120.0, 45, 50, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();
        // scheduled: 10.0 m/s, actual: 8.0 m/s → deficit = 20%
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('0', 'R1', 0, 10.0, 5, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 0, 8.0, 5, '2026-01-01T00:00:00Z')",
        ).execute(&db.pool).await.unwrap();

        let routes = scorecard_routes(&db, 7, None).await.unwrap();

        assert_eq!(routes.len(), 1);
        let deficit = routes[0].speed_vs_scheduled_pct.unwrap();
        assert!((deficit - 20.0).abs() < 0.5, "expected ~20%, got {deficit}");
    }

    #[tokio::test]
    async fn scorecard_routes_speed_is_none_when_no_speed_data() {
        let td = test_utils::setup().await;
        let db = td.db;
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '45', 'PAPINEAU', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_daily
             (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('0', 'R1', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 72.5, 120.0, 45, 50, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();

        let routes = scorecard_routes(&db, 7, None).await.unwrap();

        assert_eq!(routes.len(), 1);
        assert!(routes[0].speed_vs_scheduled_pct.is_none());
    }

    #[tokio::test]
    async fn scorecard_routes_filters_by_agency() {
        let td = test_utils::setup().await;
        let db = td.db;
        sqlx::query("INSERT INTO routes VALUES ('stm', 'R1', '15', 'Papineau', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO routes VALUES ('rtl', 'R2', '10', 'Longueuil', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_daily (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('stm', 'R1', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 80.0, 60.0, 10, 12, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_daily (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('rtl', 'R2', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 70.0, 90.0, 8, 10, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();

        let all = scorecard_routes(&db, 30, None).await.unwrap();
        assert_eq!(all.len(), 2);

        let stm = scorecard_routes(&db, 30, Some(&AgencyId::from("stm"))).await.unwrap();
        assert_eq!(stm.len(), 1);
        assert_eq!(stm[0].agency_id, "stm");

        let rtl = scorecard_routes(&db, 30, Some(&AgencyId::from("rtl"))).await.unwrap();
        assert_eq!(rtl.len(), 1);
        assert_eq!(rtl[0].agency_id, "rtl");
    }

}

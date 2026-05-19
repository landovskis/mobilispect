use anyhow::Result;
use serde::Serialize;

use crate::db::Database;
use crate::ids::{AgencyId, RouteId};

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
    use crate::ids::AgencyId;

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
    async fn load_benchmarks_returns_all_seeded_rows() {
        let td = test_utils::setup().await;
        let db = td.db;
        let benchmarks = load_benchmarks(&db).await.unwrap();
        assert_eq!(benchmarks.len(), 4);
        assert_eq!(benchmarks[0].city, "Helsinki");
        assert_eq!(benchmarks[3].city, "Tokyo");
        assert!((benchmarks[0].on_time_pct - 89.0).abs() < 0.01);
        assert!((benchmarks[3].on_time_pct - 96.0).abs() < 0.01);
        assert_eq!(benchmarks[1].city, "Singapore");
        assert_eq!(benchmarks[2].city, "Zurich");
    }

    #[tokio::test]
    async fn scorecard_routes_returns_per_route_summary() {
        let td = test_utils::setup().await;
        let db = td.db;
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '45', 'PAPINEAU', 3)")
            .execute(&db.pool).await.unwrap();
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
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_daily
             (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('0', 'R1', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 72.5, 120.0, 45, 50, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', 0, 10.0, 5, '2026-01-01T00:00:00Z')",
        ).execute(&db.pool).await.unwrap();
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
            .execute(&db.pool).await.unwrap();
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
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO routes VALUES ('rtl', 'R2', '10', 'Longueuil', 3)")
            .execute(&db.pool).await.unwrap();
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

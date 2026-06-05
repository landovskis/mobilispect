use anyhow::Result;
use serde::Serialize;

use crate::db::Database;
use crate::ids::{FeedId, RouteId};

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

/// Fetch per-day trend data for a single route (last N days).
/// Returns None if the route doesn't exist or has no computed data.
pub async fn route_trend(
    db: &Database,
    feed_id: FeedId,
    route_id: &RouteId,
    days: i64,
) -> Result<Option<RouteTrend>> {
    let route: Option<(String, String)> =
        sqlx::query_as("SELECT short_name, long_name FROM routes WHERE onestop_id = $1")
            .bind(route_id.as_str())
            .fetch_optional(&db.pool)
            .await?;

    let (short_name, long_name) = match route {
        Some(r) => r,
        None => return Ok(None),
    };

    // Daily points: on-time and speed data from route_daily_stats.
    // Average across variants per day to get a single daily point.
    #[allow(clippy::type_complexity)]
    let rows: Vec<(chrono::NaiveDate, Option<f64>, Option<f64>, Option<f64>)> = sqlx::query_as(
        "SELECT
           rds.service_date,
           AVG(rds.on_time_stops::float8 / NULLIF(rds.total_stops, 0) * 100.0) AS on_time_pct,
           AVG(rds.avg_delay_secs) AS avg_delay_secs,
           AVG(rds.actual_speed_mps) AS actual_speed_mps
         FROM route_daily_stats rds
         WHERE rds.feed_id = $1
           AND rds.route_id = $2
           AND rds.service_date >= CURRENT_DATE - ($3::INT * INTERVAL '1 day')
         GROUP BY rds.service_date
         ORDER BY rds.service_date",
    )
    .bind(feed_id.as_i64())
    .bind(route_id.as_str())
    .bind(days)
    .fetch_all(&db.pool)
    .await?;

    if rows.is_empty() {
        return Ok(None);
    }

    let trend_days = rows
        .into_iter()
        .map(
            |(service_date, on_time_pct, avg_delay_secs, actual_speed_mps)| DailyTrendPoint {
                service_date: service_date.to_string(),
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;
    use crate::ids::FeedId;

    #[tokio::test]
    async fn route_trend_returns_none_for_unknown_route() {
        let td = test_utils::setup().await;
        let db = td.db;
        let result = route_trend(&db, FeedId::from(1i64), &RouteId::from("NONEXISTENT"), 30)
            .await
            .unwrap();
        assert!(result.is_none());
    }

    #[tokio::test]
    async fn route_trend_returns_daily_points_with_on_time_data() {
        let td = test_utils::setup().await;
        let db = td.db;

        // Insert required feed row
        sqlx::query("INSERT INTO feeds (id, onestop_id, name) VALUES (1, 'f-test', 'Test')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('r-test-R1', 'test', '45', 'PAPINEAU', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_daily_stats
             (feed_id, route_id, service_date, variant_id, on_time_stops, total_stops, skipped_stops, trips_run, trips_total, avg_delay_secs, computed_at)
             VALUES (1, 'r-test-R1', '2026-01-01', 'var1', 72, 100, 0, 45, 50, 120.0, NOW())",
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_daily_stats
             (feed_id, route_id, service_date, variant_id, on_time_stops, total_stops, skipped_stops, trips_run, trips_total, avg_delay_secs, computed_at)
             VALUES (1, 'r-test-R1', '2026-01-02', 'var1', 68, 100, 0, 44, 50, 145.0, NOW())",
        ).execute(&db.pool).await.unwrap();

        let trend = route_trend(&db, FeedId::from(1i64), &RouteId::from("r-test-R1"), 3650)
            .await
            .unwrap()
            .unwrap();

        assert_eq!(trend.route_id, "r-test-R1");
        assert_eq!(trend.short_name, "45");
        assert_eq!(trend.days.len(), 2);
        assert_eq!(trend.days[0].service_date, "2026-01-01");
        // on_time_pct = 72 / 100 * 100 = 72.0
        assert!((trend.days[0].on_time_pct.unwrap() - 72.0).abs() < 0.01);
    }

    #[tokio::test]
    async fn route_trend_includes_speed_when_available() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO feeds (id, onestop_id, name) VALUES (1, 'f-test', 'Test')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('r-test-R1', 'test', '45', 'PAPINEAU', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_daily_stats
             (feed_id, route_id, service_date, variant_id, on_time_stops, total_stops, skipped_stops, trips_run, trips_total, actual_speed_mps, computed_at)
             VALUES (1, 'r-test-R1', '2026-01-01', 'var1', 72, 100, 0, 45, 50, 5.5, NOW())",
        ).execute(&db.pool).await.unwrap();

        let trend = route_trend(&db, FeedId::from(1i64), &RouteId::from("r-test-R1"), 3650)
            .await
            .unwrap()
            .unwrap();

        assert_eq!(trend.days.len(), 1);
        assert!((trend.days[0].actual_speed_mps.unwrap() - 5.5).abs() < 0.01);
    }

    #[test]
    fn speed_change_pct_returns_none_with_too_few_days() {
        let trend = RouteTrend {
            route_id: RouteId::from("R1"),
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
            route_id: RouteId::from("R1"),
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
            route_id: RouteId::from("R1"),
            short_name: "45".into(),
            long_name: "PAPINEAU".into(),
            days,
        };
        let pct = trend.speed_change_pct().unwrap();
        assert!((pct - 20.0).abs() < 0.1, "expected ~20%, got {pct}");
    }

    fn make_trend_with_speeds(first: f64, last: f64) -> RouteTrend {
        let days = (0..14)
            .map(|i| DailyTrendPoint {
                service_date: format!("2026-01-{:02}", i + 1),
                on_time_pct: None,
                avg_delay_secs: None,
                actual_speed_mps: Some(if i < 7 { first } else { last }),
            })
            .collect();
        RouteTrend {
            route_id: RouteId::from("R1"),
            short_name: "45".into(),
            long_name: "PAPINEAU".into(),
            days,
        }
    }

    #[test]
    fn speed_change_display_insufficient_data_when_too_few_days() {
        let trend = RouteTrend {
            route_id: RouteId::from("R1"),
            short_name: "45".into(),
            long_name: "PAPINEAU".into(),
            days: vec![],
        };
        assert_eq!(trend.speed_change_display(), "Insufficient data");
    }

    #[test]
    fn speed_change_display_faster_when_route_speeds_up() {
        // 5.0 → 6.0 m/s = +20%
        let trend = make_trend_with_speeds(5.0, 6.0);
        assert_eq!(trend.speed_change_display(), "+20.0% faster");
    }

    #[test]
    fn speed_change_display_slower_when_route_slows_down() {
        // 6.0 → 5.0 m/s = -16.7%
        let trend = make_trend_with_speeds(6.0, 5.0);
        assert!(trend.speed_change_display().contains("slower"));
    }

    #[test]
    fn speed_change_display_stable_when_no_significant_change() {
        let trend = make_trend_with_speeds(5.0, 5.0);
        assert_eq!(trend.speed_change_display(), "Stable");
    }

    #[test]
    fn speed_change_class_decline_when_route_slows_down() {
        let trend = make_trend_with_speeds(6.0, 5.0);
        assert_eq!(trend.speed_change_class(), "decline");
    }

    #[test]
    fn speed_change_class_improve_when_route_speeds_up() {
        let trend = make_trend_with_speeds(5.0, 6.0);
        assert_eq!(trend.speed_change_class(), "improve");
    }

    #[test]
    fn speed_change_class_empty_when_stable() {
        let trend = make_trend_with_speeds(5.0, 5.0);
        assert_eq!(trend.speed_change_class(), "");
    }
}

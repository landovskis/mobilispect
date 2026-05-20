use anyhow::Result;
use serde::Serialize;

use crate::db::Database;
use crate::ids::{AgencyId, RouteId};

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
    agency_id: &AgencyId,
    route_id: &RouteId,
    days: i64,
) -> Result<Option<RouteTrend>> {
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;
    use crate::ids::AgencyId;

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
}

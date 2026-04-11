use anyhow::Result;
use chrono::{NaiveDate, Utc};
use serde::Serialize;

use crate::config::AgencyConfig;
use crate::db::Database;

#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct RouteSpeedSummary {
    pub agency_id: String,
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub direction_id: i64,
    pub scheduled_speed_mps: f64,
    pub trip_count: i64,
    /// Average speed from vehicles observed in the last hour (m/s). None if no recent data.
    pub live_speed_mps: Option<f64>,
    /// Average actual speed over the last 7 days from stop arrival times (m/s). None if no history.
    pub actual_speed_mps: Option<f64>,
}

impl RouteSpeedSummary {
    /// Scheduled speed in km/h, rounded to one decimal place.
    pub fn scheduled_speed_kmh(&self) -> f64 {
        (self.scheduled_speed_mps * 3.6 * 10.0).round() / 10.0
    }

    pub fn actual_speed_display(&self) -> String {
        match self.actual_speed_mps {
            Some(s) => format!("{:.1} km/h", s * 3.6),
            None => "—".to_string(),
        }
    }

    pub fn live_speed_display(&self) -> String {
        match self.live_speed_mps {
            Some(s) => format!("{:.1} km/h", s * 3.6),
            None => "—".to_string(),
        }
    }

    /// Percentage by which actual speed lags scheduled speed.
    /// Positive = slower than scheduled, negative = faster.
    /// None if no actual speed data.
    pub fn speed_deficit_pct(&self) -> Option<f64> {
        let actual = self.actual_speed_mps?;
        if self.scheduled_speed_mps == 0.0 {
            return None;
        }
        Some((self.scheduled_speed_mps - actual) / self.scheduled_speed_mps * 100.0)
    }

    /// "—", "+X% faster", or "X% slower" relative to schedule.
    pub fn speed_vs_scheduled_display(&self) -> String {
        match self.speed_deficit_pct() {
            None => "—".to_string(),
            Some(d) if d > 1.0 => format!("{d:.0}% slower"),
            Some(d) if d < -1.0 => format!("{:.0}% faster", d.abs()),
            Some(_) => "On pace".to_string(),
        }
    }

    pub fn direction_label(&self) -> &'static str {
        if self.direction_id == 0 { "Outbound" } else { "Inbound" }
    }

    /// CSS class for the vs-schedule cell: "slower", "faster", or "onpace".
    pub fn speed_class(&self) -> &'static str {
        match self.speed_deficit_pct() {
            Some(d) if d > 1.0 => "slower",
            Some(d) if d < -1.0 => "faster",
            Some(_) => "onpace",
            None => "onpace",
        }
    }
}

/// Haversine distance between two lat/lon points, in meters.
fn haversine_meters(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    const R: f64 = 6_371_000.0;
    let dlat = (lat2 - lat1).to_radians();
    let dlon = (lon2 - lon1).to_radians();
    let lat1 = lat1.to_radians();
    let lat2 = lat2.to_radians();
    let a = (dlat / 2.0).sin().powi(2) + lat1.cos() * lat2.cos() * (dlon / 2.0).sin().powi(2);
    let c = 2.0 * a.sqrt().asin();
    R * c
}

/// Parse "HH:MM:SS" (HH may be ≥ 24 for post-midnight service) into total seconds.
fn parse_time_secs(s: &str) -> Option<u32> {
    let mut parts = s.splitn(3, ':');
    let h: u32 = parts.next()?.parse().ok()?;
    let m: u32 = parts.next()?.parse().ok()?;
    let sec: u32 = parts.next()?.parse().ok()?;
    Some(h * 3600 + m * 60 + sec)
}

/// Compute scheduled average speed (m/s) for every route+direction and store in `route_speed`.
/// Reads only static GTFS tables — safe to call on startup after GTFS load.
pub async fn compute_route_speed(db: &Database, agency: &AgencyConfig) -> Result<()> {
    let now = Utc::now().to_rfc3339();
    let agency_id = &agency.slug;

    // All distinct route + direction combinations that have trips with stops.
    let combos: Vec<(String, i64)> = sqlx::query_as(
        "SELECT DISTINCT t.route_id, COALESCE(t.direction_id, 0) as direction_id
         FROM trips t
         JOIN scheduled_stops ss ON ss.trip_id = t.trip_id AND ss.agency_id = t.agency_id
         WHERE t.agency_id = $1
         GROUP BY t.route_id, t.direction_id
         HAVING COUNT(ss.stop_sequence) >= 2",
    )
    .bind(agency_id)
    .fetch_all(&db.pool)
    .await?;

    for (route_id, direction_id) in &combos {
        let trips: Vec<(String,)> = sqlx::query_as(
            "SELECT trip_id FROM trips WHERE agency_id = $1 AND route_id = $2 AND COALESCE(direction_id, 0) = $3",
        )
        .bind(agency_id)
        .bind(route_id)
        .bind(direction_id)
        .fetch_all(&db.pool)
        .await?;

        let mut trip_speeds: Vec<f64> = Vec::new();

        for (trip_id,) in &trips {
            let stops: Vec<(f64, f64, String)> = sqlx::query_as(
                "SELECT s.stop_lat, s.stop_lon, ss.arrival_time
                 FROM scheduled_stops ss
                 JOIN stops s ON s.stop_id = ss.stop_id AND s.agency_id = ss.agency_id
                 WHERE ss.agency_id = $1 AND ss.trip_id = $2
                 ORDER BY ss.stop_sequence",
            )
            .bind(agency_id)
            .bind(trip_id)
            .fetch_all(&db.pool)
            .await?;

            if stops.len() < 2 {
                continue;
            }

            // Total distance: sum of consecutive stop-to-stop haversine distances.
            let total_distance_m: f64 = stops
                .windows(2)
                .map(|w| haversine_meters(w[0].0, w[0].1, w[1].0, w[1].1))
                .sum();

            // Scheduled duration: last arrival minus first arrival.
            let first_secs = parse_time_secs(&stops.first().unwrap().2);
            let last_secs = parse_time_secs(&stops.last().unwrap().2);
            let duration_secs = match (first_secs, last_secs) {
                (Some(f), Some(l)) if l > f => (l - f) as f64,
                _ => continue,
            };

            if total_distance_m > 0.0 && duration_secs > 0.0 {
                trip_speeds.push(total_distance_m / duration_secs);
            }
        }

        if trip_speeds.is_empty() {
            continue;
        }

        let avg_speed = trip_speeds.iter().sum::<f64>() / trip_speeds.len() as f64;
        let trip_count = trip_speeds.len() as i64;

        sqlx::query!(
            "INSERT INTO route_speed
             (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6)
             ON CONFLICT (agency_id, route_id, direction_id) DO UPDATE SET
               scheduled_speed_mps = EXCLUDED.scheduled_speed_mps,
               trip_count = EXCLUDED.trip_count,
               computed_at = EXCLUDED.computed_at",
            agency_id,
            route_id,
            direction_id,
            avg_speed,
            trip_count,
            now,
        )
        .execute(&db.pool)
        .await?;
    }

    Ok(())
}

/// Compute actual average speed per route+direction for a service date from stop arrival times.
/// Uses `stop_time_events.arrival_time_unix` to determine actual travel time per trip.
pub async fn compute_route_speed_daily(db: &Database, agency: &AgencyConfig, service_date: NaiveDate) -> Result<()> {
    let date_str = service_date.to_string();
    let now = Utc::now().to_rfc3339();
    let agency_id = &agency.slug;

    // All distinct route + direction combos with stop time events on this date.
    let combos: Vec<(String, i64)> = sqlx::query_as(
        "SELECT DISTINCT t.route_id, COALESCE(t.direction_id, 0) as direction_id
         FROM stop_time_events ste
         JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
         WHERE ste.agency_id = $1 AND ste.observed_at::TIMESTAMPTZ::DATE = $2::DATE
           AND ste.arrival_time_unix IS NOT NULL",
    )
    .bind(agency_id)
    .bind(&date_str)
    .fetch_all(&db.pool)
    .await?;

    for (route_id, direction_id) in &combos {
        let trips: Vec<(String,)> = sqlx::query_as(
            "SELECT DISTINCT ste.trip_id
             FROM stop_time_events ste
             JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
             WHERE t.agency_id = $1 AND t.route_id = $2
               AND COALESCE(t.direction_id, 0) = $3
               AND ste.observed_at::TIMESTAMPTZ::DATE = $4::DATE
               AND ste.arrival_time_unix IS NOT NULL",
        )
        .bind(agency_id)
        .bind(route_id)
        .bind(direction_id)
        .bind(&date_str)
        .fetch_all(&db.pool)
        .await?;

        let mut trip_speeds: Vec<f64> = Vec::new();

        for (trip_id,) in &trips {
            // Get last observed arrival_time_unix per stop (closest to actual arrival).
            let stops: Vec<(f64, f64, i64)> = sqlx::query_as(
                "SELECT s.stop_lat, s.stop_lon, MAX(ste.arrival_time_unix) as arrival_time_unix
                 FROM stop_time_events ste
                 JOIN scheduled_stops ss
                   ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id AND ss.agency_id = ste.agency_id
                 JOIN stops s ON s.stop_id = ste.stop_id AND s.agency_id = ste.agency_id
                 WHERE ste.agency_id = $1 AND ste.trip_id = $2
                   AND ste.observed_at::TIMESTAMPTZ::DATE = $3::DATE
                   AND ste.arrival_time_unix IS NOT NULL
                 GROUP BY ss.stop_sequence, s.stop_lat, s.stop_lon
                 ORDER BY ss.stop_sequence",
            )
            .bind(agency_id)
            .bind(trip_id)
            .bind(&date_str)
            .fetch_all(&db.pool)
            .await?;

            if stops.len() < 2 {
                continue;
            }

            let total_distance_m: f64 = stops
                .windows(2)
                .map(|w| haversine_meters(w[0].0, w[0].1, w[1].0, w[1].1))
                .sum();

            let first_unix = stops.first().unwrap().2;
            let last_unix = stops.last().unwrap().2;
            let duration_secs = (last_unix - first_unix) as f64;

            if total_distance_m > 0.0 && duration_secs > 0.0 {
                trip_speeds.push(total_distance_m / duration_secs);
            }
        }

        if trip_speeds.is_empty() {
            continue;
        }

        let avg_speed = trip_speeds.iter().sum::<f64>() / trip_speeds.len() as f64;
        let trip_count = trip_speeds.len() as i64;

        sqlx::query!(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             ON CONFLICT (agency_id, route_id, service_date, direction_id) DO UPDATE SET
               actual_speed_mps = EXCLUDED.actual_speed_mps,
               trip_count = EXCLUDED.trip_count,
               computed_at = EXCLUDED.computed_at",
            agency_id,
            route_id,
            date_str,
            direction_id,
            avg_speed,
            trip_count,
            now,
        )
        .execute(&db.pool)
        .await?;
    }

    Ok(())
}

/// Fetch scheduled, live, and historical actual speed for all routes.
/// Live speed: average from vehicle positions in the last hour.
/// Actual speed: average from route_speed_daily over the last 7 days.
/// If `agency_filter` is Some, only returns routes for that agency.
pub async fn route_speed_summary(db: &Database, agency_filter: Option<&str>) -> Result<Vec<RouteSpeedSummary>> {
    let rows: Vec<RouteSpeedSummary> = match agency_filter {
        None => sqlx::query_as(
            "SELECT
               rs.agency_id,
               rs.route_id,
               r.short_name,
               r.long_name,
               rs.direction_id,
               rs.scheduled_speed_mps,
               rs.trip_count,
               live.avg_live_speed as live_speed_mps,
               hist.avg_actual_speed as actual_speed_mps
             FROM route_speed rs
             JOIN routes r ON rs.agency_id = r.agency_id AND rs.route_id = r.route_id
             LEFT JOIN (
               SELECT t.agency_id, t.route_id, AVG(vp.speed) as avg_live_speed
               FROM vehicle_positions vp
               JOIN trips t ON t.trip_id = vp.trip_id AND t.agency_id = vp.agency_id
               WHERE vp.speed IS NOT NULL
                 AND vp.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '1 hour'
               GROUP BY t.agency_id, t.route_id
             ) live ON live.agency_id = rs.agency_id AND live.route_id = rs.route_id
             LEFT JOIN (
               SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) as avg_actual_speed
               FROM route_speed_daily
               WHERE service_date >= (CURRENT_DATE - INTERVAL '7 days')::TEXT
               GROUP BY agency_id, route_id, direction_id
             ) hist ON hist.agency_id = rs.agency_id AND hist.route_id = rs.route_id AND hist.direction_id = rs.direction_id
             ORDER BY rs.agency_id, CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST, r.short_name, rs.direction_id",
        )
        .fetch_all(&db.pool)
        .await?,

        Some(agency) => sqlx::query_as(
            "SELECT
               rs.agency_id,
               rs.route_id,
               r.short_name,
               r.long_name,
               rs.direction_id,
               rs.scheduled_speed_mps,
               rs.trip_count,
               live.avg_live_speed as live_speed_mps,
               hist.avg_actual_speed as actual_speed_mps
             FROM route_speed rs
             JOIN routes r ON rs.agency_id = r.agency_id AND rs.route_id = r.route_id
             LEFT JOIN (
               SELECT t.agency_id, t.route_id, AVG(vp.speed) as avg_live_speed
               FROM vehicle_positions vp
               JOIN trips t ON t.trip_id = vp.trip_id AND t.agency_id = vp.agency_id
               WHERE vp.speed IS NOT NULL
                 AND vp.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '1 hour'
               GROUP BY t.agency_id, t.route_id
             ) live ON live.agency_id = rs.agency_id AND live.route_id = rs.route_id
             LEFT JOIN (
               SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) as avg_actual_speed
               FROM route_speed_daily
               WHERE service_date >= (CURRENT_DATE - INTERVAL '7 days')::TEXT
               GROUP BY agency_id, route_id, direction_id
             ) hist ON hist.agency_id = rs.agency_id AND hist.route_id = rs.route_id AND hist.direction_id = rs.direction_id
             WHERE rs.agency_id = $1
             ORDER BY rs.agency_id, CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST, r.short_name, rs.direction_id",
        )
        .bind(agency)
        .fetch_all(&db.pool)
        .await?,
    };
    Ok(rows)
}

/// Compute actual average speed per route+direction for each UTC hour, from stop arrival times
/// observed in the last 4 hours. Called after every GTFS-RT poll so the data stays fresh.
pub async fn compute_route_speed_hourly(db: &Database, agency: &AgencyConfig) -> Result<()> {
    let now = Utc::now().to_rfc3339();
    let agency_id = &agency.slug;

    let combos: Vec<(String, i64)> = sqlx::query_as(
        "SELECT DISTINCT t.route_id, COALESCE(t.direction_id, 0) as direction_id
         FROM stop_time_events ste
         JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
         WHERE ste.agency_id = $1 AND ste.arrival_time_unix IS NOT NULL
           AND ste.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '4 hours'",
    )
    .bind(agency_id)
    .fetch_all(&db.pool)
    .await?;

    for (route_id, direction_id) in &combos {
        let trips: Vec<(String,)> = sqlx::query_as(
            "SELECT DISTINCT ste.trip_id
             FROM stop_time_events ste
             JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
             WHERE t.agency_id = $1 AND t.route_id = $2
               AND COALESCE(t.direction_id, 0) = $3
               AND ste.arrival_time_unix IS NOT NULL
               AND ste.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '4 hours'",
        )
        .bind(agency_id)
        .bind(route_id)
        .bind(direction_id)
        .fetch_all(&db.pool)
        .await?;

        // Accumulate per-hour speed samples: hour_utc -> Vec<speed_mps>
        let mut hour_speeds: std::collections::HashMap<String, Vec<f64>> =
            std::collections::HashMap::new();

        for (trip_id,) in &trips {
            let stops: Vec<(f64, f64, i64)> = sqlx::query_as(
                "SELECT s.stop_lat, s.stop_lon, MAX(ste.arrival_time_unix) as arrival_time_unix
                 FROM stop_time_events ste
                 JOIN scheduled_stops ss
                   ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id AND ss.agency_id = ste.agency_id
                 JOIN stops s ON s.stop_id = ste.stop_id AND s.agency_id = ste.agency_id
                 WHERE ste.agency_id = $1 AND ste.trip_id = $2
                   AND ste.arrival_time_unix IS NOT NULL
                 GROUP BY ss.stop_sequence, s.stop_lat, s.stop_lon
                 ORDER BY ss.stop_sequence",
            )
            .bind(agency_id)
            .bind(trip_id)
            .fetch_all(&db.pool)
            .await?;

            if stops.len() < 2 {
                continue;
            }

            let total_distance_m: f64 = stops
                .windows(2)
                .map(|w| haversine_meters(w[0].0, w[0].1, w[1].0, w[1].1))
                .sum();

            let first_unix = stops.first().unwrap().2;
            let last_unix = stops.last().unwrap().2;
            let duration_secs = (last_unix - first_unix) as f64;

            if total_distance_m > 0.0 && duration_secs > 0.0 {
                let hour_utc = chrono::DateTime::from_timestamp(first_unix, 0)
                    .map(|dt: chrono::DateTime<Utc>| dt.format("%Y-%m-%d %H").to_string())
                    .unwrap_or_else(|| "1970-01-01 00".to_string());
                hour_speeds
                    .entry(hour_utc)
                    .or_default()
                    .push(total_distance_m / duration_secs);
            }
        }

        for (hour_utc, speeds) in &hour_speeds {
            let avg_speed = speeds.iter().sum::<f64>() / speeds.len() as f64;
            let trip_count = speeds.len() as i64;
            sqlx::query(
                "INSERT INTO route_speed_hourly
                 (agency_id, route_id, direction_id, hour_utc, actual_speed_mps, trip_count, computed_at)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)
                 ON CONFLICT (agency_id, route_id, direction_id, hour_utc) DO UPDATE SET
                   actual_speed_mps = EXCLUDED.actual_speed_mps,
                   trip_count = EXCLUDED.trip_count,
                   computed_at = EXCLUDED.computed_at",
            )
            .bind(agency_id.as_str())
            .bind(route_id.as_str())
            .bind(direction_id)
            .bind(hour_utc.as_str())
            .bind(avg_speed)
            .bind(trip_count)
            .bind(now.as_str())
            .execute(&db.pool)
            .await?;
        }
    }

    Ok(())
}

/// Per-route, per-direction average speed broken down by day type (weekday / Saturday / Sunday).
/// Averaged over the last 90 days of `route_speed_hourly` data.
#[derive(Debug, sqlx::FromRow)]
pub struct RouteSpeedDayType {
    pub agency_id: String,
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub direction_id: i64,
    pub weekday_speed_mps: Option<f64>,
    pub saturday_speed_mps: Option<f64>,
    pub sunday_speed_mps: Option<f64>,
}

pub async fn route_speed_by_day_type(
    db: &Database,
    agency_filter: Option<&str>,
) -> Result<Vec<RouteSpeedDayType>> {
    let rows = match agency_filter {
        None => sqlx::query_as(
            "SELECT
               rs.agency_id,
               rs.route_id,
               r.short_name,
               r.long_name,
               rs.direction_id,
               AVG(CASE WHEN EXTRACT(DOW FROM SUBSTRING(rsh.hour_utc, 1, 10)::DATE)::INT IN (1,2,3,4,5) THEN rsh.actual_speed_mps END) as weekday_speed_mps,
               AVG(CASE WHEN EXTRACT(DOW FROM SUBSTRING(rsh.hour_utc, 1, 10)::DATE)::INT = 6 THEN rsh.actual_speed_mps END) as saturday_speed_mps,
               AVG(CASE WHEN EXTRACT(DOW FROM SUBSTRING(rsh.hour_utc, 1, 10)::DATE)::INT = 0 THEN rsh.actual_speed_mps END) as sunday_speed_mps
             FROM route_speed rs
             JOIN routes r ON r.agency_id = rs.agency_id AND r.route_id = rs.route_id
             LEFT JOIN route_speed_hourly rsh
               ON rsh.agency_id = rs.agency_id AND rsh.route_id = rs.route_id AND rsh.direction_id = rs.direction_id
               AND rsh.hour_utc >= TO_CHAR(NOW() - INTERVAL '90 days', 'YYYY-MM-DD HH24')
             GROUP BY rs.agency_id, rs.route_id, rs.direction_id
             ORDER BY rs.agency_id, CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST, r.short_name, rs.direction_id",
        )
        .fetch_all(&db.pool)
        .await?,

        Some(agency) => sqlx::query_as(
            "SELECT
               rs.agency_id,
               rs.route_id,
               r.short_name,
               r.long_name,
               rs.direction_id,
               AVG(CASE WHEN EXTRACT(DOW FROM SUBSTRING(rsh.hour_utc, 1, 10)::DATE)::INT IN (1,2,3,4,5) THEN rsh.actual_speed_mps END) as weekday_speed_mps,
               AVG(CASE WHEN EXTRACT(DOW FROM SUBSTRING(rsh.hour_utc, 1, 10)::DATE)::INT = 6 THEN rsh.actual_speed_mps END) as saturday_speed_mps,
               AVG(CASE WHEN EXTRACT(DOW FROM SUBSTRING(rsh.hour_utc, 1, 10)::DATE)::INT = 0 THEN rsh.actual_speed_mps END) as sunday_speed_mps
             FROM route_speed rs
             JOIN routes r ON r.agency_id = rs.agency_id AND r.route_id = rs.route_id
             LEFT JOIN route_speed_hourly rsh
               ON rsh.agency_id = rs.agency_id AND rsh.route_id = rs.route_id AND rsh.direction_id = rs.direction_id
               AND rsh.hour_utc >= TO_CHAR(NOW() - INTERVAL '90 days', 'YYYY-MM-DD HH24')
             WHERE rs.agency_id = $1
             GROUP BY rs.agency_id, rs.route_id, rs.direction_id
             ORDER BY rs.agency_id, CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST, r.short_name, rs.direction_id",
        )
        .bind(agency)
        .fetch_all(&db.pool)
        .await?,
    };
    Ok(rows)
}

#[cfg(test)]
mod tests {
    use super::*;
    use sqlx::PgPool;
    use crate::config::AgencyConfig;

    fn test_agency() -> AgencyConfig {
        AgencyConfig {
            slug: "test".to_string(),
            name: "Test Agency".to_string(),
            gtfs_static_url: String::new(),
            gtfs_rt_vehicle_positions_url: String::new(),
            gtfs_rt_trip_updates_url: String::new(),
            gtfs_api_key: None,
            agency_utc_offset: "-04:00".to_string(),
        }
    }

    #[test]
    fn haversine_equator_one_degree_longitude() {
        // At the equator, 1 degree of longitude ≈ 111,195 m.
        let d = haversine_meters(0.0, 0.0, 0.0, 1.0);
        assert!((d - 111_195.0).abs() < 100.0, "expected ~111195 m, got {d}");
    }

    #[test]
    fn haversine_same_point_is_zero() {
        let d = haversine_meters(45.5, -73.5, 45.5, -73.5);
        assert_eq!(d, 0.0);
    }

    #[test]
    fn parse_time_secs_normal() {
        assert_eq!(parse_time_secs("08:30:00"), Some(8 * 3600 + 30 * 60));
    }

    #[test]
    fn parse_time_secs_past_midnight() {
        // 25:15:00 = 25h past midnight = 91500 s
        assert_eq!(parse_time_secs("25:15:00"), Some(25 * 3600 + 15 * 60));
    }

    #[test]
    fn parse_time_secs_invalid_returns_none() {
        assert_eq!(parse_time_secs("not-a-time"), None);
    }

    #[sqlx::test]
    async fn compute_route_speed_stores_result_for_simple_route(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S1', 1, '08:00:00', '08:00:00')"
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S2', 2, '08:10:00', '08:10:00')"
        ).execute(&db.pool).await.unwrap();

        compute_route_speed(&db, &test_agency()).await.unwrap();

        let row: (f64, i64) = sqlx::query_as(
            "SELECT scheduled_speed_mps, trip_count FROM route_speed WHERE route_id = 'R1' AND direction_id = 0"
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();

        let (speed, count) = row;
        assert!((speed - 1.852).abs() < 0.05, "expected ~1.852 m/s, got {speed}");
        assert_eq!(count, 1);
    }

    #[sqlx::test]
    async fn route_speed_summary_returns_route_names(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '42', 'The Answer', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed VALUES ('test', 'R1', 0, 10.0, 5, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert_eq!(summary[0].short_name, "42");
        assert_eq!(summary[0].long_name, "The Answer");
        assert_eq!(summary[0].direction_id, 0);
        assert_eq!(summary[0].scheduled_speed_mps, 10.0);
        assert_eq!(summary[0].trip_count, 5);
    }

    #[sqlx::test]
    async fn route_speed_summary_includes_live_speed_from_recent_vehicles(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '10', 'Route 10', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed VALUES ('test', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        // Vehicle active now with speed 15 m/s.
        sqlx::query(
            "INSERT INTO vehicle_positions
             (agency_id, observed_at, trip_id, latitude, longitude, speed)
             VALUES ('test', NOW()::TEXT, 'T1', 45.5, -73.5, 15.0)"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        let live = summary[0].live_speed_mps.expect("expected live speed");
        assert!((live - 15.0).abs() < 0.01, "expected 15.0 m/s, got {live}");
    }

    #[sqlx::test]
    async fn route_speed_summary_live_speed_is_none_when_no_recent_vehicles(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '10', 'Route 10', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed VALUES ('test', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        // Vehicle last seen 2 hours ago — outside the 1-hour window.
        sqlx::query(
            "INSERT INTO vehicle_positions
             (agency_id, observed_at, trip_id, latitude, longitude, speed)
             VALUES ('test', (NOW() - INTERVAL '2 hours')::TEXT, 'T1', 45.5, -73.5, 15.0)"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert!(summary[0].live_speed_mps.is_none(), "expected None for stale vehicle");
    }

    #[sqlx::test]
    async fn compute_route_speed_daily_stores_actual_speed(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S1', 1, '08:00:00', '08:00:00')"
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S2', 2, '08:10:00', '08:10:00')"
        ).execute(&db.pool).await.unwrap();

        let t_s1: i64 = 1767225600;
        let t_s2: i64 = t_s1 + 900;
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
             VALUES ('test', '2026-01-01T08:00:00Z', 'T1', 'S1', 1, $1)"
        ).bind(t_s1).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
             VALUES ('test', '2026-01-01T08:15:00Z', 'T1', 'S2', 2, $1)"
        ).bind(t_s2).execute(&db.pool).await.unwrap();

        let date = chrono::NaiveDate::from_ymd_opt(2026, 1, 1).unwrap();
        compute_route_speed_daily(&db, &test_agency(), date).await.unwrap();

        let row: (f64, i64) = sqlx::query_as(
            "SELECT actual_speed_mps, trip_count
             FROM route_speed_daily
             WHERE route_id = 'R1' AND service_date = '2026-01-01' AND direction_id = 0"
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();

        let (speed, count) = row;
        assert!((speed - 1.235).abs() < 0.05, "expected ~1.235 m/s, got {speed}");
        assert_eq!(count, 1);
    }

    #[sqlx::test]
    async fn route_speed_summary_includes_actual_speed_from_history(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '99', 'Route 99', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed VALUES ('test', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('test', 'R1', (CURRENT_DATE - 1)::TEXT, 0, 6.5, 10, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        let actual = summary[0].actual_speed_mps.expect("expected actual speed");
        assert!((actual - 6.5).abs() < 0.01, "expected 6.5 m/s, got {actual}");
    }

    #[sqlx::test]
    async fn route_speed_summary_actual_speed_is_none_when_no_history(pool: PgPool) {
        let db = Database { pool };

        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '99', 'Route 99', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed VALUES ('test', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert!(summary[0].actual_speed_mps.is_none(), "expected None without history");
    }

    #[sqlx::test]
    async fn route_speed_summary_filters_by_agency(pool: PgPool) {
        let db = Database { pool };
        sqlx::query("INSERT INTO routes VALUES ('stm', 'R1', '15', 'Papineau', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO routes VALUES ('rtl', 'R2', '10', 'Longueuil', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ('stm', 'R1', 0, 5.5, 10, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ('rtl', 'R2', 0, 6.0, 8, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let all = route_speed_summary(&db, None).await.unwrap();
        assert_eq!(all.len(), 2);

        let stm = route_speed_summary(&db, Some("stm")).await.unwrap();
        assert_eq!(stm.len(), 1);
        assert_eq!(stm[0].agency_id, "stm");

        let rtl = route_speed_summary(&db, Some("rtl")).await.unwrap();
        assert_eq!(rtl.len(), 1);
        assert_eq!(rtl[0].agency_id, "rtl");
    }

    fn make_summary(scheduled: f64, actual: Option<f64>) -> RouteSpeedSummary {
        RouteSpeedSummary {
            agency_id: "test".into(),
            route_id: "R1".into(),
            short_name: "1".into(),
            long_name: "Route 1".into(),
            direction_id: 0,
            scheduled_speed_mps: scheduled,
            trip_count: 1,
            live_speed_mps: None,
            actual_speed_mps: actual,
        }
    }

    #[test]
    fn scheduled_speed_kmh_converts_correctly() {
        let s = make_summary(10.0, None);
        // 10 m/s * 3.6 = 36.0 km/h
        assert_eq!(s.scheduled_speed_kmh(), 36.0);
    }

    #[test]
    fn speed_deficit_pct_is_none_without_actual_data() {
        let s = make_summary(10.0, None);
        assert!(s.speed_deficit_pct().is_none());
    }

    #[test]
    fn speed_deficit_pct_positive_when_slower_than_scheduled() {
        // scheduled 10 m/s, actual 8 m/s → 20% slower
        let s = make_summary(10.0, Some(8.0));
        let pct = s.speed_deficit_pct().unwrap();
        assert!((pct - 20.0).abs() < 0.01, "expected 20.0%, got {pct}");
    }

    #[test]
    fn speed_deficit_pct_negative_when_faster_than_scheduled() {
        // scheduled 10 m/s, actual 11 m/s → -10% (faster)
        let s = make_summary(10.0, Some(11.0));
        let pct = s.speed_deficit_pct().unwrap();
        assert!((pct - (-10.0)).abs() < 0.01, "expected -10.0%, got {pct}");
    }

    #[test]
    fn speed_vs_scheduled_display_shows_slower() {
        let s = make_summary(10.0, Some(8.0));
        assert_eq!(s.speed_vs_scheduled_display(), "20% slower");
    }

    #[test]
    fn speed_vs_scheduled_display_shows_faster() {
        let s = make_summary(10.0, Some(11.0));
        assert_eq!(s.speed_vs_scheduled_display(), "10% faster");
    }

    #[test]
    fn speed_vs_scheduled_display_shows_on_pace_within_one_pct() {
        let s = make_summary(10.0, Some(10.05)); // 0.5% faster — within threshold
        assert_eq!(s.speed_vs_scheduled_display(), "On pace");
    }

    #[test]
    fn speed_vs_scheduled_display_is_dash_without_data() {
        let s = make_summary(10.0, None);
        assert_eq!(s.speed_vs_scheduled_display(), "—");
    }
}

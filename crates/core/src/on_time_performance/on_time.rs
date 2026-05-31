use anyhow::Result;
use chrono::{NaiveDate, Utc};
use tracing::{info, warn};

use crate::config::{AgencyConfig, Config};
use crate::db::Database;
use crate::ids::{AgencyId, FeedId};

pub struct TripResult {
    /// Number of stops observed within the on-time window.
    /// Stored as i64 for direct use in SQL SUM aggregations.
    pub on_time_stops: i64,
    pub avg_delay_secs: f64,
    /// Algebraically largest delay (most late). Negative when all stops are early.
    pub max_delay_secs: f64,
}

pub fn classify_trip_delays(
    delays: &[i64],
    early_threshold: i64,
    late_threshold: i64,
) -> TripResult {
    if delays.is_empty() {
        return TripResult {
            on_time_stops: 0,
            avg_delay_secs: 0.0,
            max_delay_secs: 0.0,
        };
    }
    let avg_delay_secs = delays.iter().sum::<i64>() as f64 / delays.len() as f64;
    let max_delay_secs = delays.iter().copied().max().unwrap_or(0) as f64;
    let on_time_stops = delays
        .iter()
        .filter(|&&d| d >= early_threshold && d <= late_threshold)
        .count() as i64;
    TripResult {
        on_time_stops,
        avg_delay_secs,
        max_delay_secs,
    }
}

/// Compute on-time performance for all routes on a given service date.
pub async fn compute_route_daily(
    db: &Database,
    config: &Config,
    agency: &AgencyConfig,
    service_date: NaiveDate,
) -> Result<()> {
    let date_str = service_date.to_string();
    let now = chrono::Utc::now().to_rfc3339();
    let agency_id = AgencyId::from(agency.id.to_string());

    let trips = sqlx::query!(
        "SELECT DISTINCT t.trip_id, t.route_id
         FROM trips t
         JOIN stop_time_events ste ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
         WHERE t.agency_id = $1 AND ste.observed_at::TIMESTAMPTZ::DATE::TEXT = $2",
        &agency_id,
        date_str,
    )
    .fetch_all(&db.pool)
    .await?;

    for trip in &trips {
        // Scheduled times are in agency local time; append UTC offset to get correct Unix epoch.
        let offset = &agency.agency_utc_offset;
        let delays: Vec<i64> = sqlx::query_as::<_, (Option<i64>,)>(
            "SELECT
               CAST(COALESCE(
                 ste.arrival_delay,
                 CASE WHEN ste.arrival_time_unix IS NOT NULL
                   THEN ste.arrival_time_unix -
                        EXTRACT(EPOCH FROM (
                          $1 || 'T' ||
                          CASE WHEN SUBSTRING(ss.arrival_time, 1, 2)::INTEGER >= 24
                            THEN LPAD((SUBSTRING(ss.arrival_time, 1, 2)::INTEGER - 24)::TEXT, 2, '0')
                                 || SUBSTRING(ss.arrival_time, 3)
                            ELSE ss.arrival_time
                          END || $2
                        )::TIMESTAMPTZ)::BIGINT
                   ELSE NULL
                 END
               ) AS BIGINT) as delay
             FROM stop_time_events ste
             JOIN scheduled_stops ss
               ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id AND ss.agency_id = ste.agency_id
             WHERE ste.trip_id = $3 AND ste.agency_id = $4 AND ste.observed_at::TIMESTAMPTZ::DATE::TEXT = $5
               AND (ste.arrival_delay IS NOT NULL OR ste.arrival_time_unix IS NOT NULL)",
        )
        .bind(&date_str)
        .bind(offset)
        .bind(&trip.trip_id)
        .bind(&agency_id)
        .bind(&date_str)
        .fetch_all(&db.pool)
        .await?
        .into_iter()
        .filter_map(|(d,)| d)
        .collect();
        if delays.is_empty() {
            continue;
        }
        let trip_result = classify_trip_delays(
            &delays,
            config.on_time_early_threshold_secs,
            config.on_time_late_threshold_secs,
        );

        sqlx::query!(
            "INSERT INTO trip_results
             (agency_id, trip_id, service_date, route_id, on_time, avg_delay_secs, max_delay_secs, completed, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, 1, $8)
             ON CONFLICT (agency_id, trip_id, service_date) DO UPDATE SET
               route_id = EXCLUDED.route_id,
               on_time = EXCLUDED.on_time,
               avg_delay_secs = EXCLUDED.avg_delay_secs,
               max_delay_secs = EXCLUDED.max_delay_secs,
               completed = EXCLUDED.completed,
               computed_at = EXCLUDED.computed_at",
            &agency_id,
            trip.trip_id,
            date_str,
            trip.route_id,
            trip_result.on_time_stops,
            trip_result.avg_delay_secs,
            trip_result.max_delay_secs,
            now,
        )
        .execute(&db.pool)
        .await?;
    }

    // Aggregate to route_daily using non-macro query for complex aggregation
    let routes: Vec<(String,)> = sqlx::query_as(
        "SELECT DISTINCT route_id FROM trip_results WHERE agency_id = $1 AND service_date = $2",
    )
    .bind(&agency_id)
    .bind(&date_str)
    .fetch_all(&db.pool)
    .await?;

    for (route_id,) in &routes {
        let row: (i64, i64, f64, i64) = sqlx::query_as(
            "SELECT
               COUNT(*) as trips_run,
               COALESCE(SUM(on_time), 0) as on_time_count,
               COALESCE(AVG(avg_delay_secs), 0.0) as avg_delay,
               (SELECT COUNT(*) FROM trips WHERE agency_id = $1 AND route_id = $2) as trips_total
             FROM trip_results
             WHERE agency_id = $3 AND route_id = $4 AND service_date = $5",
        )
        .bind(&agency_id)
        .bind(route_id)
        .bind(&agency_id)
        .bind(route_id)
        .bind(&date_str)
        .fetch_one(&db.pool)
        .await?;

        let (trips_run, on_time_count, avg_delay, trips_total) = row;
        let on_time_pct = if trips_run > 0 {
            (on_time_count as f64 / trips_run as f64) * 100.0
        } else {
            0.0
        };

        sqlx::query!(
            "INSERT INTO route_daily
             (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
             ON CONFLICT (agency_id, route_id, service_date) DO UPDATE SET
               on_time_pct = EXCLUDED.on_time_pct,
               avg_delay_secs = EXCLUDED.avg_delay_secs,
               trips_run = EXCLUDED.trips_run,
               trips_total = EXCLUDED.trips_total,
               computed_at = EXCLUDED.computed_at",
            &agency_id,
            route_id,
            date_str,
            on_time_pct,
            avg_delay,
            trips_run,
            trips_total,
            now,
        )
        .execute(&db.pool)
        .await?;
    }

    info!(
        "Computed performance for {} trips, {} routes on {}",
        trips.len(),
        routes.len(),
        date_str
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn classify_all_on_time_returns_one() {
        let delays = vec![0i64, 60, 120];
        let r = classify_trip_delays(&delays, -60, 300);
        assert_eq!(r.on_time_stops, 3);
    }

    #[test]
    fn classify_one_late_returns_zero() {
        let delays = vec![0i64, 60, 400];
        let r = classify_trip_delays(&delays, -60, 300);
        assert_eq!(r.on_time_stops, 2);
    }

    #[test]
    fn classify_one_early_returns_zero() {
        let delays = vec![0i64, 60, -120];
        let r = classify_trip_delays(&delays, -60, 300);
        assert_eq!(r.on_time_stops, 2);
    }

    #[test]
    fn classify_avg_delay_computed_correctly() {
        let delays = vec![100i64, 200, 300];
        let r = classify_trip_delays(&delays, -60, 300);
        assert!((r.avg_delay_secs - 200.0).abs() < 0.001);
    }

    #[test]
    fn classify_max_delay_computed_correctly() {
        let delays = vec![100i64, 200, 300];
        let r = classify_trip_delays(&delays, -60, 300);
        assert!((r.max_delay_secs - 300.0).abs() < 0.001);
    }

    #[test]
    fn classify_empty_delays_returns_on_time_with_zeros() {
        let r = classify_trip_delays(&[], -60, 300);
        assert_eq!(r.on_time, 0);
        assert_eq!(r.avg_delay_secs, 0.0);
        assert_eq!(r.max_delay_secs, 0.0);
    }

    #[test]
    fn classify_late_threshold_boundary_is_inclusive() {
        let r = classify_trip_delays(&[300], -60, 300);
        assert_eq!(r.on_time, 1);
    }

    #[test]
    fn classify_early_threshold_boundary_is_inclusive() {
        let r = classify_trip_delays(&[-60], -60, 300);
        assert_eq!(r.on_time, 1);
    }

    #[test]
    fn classify_all_early_returns_zero() {
        let delays = vec![-120i64, -90, -60];
        let r = classify_trip_delays(&delays, -60, 300);
        assert_eq!(r.on_time, 0);
    }
}

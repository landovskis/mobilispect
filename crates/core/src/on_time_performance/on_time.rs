use anyhow::Result;
use chrono::NaiveDate;
use tracing::info;

use crate::config::{AgencyConfig, Config};
use crate::db::Database;
use crate::ids::FeedId;

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
    let now = chrono::Utc::now();
    let feed_id = FeedId::from(agency.id);

    // trips no longer has agency_id or route_id; route_id is in route_variants via variant_id.
    let trips: Vec<(String, String)> = sqlx::query_as(
        "SELECT DISTINCT t.trip_id, rv.route_id
         FROM trips t
         JOIN route_variants rv ON rv.feed_id = t.feed_id AND rv.variant_id = t.variant_id
         JOIN stop_time_events ste ON ste.trip_id = t.trip_id AND ste.feed_id = t.feed_id
         WHERE t.feed_id = $1 AND ste.observed_at::DATE = $2::DATE",
    )
    .bind(feed_id.as_i64())
    .bind(service_date)
    .fetch_all(&db.pool)
    .await?;

    for (trip_id, _route_id) in &trips {
        // Delay comes from ste.arrival_delay (ingest sets this from GTFS-RT).
        // arrival_time_unix was removed in migration 012.
        let delays: Vec<i64> = sqlx::query_as::<_, (Option<i64>,)>(
            "SELECT ste.arrival_delay
             FROM stop_time_events ste
             WHERE ste.feed_id = $1
               AND ste.trip_id = $2
               AND ste.observed_at::DATE = $3::DATE
               AND ste.arrival_delay IS NOT NULL",
        )
        .bind(feed_id.as_i64())
        .bind(trip_id)
        .bind(service_date)
        .fetch_all(&db.pool)
        .await?
        .into_iter()
        .filter_map(|(d,)| d)
        .collect();
        if delays.is_empty() {
            continue;
        }

        // Count scheduled stops for this trip to populate total_stops.
        // scheduled_stops no longer has agency_id; use feed_id.
        let total_stops: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM scheduled_stops WHERE feed_id = $1 AND trip_id = $2",
        )
        .bind(feed_id.as_i64())
        .bind(trip_id)
        .fetch_one(&db.pool)
        .await?;

        let trip_result = classify_trip_delays(
            &delays,
            config.on_time_early_threshold_secs,
            config.on_time_late_threshold_secs,
        );

        let observed_stops = delays.len() as i64;
        let skipped_stops: i64 = 0; // SKIPPED stops from GTFS-RT schedule_relationship not yet tracked

        sqlx::query(
            "INSERT INTO trip_results
             (feed_id, trip_id, service_date, on_time_stops, observed_stops, total_stops, skipped_stops, avg_delay_secs, max_delay_secs, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
             ON CONFLICT (feed_id, trip_id, service_date) DO UPDATE SET
               on_time_stops = EXCLUDED.on_time_stops,
               observed_stops = EXCLUDED.observed_stops,
               total_stops = EXCLUDED.total_stops,
               skipped_stops = EXCLUDED.skipped_stops,
               avg_delay_secs = EXCLUDED.avg_delay_secs,
               max_delay_secs = EXCLUDED.max_delay_secs,
               computed_at = EXCLUDED.computed_at",
        )
        .bind(feed_id.as_i64())
        .bind(trip_id)
        .bind(service_date)
        .bind(trip_result.on_time_stops)
        .bind(observed_stops)
        .bind(total_stops)
        .bind(skipped_stops)
        .bind(trip_result.avg_delay_secs)
        .bind(trip_result.max_delay_secs)
        .bind(now)
        .execute(&db.pool)
        .await?;
    }

    // Aggregate to route_daily_stats per (feed_id, onestop_route_id, service_date, variant_id).
    // trips no longer has route_id; derive it from route_variants.
    // trips_total = trips in route_variants for this feed and route_id.
    let route_variant_combos: Vec<(String, String, String)> = sqlx::query_as(
        "SELECT DISTINCT rv.route_id AS gtfs_route_id,
                t.variant_id AS variant_id,
                fri.onestop_id AS onestop_route_id
         FROM trip_results tr
         JOIN trips t ON t.feed_id = tr.feed_id AND t.trip_id = tr.trip_id
         JOIN route_variants rv ON rv.feed_id = t.feed_id AND rv.variant_id = t.variant_id
         JOIN feed_route_ids fri ON fri.feed_id = $1 AND fri.gtfs_route_id = rv.route_id
         WHERE tr.feed_id = $1 AND tr.service_date = $2",
    )
    .bind(feed_id.as_i64())
    .bind(service_date)
    .fetch_all(&db.pool)
    .await?;

    let mut routes_written = 0usize;
    for (gtfs_route_id, variant_id, onestop_route_id) in &route_variant_combos {
        // trips_total = total trips in route_variants for this feed/route/variant.
        let row: (i64, i64, i64, i64, f64, f64, i64) = sqlx::query_as(
            "SELECT
               COUNT(*)                              AS trips_run,
               COALESCE(SUM(tr.on_time_stops), 0)   AS on_time_stops,
               COALESCE(SUM(tr.total_stops), 0)     AS total_stops,
               COALESCE(SUM(tr.skipped_stops), 0)   AS skipped_stops,
               COALESCE(AVG(tr.avg_delay_secs), 0.0) AS avg_delay,
               COALESCE(MAX(tr.max_delay_secs), 0.0) AS max_delay,
               (SELECT COUNT(*)
                FROM trips t2
                JOIN route_variants rv2 ON rv2.feed_id = t2.feed_id AND rv2.variant_id = t2.variant_id
                WHERE t2.feed_id = $1
                  AND rv2.route_id = $2
                  AND t2.variant_id = $3) AS trips_total
             FROM trip_results tr
             JOIN trips t ON t.feed_id = tr.feed_id AND t.trip_id = tr.trip_id
             JOIN route_variants rv ON rv.feed_id = t.feed_id AND rv.variant_id = t.variant_id
             WHERE tr.feed_id = $1
               AND tr.service_date = $4
               AND rv.route_id = $2
               AND t.variant_id = $3",
        )
        .bind(feed_id.as_i64())
        .bind(gtfs_route_id)
        .bind(variant_id)
        .bind(service_date)
        .fetch_one(&db.pool)
        .await?;

        let (trips_run, on_time_stops, total_stops, skipped_stops, avg_delay, max_delay, trips_total) = row;

        sqlx::query(
            "INSERT INTO route_daily_stats
             (feed_id, route_id, service_date, variant_id,
              on_time_stops, total_stops, skipped_stops,
              trips_run, trips_total,
              avg_delay_secs, max_delay_secs,
              actual_speed_mps, avg_dwell_secs,
              computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, NULL, NULL, $12)
             ON CONFLICT (feed_id, route_id, service_date, variant_id) DO NOTHING",
        )
        .bind(feed_id.as_i64())
        .bind(onestop_route_id)
        .bind(service_date)
        .bind(variant_id)
        .bind(on_time_stops)
        .bind(total_stops)
        .bind(skipped_stops)
        .bind(trips_run)
        .bind(trips_total)
        .bind(avg_delay)
        .bind(max_delay)
        .bind(now)
        .execute(&db.pool)
        .await?;

        routes_written += 1;
    }

    info!(
        "Computed performance for {} trips, {} route+variant combos on {}",
        trips.len(),
        routes_written,
        date_str
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn classify_all_on_time_returns_three() {
        let delays = vec![0i64, 60, 120];
        let r = classify_trip_delays(&delays, -60, 300);
        assert_eq!(r.on_time_stops, 3);
    }

    #[test]
    fn classify_one_late_returns_two() {
        let delays = vec![0i64, 60, 400];
        let r = classify_trip_delays(&delays, -60, 300);
        assert_eq!(r.on_time_stops, 2);
    }

    #[test]
    fn classify_one_early_returns_two() {
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
        assert_eq!(r.on_time_stops, 0);
        assert_eq!(r.avg_delay_secs, 0.0);
        assert_eq!(r.max_delay_secs, 0.0);
    }

    #[test]
    fn classify_late_threshold_boundary_is_inclusive() {
        let r = classify_trip_delays(&[300], -60, 300);
        assert_eq!(r.on_time_stops, 1);
    }

    #[test]
    fn classify_early_threshold_boundary_is_inclusive() {
        let r = classify_trip_delays(&[-60], -60, 300);
        assert_eq!(r.on_time_stops, 1);
    }

    #[test]
    fn classify_all_early_returns_one_at_boundary() {
        let delays = vec![-120i64, -90, -60];
        let r = classify_trip_delays(&delays, -60, 300);
        // -60 is exactly at boundary (inclusive), -120 and -90 are outside
        assert_eq!(r.on_time_stops, 1);
    }
}

use std::time::Duration;

use chrono::{Duration as ChronoDuration, Utc};
use tracing::{info, warn};

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;
use mobilispect_core::on_time_performance::compute_route_daily;
use mobilispect_core::speed_analysis::compute_route_speed_daily;

/// Compute daily metrics for the last `days` completed days.
/// Called once at startup to backfill any gaps from previous restarts or deployments.
/// Safe to re-run: all computations are `ON CONFLICT DO UPDATE`.
pub async fn backfill_daily_metrics(
    db: &Database,
    config: &Config,
    feeds: &[mobilispect_core::config::FeedConfig],
    days: u32,
) {
    let today = Utc::now().date_naive();
    for days_back in 1..=days as i64 {
        let date = today - ChronoDuration::days(days_back);
        for agency in feeds {
            match compute_route_daily(db, config, agency, date).await {
                Ok(()) => info!(agency = %agency.id, %date, "Backfilled daily on-time metrics"),
                Err(e) => {
                    warn!(agency = %agency.id, %date, error = %e, "Backfill on-time metrics failed")
                }
            }
            match compute_route_speed_daily(db, agency, date).await {
                Ok(()) => info!(agency = %agency.id, %date, "Backfilled daily speed metrics"),
                Err(e) => {
                    warn!(agency = %agency.id, %date, error = %e, "Backfill speed metrics failed")
                }
            }
        }
    }
}

pub async fn retention_loop(db: &Database, config: &Config) {
    let mut interval = tokio::time::interval(Duration::from_secs(86400));

    loop {
        interval.tick().await;

        let days = config.retention_days as i64;

        match sqlx::query!(
            "DELETE FROM stop_time_events WHERE observed_at::TIMESTAMPTZ < NOW() - ($1::BIGINT * INTERVAL '1 day')",
            days
        )
        .execute(&db.pool)
        .await
        {
            Ok(result) => {
                info!(
                    rows_deleted = result.rows_affected(),
                    "Deleted old rows from stop_time_events"
                );
            }
            Err(e) => {
                tracing::error!(error = %e, "Failed to delete old rows from stop_time_events");
            }
        }

        match sqlx::query!(
            "DELETE FROM vehicle_positions WHERE observed_at::TIMESTAMPTZ < NOW() - ($1::BIGINT * INTERVAL '1 day')",
            days
        )
        .execute(&db.pool)
        .await
        {
            Ok(result) => {
                info!(
                    rows_deleted = result.rows_affected(),
                    "Deleted old rows from vehicle_positions"
                );
            }
            Err(e) => {
                tracing::error!(error = %e, "Failed to delete old rows from vehicle_positions");
            }
        }

        // Compute metrics for yesterday (completed service day) and today (partial data so far).
        // Yesterday is always fully populated regardless of when the worker restarts.
        let db_feeds = match mobilispect_core::db::feeds::load_feeds(&db.pool).await {
            Ok(feeds) => feeds,
            Err(e) => {
                warn!("Failed to load feeds from DB: {e:#}");
                vec![]
            }
        };
        let feeds: Vec<mobilispect_core::config::FeedConfig> = db_feeds
            .into_iter()
            .map(mobilispect_core::config::FeedConfig::from)
            .collect();
        for date in daily_metrics_window(Utc::now().date_naive()) {
            for agency in &feeds {
                match compute_route_daily(db, config, agency, date).await {
                    Ok(()) => info!(agency = %agency.id, %date, "Computed daily on-time metrics"),
                    Err(e) => {
                        tracing::error!(agency = %agency.id, %date, error = %e, "Failed to compute daily on-time metrics")
                    }
                }
                match compute_route_speed_daily(db, agency, date).await {
                    Ok(()) => info!(agency = %agency.id, %date, "Computed daily speed metrics"),
                    Err(e) => {
                        tracing::error!(agency = %agency.id, %date, error = %e, "Failed to compute daily speed metrics")
                    }
                }
            }
        }
    }
}

/// Returns the two UTC dates to compute daily metrics for on each maintenance tick:
/// `[yesterday, today]`. Yesterday is the most recently completed service day;
/// today captures partial data accumulated since midnight UTC.
fn daily_metrics_window(today: chrono::NaiveDate) -> [chrono::NaiveDate; 2] {
    let yesterday = today - ChronoDuration::days(1);
    [yesterday, today]
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::NaiveDate;
    use mobilispect_core::db::test_utils;

    fn test_config() -> Config {
        Config {
            database_url: String::new(),
            poll_interval_secs: 30,
            bind_address: "0.0.0.0:3000".to_string(),
            on_time_early_threshold_secs: 60,
            on_time_late_threshold_secs: 300,
            retention_days: 30,
            worker_health_bind_address: "0.0.0.0:8080".to_string(),
            transitland_api_key: None,
            osm_cache_dir: "./osm-cache".to_string(),
        }
    }

    fn test_feed() -> mobilispect_core::config::FeedConfig {
        mobilispect_core::config::FeedConfig {
            id: 1,
            name: "Test Feed".to_string(),
            gtfs_static_url: "http://test".to_string(),
            gtfs_rt_vehicle_positions_url: None,
            gtfs_rt_trip_updates_url: None,
            gtfs_api_key: None,
            agency_utc_offset: "UTC".to_string(),
            transitland_feed_id: None,
        }
    }

    /// Insert minimal static GTFS + one trip's stop time events for the given `observed_at` timestamp.
    async fn insert_speed_data(pool: &sqlx::PgPool, observed_at: &str) {
        // Feed and agency must exist before routes/trips (FK constraints).
        sqlx::query(
            "INSERT INTO feeds (id, gtfs_static_url) VALUES (1, 'http://test') ON CONFLICT DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO agencies (onestop_id, name) VALUES ('A1', 'Test Agency') ON CONFLICT DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();

        // Canonical route (onestop_id-keyed, agency_id references agencies.onestop_id).
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type)
             VALUES ('R1', 'A1', '1', 'Route 1', 3) ON CONFLICT DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();

        // Mapping from GTFS route_id → onestop_id so compute_route_speed_daily can join.
        sqlx::query(
            "INSERT INTO feed_route_ids (feed_id, gtfs_route_id, onestop_id)
             VALUES (1, 'R1', 'R1') ON CONFLICT DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();

        // Canonical stops.
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon)
             VALUES ('S1', 'Stop 1', 45.50, -73.50) ON CONFLICT DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon)
             VALUES ('S2', 'Stop 2', 45.51, -73.50) ON CONFLICT DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();

        // Route variant (route_id = GTFS route_id 'R1', direction_id 0, is_primary = true).
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary)
             VALUES (1, 'VAR1', 'R1', 0, 2, 1, true) ON CONFLICT DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();

        // Trip belonging to the variant.
        sqlx::query(
            "INSERT INTO trips (feed_id, trip_id, variant_id, service_id)
             VALUES (1, 'T1', 'VAR1', 'WD') ON CONFLICT DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();

        // Scheduled stops for the trip.
        sqlx::query(
            "INSERT INTO scheduled_stops (feed_id, trip_id, stop_id, stop_sequence, arrival_time, departure_time)
             VALUES (1, 'T1', 'S1', 1, '08:00:00', '08:00:00') ON CONFLICT DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops (feed_id, trip_id, stop_id, stop_sequence, arrival_time, departure_time)
             VALUES (1, 'T1', 'S2', 2, '08:10:00', '08:10:00') ON CONFLICT DO NOTHING",
        )
        .execute(pool)
        .await
        .unwrap();

        // Partition for stop_time_events (partitioned table requires a matching partition).
        sqlx::query(
            "CREATE TABLE IF NOT EXISTS stop_time_events_maint_test_part \
             PARTITION OF stop_time_events \
             FOR VALUES FROM ('2020-01-01') TO ('2030-01-01')",
        )
        .execute(pool)
        .await
        .unwrap();

        // Two stop time events with TIMESTAMPTZ arrival_time (15 min apart → ~1.235 m/s).
        let t1 = chrono::DateTime::from_timestamp(1_767_225_600, 0).unwrap();
        let t2 = chrono::DateTime::from_timestamp(1_767_225_600 + 900, 0).unwrap();
        let observed: chrono::DateTime<chrono::Utc> = observed_at.parse().unwrap();
        sqlx::query(
            "INSERT INTO stop_time_events
             (feed_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time)
             VALUES (1, $1, 'T1', 'S1', 1, $2)",
        )
        .bind(observed)
        .bind(t1)
        .execute(pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stop_time_events
             (feed_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time)
             VALUES (1, $1, 'T1', 'S2', 2, $2)",
        )
        .bind(observed)
        .bind(t2)
        .execute(pool)
        .await
        .unwrap();
    }

    #[test]
    fn daily_metrics_window_returns_yesterday_and_today() {
        let today = NaiveDate::from_ymd_opt(2026, 5, 27).unwrap();
        let [yesterday, window_today] = daily_metrics_window(today);
        assert_eq!(yesterday, NaiveDate::from_ymd_opt(2026, 5, 26).unwrap());
        assert_eq!(window_today, today);
        assert!(yesterday < window_today, "yesterday must be before today");
    }

    #[tokio::test]
    async fn backfill_daily_metrics_populates_route_speed_daily_for_past_dates() {
        let td = test_utils::setup().await;
        let yesterday = Utc::now().date_naive() - ChronoDuration::days(1);
        let observed_at = format!("{}T12:00:00Z", yesterday);
        insert_speed_data(&td.db.pool, &observed_at).await;

        backfill_daily_metrics(&td.db, &test_config(), &[test_feed()], 1).await;

        let (count,): (i64,) = sqlx::query_as(
            "SELECT COUNT(*) FROM route_daily_stats WHERE route_id = 'R1' AND service_date = $1",
        )
        .bind(yesterday)
        .fetch_one(&td.db.pool)
        .await
        .unwrap();
        assert!(
            count > 0,
            "backfill must populate route_daily_stats for yesterday"
        );
    }

    #[tokio::test]
    async fn retention_loop_computes_daily_speed_for_yesterday_on_first_tick() {
        let td = test_utils::setup().await;
        let yesterday = Utc::now().date_naive() - ChronoDuration::days(1);
        let observed_at = format!("{}T12:00:00Z", yesterday);
        insert_speed_data(&td.db.pool, &observed_at).await;

        let db_clone = td.db.clone();
        let config = test_config();
        // retention_loop fires its first tick immediately; spawn and let it run.
        let handle = tokio::spawn(async move {
            retention_loop(&db_clone, &config).await;
        });
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(10);
        loop {
            let (count,): (i64,) = sqlx::query_as(
                "SELECT COUNT(*) FROM route_daily_stats WHERE route_id = 'R1' AND service_date = $1",
            )
            .bind(yesterday)
            .fetch_one(&td.db.pool)
            .await
            .unwrap();
            if count > 0 {
                break;
            }
            if std::time::Instant::now() >= deadline {
                break;
            }
            tokio::time::sleep(std::time::Duration::from_millis(100)).await;
        }
        handle.abort();

        let (final_count,): (i64,) = sqlx::query_as(
            "SELECT COUNT(*) FROM route_daily_stats WHERE route_id = 'R1' AND service_date = $1",
        )
        .bind(yesterday)
        .fetch_one(&td.db.pool)
        .await
        .unwrap();
        assert!(
            final_count > 0,
            "retention_loop must compute route_daily_stats for yesterday on first tick"
        );
    }
}

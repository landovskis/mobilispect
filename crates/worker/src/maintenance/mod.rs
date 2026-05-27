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
pub async fn backfill_daily_metrics(db: &Database, config: &Config, days: u32) {
    let today = Utc::now().date_naive();
    for days_back in 1..=days as i64 {
        let date = today - ChronoDuration::days(days_back);
        for agency in &config.agencies {
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
        let today = Utc::now().date_naive();
        let yesterday = today - ChronoDuration::days(1);
        for date in [yesterday, today] {
            for agency in &config.agencies {
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

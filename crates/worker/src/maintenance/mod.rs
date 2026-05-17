use std::time::Duration;

use chrono::Utc;
use tracing::info;

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;
use mobilispect_core::metrics::compute_route_daily;
use mobilispect_core::speed::compute_route_speed_daily;

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

        let today = Utc::now().date_naive();
        for agency in &config.agencies {
            match compute_route_daily(db, config, agency, today).await {
                Ok(()) => info!(agency = %agency.id, "Computed daily on-time metrics"),
                Err(e) => {
                    tracing::error!(agency = %agency.id, error = %e, "Failed to compute daily on-time metrics")
                }
            }
            match compute_route_speed_daily(db, agency, today).await {
                Ok(()) => info!(agency = %agency.id, "Computed daily speed metrics"),
                Err(e) => {
                    tracing::error!(agency = %agency.id, error = %e, "Failed to compute daily speed metrics")
                }
            }
        }
    }
}

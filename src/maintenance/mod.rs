use std::time::Duration;

use tracing::info;

use crate::config::Config;
use crate::db::Database;

pub async fn retention_loop(db: &Database, config: &Config) {
    let mut interval = tokio::time::interval(Duration::from_secs(86400));

    loop {
        interval.tick().await;

        let days = config.retention_days as i64;

        match sqlx::query!(
            "DELETE FROM stop_time_events WHERE observed_at < datetime('now', printf('-%d days', ?))",
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
            "DELETE FROM vehicle_positions WHERE observed_at < datetime('now', printf('-%d days', ?))",
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
    }
}

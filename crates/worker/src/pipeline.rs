use anyhow::Result;
use mobilispect_core::config::AgencyConfig;
use mobilispect_core::db::Database;
use mobilispect_core::speed_analysis;

pub async fn run_static_hooks(db: &Database, agency: &AgencyConfig) -> Result<()> {
    speed_analysis::on_static_loaded(db, agency).await?;
    Ok(())
}

pub async fn run_realtime_hooks(db: &Database, agency: &AgencyConfig) -> Result<()> {
    speed_analysis::on_realtime_polled(db, agency).await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use mobilispect_core::config::AgencyConfig;
    use mobilispect_core::db::test_utils;

    fn test_agency() -> AgencyConfig {
        AgencyConfig {
            id: 0,
            name: "Test Agency".to_string(),
            gtfs_static_url: String::new(),
            gtfs_rt_vehicle_positions_url: None,
            gtfs_rt_trip_updates_url: None,
            gtfs_api_key: None,
            agency_utc_offset: "-04:00".to_string(),
        }
    }

    #[tokio::test]
    async fn run_static_hooks_succeeds_on_empty_db() {
        let td = test_utils::setup().await;
        run_static_hooks(&td.db, &test_agency()).await.unwrap();
    }

    #[tokio::test]
    async fn run_realtime_hooks_succeeds_on_empty_db() {
        let td = test_utils::setup().await;
        run_realtime_hooks(&td.db, &test_agency()).await.unwrap();
    }
}

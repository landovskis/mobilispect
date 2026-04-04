use anyhow::{Context, Result};

#[derive(Debug, Clone)]
pub struct Config {
    pub agency_name: String,
    pub database_url: String,
    pub gtfs_static_url: String,
    pub gtfs_rt_vehicle_positions_url: String,
    pub gtfs_rt_trip_updates_url: String,
    pub gtfs_api_key: Option<String>,
    pub poll_interval_secs: u64,
    pub bind_address: String,
    pub on_time_early_threshold_secs: i64,
    pub on_time_late_threshold_secs: i64,
    /// UTC offset string for agency local time, e.g. "-04:00" or "-05:00"
    pub agency_utc_offset: String,
}

impl Config {
    pub fn from_env() -> Result<Self> {
        Ok(Self {
            agency_name: std::env::var("AGENCY_NAME")
                .unwrap_or_else(|_| "STM".to_string()),
            database_url: std::env::var("DATABASE_URL")
                .unwrap_or_else(|_| "sqlite://mobilispect.db".to_string()),
            gtfs_static_url: std::env::var("GTFS_STATIC_URL")
                .context("GTFS_STATIC_URL must be set")?,
            gtfs_rt_vehicle_positions_url: std::env::var("GTFS_RT_VEHICLE_POSITIONS_URL")
                .context("GTFS_RT_VEHICLE_POSITIONS_URL must be set")?,
            gtfs_rt_trip_updates_url: std::env::var("GTFS_RT_TRIP_UPDATES_URL")
                .context("GTFS_RT_TRIP_UPDATES_URL must be set")?,
            gtfs_api_key: std::env::var("GTFS_API_KEY").ok(),
            poll_interval_secs: std::env::var("POLL_INTERVAL_SECS")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(30),
            bind_address: std::env::var("BIND_ADDRESS")
                .unwrap_or_else(|_| "0.0.0.0:3000".to_string()),
            // Industry standard: -60s (early) to +300s (5 min late)
            on_time_early_threshold_secs: std::env::var("ON_TIME_EARLY_THRESHOLD_SECS")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(-60),
            on_time_late_threshold_secs: std::env::var("ON_TIME_LATE_THRESHOLD_SECS")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(300),
            agency_utc_offset: std::env::var("AGENCY_UTC_OFFSET")
                .unwrap_or_else(|_| "-05:00".to_string()),
        })
    }
}

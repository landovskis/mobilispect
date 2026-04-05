use anyhow::{Context, Result};

/// Per-agency settings — one entry per monitored transit agency.
#[derive(Debug, Clone)]
pub struct AgencyConfig {
    pub slug: String,
    pub name: String,
    pub gtfs_static_url: String,
    pub gtfs_rt_vehicle_positions_url: String,
    pub gtfs_rt_trip_updates_url: String,
    pub gtfs_api_key: Option<String>,
    /// UTC offset string for agency local time, e.g. "-04:00" or "-05:00"
    pub agency_utc_offset: String,
}

#[derive(Debug, Clone)]
pub struct Config {
    pub agencies: Vec<AgencyConfig>,
    pub database_url: String,
    pub poll_interval_secs: u64,
    pub bind_address: String,
    pub on_time_early_threshold_secs: i64,
    pub on_time_late_threshold_secs: i64,
    /// Number of days to retain rows in stop_time_events and vehicle_positions
    pub retention_days: u32,
}

impl Config {
    pub fn from_env() -> Result<Self> {
        let agencies = if std::env::var("AGENCY_0_SLUG").is_ok() {
            // New multi-agency format: read AGENCY_0_*, AGENCY_1_*, ...
            // until no SLUG var is present for the next index.
            let mut agencies = Vec::new();
            let mut i = 0usize;
            loop {
                let slug_key = format!("AGENCY_{i}_SLUG");
                match std::env::var(&slug_key) {
                    Ok(slug) => {
                        let agency = AgencyConfig {
                            slug: slug.clone(),
                            name: std::env::var(format!("AGENCY_{i}_NAME"))
                                .unwrap_or_else(|_| slug.to_uppercase()),
                            gtfs_static_url: std::env::var(format!(
                                "AGENCY_{i}_GTFS_STATIC_URL"
                            ))
                            .with_context(|| {
                                format!("AGENCY_{i}_GTFS_STATIC_URL must be set")
                            })?,
                            gtfs_rt_vehicle_positions_url: std::env::var(format!(
                                "AGENCY_{i}_GTFS_RT_VEHICLE_POSITIONS_URL"
                            ))
                            .with_context(|| {
                                format!("AGENCY_{i}_GTFS_RT_VEHICLE_POSITIONS_URL must be set")
                            })?,
                            gtfs_rt_trip_updates_url: std::env::var(format!(
                                "AGENCY_{i}_GTFS_RT_TRIP_UPDATES_URL"
                            ))
                            .with_context(|| {
                                format!("AGENCY_{i}_GTFS_RT_TRIP_UPDATES_URL must be set")
                            })?,
                            gtfs_api_key: std::env::var(format!("AGENCY_{i}_GTFS_API_KEY")).ok(),
                            agency_utc_offset: std::env::var(format!("AGENCY_{i}_UTC_OFFSET"))
                                .unwrap_or_else(|_| "-05:00".to_string()),
                        };
                        agencies.push(agency);
                        i += 1;
                    }
                    Err(_) => break,
                }
            }
            agencies
        } else {
            // Legacy flat env vars — single-agency backward-compatible mode.
            vec![AgencyConfig {
                slug: std::env::var("AGENCY_NAME")
                    .unwrap_or_else(|_| "stm".to_string())
                    .to_lowercase(),
                name: std::env::var("AGENCY_NAME")
                    .unwrap_or_else(|_| "STM".to_string()),
                gtfs_static_url: std::env::var("GTFS_STATIC_URL")
                    .context("GTFS_STATIC_URL must be set")?,
                gtfs_rt_vehicle_positions_url: std::env::var("GTFS_RT_VEHICLE_POSITIONS_URL")
                    .context("GTFS_RT_VEHICLE_POSITIONS_URL must be set")?,
                gtfs_rt_trip_updates_url: std::env::var("GTFS_RT_TRIP_UPDATES_URL")
                    .context("GTFS_RT_TRIP_UPDATES_URL must be set")?,
                gtfs_api_key: std::env::var("GTFS_API_KEY").ok(),
                agency_utc_offset: std::env::var("AGENCY_UTC_OFFSET")
                    .unwrap_or_else(|_| "-05:00".to_string()),
            }]
        };

        Ok(Self {
            agencies,
            database_url: std::env::var("DATABASE_URL")
                .unwrap_or_else(|_| "sqlite://mobilispect.db".to_string()),
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
            retention_days: std::env::var("RETENTION_DAYS")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(30),
        })
    }
}

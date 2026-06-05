use anyhow::{Context, Result, bail};
use serde::Deserialize;
use std::path::Path;

pub type AgencyConfig = FeedConfig;

/// Per-feed settings - one entry per GTFS data source.
#[derive(Debug, Clone)]
pub struct FeedConfig {
    pub id: u32,
    pub name: String,
    pub gtfs_static_url: String,
    pub gtfs_rt_vehicle_positions_url: Option<String>,
    pub gtfs_rt_trip_updates_url: Option<String>,
    pub gtfs_api_key: Option<String>,
    /// UTC offset string for agency local time, e.g. "-04:00" or "-05:00"
    pub agency_utc_offset: String,
    /// Transitland feed Onestop ID (e.g. "f-f25d-stm"). When None, Transitland
    /// resolution is skipped for this feed.
    pub transitland_feed_id: Option<String>,
}

#[derive(Debug, Clone)]
pub struct Config {
    pub database_url: String,
    pub poll_interval_secs: u64,
    pub bind_address: String,
    pub on_time_early_threshold_secs: i64,
    pub on_time_late_threshold_secs: i64,
    /// Number of days to retain rows in stop_time_events and vehicle_positions
    pub retention_days: u32,
    pub worker_health_bind_address: String,
    /// Shared Transitland API key for all feeds. When None, unauthenticated requests
    /// are made (subject to rate limits).
    pub transitland_api_key: Option<String>,
}

impl Config {
    pub fn load() -> Result<Self> {
        let path = std::env::var("MOBILISPECT_CONFIG").unwrap_or_else(|_| "config.toml".into());
        Self::from_toml_file(path)
    }

    pub fn from_toml_file(path: impl AsRef<Path>) -> Result<Self> {
        let path = path.as_ref();
        let contents = std::fs::read_to_string(path)
            .with_context(|| format!("failed to read config file {}", path.display()))?;
        Self::from_toml_str(&contents)
            .with_context(|| format!("failed to load config file {}", path.display()))
    }

    pub fn from_toml_str(contents: &str) -> Result<Self> {
        Self::from_toml_str_with_env(contents, |name| std::env::var(name).ok())
    }

    fn from_toml_str_with_env<F>(contents: &str, env: F) -> Result<Self>
    where
        F: Fn(&str) -> Option<String>,
    {
        let file: TomlConfig =
            toml::from_str(contents).context("failed to parse TOML configuration")?;

        let database_url = resolve_required_secret(
            file.database_url,
            file.database_url_env,
            "database_url",
            "database_url_env",
            &env,
        )?;

        let transitland_api_key = resolve_optional_secret(
            file.transitland_api_key,
            file.transitland_api_key_env,
            "transitland_api_key",
            "transitland_api_key_env",
            &env,
        )?;

        Ok(Self {
            database_url,
            poll_interval_secs: file.poll_interval_secs.unwrap_or(30),
            bind_address: file
                .bind_address
                .unwrap_or_else(|| "0.0.0.0:3000".to_string()),
            // Industry standard: -60s (early) to +300s (5 min late)
            on_time_early_threshold_secs: file.on_time_early_threshold_secs.unwrap_or(-60),
            on_time_late_threshold_secs: file.on_time_late_threshold_secs.unwrap_or(300),
            retention_days: file.retention_days.unwrap_or(30),
            worker_health_bind_address: file
                .worker_health_bind_address
                .unwrap_or_else(|| "0.0.0.0:9090".to_string()),
            transitland_api_key,
        })
    }
}

#[derive(Debug, Deserialize)]
struct TomlConfig {
    database_url: Option<String>,
    database_url_env: Option<String>,
    poll_interval_secs: Option<u64>,
    bind_address: Option<String>,
    on_time_early_threshold_secs: Option<i64>,
    on_time_late_threshold_secs: Option<i64>,
    retention_days: Option<u32>,
    worker_health_bind_address: Option<String>,
    /// Transitland API key (direct value — not recommended for secrets).
    transitland_api_key: Option<String>,
    /// Env var name holding the Transitland API key (preferred for secrets).
    transitland_api_key_env: Option<String>,
}

fn resolve_required_secret<F>(
    value: Option<String>,
    env_name: Option<String>,
    value_field: &str,
    env_field: &str,
    env: &F,
) -> Result<String>
where
    F: Fn(&str) -> Option<String>,
{
    resolve_optional_secret(value, env_name, value_field, env_field, env)?
        .with_context(|| format!("{value_field} or {env_field} must be set"))
}

fn resolve_optional_secret<F>(
    value: Option<String>,
    env_name: Option<String>,
    value_field: &str,
    env_field: &str,
    env: &F,
) -> Result<Option<String>>
where
    F: Fn(&str) -> Option<String>,
{
    match (value, env_name) {
        (Some(_), Some(_)) => bail!("set only one of {value_field} or {env_field}"),
        (Some(value), None) => Ok(Some(value)),
        (None, Some(env_name)) => env(&env_name)
            .with_context(|| {
                format!("{env_field} references missing environment variable {env_name}")
            })
            .map(Some),
        (None, None) => Ok(None),
    }
}

impl From<crate::db::feeds::DbFeed> for FeedConfig {
    fn from(f: crate::db::feeds::DbFeed) -> Self {
        Self {
            id: f.id as u32,
            name: f.name.unwrap_or_else(|| f.id.to_string()),
            gtfs_static_url: f.gtfs_static_url,
            gtfs_rt_vehicle_positions_url: f.gtfs_rt_vehicle_positions_url,
            gtfs_rt_trip_updates_url: f.gtfs_rt_trip_updates_url,
            gtfs_api_key: f.gtfs_api_key,
            agency_utc_offset: "UTC".to_string(),
            transitland_feed_id: f.transitland_onestop_id,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn config_from_toml(contents: &str, env: &[(&str, &str)]) -> Result<Config> {
        let env: HashMap<String, String> = env
            .iter()
            .map(|(key, value)| (key.to_string(), value.to_string()))
            .collect();
        Config::from_toml_str_with_env(contents, |name| env.get(name).cloned())
    }

    #[test]
    fn loads_database_url_from_env_ref() {
        let config = config_from_toml(
            r#"
database_url_env = "DATABASE_URL"
"#,
            &[(
                "DATABASE_URL",
                "postgres://user:pass@localhost:5432/mobilispect",
            )],
        )
        .unwrap();

        assert_eq!(
            config.database_url,
            "postgres://user:pass@localhost:5432/mobilispect"
        );
    }

    #[test]
    fn applies_defaults_for_optional_toml_fields() {
        let config = config_from_toml(
            r#"
database_url = "postgres://localhost/mobilispect"
"#,
            &[],
        )
        .unwrap();

        assert_eq!(config.poll_interval_secs, 30);
        assert_eq!(config.bind_address, "0.0.0.0:3000");
        assert_eq!(config.on_time_early_threshold_secs, -60);
        assert_eq!(config.on_time_late_threshold_secs, 300);
        assert_eq!(config.retention_days, 30);
        assert_eq!(config.worker_health_bind_address, "0.0.0.0:9090");
    }

    #[test]
    fn errors_when_secret_env_ref_is_missing() {
        let err = config_from_toml(
            r#"
database_url_env = "DATABASE_URL"
"#,
            &[],
        )
        .unwrap_err();

        assert!(
            format!("{err:#}")
                .contains("database_url_env references missing environment variable DATABASE_URL")
        );
    }

    #[test]
    fn errors_when_direct_value_and_env_ref_are_both_set() {
        let err = config_from_toml(
            r#"
database_url = "postgres://localhost/mobilispect"
database_url_env = "DATABASE_URL"
"#,
            &[("DATABASE_URL", "postgres://from-env/mobilispect")],
        )
        .unwrap_err();

        assert!(format!("{err:#}").contains("set only one of database_url or database_url_env"));
    }
}

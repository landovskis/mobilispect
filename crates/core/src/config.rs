use anyhow::{Context, Result, bail};
use serde::Deserialize;
use std::path::Path;

/// Compatibility alias — will be removed once all callers are migrated to FeedConfig.
#[doc(hidden)]
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

/// A transit network built from one or more feeds.
#[derive(Debug, Clone)]
pub struct NetworkConfig {
    pub id: u32,
    pub name: String,
    pub feeds: Vec<FeedConfig>,
}

#[derive(Debug, Clone)]
pub struct RegionConfig {
    pub name: String,
    pub timezone: String,
    pub networks: Vec<NetworkConfig>,
}

#[derive(Debug, Clone)]
pub struct Config {
    /// Flat list of all feeds across all networks — convenient for worker iteration.
    pub feeds: Vec<FeedConfig>,
    pub region: RegionConfig,
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

        if file.region.networks.is_empty() {
            bail!("config must define at least one region network");
        }

        let networks = file
            .region
            .networks
            .into_iter()
            .map(|network| {
                if network.feeds.is_empty() {
                    bail!(
                        "network '{}' must define at least one feed",
                        network.name
                    );
                }
                let feeds = network
                    .feeds
                    .into_iter()
                    .map(|feed| feed.into_runtime(&env))
                    .collect::<Result<Vec<_>>>()?;
                Ok(NetworkConfig {
                    id: network.id,
                    name: network.name,
                    feeds,
                })
            })
            .collect::<Result<Vec<_>>>()?;

        let feeds: Vec<FeedConfig> = networks
            .iter()
            .flat_map(|n| n.feeds.iter().cloned())
            .collect();

        let region = RegionConfig {
            name: file.region.name,
            timezone: file.region.timezone,
            networks,
        };

        Ok(Self {
            feeds,
            region,
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
    region: TomlRegionConfig,
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

#[derive(Debug, Deserialize)]
struct TomlRegionConfig {
    name: String,
    timezone: String,
    networks: Vec<TomlNetworkConfig>,
}

#[derive(Debug, Deserialize)]
struct TomlNetworkConfig {
    id: u32,
    name: String,
    feeds: Vec<TomlFeedConfig>,
}

#[derive(Debug, Deserialize)]
struct TomlFeedConfig {
    id: u32,
    name: String,
    gtfs_static_url: String,
    gtfs_rt_vehicle_positions_url: Option<String>,
    gtfs_rt_trip_updates_url: Option<String>,
    gtfs_api_key: Option<String>,
    gtfs_api_key_env: Option<String>,
    agency_utc_offset: Option<String>,
    /// Transitland feed Onestop ID for this specific feed (e.g. "f-f25d-stm").
    transitland_feed_id: Option<String>,
}

impl TomlFeedConfig {
    fn into_runtime<F>(self, env: &F) -> Result<FeedConfig>
    where
        F: Fn(&str) -> Option<String>,
    {
        let gtfs_api_key = resolve_optional_secret(
            self.gtfs_api_key,
            self.gtfs_api_key_env,
            "gtfs_api_key",
            "gtfs_api_key_env",
            env,
        )?;

        Ok(FeedConfig {
            id: self.id,
            name: self.name,
            gtfs_static_url: self.gtfs_static_url,
            gtfs_rt_vehicle_positions_url: self.gtfs_rt_vehicle_positions_url,
            gtfs_rt_trip_updates_url: self.gtfs_rt_trip_updates_url,
            gtfs_api_key,
            agency_utc_offset: self
                .agency_utc_offset
                .unwrap_or_else(|| "-05:00".to_string()),
            transitland_feed_id: self.transitland_feed_id,
        })
    }
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
    fn loads_toml_config_and_resolves_dotenvx_secret_env_refs() {
        let config = config_from_toml(
            r#"
database_url_env = "DATABASE_URL"
bind_address = "127.0.0.1:4000"
poll_interval_secs = 45
retention_days = 14

[region]
name = "Montreal"
timezone = "America/Toronto"

[[region.networks]]
id = 0
name = "Montreal Transit"

[[region.networks.feeds]]
id = 0
name = "STM"
gtfs_static_url = "https://example.com/stm.zip"
gtfs_rt_vehicle_positions_url = "https://example.com/vehicle.pb"
gtfs_rt_trip_updates_url = "https://example.com/trip.pb"
gtfs_api_key_env = "STM_GTFS_API_KEY"
agency_utc_offset = "-04:00"
"#,
            &[
                (
                    "DATABASE_URL",
                    "postgres://user:pass@localhost:5432/mobilispect",
                ),
                ("STM_GTFS_API_KEY", "secret-api-key"),
            ],
        )
        .unwrap();

        assert_eq!(
            config.database_url,
            "postgres://user:pass@localhost:5432/mobilispect"
        );
        assert_eq!(config.region.name, "Montreal");
        assert_eq!(config.region.timezone, "America/Toronto");
        assert_eq!(config.region.networks.len(), 1);
        assert_eq!(config.region.networks[0].id, 0);
        assert_eq!(config.region.networks[0].name, "Montreal Transit");
        assert_eq!(config.region.networks[0].feeds.len(), 1);
        assert_eq!(config.bind_address, "127.0.0.1:4000");
        assert_eq!(config.poll_interval_secs, 45);
        assert_eq!(config.retention_days, 14);
        assert_eq!(config.feeds.len(), 1);
        let feed = &config.feeds[0];
        assert_eq!(feed.id, 0);
        assert_eq!(feed.name, "STM");
        assert_eq!(feed.gtfs_static_url, "https://example.com/stm.zip");
        assert_eq!(
            feed.gtfs_rt_vehicle_positions_url.as_deref(),
            Some("https://example.com/vehicle.pb")
        );
        assert_eq!(
            feed.gtfs_rt_trip_updates_url.as_deref(),
            Some("https://example.com/trip.pb")
        );
        assert_eq!(feed.gtfs_api_key.as_deref(), Some("secret-api-key"));
        assert_eq!(feed.agency_utc_offset, "-04:00");
    }

    #[test]
    fn applies_defaults_for_optional_toml_fields() {
        let config = config_from_toml(
            r#"
database_url = "postgres://localhost/mobilispect"

[region]
name = "Montreal"
timezone = "America/Toronto"

[[region.networks]]
id = 0
name = "South Shore"

[[region.networks.feeds]]
id = 1
name = "RTL"
gtfs_static_url = "https://example.com/rtl.zip"
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
        assert_eq!(config.region.name, "Montreal");
        assert_eq!(config.region.timezone, "America/Toronto");
        assert_eq!(config.region.networks.len(), 1);
        assert_eq!(config.feeds[0].id, 1);
        assert_eq!(config.feeds[0].name, "RTL");
        assert_eq!(config.feeds[0].agency_utc_offset, "-05:00");
    }

    #[test]
    fn flattens_feeds_from_multiple_networks() {
        let config = config_from_toml(
            r#"
database_url = "postgres://localhost/mobilispect"

[region]
name = "Montreal"
timezone = "America/Toronto"

[[region.networks]]
id = 0
name = "Island"

[[region.networks.feeds]]
id = 0
name = "STM"
gtfs_static_url = "https://example.com/stm.zip"

[[region.networks]]
id = 1
name = "South Shore"

[[region.networks.feeds]]
id = 1
name = "RTL"
gtfs_static_url = "https://example.com/rtl.zip"

[[region.networks.feeds]]
id = 2
name = "CIT Sorel"
gtfs_static_url = "https://example.com/cit.zip"
"#,
            &[],
        )
        .unwrap();

        assert_eq!(config.region.networks.len(), 2);
        assert_eq!(config.feeds.len(), 3);
        assert_eq!(config.feeds[0].name, "STM");
        assert_eq!(config.feeds[1].name, "RTL");
        assert_eq!(config.feeds[2].name, "CIT Sorel");
    }

    #[test]
    fn errors_when_region_is_missing() {
        let err = config_from_toml(
            r#"
database_url = "postgres://localhost/mobilispect"
"#,
            &[],
        )
        .unwrap_err();

        assert!(format!("{err:#}").contains("missing field `region`"));
    }

    #[test]
    fn errors_when_region_timezone_is_missing() {
        let err = config_from_toml(
            r#"
database_url = "postgres://localhost/mobilispect"

[region]
name = "Montreal"

[[region.networks]]
id = 0
name = "Island"

[[region.networks.feeds]]
id = 0
name = "STM"
gtfs_static_url = "https://example.com/stm.zip"
"#,
            &[],
        )
        .unwrap_err();

        assert!(format!("{err:#}").contains("missing field `timezone`"));
    }

    #[test]
    fn errors_when_region_networks_are_empty() {
        let err = config_from_toml(
            r#"
database_url = "postgres://localhost/mobilispect"

[region]
name = "Montreal"
timezone = "America/Toronto"
networks = []
"#,
            &[],
        )
        .unwrap_err();

        assert!(
            format!("{err:#}").contains("config must define at least one region network")
        );
    }

    #[test]
    fn errors_when_network_feeds_are_empty() {
        let err = config_from_toml(
            r#"
database_url = "postgres://localhost/mobilispect"

[region]
name = "Montreal"
timezone = "America/Toronto"

[[region.networks]]
id = 0
name = "Island"
feeds = []
"#,
            &[],
        )
        .unwrap_err();

        assert!(
            format!("{err:#}").contains("must define at least one feed")
        );
    }

    #[test]
    fn errors_when_secret_env_ref_is_missing() {
        let err = config_from_toml(
            r#"
database_url_env = "DATABASE_URL"

[region]
name = "Montreal"
timezone = "America/Toronto"

[[region.networks]]
id = 0
name = "Island"

[[region.networks.feeds]]
id = 0
name = "STM"
gtfs_static_url = "https://example.com/stm.zip"
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

[region]
name = "Montreal"
timezone = "America/Toronto"

[[region.networks]]
id = 0
name = "Island"

[[region.networks.feeds]]
id = 0
name = "STM"
gtfs_static_url = "https://example.com/stm.zip"
"#,
            &[("DATABASE_URL", "postgres://from-env/mobilispect")],
        )
        .unwrap_err();

        assert!(format!("{err:#}").contains("set only one of database_url or database_url_env"));
    }
}

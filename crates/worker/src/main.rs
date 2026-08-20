use anyhow::Result;
use tracing::{info, warn};
use tracing_subscriber::EnvFilter;

use mobilispect_core::config::{Config, FeedConfig};
use mobilispect_core::db::Database;
use mobilispect_core::db::feeds::load_feeds;
use mobilispect_core::ids::FeedId;
use mobilispect_core::transitland::TransitlandClient;
mod feed_ingestion;
mod health;
mod maintenance;
mod pipeline;
mod region_provisioning;

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("mobilispect=info".parse()?))
        .init();

    let config = Config::load()?;
    let db = Database::connect(&config.database_url).await?;

    let feeds: Vec<FeedConfig> = loop {
        let db_feeds = load_feeds(&db.pool).await?;
        if !db_feeds.is_empty() {
            break db_feeds.into_iter().map(FeedConfig::from).collect();
        }
        warn!("No feeds in DB yet — waiting for first-launch setup to complete (retrying in 30s)");
        tokio::time::sleep(std::time::Duration::from_secs(30)).await;
    };
    info!(
        "Mobilispect worker starting — {} feed(s) in DB",
        feeds.len()
    );

    let transitland =
        std::sync::Arc::new(TransitlandClient::new(config.transitland_api_key.clone()));

    let mut set: tokio::task::JoinSet<(mobilispect_core::config::FeedConfig, Result<()>)> =
        tokio::task::JoinSet::new();
    for agency in &feeds {
        let db = db.clone();
        let agency = agency.clone();
        let feed_id = FeedId::from(agency.id);
        let transitland = transitland.clone();
        set.spawn(async move {
            info!("Loading static GTFS for agency: {}", agency.name);
            let result = async {
                feed_ingestion::static_feed::load_if_needed(&db, &agency, feed_id, &transitland)
                    .await?;
                pipeline::run_static_hooks(&db, &agency).await?;
                info!("Static import complete for agency: {}", agency.name);
                Ok(())
            }
            .await;
            (agency, result)
        });
    }

    let mut loaded = Vec::new();
    while let Some(res) = set.join_next().await {
        let (agency, result) = res?;
        match result {
            Ok(()) => loaded.push(agency),
            Err(e) => warn!(
                "Skipping {}: failed to load static GTFS: {e:#}",
                agency.name
            ),
        }
    }

    // Backfill the last 7 days of daily metrics on startup to recover from any gaps
    // caused by restarts or deployments that ran at the start of the UTC day before
    // any service data had accumulated.
    maintenance::backfill_daily_metrics(&db, &config, &feeds, 7).await;

    // Not awaited: downloading StatsCan boundary data and Geofabrik OSM
    // extracts can take a while on first run and must not delay real-time
    // GTFS-RT polling from starting below.
    let db_provisioning = db.clone();
    let config_provisioning = config.clone();
    tokio::spawn(async move {
        region_provisioning::run_all(db_provisioning, config_provisioning).await;
    });

    for agency in loaded {
        let db_rt = db.clone();
        let poll_interval = config.poll_interval_secs;
        tokio::spawn(async move {
            loop {
                feed_ingestion::realtime::poll_loop(&db_rt, &agency, poll_interval).await;
                warn!(agency = %agency.name, "RT poll loop exited unexpectedly, restarting in 30s");
                tokio::time::sleep(std::time::Duration::from_secs(30)).await;
            }
        });
    }

    let db_maint = db.clone();
    let config_maint = config.clone();
    tokio::spawn(async move {
        loop {
            maintenance::retention_loop(&db_maint, &config_maint).await;
            warn!("Maintenance loop exited unexpectedly, restarting in 30s");
            tokio::time::sleep(std::time::Duration::from_secs(30)).await;
        }
    });

    let db_health = db.clone();
    let health_addr = config.worker_health_bind_address.clone();
    tokio::spawn(async move {
        if let Err(e) = health::serve(db_health, &health_addr).await {
            warn!("Health server failed: {e:#}");
        }
    });

    std::future::pending::<()>().await;

    Ok(())
}

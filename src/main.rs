mod config;
mod db;
mod gtfs;
mod maintenance;
mod metrics;
mod speed;
mod web;

use anyhow::Result;
use tracing::{info, warn};
use tracing_subscriber::EnvFilter;

use crate::config::Config;
use crate::db::Database;

#[tokio::main]
async fn main() -> Result<()> {
    dotenvy::dotenv().ok();

    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("mobilispect=info".parse()?))
        .init();

    let config = Config::from_env()?;
    let db = Database::connect(&config.database_url).await?;
    db.migrate().await?;

    info!(
        "Mobilispect starting — {} agency/agencies configured",
        config.agencies.len()
    );

    // Load static GTFS for all agencies in parallel. Failed agencies are logged
    // and skipped; the process continues with the remaining agencies.
    let mut set: tokio::task::JoinSet<(config::AgencyConfig, Result<()>)> =
        tokio::task::JoinSet::new();
    for agency in &config.agencies {
        let db = db.clone();
        let agency = agency.clone();
        set.spawn(async move {
            info!("Loading static GTFS for agency: {}", agency.name);
            let result = async {
                gtfs::static_feed::load_if_needed(&db, &agency).await?;
                speed::compute_route_speed(&db, &agency).await?;
                info!("Computed scheduled speed for agency: {}", agency.name);
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
            Err(e) => warn!("Skipping {}: failed to load static GTFS: {e:#}", agency.name),
        }
    }

    // Start a GTFS-RT poll loop for each successfully loaded agency.
    for agency in loaded {
        let db_rt = db.clone();
        let poll_interval = config.poll_interval_secs;
        tokio::spawn(async move {
            loop {
                gtfs::realtime::poll_loop(&db_rt, &agency, poll_interval).await;
                warn!(agency = %agency.name, "RT poll loop exited unexpectedly, restarting in 30s");
                tokio::time::sleep(std::time::Duration::from_secs(30)).await;
            }
        });
    }

    // Start background task: data retention cleanup once per day
    let db_maint = db.clone();
    let config_maint = config.clone();
    tokio::spawn(async move {
        loop {
            maintenance::retention_loop(&db_maint, &config_maint).await;
            warn!("Maintenance loop exited unexpectedly, restarting in 30s");
            tokio::time::sleep(std::time::Duration::from_secs(30)).await;
        }
    });

    // Start web server
    web::serve(&db, &config).await?;

    Ok(())
}

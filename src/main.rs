mod config;
mod db;
mod gtfs;
mod maintenance;
mod metrics;
mod web;

use anyhow::Result;
use tracing::info;
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

    // Load static GTFS and start a GTFS-RT poll loop for each configured agency.
    for agency in &config.agencies {
        info!("Loading static GTFS for agency: {}", agency.name);
        gtfs::static_feed::load_if_needed(&db, &agency.gtfs_static_url).await?;

        let db_rt = db.clone();
        let agency_rt = agency.clone();
        let poll_interval = config.poll_interval_secs;
        tokio::spawn(async move {
            gtfs::realtime::poll_loop(&db_rt, &agency_rt, poll_interval).await;
        });
    }

    // Start background task: data retention cleanup once per day
    let db_maint = db.clone();
    let config_maint = config.clone();
    tokio::spawn(async move {
        maintenance::retention_loop(&db_maint, &config_maint).await;
    });

    // Start web server
    web::serve(&db, &config).await?;

    Ok(())
}

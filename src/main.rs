mod config;
mod db;
mod gtfs;
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

    info!("Mobilispect starting — agency: {}", config.agency_name);

    // Load static GTFS on startup if not already loaded
    gtfs::static_feed::load_if_needed(&db, &config.gtfs_static_url).await?;

    // Start background task: poll GTFS-RT every 30s
    let db_rt = db.clone();
    let config_rt = config.clone();
    tokio::spawn(async move {
        gtfs::realtime::poll_loop(&db_rt, &config_rt).await;
    });

    // Start web server
    web::serve(&db, &config).await?;

    Ok(())
}

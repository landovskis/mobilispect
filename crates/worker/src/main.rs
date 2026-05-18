use anyhow::Result;
use tracing::{info, warn};
use tracing_subscriber::EnvFilter;

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;
use mobilispect_core::speed;

mod gtfs;
mod maintenance;
mod pipeline;

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("mobilispect=info".parse()?))
        .init();

    let config = Config::load()?;
    let db = Database::connect(&config.database_url).await?;

    info!(
        "Mobilispect worker starting — {} agency/agencies configured",
        config.agencies.len()
    );

    let mut set: tokio::task::JoinSet<(mobilispect_core::config::AgencyConfig, Result<()>)> =
        tokio::task::JoinSet::new();
    for agency in &config.agencies {
        let db = db.clone();
        let agency = agency.clone();
        set.spawn(async move {
            info!("Loading static GTFS for agency: {}", agency.name);
            let result = async {
                gtfs::static_feed::load_if_needed(&db, &agency).await?;
                speed::compute_route_speed(&db, &agency).await?;
                speed::compute_route_speed_by_day_type(&db, &agency).await?;
                info!(
                    "Computed scheduled speed (all day types) for agency: {}",
                    agency.name
                );
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

    let db_maint = db.clone();
    let config_maint = config.clone();
    tokio::spawn(async move {
        loop {
            maintenance::retention_loop(&db_maint, &config_maint).await;
            warn!("Maintenance loop exited unexpectedly, restarting in 30s");
            tokio::time::sleep(std::time::Duration::from_secs(30)).await;
        }
    });

    std::future::pending::<()>().await;

    Ok(())
}

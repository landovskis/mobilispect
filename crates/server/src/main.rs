use anyhow::Result;
use tracing_subscriber::EnvFilter;

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;

mod web;

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("mobilispect=info".parse()?))
        .init();

    let config = Config::load()?;
    let db = Database::connect(&config.database_url).await?;
    db.migrate().await?;

    web::serve(&db, &config).await?;

    Ok(())
}

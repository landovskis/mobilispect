use anyhow::Result;
use tracing_subscriber::EnvFilter;

use mobilispect::config::Config;
use mobilispect::db::Database;
use mobilispect::web;

#[tokio::main]
async fn main() -> Result<()> {
    dotenvy::dotenv().ok();

    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("mobilispect=info".parse()?))
        .init();

    let config = Config::from_env()?;
    let db = Database::connect(&config.database_url).await?;
    db.migrate().await?;

    web::serve(&db, &config).await?;

    Ok(())
}

use anyhow::Result;
use axum::{routing::get, Router};
use tower_http::trace::TraceLayer;
use tracing::info;

use crate::config::Config;
use crate::db::Database;

mod handlers;

#[derive(Clone)]
pub struct AppState {
    pub db: Database,
    pub config: Config,
}

pub async fn serve(db: &Database, config: &Config) -> Result<()> {
    let state = AppState {
        db: db.clone(),
        config: config.clone(),
    };

    let app = Router::new()
        .route("/", get(handlers::dashboard))
        .route("/report", get(handlers::report))
        .route("/speed", get(handlers::speed_page))
        .route("/scorecard", get(handlers::scorecard))
        .route("/routes/:agency_id/:route_id", get(handlers::route_detail))
        .route("/hotspots", get(handlers::hotspots))
        .route("/compute", get(handlers::compute))
        .route("/api/routes", get(handlers::api_routes))
        .route("/api/routes/speed", get(handlers::api_route_speed))
        .layer(TraceLayer::new_for_http())
        .with_state(state);

    let listener = tokio::net::TcpListener::bind(&config.bind_address).await?;
    info!("Dashboard available at http://{}", config.bind_address);
    axum::serve(listener, app).await?;
    Ok(())
}

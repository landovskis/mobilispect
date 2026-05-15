use anyhow::Result;
use axum::{Router, routing::get};
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

pub fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/", get(handlers::speed_page))
        .route("/report", get(handlers::report))
        .route("/speed", get(handlers::speed_page))
        .route("/scorecard", get(handlers::scorecard))
        .route("/frequency", get(handlers::frequency_page))
        // /speed route registered BEFORE bare :route_id to avoid shadowing
        .route(
            "/routes/:agency_id/:route_id/speed",
            get(handlers::route_speed_detail),
        )
        .route("/routes/:agency_id/:route_id", get(handlers::route_detail))
        .route("/hotspots", get(handlers::hotspots))
        .route("/api/routes", get(handlers::api_routes))
        .route("/api/routes/speed", get(handlers::api_route_speed))
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

pub async fn serve(db: &Database, config: &Config) -> Result<()> {
    let state = AppState {
        db: db.clone(),
        config: config.clone(),
    };
    let app = build_router(state);
    let listener = tokio::net::TcpListener::bind(&config.bind_address).await?;
    info!("Dashboard available at http://{}", config.bind_address);
    axum::serve(listener, app).await?;
    Ok(())
}

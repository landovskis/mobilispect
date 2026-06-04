use anyhow::Result;
use axum::{Router, routing::get};
use std::sync::Arc;
use tokio::sync::RwLock;
use tower_http::trace::TraceLayer;
use tracing::info;

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;

mod handlers;
pub mod middleware;

pub enum SetupState {
    Idle,
    Running,
    Done { city: String },
    Failed { message: String },
}

#[derive(Clone)]
pub struct AppState {
    pub db: Database,
    pub config: Config,
    pub region: Arc<RwLock<Option<String>>>,
    pub setup_state: Arc<tokio::sync::Mutex<SetupState>>,
}

pub fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/", get(handlers::speed_page))
        .route("/speed", get(handlers::speed_page))
        .route("/schedule", get(handlers::frequency_page))
        .route("/frequency", get(handlers::frequency_page))
        .route(
            "/schedule/:feed_id/:route_id",
            get(handlers::schedule_detail),
        )
        .route(
            "/routes/:agency_id/:route_id/speed",
            get(handlers::route_speed_detail),
        )
        .route("/routes/:agency_id/:route_id", get(handlers::route_detail))
        .route("/api/routes", get(handlers::api_routes))
        .route("/api/routes/speed", get(handlers::api_route_speed))
        .route("/health", get(handlers::health_check))
        .layer(axum::middleware::from_fn_with_state(
            state.clone(),
            middleware::require_region_configured,
        ))
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

pub async fn serve(db: &Database, config: &Config) -> Result<()> {
    let region_name: Option<String> = sqlx::query_scalar!("SELECT name FROM regions LIMIT 1")
        .fetch_optional(&db.pool)
        .await?;

    if let Some(ref name) = region_name {
        info!("Region '{}' already configured", name);
    } else {
        info!("No region configured — first-launch setup required");
    }

    let state = AppState {
        db: db.clone(),
        config: config.clone(),
        region: Arc::new(RwLock::new(region_name)),
        setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
    };

    let setup_router = Router::new()
        .route(
            "/setup",
            get(handlers::setup_page).post(handlers::setup_submit),
        )
        .route("/setup/status", get(handlers::setup_status))
        .with_state(state.clone());

    let app = Router::new().merge(build_router(state)).merge(setup_router);

    let listener = tokio::net::TcpListener::bind(&config.bind_address).await?;
    info!("Dashboard available at http://{}", config.bind_address);
    axum::serve(listener, app).await?;
    Ok(())
}

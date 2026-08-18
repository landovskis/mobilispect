use anyhow::Result;
use axum::{
    Router,
    routing::{get, post},
};
use std::sync::Arc;
use tokio::sync::RwLock;
use tower_http::services::{ServeDir, ServeFile};
use tower_http::trace::TraceLayer;
use tracing::info;

use mobilispect_core::config::Config;
use mobilispect_core::db::Database;

mod corridor_api;
mod handlers;
mod intersection_api;
mod lane_editor_api;
pub mod middleware;
mod osm_import;
mod remix_api;

#[derive(Debug)]
pub enum SetupState {
    Idle,
    Running,
    Done { city: String },
    Failed { message: String, city: String },
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
        .route("/api/regions", get(remix_api::list_regions))
        .route(
            "/api/regions/:region_id/remixes",
            get(remix_api::list_region_remixes),
        )
        .route("/api/remixes", post(remix_api::create_remix))
        .route("/api/remixes/:remix_id", get(remix_api::get_remix))
        .route(
            "/api/remixes/:remix_id/corridors",
            get(remix_api::list_remix_corridors),
        )
        .route(
            "/api/remixes/:remix_id/corridors/manual",
            post(corridor_api::start_manual_corridor),
        )
        .route(
            "/api/corridors/:corridor_id/points",
            post(corridor_api::add_manual_point),
        )
        .route(
            "/api/corridors/:corridor_id/finish",
            post(corridor_api::finish_manual_corridor),
        )
        .route(
            "/api/remixes/:remix_id/streets",
            post(osm_import::search_streets),
        )
        .route(
            "/api/remixes/:remix_id/corridors/import",
            post(osm_import::import_corridor),
        )
        .route(
            "/api/corridors/:corridor_id/cross-sections",
            get(lane_editor_api::list_cross_sections),
        )
        .route(
            "/api/corridors/:corridor_id/cross-sections/:cross_section_id/label",
            axum::routing::patch(lane_editor_api::update_label),
        )
        .route(
            "/api/cross-sections/:cross_section_id",
            get(lane_editor_api::get_cross_section),
        )
        .route(
            "/api/cross-sections/:cross_section_id/lanes",
            get(lane_editor_api::list_lanes).post(lane_editor_api::insert_lane),
        )
        .route(
            "/api/lanes/:lane_id",
            axum::routing::patch(lane_editor_api::update_lane).delete(lane_editor_api::delete_lane),
        )
        .route(
            "/api/lanes/:lane_id/access-rules",
            axum::routing::put(lane_editor_api::set_access_rules),
        )
        .route(
            "/api/intersections/:id",
            get(intersection_api::get_intersection)
                .put(intersection_api::set_intersection_treatment),
        )
        .route(
            "/api/intersections/:id/turn-movements",
            get(intersection_api::list_turn_movements).post(intersection_api::set_turn_movement),
        )
        .route(
            "/api/intersections/:id/turn-movements/:from_lane_id/:to_lane_id",
            axum::routing::delete(intersection_api::delete_turn_movement),
        )
        .route(
            "/api/corridors/:corridor_id/cross-sections/:cross_section_id/split",
            post(intersection_api::split_corridor),
        )
        .nest_service(
            "/builder",
            ServeDir::new("crates/corridor_builder_web/dist").not_found_service(ServeFile::new(
                "crates/corridor_builder_web/dist/index.html",
            )),
        )
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

    let health_router = Router::new()
        .route("/health", get(handlers::health_check))
        .with_state(state.clone());

    let app = Router::new()
        .merge(build_router(state))
        .merge(setup_router)
        .merge(health_router);

    let listener = tokio::net::TcpListener::bind(&config.bind_address).await?;
    info!("Dashboard available at http://{}", config.bind_address);
    axum::serve(listener, app).await?;
    Ok(())
}

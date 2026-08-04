//! JSON API for corridor creation (manual trace). See
//! `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`.

use axum::Json;
use axum::extract::{Path, State};
use axum::http::StatusCode;

use mobilispect_core::corridor_design::{Coordinate, geometry, repository};
use mobilispect_core::ids::{CorridorId, RemixId};

use crate::web::AppState;

type ApiError = (StatusCode, Json<serde_json::Value>);

fn internal_error(context: &str, err: anyhow::Error) -> ApiError {
    tracing::error!(error = %err, "{context}");
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        Json(serde_json::json!({ "error": "Internal Server Error" })),
    )
}

fn bad_request(message: &str) -> ApiError {
    (
        StatusCode::BAD_REQUEST,
        Json(serde_json::json!({ "error": message })),
    )
}

#[derive(Debug, serde::Deserialize)]
pub struct StartManualCorridorRequest {
    pub name: String,
}

#[derive(Debug, serde::Serialize)]
pub struct StartManualCorridorResponse {
    pub id: i64,
}

/// `POST /api/remixes/:remix_id/corridors/manual` — starts a new in-progress
/// manual trace, scoped to the given remix.
pub async fn start_manual_corridor(
    State(state): State<AppState>,
    Path(remix_id): Path<i64>,
    Json(req): Json<StartManualCorridorRequest>,
) -> Result<(StatusCode, Json<StartManualCorridorResponse>), ApiError> {
    if req.name.trim().is_empty() {
        return Err(bad_request("name must not be blank"));
    }

    let corridor_id =
        repository::start_manual_corridor(&state.db.pool, RemixId::from(remix_id), req.name.trim())
            .await
            .map_err(|e| internal_error("start_manual_corridor", e))?;

    Ok((
        StatusCode::CREATED,
        Json(StartManualCorridorResponse {
            id: corridor_id.as_i64(),
        }),
    ))
}

#[derive(Debug, serde::Deserialize)]
pub struct AddManualPointRequest {
    pub lat: f64,
    pub lon: f64,
}

#[derive(Debug, serde::Serialize)]
pub struct CrossSectionResponse {
    pub id: i64,
    pub position: f64,
    pub lat: f64,
    pub lon: f64,
}

/// `POST /api/corridors/:corridor_id/points` — validates and persists the next
/// point in an in-progress manual trace.
pub async fn add_manual_point(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
    Json(req): Json<AddManualPointRequest>,
) -> Result<(StatusCode, Json<CrossSectionResponse>), ApiError> {
    let coordinate = Coordinate::new(req.lat, req.lon);
    if !coordinate.is_valid() {
        return Err(bad_request("lat/lon is outside valid WGS84 range"));
    }

    let corridor_id = CorridorId::from(corridor_id);
    let existing = repository::get_corridor_cross_sections(&state.db.pool, corridor_id)
        .await
        .map_err(|e| internal_error("add_manual_point: get_corridor_cross_sections", e))?;
    let existing_coordinates: Vec<Coordinate> = existing
        .iter()
        .map(|cs| Coordinate::new(cs.lat, cs.lon))
        .collect();

    if let Err(e) = geometry::validate_next_point(&existing_coordinates, coordinate) {
        return Err(bad_request(&e.to_string()));
    }

    let position = geometry::next_position(&existing_coordinates);
    let cross_section_id =
        repository::insert_cross_section(&state.db.pool, corridor_id, coordinate, position)
            .await
            .map_err(|e| internal_error("add_manual_point: insert_cross_section", e))?;

    Ok((
        StatusCode::CREATED,
        Json(CrossSectionResponse {
            id: cross_section_id.as_i64(),
            position: f64::from(position),
            lat: req.lat,
            lon: req.lon,
        }),
    ))
}

#[derive(Debug, serde::Serialize)]
pub struct FinishManualCorridorResponse {
    pub id: i64,
    pub cross_section_count: i64,
}

/// `POST /api/corridors/:corridor_id/finish` — finalizes an in-progress manual
/// trace, rejecting the request if fewer than the minimum number of points have
/// been placed.
pub async fn finish_manual_corridor(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
) -> Result<Json<FinishManualCorridorResponse>, ApiError> {
    let corridor_id = CorridorId::from(corridor_id);
    let existing = repository::get_corridor_cross_sections(&state.db.pool, corridor_id)
        .await
        .map_err(|e| internal_error("finish_manual_corridor: get_corridor_cross_sections", e))?;
    let existing_coordinates: Vec<Coordinate> = existing
        .iter()
        .map(|cs| Coordinate::new(cs.lat, cs.lon))
        .collect();

    if let Err(e) = geometry::validate_finishable(&existing_coordinates) {
        return Err(bad_request(&e.to_string()));
    }

    repository::finalize_corridor(&state.db.pool, corridor_id)
        .await
        .map_err(|e| internal_error("finish_manual_corridor: finalize_corridor", e))?;

    Ok(Json(FinishManualCorridorResponse {
        id: corridor_id.as_i64(),
        cross_section_count: existing.len() as i64,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::web::SetupState;
    use mobilispect_core::config::Config;
    use mobilispect_core::db::test_utils;
    use std::sync::Arc;
    use tokio::sync::RwLock;

    fn test_config() -> Config {
        Config {
            database_url: String::new(),
            poll_interval_secs: 30,
            bind_address: "0.0.0.0:3000".to_string(),
            on_time_early_threshold_secs: -60,
            on_time_late_threshold_secs: 300,
            retention_days: 30,
            worker_health_bind_address: "0.0.0.0:9090".to_string(),
            transitland_api_key: None,
        }
    }

    async fn test_state() -> (AppState, test_utils::TestDb) {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db.clone(),
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        (state, td)
    }

    async fn seed_remix(state: &AppState) -> i64 {
        sqlx::query(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon) \
             VALUES (1, 'Test Region', 'UTC', 45.40, -73.70, 45.60, -73.50) \
             ON CONFLICT (id) DO NOTHING",
        )
        .execute(&state.db.pool)
        .await
        .unwrap();
        sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id) VALUES ('Test Remix', 1) RETURNING id",
        )
        .fetch_one(&state.db.pool)
        .await
        .unwrap()
    }

    #[tokio::test]
    async fn start_manual_corridor_with_blank_name_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = start_manual_corridor(
            State(state),
            Path(remix_id),
            Json(StartManualCorridorRequest {
                name: "   ".to_string(),
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn start_manual_corridor_happy_path_returns_201() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = start_manual_corridor(
            State(state),
            Path(remix_id),
            Json(StartManualCorridorRequest {
                name: "5th Ave".to_string(),
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0, StatusCode::CREATED);
        assert!(response.1.id > 0);
    }

    #[tokio::test]
    async fn add_manual_point_with_invalid_coordinate_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            RemixId::from(remix_id),
            "Test Corridor",
        )
        .await
        .unwrap();

        let response = add_manual_point(
            State(state),
            Path(corridor_id.as_i64()),
            Json(AddManualPointRequest {
                lat: 200.0,
                lon: -73.6,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn add_manual_point_too_close_to_previous_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            RemixId::from(remix_id),
            "Test Corridor",
        )
        .await
        .unwrap();

        add_manual_point(
            State(state.clone()),
            Path(corridor_id.as_i64()),
            Json(AddManualPointRequest {
                lat: 45.5017,
                lon: -73.5673,
            }),
        )
        .await
        .unwrap();

        let response = add_manual_point(
            State(state),
            Path(corridor_id.as_i64()),
            Json(AddManualPointRequest {
                lat: 45.5017,
                lon: -73.5673,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn finish_manual_corridor_with_fewer_than_two_points_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            RemixId::from(remix_id),
            "Test Corridor",
        )
        .await
        .unwrap();

        let response = finish_manual_corridor(State(state), Path(corridor_id.as_i64())).await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn finish_manual_corridor_happy_path_returns_correct_count() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            RemixId::from(remix_id),
            "Test Corridor",
        )
        .await
        .unwrap();

        add_manual_point(
            State(state.clone()),
            Path(corridor_id.as_i64()),
            Json(AddManualPointRequest {
                lat: 45.5017,
                lon: -73.5673,
            }),
        )
        .await
        .unwrap();
        add_manual_point(
            State(state.clone()),
            Path(corridor_id.as_i64()),
            Json(AddManualPointRequest {
                lat: 45.5031,
                lon: -73.5661,
            }),
        )
        .await
        .unwrap();

        let response = finish_manual_corridor(State(state), Path(corridor_id.as_i64()))
            .await
            .unwrap();

        assert_eq!(response.0.id, corridor_id.as_i64());
        assert_eq!(response.0.cross_section_count, 2);
    }
}

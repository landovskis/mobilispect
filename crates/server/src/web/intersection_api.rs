//! JSON API for the Intersection aggregate: treatment fields, turn
//! movements, and corridor splitting. See
//! `docs/superpowers/specs/2026-08-12-corridor-intersection-aggregate-design.md`.

use axum::Json;
use axum::extract::{Path, State};
use axum::http::StatusCode;

use mobilispect_core::corridor_design::intersection::{
    BusGate, BusStop, TurnConflict, TurnMovementSource,
};
use mobilispect_core::corridor_design::repository;
use mobilispect_core::corridor_design::repository::SplitCorridorError;
use mobilispect_core::corridor_design::splitting::SplitError;
use mobilispect_core::ids::{CorridorId, CrossSectionId, IntersectionId, LaneId};

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

#[derive(Debug, serde::Serialize)]
pub struct IntersectionResponse {
    pub id: i64,
    pub lat: f64,
    pub lon: f64,
    pub osm_node_ids: Vec<i64>,
    pub bus_gate: Option<String>,
    pub turn_conflict: Option<String>,
    pub bus_stop: Option<String>,
}

fn to_intersection_response(
    intersection: mobilispect_core::corridor_design::intersection::Intersection,
) -> IntersectionResponse {
    IntersectionResponse {
        id: intersection.id.as_i64(),
        lat: intersection.lat,
        lon: intersection.lon,
        osm_node_ids: intersection.osm_node_ids,
        bus_gate: intersection.bus_gate.map(|g| g.as_db_str().to_string()),
        turn_conflict: intersection
            .turn_conflict
            .map(|c| c.as_db_str().to_string()),
        bus_stop: intersection.bus_stop.map(|b| b.as_db_str().to_string()),
    }
}

/// `GET /api/intersections/:id`
pub async fn get_intersection(
    State(state): State<AppState>,
    Path(id): Path<i64>,
) -> Result<Json<IntersectionResponse>, ApiError> {
    let intersection = repository::get_intersection(&state.db.pool, IntersectionId::from(id))
        .await
        .map_err(|e| internal_error("get_intersection", e))?;
    Ok(Json(to_intersection_response(intersection)))
}

#[derive(Debug, serde::Deserialize)]
pub struct SetIntersectionTreatmentRequest {
    pub bus_gate: Option<String>,
    pub turn_conflict: Option<String>,
    pub bus_stop: Option<String>,
}

/// `PUT /api/intersections/:id`
pub async fn set_intersection_treatment(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    Json(req): Json<SetIntersectionTreatmentRequest>,
) -> Result<Json<IntersectionResponse>, ApiError> {
    let bus_gate = req
        .bus_gate
        .as_deref()
        .map(|s| BusGate::from_db_str(s).ok_or_else(|| bad_request("unrecognized bus_gate")))
        .transpose()?;
    let turn_conflict = req
        .turn_conflict
        .as_deref()
        .map(|s| {
            TurnConflict::from_db_str(s).ok_or_else(|| bad_request("unrecognized turn_conflict"))
        })
        .transpose()?;
    let bus_stop = req
        .bus_stop
        .as_deref()
        .map(|s| BusStop::from_db_str(s).ok_or_else(|| bad_request("unrecognized bus_stop")))
        .transpose()?;

    let updated = repository::set_intersection_treatment(
        &state.db.pool,
        IntersectionId::from(id),
        bus_gate,
        turn_conflict,
        bus_stop,
    )
    .await
    .map_err(|e| internal_error("set_intersection_treatment", e))?;

    Ok(Json(to_intersection_response(updated)))
}

#[derive(Debug, serde::Serialize)]
pub struct TurnMovementResponse {
    pub from_lane_id: i64,
    pub to_lane_id: i64,
    pub source: String,
}

/// `GET /api/intersections/:id/turn-movements`
pub async fn list_turn_movements(
    State(state): State<AppState>,
    Path(id): Path<i64>,
) -> Result<Json<Vec<TurnMovementResponse>>, ApiError> {
    let movements = repository::list_turn_movements(&state.db.pool, IntersectionId::from(id))
        .await
        .map_err(|e| internal_error("list_turn_movements", e))?;
    Ok(Json(
        movements
            .into_iter()
            .map(|m| TurnMovementResponse {
                from_lane_id: m.from_lane_id.as_i64(),
                to_lane_id: m.to_lane_id.as_i64(),
                source: m.source.as_db_str().to_string(),
            })
            .collect(),
    ))
}

#[derive(Debug, serde::Deserialize)]
pub struct SetTurnMovementRequest {
    pub from_lane_id: i64,
    pub to_lane_id: i64,
}

/// `POST /api/intersections/:id/turn-movements` — always `source = Manual`;
/// only `resolve_corridor_endpoints` (Task 8) creates `Inferred` rows.
pub async fn set_turn_movement(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    Json(req): Json<SetTurnMovementRequest>,
) -> Result<StatusCode, ApiError> {
    repository::set_turn_movement(
        &state.db.pool,
        IntersectionId::from(id),
        LaneId::from(req.from_lane_id),
        LaneId::from(req.to_lane_id),
        TurnMovementSource::Manual,
    )
    .await
    .map_err(|e| internal_error("set_turn_movement", e))?;
    Ok(StatusCode::NO_CONTENT)
}

/// `DELETE /api/intersections/:id/turn-movements/:from_lane_id/:to_lane_id`
pub async fn delete_turn_movement(
    State(state): State<AppState>,
    Path((id, from_lane_id, to_lane_id)): Path<(i64, i64, i64)>,
) -> Result<StatusCode, ApiError> {
    repository::delete_turn_movement(
        &state.db.pool,
        IntersectionId::from(id),
        LaneId::from(from_lane_id),
        LaneId::from(to_lane_id),
    )
    .await
    .map_err(|e| internal_error("delete_turn_movement", e))?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Debug, serde::Deserialize)]
pub struct SplitCorridorRequest {
    pub expected_sequence_version: i64,
}

#[derive(Debug, serde::Serialize)]
pub struct SplitCorridorResponse {
    pub head_corridor_id: i64,
    pub tail_corridor_id: i64,
    pub new_intersection_id: i64,
}

/// `POST /api/corridors/:corridor_id/cross-sections/:cross_section_id/split`
pub async fn split_corridor(
    State(state): State<AppState>,
    Path((corridor_id, cross_section_id)): Path<(i64, i64)>,
    Json(req): Json<SplitCorridorRequest>,
) -> Result<Json<SplitCorridorResponse>, ApiError> {
    let (head_corridor_id, tail_corridor_id, new_intersection_id) =
        repository::split_corridor_at_cross_section(
            &state.db.pool,
            CorridorId::from(corridor_id),
            CrossSectionId::from(cross_section_id),
            req.expected_sequence_version,
        )
        .await
        .map_err(|e| match e {
            // Domain-validation failures: the client can fix these by
            // retrying with a different split point or a fresh
            // `expected_sequence_version`, so they're 400s. Each gets a
            // fixed, client-safe message rather than echoing the
            // underlying error's `Display` text.
            SplitCorridorError::Split(split_err) => {
                tracing::warn!(error = %split_err, "split_corridor: invalid split point");
                bad_request(match split_err {
                    SplitError::NotFound(_) => "cross-section not found in this corridor",
                    SplitError::AlreadyEndpoint(_) => {
                        "cross-section is already an endpoint; nothing to split"
                    }
                    SplitError::TooCloseToEndpoint(_) => {
                        "cross-section is too close to an existing endpoint to split there"
                    }
                })
            }
            SplitCorridorError::CorridorNotFound => {
                tracing::warn!("split_corridor: corridor not found");
                bad_request("corridor not found")
            }
            SplitCorridorError::StaleVersion { expected, actual } => {
                tracing::warn!(expected, actual, "split_corridor: stale sequence version");
                bad_request("corridor has changed since you loaded it; reload and try again")
            }
            // Genuine infrastructure failure (e.g. a DB connection error) --
            // not something the client can fix by changing its request, so
            // this is a 500 with the codebase's standard generic message,
            // logged at error level like every other `internal_error` path.
            SplitCorridorError::Database(err) => internal_error("split_corridor", err),
        })?;

    Ok(Json(SplitCorridorResponse {
        head_corridor_id: head_corridor_id.as_i64(),
        tail_corridor_id: tail_corridor_id.as_i64(),
        new_intersection_id: new_intersection_id.as_i64(),
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::web::SetupState;
    use mobilispect_core::config::Config;
    use mobilispect_core::corridor_design::Coordinate;
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
    async fn get_intersection_returns_treatment_fields() {
        let (state, _td) = test_state().await;
        let intersection_id =
            repository::create_or_match_intersection(&state.db.pool, 45.50, -73.60, None)
                .await
                .unwrap();
        repository::set_intersection_treatment(
            &state.db.pool,
            intersection_id,
            Some(BusGate::SignalControlled),
            None,
            None,
        )
        .await
        .unwrap();

        let response = get_intersection(State(state), Path(intersection_id.as_i64()))
            .await
            .unwrap();

        assert_eq!(response.0.bus_gate.as_deref(), Some("signal_controlled"));
    }

    #[tokio::test]
    async fn set_intersection_treatment_with_unrecognized_value_returns_400() {
        let (state, _td) = test_state().await;
        let intersection_id =
            repository::create_or_match_intersection(&state.db.pool, 45.50, -73.60, None)
                .await
                .unwrap();

        let response = set_intersection_treatment(
            State(state),
            Path(intersection_id.as_i64()),
            Json(SetIntersectionTreatmentRequest {
                bus_gate: Some("spaceship".to_string()),
                turn_conflict: None,
                bus_stop: None,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn split_corridor_happy_path_returns_new_ids() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            mobilispect_core::ids::RemixId::from(remix_id),
            "Corridor",
        )
        .await
        .unwrap();
        let mut cross_section_ids = Vec::new();
        for (position, lat) in [45.500, 45.501, 45.502].into_iter().enumerate() {
            let cs_id = repository::insert_cross_section(
                &state.db.pool,
                corridor_id,
                Coordinate::new(lat, -73.600),
                position as i32,
            )
            .await
            .unwrap();
            cross_section_ids.push(cs_id);
        }

        let response = split_corridor(
            State(state),
            Path((corridor_id.as_i64(), cross_section_ids[1].as_i64())),
            Json(SplitCorridorRequest {
                expected_sequence_version: 0,
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0.head_corridor_id, corridor_id.as_i64());
        assert_ne!(response.0.tail_corridor_id, corridor_id.as_i64());
    }

    #[tokio::test]
    async fn split_corridor_at_endpoint_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            mobilispect_core::ids::RemixId::from(remix_id),
            "Corridor",
        )
        .await
        .unwrap();
        let cs_id = repository::insert_cross_section(
            &state.db.pool,
            corridor_id,
            Coordinate::new(45.500, -73.600),
            0,
        )
        .await
        .unwrap();

        let response = split_corridor(
            State(state),
            Path((corridor_id.as_i64(), cs_id.as_i64())),
            Json(SplitCorridorRequest {
                expected_sequence_version: 0,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn split_corridor_too_close_to_endpoint_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            mobilispect_core::ids::RemixId::from(remix_id),
            "Corridor",
        )
        .await
        .unwrap();
        // Second point is ~1.1m from the first (well under
        // `splitting::MIN_SPLIT_ENDPOINT_DISTANCE_METERS` = 3.0m), so
        // splitting there should be rejected as `TooCloseToEndpoint` rather
        // than accepted.
        let mut cross_section_ids = Vec::new();
        for (position, lat) in [45.500, 45.50001, 45.502, 45.503].into_iter().enumerate() {
            let cs_id = repository::insert_cross_section(
                &state.db.pool,
                corridor_id,
                Coordinate::new(lat, -73.600),
                position as i32,
            )
            .await
            .unwrap();
            cross_section_ids.push(cs_id);
        }

        let response = split_corridor(
            State(state),
            Path((corridor_id.as_i64(), cross_section_ids[1].as_i64())),
            Json(SplitCorridorRequest {
                expected_sequence_version: 0,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn split_corridor_with_stale_sequence_version_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;
        let corridor_id = repository::start_manual_corridor(
            &state.db.pool,
            mobilispect_core::ids::RemixId::from(remix_id),
            "Corridor",
        )
        .await
        .unwrap();
        let mut cross_section_ids = Vec::new();
        for (position, lat) in [45.500, 45.501, 45.502].into_iter().enumerate() {
            let cs_id = repository::insert_cross_section(
                &state.db.pool,
                corridor_id,
                Coordinate::new(lat, -73.600),
                position as i32,
            )
            .await
            .unwrap();
            cross_section_ids.push(cs_id);
        }

        // Corridor's real `sequence_version` starts at 0 (migration 023's
        // default); passing a stale-looking 999 should be rejected as a
        // client-fixable 400, not a 500 -- this is the
        // `SplitCorridorError::StaleVersion` path, distinct from a genuine
        // infrastructure failure (`SplitCorridorError::Database`).
        let response = split_corridor(
            State(state),
            Path((corridor_id.as_i64(), cross_section_ids[1].as_i64())),
            Json(SplitCorridorRequest {
                expected_sequence_version: 999,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }
}

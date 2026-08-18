//! JSON API for cross-section label editing and lane CRUD (REQ-006). See
//! `docs/superpowers/specs/2026-08-09-corridor-lane-editor-design.md`.

use axum::Json;
use axum::extract::{Path, State};
use axum::http::StatusCode;

use mobilispect_core::corridor_design::edit::validate_label;
use mobilispect_core::corridor_design::lanes::{
    AccessMode, Lane, LaneDirection, LaneType, TimeWindow, TimedAccessRule,
};
use mobilispect_core::corridor_design::position::{Neighbors, assign_position};
use mobilispect_core::corridor_design::repository;
use mobilispect_core::ids::{CorridorId, CrossSectionId, LaneId};

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

/// Every failure mode of `update_cross_section_label` (not found, wrong corridor,
/// stale version) collapses into one untyped `anyhow::Error` at the repository
/// layer -- see that function's own doc comment. Mapped uniformly to 409 here
/// (rather than the 500 every other "not found" case in this codebase uses),
/// since this is the one endpoint in this plan with a real optimistic-concurrency
/// story; a not-found row is a degenerate case of "conflicts with what the client
/// expected to be true," not a server fault.
fn conflict(message: &str) -> ApiError {
    (
        StatusCode::CONFLICT,
        Json(serde_json::json!({ "error": message })),
    )
}

fn not_found(message: &str) -> ApiError {
    (
        StatusCode::NOT_FOUND,
        Json(serde_json::json!({ "error": message })),
    )
}

const MAX_LANE_WIDTH_METERS: f64 = 20.0;

fn parse_lane_type(raw: &str) -> Result<LaneType, ApiError> {
    LaneType::from_db_str(raw).ok_or_else(|| bad_request("unrecognized lane_type"))
}

fn parse_lane_direction(raw: &str) -> Result<LaneDirection, ApiError> {
    LaneDirection::from_db_str(raw).ok_or_else(|| bad_request("unrecognized direction"))
}

fn validate_width(width_meters: f64) -> Result<(), ApiError> {
    if width_meters <= 0.0 || width_meters > MAX_LANE_WIDTH_METERS {
        return Err(bad_request("width_meters must be between 0 and 20 meters"));
    }
    Ok(())
}

// --- Cross-sections ---

#[derive(Debug, serde::Serialize)]
pub struct CrossSectionResponse {
    pub id: i64,
    pub position: f64,
    pub label: Option<String>,
    pub lat: f64,
    pub lon: f64,
    pub version: i32,
    /// `Some` only for a corridor's first/last cross-section (an
    /// "endpoint") -- see `CrossSection::intersection_id`'s own doc comment.
    /// Lets the WASM intersection editor resolve which `Intersection` (if
    /// any) a given cross-section belongs to.
    pub intersection_id: Option<i64>,
}

fn to_cross_section_response(
    cs: mobilispect_core::corridor_design::CrossSection,
) -> CrossSectionResponse {
    CrossSectionResponse {
        id: cs.id.as_i64(),
        position: cs.position,
        label: cs.label,
        lat: cs.lat,
        lon: cs.lon,
        version: cs.version,
        intersection_id: cs.intersection_id.map(|id| id.as_i64()),
    }
}

/// `GET /api/corridors/:corridor_id/cross-sections` — for the corridor page's
/// mini-map.
pub async fn list_cross_sections(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
) -> Result<Json<Vec<CrossSectionResponse>>, ApiError> {
    let cross_sections =
        repository::get_corridor_cross_sections(&state.db.pool, CorridorId::from(corridor_id))
            .await
            .map_err(|e| internal_error("list_cross_sections", e))?;

    Ok(Json(
        cross_sections
            .into_iter()
            .map(to_cross_section_response)
            .collect(),
    ))
}

/// `GET /api/cross-sections/:cross_section_id` — minimal single-resource
/// lookup, independent of the owning corridor. Primarily for the WASM
/// intersection editor, which reaches this page from a route that carries
/// only `cross_section_id` (not `corridor_id`) and needs the
/// cross-section's `intersection_id` to load the right `Intersection`.
pub async fn get_cross_section(
    State(state): State<AppState>,
    Path(cross_section_id): Path<i64>,
) -> Result<Json<CrossSectionResponse>, ApiError> {
    let cross_section =
        repository::get_cross_section(&state.db.pool, CrossSectionId::from(cross_section_id))
            .await
            .map_err(|e| internal_error("get_cross_section", e))?
            .ok_or_else(|| not_found("cross-section not found"))?;

    Ok(Json(to_cross_section_response(cross_section)))
}

#[derive(Debug, serde::Deserialize)]
pub struct UpdateLabelRequest {
    pub label: Option<String>,
    pub expected_version: i32,
}

/// `PATCH /api/corridors/:corridor_id/cross-sections/:cross_section_id/label`
pub async fn update_label(
    State(state): State<AppState>,
    Path((corridor_id, cross_section_id)): Path<(i64, i64)>,
    Json(req): Json<UpdateLabelRequest>,
) -> Result<Json<CrossSectionResponse>, ApiError> {
    // `req.label: None` means "clear the label" (skips validate_label entirely --
    // an absent label is never invalid). `Some(raw)` goes through validate_label,
    // which rejects empty/too-long/control-character input as 400.
    let new_label = req
        .label
        .map(|raw| validate_label(&raw).map_err(|e| bad_request(&e.to_string())))
        .transpose()?
        .flatten();

    let updated = repository::update_cross_section_label(
        &state.db.pool,
        CorridorId::from(corridor_id),
        CrossSectionId::from(cross_section_id),
        new_label,
        req.expected_version,
    )
    .await
    .map_err(|e| {
        // Deliberately a fixed message rather than `e.to_string()`. The
        // adjudicated failure modes (not found / wrong corridor / stale
        // version) are all described by it, and `update_cross_section_label`
        // can also surface a raw `sqlx::Error` (pool timeout, connection
        // loss) through the same `?` -- whose text can carry SQL fragments and
        // column names. Logged for operators exactly as `internal_error` does.
        tracing::error!(error = %e, "update_label");
        conflict("cross-section not found, not part of this corridor, or has been edited since you loaded it")
    })?;

    Ok(Json(to_cross_section_response(updated)))
}

// --- Lanes ---

#[derive(Debug, serde::Serialize)]
pub struct TimeWindowResponse {
    pub days: String,
    pub start_time: String,
    pub end_time: String,
}

#[derive(Debug, serde::Serialize)]
pub struct AccessRuleResponse {
    pub time_window: Option<TimeWindowResponse>,
    pub allowed_modes: Vec<String>,
}

#[derive(Debug, serde::Serialize)]
pub struct LaneResponse {
    pub id: i64,
    pub position: f64,
    pub lane_type: String,
    pub width_meters: f64,
    pub direction: String,
    pub access_rules: Vec<AccessRuleResponse>,
}

fn to_lane_response(lane: Lane) -> LaneResponse {
    LaneResponse {
        id: lane.id.as_i64(),
        position: lane.position,
        lane_type: lane.lane_type.as_db_str().to_string(),
        width_meters: lane.width_meters,
        direction: lane.direction.as_db_str().to_string(),
        access_rules: lane
            .access_rules
            .into_iter()
            .map(to_access_rule_response)
            .collect(),
    }
}

fn to_access_rule_response(rule: TimedAccessRule) -> AccessRuleResponse {
    AccessRuleResponse {
        time_window: rule.time_window.map(|w| TimeWindowResponse {
            days: w.days,
            start_time: w.start_time.format("%H:%M").to_string(),
            end_time: w.end_time.format("%H:%M").to_string(),
        }),
        allowed_modes: rule
            .allowed_modes
            .iter()
            .map(|m| m.as_db_str().to_string())
            .collect(),
    }
}

/// `GET /api/cross-sections/:cross_section_id/lanes` — for the lane diagram.
pub async fn list_lanes(
    State(state): State<AppState>,
    Path(cross_section_id): Path<i64>,
) -> Result<Json<Vec<LaneResponse>>, ApiError> {
    let lanes = repository::get_lanes_for_cross_section(
        &state.db.pool,
        CrossSectionId::from(cross_section_id),
    )
    .await
    .map_err(|e| internal_error("list_lanes", e))?;
    Ok(Json(lanes.into_iter().map(to_lane_response).collect()))
}

#[derive(Debug, serde::Deserialize)]
pub struct InsertLaneRequest {
    pub lane_type: String,
    pub width_meters: f64,
    pub direction: String,
    pub neighbor_before_position: Option<f64>,
    pub neighbor_after_position: Option<f64>,
}

/// `POST /api/cross-sections/:cross_section_id/lanes` — inserts a new lane in the
/// gap between two neighbors the client already knows (it's rendering the full
/// ordered lane list), or at either end. `assign_position`'s pure logic runs here,
/// in the shell, before any I/O -- a `PositionAssignmentError` becomes a 400, never
/// reaching the repository layer.
pub async fn insert_lane(
    State(state): State<AppState>,
    Path(cross_section_id): Path<i64>,
    Json(req): Json<InsertLaneRequest>,
) -> Result<(StatusCode, Json<LaneResponse>), ApiError> {
    let lane_type = parse_lane_type(&req.lane_type)?;
    let direction = parse_lane_direction(&req.direction)?;
    validate_width(req.width_meters)?;

    let neighbors = Neighbors {
        before: req.neighbor_before_position,
        after: req.neighbor_after_position,
    };
    let position = assign_position(neighbors).map_err(|e| bad_request(&e.to_string()))?;

    let lane = repository::insert_lane(
        &state.db.pool,
        CrossSectionId::from(cross_section_id),
        lane_type,
        req.width_meters,
        direction,
        position,
    )
    .await
    .map_err(|e| internal_error("insert_lane", e))?;

    Ok((StatusCode::CREATED, Json(to_lane_response(lane))))
}

#[derive(Debug, serde::Deserialize)]
pub struct UpdateLaneRequest {
    pub lane_type: String,
    pub width_meters: f64,
    pub direction: String,
}

/// `PATCH /api/lanes/:lane_id`
pub async fn update_lane(
    State(state): State<AppState>,
    Path(lane_id): Path<i64>,
    Json(req): Json<UpdateLaneRequest>,
) -> Result<Json<LaneResponse>, ApiError> {
    let lane_type = parse_lane_type(&req.lane_type)?;
    let direction = parse_lane_direction(&req.direction)?;
    validate_width(req.width_meters)?;

    let lane = repository::update_lane(
        &state.db.pool,
        LaneId::from(lane_id),
        lane_type,
        req.width_meters,
        direction,
    )
    .await
    .map_err(|e| internal_error("update_lane", e))?;

    Ok(Json(to_lane_response(lane)))
}

/// `DELETE /api/lanes/:lane_id`
pub async fn delete_lane(
    State(state): State<AppState>,
    Path(lane_id): Path<i64>,
) -> Result<StatusCode, ApiError> {
    repository::delete_lane(&state.db.pool, LaneId::from(lane_id))
        .await
        .map_err(|e| internal_error("delete_lane", e))?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Debug, serde::Deserialize)]
pub struct TimeWindowRequest {
    pub days: String,
    pub start_time: String,
    pub end_time: String,
}

#[derive(Debug, serde::Deserialize)]
pub struct AccessRuleRequest {
    pub time_window: Option<TimeWindowRequest>,
    pub allowed_modes: Vec<String>,
}

#[derive(Debug, serde::Deserialize)]
pub struct SetAccessRulesRequest {
    pub rules: Vec<AccessRuleRequest>,
}

/// `PUT /api/lanes/:lane_id/access-rules` — replaces the lane's whole rule list.
pub async fn set_access_rules(
    State(state): State<AppState>,
    Path(lane_id): Path<i64>,
    Json(req): Json<SetAccessRulesRequest>,
) -> Result<Json<Vec<AccessRuleResponse>>, ApiError> {
    let mut rules = Vec::with_capacity(req.rules.len());
    for rule in req.rules {
        let allowed_modes = rule
            .allowed_modes
            .iter()
            .map(|m| {
                AccessMode::from_db_str(m).ok_or_else(|| bad_request("unrecognized access mode"))
            })
            .collect::<Result<Vec<_>, _>>()?;
        let time_window = match rule.time_window {
            Some(tw) => Some(TimeWindow {
                days: tw.days,
                start_time: chrono::NaiveTime::parse_from_str(&tw.start_time, "%H:%M")
                    .map_err(|_| bad_request("start_time must be HH:MM"))?,
                end_time: chrono::NaiveTime::parse_from_str(&tw.end_time, "%H:%M")
                    .map_err(|_| bad_request("end_time must be HH:MM"))?,
            }),
            None => None,
        };
        rules.push(TimedAccessRule {
            time_window,
            allowed_modes,
        });
    }

    repository::set_lane_access_rules(&state.db.pool, LaneId::from(lane_id), &rules)
        .await
        .map_err(|e| internal_error("set_access_rules", e))?;

    Ok(Json(
        rules.into_iter().map(to_access_rule_response).collect(),
    ))
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

    async fn seed_corridor_with_cross_section_and_lanes(state: &AppState) -> (i64, i64, i64, i64) {
        sqlx::query(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon) \
             VALUES (1, 'Test Region', 'UTC', 45.40, -73.70, 45.60, -73.50) \
             ON CONFLICT (id) DO NOTHING",
        )
        .execute(&state.db.pool)
        .await
        .unwrap();
        let remix_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id) VALUES ('Test Remix', 1) RETURNING id",
        )
        .fetch_one(&state.db.pool)
        .await
        .unwrap();
        let corridor_id: i64 = sqlx::query_scalar(
            "INSERT INTO corridors (name, geometry_source, remix_id) VALUES ('Test Corridor', 'manual', $1) RETURNING id",
        )
        .bind(remix_id)
        .fetch_one(&state.db.pool)
        .await
        .unwrap();
        let cross_section_id: i64 = sqlx::query_scalar(
            "INSERT INTO cross_sections (corridor_id, position, lat, lon, label) \
             VALUES ($1, 0, 45.50, -73.60, 'Main St @ 5th') RETURNING id",
        )
        .bind(corridor_id)
        .fetch_one(&state.db.pool)
        .await
        .unwrap();
        let lane_id: i64 = sqlx::query_scalar(
            "INSERT INTO lanes (cross_section_id, position, lane_type, width_meters, direction) \
             VALUES ($1, 1, 'travel', 3.0, 'forward') RETURNING id",
        )
        .bind(cross_section_id)
        .fetch_one(&state.db.pool)
        .await
        .unwrap();
        (remix_id, corridor_id, cross_section_id, lane_id)
    }

    #[tokio::test]
    async fn list_cross_sections_returns_seeded_cross_section() {
        let (state, _td) = test_state().await;
        let (_remix_id, corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = list_cross_sections(State(state), Path(corridor_id))
            .await
            .unwrap();

        assert_eq!(response.0.len(), 1);
        assert_eq!(response.0[0].id, cross_section_id);
        assert_eq!(response.0[0].label.as_deref(), Some("Main St @ 5th"));
        assert_eq!(response.0[0].version, 1);
    }

    #[tokio::test]
    async fn get_cross_section_returns_seeded_cross_section() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = get_cross_section(State(state), Path(cross_section_id))
            .await
            .unwrap();

        assert_eq!(response.0.id, cross_section_id);
        assert_eq!(response.0.label.as_deref(), Some("Main St @ 5th"));
        // Seeded via a raw INSERT that omits `intersection_id`, so this
        // cross-section is not an endpoint of any intersection.
        assert_eq!(response.0.intersection_id, None);
    }

    #[tokio::test]
    async fn get_cross_section_with_unknown_id_returns_404() {
        let (state, _td) = test_state().await;

        let response = get_cross_section(State(state), Path(999_999)).await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::NOT_FOUND);
    }

    #[tokio::test]
    async fn update_label_happy_path_returns_incremented_version() {
        let (state, _td) = test_state().await;
        let (_remix_id, corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = update_label(
            State(state),
            Path((corridor_id, cross_section_id)),
            Json(UpdateLabelRequest {
                label: Some("Main St @ 5th Ave (widened)".to_string()),
                expected_version: 1,
            }),
        )
        .await
        .unwrap();

        assert_eq!(
            response.0.label.as_deref(),
            Some("Main St @ 5th Ave (widened)")
        );
        assert_eq!(response.0.version, 2);
    }

    #[tokio::test]
    async fn update_label_with_blank_label_returns_400() {
        let (state, _td) = test_state().await;
        let (_remix_id, corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = update_label(
            State(state),
            Path((corridor_id, cross_section_id)),
            Json(UpdateLabelRequest {
                label: Some("   ".to_string()),
                expected_version: 1,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn update_label_with_stale_version_returns_409() {
        let (state, _td) = test_state().await;
        let (_remix_id, corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = update_label(
            State(state),
            Path((corridor_id, cross_section_id)),
            Json(UpdateLabelRequest {
                label: Some("wrong version".to_string()),
                expected_version: 999,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::CONFLICT);
    }

    #[tokio::test]
    async fn list_lanes_returns_seeded_lane() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = list_lanes(State(state), Path(cross_section_id))
            .await
            .unwrap();

        assert_eq!(response.0.len(), 1);
        assert_eq!(response.0[0].id, lane_id);
        assert_eq!(response.0[0].lane_type, "travel");
    }

    #[tokio::test]
    async fn insert_lane_happy_path_returns_201() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = insert_lane(
            State(state),
            Path(cross_section_id),
            Json(InsertLaneRequest {
                lane_type: "sidewalk".to_string(),
                width_meters: 1.8,
                direction: "none".to_string(),
                neighbor_before_position: None,
                neighbor_after_position: Some(1.0),
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0, StatusCode::CREATED);
        assert_eq!(response.1.lane_type, "sidewalk");
        assert!(response.1.position < 1.0);
    }

    #[tokio::test]
    async fn insert_lane_with_invalid_width_returns_400() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = insert_lane(
            State(state),
            Path(cross_section_id),
            Json(InsertLaneRequest {
                lane_type: "sidewalk".to_string(),
                width_meters: 0.0,
                direction: "none".to_string(),
                neighbor_before_position: None,
                neighbor_after_position: Some(1.0),
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn insert_lane_with_non_monotonic_neighbors_returns_400() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, _lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = insert_lane(
            State(state),
            Path(cross_section_id),
            Json(InsertLaneRequest {
                lane_type: "sidewalk".to_string(),
                width_meters: 1.8,
                direction: "none".to_string(),
                neighbor_before_position: Some(5.0),
                neighbor_after_position: Some(3.0),
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn update_lane_happy_path() {
        let (state, td) = test_state().await;
        let (_remix_id, _corridor_id, cross_section_id, lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = update_lane(
            State(state),
            Path(lane_id),
            Json(UpdateLaneRequest {
                lane_type: "turn".to_string(),
                width_meters: 3.2,
                direction: "backward".to_string(),
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0.lane_type, "turn");
        assert_eq!(response.0.width_meters, 3.2);
        assert_eq!(response.0.direction, "backward");

        // Independent read-back: the handler's response body is built from
        // `repository::update_lane`'s return value, which echoes back the
        // type/width/direction it was *given* rather than what the row now
        // holds. A fresh query is the only thing here that can fail if the
        // UPDATE stopped writing one of those columns.
        let persisted = repository::get_lanes_for_cross_section(
            &td.db.pool,
            CrossSectionId::from(cross_section_id),
        )
        .await
        .unwrap()
        .into_iter()
        .find(|l| l.id.as_i64() == lane_id)
        .expect("the updated lane is still in the cross-section");
        assert_eq!(persisted.lane_type.as_db_str(), "turn");
        assert_eq!(persisted.width_meters, 3.2);
        assert_eq!(persisted.direction.as_db_str(), "backward");
    }

    #[tokio::test]
    async fn delete_lane_happy_path_returns_204() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, _cross_section_id, lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = delete_lane(State(state), Path(lane_id)).await.unwrap();

        assert_eq!(response, StatusCode::NO_CONTENT);
    }

    #[tokio::test]
    async fn set_access_rules_happy_path() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, _cross_section_id, lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = set_access_rules(
            State(state),
            Path(lane_id),
            Json(SetAccessRulesRequest {
                rules: vec![AccessRuleRequest {
                    time_window: Some(TimeWindowRequest {
                        days: "weekdays".to_string(),
                        start_time: "07:00".to_string(),
                        end_time: "09:00".to_string(),
                    }),
                    allowed_modes: vec!["transit".to_string()],
                }],
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0.len(), 1);
        assert_eq!(response.0[0].allowed_modes, vec!["transit".to_string()]);
        let window = response.0[0].time_window.as_ref().unwrap();
        assert_eq!(window.start_time, "07:00");
    }

    #[tokio::test]
    async fn set_access_rules_with_unrecognized_mode_returns_400() {
        let (state, _td) = test_state().await;
        let (_remix_id, _corridor_id, _cross_section_id, lane_id) =
            seed_corridor_with_cross_section_and_lanes(&state).await;

        let response = set_access_rules(
            State(state),
            Path(lane_id),
            Json(SetAccessRulesRequest {
                rules: vec![AccessRuleRequest {
                    time_window: None,
                    allowed_modes: vec!["spaceship".to_string()],
                }],
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }
}

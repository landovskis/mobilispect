//! HTTP handlers for manually tracing a corridor's road geometry by clicking points
//! on a map, as an alternative to importing it (see `corridor_import.rs`). See
//! `docs/ddd/bounded-context-canvas.md` (Corridor Design context) and the Corridor
//! Segment Editor PRD, REQ-002.
//!
//! Routes are not yet registered in `web/mod.rs` — that's Loop B's job.

use axum::extract::{Path, State};
use axum::response::IntoResponse;
use axum::{Json, http::StatusCode};

use crate::web::AppState;

/// Request body for `POST /corridors/manual`: starts a new in-progress manual trace.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct StartManualCorridorRequest {
    pub name: String,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct StartManualCorridorResponse {
    pub id: i64,
}

/// Request body for `POST /corridors/:corridor_id/points`: one clicked point.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct AddManualPointRequest {
    pub lat: f64,
    pub lon: f64,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct CrossSectionResponse {
    pub id: i64,
    pub position: i32,
    pub lat: f64,
    pub lon: f64,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct FinishManualCorridorResponse {
    pub id: i64,
    pub cross_section_count: i64,
}

/// `POST /corridors/manual` — creates a new corridor with `geometry_source =
/// 'manual'` and no points yet, ready for the analyst to start clicking.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-002-08 (Loop B GREEN pass).
pub async fn start_manual_corridor(
    State(state): State<AppState>,
    Json(req): Json<StartManualCorridorRequest>,
) -> axum::response::Response {
    let _ = (state, req);
    StatusCode::NOT_IMPLEMENTED.into_response()
}

/// `POST /corridors/:corridor_id/points` — validates and persists the next point in
/// an in-progress manual trace.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-002-08 (Loop B GREEN pass).
pub async fn add_manual_point(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
    Json(req): Json<AddManualPointRequest>,
) -> axum::response::Response {
    let _ = (state, corridor_id, req);
    StatusCode::NOT_IMPLEMENTED.into_response()
}

/// `DELETE /corridors/:corridor_id/points/last` — removes the most recently placed
/// point from an in-progress manual trace.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-002-08 (Loop B GREEN pass).
pub async fn undo_last_point(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
) -> axum::response::Response {
    let _ = (state, corridor_id);
    StatusCode::NOT_IMPLEMENTED.into_response()
}

/// `POST /corridors/:corridor_id/finish` — finalizes an in-progress manual trace,
/// rejecting the request if fewer than the minimum number of points have been placed.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-002-08 (Loop B GREEN pass).
pub async fn finish_manual_corridor(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
) -> axum::response::Response {
    let _ = (state, corridor_id);
    StatusCode::NOT_IMPLEMENTED.into_response()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::web::SetupState;
    use axum::http::StatusCode;
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

    /// TC-REQ-002-02: "Finish Trace" attempted with fewer than 2 points (the boundary
    /// case: exactly 1 point placed) is rejected with 400.
    ///
    /// Routes aren't registered in the router yet (Loop B), so this calls the handler
    /// function directly, same pattern as `corridor_import.rs`'s tests. There is no
    /// real corridor/point setup here since the handler is a bare 501 stub — this
    /// exercises the handler's current (incorrect) response and will keep failing
    /// until Loop B implements the real 400 `INSUFFICIENT_POINTS` behavior.
    #[tokio::test]
    async fn test_tc_req_002_02_finish_trace_with_one_point_returns_400() {
        let (state, _td) = test_state().await;

        let response = finish_manual_corridor(State(state), Path(1)).await;

        assert_eq!(
            response.status(),
            StatusCode::BAD_REQUEST,
            "TC-REQ-002-02: finishing a trace with fewer than 2 points should return 400 INSUFFICIENT_POINTS"
        );
    }

    /// TC-REQ-002-03: a duplicate/degenerate point click is rejected with 400.
    ///
    /// Same caveat as above — the handler is a bare 501 stub, so this fails until
    /// Loop B wires up real validation via `geometry::validate_next_point`.
    #[tokio::test]
    async fn test_tc_req_002_03_duplicate_point_returns_400() {
        let (state, _td) = test_state().await;

        let req = AddManualPointRequest {
            lat: 45.5017,
            lon: -73.5673,
        };
        let response = add_manual_point(State(state), Path(1), Json(req)).await;

        assert_eq!(
            response.status(),
            StatusCode::BAD_REQUEST,
            "TC-REQ-002-03: a duplicate/too-close point click should return 400 INVALID_POINT"
        );
    }
}

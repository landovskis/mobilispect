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

/// Request body for `POST /corridors/:corridor_id/cross-sections`: the anchor to
/// insert after (`None` means "insert at the start of the sequence") plus the new
/// cross-section's coordinate.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct AddCrossSectionRequest {
    pub insert_after_cross_section_id: Option<i64>,
    pub lat: f64,
    pub lon: f64,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct AddCrossSectionResponse {
    pub id: i64,
    pub corridor_id: i64,
    pub sequence_index: i64,
    pub sequence_total: i64,
    pub lat: f64,
    pub lon: f64,
}

/// `POST /corridors/:corridor_id/cross-sections` — inserts a new cross-section into
/// an existing corridor's sequence, at a fractional position between the two rows
/// bracketing `insert_after_cross_section_id` (see
/// `corridor_design::position::assign_position`).
///
/// Route is not yet registered in `web/mod.rs` — that's Loop B's job.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-004-07 (Loop B GREEN pass).
pub async fn add_cross_section(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
    Json(req): Json<AddCrossSectionRequest>,
) -> axum::response::Response {
    let _ = (state, corridor_id, req);
    StatusCode::NOT_IMPLEMENTED.into_response()
}

/// Request body for `PATCH /corridors/:corridor_id/cross-sections/order`: the
/// caller's last-known `corridors.sequence_version` (optimistic-concurrency
/// check) plus the corridor's cross-sections in their desired new order.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct ReorderCrossSectionsRequest {
    pub expected_version: i64,
    pub cross_section_ids: Vec<i64>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct ReorderCrossSectionsResponse {
    pub version: i64,
    pub cross_sections: Vec<CrossSectionResponse>,
}

/// `PATCH /corridors/:corridor_id/cross-sections/order` — reorders every
/// cross-section in a corridor's sequence to match the submitted ID order,
/// rejecting the request if it isn't exactly a permutation of the corridor's
/// current cross-sections (see
/// `corridor_design::position::compute_reordered_positions`) or if
/// `expected_version` no longer matches `corridors.sequence_version`.
///
/// Route is not yet registered in `web/mod.rs` — that's Loop B's job.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-005-07 (Loop B GREEN pass).
pub async fn reorder_cross_sections(
    State(state): State<AppState>,
    Path(corridor_id): Path<i64>,
    Json(req): Json<ReorderCrossSectionsRequest>,
) -> axum::response::Response {
    let _ = (state, corridor_id, req);
    StatusCode::NOT_IMPLEMENTED.into_response()
}

/// Decides whether the OSM attribution partial should be included in the corridor
/// editor page's template context, from the corridor's `geometry_source`.
///
/// Thin wiring around `core::corridor_design::attribution::attribution_visible` —
/// see that function's doc comment for the fail-safe rationale on `None`. There is no
/// real editor-page handler yet to call this from (that's a later requirement's Loop
/// B); this exists so the "thread geometry_source into template context" task
/// (IMP-REQ-003-03) has a concrete, testable seam now.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-003-03 (Loop B GREEN pass).
pub fn attribution_context(
    geometry_source: Option<mobilispect_core::corridor_design::GeometrySource>,
) -> bool {
    let _ = geometry_source;
    unimplemented!("IMP-REQ-003-03: attribution_context not yet implemented")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::web::SetupState;
    use axum::http::StatusCode;
    use mobilispect_core::config::Config;
    use mobilispect_core::corridor_design::GeometrySource;
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

    // --- REQ-003: OSM attribution visibility ---
    //
    // There is no real editor-page handler yet (that lands in a later requirement's
    // Loop B), so TC-REQ-003-1/2/3/4 below are necessarily simplified: each calls
    // `attribution_context` directly with the `geometry_source` the real scenario's
    // precondition would seed, and asserts the boolean result, rather than seeding a
    // corridor in Postgres, requesting `/corridors/{id}/edit`, and asserting on
    // rendered HTML (`.osm-attribution` element, exact text/href). Full DOM-level
    // assertion is deferred to when the editor page exists (IMP-REQ-003-05 onward).

    /// Approximates TC-REQ-003-1 (Imported corridor displays OSM attribution strip in
    /// editor): an imported corridor's geometry_source should make the attribution
    /// context `true`.
    #[test]
    fn test_tc_req_003_1_imported_corridor_attribution_context_true() {
        let result = attribution_context(Some(GeometrySource::Imported));
        assert_eq!(result, true);
    }

    /// Approximates TC-REQ-003-2 (Manual-only corridor does not display OSM
    /// attribution strip): a manual corridor's geometry_source should make the
    /// attribution context `false`.
    #[test]
    fn test_tc_req_003_2_manual_corridor_attribution_context_false() {
        let result = attribution_context(Some(GeometrySource::Manual));
        assert_eq!(result, false);
    }

    /// Approximates TC-REQ-003-3 (Corridor with partially-imported geometry still
    /// shows attribution at corridor level): the decision is corridor-level, driven
    /// solely by `geometry_source = 'imported'`, regardless of what fraction of the
    /// corridor's individual cross-sections were subsequently hand-edited — this test
    /// cannot express "partially edited" without real cross_sections rows, so it
    /// asserts the same `Imported` input as TC-REQ-003-1 still yields `true`.
    #[test]
    fn test_tc_req_003_3_partially_imported_corridor_attribution_context_true() {
        let result = attribution_context(Some(GeometrySource::Imported));
        assert_eq!(result, true);
    }

    /// Approximates TC-REQ-003-4 (Corridor with missing geometry_source fails safe to
    /// showing attribution): a `None` geometry_source (data-migration gap) should
    /// fail safe to `true`. The real test case also asserts a 200 response and a
    /// `WARN`-level log line referencing the corridor ID — both require the real
    /// editor-page handler and are deferred alongside it.
    #[test]
    fn test_tc_req_003_4_missing_geometry_source_attribution_context_fails_safe_true() {
        let result = attribution_context(None);
        assert_eq!(result, true);
    }

    /// TC-REQ-003-5 (No analyst-facing control can hide or dismiss the attribution
    /// strip): requires inspecting real rendered HTML/DOM for the absence of a
    /// dismiss control and verifying no "hide overlays" editor action removes it —
    /// there is no real editor page or rendered `_osm_attribution.html` output yet to
    /// inspect, so this cannot be meaningfully tested at the unit level.
    #[ignore = "IMP-REQ-003-05/06: needs the real editor page + rendered partial to inspect DOM for a dismiss control"]
    #[test]
    fn test_tc_req_003_5_no_dismiss_control_on_attribution_strip() {
        todo!()
    }
}

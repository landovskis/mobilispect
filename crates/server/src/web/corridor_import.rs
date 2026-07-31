//! HTTP handlers for importing corridor geometry from external road-network data
//! (OSM via Overpass). See `docs/ddd/bounded-context-canvas.md` (Corridor Design
//! context) and the Corridor Segment Editor PRD, REQ-001.
//!
//! Routes are not yet registered in `web/mod.rs` — see IMP-REQ-001-12 (Loop B).

use axum::extract::State;
use axum::response::IntoResponse;
use axum::{Json, http::StatusCode};

use crate::web::AppState;

/// A bounding box to search for road geometry within, for the map-search preview step.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct FetchPreviewRequest {
    pub min_lat: f64,
    pub min_lon: f64,
    pub max_lat: f64,
    pub max_lon: f64,
}

/// Request body for `POST /api/corridors`: a corridor name plus the source geometry
/// to import, as a GeoJSON FeatureCollection. `source_geometry` is left as
/// `serde_json::Value` for now — typed GeoJSON parsing is Loop B's job
/// (`geometry::normalize_corridor_geometry` consumes the parsed form).
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct ImportCorridorRequest {
    pub name: String,
    pub source_geometry: serde_json::Value,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct ImportCorridorResponse {
    pub id: i64,
    pub cross_section_count: i64,
}

/// `POST /api/corridors/import/fetch-preview` — searches OSM/Overpass for road
/// geometry within a bounding box, for the analyst to preview before import.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-001-09 (Loop B GREEN pass).
pub async fn fetch_preview(
    State(state): State<AppState>,
    Json(req): Json<FetchPreviewRequest>,
) -> axum::response::Response {
    let _ = (state, req);
    StatusCode::NOT_IMPLEMENTED.into_response()
}

/// `POST /api/corridors` — normalizes and persists a corridor from imported source
/// geometry.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-001-07/IMP-REQ-001-06 (Loop B GREEN pass).
pub async fn import_corridor(
    State(state): State<AppState>,
    Json(req): Json<ImportCorridorRequest>,
) -> axum::response::Response {
    let _ = (state, req);
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

    /// Reads a fixture file from `crates/core/tests/fixtures/corridor_design/`.
    /// Fixtures are colocated with core (see `corridor_design::geometry`'s and
    /// `corridor_design::repository`'s tests) rather than duplicated per-crate.
    fn load_fixture(name: &str) -> String {
        let path = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../core/tests/fixtures/corridor_design")
            .join(name);
        std::fs::read_to_string(&path)
            .unwrap_or_else(|e| panic!("failed to read fixture {path:?}: {e}"))
    }

    /// TC-REQ-001-2: importing the smallest valid corridor (a single 2-point way)
    /// returns 201 Created with cross_section_count = 2.
    ///
    /// Routes aren't registered in the router yet (IMP-REQ-001-12), so this calls
    /// the handler function directly with constructed extractors rather than going
    /// through `build_router` + a test client — it still exercises the same handler
    /// code path.
    #[tokio::test]
    async fn test_tc_req_001_2_import_smallest_valid_corridor_returns_201() {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };

        let fixture = load_fixture("single_segment_min.geojson");
        let source_geometry: serde_json::Value = serde_json::from_str(&fixture).unwrap();
        let req = ImportCorridorRequest {
            name: "Minimal Corridor".to_string(),
            source_geometry,
        };

        let response = import_corridor(State(state), Json(req)).await;

        assert_eq!(
            response.status(),
            StatusCode::CREATED,
            "TC-REQ-001-2: importing a single 2-point way should return 201 Created"
        );

        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
            .await
            .unwrap();
        let body: ImportCorridorResponse = serde_json::from_slice(&bytes)
            .expect("response body should deserialize as ImportCorridorResponse");
        assert_eq!(body.cross_section_count, 2);
    }

    /// TC-REQ-001-6: a malformed (truncated) JSON body fails to deserialize into
    /// `ImportCorridorRequest`. In the real request path this is what Axum's `Json`
    /// extractor rejection surfaces as a 400 before the handler body ever runs —
    /// asserting the full request-level 400 response is deferred to Loop B once
    /// `POST /api/corridors` is registered in the router (IMP-REQ-001-12).
    #[test]
    fn test_tc_req_001_6_malformed_json_body_fails_deserialization() {
        let result = serde_json::from_str::<ImportCorridorRequest>("{truncated");
        assert!(result.is_err());
    }

    /// TC-REQ-001-5: Overpass unreachable across all mirrors/retries should return
    /// 503 with `corridor.import.source_unavailable`. There is no Overpass fetch
    /// shell yet to call (`fetch_preview` is a bare stub with no HTTP client), so
    /// this can't be meaningfully written as a real test yet.
    #[test]
    #[ignore = "IMP-REQ-001-09: needs Overpass fetch shell, implemented in Loop B"]
    fn test_tc_req_001_5_overpass_unreachable_returns_503() {
        todo!()
    }
}

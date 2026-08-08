//! JSON API for OSM-based corridor import (search + import). See
//! `docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md`.

use std::collections::HashMap;

use axum::Json;
use axum::extract::{Path, State};
use axum::http::StatusCode;

use mobilispect_core::corridor_design::geometry::{
    RawGeometry, RawPoint, RawWaySegment, normalize_corridor_geometry,
};
use mobilispect_core::corridor_design::lanes_from_osm::derive_lanes_from_osm_tags;
use mobilispect_core::corridor_design::{Coordinate, repository};
use mobilispect_core::ids::RemixId;
use mobilispect_core::osm::OverpassClient;
use mobilispect_core::remix::BoundingBox;

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
pub struct SearchStreetsRequest {
    pub min_lat: f64,
    pub min_lon: f64,
    pub max_lat: f64,
    pub max_lon: f64,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct OsmPointResponse {
    pub lat: f64,
    pub lon: f64,
    pub osm_node_id: Option<i64>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct OsmWayResponse {
    pub osm_way_id: i64,
    pub points: Vec<OsmPointResponse>,
    pub tags: HashMap<String, String>,
}

/// `POST /api/remixes/:remix_id/streets` — fetches OSM ways within a
/// bounding box for the analyst to select from. Read-only; persists nothing.
pub async fn search_streets(
    State(_state): State<AppState>,
    Path(_remix_id): Path<i64>,
    Json(req): Json<SearchStreetsRequest>,
) -> Result<Json<Vec<OsmWayResponse>>, ApiError> {
    let bbox = BoundingBox {
        min_lat: req.min_lat,
        min_lon: req.min_lon,
        max_lat: req.max_lat,
        max_lon: req.max_lon,
    };
    if bbox.validate().is_err() {
        return Err(bad_request("bounding box is invalid"));
    }

    let client = OverpassClient::new();
    let ways = client
        .fetch_ways_in_bbox(bbox)
        .await
        .map_err(|e| internal_error("search_streets: fetch_ways_in_bbox", anyhow::Error::new(e)))?;

    Ok(Json(
        ways.into_iter()
            .map(|way| OsmWayResponse {
                osm_way_id: way.osm_way_id,
                points: way
                    .points
                    .into_iter()
                    .map(|p| OsmPointResponse {
                        lat: p.coordinate.lat,
                        lon: p.coordinate.lon,
                        osm_node_id: p.osm_node_id,
                    })
                    .collect(),
                tags: way.tags,
            })
            .collect(),
    ))
}

#[derive(Debug, serde::Deserialize)]
pub struct ImportCorridorRequest {
    pub name: String,
    pub ways: Vec<OsmWayResponse>,
}

#[derive(Debug, serde::Serialize)]
pub struct ImportCorridorResponse {
    pub id: i64,
}

/// `POST /api/remixes/:remix_id/corridors/import` — normalizes the analyst's
/// selected OSM ways into one corridor, derives baseline lanes per
/// cross-section from each way's tags, and persists both.
pub async fn import_corridor(
    State(state): State<AppState>,
    Path(remix_id): Path<i64>,
    Json(req): Json<ImportCorridorRequest>,
) -> Result<(StatusCode, Json<ImportCorridorResponse>), ApiError> {
    if req.name.trim().is_empty() {
        return Err(bad_request("name must not be blank"));
    }
    if req.ways.is_empty() {
        return Err(bad_request("select at least one street"));
    }

    // Built before `req.ways` is consumed below — looked up per cross-section
    // by which way it originated from, once cross-sections exist.
    let tags_by_way_id: HashMap<i64, HashMap<String, String>> = req
        .ways
        .iter()
        .map(|w| (w.osm_way_id, w.tags.clone()))
        .collect();

    let raw = RawGeometry {
        segments: req
            .ways
            .into_iter()
            .map(|way| RawWaySegment {
                osm_way_id: Some(way.osm_way_id),
                points: way
                    .points
                    .into_iter()
                    .map(|p| RawPoint {
                        coordinate: Coordinate::new(p.lat, p.lon),
                        osm_node_id: p.osm_node_id,
                    })
                    .collect(),
            })
            .collect(),
    };

    let normalized = normalize_corridor_geometry(raw).map_err(|e| bad_request(&e.to_string()))?;

    // `corridors.import_format` has a CHECK constraint (migration 021) that
    // only allows 'geojson_osm_export' -- predates this plan, and this plan
    // adds no new migration, so this is the only valid value here despite
    // the OSM data actually arriving via Overpass, not a GeoJSON export.
    // Provenance is still fully captured by `geometry_source = 'imported'`
    // and the OSM attribution string passed below.
    let corridor_id = repository::insert_corridor(
        &state.db.pool,
        RemixId::from(remix_id),
        req.name.trim(),
        "geojson_osm_export",
        Some("© OpenStreetMap contributors"),
        &normalized,
    )
    .await
    .map_err(|e| internal_error("import_corridor: insert_corridor", e))?;

    let cross_sections = repository::get_corridor_cross_sections(&state.db.pool, corridor_id)
        .await
        .map_err(|e| internal_error("import_corridor: get_corridor_cross_sections", e))?;

    let empty_tags: HashMap<String, String> = HashMap::new();
    for cross_section in cross_sections {
        let tags = cross_section
            .osm_way_id
            .and_then(|id| tags_by_way_id.get(&id))
            .unwrap_or(&empty_tags);
        let drafts = derive_lanes_from_osm_tags(tags);
        repository::insert_lanes_for_cross_section(&state.db.pool, cross_section.id, &drafts)
            .await
            .map_err(|e| internal_error("import_corridor: insert_lanes_for_cross_section", e))?;
    }

    Ok((
        StatusCode::CREATED,
        Json(ImportCorridorResponse {
            id: corridor_id.as_i64(),
        }),
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
    use wiremock::matchers::method;
    use wiremock::{Mock, MockServer, ResponseTemplate};

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

    fn sample_way_response(
        osm_way_id: i64,
        points: Vec<(f64, f64, i64)>,
        tags: HashMap<String, String>,
    ) -> OsmWayResponse {
        OsmWayResponse {
            osm_way_id,
            points: points
                .into_iter()
                .map(|(lat, lon, node_id)| OsmPointResponse {
                    lat,
                    lon,
                    osm_node_id: Some(node_id),
                })
                .collect(),
            tags,
        }
    }

    // --- search_streets ---
    //
    // These tests set `OVERPASS_BASE_URL` via `std::env::set_var` to point at a
    // per-test wiremock server. This project's test runner is `cargo nextest`,
    // which isolates every test in its own process (unlike plain `cargo test`'s
    // shared-process threads) — so this env var never leaks between tests.
    // `std::env::set_var` is `unsafe` as of this project's Rust edition (2024);
    // wrapped accordingly below.

    #[tokio::test]
    async fn search_streets_with_invalid_bbox_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = search_streets(
            State(state),
            Path(remix_id),
            Json(SearchStreetsRequest {
                min_lat: 46.0,
                min_lon: -73.70,
                max_lat: 45.0, // max < min: invalid
                max_lon: -73.50,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn search_streets_happy_path_returns_parsed_ways() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "version": 0.6,
                "elements": [{
                    "type": "way",
                    "id": 777,
                    "nodes": [1, 2],
                    "geometry": [
                        {"lat": 45.500, "lon": -73.580},
                        {"lat": 45.501, "lon": -73.579}
                    ],
                    "tags": {"highway": "residential", "name": "Test St"}
                }]
            })))
            .mount(&server)
            .await;
        unsafe {
            std::env::set_var("OVERPASS_BASE_URL", server.uri());
        }

        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = search_streets(
            State(state),
            Path(remix_id),
            Json(SearchStreetsRequest {
                min_lat: 45.40,
                min_lon: -73.70,
                max_lat: 45.60,
                max_lon: -73.50,
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0.len(), 1);
        assert_eq!(response.0[0].osm_way_id, 777);
        assert_eq!(response.0[0].points.len(), 2);
        assert_eq!(response.0[0].tags.get("name"), Some(&"Test St".to_string()));
    }

    #[tokio::test]
    async fn search_streets_returns_500_when_overpass_unreachable() {
        // Port 1 is a reserved/privileged port nothing listens on locally —
        // connection refused, simulating Overpass being unreachable.
        unsafe {
            std::env::set_var("OVERPASS_BASE_URL", "http://127.0.0.1:1");
        }

        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = search_streets(
            State(state),
            Path(remix_id),
            Json(SearchStreetsRequest {
                min_lat: 45.40,
                min_lon: -73.70,
                max_lat: 45.60,
                max_lon: -73.50,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::INTERNAL_SERVER_ERROR);
    }

    // --- import_corridor ---

    #[tokio::test]
    async fn import_corridor_with_blank_name_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = import_corridor(
            State(state),
            Path(remix_id),
            Json(ImportCorridorRequest {
                name: "   ".to_string(),
                ways: vec![sample_way_response(
                    1,
                    vec![(45.500, -73.580, 10), (45.501, -73.579, 11)],
                    HashMap::new(),
                )],
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn import_corridor_with_no_ways_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let response = import_corridor(
            State(state),
            Path(remix_id),
            Json(ImportCorridorRequest {
                name: "Test Import".to_string(),
                ways: vec![],
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn import_corridor_with_disconnected_ways_returns_400() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let way_a = sample_way_response(
            1,
            vec![(45.500, -73.580, 10), (45.501, -73.579, 11)],
            HashMap::new(),
        );
        // ~150m away -- no shared endpoint with way_a.
        let way_b = sample_way_response(
            2,
            vec![(45.503, -73.575, 12), (45.504, -73.574, 13)],
            HashMap::new(),
        );

        let response = import_corridor(
            State(state),
            Path(remix_id),
            Json(ImportCorridorRequest {
                name: "Test Import".to_string(),
                ways: vec![way_a, way_b],
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn import_corridor_happy_path_persists_corridor_and_lanes() {
        let (state, _td) = test_state().await;
        let remix_id = seed_remix(&state).await;

        let mut tags = HashMap::new();
        tags.insert("highway".to_string(), "residential".to_string());
        tags.insert("lanes".to_string(), "2".to_string());

        let way = sample_way_response(42, vec![(45.500, -73.580, 10), (45.501, -73.579, 11)], tags);

        let response = import_corridor(
            State(state.clone()),
            Path(remix_id),
            Json(ImportCorridorRequest {
                name: "Test Imported Corridor".to_string(),
                ways: vec![way],
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0, StatusCode::CREATED);
        let corridor_id = mobilispect_core::ids::CorridorId::from(response.1.id);

        let cross_sections = repository::get_corridor_cross_sections(&state.db.pool, corridor_id)
            .await
            .unwrap();
        assert_eq!(cross_sections.len(), 2);

        let lanes = repository::get_lanes_for_cross_section(&state.db.pool, cross_sections[0].id)
            .await
            .unwrap();
        assert_eq!(lanes.len(), 2, "lanes=2 tag should derive 2 travel lanes");
    }
}

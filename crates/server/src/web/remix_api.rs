//! JSON API for the Corridor Builder WASM shell: regions, remixes, and a
//! remix's corridors as GeoJSON. See
//! `docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md`.

use axum::Json;
use axum::extract::{Path, State};
use axum::http::StatusCode;

use mobilispect_core::ids::{RegionId, RemixId};
use mobilispect_core::remix::{geojson, repository};

use crate::web::AppState;

type ApiError = (StatusCode, Json<serde_json::Value>);

fn internal_error(context: &str, err: anyhow::Error) -> ApiError {
    tracing::error!(error = %err, "{context}");
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        Json(serde_json::json!({ "error": "Internal Server Error" })),
    )
}

#[derive(Debug, serde::Serialize)]
pub struct BoundingBoxResponse {
    pub min_lat: f64,
    pub min_lon: f64,
    pub max_lat: f64,
    pub max_lon: f64,
}

impl From<mobilispect_core::remix::BoundingBox> for BoundingBoxResponse {
    fn from(bbox: mobilispect_core::remix::BoundingBox) -> Self {
        Self {
            min_lat: bbox.min_lat,
            min_lon: bbox.min_lon,
            max_lat: bbox.max_lat,
            max_lon: bbox.max_lon,
        }
    }
}

#[derive(Debug, serde::Serialize)]
pub struct RegionResponse {
    pub id: i64,
    pub name: String,
    pub bbox: BoundingBoxResponse,
}

impl From<mobilispect_core::remix::Region> for RegionResponse {
    fn from(region: mobilispect_core::remix::Region) -> Self {
        Self {
            id: region.id.as_i64(),
            name: region.name,
            bbox: region.bounding_box.into(),
        }
    }
}

/// `GET /api/regions` — regions with a bounding box set, for the
/// metro-region picker.
pub async fn list_regions(
    State(state): State<AppState>,
) -> Result<Json<Vec<RegionResponse>>, ApiError> {
    let regions = repository::list_regions_with_bounding_box(&state.db.pool)
        .await
        .map_err(|e| internal_error("list_regions", e))?;
    Ok(Json(
        regions.into_iter().map(RegionResponse::from).collect(),
    ))
}

#[derive(Debug, serde::Serialize)]
pub struct RemixSummaryResponse {
    pub id: i64,
    pub name: String,
}

/// `GET /api/regions/:region_id/remixes` — a region's remixes,
/// most-recently-updated first.
pub async fn list_region_remixes(
    State(state): State<AppState>,
    Path(region_id): Path<i64>,
) -> Result<Json<Vec<RemixSummaryResponse>>, ApiError> {
    let remixes = repository::list_remixes_for_region(&state.db.pool, RegionId::from(region_id))
        .await
        .map_err(|e| internal_error("list_region_remixes", e))?;
    Ok(Json(
        remixes
            .into_iter()
            .map(|r| RemixSummaryResponse {
                id: r.id.as_i64(),
                name: r.name,
            })
            .collect(),
    ))
}

#[derive(Debug, serde::Deserialize)]
pub struct CreateRemixRequest {
    pub name: String,
    pub region_id: i64,
}

#[derive(Debug, serde::Serialize)]
pub struct CreateRemixResponse {
    pub id: i64,
}

/// `POST /api/remixes` — creates a remix. Rejects a blank name or a region
/// with no bounding box set (400 either way).
pub async fn create_remix(
    State(state): State<AppState>,
    Json(req): Json<CreateRemixRequest>,
) -> Result<(StatusCode, Json<CreateRemixResponse>), ApiError> {
    if req.name.trim().is_empty() {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "error": "name must not be blank" })),
        ));
    }

    let regions = repository::list_regions_with_bounding_box(&state.db.pool)
        .await
        .map_err(|e| internal_error("create_remix: list_regions_with_bounding_box", e))?;
    if !regions.iter().any(|r| r.id.as_i64() == req.region_id) {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "error": "region has no bounding box set" })),
        ));
    }

    let remix_id = repository::insert_remix(
        &state.db.pool,
        req.name.trim(),
        RegionId::from(req.region_id),
    )
    .await
    .map_err(|e| internal_error("create_remix: insert_remix", e))?;

    Ok((
        StatusCode::CREATED,
        Json(CreateRemixResponse {
            id: remix_id.as_i64(),
        }),
    ))
}

#[derive(Debug, serde::Serialize)]
pub struct RemixDetailResponse {
    pub id: i64,
    pub name: String,
    pub region: RegionResponse,
}

/// `GET /api/remixes/:remix_id` — a remix plus its region (with bounding box).
pub async fn get_remix(
    State(state): State<AppState>,
    Path(remix_id): Path<i64>,
) -> Result<Json<RemixDetailResponse>, ApiError> {
    let found = repository::get_remix(&state.db.pool, RemixId::from(remix_id))
        .await
        .map_err(|e| internal_error("get_remix", e))?;

    let Some((remix, region)) = found else {
        return Err((
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({ "error": "remix not found" })),
        ));
    };

    Ok(Json(RemixDetailResponse {
        id: remix.id.as_i64(),
        name: remix.name,
        region: RegionResponse::from(region),
    }))
}

/// `GET /api/remixes/:remix_id/corridors` — the remix's corridors as a
/// GeoJSON `FeatureCollection` for the region map's overlay.
pub async fn list_remix_corridors(
    State(state): State<AppState>,
    Path(remix_id): Path<i64>,
) -> Result<Json<geojson::FeatureCollection>, ApiError> {
    let corridors = repository::list_corridors_for_remix(&state.db.pool, RemixId::from(remix_id))
        .await
        .map_err(|e| internal_error("list_remix_corridors", e))?;
    Ok(Json(geojson::build_corridors_feature_collection(
        &corridors,
    )))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::web::SetupState;
    use axum::body::to_bytes;
    use axum::response::IntoResponse;
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
            osm_cache_dir: "./osm-cache".to_string(),
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

    async fn seed_region_with_bbox(state: &AppState, id: i64, name: &str) -> RegionId {
        sqlx::query(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon) \
             VALUES ($1, $2, 'UTC', 45.40, -73.70, 45.60, -73.50)",
        )
        .bind(id)
        .bind(name)
        .execute(&state.db.pool)
        .await
        .unwrap();
        RegionId::from(id)
    }

    #[tokio::test]
    async fn list_regions_returns_only_regions_with_a_bbox() {
        let (state, _td) = test_state().await;
        seed_region_with_bbox(&state, 1, "Has Bbox").await;
        sqlx::query("INSERT INTO regions (id, name, timezone) VALUES (2, 'No Bbox', 'UTC')")
            .execute(&state.db.pool)
            .await
            .unwrap();

        let response = list_regions(State(state)).await.unwrap();

        assert_eq!(response.0.len(), 1);
        assert_eq!(response.0[0].name, "Has Bbox");
    }

    #[tokio::test]
    async fn create_remix_with_blank_name_returns_400() {
        let (state, _td) = test_state().await;
        let region_id = seed_region_with_bbox(&state, 1, "Test Region").await;

        let response = create_remix(
            State(state),
            Json(CreateRemixRequest {
                name: "   ".to_string(),
                region_id: region_id.as_i64(),
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn create_remix_with_region_missing_bbox_returns_400() {
        let (state, _td) = test_state().await;
        sqlx::query("INSERT INTO regions (id, name, timezone) VALUES (1, 'No Bbox', 'UTC')")
            .execute(&state.db.pool)
            .await
            .unwrap();

        let response = create_remix(
            State(state),
            Json(CreateRemixRequest {
                name: "Test Remix".to_string(),
                region_id: 1,
            }),
        )
        .await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn create_remix_happy_path_returns_201_with_id() {
        let (state, _td) = test_state().await;
        let region_id = seed_region_with_bbox(&state, 1, "Test Region").await;

        let response = create_remix(
            State(state),
            Json(CreateRemixRequest {
                name: "Downtown bike lanes".to_string(),
                region_id: region_id.as_i64(),
            }),
        )
        .await
        .unwrap();

        assert_eq!(response.0, StatusCode::CREATED);
        assert!(response.1.id > 0);
    }

    #[tokio::test]
    async fn get_remix_for_unknown_id_returns_404() {
        let (state, _td) = test_state().await;

        let response = get_remix(State(state), Path(999_999)).await;

        assert!(response.is_err());
        assert_eq!(response.unwrap_err().0, StatusCode::NOT_FOUND);
    }

    #[tokio::test]
    async fn list_remix_corridors_for_remix_with_no_corridors_returns_empty_feature_collection() {
        let (state, _td) = test_state().await;
        let region_id = seed_region_with_bbox(&state, 1, "Test Region").await;
        let remix_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id) VALUES ('Empty Remix', $1) RETURNING id",
        )
        .bind(region_id.as_i64())
        .fetch_one(&state.db.pool)
        .await
        .unwrap();

        let response = list_remix_corridors(State(state), Path(remix_id))
            .await
            .unwrap();

        assert_eq!(response.0.features.len(), 0);
    }

    // Kept as a body-shape smoke test rather than deep JSON assertions —
    // `remix::geojson::build_corridors_feature_collection`'s own unit tests
    // already cover the exact shape.
    #[tokio::test]
    async fn list_remix_corridors_response_is_valid_json() {
        let (state, _td) = test_state().await;
        let region_id = seed_region_with_bbox(&state, 1, "Test Region").await;
        let remix_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id) VALUES ('Test Remix', $1) RETURNING id",
        )
        .bind(region_id.as_i64())
        .fetch_one(&state.db.pool)
        .await
        .unwrap();

        let response = list_remix_corridors(State(state), Path(remix_id))
            .await
            .unwrap()
            .into_response();
        let bytes = to_bytes(response.into_body(), usize::MAX).await.unwrap();
        let parsed: serde_json::Value = serde_json::from_slice(&bytes).unwrap();

        assert_eq!(parsed["type"], "FeatureCollection");
    }
}

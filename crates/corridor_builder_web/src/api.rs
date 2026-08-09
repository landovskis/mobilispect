//! JSON API client for the server's `/api/regions`, `/api/remixes` endpoints
//! (see `crates/server/src/web/remix_api.rs`), the `/api/remixes/:id/corridors`,
//! `/api/corridors` manual-tracing endpoints (see
//! `crates/server/src/web/corridor_api.rs`), and the `/api/corridors/:id/cross-sections`,
//! `/api/cross-sections/:id/lanes`, `/api/lanes/:id` lane-editor endpoints (see
//! `crates/server/src/web/lane_editor_api.rs`).

use serde::{Deserialize, Serialize};

const API_BASE: &str = "/api";

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct BoundingBox {
    pub min_lat: f64,
    pub min_lon: f64,
    pub max_lat: f64,
    pub max_lon: f64,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct Region {
    pub id: i64,
    pub name: String,
    pub bbox: BoundingBox,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct RemixSummary {
    pub id: i64,
    pub name: String,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct RemixDetail {
    pub id: i64,
    pub name: String,
    pub region: Region,
}

#[derive(Debug, Clone, Serialize)]
struct CreateRemixRequest {
    name: String,
    region_id: i64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CreateRemixResponse {
    pub id: i64,
}

/// Sends a `gloo_net` request and decodes a JSON response, surfacing the
/// server's `{"error": "..."}` message as `Err` on any non-2xx status (e.g.
/// a blank name on create, or 404 on an unknown remix) instead of trying —
/// and failing confusingly — to decode an error body as the success type.
///
/// Generic over anything convertible into a `gloo_net::http::Request` so it
/// accepts both a bare `RequestBuilder` (GET calls, built implicitly on
/// `.send()`) and an already-built `Request` (POST calls that went through
/// `RequestBuilder::json`, which itself returns a `Request`).
async fn send_and_decode<T, R>(request: R) -> Result<T, String>
where
    T: for<'de> Deserialize<'de>,
    R: TryInto<gloo_net::http::Request>,
    R::Error: std::fmt::Display,
{
    let request: gloo_net::http::Request = request.try_into().map_err(|e| e.to_string())?;
    let response = request.send().await.map_err(|e| e.to_string())?;

    if !response.ok() {
        let body: serde_json::Value = response.json().await.unwrap_or_default();
        let message = body["error"]
            .as_str()
            .unwrap_or("request failed")
            .to_string();
        return Err(message);
    }

    response.json().await.map_err(|e| e.to_string())
}

pub async fn list_regions() -> Result<Vec<Region>, String> {
    send_and_decode(gloo_net::http::Request::get(&format!("{API_BASE}/regions"))).await
}

pub async fn list_region_remixes(region_id: i64) -> Result<Vec<RemixSummary>, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/regions/{region_id}/remixes"
    )))
    .await
}

pub async fn create_remix(name: String, region_id: i64) -> Result<CreateRemixResponse, String> {
    let request = gloo_net::http::Request::post(&format!("{API_BASE}/remixes"))
        .json(&CreateRemixRequest { name, region_id })
        .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

/// Returns `Err("remix not found")` (the server's own message) when
/// `remix_id` doesn't exist, rather than a confusing JSON-decode error —
/// see the design spec's Error Handling table.
pub async fn get_remix(remix_id: i64) -> Result<RemixDetail, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/remixes/{remix_id}"
    )))
    .await
}

/// Returns the raw GeoJSON `FeatureCollection` as a `serde_json::Value` —
/// it's only ever handed straight to MapLibre, never inspected field by
/// field on the Rust side, so a typed struct would add nothing.
pub async fn get_remix_corridors(remix_id: i64) -> Result<serde_json::Value, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/remixes/{remix_id}/corridors"
    )))
    .await
}

#[derive(Debug, Clone, Serialize)]
struct StartManualCorridorRequest {
    name: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct StartManualCorridorResponse {
    pub id: i64,
}

pub async fn start_manual_corridor(
    remix_id: i64,
    name: String,
) -> Result<StartManualCorridorResponse, String> {
    let request =
        gloo_net::http::Request::post(&format!("{API_BASE}/remixes/{remix_id}/corridors/manual"))
            .json(&StartManualCorridorRequest { name })
            .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, Serialize)]
struct AddManualPointRequest {
    lat: f64,
    lon: f64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CrossSectionResponse {
    #[allow(dead_code)]
    pub id: i64,
    pub position: f64,
    #[allow(dead_code)]
    pub lat: f64,
    #[allow(dead_code)]
    pub lon: f64,
}

pub async fn add_manual_point(
    corridor_id: i64,
    lat: f64,
    lon: f64,
) -> Result<CrossSectionResponse, String> {
    let request =
        gloo_net::http::Request::post(&format!("{API_BASE}/corridors/{corridor_id}/points"))
            .json(&AddManualPointRequest { lat, lon })
            .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[allow(dead_code)]
#[derive(Debug, Clone, Deserialize)]
pub struct FinishManualCorridorResponse {
    pub id: i64,
    pub cross_section_count: i64,
}

pub async fn finish_manual_corridor(
    corridor_id: i64,
) -> Result<FinishManualCorridorResponse, String> {
    let request =
        gloo_net::http::Request::post(&format!("{API_BASE}/corridors/{corridor_id}/finish"));
    send_and_decode(request).await
}

#[derive(Debug, Clone, Serialize)]
struct SearchStreetsRequest {
    min_lat: f64,
    min_lon: f64,
    max_lat: f64,
    max_lon: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct OsmPointResponse {
    pub lat: f64,
    pub lon: f64,
    pub osm_node_id: Option<i64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct OsmWayResponse {
    pub osm_way_id: i64,
    pub points: Vec<OsmPointResponse>,
    pub tags: std::collections::HashMap<String, String>,
}

pub async fn search_streets(
    remix_id: i64,
    min_lat: f64,
    min_lon: f64,
    max_lat: f64,
    max_lon: f64,
) -> Result<Vec<OsmWayResponse>, String> {
    let request = gloo_net::http::Request::post(&format!("{API_BASE}/remixes/{remix_id}/streets"))
        .json(&SearchStreetsRequest {
            min_lat,
            min_lon,
            max_lat,
            max_lon,
        })
        .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, Serialize)]
struct ImportCorridorRequest {
    name: String,
    ways: Vec<OsmWayResponse>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ImportCorridorResponse {
    pub id: i64,
}

pub async fn import_corridor(
    remix_id: i64,
    name: String,
    ways: Vec<OsmWayResponse>,
) -> Result<ImportCorridorResponse, String> {
    let request =
        gloo_net::http::Request::post(&format!("{API_BASE}/remixes/{remix_id}/corridors/import"))
            .json(&ImportCorridorRequest { name, ways })
            .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct CrossSectionSummary {
    pub id: i64,
    #[allow(dead_code)]
    pub position: f64,
    pub label: Option<String>,
    pub lat: f64,
    pub lon: f64,
    pub version: i32,
}

pub async fn list_cross_sections(corridor_id: i64) -> Result<Vec<CrossSectionSummary>, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/corridors/{corridor_id}/cross-sections"
    )))
    .await
}

#[derive(Debug, Clone, Serialize)]
struct UpdateLabelRequest {
    label: Option<String>,
    expected_version: i32,
}

pub async fn update_cross_section_label(
    corridor_id: i64,
    cross_section_id: i64,
    label: Option<String>,
    expected_version: i32,
) -> Result<CrossSectionSummary, String> {
    let request = gloo_net::http::Request::patch(&format!(
        "{API_BASE}/corridors/{corridor_id}/cross-sections/{cross_section_id}/label"
    ))
    .json(&UpdateLabelRequest {
        label,
        expected_version,
    })
    .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TimeWindowValue {
    pub days: String,
    pub start_time: String,
    pub end_time: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct AccessRuleValue {
    pub time_window: Option<TimeWindowValue>,
    pub allowed_modes: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct LaneResponse {
    pub id: i64,
    pub position: f64,
    pub lane_type: String,
    pub width_meters: f64,
    pub direction: String,
    pub access_rules: Vec<AccessRuleValue>,
}

pub async fn list_lanes(cross_section_id: i64) -> Result<Vec<LaneResponse>, String> {
    send_and_decode(gloo_net::http::Request::get(&format!(
        "{API_BASE}/cross-sections/{cross_section_id}/lanes"
    )))
    .await
}

#[derive(Debug, Clone, Serialize)]
struct UpdateLaneRequest {
    lane_type: String,
    width_meters: f64,
    direction: String,
}

pub async fn update_lane(
    lane_id: i64,
    lane_type: String,
    width_meters: f64,
    direction: String,
) -> Result<LaneResponse, String> {
    let request = gloo_net::http::Request::patch(&format!("{API_BASE}/lanes/{lane_id}"))
        .json(&UpdateLaneRequest {
            lane_type,
            width_meters,
            direction,
        })
        .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

#[derive(Debug, Clone, Serialize)]
struct InsertLaneRequest {
    lane_type: String,
    width_meters: f64,
    direction: String,
    neighbor_before_position: Option<f64>,
    neighbor_after_position: Option<f64>,
}

pub async fn insert_lane(
    cross_section_id: i64,
    lane_type: String,
    width_meters: f64,
    direction: String,
    neighbor_before_position: Option<f64>,
    neighbor_after_position: Option<f64>,
) -> Result<LaneResponse, String> {
    let request = gloo_net::http::Request::post(&format!(
        "{API_BASE}/cross-sections/{cross_section_id}/lanes"
    ))
    .json(&InsertLaneRequest {
        lane_type,
        width_meters,
        direction,
        neighbor_before_position,
        neighbor_after_position,
    })
    .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

/// No response body (`204 No Content`) -- doesn't go through `send_and_decode`,
/// which always tries to JSON-decode a success response.
pub async fn delete_lane(lane_id: i64) -> Result<(), String> {
    let response = gloo_net::http::Request::delete(&format!("{API_BASE}/lanes/{lane_id}"))
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if response.ok() {
        Ok(())
    } else {
        let body: serde_json::Value = response.json().await.unwrap_or_default();
        Err(body["error"]
            .as_str()
            .unwrap_or("request failed")
            .to_string())
    }
}

#[derive(Debug, Clone, Serialize)]
struct SetAccessRulesRequest {
    rules: Vec<AccessRuleValue>,
}

pub async fn set_access_rules(
    lane_id: i64,
    rules: Vec<AccessRuleValue>,
) -> Result<Vec<AccessRuleValue>, String> {
    let request = gloo_net::http::Request::put(&format!("{API_BASE}/lanes/{lane_id}/access-rules"))
        .json(&SetAccessRulesRequest { rules })
        .map_err(|e| e.to_string())?;
    send_and_decode(request).await
}

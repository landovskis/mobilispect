//! Overpass API client: fetches OSM way geometry+tags for a bounding box. See
//! `docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md`.

use crate::corridor_design::Coordinate;
use crate::corridor_design::geometry::RawPoint;
use crate::remix::BoundingBox;

const DEFAULT_BASE_URL: &str = "https://overpass-api.de/api/interpreter";

/// The fixed set of `highway=*` values this app treats as an importable
/// "street" — anything a vehicle can drive on, plus dedicated cycle
/// infrastructure. Not user-configurable in this first version.
const HIGHWAY_FILTER: &[&str] = &[
    "motorway",
    "trunk",
    "primary",
    "secondary",
    "tertiary",
    "unclassified",
    "residential",
    "service",
    "cycleway",
    "path",
];

pub struct OverpassClient {
    http: reqwest::Client,
    base_url: String,
}

impl Default for OverpassClient {
    fn default() -> Self {
        Self::new()
    }
}

impl OverpassClient {
    /// Reads `OVERPASS_BASE_URL` from the environment, if set, so E2E test
    /// runs can point this at a local fixture server without threading a new
    /// field through `Config`/`config.toml` — this is a test/E2E-environment
    /// concern only, not a real operator-facing setting (mirrors this
    /// codebase's existing `MOBILISPECT_DATABASE_URL` convention for
    /// `dev.sh`). Falls back to the real Overpass endpoint when unset.
    pub fn new() -> Self {
        let base_url =
            std::env::var("OVERPASS_BASE_URL").unwrap_or_else(|_| DEFAULT_BASE_URL.to_string());
        Self {
            http: reqwest::Client::new(),
            base_url,
        }
    }

    #[cfg(test)]
    pub(crate) fn with_base_url(base_url: String) -> Self {
        Self {
            http: reqwest::Client::new(),
            base_url,
        }
    }

    pub async fn fetch_ways_in_bbox(
        &self,
        bbox: BoundingBox,
    ) -> Result<Vec<OsmWay>, OverpassError> {
        let query = build_query(bbox);
        let response = self
            .http
            .post(&self.base_url)
            .header(
                "User-Agent",
                "mobilispect/1.0 (+https://github.com/landovskis/mobilispect)",
            )
            .form(&[("data", query.as_str())])
            .send()
            .await
            .map_err(|e| OverpassError::Http(e.to_string()))?;

        if !response.status().is_success() {
            return Err(OverpassError::Http(format!(
                "Overpass returned status {}",
                response.status()
            )));
        }

        let body = response
            .text()
            .await
            .map_err(|e| OverpassError::Http(e.to_string()))?;

        parse_overpass_response(&body)
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct OsmWay {
    pub osm_way_id: i64,
    // Reuses `corridor_design::geometry::RawPoint` directly (coordinate +
    // optional OSM node id per point) -- this is exactly the shape
    // `RawWaySegment.points` needs, and Overpass's response gives both a
    // `nodes` array (node ids) and a `geometry` array (lat/lon) as parallel,
    // same-order, same-length arrays per way -- confirmed against a live
    // query (see this plan's Global Constraints).
    pub points: Vec<RawPoint>,
    pub tags: std::collections::HashMap<String, String>,
}

#[derive(Debug)]
pub enum OverpassError {
    /// The request failed, timed out, or the server returned a non-2xx status.
    Http(String),
    /// The response body didn't match the expected shape.
    Parse(String),
}

impl std::fmt::Display for OverpassError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            OverpassError::Http(msg) => write!(f, "Overpass request failed: {msg}"),
            OverpassError::Parse(msg) => write!(f, "Overpass response parse error: {msg}"),
        }
    }
}

impl std::error::Error for OverpassError {}

/// Builds the Overpass QL query for `bbox`, filtered to this app's fixed
/// `highway=*` value set. Pure — no I/O, independently testable.
fn build_query(bbox: BoundingBox) -> String {
    let highway_pattern = HIGHWAY_FILTER.join("|");
    format!(
        "[out:json][timeout:25];way[\"highway\"~\"^({highway_pattern})$\"]({},{},{},{});out geom;",
        bbox.min_lat, bbox.min_lon, bbox.max_lat, bbox.max_lon,
    )
}

#[derive(serde::Deserialize)]
struct OverpassResponse {
    elements: Vec<OverpassElement>,
}

#[derive(serde::Deserialize)]
struct OverpassElement {
    #[serde(rename = "type")]
    element_type: String,
    id: i64,
    #[serde(default)]
    nodes: Vec<i64>,
    #[serde(default)]
    geometry: Vec<OverpassGeometryPoint>,
    #[serde(default)]
    tags: std::collections::HashMap<String, String>,
}

#[derive(serde::Deserialize)]
struct OverpassGeometryPoint {
    lat: f64,
    lon: f64,
}

/// Parses a raw Overpass JSON response body into `OsmWay`s. Split out from
/// `fetch_ways_in_bbox` so query-shape and response-parsing are independently
/// unit testable without a network call. Silently skips any non-`"way"`
/// element (this app's `way[...]` query never returns another type, but this
/// guards against a future query shape change) and rejects any way whose
/// `nodes`/`geometry` arrays don't match in length (a malformed or truncated
/// response) rather than silently mis-pairing them.
fn parse_overpass_response(body: &str) -> Result<Vec<OsmWay>, OverpassError> {
    let parsed: OverpassResponse =
        serde_json::from_str(body).map_err(|e| OverpassError::Parse(e.to_string()))?;

    let mut ways = Vec::new();
    for element in parsed.elements {
        if element.element_type != "way" {
            continue;
        }
        if element.nodes.len() != element.geometry.len() {
            return Err(OverpassError::Parse(format!(
                "way {} has {} nodes but {} geometry points",
                element.id,
                element.nodes.len(),
                element.geometry.len()
            )));
        }
        let points = element
            .nodes
            .iter()
            .zip(element.geometry.iter())
            .map(|(node_id, point)| RawPoint {
                coordinate: Coordinate::new(point.lat, point.lon),
                osm_node_id: Some(*node_id),
            })
            .collect();
        ways.push(OsmWay {
            osm_way_id: element.id,
            points,
            tags: element.tags,
        });
    }
    Ok(ways)
}

#[cfg(test)]
mod tests {
    use super::*;
    use wiremock::matchers::method;
    use wiremock::{Mock, MockServer, ResponseTemplate};

    fn sample_bbox() -> BoundingBox {
        BoundingBox {
            min_lat: 45.40,
            min_lon: -73.70,
            max_lat: 45.60,
            max_lon: -73.50,
        }
    }

    // --- build_query ---

    #[test]
    fn build_query_includes_bbox_coordinates_in_order() {
        let query = build_query(sample_bbox());
        assert!(query.contains("(45.4,-73.7,45.6,-73.5)"));
    }

    #[test]
    fn build_query_ends_with_out_geom() {
        let query = build_query(sample_bbox());
        assert!(query.ends_with("out geom;"));
    }

    #[test]
    fn build_query_includes_highway_filter_values() {
        let query = build_query(sample_bbox());
        assert!(query.contains("residential"));
        assert!(query.contains("cycleway"));
        assert!(query.contains("path"));
    }

    // --- parse_overpass_response ---

    #[test]
    fn parse_overpass_response_extracts_way_with_tags_and_points() {
        let body = r#"{
            "version": 0.6,
            "elements": [
                {
                    "type": "way",
                    "id": 4517656,
                    "nodes": [111, 222],
                    "geometry": [
                        {"lat": 45.500, "lon": -73.580},
                        {"lat": 45.501, "lon": -73.579}
                    ],
                    "tags": {"highway": "residential", "name": "Main St"}
                }
            ]
        }"#;

        let ways = parse_overpass_response(body).unwrap();
        assert_eq!(ways.len(), 1);
        assert_eq!(ways[0].osm_way_id, 4517656);
        assert_eq!(ways[0].points.len(), 2);
        assert_eq!(ways[0].points[0].osm_node_id, Some(111));
        assert_eq!(
            ways[0].points[0].coordinate,
            Coordinate::new(45.500, -73.580)
        );
        assert_eq!(ways[0].points[1].osm_node_id, Some(222));
        assert_eq!(
            ways[0].tags.get("highway"),
            Some(&"residential".to_string())
        );
        assert_eq!(ways[0].tags.get("name"), Some(&"Main St".to_string()));
    }

    #[test]
    fn parse_overpass_response_returns_empty_vec_for_no_elements() {
        let body = r#"{"version": 0.6, "elements": []}"#;
        let ways = parse_overpass_response(body).unwrap();
        assert!(ways.is_empty());
    }

    #[test]
    fn parse_overpass_response_skips_non_way_elements() {
        let body = r#"{
            "version": 0.6,
            "elements": [
                {"type": "node", "id": 111, "lat": 45.5, "lon": -73.5},
                {
                    "type": "way",
                    "id": 200,
                    "nodes": [1, 2],
                    "geometry": [
                        {"lat": 45.500, "lon": -73.580},
                        {"lat": 45.501, "lon": -73.579}
                    ],
                    "tags": {}
                }
            ]
        }"#;

        let ways = parse_overpass_response(body).unwrap();
        assert_eq!(ways.len(), 1);
        assert_eq!(ways[0].osm_way_id, 200);
    }

    #[test]
    fn parse_overpass_response_rejects_malformed_json() {
        let result = parse_overpass_response("not json");
        assert!(matches!(result, Err(OverpassError::Parse(_))));
    }

    #[test]
    fn parse_overpass_response_rejects_mismatched_nodes_and_geometry_length() {
        let body = r#"{
            "version": 0.6,
            "elements": [
                {
                    "type": "way",
                    "id": 300,
                    "nodes": [1, 2, 3],
                    "geometry": [
                        {"lat": 45.500, "lon": -73.580},
                        {"lat": 45.501, "lon": -73.579}
                    ],
                    "tags": {}
                }
            ]
        }"#;

        let result = parse_overpass_response(body);
        assert!(matches!(result, Err(OverpassError::Parse(_))));
    }

    // --- fetch_ways_in_bbox (network, via wiremock) ---

    #[tokio::test]
    async fn fetch_ways_in_bbox_returns_parsed_ways_on_success() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "version": 0.6,
                "elements": [{
                    "type": "way",
                    "id": 500,
                    "nodes": [1, 2],
                    "geometry": [
                        {"lat": 45.500, "lon": -73.580},
                        {"lat": 45.501, "lon": -73.579}
                    ],
                    "tags": {"highway": "residential"}
                }]
            })))
            .mount(&server)
            .await;

        let client = OverpassClient::with_base_url(server.uri());
        let ways = client.fetch_ways_in_bbox(sample_bbox()).await.unwrap();

        assert_eq!(ways.len(), 1);
        assert_eq!(ways[0].osm_way_id, 500);
    }

    #[tokio::test]
    async fn fetch_ways_in_bbox_returns_http_error_on_non_success_status() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(ResponseTemplate::new(504))
            .mount(&server)
            .await;

        let client = OverpassClient::with_base_url(server.uri());
        let result = client.fetch_ways_in_bbox(sample_bbox()).await;

        assert!(matches!(result, Err(OverpassError::Http(_))));
    }

    #[tokio::test]
    async fn fetch_ways_in_bbox_returns_parse_error_on_malformed_body() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(ResponseTemplate::new(200).set_body_string("not json"))
            .mount(&server)
            .await;

        let client = OverpassClient::with_base_url(server.uri());
        let result = client.fetch_ways_in_bbox(sample_bbox()).await;

        assert!(matches!(result, Err(OverpassError::Parse(_))));
    }
}

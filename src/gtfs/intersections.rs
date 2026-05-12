use anyhow::Result;
use crate::db::Database;
use serde::Deserialize;
use std::collections::HashMap;
use std::path::Path;

#[derive(Debug)]
pub struct RawIntersection {
    pub lat: f64,
    pub lon: f64,
    pub control_type: String,
    pub name: Option<String>,
}

#[derive(Deserialize)]
struct OverpassResponse {
    elements: Vec<OverpassElement>,
}

#[derive(Deserialize)]
struct OverpassElement {
    #[serde(rename = "type")]
    element_type: String,
    id: u64,
    lat: Option<f64>,
    lon: Option<f64>,
    nodes: Option<Vec<u64>>,
    tags: Option<HashMap<String, String>>,
}

/// Project a point (node_lat, node_lon) onto the line segment from→to.
/// Returns t ∈ [0.0, 1.0]: 0.0 = at from, 1.0 = at to.
/// Uses a flat 2-D approximation (lon/lat as x/y). Accurate for short segments (<5 km).
pub fn project_to_segment(
    node_lat: f64,
    node_lon: f64,
    from_lat: f64,
    from_lon: f64,
    to_lat: f64,
    to_lon: f64,
) -> f64 {
    let dx = to_lon - from_lon;
    let dy = to_lat - from_lat;
    let len_sq = dx * dx + dy * dy;
    if len_sq == 0.0 {
        return 0.0;
    }
    let t = ((node_lon - from_lon) * dx + (node_lat - from_lat) * dy) / len_sq;
    t.clamp(0.0, 1.0)
}

/// Distance in metres from a point to the closest point on a segment.
/// Uses a flat-earth approximation; accurate to within ~1% for segments under 5 km.
pub fn dist_to_segment_m(
    node_lat: f64,
    node_lon: f64,
    from_lat: f64,
    from_lon: f64,
    to_lat: f64,
    to_lon: f64,
) -> f64 {
    let t = project_to_segment(node_lat, node_lon, from_lat, from_lon, to_lat, to_lon);
    let closest_lat = from_lat + t * (to_lat - from_lat);
    let closest_lon = from_lon + t * (to_lon - from_lon);
    let cos_lat = node_lat.to_radians().cos();
    let dlat_m = (node_lat - closest_lat) * 111_000.0;
    let dlon_m = (node_lon - closest_lon) * 111_000.0 * cos_lat;
    (dlat_m * dlat_m + dlon_m * dlon_m).sqrt()
}

/// A single OSM node carrying a traffic control tag.
#[derive(Debug)]
pub struct OsmNode {
    pub id: i64,
    pub lat: f64,
    pub lon: f64,
    pub control_type: String,
}

/// A named OSM way, carrying the IDs of the nodes it passes through.
#[derive(Debug)]
pub struct OsmWay {
    pub node_ids: Vec<i64>,
    pub name: String,
}

/// Convert pre-filtered OSM nodes and ways into [`RawIntersection`]s.
/// For each node, crossing way names are joined via [`build_intersection_name`].
pub fn intersections_from_elements(nodes: &[OsmNode], ways: &[OsmWay]) -> Vec<RawIntersection> {
    nodes
        .iter()
        .map(|node| {
            let crossing_names: Vec<String> = ways
                .iter()
                .filter(|w| w.node_ids.contains(&node.id))
                .map(|w| w.name.clone())
                .take(2)
                .collect();
            RawIntersection {
                lat: node.lat,
                lon: node.lon,
                control_type: node.control_type.clone(),
                name: build_intersection_name(&crossing_names),
            }
        })
        .collect()
}

const CONTROL_TYPES: &[&str] = &[
    "traffic_signals",
    "stop",
    "give_way",
    "mini_roundabout",
    "roundabout",
];

/// Read intersection control nodes from an OSM PBF file, filtered to the given
/// bounding box, and return them as [`RawIntersection`]s.
///
/// Only nodes tagged `highway` with one of the five control types are kept.
/// Crossing street names are derived from named ways that pass through each node.
pub fn intersections_from_pbf(
    path: &Path,
    min_lat: f64,
    max_lat: f64,
    min_lon: f64,
    max_lon: f64,
) -> Result<Vec<RawIntersection>> {
    use osmpbf::{Element, ElementReader};

    let reader = ElementReader::from_path(path)?;

    // id → (lat, lon, control_type)
    let mut control_nodes: HashMap<i64, OsmNode> = HashMap::new();
    // node_id → collected way names
    let mut node_way_names: HashMap<i64, Vec<String>> = HashMap::new();

    reader.for_each(|element| match element {
        Element::Node(n) => {
            let lat = n.lat();
            let lon = n.lon();
            if lat < min_lat || lat > max_lat || lon < min_lon || lon > max_lon {
                return;
            }
            for (k, v) in n.tags() {
                if k == "highway" && CONTROL_TYPES.contains(&v) {
                    control_nodes.insert(
                        n.id(),
                        OsmNode { id: n.id(), lat, lon, control_type: v.to_string() },
                    );
                    break;
                }
            }
        }
        Element::DenseNode(n) => {
            let lat = n.lat();
            let lon = n.lon();
            if lat < min_lat || lat > max_lat || lon < min_lon || lon > max_lon {
                return;
            }
            for (k, v) in n.tags() {
                if k == "highway" && CONTROL_TYPES.contains(&v) {
                    control_nodes.insert(
                        n.id(),
                        OsmNode { id: n.id(), lat, lon, control_type: v.to_string() },
                    );
                    break;
                }
            }
        }
        Element::Way(w) => {
            let name = w.tags().find(|(k, _)| *k == "name").map(|(_, v)| v.to_string());
            if let Some(name) = name {
                for ref_id in w.refs() {
                    if control_nodes.contains_key(&ref_id) {
                        let names = node_way_names.entry(ref_id).or_default();
                        if names.len() < 2 {
                            names.push(name.clone());
                        }
                    }
                }
            }
        }
        Element::Relation(_) => {}
    })?;

    Ok(control_nodes
        .into_values()
        .map(|node| {
            let names = node_way_names.remove(&node.id).unwrap_or_default();
            RawIntersection {
                lat: node.lat,
                lon: node.lon,
                control_type: node.control_type,
                name: build_intersection_name(&names),
            }
        })
        .collect())
}

/// Format up to two crossing street names as an intersection label.
/// Returns None if way_names is empty.
pub fn build_intersection_name(way_names: &[String]) -> Option<String> {
    match way_names {
        [] => None,
        [one] => Some(one.clone()),
        [a, b, ..] => Some(format!("{a} & {b}")),
    }
}

pub fn parse_overpass_response(json: &str) -> Result<Vec<RawIntersection>> {
    let response: OverpassResponse = serde_json::from_str(json)?;

    let nodes: Vec<&OverpassElement> = response
        .elements
        .iter()
        .filter(|e| e.element_type == "node")
        .collect();
    let ways: Vec<&OverpassElement> = response
        .elements
        .iter()
        .filter(|e| e.element_type == "way")
        .collect();

    let mut result = Vec::new();
    for node in nodes {
        let control_type = node
            .tags
            .as_ref()
            .and_then(|t| t.get("highway"))
            .cloned()
            .unwrap_or_default();

        let crossing_names: Vec<String> = ways
            .iter()
            .filter(|w| {
                w.nodes
                    .as_ref()
                    .map(|ns| ns.contains(&node.id))
                    .unwrap_or(false)
            })
            .filter_map(|w| w.tags.as_ref()?.get("name").cloned())
            .take(2)
            .collect();

        result.push(RawIntersection {
            lat: node.lat.unwrap_or(0.0),
            lon: node.lon.unwrap_or(0.0),
            control_type,
            name: build_intersection_name(&crossing_names),
        });
    }
    Ok(result)
}

/// Project each [`RawIntersection`] to the nearest stop pair and upsert into
/// `stop_intersections`. Shared by the Overpass and PBF code paths.
pub async fn store_intersections_from_raw(
    db: &Database,
    agency_id: &str,
    stop_pairs: &[(String, String, f64, f64, f64, f64)],
    nodes: &[RawIntersection],
) -> Result<()> {
    for node in nodes {
        // Find the nearest stop pair using perpendicular distance to segment.
        let best = stop_pairs.iter().min_by(|a, b| {
            let ta = project_to_segment(node.lat, node.lon, a.2, a.3, a.4, a.5);
            let tb = project_to_segment(node.lat, node.lon, b.2, b.3, b.4, b.5);
            let dist_sq = |pair: &(String, String, f64, f64, f64, f64), t: f64| {
                let px = pair.3 + t * (pair.5 - pair.3);
                let py = pair.2 + t * (pair.4 - pair.2);
                (node.lon - px).powi(2) + (node.lat - py).powi(2)
            };
            dist_sq(a, ta)
                .partial_cmp(&dist_sq(b, tb))
                .unwrap_or(std::cmp::Ordering::Equal)
        });

        let Some(pair) = best else { continue };
        // Discard nodes more than 50 m from the nearest segment — they are on
        // parallel or cross streets the bus does not travel.
        const MAX_DIST_M: f64 = 50.0;
        let dist = dist_to_segment_m(node.lat, node.lon, pair.2, pair.3, pair.4, pair.5);
        if dist > MAX_DIST_M {
            continue;
        }
        let position_frac = project_to_segment(node.lat, node.lon, pair.2, pair.3, pair.4, pair.5);

        sqlx::query(
            "INSERT INTO stop_intersections
             (agency_id, from_stop_id, to_stop_id, control_type, name, lat, lon, position_frac)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
             ON CONFLICT (agency_id, from_stop_id, to_stop_id, lat, lon) DO NOTHING",
        )
        .bind(agency_id)
        .bind(&pair.0)
        .bind(&pair.1)
        .bind(&node.control_type)
        .bind(&node.name)
        .bind(node.lat)
        .bind(node.lon)
        .bind(position_frac)
        .execute(&db.pool)
        .await?;
    }
    Ok(())
}

/// Parse an Overpass JSON response, project each intersection node to the nearest
/// stop pair, and upsert into `stop_intersections`. Uses ON CONFLICT DO NOTHING for idempotency.
pub async fn store_intersections_from_json(
    db: &Database,
    agency_id: &str,
    stop_pairs: &[(String, String, f64, f64, f64, f64)],
    json: &str,
) -> Result<()> {
    let nodes = parse_overpass_response(json)?;
    store_intersections_from_raw(db, agency_id, stop_pairs, &nodes).await
}

/// Fetch intersection data for all stop pairs in one route+direction that are
/// not yet present in `stop_intersections`, then upsert the results.
///
/// When `osm_pbf_path` is provided the local PBF extract is queried (no rate
/// limit). When it is `None` the Overpass API is used as a fallback.
pub async fn fetch_and_store_intersections(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
    client: &reqwest::Client,
    osm_pbf_path: Option<&Path>,
) -> Result<()> {
    #[derive(sqlx::FromRow)]
    struct PairRow {
        from_stop_id: String,
        to_stop_id: String,
        from_lat: f64,
        from_lon: f64,
        to_lat: f64,
        to_lon: f64,
    }

    let all_pairs: Vec<PairRow> = sqlx::query_as(
        "WITH rep_trip AS (
            SELECT trip_id
            FROM trips
            WHERE agency_id = $1 AND route_id = $2 AND COALESCE(direction_id, 0) = $3
            ORDER BY trip_id LIMIT 1
        ),
        ordered AS (
            SELECT s.stop_id, s.stop_lat, s.stop_lon, ss.stop_sequence
            FROM rep_trip rt
            JOIN scheduled_stops ss ON ss.agency_id = $1 AND ss.trip_id = rt.trip_id
            JOIN stops s ON s.agency_id = $1 AND s.stop_id = ss.stop_id
        ),
        with_next AS (
            SELECT stop_id AS from_stop_id, stop_lat AS from_lat, stop_lon AS from_lon,
                   LEAD(stop_id)  OVER (ORDER BY stop_sequence) AS to_stop_id,
                   LEAD(stop_lat) OVER (ORDER BY stop_sequence) AS to_lat,
                   LEAD(stop_lon) OVER (ORDER BY stop_sequence) AS to_lon
            FROM ordered
        )
        SELECT from_stop_id, to_stop_id, from_lat, from_lon, to_lat, to_lon
        FROM with_next
        WHERE to_stop_id IS NOT NULL",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(direction_id)
    .fetch_all(&db.pool)
    .await?;

    if all_pairs.is_empty() {
        return Ok(());
    }

    // Skip pairs already fetched.
    let existing_pairs: Vec<(String, String)> = sqlx::query_as(
        "SELECT DISTINCT from_stop_id, to_stop_id
         FROM stop_intersections
         WHERE agency_id = $1
           AND (from_stop_id, to_stop_id) IN (
             SELECT * FROM unnest($2::text[], $3::text[])
           )",
    )
    .bind(agency_id)
    .bind(&all_pairs.iter().map(|p| p.from_stop_id.clone()).collect::<Vec<_>>())
    .bind(&all_pairs.iter().map(|p| p.to_stop_id.clone()).collect::<Vec<_>>())
    .fetch_all(&db.pool)
    .await?;

    let new_pairs: Vec<(String, String, f64, f64, f64, f64)> = all_pairs
        .into_iter()
        .filter(|p| {
            !existing_pairs
                .iter()
                .any(|(f, t)| *f == p.from_stop_id && *t == p.to_stop_id)
        })
        .map(|p| (p.from_stop_id, p.to_stop_id, p.from_lat, p.from_lon, p.to_lat, p.to_lon))
        .collect();

    if new_pairs.is_empty() {
        return Ok(());
    }

    // Compute bounding box with 0.001° padding.
    let (min_lat, max_lat, min_lon, max_lon) = new_pairs.iter().fold(
        (f64::MAX, f64::MIN, f64::MAX, f64::MIN),
        |(mnlat, mxlat, mnlon, mxlon), (_, _, flat, flon, tlat, tlon)| {
            (
                mnlat.min(*flat).min(*tlat),
                mxlat.max(*flat).max(*tlat),
                mnlon.min(*flon).min(*tlon),
                mxlon.max(*flon).max(*tlon),
            )
        },
    );
    let (s, n, w, e) = (min_lat - 0.001, max_lat + 0.001, min_lon - 0.001, max_lon + 0.001);

    if let Some(pbf_path) = osm_pbf_path {
        let nodes = intersections_from_pbf(pbf_path, s, n, w, e)?;
        return store_intersections_from_raw(db, agency_id, &new_pairs, &nodes).await;
    }

    let query = format!(
        "[out:json][timeout:25];\
         (\
           node[\"highway\"=\"traffic_signals\"]({s},{w},{n},{e});\
           node[\"highway\"=\"stop\"]({s},{w},{n},{e});\
           node[\"highway\"=\"give_way\"]({s},{w},{n},{e});\
           node[\"highway\"=\"mini_roundabout\"]({s},{w},{n},{e});\
           node[\"highway\"=\"roundabout\"]({s},{w},{n},{e});\
         )->.intersections;\
         .intersections out body;\
         way(bn.intersections)[\"name\"][\"highway\"~\"^(primary|secondary|tertiary|residential|unclassified|trunk)\"];\
         out tags;"
    );

    let json = client
        .post("https://overpass-api.de/api/interpreter")
        .body(query)
        .send()
        .await?
        .text()
        .await?;

    store_intersections_from_json(db, agency_id, &new_pairs, &json).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn store_intersections_inserts_projected_rows() {
        let td = crate::db::test_utils::setup().await;
        let pool = &td.db.pool;

        // Segment: S1 at (49.28, -123.12) → S2 at (49.29, -123.11)
        // OSM node at (49.285, -123.115) should project to ~0.5
        let pairs: Vec<(String, String, f64, f64, f64, f64)> = vec![(
            "S1".to_string(), "S2".to_string(),
            49.28, -123.12, 49.29, -123.11,
        )];
        let json = r#"{"elements":[
          {"type":"node","id":1,"lat":49.285,"lon":-123.115,
           "tags":{"highway":"traffic_signals"}},
          {"type":"way","id":10,"nodes":[1,2],
           "tags":{"name":"Oak St","highway":"residential"}}
        ]}"#;

        store_intersections_from_json(&td.db, "ag", &pairs, json).await.unwrap();

        let rows: Vec<(String, String, String, Option<String>, f64)> = sqlx::query_as(
            "SELECT from_stop_id, to_stop_id, control_type, name, position_frac
             FROM stop_intersections WHERE agency_id = 'ag'"
        )
        .fetch_all(pool).await.unwrap();

        assert_eq!(rows.len(), 1);
        assert_eq!(rows[0].0, "S1");
        assert_eq!(rows[0].1, "S2");
        assert_eq!(rows[0].2, "traffic_signals");
        assert_eq!(rows[0].3.as_deref(), Some("Oak St"));
        assert!((rows[0].4 - 0.5).abs() < 0.1, "position_frac should be ~0.5, got {}", rows[0].4);
    }

    #[tokio::test]
    async fn store_intersections_is_idempotent() {
        let td = crate::db::test_utils::setup().await;
        let pairs: Vec<(String, String, f64, f64, f64, f64)> = vec![(
            "S1".to_string(), "S2".to_string(),
            49.28, -123.12, 49.29, -123.11,
        )];
        let json = r#"{"elements":[
          {"type":"node","id":1,"lat":49.285,"lon":-123.115,"tags":{"highway":"stop"}}
        ]}"#;

        store_intersections_from_json(&td.db, "ag", &pairs, json).await.unwrap();
        store_intersections_from_json(&td.db, "ag", &pairs, json).await.unwrap();

        let count: (i64,) = sqlx::query_as(
            "SELECT COUNT(*) FROM stop_intersections WHERE agency_id = 'ag'"
        )
        .fetch_one(&td.db.pool).await.unwrap();
        assert_eq!(count.0, 1, "second call should not duplicate rows");
    }

    #[test]
    fn project_midpoint_returns_half() {
        let t = project_to_segment(0.0, 0.5, 0.0, 0.0, 0.0, 1.0);
        assert!((t - 0.5).abs() < 1e-9, "expected 0.5 got {t}");
    }

    #[test]
    fn project_beyond_end_clamps_to_one() {
        let t = project_to_segment(0.0, 2.0, 0.0, 0.0, 0.0, 1.0);
        assert!((t - 1.0).abs() < 1e-9, "expected 1.0 got {t}");
    }

    #[test]
    fn project_before_start_clamps_to_zero() {
        let t = project_to_segment(0.0, -1.0, 0.0, 0.0, 0.0, 1.0);
        assert!((t - 0.0).abs() < 1e-9, "expected 0.0 got {t}");
    }

    #[test]
    fn project_zero_length_segment_returns_zero() {
        let t = project_to_segment(49.28, -123.12, 49.28, -123.12, 49.28, -123.12);
        assert!((t - 0.0).abs() < 1e-9);
    }

    #[test]
    fn build_name_empty_returns_none() {
        assert!(build_intersection_name(&[]).is_none());
    }

    #[test]
    fn build_name_one_street() {
        let names = vec!["Oak St".to_string()];
        assert_eq!(build_intersection_name(&names).unwrap(), "Oak St");
    }

    #[test]
    fn build_name_two_streets() {
        let names = vec!["Oak St".to_string(), "1st Ave".to_string()];
        assert_eq!(build_intersection_name(&names).unwrap(), "Oak St & 1st Ave");
    }

    #[test]
    fn build_name_three_streets_uses_first_two() {
        let names = vec![
            "Oak St".to_string(),
            "1st Ave".to_string(),
            "Highway 1".to_string(),
        ];
        assert_eq!(build_intersection_name(&names).unwrap(), "Oak St & 1st Ave");
    }

    #[test]
    fn parse_overpass_two_ways_builds_name() {
        let json = r#"{
          "elements": [
            {"type":"node","id":1,"lat":49.2828,"lon":-123.1207,
             "tags":{"highway":"traffic_signals"}},
            {"type":"way","id":10,"nodes":[1,2],
             "tags":{"name":"Oak St","highway":"residential"}},
            {"type":"way","id":11,"nodes":[1,3],
             "tags":{"name":"1st Ave","highway":"primary"}}
          ]
        }"#;
        let result = parse_overpass_response(json).unwrap();
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].control_type, "traffic_signals");
        assert!((result[0].lat - 49.2828).abs() < 1e-6);
        assert!((result[0].lon - (-123.1207)).abs() < 1e-6);
        assert_eq!(result[0].name.as_deref(), Some("Oak St & 1st Ave"));
    }

    #[test]
    fn parse_overpass_node_with_no_ways_has_no_name() {
        let json = r#"{"elements":[
          {"type":"node","id":5,"lat":49.30,"lon":-123.15,
           "tags":{"highway":"stop"}}
        ]}"#;
        let result = parse_overpass_response(json).unwrap();
        assert_eq!(result.len(), 1);
        assert!(result[0].name.is_none());
        assert_eq!(result[0].control_type, "stop");
    }

    // ── intersections_from_elements ──────────────────────────────────────────

    #[test]
    fn from_elements_traffic_signal_with_one_way_name() {
        let nodes = vec![OsmNode { id: 1, lat: 49.28, lon: -123.12, control_type: "traffic_signals".into() }];
        let ways = vec![OsmWay { node_ids: vec![1], name: "Oak St".into() }];
        let result = intersections_from_elements(&nodes, &ways);
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].control_type, "traffic_signals");
        assert!((result[0].lat - 49.28).abs() < 1e-9);
        assert!((result[0].lon - (-123.12)).abs() < 1e-9);
        assert_eq!(result[0].name.as_deref(), Some("Oak St"));
    }

    #[test]
    fn from_elements_node_with_two_crossing_ways_joins_names() {
        let nodes = vec![OsmNode { id: 1, lat: 49.28, lon: -123.12, control_type: "stop".into() }];
        let ways = vec![
            OsmWay { node_ids: vec![1, 2], name: "Oak St".into() },
            OsmWay { node_ids: vec![1, 3], name: "1st Ave".into() },
        ];
        let result = intersections_from_elements(&nodes, &ways);
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].name.as_deref(), Some("Oak St & 1st Ave"));
    }

    #[test]
    fn from_elements_node_with_no_crossing_ways_has_no_name() {
        let nodes = vec![OsmNode { id: 1, lat: 49.28, lon: -123.12, control_type: "give_way".into() }];
        let ways: Vec<OsmWay> = vec![];
        let result = intersections_from_elements(&nodes, &ways);
        assert_eq!(result.len(), 1);
        assert!(result[0].name.is_none());
    }

    #[test]
    fn from_elements_returns_all_five_control_types() {
        let nodes = vec![
            OsmNode { id: 1, lat: 1.0, lon: 1.0, control_type: "traffic_signals".into() },
            OsmNode { id: 2, lat: 2.0, lon: 2.0, control_type: "stop".into() },
            OsmNode { id: 3, lat: 3.0, lon: 3.0, control_type: "give_way".into() },
            OsmNode { id: 4, lat: 4.0, lon: 4.0, control_type: "mini_roundabout".into() },
            OsmNode { id: 5, lat: 5.0, lon: 5.0, control_type: "roundabout".into() },
        ];
        let result = intersections_from_elements(&nodes, &[]);
        assert_eq!(result.len(), 5);
        let types: Vec<&str> = result.iter().map(|r| r.control_type.as_str()).collect();
        assert!(types.contains(&"traffic_signals"));
        assert!(types.contains(&"stop"));
        assert!(types.contains(&"give_way"));
        assert!(types.contains(&"mini_roundabout"));
        assert!(types.contains(&"roundabout"));
    }

    #[test]
    fn from_elements_way_not_containing_node_does_not_add_name() {
        let nodes = vec![OsmNode { id: 1, lat: 49.28, lon: -123.12, control_type: "stop".into() }];
        let ways = vec![OsmWay { node_ids: vec![99, 100], name: "Unrelated St".into() }];
        let result = intersections_from_elements(&nodes, &ways);
        assert_eq!(result.len(), 1);
        assert!(result[0].name.is_none());
    }

    #[test]
    fn parse_overpass_all_five_control_types() {
        let json = r#"{"elements":[
          {"type":"node","id":1,"lat":1.0,"lon":1.0,"tags":{"highway":"traffic_signals"}},
          {"type":"node","id":2,"lat":2.0,"lon":2.0,"tags":{"highway":"stop"}},
          {"type":"node","id":3,"lat":3.0,"lon":3.0,"tags":{"highway":"give_way"}},
          {"type":"node","id":4,"lat":4.0,"lon":4.0,"tags":{"highway":"mini_roundabout"}},
          {"type":"node","id":5,"lat":5.0,"lon":5.0,"tags":{"highway":"roundabout"}}
        ]}"#;
        let result = parse_overpass_response(json).unwrap();
        assert_eq!(result.len(), 5);
        let types: Vec<&str> = result.iter().map(|r| r.control_type.as_str()).collect();
        assert!(types.contains(&"traffic_signals"));
        assert!(types.contains(&"stop"));
        assert!(types.contains(&"give_way"));
        assert!(types.contains(&"mini_roundabout"));
        assert!(types.contains(&"roundabout"));
    }

    #[test]
    fn dist_to_segment_point_on_segment_is_zero() {
        // Horizontal segment along lat=49.28, lon from -123.12 to -123.11.
        // Point at midpoint should be ~0 m away.
        let d = dist_to_segment_m(49.28, -123.115, 49.28, -123.12, 49.28, -123.11);
        assert!(d < 1.0, "expected ~0 m, got {d}");
    }

    #[test]
    fn dist_to_segment_perpendicular_100m_away() {
        // 0.001° latitude ≈ 111 m; so 0.0004° ≈ 44 m.
        // Node displaced ~111 m north of a horizontal segment.
        let d = dist_to_segment_m(49.281, -123.115, 49.28, -123.12, 49.28, -123.11);
        // 0.001° lat * 111_000 m/° ≈ 111 m
        assert!(d > 100.0 && d < 120.0, "expected ~111 m, got {d}");
    }

    #[tokio::test]
    async fn store_intersections_discards_nodes_beyond_50m() {
        let td = crate::db::test_utils::setup().await;
        let pairs: Vec<(String, String, f64, f64, f64, f64)> = vec![(
            "S1".to_string(), "S2".to_string(),
            49.28, -123.12, 49.28, -123.11, // horizontal segment
        )];
        // Node 100+ m away (0.001° north ≈ 111 m)
        let json = r#"{"elements":[
          {"type":"node","id":1,"lat":49.281,"lon":-123.115,"tags":{"highway":"traffic_signals"}}
        ]}"#;

        store_intersections_from_json(&td.db, "ag", &pairs, json).await.unwrap();

        let count: (i64,) = sqlx::query_as(
            "SELECT COUNT(*) FROM stop_intersections WHERE agency_id = 'ag'"
        )
        .fetch_one(&td.db.pool).await.unwrap();
        assert_eq!(count.0, 0, "node 111 m away should be discarded");
    }
}

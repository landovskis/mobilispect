//! Pure GeoJSON `FeatureCollection` assembly for the region map's corridor
//! overlay. Takes already-fetched `CorridorForMap` rows (`repository.rs`'s
//! concern) and produces the exact JSON shape MapLibre GL JS expects — no
//! I/O here.

use crate::remix::CorridorForMap;
use serde::Serialize;

#[derive(Debug, Serialize)]
pub struct FeatureCollection {
    #[serde(rename = "type")]
    pub kind: &'static str,
    pub features: Vec<Feature>,
}

#[derive(Debug, Serialize)]
pub struct Feature {
    #[serde(rename = "type")]
    pub kind: &'static str,
    pub geometry: Geometry,
    pub properties: serde_json::Value,
}

#[derive(Debug, Serialize)]
#[serde(tag = "type")]
pub enum Geometry {
    LineString { coordinates: Vec<[f64; 2]> },
    Point { coordinates: [f64; 2] },
}

/// Builds the region map's corridor overlay: one `LineString` feature per
/// corridor (properties: `feature_type: "corridor"`, `corridor_id`,
/// `highlighted`) plus one `Point` feature per corridor endpoint — its
/// first and last cross-section (properties: `feature_type:
/// "intersection"`, `cross_section_id`) — see the design spec's
/// intersection-identifier clarification. Corridors with fewer than 2
/// cross-sections contribute no features (nothing to draw or click).
pub fn build_corridors_feature_collection(corridors: &[CorridorForMap]) -> FeatureCollection {
    let mut features = Vec::new();

    for corridor in corridors {
        if corridor.cross_sections.len() < 2 {
            continue;
        }

        let coordinates: Vec<[f64; 2]> = corridor
            .cross_sections
            .iter()
            .map(|cs| [cs.lon, cs.lat])
            .collect();

        features.push(Feature {
            kind: "Feature",
            geometry: Geometry::LineString { coordinates },
            properties: serde_json::json!({
                "feature_type": "corridor",
                "corridor_id": corridor.corridor_id.as_i64(),
                "highlighted": corridor.highlighted,
            }),
        });

        let first = corridor.cross_sections.first().unwrap();
        let last = corridor.cross_sections.last().unwrap();
        for endpoint in [first, last] {
            features.push(Feature {
                kind: "Feature",
                geometry: Geometry::Point {
                    coordinates: [endpoint.lon, endpoint.lat],
                },
                properties: serde_json::json!({
                    "feature_type": "intersection",
                    "cross_section_id": endpoint.cross_section_id.as_i64(),
                }),
            });
        }
    }

    FeatureCollection {
        kind: "FeatureCollection",
        features,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ids::{CorridorId, CrossSectionId};
    use crate::remix::CrossSectionPointForMap;

    fn point(id: i64, lat: f64, lon: f64) -> CrossSectionPointForMap {
        CrossSectionPointForMap {
            cross_section_id: CrossSectionId::from(id),
            lat,
            lon,
        }
    }

    #[test]
    fn corridor_with_three_points_produces_one_line_and_two_endpoint_points() {
        let corridors = vec![CorridorForMap {
            corridor_id: CorridorId::from(1),
            highlighted: true,
            cross_sections: vec![
                point(10, 45.50, -73.60),
                point(11, 45.51, -73.59),
                point(12, 45.52, -73.58),
            ],
        }];

        let fc = build_corridors_feature_collection(&corridors);

        assert_eq!(fc.kind, "FeatureCollection");
        assert_eq!(fc.features.len(), 3, "1 LineString + 2 Point endpoints");

        let line = &fc.features[0];
        assert_eq!(line.kind, "Feature");
        match &line.geometry {
            Geometry::LineString { coordinates } => {
                assert_eq!(
                    coordinates,
                    &vec![[-73.60, 45.50], [-73.59, 45.51], [-73.58, 45.52]]
                );
            }
            _ => panic!("expected LineString"),
        }
        assert_eq!(line.properties["feature_type"], "corridor");
        assert_eq!(line.properties["corridor_id"], 1);
        assert_eq!(line.properties["highlighted"], true);

        let first_point = &fc.features[1];
        match &first_point.geometry {
            Geometry::Point { coordinates } => assert_eq!(coordinates, &[-73.60, 45.50]),
            _ => panic!("expected Point"),
        }
        assert_eq!(first_point.properties["feature_type"], "intersection");
        assert_eq!(first_point.properties["cross_section_id"], 10);

        let last_point = &fc.features[2];
        match &last_point.geometry {
            Geometry::Point { coordinates } => assert_eq!(coordinates, &[-73.58, 45.52]),
            _ => panic!("expected Point"),
        }
        assert_eq!(last_point.properties["cross_section_id"], 12);
    }

    #[test]
    fn corridor_with_fewer_than_two_points_contributes_no_features() {
        let corridors = vec![CorridorForMap {
            corridor_id: CorridorId::from(1),
            highlighted: false,
            cross_sections: vec![point(10, 45.50, -73.60)],
        }];

        let fc = build_corridors_feature_collection(&corridors);

        assert_eq!(fc.features.len(), 0);
    }

    #[test]
    fn multiple_corridors_accumulate_features_from_all() {
        let corridors = vec![
            CorridorForMap {
                corridor_id: CorridorId::from(1),
                highlighted: false,
                cross_sections: vec![point(10, 45.50, -73.60), point(11, 45.51, -73.59)],
            },
            CorridorForMap {
                corridor_id: CorridorId::from(2),
                highlighted: true,
                cross_sections: vec![point(20, 46.00, -74.00), point(21, 46.01, -74.01)],
            },
        ];

        let fc = build_corridors_feature_collection(&corridors);

        assert_eq!(fc.features.len(), 6, "2 corridors x (1 line + 2 points)");
    }

    #[test]
    fn empty_input_produces_empty_feature_collection() {
        let fc = build_corridors_feature_collection(&[]);
        assert_eq!(fc.features.len(), 0);
    }
}

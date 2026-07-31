//! Pure geometry validation and normalization — no I/O.
//!
//! Covers REQ-001 (import: [`normalize_corridor_geometry`]) and REQ-002 (manual
//! tracing: point-by-point validation, added once that requirement's pass starts).

use super::Coordinate;

/// A single point in raw source geometry, before normalization.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct RawPoint {
    pub coordinate: Coordinate,
    pub osm_node_id: Option<i64>,
}

/// One OSM way's worth of raw points, in source order.
#[derive(Debug, Clone, PartialEq)]
pub struct RawWaySegment {
    pub osm_way_id: Option<i64>,
    pub points: Vec<RawPoint>,
}

/// The full set of way segments returned for one import request.
#[derive(Debug, Clone, PartialEq, Default)]
pub struct RawGeometry {
    pub segments: Vec<RawWaySegment>,
}

/// One point in a corridor's normalized, ordered cross-section sequence.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CrossSectionPoint {
    pub position: i32,
    pub coordinate: Coordinate,
    pub osm_way_id: Option<i64>,
    pub osm_node_id: Option<i64>,
}

/// The result of successfully normalizing a corridor's imported geometry.
#[derive(Debug, Clone, PartialEq, Default)]
pub struct NormalizedCorridor {
    pub cross_sections: Vec<CrossSectionPoint>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ImportGeometryError {
    /// The source payload itself is malformed (invalid coordinates, wrong shape).
    Malformed(String),
    /// Way segments don't connect end-to-end into a single path.
    Disconnected,
    /// The path crosses itself.
    SelfIntersecting,
    /// A way segment has fewer than 2 points, or the geometry as a whole was cut
    /// short (e.g. a truncated network response) and cannot represent a corridor.
    IncompleteGeometry,
}

impl std::fmt::Display for ImportGeometryError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ImportGeometryError::Malformed(reason) => write!(f, "malformed geometry: {reason}"),
            ImportGeometryError::Disconnected => {
                write!(f, "path segments are not connected end-to-end")
            }
            ImportGeometryError::SelfIntersecting => write!(f, "path self-intersects"),
            ImportGeometryError::IncompleteGeometry => write!(
                f,
                "geometry is incomplete (a segment has fewer than 2 points, or the source was truncated)"
            ),
        }
    }
}

impl std::error::Error for ImportGeometryError {}

/// Orders a set of way segments into one connected, non-self-intersecting sequence
/// of cross-sections, assigning each point a `position` (0-based, path order).
///
/// Pure — no I/O. Coordinates are assumed already WGS84 (GeoJSON's implicit CRS per
/// RFC 7946, and the CRS OSM/Overpass data is published in) — this function validates
/// that assumption via range/finiteness checks rather than performing CRS conversion;
/// out-of-range or non-finite coordinates are rejected as malformed, not reprojected.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-001-06 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub fn normalize_corridor_geometry(
    raw: RawGeometry,
) -> Result<NormalizedCorridor, ImportGeometryError> {
    let _ = raw;
    unimplemented!("IMP-REQ-001-06: normalize_corridor_geometry not yet implemented")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn point(lat: f64, lon: f64, osm_node_id: Option<i64>) -> RawPoint {
        RawPoint {
            coordinate: Coordinate::new(lat, lon),
            osm_node_id,
        }
    }

    /// TC-REQ-001-1 (unit slice): two way segments sharing an endpoint node normalize
    /// into one ordered, connected sequence — order reflects physical adjacency, not
    /// input array order.
    #[test]
    fn normalize_corridor_geometry_orders_two_connected_segments() {
        // Segment B is listed first in the input, but segment A's end connects to
        // segment B's start — the correct output order is A's points then B's points.
        let seg_a = RawWaySegment {
            osm_way_id: Some(111),
            points: vec![
                point(45.500, -73.580, Some(1)),
                point(45.501, -73.579, Some(2)),
            ],
        };
        let seg_b = RawWaySegment {
            osm_way_id: Some(112),
            points: vec![
                point(45.501, -73.579, Some(2)), // shared node with seg_a's end
                point(45.502, -73.578, Some(3)),
            ],
        };
        let raw = RawGeometry {
            segments: vec![seg_b.clone(), seg_a.clone()],
        };

        let normalized = normalize_corridor_geometry(raw).expect("well-formed connected geometry");

        assert_eq!(normalized.cross_sections.len(), 3);
        let positions: Vec<i32> = normalized
            .cross_sections
            .iter()
            .map(|cs| cs.position)
            .collect();
        assert_eq!(positions, vec![0, 1, 2]);
        // Physical order: seg_a's first point, the shared node, seg_b's last point —
        // not seg_b-then-seg_a (input array order).
        assert_eq!(
            normalized.cross_sections[0].coordinate,
            seg_a.points[0].coordinate
        );
        assert_eq!(
            normalized.cross_sections[1].coordinate,
            seg_a.points[1].coordinate
        );
        assert_eq!(
            normalized.cross_sections[2].coordinate,
            seg_b.points[1].coordinate
        );
    }

    /// TC-REQ-001-3 (unit slice): a path that crosses itself is rejected.
    #[test]
    fn normalize_corridor_geometry_rejects_self_intersecting_path() {
        // A simple bowtie: (0,0)->(1,1) then (0,1)->(1,0) crosses the first segment.
        let raw = RawGeometry {
            segments: vec![RawWaySegment {
                osm_way_id: Some(200),
                points: vec![
                    point(0.0, 0.0, Some(10)),
                    point(1.0, 1.0, Some(11)),
                    point(0.0, 1.0, Some(12)),
                    point(1.0, 0.0, Some(13)),
                ],
            }],
        };

        let result = normalize_corridor_geometry(raw);
        assert_eq!(result, Err(ImportGeometryError::SelfIntersecting));
    }

    /// TC-REQ-001-4 (unit slice): two way segments with no shared endpoint (a real
    /// gap, well beyond floating-point tolerance) are rejected as disconnected.
    #[test]
    fn normalize_corridor_geometry_rejects_disconnected_segments() {
        let seg_a = RawWaySegment {
            osm_way_id: Some(300),
            points: vec![
                point(45.500, -73.580, Some(20)),
                point(45.501, -73.579, Some(21)),
            ],
        };
        // ~150m away from seg_a's endpoint — far beyond any snapping tolerance.
        let seg_b = RawWaySegment {
            osm_way_id: Some(301),
            points: vec![
                point(45.503, -73.575, Some(22)),
                point(45.504, -73.574, Some(23)),
            ],
        };
        let raw = RawGeometry {
            segments: vec![seg_a, seg_b],
        };

        let result = normalize_corridor_geometry(raw);
        assert_eq!(result, Err(ImportGeometryError::Disconnected));
    }

    /// TC-REQ-001-8 (unit slice): a way segment with fewer than 2 points — as would
    /// result from a source response truncated mid-transfer — is rejected as
    /// incomplete, distinct from malformed/disconnected/self-intersecting.
    #[test]
    fn normalize_corridor_geometry_rejects_segment_with_fewer_than_two_points() {
        let raw = RawGeometry {
            segments: vec![RawWaySegment {
                osm_way_id: Some(400),
                points: vec![point(45.500, -73.580, Some(30))],
            }],
        };

        let result = normalize_corridor_geometry(raw);
        assert_eq!(result, Err(ImportGeometryError::IncompleteGeometry));
    }

    /// TC-REQ-001-7 (unit slice): coordinates already in valid WGS84 range pass
    /// through unchanged — this function performs no CRS conversion (see doc
    /// comment on `normalize_corridor_geometry`); it validates the WGS84 assumption
    /// rather than reprojecting from another CRS.
    #[test]
    fn normalize_corridor_geometry_preserves_valid_wgs84_coordinates_unchanged() {
        let raw = RawGeometry {
            segments: vec![RawWaySegment {
                osm_way_id: Some(500),
                points: vec![
                    point(45.421500, -75.697200, Some(40)),
                    point(45.421600, -75.697100, Some(41)),
                ],
            }],
        };

        let normalized = normalize_corridor_geometry(raw.clone()).expect("valid WGS84 geometry");

        assert_eq!(
            normalized.cross_sections[0].coordinate,
            raw.segments[0].points[0].coordinate
        );
        assert_eq!(
            normalized.cross_sections[1].coordinate,
            raw.segments[0].points[1].coordinate
        );
    }

    /// TC-REQ-001-7 boundary: coordinates outside valid lat/lon range (e.g. a
    /// non-WGS84 source that was never converted) are rejected as malformed rather
    /// than silently accepted or mis-normalized.
    #[test]
    fn normalize_corridor_geometry_rejects_out_of_range_coordinates() {
        let raw = RawGeometry {
            segments: vec![RawWaySegment {
                osm_way_id: Some(600),
                // lat=145.0 is outside the valid WGS84 range — plausible if source
                // data used a different (unconverted) coordinate reference system.
                points: vec![
                    point(145.0, -73.580, Some(50)),
                    point(45.501, -73.579, Some(51)),
                ],
            }],
        };

        let result = normalize_corridor_geometry(raw);
        assert!(matches!(result, Err(ImportGeometryError::Malformed(_))));
    }
}

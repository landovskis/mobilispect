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

/// Errors from validating a manually-traced corridor's points (REQ-002). Distinct
/// from [`ImportGeometryError`], which covers bulk-imported geometry — manual tracing
/// validates one point at a time as the analyst clicks, so its failure modes differ.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum GeometryValidationError {
    /// The candidate point is identical to, or within `MIN_POINT_SEPARATION_METERS`
    /// of, the previous point in the trace.
    DuplicateOrTooClose,
    /// Fewer than the minimum number of points required to finish a trace.
    InsufficientPoints,
}

impl std::fmt::Display for GeometryValidationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            GeometryValidationError::DuplicateOrTooClose => write!(
                f,
                "point is too close to (or identical to) the previous point"
            ),
            GeometryValidationError::InsufficientPoints => {
                write!(f, "not enough points to finish the trace")
            }
        }
    }
}

impl std::error::Error for GeometryValidationError {}

/// Minimum distance, in meters, a newly-clicked point must be from the previous
/// point in a manual trace to be accepted. See `validate_next_point`.
pub const MIN_POINT_SEPARATION_METERS: f64 = 5.0;

/// Validates a candidate point against the corridor's existing points.
///
/// Pure — no I/O. Rejects points that are duplicate or within
/// `MIN_POINT_SEPARATION_METERS` of the immediately preceding point.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-002-04 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub fn validate_next_point(
    existing: &[Coordinate],
    candidate: Coordinate,
) -> Result<(), GeometryValidationError> {
    let Some(previous) = existing.last() else {
        return Ok(());
    };
    const EPSILON: f64 = 1e-9;
    if haversine_meters(*previous, candidate) < MIN_POINT_SEPARATION_METERS - EPSILON {
        return Err(GeometryValidationError::DuplicateOrTooClose);
    }
    Ok(())
}

/// Enforces the minimum point count (>= 2) required to finish a manual trace.
///
/// Pure — no I/O.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-002-04 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub fn validate_finishable(points: &[Coordinate]) -> Result<(), GeometryValidationError> {
    if points.len() < 2 {
        return Err(GeometryValidationError::InsufficientPoints);
    }
    Ok(())
}

/// Computes the next `position` value for a new cross-section given the current
/// ordered list of points: `0` for an empty list, `existing.len()` otherwise.
///
/// Pure — no I/O.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-002-04 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub fn next_position(existing: &[Coordinate]) -> i32 {
    existing.len() as i32
}

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
    if raw.segments.is_empty() {
        return Err(ImportGeometryError::IncompleteGeometry);
    }
    for segment in &raw.segments {
        if segment.points.len() < 2 {
            return Err(ImportGeometryError::IncompleteGeometry);
        }
        for point in &segment.points {
            if !point.coordinate.is_valid() {
                return Err(ImportGeometryError::Malformed(format!(
                    "coordinate ({}, {}) is outside valid WGS84 range",
                    point.coordinate.lat, point.coordinate.lon
                )));
            }
        }
    }

    let ordered_points = order_segments_into_path(&raw.segments)?;

    if path_self_intersects(&ordered_points) {
        return Err(ImportGeometryError::SelfIntersecting);
    }

    let cross_sections = ordered_points
        .into_iter()
        .enumerate()
        .map(|(i, p)| CrossSectionPoint {
            position: i as i32,
            coordinate: p.coordinate,
            osm_way_id: p.osm_way_id,
            osm_node_id: p.osm_node_id,
        })
        .collect();

    Ok(NormalizedCorridor { cross_sections })
}

fn haversine_meters(a: Coordinate, b: Coordinate) -> f64 {
    const EARTH_RADIUS_M: f64 = 6_371_000.0;
    let lat1 = a.lat.to_radians();
    let lat2 = b.lat.to_radians();
    let delta_lat = (b.lat - a.lat).to_radians();
    let delta_lon = (b.lon - a.lon).to_radians();
    let h =
        (delta_lat / 2.0).sin().powi(2) + lat1.cos() * lat2.cos() * (delta_lon / 2.0).sin().powi(2);
    2.0 * EARTH_RADIUS_M * h.sqrt().asin()
}

#[derive(Debug, Clone, Copy)]
struct OrderedPoint {
    coordinate: Coordinate,
    osm_way_id: Option<i64>,
    osm_node_id: Option<i64>,
}

/// Orders way segments end-to-end into one connected path. A single segment is
/// trivially "ordered" as-is. Multiple segments must chain via shared endpoint
/// coordinates (within floating-point tolerance) — this rejects (as
/// `Disconnected`) any segment that doesn't connect to the growing chain at
/// either end.
fn order_segments_into_path(
    segments: &[RawWaySegment],
) -> Result<Vec<OrderedPoint>, ImportGeometryError> {
    const COORDINATE_TOLERANCE: f64 = 1e-9;

    fn coords_match(a: Coordinate, b: Coordinate) -> bool {
        (a.lat - b.lat).abs() < COORDINATE_TOLERANCE && (a.lon - b.lon).abs() < COORDINATE_TOLERANCE
    }

    fn segment_points(segment: &RawWaySegment) -> Vec<OrderedPoint> {
        segment
            .points
            .iter()
            .map(|p| OrderedPoint {
                coordinate: p.coordinate,
                osm_way_id: segment.osm_way_id,
                osm_node_id: p.osm_node_id,
            })
            .collect()
    }

    let mut remaining: Vec<&RawWaySegment> = segments.iter().collect();
    let first = remaining.remove(0);
    let mut chain = segment_points(first);

    while !remaining.is_empty() {
        let chain_start = chain.first().unwrap().coordinate;
        let chain_end = chain.last().unwrap().coordinate;

        let match_index = remaining.iter().position(|seg| {
            let seg_start = seg.points.first().unwrap().coordinate;
            let seg_end = seg.points.last().unwrap().coordinate;
            coords_match(chain_end, seg_start)
                || coords_match(chain_end, seg_end)
                || coords_match(chain_start, seg_start)
                || coords_match(chain_start, seg_end)
        });

        let Some(index) = match_index else {
            return Err(ImportGeometryError::Disconnected);
        };

        let segment = remaining.remove(index);
        let seg_start = segment.points.first().unwrap().coordinate;
        let seg_end = segment.points.last().unwrap().coordinate;
        let mut points = segment_points(segment);

        if coords_match(chain_end, seg_start) {
            // Appends after the chain's end, dropping the duplicate shared point.
            points.remove(0);
            chain.extend(points);
        } else if coords_match(chain_end, seg_end) {
            points.reverse();
            points.remove(0);
            chain.extend(points);
        } else if coords_match(chain_start, seg_end) {
            points.pop();
            points.extend(chain);
            chain = points;
        } else {
            // coords_match(chain_start, seg_start)
            points.reverse();
            points.pop();
            points.extend(chain);
            chain = points;
        }
    }

    Ok(chain)
}

/// True if any two non-adjacent segments of the path cross each other.
/// Adjacent segments sharing an endpoint are not considered a self-intersection.
fn path_self_intersects(points: &[OrderedPoint]) -> bool {
    if points.len() < 4 {
        return false;
    }
    for i in 0..points.len() - 1 {
        let a1 = points[i].coordinate;
        let a2 = points[i + 1].coordinate;
        for j in (i + 2)..points.len() - 1 {
            // Skip the pair that shares an endpoint with segment i (the path's
            // own last segment wrapping to its first point, if ever closed).
            if i == 0
                && j == points.len() - 2
                && points[0].coordinate == points[points.len() - 1].coordinate
            {
                continue;
            }
            let b1 = points[j].coordinate;
            let b2 = points[j + 1].coordinate;
            if segments_intersect(a1, a2, b1, b2) {
                return true;
            }
        }
    }
    false
}

fn segments_intersect(p1: Coordinate, p2: Coordinate, p3: Coordinate, p4: Coordinate) -> bool {
    fn orientation(a: Coordinate, b: Coordinate, c: Coordinate) -> f64 {
        (b.lon - a.lon) * (c.lat - a.lat) - (b.lat - a.lat) * (c.lon - a.lon)
    }
    fn on_segment(a: Coordinate, b: Coordinate, c: Coordinate) -> bool {
        c.lon.min(a.lon.min(b.lon)) <= c.lon
            && c.lon <= a.lon.max(b.lon.max(c.lon))
            && c.lat.min(a.lat.min(b.lat)) <= c.lat
            && c.lat <= a.lat.max(b.lat.max(c.lat))
    }

    let o1 = orientation(p1, p2, p3);
    let o2 = orientation(p1, p2, p4);
    let o3 = orientation(p3, p4, p1);
    let o4 = orientation(p3, p4, p2);

    if (o1 > 0.0) != (o2 > 0.0) && (o3 > 0.0) != (o4 > 0.0) && o1 != 0.0 && o2 != 0.0 {
        return true;
    }

    (o1 == 0.0 && on_segment(p1, p2, p3))
        || (o2 == 0.0 && on_segment(p1, p2, p4))
        || (o3 == 0.0 && on_segment(p3, p4, p1))
        || (o4 == 0.0 && on_segment(p3, p4, p2))
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

    // --- REQ-002: manual trace point/finish validators ---

    /// Computes a coordinate exactly `meters` due north of `origin`, along the same
    /// meridian. For two points sharing a longitude, the haversine formula reduces
    /// exactly to `distance = EARTH_RADIUS_M * delta_latitude_radians` (no
    /// approximation) — this uses the same `EARTH_RADIUS_M` constant as
    /// `speed_analysis::haversine_meters`, so fixtures built here line up with
    /// whatever haversine-based implementation Loop B lands for `validate_next_point`.
    /// Test-only: not part of the pure validator API.
    fn point_north_of(origin: Coordinate, meters: f64) -> Coordinate {
        const EARTH_RADIUS_M: f64 = 6_371_000.0;
        let delta_lat_deg = (meters / EARTH_RADIUS_M).to_degrees();
        Coordinate::new(origin.lat + delta_lat_deg, origin.lon)
    }

    /// TC-REQ-002-03 (unit slice): a click at the exact same coordinate as the
    /// previous point is rejected as a duplicate.
    #[test]
    fn validate_next_point_rejects_exact_duplicate() {
        let previous = Coordinate::new(45.5017, -73.5673);
        let result = validate_next_point(&[previous], previous);
        assert_eq!(result, Err(GeometryValidationError::DuplicateOrTooClose));
    }

    /// TC-REQ-002-03 (unit slice): a click within `MIN_POINT_SEPARATION_METERS` of
    /// the previous point is rejected.
    #[test]
    fn validate_next_point_rejects_point_within_min_separation() {
        let previous = Coordinate::new(45.5017, -73.5673);
        let candidate = point_north_of(previous, 2.0);
        let result = validate_next_point(&[previous], candidate);
        assert_eq!(result, Err(GeometryValidationError::DuplicateOrTooClose));
    }

    /// Boundary: a click exactly `MIN_POINT_SEPARATION_METERS` away from the previous
    /// point is accepted (the boundary itself is inclusive of "far enough").
    #[test]
    fn validate_next_point_accepts_point_exactly_at_min_separation_boundary() {
        let previous = Coordinate::new(45.5017, -73.5673);
        let candidate = point_north_of(previous, MIN_POINT_SEPARATION_METERS);
        let result = validate_next_point(&[previous], candidate);
        assert_eq!(result, Ok(()));
    }

    /// Boundary: a click safely beyond `MIN_POINT_SEPARATION_METERS` is accepted.
    #[test]
    fn validate_next_point_accepts_point_beyond_min_separation() {
        let previous = Coordinate::new(45.5017, -73.5673);
        let candidate = point_north_of(previous, MIN_POINT_SEPARATION_METERS + 5.0);
        let result = validate_next_point(&[previous], candidate);
        assert_eq!(result, Ok(()));
    }

    /// TC-REQ-002-02 (unit slice): an empty point list cannot finish a trace.
    #[test]
    fn validate_finishable_rejects_empty_point_list() {
        let result = validate_finishable(&[]);
        assert_eq!(result, Err(GeometryValidationError::InsufficientPoints));
    }

    /// TC-REQ-002-02 (unit slice): a single-point list — the exact boundary case from
    /// TC-REQ-002-02's precondition — cannot finish a trace.
    #[test]
    fn validate_finishable_rejects_single_point_list() {
        let points = [Coordinate::new(45.5017, -73.5673)];
        let result = validate_finishable(&points);
        assert_eq!(result, Err(GeometryValidationError::InsufficientPoints));
    }

    /// A two-point list meets the minimum and is accepted.
    #[test]
    fn validate_finishable_accepts_two_point_list() {
        let points = [
            Coordinate::new(45.5017, -73.5673),
            Coordinate::new(45.5031, -73.5661),
        ];
        let result = validate_finishable(&points);
        assert_eq!(result, Ok(()));
    }

    /// `next_position` returns `0` for an empty list (the first point placed).
    #[test]
    fn next_position_returns_zero_for_empty_list() {
        assert_eq!(next_position(&[]), 0);
    }

    /// `next_position` returns `existing.len()` for a non-empty ordered list.
    #[test]
    fn next_position_returns_len_for_nonempty_list() {
        let points = [
            Coordinate::new(45.5017, -73.5673),
            Coordinate::new(45.5031, -73.5661),
            Coordinate::new(45.5045, -73.5649),
        ];
        assert_eq!(next_position(&points), 3);
    }
}

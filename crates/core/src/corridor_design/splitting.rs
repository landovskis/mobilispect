//! Pure logic for splitting a corridor at an interior cross-section into two
//! corridors meeting at a new shared `Intersection` -- no I/O. See
//! `docs/superpowers/specs/2026-08-12-corridor-intersection-aggregate-design.md`,
//! "Splitting flow".

use crate::corridor_design::CrossSection;
use crate::ids::CrossSectionId;

/// Minimum distance, in meters, a split point must be from either of the
/// corridor's existing endpoints to be accepted -- guards against creating a
/// degenerate near-zero-length corridor fragment (the "dog-leg" edge case).
pub const MIN_SPLIT_ENDPOINT_DISTANCE_METERS: f64 = 3.0;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SplitError {
    /// `split_at` does not match any cross-section in the given sequence.
    NotFound(CrossSectionId),
    /// `split_at` is already the corridor's first or last cross-section --
    /// nothing to split, it's already an endpoint.
    AlreadyEndpoint(CrossSectionId),
    /// `split_at` is within `MIN_SPLIT_ENDPOINT_DISTANCE_METERS` of an
    /// existing endpoint -- splitting here would create a sliver corridor.
    TooCloseToEndpoint(CrossSectionId),
}

impl std::fmt::Display for SplitError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            SplitError::NotFound(id) => write!(f, "cross-section {id} not found in this corridor"),
            SplitError::AlreadyEndpoint(id) => {
                write!(
                    f,
                    "cross-section {id} is already an endpoint, nothing to split"
                )
            }
            SplitError::TooCloseToEndpoint(id) => write!(
                f,
                "cross-section {id} is too close to an existing endpoint to split there"
            ),
        }
    }
}

impl std::error::Error for SplitError {}

/// The result of successfully partitioning a corridor's cross-sections at
/// `split_at`: everything up to and including `split_at` stays on the head
/// (original corridor); everything after moves to the tail (new corridor).
#[derive(Debug, Clone, PartialEq)]
pub struct SplitPartition {
    pub head: Vec<CrossSection>,
    pub tail: Vec<CrossSection>,
    pub new_intersection_lat: f64,
    pub new_intersection_lon: f64,
    pub new_intersection_osm_node_id: Option<i64>,
}

fn haversine_meters(a: (f64, f64), b: (f64, f64)) -> f64 {
    const EARTH_RADIUS_M: f64 = 6_371_000.0;
    let (lat1, lon1) = a;
    let (lat2, lon2) = b;
    let lat1_r = lat1.to_radians();
    let lat2_r = lat2.to_radians();
    let delta_lat = (lat2 - lat1).to_radians();
    let delta_lon = (lon2 - lon1).to_radians();
    let h = (delta_lat / 2.0).sin().powi(2)
        + lat1_r.cos() * lat2_r.cos() * (delta_lon / 2.0).sin().powi(2);
    2.0 * EARTH_RADIUS_M * h.sqrt().asin()
}

/// Partitions `cross_sections` (must already be ordered by `position`) at
/// `split_at`. Pure -- no I/O; the caller (`repository::split_corridor_at_cross_section`)
/// is responsible for executing the partition as a database transaction.
pub fn partition_at_split_point(
    cross_sections: &[CrossSection],
    split_at: CrossSectionId,
) -> Result<SplitPartition, SplitError> {
    let Some(split_index) = cross_sections.iter().position(|cs| cs.id == split_at) else {
        return Err(SplitError::NotFound(split_at));
    };

    let first_index = 0;
    let last_index = cross_sections.len() - 1;
    if split_index == first_index || split_index == last_index {
        return Err(SplitError::AlreadyEndpoint(split_at));
    }

    let split_point = &cross_sections[split_index];
    let first_point = &cross_sections[first_index];
    let last_point = &cross_sections[last_index];
    let distance_to_first = haversine_meters(
        (split_point.lat, split_point.lon),
        (first_point.lat, first_point.lon),
    );
    let distance_to_last = haversine_meters(
        (split_point.lat, split_point.lon),
        (last_point.lat, last_point.lon),
    );
    if distance_to_first < MIN_SPLIT_ENDPOINT_DISTANCE_METERS
        || distance_to_last < MIN_SPLIT_ENDPOINT_DISTANCE_METERS
    {
        return Err(SplitError::TooCloseToEndpoint(split_at));
    }

    let head = cross_sections[..=split_index].to_vec();
    let tail = cross_sections[split_index..].to_vec();

    Ok(SplitPartition {
        head,
        tail,
        new_intersection_lat: split_point.lat,
        new_intersection_lon: split_point.lon,
        new_intersection_osm_node_id: split_point.osm_node_id,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ids::CorridorId;

    fn cs(id: i64, position: f64, lat: f64, lon: f64) -> CrossSection {
        CrossSection {
            id: CrossSectionId::from(id),
            corridor_id: CorridorId::from(1),
            position,
            lat,
            lon,
            osm_way_id: None,
            osm_node_id: None,
            label: None,
            version: 1,
            intersection_id: None,
        }
    }

    /// Five points spaced ~111m apart (0.001 degrees of latitude), well
    /// beyond MIN_SPLIT_ENDPOINT_DISTANCE_METERS from either end.
    fn five_point_corridor() -> Vec<CrossSection> {
        vec![
            cs(1, 0.0, 45.500, -73.600),
            cs(2, 1.0, 45.501, -73.600),
            cs(3, 2.0, 45.502, -73.600),
            cs(4, 3.0, 45.503, -73.600),
            cs(5, 4.0, 45.504, -73.600),
        ]
    }

    #[test]
    fn partition_at_split_point_splits_head_and_tail_correctly() {
        let sections = five_point_corridor();
        let result = partition_at_split_point(&sections, CrossSectionId::from(3)).unwrap();

        assert_eq!(
            result.head.iter().map(|cs| cs.id).collect::<Vec<_>>(),
            vec![
                CrossSectionId::from(1),
                CrossSectionId::from(2),
                CrossSectionId::from(3)
            ]
        );
        assert_eq!(
            result.tail.iter().map(|cs| cs.id).collect::<Vec<_>>(),
            vec![
                CrossSectionId::from(3),
                CrossSectionId::from(4),
                CrossSectionId::from(5)
            ]
        );
    }

    #[test]
    fn partition_at_split_point_rejects_split_at_first_cross_section() {
        let sections = five_point_corridor();
        let result = partition_at_split_point(&sections, CrossSectionId::from(1));
        assert_eq!(
            result,
            Err(SplitError::AlreadyEndpoint(CrossSectionId::from(1)))
        );
    }

    #[test]
    fn partition_at_split_point_rejects_split_at_last_cross_section() {
        let sections = five_point_corridor();
        let result = partition_at_split_point(&sections, CrossSectionId::from(5));
        assert_eq!(
            result,
            Err(SplitError::AlreadyEndpoint(CrossSectionId::from(5)))
        );
    }

    #[test]
    fn partition_at_split_point_rejects_unknown_cross_section() {
        let sections = five_point_corridor();
        let result = partition_at_split_point(&sections, CrossSectionId::from(999));
        assert_eq!(result, Err(SplitError::NotFound(CrossSectionId::from(999))));
    }

    #[test]
    fn partition_at_split_point_rejects_split_too_close_to_an_endpoint() {
        // Point 2 is only ~0.11m from point 1 (0.000001 degrees of latitude)
        // -- well under MIN_SPLIT_ENDPOINT_DISTANCE_METERS.
        let sections = vec![
            cs(1, 0.0, 45.500000, -73.600),
            cs(2, 1.0, 45.500001, -73.600),
            cs(3, 2.0, 45.502000, -73.600),
            cs(4, 3.0, 45.504000, -73.600),
        ];
        let result = partition_at_split_point(&sections, CrossSectionId::from(2));
        assert_eq!(
            result,
            Err(SplitError::TooCloseToEndpoint(CrossSectionId::from(2)))
        );
    }

    #[test]
    fn partition_at_split_point_carries_the_split_points_osm_node_id() {
        let mut sections = five_point_corridor();
        sections[2].osm_node_id = Some(555);
        let result = partition_at_split_point(&sections, CrossSectionId::from(3)).unwrap();
        assert_eq!(result.new_intersection_osm_node_id, Some(555));
    }
}

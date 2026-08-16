//! Pure heuristic for detecting dual-carriageway pairs at import time -- no
//! I/O. See `docs/superpowers/specs/2026-08-12-corridor-intersection-aggregate-design.md`,
//! "Dual-carriageway merge" and "Edge Cases".

use crate::ids::IntersectionId;

/// Maximum distance, in meters, between two intersections for them to be
/// considered a dual-carriageway merge candidate.
pub const MERGE_DISTANCE_METERS: f64 = 15.0;

/// One intersection's worth of data needed to evaluate it as a merge
/// candidate: its id, position, and the OSM way tags of the corridor that
/// most recently linked it (the way that caused this intersection to be
/// created or matched during the current import).
#[derive(Debug, Clone, PartialEq)]
pub struct IntersectionCandidate {
    pub id: IntersectionId,
    pub lat: f64,
    pub lon: f64,
    pub is_oneway: bool,
    pub name: Option<String>,
    pub reference: Option<String>,
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

fn tags_match(a: &IntersectionCandidate, b: &IntersectionCandidate) -> bool {
    match (&a.name, &b.name) {
        (Some(a_name), Some(b_name)) if !a_name.is_empty() && a_name == b_name => return true,
        _ => {}
    }
    match (&a.reference, &b.reference) {
        (Some(a_ref), Some(b_ref)) if !a_ref.is_empty() && a_ref == b_ref => return true,
        _ => {}
    }
    false
}

/// Evaluates `candidate` against every intersection in `others`, returning
/// the id of the first one it should merge into, or `None` if no candidate
/// qualifies. A pair qualifies when: both are `is_oneway`, they're within
/// `MERGE_DISTANCE_METERS` of each other, and they share a non-empty `name`
/// or `reference`. Deterministic on `others`' order -- callers should pass
/// `others` sorted by id ascending so the lower-id intersection always wins
/// as the survivor when multiple candidates qualify.
///
/// This function only knows about intersections, not the corridors that
/// reference them, so it CANNOT tell that two candidates are the two ends of
/// the same corridor -- a pair that must never merge, or that corridor collapses
/// into a degenerate self-loop. Excluding same-corridor candidates from `others`
/// is the caller's responsibility; `repository::run_dual_carriageway_merge_pass`
/// does it in its candidate query.
pub fn detect_dual_carriageway_merge(
    candidate: &IntersectionCandidate,
    others: &[IntersectionCandidate],
) -> Option<IntersectionId> {
    if !candidate.is_oneway {
        return None;
    }
    others
        .iter()
        .find(|other| {
            other.id != candidate.id
                && other.is_oneway
                && haversine_meters((candidate.lat, candidate.lon), (other.lat, other.lon))
                    <= MERGE_DISTANCE_METERS
                && tags_match(candidate, other)
        })
        .map(|other| other.id)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn candidate(
        id: i64,
        lat: f64,
        lon: f64,
        is_oneway: bool,
        name: Option<&str>,
    ) -> IntersectionCandidate {
        IntersectionCandidate {
            id: IntersectionId::from(id),
            lat,
            lon,
            is_oneway,
            name: name.map(str::to_string),
            reference: None,
        }
    }

    #[test]
    fn detects_merge_for_close_oneway_pair_with_matching_name() {
        let a = candidate(1, 45.5000, -73.6000, true, Some("Main St"));
        let b = candidate(2, 45.50005, -73.6000, true, Some("Main St")); // ~5.5m away
        assert_eq!(detect_dual_carriageway_merge(&a, &[b.clone()]), Some(b.id));
    }

    #[test]
    fn does_not_merge_when_matching_name_but_far_apart() {
        let a = candidate(1, 45.5000, -73.6000, true, Some("Main St"));
        // ~1.1km away (0.01 degrees latitude) -- far beyond MERGE_DISTANCE_METERS.
        let b = candidate(2, 45.5100, -73.6000, true, Some("Main St"));
        assert_eq!(detect_dual_carriageway_merge(&a, &[b]), None);
    }

    #[test]
    fn does_not_merge_when_close_but_not_both_oneway() {
        let a = candidate(1, 45.5000, -73.6000, true, Some("Main St"));
        let b = candidate(2, 45.50005, -73.6000, false, Some("Main St"));
        assert_eq!(detect_dual_carriageway_merge(&a, &[b]), None);
    }

    #[test]
    fn does_not_merge_when_close_and_oneway_but_names_mismatch() {
        let a = candidate(1, 45.5000, -73.6000, true, Some("Main St"));
        let b = candidate(2, 45.50005, -73.6000, true, Some("Elm St"));
        assert_eq!(detect_dual_carriageway_merge(&a, &[b]), None);
    }

    #[test]
    fn does_not_merge_when_both_names_are_empty_even_if_close_and_oneway() {
        let a = candidate(1, 45.5000, -73.6000, true, None);
        let b = candidate(2, 45.50005, -73.6000, true, None);
        assert_eq!(detect_dual_carriageway_merge(&a, &[b]), None);
    }

    #[test]
    fn matches_on_reference_tag_when_names_absent() {
        let mut a = candidate(1, 45.5000, -73.6000, true, None);
        a.reference = Some("Route 7".to_string());
        let mut b = candidate(2, 45.50005, -73.6000, true, None);
        b.reference = Some("Route 7".to_string());
        assert_eq!(detect_dual_carriageway_merge(&a, &[b.clone()]), Some(b.id));
    }

    #[test]
    fn three_close_oneway_candidates_with_matching_names_merge_pairwise() {
        // A cluster of 3, not just a pair: `detect_dual_carriageway_merge`
        // resolves ONE candidate at a time against the full remaining set,
        // so the caller (repository::find_dual_carriageway_merge_candidates,
        // Task 6 Step 8) is responsible for iterating until no further merge
        // is found -- this test pins that a single call against a 3-way
        // cluster returns the first (lowest-id, per `others`' documented
        // sort order) qualifying match, not an error and not all three at
        // once.
        let a = candidate(1, 45.5000, -73.6000, true, Some("Main St"));
        let b = candidate(2, 45.50003, -73.6000, true, Some("Main St"));
        let c = candidate(3, 45.50006, -73.6000, true, Some("Main St"));
        assert_eq!(
            detect_dual_carriageway_merge(&c, &[a.clone(), b.clone()]),
            Some(a.id)
        );
    }
}

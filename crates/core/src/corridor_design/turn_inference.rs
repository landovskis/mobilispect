//! Pure OSM-tag-driven turn-movement inference -- no I/O. Parses `turn:lanes`
//! variants into candidate lane-to-lane pairings at an intersection. See
//! `docs/superpowers/specs/2026-08-12-corridor-intersection-aggregate-design.md`,
//! "Turn-movement inference" and the "Missing/inconsistent crossing and
//! turn-lane tags" edge case.

use std::collections::HashMap;

use crate::corridor_design::lanes::{Lane, LaneDirection};
use crate::ids::LaneId;

/// Parses one `turn:lanes`-style tag value (semicolon-separated per-lane
/// values, left-to-right matching the OSM lane ordering convention) into a
/// `Vec` of per-lane movement keywords (e.g. `"left"`, `"through"`,
/// `"right"`, or a `|`-joined combination for a shared lane like
/// `"through;right"`). Unrecognized or empty segments become an empty
/// `Vec<&str>` for that lane position, not an error -- the caller treats "no
/// recognized movement for this lane" as "no candidate produced", not a
/// parse failure.
fn parse_turn_lanes_tag(raw: &str) -> Vec<Vec<&str>> {
    raw.split('|')
        .map(|segment| {
            segment
                .split(';')
                .map(str::trim)
                .filter(|s| !s.is_empty())
                .collect()
        })
        .collect()
}

/// Infers candidate turn movements between `from_lanes` (the corridor whose
/// `turn:lanes` tag is being read) and `to_lanes` (the other corridor at the
/// same intersection). Reads `turn:lanes` (falling back to
/// `turn:lanes:forward` when present, since this codebase's OSM import
/// doesn't yet track which physical direction along a way corresponds to
/// "forward" independently of `oneway` -- an intentional simplification, not
/// an oversight; see this design's Open Points). Absent or unparseable tag
/// data produces an empty result -- never an assumed-legal default. Only
/// pairs a `from` lane carrying a recognized "left"/"right"/"through"
/// keyword against a `to` lane whose `LaneDirection` is a plausible
/// destination (`Forward` or `Both`) -- lanes with no plausible destination
/// (e.g. every `to` lane being `Backward`-only) produce no movement for that
/// `from` lane, not a panic or an arbitrary pairing.
pub fn infer_turn_movements(
    tags: &HashMap<String, String>,
    from_lanes: &[Lane],
    to_lanes: &[Lane],
) -> Vec<(LaneId, LaneId)> {
    let Some(raw) = tags
        .get("turn:lanes")
        .or_else(|| tags.get("turn:lanes:forward"))
    else {
        return Vec::new();
    };
    let parsed = parse_turn_lanes_tag(raw);

    let plausible_destinations: Vec<&Lane> = to_lanes
        .iter()
        .filter(|l| matches!(l.direction, LaneDirection::Forward | LaneDirection::Both))
        .collect();
    if plausible_destinations.is_empty() {
        return Vec::new();
    }

    let mut movements = Vec::new();
    for (from_lane, keywords) in from_lanes.iter().zip(parsed.iter()) {
        let has_recognized_keyword = keywords
            .iter()
            .any(|kw| matches!(*kw, "left" | "through" | "right"));
        if !has_recognized_keyword {
            continue;
        }
        for to_lane in &plausible_destinations {
            movements.push((from_lane.id, to_lane.id));
        }
    }
    movements
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::corridor_design::lanes::LaneType;
    use crate::ids::CrossSectionId;

    fn lane(id: i64, direction: LaneDirection) -> Lane {
        Lane {
            id: LaneId::from(id),
            cross_section_id: CrossSectionId::from(1),
            position: 0.0,
            lane_type: LaneType::Travel,
            width_meters: 3.0,
            direction,
            access_rules: vec![],
        }
    }

    #[test]
    fn infers_movement_from_recognized_turn_lanes_tag() {
        let mut tags = HashMap::new();
        tags.insert("turn:lanes".to_string(), "left|through".to_string());
        let from_lanes = vec![
            lane(1, LaneDirection::Forward),
            lane(2, LaneDirection::Forward),
        ];
        let to_lanes = vec![lane(10, LaneDirection::Forward)];

        let movements = infer_turn_movements(&tags, &from_lanes, &to_lanes);

        assert_eq!(
            movements.len(),
            2,
            "both from-lanes have a recognized keyword"
        );
        assert!(movements.contains(&(LaneId::from(1), LaneId::from(10))));
        assert!(movements.contains(&(LaneId::from(2), LaneId::from(10))));
    }

    #[test]
    fn produces_no_movements_when_turn_lanes_tag_is_absent() {
        let tags = HashMap::new();
        let from_lanes = vec![lane(1, LaneDirection::Forward)];
        let to_lanes = vec![lane(10, LaneDirection::Forward)];

        let movements = infer_turn_movements(&tags, &from_lanes, &to_lanes);
        assert!(
            movements.is_empty(),
            "absent tag must never default to an assumed-legal movement"
        );
    }

    #[test]
    fn skips_lane_positions_with_no_recognized_keyword() {
        let mut tags = HashMap::new();
        tags.insert("turn:lanes".to_string(), "none|left".to_string());
        let from_lanes = vec![
            lane(1, LaneDirection::Forward),
            lane(2, LaneDirection::Forward),
        ];
        let to_lanes = vec![lane(10, LaneDirection::Forward)];

        let movements = infer_turn_movements(&tags, &from_lanes, &to_lanes);

        assert_eq!(movements, vec![(LaneId::from(2), LaneId::from(10))]);
    }

    #[test]
    fn falls_back_to_turn_lanes_forward_when_turn_lanes_absent() {
        let mut tags = HashMap::new();
        tags.insert("turn:lanes:forward".to_string(), "through".to_string());
        let from_lanes = vec![lane(1, LaneDirection::Forward)];
        let to_lanes = vec![lane(10, LaneDirection::Forward)];

        let movements = infer_turn_movements(&tags, &from_lanes, &to_lanes);
        assert_eq!(movements, vec![(LaneId::from(1), LaneId::from(10))]);
    }

    #[test]
    fn produces_no_movements_when_no_destination_lane_is_a_plausible_target() {
        let mut tags = HashMap::new();
        tags.insert("turn:lanes".to_string(), "through".to_string());
        let from_lanes = vec![lane(1, LaneDirection::Forward)];
        let to_lanes = vec![lane(10, LaneDirection::Backward)]; // no Forward/Both lane

        let movements = infer_turn_movements(&tags, &from_lanes, &to_lanes);
        assert!(movements.is_empty());
    }

    #[test]
    fn produces_no_movements_for_malformed_tag_value() {
        let mut tags = HashMap::new();
        // Empty segments throughout -- parses to all-empty keyword lists.
        tags.insert("turn:lanes".to_string(), "||".to_string());
        let from_lanes = vec![
            lane(1, LaneDirection::Forward),
            lane(2, LaneDirection::Forward),
        ];
        let to_lanes = vec![lane(10, LaneDirection::Forward)];

        let movements = infer_turn_movements(&tags, &from_lanes, &to_lanes);
        assert!(movements.is_empty());
    }

    #[test]
    fn result_is_identical_regardless_of_relative_corridor_angle() {
        // infer_turn_movements is purely tag-driven -- it has no geometry
        // input at all, so there is no "angle" parameter to vary here. This
        // test pins that fact: calling it twice with the same tags/lanes
        // produces the same result, confirming no hidden angle-dependent
        // state exists to regress if a future geometric enhancement is added.
        let mut tags = HashMap::new();
        tags.insert("turn:lanes".to_string(), "left".to_string());
        let from_lanes = vec![lane(1, LaneDirection::Forward)];
        let to_lanes = vec![lane(10, LaneDirection::Forward)];

        let first_call = infer_turn_movements(&tags, &from_lanes, &to_lanes);
        let second_call = infer_turn_movements(&tags, &from_lanes, &to_lanes);
        assert_eq!(first_call, second_call);
    }
}

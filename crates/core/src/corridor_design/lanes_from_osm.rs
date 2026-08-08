//! Derives a baseline lane arrangement from an OSM way's tags, for corridor
//! import (REQ-001). Pure — no I/O. See
//! `docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md`.
//!
//! This is a deliberately simple approximation of OSM's real-world lane
//! tagging (which is notoriously inconsistent): it collapses `left`/`right`
//! variants of `cycleway`/`parking` into a single symmetric pair (one lane on
//! each side of the travel lanes) rather than modeling true per-side
//! placement, and a bare `cycleway=*`/`sidewalk=*` presence tag (without a
//! `:left`/`:right` suffix) is treated the same as "both sides present."
//! This is a starting point the analyst edits from, not an authoritative
//! reconstruction — refining it is future work if real usage shows gaps.

use std::collections::HashMap;

use crate::corridor_design::lanes::{
    LaneDirection, LaneDraft, LaneType, default_access_rule_for, default_width_meters_for,
};

/// Derives a left-to-right `Vec<LaneDraft>` from an OSM way's tags. Total —
/// never fails. Falls back to a single bidirectional `Travel` lane when none
/// of the recognized tags (`lanes`, `lanes:forward`, `lanes:backward`,
/// `cycleway`/`cycleway:left`/`cycleway:right`, `sidewalk`,
/// `parking:lane:both`/`:left`/`:right`) are present — the safest baseline
/// when OSM data is sparse.
pub fn derive_lanes_from_osm_tags(tags: &HashMap<String, String>) -> Vec<LaneDraft> {
    let has_lane_count_tag = tags.contains_key("lanes")
        || tags.contains_key("lanes:forward")
        || tags.contains_key("lanes:backward");
    let has_cycleway = ["cycleway", "cycleway:left", "cycleway:right"]
        .iter()
        .any(|k| tags.get(*k).is_some_and(|v| v != "no"));
    let has_parking = [
        "parking:lane:both",
        "parking:lane:left",
        "parking:lane:right",
    ]
    .iter()
    .any(|k| tags.get(*k).is_some_and(|v| v != "no"));
    let has_sidewalk = tags
        .get("sidewalk")
        .is_some_and(|v| v != "none" && v != "no");

    if !has_lane_count_tag && !has_cycleway && !has_parking && !has_sidewalk {
        return vec![lane_draft(LaneType::Travel, LaneDirection::Both)];
    }

    let oneway = tags.get("oneway").is_some_and(|v| v == "yes");
    let (forward_count, backward_count) = travel_lane_counts(tags, oneway);

    let mut lanes = Vec::new();
    if has_sidewalk {
        lanes.push(lane_draft(LaneType::Sidewalk, LaneDirection::None));
    }
    if has_parking {
        lanes.push(lane_draft(LaneType::Parking, LaneDirection::None));
    }
    if has_cycleway {
        lanes.push(lane_draft(LaneType::CycleLane, LaneDirection::Both));
    }
    for _ in 0..backward_count {
        lanes.push(lane_draft(LaneType::Travel, LaneDirection::Backward));
    }
    for _ in 0..forward_count {
        lanes.push(lane_draft(LaneType::Travel, LaneDirection::Forward));
    }
    if has_cycleway {
        lanes.push(lane_draft(LaneType::CycleLane, LaneDirection::Both));
    }
    if has_parking {
        lanes.push(lane_draft(LaneType::Parking, LaneDirection::None));
    }
    if has_sidewalk {
        lanes.push(lane_draft(LaneType::Sidewalk, LaneDirection::None));
    }

    if lanes.is_empty() {
        // e.g. `lanes=0` (rare/malformed) with no other relevant tags.
        return vec![lane_draft(LaneType::Travel, LaneDirection::Both)];
    }

    lanes
}

/// Resolves the forward/backward travel-lane counts: an explicit
/// `lanes:forward`/`lanes:backward` pair wins outright; otherwise `lanes`
/// (or a default of 1 for a oneway street / 2 otherwise) is split as evenly
/// as possible, with the forward direction getting the extra lane on an odd
/// total.
fn travel_lane_counts(tags: &HashMap<String, String>, oneway: bool) -> (u32, u32) {
    let forward_backward = (
        tags.get("lanes:forward")
            .and_then(|v| v.parse::<u32>().ok()),
        tags.get("lanes:backward")
            .and_then(|v| v.parse::<u32>().ok()),
    );
    if let (Some(forward), Some(backward)) = forward_backward {
        return (forward, backward);
    }

    let total = tags
        .get("lanes")
        .and_then(|v| v.parse::<u32>().ok())
        .unwrap_or(if oneway { 1 } else { 2 });
    if oneway {
        (total, 0)
    } else {
        let forward = total.div_ceil(2);
        (forward, total.saturating_sub(forward))
    }
}

fn lane_draft(lane_type: LaneType, direction: LaneDirection) -> LaneDraft {
    LaneDraft {
        lane_type,
        width_meters: default_width_meters_for(lane_type),
        direction,
        access_rules: vec![default_access_rule_for(lane_type)],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tags(pairs: &[(&str, &str)]) -> HashMap<String, String> {
        pairs
            .iter()
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect()
    }

    #[test]
    fn no_relevant_tags_falls_back_to_single_bidirectional_travel_lane() {
        let lanes = derive_lanes_from_osm_tags(&HashMap::new());
        assert_eq!(lanes.len(), 1);
        assert_eq!(lanes[0].lane_type, LaneType::Travel);
        assert_eq!(lanes[0].direction, LaneDirection::Both);
    }

    #[test]
    fn irrelevant_tags_only_falls_back_to_single_bidirectional_travel_lane() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("surface", "asphalt")]));
        assert_eq!(lanes.len(), 1);
        assert_eq!(lanes[0].lane_type, LaneType::Travel);
        assert_eq!(lanes[0].direction, LaneDirection::Both);
    }

    #[test]
    fn lanes_four_not_oneway_splits_evenly() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("lanes", "4")]));
        let directions: Vec<LaneDirection> = lanes.iter().map(|l| l.direction).collect();
        assert_eq!(
            directions,
            vec![
                LaneDirection::Backward,
                LaneDirection::Backward,
                LaneDirection::Forward,
                LaneDirection::Forward,
            ]
        );
    }

    #[test]
    fn lanes_three_not_oneway_gives_forward_the_extra_lane() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("lanes", "3")]));
        let directions: Vec<LaneDirection> = lanes.iter().map(|l| l.direction).collect();
        assert_eq!(
            directions,
            vec![
                LaneDirection::Backward,
                LaneDirection::Forward,
                LaneDirection::Forward,
            ]
        );
    }

    #[test]
    fn oneway_yes_with_lanes_two_gives_two_forward_lanes_no_backward() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("lanes", "2"), ("oneway", "yes")]));
        let directions: Vec<LaneDirection> = lanes.iter().map(|l| l.direction).collect();
        assert_eq!(
            directions,
            vec![LaneDirection::Forward, LaneDirection::Forward]
        );
    }

    #[test]
    fn explicit_lanes_forward_and_backward_overrides_the_even_split() {
        let lanes =
            derive_lanes_from_osm_tags(&tags(&[("lanes:forward", "2"), ("lanes:backward", "1")]));
        let directions: Vec<LaneDirection> = lanes.iter().map(|l| l.direction).collect();
        assert_eq!(
            directions,
            vec![
                LaneDirection::Backward,
                LaneDirection::Forward,
                LaneDirection::Forward,
            ]
        );
    }

    #[test]
    fn cycleway_present_adds_cycle_lanes_on_both_sides_of_travel_lanes() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("cycleway", "lane")]));
        let types: Vec<LaneType> = lanes.iter().map(|l| l.lane_type).collect();
        assert_eq!(
            types,
            vec![
                LaneType::CycleLane,
                LaneType::Travel,
                LaneType::Travel,
                LaneType::CycleLane,
            ]
        );
    }

    #[test]
    fn sidewalk_none_is_treated_as_absent() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("sidewalk", "none"), ("lanes", "2")]));
        assert!(!lanes.iter().any(|l| l.lane_type == LaneType::Sidewalk));
    }

    #[test]
    fn sidewalk_both_adds_sidewalk_lanes_on_both_ends() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("sidewalk", "both"), ("lanes", "2")]));
        assert_eq!(lanes.first().unwrap().lane_type, LaneType::Sidewalk);
        assert_eq!(lanes.last().unwrap().lane_type, LaneType::Sidewalk);
    }

    #[test]
    fn parking_lane_right_adds_parking_lanes() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[
            ("parking:lane:right", "parallel"),
            ("lanes", "2"),
        ]));
        assert!(lanes.iter().any(|l| l.lane_type == LaneType::Parking));
    }

    #[test]
    fn parking_lane_no_is_treated_as_absent() {
        let lanes =
            derive_lanes_from_osm_tags(&tags(&[("parking:lane:both", "no"), ("lanes", "2")]));
        assert!(!lanes.iter().any(|l| l.lane_type == LaneType::Parking));
    }

    #[test]
    fn every_derived_lane_gets_the_default_width_and_access_rule_for_its_type() {
        let lanes = derive_lanes_from_osm_tags(&tags(&[("lanes", "2")]));
        for lane in &lanes {
            assert_eq!(lane.width_meters, default_width_meters_for(lane.lane_type));
            assert_eq!(
                lane.access_rules,
                vec![default_access_rule_for(lane.lane_type)]
            );
        }
    }
}

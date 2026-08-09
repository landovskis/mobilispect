//! Lane domain types: a cross-section is an ordered, left-to-right arrangement of
//! lanes, each with a type, width, direction, and access policy. See
//! `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`.

use crate::ids::{CrossSectionId, LaneId};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LaneType {
    Travel,
    Turn,
    Transit,
    QueueJump,
    CycleLane,
    CycleTrack,
    Parking,
    Sidewalk,
    Median,
    Buffer,
}

impl LaneType {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            LaneType::Travel => "travel",
            LaneType::Turn => "turn",
            LaneType::Transit => "transit",
            LaneType::QueueJump => "queue_jump",
            LaneType::CycleLane => "cycle_lane",
            LaneType::CycleTrack => "cycle_track",
            LaneType::Parking => "parking",
            LaneType::Sidewalk => "sidewalk",
            LaneType::Median => "median",
            LaneType::Buffer => "buffer",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "travel" => Some(LaneType::Travel),
            "turn" => Some(LaneType::Turn),
            "transit" => Some(LaneType::Transit),
            "queue_jump" => Some(LaneType::QueueJump),
            "cycle_lane" => Some(LaneType::CycleLane),
            "cycle_track" => Some(LaneType::CycleTrack),
            "parking" => Some(LaneType::Parking),
            "sidewalk" => Some(LaneType::Sidewalk),
            "median" => Some(LaneType::Median),
            "buffer" => Some(LaneType::Buffer),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LaneDirection {
    Forward,
    Backward,
    Both,
    None,
}

impl LaneDirection {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            LaneDirection::Forward => "forward",
            LaneDirection::Backward => "backward",
            LaneDirection::Both => "both",
            LaneDirection::None => "none",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "forward" => Some(LaneDirection::Forward),
            "backward" => Some(LaneDirection::Backward),
            "both" => Some(LaneDirection::Both),
            "none" => Some(LaneDirection::None),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AccessMode {
    Car,
    Transit,
    Bicycle,
    Pedestrian,
    Emergency,
    Taxi,
    Freight,
    Hov,
}

impl AccessMode {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            AccessMode::Car => "car",
            AccessMode::Transit => "transit",
            AccessMode::Bicycle => "bicycle",
            AccessMode::Pedestrian => "pedestrian",
            AccessMode::Emergency => "emergency",
            AccessMode::Taxi => "taxi",
            AccessMode::Freight => "freight",
            AccessMode::Hov => "hov",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "car" => Some(AccessMode::Car),
            "transit" => Some(AccessMode::Transit),
            "bicycle" => Some(AccessMode::Bicycle),
            "pedestrian" => Some(AccessMode::Pedestrian),
            "emergency" => Some(AccessMode::Emergency),
            "taxi" => Some(AccessMode::Taxi),
            "freight" => Some(AccessMode::Freight),
            "hov" => Some(AccessMode::Hov),
            _ => None,
        }
    }
}

/// `None` means "always active" (the default). A concrete `TimeWindow` narrows
/// the rule to specific days/hours (e.g. a part-time bus lane).
#[derive(Debug, Clone, PartialEq)]
pub struct TimeWindow {
    pub days: String,
    pub start_time: chrono::NaiveTime,
    pub end_time: chrono::NaiveTime,
}

#[derive(Debug, Clone, PartialEq)]
pub struct TimedAccessRule {
    pub time_window: Option<TimeWindow>,
    pub allowed_modes: Vec<AccessMode>,
}

/// A lane before it has been persisted — no `id`/`cross_section_id` yet.
#[derive(Debug, Clone, PartialEq)]
pub struct LaneDraft {
    pub lane_type: LaneType,
    pub width_meters: f64,
    pub direction: LaneDirection,
    pub access_rules: Vec<TimedAccessRule>,
}

/// A persisted lane, as returned from the repository.
#[derive(Debug, Clone, PartialEq)]
pub struct Lane {
    pub id: LaneId,
    pub cross_section_id: CrossSectionId,
    pub position: f64,
    pub lane_type: LaneType,
    pub width_meters: f64,
    pub direction: LaneDirection,
    pub access_rules: Vec<TimedAccessRule>,
}

/// The always-on access rule an analyst gets by default for a given lane type,
/// before overriding it with a time-windowed rule for a special treatment (a BAT
/// lane, a part-time bus lane, etc.).
pub fn default_access_rule_for(lane_type: LaneType) -> TimedAccessRule {
    let allowed_modes = match lane_type {
        LaneType::Travel | LaneType::Turn => vec![AccessMode::Car, AccessMode::Emergency],
        LaneType::Transit | LaneType::QueueJump => vec![AccessMode::Transit, AccessMode::Emergency],
        LaneType::CycleLane | LaneType::CycleTrack => vec![AccessMode::Bicycle],
        LaneType::Parking => vec![AccessMode::Car],
        LaneType::Sidewalk => vec![AccessMode::Pedestrian],
        LaneType::Median | LaneType::Buffer => vec![],
    };
    TimedAccessRule {
        time_window: None,
        allowed_modes,
    }
}

/// The default width (in meters) for a lane type, used when no explicit width is
/// supplied (e.g. no `width:lanes=*` OSM tag on import — the common case).
pub fn default_width_meters_for(lane_type: LaneType) -> f64 {
    match lane_type {
        LaneType::Travel | LaneType::Turn => 3.0,
        LaneType::Transit | LaneType::QueueJump => 3.2,
        LaneType::CycleLane => 1.5,
        LaneType::CycleTrack => 2.0,
        LaneType::Parking => 2.0,
        LaneType::Sidewalk => 1.8,
        LaneType::Median => 1.2,
        LaneType::Buffer => 0.6,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lane_type_db_str_round_trips_all_variants() {
        for lane_type in [
            LaneType::Travel,
            LaneType::Turn,
            LaneType::Transit,
            LaneType::QueueJump,
            LaneType::CycleLane,
            LaneType::CycleTrack,
            LaneType::Parking,
            LaneType::Sidewalk,
            LaneType::Median,
            LaneType::Buffer,
        ] {
            let s = lane_type.as_db_str();
            assert_eq!(LaneType::from_db_str(s), Some(lane_type));
        }
    }

    #[test]
    fn lane_type_from_db_str_rejects_unknown_value() {
        assert_eq!(LaneType::from_db_str("bogus"), None);
    }

    #[test]
    fn lane_direction_db_str_round_trips_all_variants() {
        for direction in [
            LaneDirection::Forward,
            LaneDirection::Backward,
            LaneDirection::Both,
            LaneDirection::None,
        ] {
            let s = direction.as_db_str();
            assert_eq!(LaneDirection::from_db_str(s), Some(direction));
        }
    }

    #[test]
    fn access_mode_db_str_round_trips_all_variants() {
        for mode in [
            AccessMode::Car,
            AccessMode::Transit,
            AccessMode::Bicycle,
            AccessMode::Pedestrian,
            AccessMode::Emergency,
            AccessMode::Taxi,
            AccessMode::Freight,
            AccessMode::Hov,
        ] {
            let s = mode.as_db_str();
            assert_eq!(AccessMode::from_db_str(s), Some(mode));
        }
    }

    #[test]
    fn default_access_rule_for_travel_is_car_and_emergency_always_on() {
        let rule = default_access_rule_for(LaneType::Travel);
        assert_eq!(rule.time_window, None);
        assert_eq!(
            rule.allowed_modes,
            vec![AccessMode::Car, AccessMode::Emergency]
        );
    }

    #[test]
    fn default_access_rule_for_cycle_lane_is_bicycle_only() {
        let rule = default_access_rule_for(LaneType::CycleLane);
        assert_eq!(rule.allowed_modes, vec![AccessMode::Bicycle]);
    }

    #[test]
    fn default_access_rule_for_median_allows_no_modes() {
        let rule = default_access_rule_for(LaneType::Median);
        assert_eq!(rule.allowed_modes, Vec::<AccessMode>::new());
    }

    #[test]
    fn default_width_meters_matches_the_approved_mockup_values() {
        assert_eq!(default_width_meters_for(LaneType::Travel), 3.0);
        assert_eq!(default_width_meters_for(LaneType::CycleLane), 1.5);
        assert_eq!(default_width_meters_for(LaneType::Parking), 2.0);
        assert_eq!(default_width_meters_for(LaneType::Sidewalk), 1.8);
    }
}

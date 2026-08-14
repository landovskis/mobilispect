//! Intersection domain types: a shared point where one or more corridors
//! meet, holding an optional bus-gate/turn-conflict/bus-stop treatment and a
//! set of legal turn movements between the lanes of corridors meeting there.
//! See `docs/superpowers/specs/2026-08-12-corridor-intersection-aggregate-design.md`.

use crate::ids::{IntersectionId, LaneId};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BusGate {
    SignalControlled,
    YieldControlled,
}

impl BusGate {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            BusGate::SignalControlled => "signal_controlled",
            BusGate::YieldControlled => "yield_controlled",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "signal_controlled" => Some(BusGate::SignalControlled),
            "yield_controlled" => Some(BusGate::YieldControlled),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TurnConflict {
    IndirectLeftViaAlternative,
    IndirectLeftWithinIntersection,
    RightInRightOut,
    DeadEndLateralStreet,
}

impl TurnConflict {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            TurnConflict::IndirectLeftViaAlternative => "indirect_left_via_alternative",
            TurnConflict::IndirectLeftWithinIntersection => "indirect_left_within_intersection",
            TurnConflict::RightInRightOut => "right_in_right_out",
            TurnConflict::DeadEndLateralStreet => "dead_end_lateral_street",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "indirect_left_via_alternative" => Some(TurnConflict::IndirectLeftViaAlternative),
            "indirect_left_within_intersection" => {
                Some(TurnConflict::IndirectLeftWithinIntersection)
            }
            "right_in_right_out" => Some(TurnConflict::RightInRightOut),
            "dead_end_lateral_street" => Some(TurnConflict::DeadEndLateralStreet),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BusStop {
    BusBulb,
    SignalProtectedPlatform,
}

impl BusStop {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            BusStop::BusBulb => "bus_bulb",
            BusStop::SignalProtectedPlatform => "signal_protected_platform",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "bus_bulb" => Some(BusStop::BusBulb),
            "signal_protected_platform" => Some(BusStop::SignalProtectedPlatform),
            _ => None,
        }
    }
}

/// A persisted intersection treatment, as returned from the repository. Every
/// field but `cross_section_id` is optional -- both are independently
/// clearable via the intersection editor's "None" option, and a
/// cross-section with no treatment configured yet has no row at all (see
/// `repository::get_intersection_treatment`, which synthesizes an all-`None`
/// value in that case rather than requiring a row to exist first).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct IntersectionTreatment {
    pub cross_section_id: crate::ids::CrossSectionId,
    pub bus_gate: Option<BusGate>,
    pub turn_conflict: Option<TurnConflict>,
}

/// A persisted intersection, as returned from the repository. `osm_node_ids`
/// is usually one entry; more than one after a dual-carriageway merge folds a
/// second node's intersection into this one (see `dual_carriageway.rs`).
/// Empty for a manually-traced corridor's private intersection.
#[derive(Debug, Clone, PartialEq)]
pub struct Intersection {
    pub id: IntersectionId,
    pub lat: f64,
    pub lon: f64,
    pub osm_node_ids: Vec<i64>,
    pub bus_gate: Option<BusGate>,
    pub turn_conflict: Option<TurnConflict>,
    pub bus_stop: Option<BusStop>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TurnMovementSource {
    Inferred,
    Manual,
}

impl TurnMovementSource {
    pub const fn as_db_str(self) -> &'static str {
        match self {
            TurnMovementSource::Inferred => "inferred",
            TurnMovementSource::Manual => "manual",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "inferred" => Some(TurnMovementSource::Inferred),
            "manual" => Some(TurnMovementSource::Manual),
            _ => None,
        }
    }
}

/// A legal source-lane -> destination-lane pairing at an `Intersection`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct TurnMovement {
    pub intersection_id: IntersectionId,
    pub from_lane_id: LaneId,
    pub to_lane_id: LaneId,
    pub source: TurnMovementSource,
}

/// An audit record of an automatic dual-carriageway merge (see
/// `dual_carriageway.rs`). No `absorbed_intersection_id` -- that row is
/// deleted as part of the same transaction that inserts this log entry, so
/// its id would immediately dangle; `absorbed_osm_node_ids` is what survives.
#[derive(Debug, Clone, PartialEq)]
pub struct IntersectionMerge {
    pub surviving_intersection_id: IntersectionId,
    pub absorbed_osm_node_ids: Vec<i64>,
    pub treatment_conflict: bool,
    pub merged_at: chrono::DateTime<chrono::Utc>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bus_gate_db_str_round_trips_all_variants() {
        for gate in [BusGate::SignalControlled, BusGate::YieldControlled] {
            assert_eq!(BusGate::from_db_str(gate.as_db_str()), Some(gate));
        }
    }

    #[test]
    fn bus_gate_from_db_str_rejects_unknown_value() {
        assert_eq!(BusGate::from_db_str("bogus"), None);
    }

    #[test]
    fn turn_conflict_db_str_round_trips_all_variants() {
        for conflict in [
            TurnConflict::IndirectLeftViaAlternative,
            TurnConflict::IndirectLeftWithinIntersection,
            TurnConflict::RightInRightOut,
            TurnConflict::DeadEndLateralStreet,
        ] {
            assert_eq!(
                TurnConflict::from_db_str(conflict.as_db_str()),
                Some(conflict)
            );
        }
    }

    #[test]
    fn turn_conflict_from_db_str_rejects_unknown_value() {
        assert_eq!(TurnConflict::from_db_str("bogus"), None);
    }

    #[test]
    fn bus_stop_db_str_round_trips_all_variants() {
        for stop in [BusStop::BusBulb, BusStop::SignalProtectedPlatform] {
            assert_eq!(BusStop::from_db_str(stop.as_db_str()), Some(stop));
        }
    }

    #[test]
    fn bus_stop_from_db_str_rejects_unknown_value() {
        assert_eq!(BusStop::from_db_str("bogus"), None);
    }

    #[test]
    fn turn_movement_source_db_str_round_trips_all_variants() {
        for source in [TurnMovementSource::Inferred, TurnMovementSource::Manual] {
            assert_eq!(
                TurnMovementSource::from_db_str(source.as_db_str()),
                Some(source)
            );
        }
    }

    #[test]
    fn turn_movement_source_from_db_str_rejects_unknown_value() {
        assert_eq!(TurnMovementSource::from_db_str("bogus"), None);
    }

    #[test]
    fn intersection_carries_all_fields() {
        use crate::ids::IntersectionId;
        let intersection = Intersection {
            id: IntersectionId::from(1),
            lat: 45.5,
            lon: -73.6,
            osm_node_ids: vec![10, 11],
            bus_gate: Some(BusGate::SignalControlled),
            turn_conflict: None,
            bus_stop: None,
        };
        assert_eq!(intersection.osm_node_ids, vec![10, 11]);
        assert_eq!(intersection.bus_gate, Some(BusGate::SignalControlled));
    }

    #[test]
    fn turn_movement_carries_lane_pair_and_source() {
        use crate::ids::{IntersectionId, LaneId};
        let movement = TurnMovement {
            intersection_id: IntersectionId::from(1),
            from_lane_id: LaneId::from(2),
            to_lane_id: LaneId::from(3),
            source: TurnMovementSource::Manual,
        };
        assert_eq!(movement.source, TurnMovementSource::Manual);
    }

    #[test]
    fn intersection_merge_carries_absorbed_node_ids_and_conflict_flag() {
        use crate::ids::IntersectionId;
        let merge = IntersectionMerge {
            surviving_intersection_id: IntersectionId::from(1),
            absorbed_osm_node_ids: vec![99],
            treatment_conflict: true,
            merged_at: chrono::Utc::now(),
        };
        assert_eq!(merge.absorbed_osm_node_ids, vec![99]);
        assert!(merge.treatment_conflict);
    }

    #[test]
    fn intersection_treatment_carries_cross_section_id_and_both_optional_fields() {
        use crate::ids::CrossSectionId;
        let treatment = IntersectionTreatment {
            cross_section_id: CrossSectionId::from(42),
            bus_gate: Some(BusGate::SignalControlled),
            turn_conflict: None,
        };
        assert_eq!(treatment.cross_section_id, CrossSectionId::from(42));
        assert_eq!(treatment.bus_gate, Some(BusGate::SignalControlled));
        assert_eq!(treatment.turn_conflict, None);
    }
}

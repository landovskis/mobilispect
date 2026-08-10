//! Intersection treatment domain types: an endpoint cross-section may carry an
//! optional bus-gate control and turn-conflict classification; any
//! cross-section may carry an optional bus-stop platform type. See
//! `docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md`'s
//! "Intersection Treatments" section.

use crate::ids::CrossSectionId;

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
    pub cross_section_id: CrossSectionId,
    pub bus_gate: Option<BusGate>,
    pub turn_conflict: Option<TurnConflict>,
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
    fn intersection_treatment_carries_cross_section_id_and_both_optional_fields() {
        let treatment = IntersectionTreatment {
            cross_section_id: crate::ids::CrossSectionId::from(42),
            bus_gate: Some(BusGate::SignalControlled),
            turn_conflict: None,
        };
        assert_eq!(
            treatment.cross_section_id,
            crate::ids::CrossSectionId::from(42)
        );
        assert_eq!(treatment.bus_gate, Some(BusGate::SignalControlled));
        assert_eq!(treatment.turn_conflict, None);
    }
}

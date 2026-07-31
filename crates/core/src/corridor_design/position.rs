//! Pure fractional-key position assignment for inserting a cross-section anywhere
//! in a corridor's ordered sequence — no I/O.
//!
//! Covers REQ-004 (add cross-sections) and is reused, unmodified, by REQ-005
//! (reorder). See the Corridor Segment Editor SDD, REQ-004 "Design Approach", for
//! why `position` is a fractional ordering key (arithmetic midpoint between
//! neighbors) rather than a dense, renumbered-on-every-insert integer sequence.

// NOTE: using f64 for fractional position; BigDecimal would need the sqlx
// "bigdecimal" feature enabled — flag for Loop B. `crates/core/Cargo.toml`'s sqlx
// dependency currently enables only ["runtime-tokio", "postgres", "chrono",
// "migrate"], so `sqlx::types::BigDecimal` (as the SDD's interface signatures show)
// is not available without a dependency-feature change, which is outside this
// task's scope.

/// The two cross-sections a new one is being inserted between, by their current
/// `position` values. `None` on either side means the insertion is at a sequence
/// boundary (start or end) rather than strictly between two existing rows.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Neighbors {
    pub before: Option<f64>,
    pub after: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PositionAssignmentError {
    /// No valid position exists between the given neighbors.
    UnresolvableInterval,
    /// Neighbor positions are not correctly ordered (equal, or `before >= after`).
    NonMonotonicNeighbors,
}

impl std::fmt::Display for PositionAssignmentError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PositionAssignmentError::UnresolvableInterval => {
                write!(f, "no valid position exists between the given neighbors")
            }
            PositionAssignmentError::NonMonotonicNeighbors => {
                write!(f, "neighbor positions are not correctly ordered")
            }
        }
    }
}

impl std::error::Error for PositionAssignmentError {}

/// Computes a new fractional `position` for a cross-section being inserted between
/// `neighbors.before` and `neighbors.after`.
///
/// Pure — no I/O. `before = None` means "insert at the start of the sequence";
/// `after = None` means "insert at the end"; both `None` means the corridor has no
/// existing cross-sections yet.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-004-04 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub fn assign_position(neighbors: Neighbors) -> Result<f64, PositionAssignmentError> {
    let _ = neighbors;
    unimplemented!("IMP-REQ-004-04: assign_position not yet implemented")
}

#[cfg(test)]
mod tests {
    use super::*;

    /// SDD REQ-004 unit test 1: both neighbors present and well-ordered returns
    /// their arithmetic midpoint.
    #[test]
    fn assign_position_returns_midpoint_for_well_ordered_neighbors() {
        let neighbors = Neighbors {
            before: Some(1.0),
            after: Some(2.0),
        };
        let result = assign_position(neighbors).expect("well-ordered neighbors should succeed");
        assert_eq!(result, 1.5);
    }

    /// SDD REQ-004 unit test 2: `before = None`, `after = Some(x)` (insert at start
    /// of sequence) returns a value strictly less than `x`.
    #[test]
    fn assign_position_returns_value_less_than_after_when_before_is_none() {
        let neighbors = Neighbors {
            before: None,
            after: Some(10.0),
        };
        let result = assign_position(neighbors).expect("insert-at-start should succeed");
        assert!(result < 10.0);
    }

    /// SDD REQ-004 unit test 3: `before = Some(x)`, `after = None` (insert at end of
    /// sequence) returns a value strictly greater than `x`.
    #[test]
    fn assign_position_returns_value_greater_than_before_when_after_is_none() {
        let neighbors = Neighbors {
            before: Some(10.0),
            after: None,
        };
        let result = assign_position(neighbors).expect("insert-at-end should succeed");
        assert!(result > 10.0);
    }

    /// SDD REQ-004 unit test 4: equal or out-of-order neighbors are rejected as
    /// `NonMonotonicNeighbors` rather than producing a nonsensical position.
    #[test]
    fn assign_position_rejects_equal_or_out_of_order_neighbors() {
        let equal = Neighbors {
            before: Some(5.0),
            after: Some(5.0),
        };
        assert_eq!(
            assign_position(equal),
            Err(PositionAssignmentError::NonMonotonicNeighbors)
        );

        let out_of_order = Neighbors {
            before: Some(5.0),
            after: Some(3.0),
        };
        assert_eq!(
            assign_position(out_of_order),
            Err(PositionAssignmentError::NonMonotonicNeighbors)
        );
    }
}

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

use crate::ids::CrossSectionId;

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

/// Why a requested REQ-005 reorder cannot be applied: the requested ID list is not
/// exactly a permutation of the corridor's current cross-section ID set.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ReorderValidationError {
    /// A cross-section that belongs to the corridor's current sequence was left
    /// out of the requested order.
    MissingCrossSection(CrossSectionId),
    /// The requested order references a cross-section that does not belong to
    /// this corridor's current sequence.
    UnknownCrossSection(CrossSectionId),
    /// The requested order lists the same cross-section more than once.
    DuplicateCrossSection(CrossSectionId),
    /// The requested order is empty.
    EmptyOrder,
}

impl std::fmt::Display for ReorderValidationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ReorderValidationError::MissingCrossSection(id) => write!(
                f,
                "requested order is missing cross-section {id}, which is part of the corridor's current sequence"
            ),
            ReorderValidationError::UnknownCrossSection(id) => write!(
                f,
                "requested order references cross-section {id}, which does not belong to this corridor"
            ),
            ReorderValidationError::DuplicateCrossSection(id) => write!(
                f,
                "requested order references cross-section {id} more than once"
            ),
            ReorderValidationError::EmptyOrder => write!(f, "requested order is empty"),
        }
    }
}

impl std::error::Error for ReorderValidationError {}

/// Computes freshly assigned, evenly-spaced canonical positions for every
/// cross-section in a corridor, in the sequence described by `requested_order`.
///
/// Pure — no I/O. `requested_order` must be exactly a permutation of
/// `current_order` (same ID set, no additions, omissions, or duplicates) or this
/// returns a [`ReorderValidationError`] describing the mismatch and nothing is
/// computed. On success, returns one `(CrossSectionId, position)` pair per input
/// ID, in `requested_order`'s order, with positions strictly increasing — see the
/// Corridor Segment Editor SDD, REQ-005 "Design Approach", for why this is a full
/// batch recompute (`position = (index + 1) * 1024`-style spacing) rather than an
/// in-place patch: the client submits the corridor's entire new order as one list,
/// so this is the natural place to also rebalance the fractional-key gap budget
/// REQ-004's repeated midpoint insertions gradually erode.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-005-03 (Loop B GREEN pass). This stub exists
/// so Loop A's tests compile and fail for the right reason (production code
/// absent).
pub fn compute_reordered_positions(
    current_order: &[CrossSectionId],
    requested_order: &[CrossSectionId],
) -> Result<Vec<(CrossSectionId, f64)>, ReorderValidationError> {
    if requested_order.is_empty() {
        return Err(ReorderValidationError::EmptyOrder);
    }

    let mut seen = std::collections::HashSet::new();
    for id in requested_order {
        if !seen.insert(*id) {
            return Err(ReorderValidationError::DuplicateCrossSection(*id));
        }
    }

    let current_set: std::collections::HashSet<CrossSectionId> =
        current_order.iter().copied().collect();
    for id in requested_order {
        if !current_set.contains(id) {
            return Err(ReorderValidationError::UnknownCrossSection(*id));
        }
    }

    let requested_set: std::collections::HashSet<CrossSectionId> =
        requested_order.iter().copied().collect();
    for id in current_order {
        if !requested_set.contains(id) {
            return Err(ReorderValidationError::MissingCrossSection(*id));
        }
    }

    Ok(requested_order
        .iter()
        .enumerate()
        .map(|(i, id)| (*id, ((i + 1) as f64) * 1024.0))
        .collect())
}

/// Computes a new fractional `position` for a cross-section being inserted between
/// `neighbors.before` and `neighbors.after`.
///
/// Pure — no I/O. `before = None` means "insert at the start of the sequence";
/// `after = None` means "insert at the end"; both `None` means the corridor has no
/// existing cross-sections yet.
pub fn assign_position(neighbors: Neighbors) -> Result<f64, PositionAssignmentError> {
    match (neighbors.before, neighbors.after) {
        (Some(before), Some(after)) => {
            if before >= after {
                return Err(PositionAssignmentError::NonMonotonicNeighbors);
            }
            let midpoint = before + (after - before) / 2.0;
            if midpoint <= before || midpoint >= after {
                // Floating-point precision exhausted: `before`/`after` are so
                // close together that no representable value lies strictly
                // between them.
                return Err(PositionAssignmentError::UnresolvableInterval);
            }
            Ok(midpoint)
        }
        (None, Some(after)) => Ok(after - 1.0),
        (Some(before), None) => Ok(before + 1.0),
        (None, None) => Ok(0.0),
    }
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

    // --- REQ-005: compute_reordered_positions ---

    /// A 5-ID fixture matching the SDD's REQ-005 unit test scenarios (`[XS-1..XS-5]`
    /// represented as `CrossSectionId(1)..CrossSectionId(5)`).
    fn five_ids() -> Vec<CrossSectionId> {
        (1..=5).map(CrossSectionId::from).collect()
    }

    /// Asserts a successful `compute_reordered_positions` result: the returned
    /// pairs are in `expected_order`, with strictly increasing positions, no
    /// duplicate positions, and exactly the same ID set as the input.
    fn assert_valid_reorder(
        result: &Result<Vec<(CrossSectionId, f64)>, ReorderValidationError>,
        expected_order: &[CrossSectionId],
    ) {
        let pairs = result
            .as_ref()
            .expect("valid permutation should be accepted");
        let returned_ids: Vec<CrossSectionId> = pairs.iter().map(|(id, _)| *id).collect();
        assert_eq!(
            returned_ids, expected_order,
            "returned cross-sections should be in the requested order"
        );

        let mut previous_position: Option<f64> = None;
        for (_, position) in pairs {
            if let Some(prev) = previous_position {
                assert!(
                    *position > prev,
                    "positions should be strictly increasing: {prev} then {position}"
                );
            }
            previous_position = Some(*position);
        }

        let mut sorted_returned = returned_ids.clone();
        sorted_returned.sort();
        let mut sorted_expected = expected_order.to_vec();
        sorted_expected.sort();
        assert_eq!(
            sorted_returned, sorted_expected,
            "returned ID set should exactly match the requested (and current) ID set"
        );
    }

    /// SDD REQ-005 unit test 1 (move-to-start): moving the last of 5
    /// cross-sections to position 0 yields the requested order with strictly
    /// increasing positions.
    #[test]
    fn compute_reordered_positions_move_to_start() {
        let current = five_ids();
        let requested = vec![current[4], current[0], current[1], current[2], current[3]];

        let result = compute_reordered_positions(&current, &requested);

        assert_valid_reorder(&result, &requested);
    }

    /// SDD REQ-005 unit test 2 (move-to-end): moving the first of 5
    /// cross-sections to the last position yields the requested order with
    /// strictly increasing positions.
    #[test]
    fn compute_reordered_positions_move_to_end() {
        let current = five_ids();
        let requested = vec![current[1], current[2], current[3], current[4], current[0]];

        let result = compute_reordered_positions(&current, &requested);

        assert_valid_reorder(&result, &requested);
    }

    /// SDD REQ-005 unit test 3 (move-to-middle): moving the last cross-section to
    /// the middle of the sequence yields the requested order with strictly
    /// increasing positions.
    #[test]
    fn compute_reordered_positions_move_to_middle() {
        let current = five_ids();
        let requested = vec![current[0], current[1], current[4], current[2], current[3]];

        let result = compute_reordered_positions(&current, &requested);

        assert_valid_reorder(&result, &requested);
    }

    /// SDD REQ-005 unit test 4 (invariant): for a handful of hand-picked
    /// permutations of the same 5-ID set, the returned positions are always
    /// strictly increasing, contain no duplicates, and cover exactly the same ID
    /// set as the input — regardless of how scrambled the requested order is.
    #[test]
    fn compute_reordered_positions_invariant_holds_across_permutations() {
        let current = five_ids();
        let permutations: Vec<Vec<CrossSectionId>> = vec![
            vec![current[2], current[0], current[3], current[1], current[4]],
            vec![current[4], current[3], current[2], current[1], current[0]],
            vec![current[1], current[3], current[0], current[4], current[2]],
        ];

        for requested in &permutations {
            let result = compute_reordered_positions(&current, requested);
            assert_valid_reorder(&result, requested);
        }
    }

    /// `assert_valid_reorder`'s "strictly increasing, no duplicates" invariant
    /// holds for any positive, strictly-increasing spacing formula, so it can't
    /// tell `(index + 1) * 1024.0` apart from e.g. `(index + 1) + 1024.0` or
    /// `(index + 1) / 1024.0` — both are also strictly increasing over
    /// non-negative indices. Pin the exact literal spacing so a regression in
    /// the formula itself (not just its ordering) is caught.
    #[test]
    fn compute_reordered_positions_spaces_positions_by_1024() {
        let current = five_ids();
        let requested = current.clone();

        let result = compute_reordered_positions(&current, &requested).unwrap();

        let positions: Vec<f64> = result.iter().map(|(_, position)| *position).collect();
        assert_eq!(positions, vec![1024.0, 2048.0, 3072.0, 4096.0, 5120.0]);
    }

    /// SDD REQ-005 unit test 5a: a requested order referencing a cross-section ID
    /// unknown to the corridor's current sequence is rejected as
    /// `UnknownCrossSection`, not silently accepted or misreported as something
    /// else.
    #[test]
    fn compute_reordered_positions_rejects_unknown_cross_section() {
        let current = five_ids();
        let unknown_id = CrossSectionId::from(999);
        // All 5 current IDs present (nothing missing), plus one ID the corridor
        // doesn't recognize — isolates the failure to "unknown", not "missing".
        let requested = vec![
            current[0], current[1], current[2], current[3], current[4], unknown_id,
        ];

        let result = compute_reordered_positions(&current, &requested);

        match result {
            Err(ReorderValidationError::UnknownCrossSection(id)) => {
                assert_eq!(id, unknown_id)
            }
            other => panic!("expected UnknownCrossSection({unknown_id:?}), got {other:?}"),
        }
    }

    /// SDD REQ-005 unit test 5b: a requested order that leaves out a
    /// cross-section belonging to the corridor's current sequence is rejected as
    /// `MissingCrossSection`.
    #[test]
    fn compute_reordered_positions_rejects_missing_cross_section() {
        let current = five_ids();
        // Omits current[2] entirely; no unknown or duplicate IDs — isolates the
        // failure to "missing".
        let requested = vec![current[0], current[1], current[3], current[4]];

        let result = compute_reordered_positions(&current, &requested);

        match result {
            Err(ReorderValidationError::MissingCrossSection(id)) => {
                assert_eq!(id, current[2])
            }
            other => panic!(
                "expected MissingCrossSection({:?}), got {other:?}",
                current[2]
            ),
        }
    }

    /// SDD REQ-005 unit test 5c: a requested order that lists the same
    /// cross-section twice is rejected as `DuplicateCrossSection`.
    #[test]
    fn compute_reordered_positions_rejects_duplicate_cross_section() {
        let current = five_ids();
        // All 5 current IDs present (nothing missing), plus a second occurrence
        // of current[0] — isolates the failure to "duplicate".
        let requested = vec![
            current[0], current[1], current[2], current[3], current[4], current[0],
        ];

        let result = compute_reordered_positions(&current, &requested);

        match result {
            Err(ReorderValidationError::DuplicateCrossSection(id)) => {
                assert_eq!(id, current[0])
            }
            other => panic!(
                "expected DuplicateCrossSection({:?}), got {other:?}",
                current[0]
            ),
        }
    }

    /// SDD REQ-005 unit test 5d: an empty requested order is rejected as
    /// `EmptyOrder` rather than treated as "remove every cross-section" or
    /// silently accepted.
    #[test]
    fn compute_reordered_positions_rejects_empty_order() {
        let current = five_ids();
        let requested: Vec<CrossSectionId> = vec![];

        let result = compute_reordered_positions(&current, &requested);

        assert_eq!(result, Err(ReorderValidationError::EmptyOrder));
    }
}

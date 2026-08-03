//! Pure single-cross-section label edit — no I/O.
//!
//! Covers REQ-006: validating a cross-section's descriptive `label` and applying
//! an edit to exactly one cross-section within an in-memory sequence, proving the
//! isolation guarantee (siblings are returned byte-identical). See
//! `docs/ddd/bounded-context-canvas.md` (Corridor Design context) and the Corridor
//! Segment Editor SDD, REQ-006, for the isolation guarantee's data-layer
//! counterpart (`repository::update_cross_section_label`'s single-row `UPDATE`).

use crate::corridor_design::CrossSection;
use crate::ids::CrossSectionId;

/// Why a submitted label was rejected.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LabelValidationError {
    /// The label is empty after trimming leading/trailing whitespace.
    Empty,
    /// The label exceeds [`MAX_LABEL_LENGTH`] characters.
    TooLong,
    /// The label contains one or more control characters.
    ContainsControlCharacters,
}

impl std::fmt::Display for LabelValidationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            LabelValidationError::Empty => write!(f, "label is empty after trimming"),
            LabelValidationError::TooLong => {
                write!(f, "label exceeds {MAX_LABEL_LENGTH} characters")
            }
            LabelValidationError::ContainsControlCharacters => {
                write!(f, "label contains control characters")
            }
        }
    }
}

impl std::error::Error for LabelValidationError {}

/// Why [`apply_cross_section_edit`] could not apply a requested edit.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EditError {
    /// `target_id` does not match any cross-section in the given sequence.
    NotFound(CrossSectionId),
}

impl std::fmt::Display for EditError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            EditError::NotFound(id) => write!(f, "cross-section {id} not found"),
        }
    }
}

impl std::error::Error for EditError {}

/// Maximum allowed length (in characters) of a cross-section's descriptive label.
pub const MAX_LABEL_LENGTH: usize = 200;

/// Validates and trims a raw label submitted for a cross-section.
///
/// Pure — no I/O. Trims leading/trailing whitespace; rejects an empty (post-trim)
/// label, a label longer than [`MAX_LABEL_LENGTH`] characters, and a label
/// containing control characters. See the Corridor Segment Editor SDD, REQ-006
/// "Security" section.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-006-05 (Loop B GREEN pass). This stub exists
/// so Loop A's tests compile and fail for the right reason (production code
/// absent).
pub fn validate_label(raw: &str) -> Result<Option<String>, LabelValidationError> {
    let _ = raw;
    unimplemented!("IMP-REQ-006-05: validate_label not yet implemented")
}

/// Returns a new `Vec<CrossSection>` in which every cross-section other than
/// `target_id` is unchanged from `cross_sections`, and `target_id`'s `label` is
/// replaced with `new_label`.
///
/// Pure — no I/O. Proves the isolation guarantee described in the Corridor
/// Segment Editor SDD, REQ-006 "Design Approach": editing one cross-section must
/// not touch any other cross-section's fields. Returns [`EditError::NotFound`] if
/// `target_id` does not match any cross-section in `cross_sections` — the input
/// `Vec` was taken by value, so there is nothing left to "roll back"; the caller
/// simply never receives a mutated sequence.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-006-05 (Loop B GREEN pass). This stub exists
/// so Loop A's tests compile and fail for the right reason (production code
/// absent).
pub fn apply_cross_section_edit(
    cross_sections: Vec<CrossSection>,
    target_id: CrossSectionId,
    new_label: Option<String>,
) -> Result<Vec<CrossSection>, EditError> {
    let _ = (cross_sections, target_id, new_label);
    unimplemented!("IMP-REQ-006-05: apply_cross_section_edit not yet implemented")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ids::CorridorId;

    /// Builds a `CrossSection` fixture with a distinct position/coordinate/label,
    /// matching the shape TC-REQ-006-1/2's A/B/C fixture describes.
    fn make_cross_section(id: i64, position: f64, label: &str) -> CrossSection {
        CrossSection {
            id: CrossSectionId::from(id),
            corridor_id: CorridorId::from(1),
            position,
            lat: 45.500 + position * 0.001,
            lon: -73.600 + position * 0.001,
            osm_way_id: None,
            osm_node_id: None,
            label: Some(label.to_string()),
        }
    }

    /// Three cross-sections A, B, C matching TC-REQ-006-1/2's stated
    /// preconditions (distinct labels, position order A < B < C).
    fn sample_three() -> Vec<CrossSection> {
        vec![
            make_cross_section(1, 0.0, "Main St @ 5th Ave"),
            make_cross_section(2, 1.0, "Main St @ 6th Ave"),
            make_cross_section(3, 2.0, "Main St @ 7th Ave"),
        ]
    }

    /// SDD REQ-006 unit test 1 / TC-REQ-006-2 (unit slice): editing B's label
    /// leaves A and C byte-identical — every field, not just `label`.
    #[test]
    fn apply_cross_section_edit_updates_only_target_label() {
        let cross_sections = sample_three();
        let original_a = cross_sections[0].clone();
        let original_c = cross_sections[2].clone();
        let target_id = cross_sections[1].id;
        let new_label = "Main St @ 6th Ave (widened)".to_string();

        let result = apply_cross_section_edit(cross_sections, target_id, Some(new_label.clone()));

        let updated = result.expect("editing a known target should succeed");
        assert_eq!(updated.len(), 3);
        assert_eq!(
            updated[0], original_a,
            "sibling A must be byte-identical across every field"
        );
        assert_eq!(
            updated[2], original_c,
            "sibling C must be byte-identical across every field"
        );
        assert_eq!(updated[1].id, target_id);
        assert_eq!(updated[1].label.as_deref(), Some(new_label.as_str()));
        // Every non-label field on the target itself is also unchanged.
        assert_eq!(updated[1].position, 1.0);
        assert_eq!(updated[1].corridor_id, CorridorId::from(1));
    }

    /// SDD REQ-006 unit test 2 / TC-REQ-006 preconditions: an unknown `target_id`
    /// is rejected as `EditError::NotFound` with no partial mutation.
    #[test]
    fn apply_cross_section_edit_with_unknown_target_returns_not_found() {
        let cross_sections = sample_three();
        // `apply_cross_section_edit` takes `cross_sections` by value, so "the
        // input list is unchanged" is verified against this pre-call clone
        // rather than by reusing the moved value.
        let original = cross_sections.clone();
        let unknown_id = CrossSectionId::from(999);

        let result = apply_cross_section_edit(
            cross_sections,
            unknown_id,
            Some("does not matter".to_string()),
        );

        assert_eq!(result, Err(EditError::NotFound(unknown_id)));
        assert_eq!(original, sample_three(), "input list should be unchanged");
    }

    /// SDD REQ-006 unit test 3 / TC-REQ-006-3 (boundary, pure-function slice): a
    /// label longer than [`MAX_LABEL_LENGTH`] is rejected as `TooLong`.
    #[test]
    fn validate_label_rejects_201_char_label() {
        let label = "a".repeat(MAX_LABEL_LENGTH + 1);
        assert_eq!(validate_label(&label), Err(LabelValidationError::TooLong));
    }

    /// SDD REQ-006 unit test 3 / TC-REQ-006-3: a whitespace-only label is
    /// rejected as `Empty` (empty after trimming), not silently accepted as "no
    /// label".
    #[test]
    fn validate_label_rejects_whitespace_only_label() {
        assert_eq!(validate_label("   \t  "), Err(LabelValidationError::Empty));
    }

    /// SDD REQ-006 unit test 3 / TC-REQ-006-3 (boundary): a label of exactly
    /// [`MAX_LABEL_LENGTH`] characters is accepted.
    #[test]
    fn validate_label_accepts_200_char_label() {
        let label = "a".repeat(MAX_LABEL_LENGTH);
        assert_eq!(validate_label(&label), Ok(Some(label)));
    }

    /// SDD REQ-006 unit test 3: a normal label is accepted and trimmed.
    #[test]
    fn validate_label_accepts_normal_trimmed_label() {
        assert_eq!(
            validate_label("  Main St @ 6th Ave  "),
            Ok(Some("Main St @ 6th Ave".to_string()))
        );
    }
}

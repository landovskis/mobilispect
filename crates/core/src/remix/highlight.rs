//! Pure predicate for whether a corridor counts as "edited" for the region
//! map's highlight overlay. See the design spec's "Edited corridor" term.

use crate::corridor_design::GeometrySource;
use chrono::{DateTime, Utc};

/// A corridor counts as edited if it was traced manually (inherently
/// authored — there's no pristine baseline to diff against) or if it has
/// been mutated since creation (`updated_at` advanced past `created_at` by
/// one of the not-yet-built segment-editor's add/reorder/edit operations).
pub fn is_corridor_edited(
    geometry_source: GeometrySource,
    created_at: DateTime<Utc>,
    updated_at: DateTime<Utc>,
) -> bool {
    geometry_source == GeometrySource::Manual || updated_at > created_at
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::TimeZone;

    fn ts(seconds: i64) -> DateTime<Utc> {
        Utc.timestamp_opt(seconds, 0).unwrap()
    }

    #[test]
    fn manual_corridor_is_always_edited_regardless_of_timestamps() {
        assert!(is_corridor_edited(GeometrySource::Manual, ts(100), ts(100)));
    }

    #[test]
    fn imported_corridor_untouched_since_creation_is_not_edited() {
        assert!(!is_corridor_edited(
            GeometrySource::Imported,
            ts(100),
            ts(100)
        ));
    }

    #[test]
    fn imported_corridor_mutated_after_creation_is_edited() {
        assert!(is_corridor_edited(
            GeometrySource::Imported,
            ts(100),
            ts(200)
        ));
    }
}

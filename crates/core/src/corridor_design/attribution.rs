//! Pure OSM/ODbL attribution visibility decision — no I/O.
//!
//! Covers REQ-003: whether the editor should render the `_osm_attribution.html`
//! partial for a given corridor, derived from its `geometry_source`. See
//! `docs/ddd/bounded-context-canvas.md` (Corridor Design context) and the Corridor
//! Segment Editor SDD, REQ-003, for the fail-safe rationale on `None`.

use crate::corridor_design::GeometrySource;

/// Decides whether the OSM attribution strip should be shown for a corridor.
///
/// Pure — no I/O. `Imported` corridors show attribution (ODbL obligation);
/// `Manual` corridors do not (no OSM data was used). A missing/unset
/// `geometry_source` (e.g. a data-migration gap) fails safe to `true` — see
/// SDD REQ-003 "Error Handling" for the accompanying `tracing::warn!` requirement,
/// which belongs to the imperative shell that calls this function, not here.
///
/// NOT YET IMPLEMENTED — see IMP-REQ-003-02 (Loop B GREEN pass). This stub exists so
/// Loop A's tests compile and fail for the right reason (production code absent).
pub fn attribution_visible(geometry_source: Option<GeometrySource>) -> bool {
    !matches!(geometry_source, Some(GeometrySource::Manual))
}

#[cfg(test)]
mod tests {
    use super::*;

    /// SDD REQ-003 unit test 1 / TC-REQ-003-1 (unit slice): an imported corridor's
    /// geometry_source shows attribution.
    #[test]
    fn attribution_visible_true_for_imported() {
        assert_eq!(attribution_visible(Some(GeometrySource::Imported)), true);
    }

    /// SDD REQ-003 unit test 2 / TC-REQ-003-2 (unit slice): a manual-only corridor's
    /// geometry_source does not show attribution.
    #[test]
    fn attribution_visible_false_for_manual() {
        assert_eq!(attribution_visible(Some(GeometrySource::Manual)), false);
    }

    /// SDD REQ-003 unit test 3 / TC-REQ-003-4 (unit slice): a missing/unset
    /// geometry_source (data-migration gap) fails safe to showing attribution.
    #[test]
    fn attribution_visible_true_for_missing_geometry_source() {
        assert_eq!(attribution_visible(None), true);
    }
}

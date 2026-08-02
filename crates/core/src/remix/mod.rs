//! Corridor Builder shell: a remix is a named draft of proposed street
//! corridor changes scoped to one metro region. See
//! `docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md`.

pub mod geojson;
pub mod highlight;

use crate::ids::{CorridorId, CrossSectionId, RegionId, RemixId};

/// A metro region's lat/lon extent, used to frame the region map on load.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BoundingBox {
    pub min_lat: f64,
    pub min_lon: f64,
    pub max_lat: f64,
    pub max_lon: f64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct BoundingBoxValidationError;

impl BoundingBox {
    /// A bounding box is valid when both extents are non-degenerate
    /// (`min < max` on each axis) and all four values fall within valid
    /// WGS84 ranges.
    pub fn validate(&self) -> Result<(), BoundingBoxValidationError> {
        let lat_range_ok = (-90.0..=90.0).contains(&self.min_lat)
            && (-90.0..=90.0).contains(&self.max_lat)
            && self.min_lat < self.max_lat;
        let lon_range_ok = (-180.0..=180.0).contains(&self.min_lon)
            && (-180.0..=180.0).contains(&self.max_lon)
            && self.min_lon < self.max_lon;
        if lat_range_ok && lon_range_ok {
            Ok(())
        } else {
            Err(BoundingBoxValidationError)
        }
    }
}

/// A metro region an analyst can build corridors in, as returned from the repository.
#[derive(Debug, Clone, PartialEq)]
pub struct Region {
    pub id: RegionId,
    pub name: String,
    pub bounding_box: BoundingBox,
}

/// A named draft of proposed street corridor changes, scoped to one region.
#[derive(Debug, Clone, PartialEq)]
pub struct Remix {
    pub id: RemixId,
    pub name: String,
    pub region_id: RegionId,
}

/// One corridor's geometry and highlight state, as needed to render it on
/// the region map. `cross_sections` is ordered by `position`; the first and
/// last entries are the corridor's two "intersection" endpoints (see the
/// design spec's identifier clarification — there is no separate
/// Intersection aggregate yet).
#[derive(Debug, Clone, PartialEq)]
pub struct CorridorForMap {
    pub corridor_id: CorridorId,
    pub highlighted: bool,
    pub cross_sections: Vec<CrossSectionPointForMap>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CrossSectionPointForMap {
    pub cross_section_id: CrossSectionId,
    pub lat: f64,
    pub lon: f64,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn valid_bbox() -> BoundingBox {
        BoundingBox {
            min_lat: 45.40,
            min_lon: -73.70,
            max_lat: 45.60,
            max_lon: -73.50,
        }
    }

    #[test]
    fn valid_bounding_box_passes_validation() {
        assert_eq!(valid_bbox().validate(), Ok(()));
    }

    #[test]
    fn bounding_box_with_min_lat_greater_than_max_lat_is_rejected() {
        let mut bbox = valid_bbox();
        bbox.min_lat = 46.0;
        bbox.max_lat = 45.0;
        assert_eq!(bbox.validate(), Err(BoundingBoxValidationError));
    }

    #[test]
    fn bounding_box_with_equal_min_and_max_lon_is_rejected() {
        let mut bbox = valid_bbox();
        bbox.min_lon = -73.60;
        bbox.max_lon = -73.60;
        assert_eq!(bbox.validate(), Err(BoundingBoxValidationError));
    }

    #[test]
    fn bounding_box_with_out_of_range_latitude_is_rejected() {
        let mut bbox = valid_bbox();
        bbox.max_lat = 95.0;
        assert_eq!(bbox.validate(), Err(BoundingBoxValidationError));
    }

    #[test]
    fn bounding_box_with_out_of_range_longitude_is_rejected() {
        let mut bbox = valid_bbox();
        bbox.min_lon = -185.0;
        assert_eq!(bbox.validate(), Err(BoundingBoxValidationError));
    }
}

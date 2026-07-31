//! Corridor Design: an agency analyst defines a corridor as an ordered sequence of
//! cross-sections, built either by importing real-world road geometry (GIS/OSM) or
//! by tracing it manually. See `docs/ddd/bounded-context-canvas.md` (Corridor Design
//! context) and the Corridor Segment Editor PRD for the full requirement set.

pub mod attribution;
pub mod geometry;
pub mod position;
pub mod repository;

// Declared incrementally as each requirement's Loop A pass adds its file:
// pub mod edit;         // REQ-006

use crate::ids::{CorridorId, CrossSectionId};

/// How a corridor's road geometry was produced.
#[derive(Debug, Clone, Copy, PartialEq, Eq, sqlx::Type, serde::Serialize, serde::Deserialize)]
#[sqlx(type_name = "text", rename_all = "lowercase")]
pub enum GeometrySource {
    Imported,
    Manual,
}

impl GeometrySource {
    pub fn as_db_str(self) -> &'static str {
        match self {
            GeometrySource::Imported => "imported",
            GeometrySource::Manual => "manual",
        }
    }

    pub fn from_db_str(s: &str) -> Option<Self> {
        match s {
            "imported" => Some(GeometrySource::Imported),
            "manual" => Some(GeometrySource::Manual),
            _ => None,
        }
    }
}

/// A single lat/lon coordinate, WGS84.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Coordinate {
    pub lat: f64,
    pub lon: f64,
}

impl Coordinate {
    pub fn new(lat: f64, lon: f64) -> Self {
        Self { lat, lon }
    }

    /// True if both components are finite and within valid WGS84 ranges.
    pub fn is_valid(&self) -> bool {
        self.lat.is_finite()
            && self.lon.is_finite()
            && (-90.0..=90.0).contains(&self.lat)
            && (-180.0..=180.0).contains(&self.lon)
    }
}

/// A persisted corridor, as returned from the repository.
#[derive(Debug, Clone, sqlx::FromRow)]
pub struct Corridor {
    pub id: CorridorId,
    pub name: String,
    #[sqlx(try_from = "String")]
    pub geometry_source: GeometrySourceColumn,
    pub import_format: Option<String>,
    pub osm_attribution: Option<String>,
}

/// Newtype wrapper so `sqlx::FromRow`'s `try_from = "String"` can convert the raw
/// `TEXT` column into a `GeometrySource` without requiring a custom `sqlx::Decode` impl.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct GeometrySourceColumn(pub GeometrySource);

impl TryFrom<String> for GeometrySourceColumn {
    type Error = String;

    fn try_from(value: String) -> Result<Self, Self::Error> {
        GeometrySource::from_db_str(&value)
            .map(GeometrySourceColumn)
            .ok_or_else(|| format!("unknown geometry_source value: {value}"))
    }
}

/// A persisted cross-section, as returned from the repository.
#[derive(Debug, Clone, PartialEq, sqlx::FromRow)]
pub struct CrossSection {
    pub id: CrossSectionId,
    pub corridor_id: CorridorId,
    pub position: i32,
    pub lat: f64,
    pub lon: f64,
    pub osm_way_id: Option<i64>,
    pub osm_node_id: Option<i64>,
}

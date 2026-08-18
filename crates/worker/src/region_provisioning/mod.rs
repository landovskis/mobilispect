//! Background job: populates a region's bounding box from StatsCan CMA/CA
//! data, then caches a clipped/merged OSM PBF extract for it. See
//! `docs/superpowers/specs/2026-08-18-region-osm-data-caching-design.md`.

pub mod provinces;
pub mod statcan;

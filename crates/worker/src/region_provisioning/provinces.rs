//! Hardcoded Canada province/territory table: Geofabrik download slug plus an
//! approximate rectangular extent, used only to decide which provincial OSM
//! PBF(s) to download for a region's bbox — the actual clip against the real
//! bbox happens precisely, via `osmium extract`, in `osm_extract.rs`. See
//! `docs/superpowers/specs/2026-08-18-region-osm-data-caching-design.md`.

use mobilispect_core::remix::BoundingBox;

pub struct Province {
    pub geofabrik_slug: &'static str,
    pub name: &'static str,
    pub approx_bbox: BoundingBox,
}

pub const PROVINCES: &[Province] = &[
    Province {
        geofabrik_slug: "alberta",
        name: "Alberta",
        approx_bbox: BoundingBox {
            min_lat: 49.0,
            min_lon: -120.0,
            max_lat: 60.0,
            max_lon: -110.0,
        },
    },
    Province {
        geofabrik_slug: "british-columbia",
        name: "British Columbia",
        approx_bbox: BoundingBox {
            min_lat: 48.3,
            min_lon: -139.1,
            max_lat: 60.0,
            max_lon: -114.0,
        },
    },
    Province {
        geofabrik_slug: "manitoba",
        name: "Manitoba",
        approx_bbox: BoundingBox {
            min_lat: 49.0,
            min_lon: -102.1,
            max_lat: 60.0,
            max_lon: -88.9,
        },
    },
    Province {
        geofabrik_slug: "new-brunswick",
        name: "New Brunswick",
        approx_bbox: BoundingBox {
            min_lat: 44.5,
            min_lon: -69.1,
            max_lat: 48.1,
            max_lon: -63.7,
        },
    },
    Province {
        geofabrik_slug: "newfoundland-and-labrador",
        name: "Newfoundland and Labrador",
        approx_bbox: BoundingBox {
            min_lat: 46.5,
            min_lon: -67.9,
            max_lat: 60.4,
            max_lon: -52.6,
        },
    },
    Province {
        geofabrik_slug: "northwest-territories",
        name: "Northwest Territories",
        approx_bbox: BoundingBox {
            min_lat: 59.9,
            min_lon: -136.5,
            max_lat: 78.8,
            max_lon: -101.9,
        },
    },
    Province {
        geofabrik_slug: "nova-scotia",
        name: "Nova Scotia",
        approx_bbox: BoundingBox {
            min_lat: 43.4,
            min_lon: -66.4,
            max_lat: 47.1,
            max_lon: -59.7,
        },
    },
    Province {
        geofabrik_slug: "nunavut",
        name: "Nunavut",
        approx_bbox: BoundingBox {
            min_lat: 60.0,
            min_lon: -102.0,
            max_lat: 83.2,
            max_lon: -61.2,
        },
    },
    Province {
        geofabrik_slug: "ontario",
        name: "Ontario",
        approx_bbox: BoundingBox {
            min_lat: 41.6,
            min_lon: -95.2,
            max_lat: 56.9,
            max_lon: -74.3,
        },
    },
    Province {
        geofabrik_slug: "prince-edward-island",
        name: "Prince Edward Island",
        approx_bbox: BoundingBox {
            min_lat: 45.9,
            min_lon: -64.5,
            max_lat: 47.1,
            max_lon: -61.9,
        },
    },
    Province {
        geofabrik_slug: "quebec",
        name: "Quebec",
        approx_bbox: BoundingBox {
            min_lat: 44.9,
            min_lon: -79.8,
            max_lat: 62.6,
            max_lon: -57.1,
        },
    },
    Province {
        geofabrik_slug: "saskatchewan",
        name: "Saskatchewan",
        approx_bbox: BoundingBox {
            min_lat: 48.9,
            min_lon: -110.0,
            max_lat: 60.0,
            max_lon: -101.3,
        },
    },
    Province {
        geofabrik_slug: "yukon",
        name: "Yukon",
        approx_bbox: BoundingBox {
            min_lat: 59.9,
            min_lon: -141.1,
            max_lat: 69.7,
            max_lon: -123.7,
        },
    },
];

/// Pure rectangle-overlap test against each province's `approx_bbox`. Standard
/// axis-aligned bbox intersection: two boxes overlap unless one is entirely
/// to a side of the other on either axis. An approximation is sufficient here
/// -- this only decides which provincial PBF(s) to download; the precise clip
/// against the region's real bbox happens later via `osmium extract`.
pub fn provinces_overlapping(bbox: BoundingBox) -> Vec<&'static Province> {
    PROVINCES
        .iter()
        .filter(|p| {
            let b = p.approx_bbox;
            bbox.min_lat <= b.max_lat
                && bbox.max_lat >= b.min_lat
                && bbox.min_lon <= b.max_lon
                && bbox.max_lon >= b.min_lon
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bbox_fully_inside_one_province_matches_only_that_province() {
        // Downtown Edmonton -- deep enough inside Alberta's approx bbox to
        // avoid the rough rectangles' inherent overlap near a shared border
        // (e.g. Calgary sits close enough to Alberta/BC's approx boundary
        // that both legitimately match -- harmless over-inclusion, since the
        // real clip against the exact region bbox happens later via
        // `osmium extract`, but not what this test wants to demonstrate).
        let bbox = BoundingBox {
            min_lat: 53.50,
            min_lon: -113.55,
            max_lat: 53.60,
            max_lon: -113.45,
        };
        let matches: Vec<&str> = provinces_overlapping(bbox)
            .iter()
            .map(|p| p.geofabrik_slug)
            .collect();
        assert_eq!(matches, vec!["alberta"]);
    }

    #[test]
    fn bbox_straddling_ontario_quebec_matches_both() {
        // An Ottawa-Gatineau-shaped box straddling the Ontario/Quebec border.
        let bbox = BoundingBox {
            min_lat: 45.35,
            min_lon: -76.0,
            max_lat: 45.55,
            max_lon: -75.5,
        };
        let mut matches: Vec<&str> = provinces_overlapping(bbox)
            .iter()
            .map(|p| p.geofabrik_slug)
            .collect();
        matches.sort();
        assert_eq!(matches, vec!["ontario", "quebec"]);
    }

    #[test]
    fn bbox_outside_canada_matches_nothing() {
        // Roughly New York City.
        let bbox = BoundingBox {
            min_lat: 40.70,
            min_lon: -74.01,
            max_lat: 40.72,
            max_lon: -73.99,
        };
        assert!(provinces_overlapping(bbox).is_empty());
    }

    #[test]
    fn every_province_has_a_non_degenerate_bbox() {
        for province in PROVINCES {
            let b = province.approx_bbox;
            assert!(
                b.min_lat < b.max_lat && b.min_lon < b.max_lon,
                "{} has a degenerate bbox",
                province.name
            );
        }
    }
}

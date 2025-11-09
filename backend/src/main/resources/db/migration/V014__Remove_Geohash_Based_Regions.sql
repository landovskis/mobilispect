-- Remove Geohash-Based Regions
-- Version: 1.0.0
-- Removes old geohash-based region IDs in favor of proper geographic regions
-- that will be auto-created by FeedDiscoveryService using operator places data

-- Background:
-- Old approach used geohashes in region IDs (e.g., r-9q9-toronto, r-f25d-montreal)
-- New approach uses geographic hierarchy (e.g., r-canada-ontario-toronto, r-canada-quebec-montreal)
-- This ensures one region per unique (adm0_name, adm1_name, city_name) triple

-- Delete feeds referencing geohash-based regions
-- They will be re-discovered with proper geographic regions via global discovery
DELETE FROM feeds WHERE region_onestop_id IN (
    'r-f25d-montreal',
    'r-f25d-auto',
    'r-9q9-toronto',
    'r-9q5-vancouver',
    'r-f244-ottawa',
    'r-9q9-sanfranciscobayarea'
);

-- Delete old geohash-based regions
-- FeedDiscoveryService will auto-create proper geographic regions from operator places data
DELETE FROM metropolitan_regions WHERE region_onestop_id IN (
    'r-f25d-montreal',
    'r-f25d-auto',
    'r-9q9-toronto',
    'r-9q5-vancouver',
    'r-f244-ottawa',
    'r-9q9-sanfranciscobayarea'
);

-- Note: Regions and feeds will be automatically created by FeedDiscoveryService
-- when global discovery is triggered via POST /api/feeds/discover?spec=GTFS
-- The service uses Transit.land operator places data to create proper geographic regions

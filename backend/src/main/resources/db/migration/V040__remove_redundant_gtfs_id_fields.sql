-- Remove redundant GTFS ID fields from agencies and routes tables
-- Version: 1.0.0
-- Feature: Domain model simplification
-- Constitutional Compliance: PostgreSQL 18, DDD Architecture
--
-- This migration removes redundant fields that duplicated information already
-- present in composite IDs:
-- - agencies.gtfs_agency_id (embedded in agency_onestop_id as feedId/gtfsAgencyId)
-- - routes.gtfs_route_id (embedded in route id as agencyId/gtfsRouteId)
-- - agencies.website, phone, last_feed_import (unused optional fields)

-- Remove columns from agencies table
ALTER TABLE agencies
    DROP COLUMN IF EXISTS gtfs_agency_id,
    DROP COLUMN IF EXISTS website,
    DROP COLUMN IF EXISTS phone,
    DROP COLUMN IF EXISTS last_feed_import;

-- Remove column from routes table
ALTER TABLE routes
    DROP COLUMN IF EXISTS gtfs_route_id;

-- Update comments to reflect the simplified schema
COMMENT ON TABLE agencies IS 'Transit operators providing public transportation services. Agency GTFS ID is embedded in agency_onestop_id as feedId/gtfsAgencyId.';
COMMENT ON TABLE routes IS 'Named transit lines operated by agencies. Route GTFS ID is embedded in route id as agencyId/gtfsRouteId.';

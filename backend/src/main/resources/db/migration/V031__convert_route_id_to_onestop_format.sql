-- Convert Route IDs to Onestop ID Format
-- Version: 1.0.0
-- Feature: 003-transit-route-frequency
-- Constitutional Compliance: PostgreSQL 17

-- This migration converts route IDs from simple numeric format to Transitland Onestop ID format.
-- Format: r-{geohash}-{route_identifier}
-- Example: r-f25d-100 (Route 100 in Montreal area)
--
-- The geohash is inherited from the agency's Onestop ID, providing geographic context
-- while maintaining global uniqueness across the system.

-- Step 1: Drop foreign key constraint temporarily
ALTER TABLE route_variants DROP CONSTRAINT route_variants_route_id_fkey;

-- Step 2: Increase column sizes to accommodate Onestop ID format
ALTER TABLE routes ALTER COLUMN id TYPE VARCHAR(255);
ALTER TABLE route_variants ALTER COLUMN route_id TYPE VARCHAR(255);

-- Step 3: Create a mapping table for old to new IDs
CREATE TEMP TABLE route_id_mapping AS
SELECT
    r.id AS old_id,
    'r-' ||
    SUBSTRING(a.agency_onestop_id FROM 3 FOR POSITION('-' IN SUBSTRING(a.agency_onestop_id FROM 3)) - 1) ||
    '-' ||
    LOWER(REGEXP_REPLACE(r.gtfs_route_id, '[^a-zA-Z0-9]+', '~', 'g')) AS new_id
FROM routes r
JOIN agencies a ON r.agency_onestop_id = a.agency_onestop_id;

-- Step 4: Update route_variants first using the mapping
UPDATE route_variants rv
SET route_id = m.new_id
FROM route_id_mapping m
WHERE rv.route_id = m.old_id;

-- Step 5: Update routes to new IDs
UPDATE routes r
SET id = m.new_id
FROM route_id_mapping m
WHERE r.id = m.old_id;

-- Step 6: Re-add foreign key constraint with CASCADE
ALTER TABLE route_variants
    ADD CONSTRAINT route_variants_route_id_fkey
    FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE CASCADE;

-- Step 7: Update table comment to reflect new ID format
COMMENT ON COLUMN routes.id IS 'RouteId value class - Transitland Onestop ID format (r-{geohash}-{route_identifier})';

-- Step 8: Add check constraint to ensure Onestop ID format
-- Pattern: r-{location_id}-{route_identifier}
-- Must start with 'r-' and contain at least one hyphen separator
-- Supports Unicode characters for international transit agencies
ALTER TABLE routes ADD CONSTRAINT check_route_id_onestop_format
    CHECK (id ~ '^r-.+-.+$');

-- Change RouteId delimiter from hyphen to underscore
--
-- This fixes a bug where GTFS route IDs containing hyphens (e.g., "439-N" for express variants)
-- caused the feedLocalId() method to extract incorrect values using substringAfterLast("-").
--
-- Old format: r-{agency_onestop_id}-{gtfs_route_id}  (e.g., r-f-dpz6-stm-STM-439-N)
-- New format: r-{agency_onestop_id}_{gtfs_route_id}  (e.g., r-f-dpz6-stm-STM_439-N)
--
-- The underscore delimiter ensures correct extraction even when GTFS route IDs contain hyphens.

-- Step 1: Update route_variants table first (child table)
-- We need to update the foreign key values before updating the primary key
UPDATE route_variants rv
SET route_id = 'r-' || r.agency_onestop_id || '_' || SUBSTRING(rv.route_id FROM LENGTH('r-' || r.agency_onestop_id || '-') + 1)
FROM routes r
WHERE rv.route_id = r.id;

-- Step 2: Update routes table (parent table)
UPDATE routes
SET id = 'r-' || agency_onestop_id || '_' || SUBSTRING(id FROM LENGTH('r-' || agency_onestop_id || '-') + 1);

-- Step 3: Verify no orphaned route_variants remain
-- This should return 0 rows if migration was successful
DO $$
DECLARE
    orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM route_variants rv
    WHERE NOT EXISTS (
        SELECT 1 FROM routes r WHERE r.id = rv.route_id
    );

    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Migration failed: % orphaned route_variants found', orphan_count;
    END IF;
END $$;
-- Fix Montreal Region Geohash
-- Issue: Montreal was incorrectly seeded with geohash 9q8y (San Francisco)
-- Solution: Update to correct geohash f25d (Montreal) and reassign feeds

-- Step 1: Delete the incorrect Montreal region if it exists and has no feeds
DELETE FROM metropolitan_regions
WHERE region_onestop_id = 'r-9q8y-montreal'
AND NOT EXISTS (
    SELECT 1 FROM feeds WHERE region_onestop_id = 'r-9q8y-montreal'
);

-- Step 2: Create the correct Montreal region if it doesn't exist
-- This must happen BEFORE reassigning feeds to avoid foreign key violations
INSERT INTO metropolitan_regions (region_onestop_id, name, auto_update_enabled, adm0_name, adm1_name)
VALUES ('r-f25d-montreal', 'Montreal', true, 'Canada', 'Quebec')
ON CONFLICT (region_onestop_id) DO UPDATE SET
    name = EXCLUDED.name,
    auto_update_enabled = EXCLUDED.auto_update_enabled,
    adm0_name = COALESCE(metropolitan_regions.adm0_name, EXCLUDED.adm0_name),
    adm1_name = COALESCE(metropolitan_regions.adm1_name, EXCLUDED.adm1_name),
    updated_at = NOW();

-- Step 3: Reassign all feeds from the auto-created region to the proper Montreal region
UPDATE feeds
SET region_onestop_id = 'r-f25d-montreal'
WHERE region_onestop_id = 'r-f25d-auto';

-- Step 4: Delete the auto-created region if it's now empty
DELETE FROM metropolitan_regions
WHERE region_onestop_id = 'r-f25d-auto'
AND NOT EXISTS (
    SELECT 1 FROM feeds WHERE region_onestop_id = 'r-f25d-auto'
);

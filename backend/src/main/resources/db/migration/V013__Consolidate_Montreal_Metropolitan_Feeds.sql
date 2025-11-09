-- Consolidate Montreal Metropolitan Area Feeds
-- Issue: Greater Montreal feeds are split across multiple auto-created regions
-- Solution: Consolidate all f25* feeds into r-f25d-auto region
--
-- Greater Montreal agencies include:
-- - STM (Société de transport de Montréal)
-- - STL (Société de transport de Laval)
-- - RTL (Réseau de transport de Longueuil)
-- - EXO (Various suburban transit services)

-- Step 1: Create or ensure r-f25d-auto region exists
-- We use r-f25d-auto (not r-f25d-montreal) because the code creates "r-{geohash}-auto" regions
INSERT INTO metropolitan_regions (region_onestop_id, name, auto_update_enabled, adm0_name, adm1_name)
VALUES ('r-f25d-auto', 'Greater Montreal', true, 'Canada', 'Quebec')
ON CONFLICT (region_onestop_id) DO UPDATE SET
    name = 'Greater Montreal',
    auto_update_enabled = true,
    adm0_name = COALESCE(metropolitan_regions.adm0_name, EXCLUDED.adm0_name),
    adm1_name = COALESCE(metropolitan_regions.adm1_name, EXCLUDED.adm1_name),
    updated_at = NOW();

-- Step 2: Reassign all Greater Montreal feeds (f25*) to the consolidated region
UPDATE feeds
SET region_onestop_id = 'r-f25d-auto'
WHERE region_onestop_id LIKE 'r-f25%'
  AND region_onestop_id != 'r-f25d-auto';

-- Step 3: Reassign feeds from the incorrectly named r-f25d-montreal to r-f25d-auto
UPDATE feeds
SET region_onestop_id = 'r-f25d-auto'
WHERE region_onestop_id = 'r-f25d-montreal';

-- Step 4: Delete empty Montreal-area regions
DELETE FROM metropolitan_regions
WHERE region_onestop_id LIKE 'r-f25%'
  AND region_onestop_id != 'r-f25d-auto'
  AND NOT EXISTS (
    SELECT 1 FROM feeds WHERE region_onestop_id = metropolitan_regions.region_onestop_id
  );

-- Step 5: Delete the incorrectly named region if it exists and is now empty
DELETE FROM metropolitan_regions
WHERE region_onestop_id = 'r-f25d-montreal'
  AND NOT EXISTS (
    SELECT 1 FROM feeds WHERE region_onestop_id = 'r-f25d-montreal'
  );

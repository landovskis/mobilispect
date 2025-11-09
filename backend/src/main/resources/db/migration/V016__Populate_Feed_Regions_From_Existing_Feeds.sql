-- Populate Feed-Region Associations from Existing Feeds
-- Version: 1.0.0
-- Purpose: Associate existing feeds with regions based on feed onestop ID geohash
--
-- Background:
-- V014 deleted all feeds and regions, expecting them to be re-discovered.
-- V015 converted to many-to-many relationship but found no data to migrate.
-- Feeds were re-discovered but never associated with regions because
-- FeedDiscoveryStartup only runs when feed count is 0.
--
-- This migration associates feeds with their regions using geohash-based logic,
-- matching the FeedDiscoveryService fallback approach.

-- Step 1: Associate Montreal feeds (f-f25*) with Greater Montreal region
INSERT INTO feed_regions (feed_onestop_id, region_onestop_id)
SELECT f.feed_onestop_id, 'r-f25d-auto'
FROM feeds f
WHERE f.feed_onestop_id LIKE 'f-f25%'
  AND EXISTS (SELECT 1 FROM metropolitan_regions WHERE region_onestop_id = 'r-f25d-auto')
ON CONFLICT DO NOTHING;

-- Step 2: Associate other geohash-based feeds with their auto-created regions
-- This handles feeds that match existing auto-regions (r-{geohash}-auto pattern)

-- Extract geohash from feed onestop ID and match to existing regions
-- Feed ID format: f-{geohash}-{name}, Region ID format: r-{geohash}-auto
WITH feed_geohashes AS (
    SELECT
        feed_onestop_id,
        -- Extract geohash (second component after 'f-')
        SPLIT_PART(feed_onestop_id, '-', 2) as geohash
    FROM feeds
    WHERE feed_onestop_id LIKE 'f-%'
),
normalized_geohashes AS (
    SELECT
        feed_onestop_id,
        geohash,
        -- Normalize geohash using same rules as FeedDiscoveryService.normalizeGeohashToMetroArea
        CASE
            -- Greater Montreal: All f25* geohashes map to f25d
            WHEN geohash LIKE 'f25%' THEN 'f25d'
            -- San Francisco Bay Area: All 9q8* and 9q9* geohashes
            WHEN geohash LIKE '9q8%' OR geohash LIKE '9q9%' THEN '9q9'
            -- Toronto area: 9q5* geohashes
            WHEN geohash LIKE '9q5%' THEN '9q5'
            -- Ottawa area: f244* geohashes
            WHEN geohash LIKE 'f244%' THEN 'f244'
            -- For other areas, use first 3 characters as metro identifier
            WHEN LENGTH(geohash) >= 3 THEN SUBSTRING(geohash, 1, 3)
            ELSE geohash
        END as normalized_geohash
    FROM feed_geohashes
)
INSERT INTO feed_regions (feed_onestop_id, region_onestop_id)
SELECT
    ng.feed_onestop_id,
    'r-' || ng.normalized_geohash || '-auto' as region_onestop_id
FROM normalized_geohashes ng
WHERE EXISTS (
    SELECT 1
    FROM metropolitan_regions mr
    WHERE mr.region_onestop_id = 'r-' || ng.normalized_geohash || '-auto'
)
ON CONFLICT DO NOTHING;

-- Step 3: Log statistics
DO $$
DECLARE
    total_feeds INTEGER;
    associated_feeds INTEGER;
    unassociated_feeds INTEGER;
    montreal_feeds INTEGER;
BEGIN
    SELECT COUNT(*) INTO total_feeds FROM feeds;
    SELECT COUNT(DISTINCT feed_onestop_id) INTO associated_feeds FROM feed_regions;
    unassociated_feeds := total_feeds - associated_feeds;

    SELECT COUNT(*) INTO montreal_feeds
    FROM feed_regions
    WHERE region_onestop_id = 'r-f25d-auto';

    RAISE NOTICE '=== Feed-Region Association Statistics ===';
    RAISE NOTICE 'Total feeds: %', total_feeds;
    RAISE NOTICE 'Associated feeds: %', associated_feeds;
    RAISE NOTICE 'Unassociated feeds: %', unassociated_feeds;
    RAISE NOTICE 'Montreal feeds (r-f25d-auto): %', montreal_feeds;
    RAISE NOTICE '==========================================';
END $$;

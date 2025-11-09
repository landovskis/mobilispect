-- Convert Feed-Region Relationship to Many-to-Many
-- Version: 1.0.0
-- Allows feeds to belong to multiple regions (e.g., Caltrain serves SF, SJ, Palo Alto)

-- Background:
-- Transit feeds often serve multiple cities. For example:
-- - Caltrain serves San Francisco, San Jose, Palo Alto, Redwood City, etc.
-- - Regional rail systems serve multiple metropolitan areas
-- A feed should appear in all regions it serves based on operator places data

-- Step 1: Create junction table for many-to-many relationship
CREATE TABLE feed_regions (
    feed_onestop_id VARCHAR(255) NOT NULL REFERENCES feeds(feed_onestop_id) ON DELETE CASCADE,
    region_onestop_id VARCHAR(255) NOT NULL REFERENCES metropolitan_regions(region_onestop_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (feed_onestop_id, region_onestop_id)
);

-- Add index for efficient region-based queries
CREATE INDEX idx_feed_regions_region_onestop_id ON feed_regions(region_onestop_id);
CREATE INDEX idx_feed_regions_feed_onestop_id ON feed_regions(feed_onestop_id);

COMMENT ON TABLE feed_regions IS 'Many-to-many relationship between feeds and regions they serve';

-- Step 2: Migrate existing feed-region associations to junction table
INSERT INTO feed_regions (feed_onestop_id, region_onestop_id)
SELECT feed_onestop_id, region_onestop_id
FROM feeds
WHERE region_onestop_id IS NOT NULL;

-- Step 3: Remove the old foreign key column from feeds table
-- This is a breaking change but necessary for many-to-many relationship
ALTER TABLE feeds DROP COLUMN region_onestop_id;

-- Step 4: Drop the old index that's no longer needed
DROP INDEX IF EXISTS idx_feeds_region_onestop_id;

-- Note: FeedDiscoveryService will now create multiple region associations
-- based on all places an operator serves from Transit.land operator data

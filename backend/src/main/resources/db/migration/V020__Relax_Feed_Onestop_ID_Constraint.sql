-- Relax feed_onestop_id constraint and increase length limit
-- Version: 1.0.2
-- Purpose: Further relax feed_onestop_id validation and increase length for very long IDs
--
-- Changes:
-- 1. Remove the 'f-' prefix requirement - just check that ID is not empty
-- 2. Increase column length from 255 to 512 characters for very long feed IDs
--
-- Rationale:
-- Some Transit.land feeds have extremely long IDs (e.g., concatenated operator names)
-- and the system should be flexible enough to handle any valid identifier format.

-- Drop the existing prefix constraint
ALTER TABLE feeds DROP CONSTRAINT IF EXISTS check_feed_onestop_id_prefix;

-- Increase the column length to 512 characters
ALTER TABLE feeds ALTER COLUMN feed_onestop_id TYPE VARCHAR(512);

-- Also update related tables that reference feed_onestop_id
ALTER TABLE feed_authentication ALTER COLUMN feed_onestop_id TYPE VARCHAR(512);
ALTER TABLE feed_imports ALTER COLUMN feed_onestop_id TYPE VARCHAR(512);

-- Add minimal validation: feed_onestop_id must not be empty
ALTER TABLE feeds ADD CONSTRAINT check_feed_onestop_id_not_empty
    CHECK (length(trim(feed_onestop_id)) > 0);

-- Update comments
COMMENT ON CONSTRAINT check_feed_onestop_id_not_empty ON feeds IS
    'Minimal validation: Feed onestop ID must not be empty or whitespace-only';

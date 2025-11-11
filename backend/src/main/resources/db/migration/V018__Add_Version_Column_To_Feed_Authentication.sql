-- Add version column for optimistic locking to feed_authentication table
-- This prevents concurrent modification issues when multiple feeds share authentication

ALTER TABLE feed_authentication
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add comment explaining the purpose
COMMENT ON COLUMN feed_authentication.version IS 'Version number for optimistic locking to prevent concurrent modification conflicts';

-- V053: Add partial unique index to prevent concurrent active imports for the same feed
--
-- This migration adds a partial unique index on feed_imports to ensure that only one
-- import can be in 'running' or 'pending' status for a given feed at any time. This
-- prevents race conditions when multiple import requests are made concurrently for the
-- same feed.
--
-- The index is partial (uses WHERE clause) so it only applies to active imports,
-- allowing multiple completed/failed/cancelled imports for the same feed.

CREATE UNIQUE INDEX idx_feed_imports_unique_active_per_feed
ON feed_imports(feed_onestop_id)
WHERE status IN ('running', 'pending');

COMMENT ON INDEX idx_feed_imports_unique_active_per_feed IS
'Ensures only one active import (running/pending) per feed at any time. Prevents race conditions during concurrent import requests.';

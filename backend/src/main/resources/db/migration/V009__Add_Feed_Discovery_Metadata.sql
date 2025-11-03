-- Add additional metadata columns for feed discovery enhancements (FR-020)
ALTER TABLE feeds
    ADD COLUMN IF NOT EXISTS static_feed_url TEXT,
    ADD COLUMN IF NOT EXISTS realtime_feed_url TEXT,
    ADD COLUMN IF NOT EXISTS operator_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS last_discovered_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN feeds.static_feed_url IS 'Latest GTFS static feed URL as reported by Transit.land';
COMMENT ON COLUMN feeds.realtime_feed_url IS 'Latest GTFS-RT feed URL as reported by Transit.land';
COMMENT ON COLUMN feeds.operator_name IS 'Primary operator name associated with the feed';
COMMENT ON COLUMN feeds.last_discovered_at IS 'Timestamp when the feed was last discovered via Transit.land sync';

-- Align authentication metadata with Transit.land authorization hints
ALTER TABLE feed_authentication
    ADD COLUMN IF NOT EXISTS header_name VARCHAR(120);

COMMENT ON COLUMN feed_authentication.header_name IS 'HTTP header required when using API key based authentication';

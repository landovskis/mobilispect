-- Create trip_updates table for GTFS-RT realtime trip updates
-- Per ADR 0011: GTFS-RT Parallel Ingestion Architecture

CREATE TABLE IF NOT EXISTS trip_updates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feed_id VARCHAR(128) NOT NULL,
    trip_id VARCHAR(128) NOT NULL,
    route_id VARCHAR(128),
    vehicle_id VARCHAR(128),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    delay INTEGER,
    schedule_relationship VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trip_updates_feed ON trip_updates(feed_id);
CREATE INDEX idx_trip_updates_trip ON trip_updates(feed_id, trip_id);
CREATE INDEX idx_trip_updates_timestamp ON trip_updates(timestamp DESC);
CREATE INDEX idx_trip_updates_route ON trip_updates(route_id) WHERE route_id IS NOT NULL;

COMMENT ON TABLE trip_updates IS 'Realtime trip updates (delay predictions) from GTFS-RT feeds';
COMMENT ON COLUMN trip_updates.feed_id IS 'Source feed Onestop ID';
COMMENT ON COLUMN trip_updates.delay IS 'Schedule deviation in seconds (positive = late, negative = early)';

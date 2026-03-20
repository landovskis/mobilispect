-- Create vehicle_positions table for GTFS-RT realtime vehicle positions
-- Per ADR 0011: GTFS-RT Parallel Ingestion Architecture

CREATE TABLE IF NOT EXISTS vehicle_positions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feed_id VARCHAR(128) NOT NULL,
    vehicle_id VARCHAR(128) NOT NULL,
    trip_id VARCHAR(128),
    route_id VARCHAR(128),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    bearing REAL,
    speed REAL,
    current_stop_sequence INTEGER,
    current_status VARCHAR(32),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    congestion_level VARCHAR(32),
    occupancy_status VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vehicle_positions_feed ON vehicle_positions(feed_id);
CREATE INDEX idx_vehicle_positions_vehicle ON vehicle_positions(feed_id, vehicle_id);
CREATE INDEX idx_vehicle_positions_timestamp ON vehicle_positions(timestamp DESC);
CREATE INDEX idx_vehicle_positions_route ON vehicle_positions(route_id) WHERE route_id IS NOT NULL;

COMMENT ON TABLE vehicle_positions IS 'Realtime vehicle positions from GTFS-RT feeds';
COMMENT ON COLUMN vehicle_positions.feed_id IS 'Source feed Onestop ID';
COMMENT ON COLUMN vehicle_positions.vehicle_id IS 'Vehicle identifier from GTFS-RT';
COMMENT ON COLUMN vehicle_positions.timestamp IS 'Time of position observation';

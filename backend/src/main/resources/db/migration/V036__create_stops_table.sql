-- GTFS Stops Table
-- Version: 1.0.0
-- Feature: Add GTFS stops to import feed process
-- Constitutional Compliance: PostgreSQL 17, DDD Architecture, Spring Modulith

-- This migration creates the stops table for persisting transit stop/station data from GTFS feeds.
-- Stops are physical locations where passengers board or alight from transit vehicles.
-- Each stop is uniquely identified by a Transitland Onestop ID (s-geohash-name format).

-- Stops Table
-- Purpose: Transit stops and stations from GTFS feeds
-- Relationship: Many stops per feed
-- ID Strategy: Transitland Onestop ID (s-geohash-name) as primary key
CREATE TABLE stops (
    stop_onestop_id VARCHAR(255) PRIMARY KEY,  -- StopId value class (Transitland Onestop ID format: s-geohash-name)
    feed_onestop_id VARCHAR(512) NOT NULL REFERENCES feeds(feed_onestop_id) ON DELETE CASCADE,
    gtfs_stop_id VARCHAR(255) NOT NULL,  -- ID from GTFS stops.txt
    name VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    stop_code VARCHAR(50),  -- GTFS stop_code: short text or number for riders
    stop_desc TEXT,  -- GTFS stop_desc: description providing useful information
    zone_id VARCHAR(50),  -- GTFS zone_id: fare zone identifier
    stop_url VARCHAR(512),  -- GTFS stop_url: URL for this stop
    location_type INTEGER,  -- GTFS location_type: 0=stop, 1=station, 2=entrance, 3=node, 4=boarding area
    parent_station VARCHAR(255),  -- GTFS parent_station: For stops with parent stations
    active BOOLEAN NOT NULL DEFAULT true,
    first_seen TIMESTAMP WITH TIME ZONE NOT NULL,  -- When this stop was first observed in GTFS data
    last_seen TIMESTAMP WITH TIME ZONE NOT NULL,  -- When this stop was last observed in GTFS data
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT check_stop_name_not_empty CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT check_latitude_range CHECK (latitude >= -90 AND latitude <= 90),
    CONSTRAINT check_longitude_range CHECK (longitude >= -180 AND longitude <= 180),
    CONSTRAINT check_location_type_range CHECK (location_type IS NULL OR location_type IN (0, 1, 2, 3, 4))
);

-- Indexes for performance
CREATE INDEX idx_stops_feed ON stops(feed_onestop_id);
CREATE INDEX idx_stops_gtfs_id ON stops(feed_onestop_id, gtfs_stop_id);
CREATE INDEX idx_stops_location ON stops(latitude, longitude);
CREATE INDEX idx_stops_active ON stops(active);
CREATE INDEX idx_stops_location_type ON stops(location_type) WHERE location_type IS NOT NULL;
CREATE INDEX idx_stops_last_seen ON stops(last_seen);

-- Documentation
COMMENT ON TABLE stops IS 'Transit stops and stations from GTFS feeds';
COMMENT ON COLUMN stops.stop_onestop_id IS 'Transitland Onestop ID (s-geohash-name format) - StopId value class - PRIMARY KEY';
COMMENT ON COLUMN stops.feed_onestop_id IS 'Foreign key to feeds table - each stop belongs to a feed';
COMMENT ON COLUMN stops.gtfs_stop_id IS 'Stop ID from GTFS stops.txt file';
COMMENT ON COLUMN stops.name IS 'Name of stop or station';
COMMENT ON COLUMN stops.latitude IS 'WGS 84 latitude of stop location (-90 to +90)';
COMMENT ON COLUMN stops.longitude IS 'WGS 84 longitude of stop location (-180 to +180)';
COMMENT ON COLUMN stops.stop_code IS 'Short text or number for riders (e.g., stop code on signage)';
COMMENT ON COLUMN stops.stop_desc IS 'Description providing useful information about the stop';
COMMENT ON COLUMN stops.zone_id IS 'Fare zone identifier for this stop';
COMMENT ON COLUMN stops.stop_url IS 'URL for this specific stop';
COMMENT ON COLUMN stops.location_type IS 'Type of location: 0=stop/platform, 1=station, 2=entrance/exit, 3=generic node, 4=boarding area';
COMMENT ON COLUMN stops.parent_station IS 'For stops with parent stations, the stop_id of the parent station';
COMMENT ON COLUMN stops.active IS 'Whether this stop is currently active in the feed';
COMMENT ON COLUMN stops.first_seen IS 'Timestamp when this stop was first observed in GTFS data';
COMMENT ON COLUMN stops.last_seen IS 'Timestamp when this stop was last observed in GTFS data';
COMMENT ON COLUMN stops.created_at IS 'Timestamp when this record was created in our system';
COMMENT ON COLUMN stops.updated_at IS 'Timestamp when this record was last updated';

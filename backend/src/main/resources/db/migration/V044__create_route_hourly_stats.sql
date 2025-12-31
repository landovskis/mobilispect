-- Transit Route Frequency Analysis - Route Hourly Stats
-- Version: 1.0.0
-- Feature: 003-transit-route-frequency
-- Constitutional Compliance: PostgreSQL 17, DDD Architecture

-- This migration creates the route_hourly_stats table for tracking hourly trip counts
-- and average speeds for routes.

CREATE TABLE route_hourly_stats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    route_id VARCHAR(50) NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    service_date DATE NOT NULL,
    hour_of_day SMALLINT NOT NULL,
    trip_count INTEGER NOT NULL DEFAULT 0,
    average_speed_kph DOUBLE PRECISION,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT check_route_hourly_stats_hour CHECK (hour_of_day >= 0 AND hour_of_day <= 23),
    CONSTRAINT check_route_hourly_stats_trip_count CHECK (trip_count >= 0),
    CONSTRAINT check_route_hourly_stats_average_speed CHECK (
        average_speed_kph IS NULL OR average_speed_kph > 0
    ),
    CONSTRAINT unique_route_hourly_stats UNIQUE (route_id, service_date, hour_of_day)
);

COMMENT ON TABLE route_hourly_stats IS 'Hourly trip counts and average speeds for routes';
COMMENT ON COLUMN route_hourly_stats.route_id IS 'Foreign key to routes table';
COMMENT ON COLUMN route_hourly_stats.service_date IS 'Date this hourly stat applies to';
COMMENT ON COLUMN route_hourly_stats.hour_of_day IS 'Hour of day (0-23) for the stat';
COMMENT ON COLUMN route_hourly_stats.trip_count IS 'Number of trips starting during this hour';
COMMENT ON COLUMN route_hourly_stats.average_speed_kph IS 'Average speed in km/h for trips starting during this hour';
COMMENT ON COLUMN route_hourly_stats.calculated_at IS 'Timestamp when this stat was calculated from GTFS data';

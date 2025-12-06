-- Transit Route Frequency Analysis - Frequency Tables
-- Version: 1.0.0
-- Feature: 003-transit-route-frequency
-- Constitutional Compliance: PostgreSQL 17, DDD Architecture

-- This migration creates the frequencies table for tracking service headways
-- during specific time periods for route variants.

-- Time period enumeration for frequency analysis
CREATE TYPE time_period AS ENUM (
    'WEEKDAY_AM_PEAK',    -- 6:00-9:00 AM on weekdays
    'WEEKDAY_PM_PEAK',    -- 4:00-7:00 PM on weekdays
    'WEEKDAY_OFF_PEAK',   -- All other weekday hours
    'WEEKEND',            -- Saturday-Sunday all day
    'HOLIDAY'             -- Based on GTFS calendar_dates.txt
);

COMMENT ON TYPE time_period IS 'Time periods for frequency analysis based on service patterns';

-- Frequencies table
-- Purpose: Track service headways for route variants during specific time periods
-- Relationship: Many frequencies per variant (one per service date and time period)
-- Uniqueness: One frequency record per (variant, service_date, time_period) combination
CREATE TABLE frequencies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    variant_id VARCHAR(64) NOT NULL REFERENCES route_variants(id) ON DELETE CASCADE,
    service_date DATE NOT NULL,
    time_period time_period NOT NULL,
    average_headway_minutes DOUBLE PRECISION,  -- NULL if irregular schedule
    min_headway_minutes DOUBLE PRECISION,
    max_headway_minutes DOUBLE PRECISION,
    trip_count INTEGER NOT NULL DEFAULT 0,  -- Number of trips in this period
    is_irregular BOOLEAN NOT NULL DEFAULT false,  -- True if no fixed headway pattern
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT check_frequency_trip_count CHECK (trip_count >= 0),
    CONSTRAINT check_frequency_average_headway CHECK (average_headway_minutes IS NULL OR average_headway_minutes > 0),
    CONSTRAINT check_frequency_min_headway CHECK (min_headway_minutes IS NULL OR min_headway_minutes > 0),
    CONSTRAINT check_frequency_max_headway CHECK (max_headway_minutes IS NULL OR max_headway_minutes > 0),
    CONSTRAINT check_frequency_regular_has_average CHECK (
        is_irregular = true OR average_headway_minutes IS NOT NULL
    ),
    CONSTRAINT check_frequency_min_max_order CHECK (
        min_headway_minutes IS NULL OR
        max_headway_minutes IS NULL OR
        min_headway_minutes <= max_headway_minutes
    )
);

COMMENT ON TABLE frequencies IS 'Service headways for route variants during specific time periods';
COMMENT ON COLUMN frequencies.variant_id IS 'Foreign key to route_variants - the service pattern being measured';
COMMENT ON COLUMN frequencies.service_date IS 'Date this frequency measurement applies to';
COMMENT ON COLUMN frequencies.time_period IS 'Time period classification (peak, off-peak, weekend, holiday)';
COMMENT ON COLUMN frequencies.average_headway_minutes IS 'Average time between trips in minutes (NULL for irregular service)';
COMMENT ON COLUMN frequencies.min_headway_minutes IS 'Minimum headway observed during this period';
COMMENT ON COLUMN frequencies.max_headway_minutes IS 'Maximum headway observed during this period';
COMMENT ON COLUMN frequencies.trip_count IS 'Number of trips that occurred during this time period';
COMMENT ON COLUMN frequencies.is_irregular IS 'True if service has no fixed headway pattern';
COMMENT ON COLUMN frequencies.calculated_at IS 'Timestamp when this frequency was calculated from GTFS data';

-- Create variant_schedule table to store schedule summaries for route variants
-- Feature: route-variant-schedule-display

-- Create variant_schedule table
CREATE TABLE IF NOT EXISTS variant_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id VARCHAR(64) NOT NULL UNIQUE,
    first_departure_time TIME NOT NULL,
    last_departure_time TIME NOT NULL,
    trip_count INTEGER NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Ensure valid trip count
    CONSTRAINT positive_trip_count CHECK (trip_count > 0)
);

-- Create indexes for efficient querying
CREATE INDEX idx_variant_schedule_variant ON variant_schedule(variant_id);
CREATE INDEX idx_variant_schedule_calculated_at ON variant_schedule(calculated_at);

-- Add comments for documentation
COMMENT ON TABLE variant_schedule IS 'Stores schedule summary (first/last departure times) for route variants';
COMMENT ON COLUMN variant_schedule.id IS 'Unique identifier (UUID)';
COMMENT ON COLUMN variant_schedule.variant_id IS 'Route variant ID this schedule applies to (SHA-256 hash)';
COMMENT ON COLUMN variant_schedule.first_departure_time IS 'Earliest departure time from the first stop across all trips';
COMMENT ON COLUMN variant_schedule.last_departure_time IS 'Latest departure time from the first stop across all trips';
COMMENT ON COLUMN variant_schedule.trip_count IS 'Number of trips operating this variant';
COMMENT ON COLUMN variant_schedule.calculated_at IS 'Timestamp when this schedule summary was calculated';
COMMENT ON COLUMN variant_schedule.created_at IS 'Record creation timestamp';

-- Create variant_departures table to store individual departure times
-- Feature: route-variant-schedule-display

-- Create variant_departures table
CREATE TABLE IF NOT EXISTS variant_departures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id VARCHAR(64) NOT NULL,
    departure_time TIME NOT NULL,
    trip_id VARCHAR(128) NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Ensure unique departure per trip for a variant
    CONSTRAINT unique_variant_trip UNIQUE (variant_id, trip_id)
);

-- Create indexes for efficient querying
CREATE INDEX idx_variant_departures_variant ON variant_departures(variant_id);
CREATE INDEX idx_variant_departures_time ON variant_departures(variant_id, departure_time);
CREATE INDEX idx_variant_departures_calculated ON variant_departures(calculated_at);

-- Add comments for documentation
COMMENT ON TABLE variant_departures IS 'Stores individual departure times for route variants to enable complete schedule display';
COMMENT ON COLUMN variant_departures.id IS 'Unique identifier (UUID)';
COMMENT ON COLUMN variant_departures.variant_id IS 'Route variant ID (SHA-256 hash)';
COMMENT ON COLUMN variant_departures.departure_time IS 'Departure time from the first stop';
COMMENT ON COLUMN variant_departures.trip_id IS 'Original GTFS trip ID for reference';
COMMENT ON COLUMN variant_departures.calculated_at IS 'Timestamp when this departure was recorded';
COMMENT ON COLUMN variant_departures.created_at IS 'Record creation timestamp';

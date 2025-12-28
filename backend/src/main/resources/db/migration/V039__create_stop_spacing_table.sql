-- Create stop_spacing table to store distances between consecutive stops
-- Feature: 001-stop-spacing-classification
-- Replaces the average_stop_spacing_km column in route_variants with granular per-segment spacing data

-- Create stop_spacing table
CREATE TABLE IF NOT EXISTS stop_spacing (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id VARCHAR(64) NOT NULL,
    from_stop_id VARCHAR(64) NOT NULL,
    to_stop_id VARCHAR(64) NOT NULL,
    stop_sequence INTEGER NOT NULL,
    distance_meters DOUBLE PRECISION NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Ensure unique spacing records per variant and stop pair
    CONSTRAINT unique_variant_stop_pair UNIQUE (variant_id, from_stop_id, to_stop_id),

    -- Ensure valid distance
    CONSTRAINT positive_distance CHECK (distance_meters >= 0),

    -- Ensure valid sequence
    CONSTRAINT non_negative_sequence CHECK (stop_sequence >= 0)
);

-- Create indexes for efficient querying
CREATE INDEX idx_stop_spacing_variant ON stop_spacing(variant_id);
CREATE INDEX idx_stop_spacing_sequence ON stop_spacing(variant_id, stop_sequence);
CREATE INDEX idx_stop_spacing_calculated_at ON stop_spacing(calculated_at);

-- Add comments for documentation
COMMENT ON TABLE stop_spacing IS 'Stores distance between consecutive stops on route variants';
COMMENT ON COLUMN stop_spacing.id IS 'Unique identifier (UUID)';
COMMENT ON COLUMN stop_spacing.variant_id IS 'Route variant ID this spacing applies to';
COMMENT ON COLUMN stop_spacing.from_stop_id IS 'GTFS stop ID for the origin stop';
COMMENT ON COLUMN stop_spacing.to_stop_id IS 'GTFS stop ID for the destination stop';
COMMENT ON COLUMN stop_spacing.stop_sequence IS 'Sequence number of the from-stop in the variant (0-based)';
COMMENT ON COLUMN stop_spacing.distance_meters IS 'Distance between the two stops in meters (using Haversine formula)';
COMMENT ON COLUMN stop_spacing.calculated_at IS 'Timestamp when this spacing was calculated';
COMMENT ON COLUMN stop_spacing.created_at IS 'Record creation timestamp';

-- Remove the old average_stop_spacing_km column from route_variants
-- This data is now captured with more granularity in the stop_spacing table
ALTER TABLE route_variants DROP COLUMN IF EXISTS average_stop_spacing_km;

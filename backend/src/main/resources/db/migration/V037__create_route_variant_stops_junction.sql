-- Route Variant Stops Junction Table
-- Version: 1.0.0
-- Feature: Add GTFS stops to import feed process
-- Constitutional Compliance: PostgreSQL 17, DDD Architecture, Spring Modulith

-- This migration creates the route_variant_stops junction table for normalized stop references.
-- This table links route variants to their ordered stop sequences, complementing the denormalized
-- stop_pattern and stop_name_pattern columns in the route_variants table.

-- Purpose: Enable efficient querying like "find all variants serving stop X" while maintaining
-- the performance benefits of denormalized stop patterns for display purposes.

-- Route Variant Stops Junction Table
-- Purpose: Links route variants to their ordered stop sequences
-- Relationship: Many-to-many between route_variants and stops
-- Primary Key: Composite of variant_id and stop_sequence
CREATE TABLE route_variant_stops (
    variant_id VARCHAR(64) NOT NULL REFERENCES route_variants(id) ON DELETE CASCADE,
    stop_onestop_id VARCHAR(255) NOT NULL REFERENCES stops(stop_onestop_id) ON DELETE CASCADE,
    stop_sequence INTEGER NOT NULL,  -- Order of stop in variant (0-based)

    -- Composite primary key ensures each sequence position is unique per variant
    PRIMARY KEY (variant_id, stop_sequence),

    -- Constraints
    CONSTRAINT check_sequence_positive CHECK (stop_sequence >= 0)
);

-- Indexes for performance
CREATE INDEX idx_route_variant_stops_stop ON route_variant_stops(stop_onestop_id);
CREATE INDEX idx_route_variant_stops_variant ON route_variant_stops(variant_id);

-- Documentation
COMMENT ON TABLE route_variant_stops IS 'Junction table linking route variants to their ordered stop sequences';
COMMENT ON COLUMN route_variant_stops.variant_id IS 'Foreign key to route_variants table (SHA-256 hash)';
COMMENT ON COLUMN route_variant_stops.stop_onestop_id IS 'Foreign key to stops table (Transitland Onestop ID)';
COMMENT ON COLUMN route_variant_stops.stop_sequence IS 'Order of stop in the variant sequence (0-based, matches GTFS stop_times.stop_sequence)';

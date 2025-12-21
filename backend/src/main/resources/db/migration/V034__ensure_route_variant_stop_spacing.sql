-- Ensure average stop spacing column exists for route variants
-- Feature: 001-stop-spacing-classification

ALTER TABLE route_variants
    ADD COLUMN IF NOT EXISTS average_stop_spacing_km DOUBLE PRECISION;

COMMENT ON COLUMN route_variants.average_stop_spacing_km IS 'Average distance between consecutive stops in kilometers';

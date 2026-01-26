-- Add route classification columns to route_variants table
-- Classification is based on average stop spacing calculated from stop_spacing records

ALTER TABLE route_variants
    ADD COLUMN classification VARCHAR(20),
    ADD COLUMN average_stop_spacing_meters DOUBLE PRECISION;

-- Add check constraint for valid classification values
ALTER TABLE route_variants
    ADD CONSTRAINT route_variants_classification_check
    CHECK (classification IS NULL OR classification IN (
        'LOCAL', 'LIMITED', 'RAPID', 'SUBURBAN',
        'REGIONAL', 'EXPRESS', 'REGIONAL_EXPRESS', 'UNKNOWN'
    ));

-- Create index for filtering by classification
CREATE INDEX idx_route_variants_classification ON route_variants (classification);

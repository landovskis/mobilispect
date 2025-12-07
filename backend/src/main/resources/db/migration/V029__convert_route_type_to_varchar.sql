-- Convert route_type from custom enum to VARCHAR
-- This allows Hibernate to work with the column without requiring custom type handling

-- First, alter the column to use VARCHAR with a constraint
ALTER TABLE routes
  ALTER COLUMN route_type TYPE VARCHAR(50);

-- Add check constraint to ensure only valid route types are stored
ALTER TABLE routes
  ADD CONSTRAINT check_route_type_valid
  CHECK (route_type IN ('TRAM', 'SUBWAY', 'RAIL', 'BUS', 'FERRY', 'CABLE_TRAM', 'AERIAL_LIFT', 'FUNICULAR', 'TROLLEYBUS', 'MONORAIL'));

-- Drop the custom enum type (only after the column is converted)
DROP TYPE IF EXISTS route_type;

COMMENT ON COLUMN routes.route_type IS 'GTFS route_type as VARCHAR with check constraint for valid values';

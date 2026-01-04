-- Drop overly strict route ID format constraint to allow existing IDs.
ALTER TABLE routes
    DROP CONSTRAINT IF EXISTS check_route_id_onestop_format;

-- Remove legacy GTFS route ID column to align with current domain model.
-- Safe to run even if column/index already removed by later migrations.
ALTER TABLE routes
    DROP COLUMN IF EXISTS gtfs_route_id;

DROP INDEX IF EXISTS idx_routes_gtfs_route_id;

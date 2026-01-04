-- Remove legacy GTFS agency ID column to align with current domain model.
-- Safe to run even if column/index already removed by later migrations.
ALTER TABLE agencies
    DROP COLUMN IF EXISTS gtfs_agency_id;

DROP INDEX IF EXISTS idx_agencies_gtfs_agency_id;

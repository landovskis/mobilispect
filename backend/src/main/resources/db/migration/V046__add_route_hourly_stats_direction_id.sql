-- Transit Route Frequency Analysis - Route Hourly Stats
-- Version: 1.1.0
-- Feature: 003-transit-route-frequency
-- Constitutional Compliance: PostgreSQL 17, DDD Architecture

-- Add direction_id to separate hourly stats by GTFS direction_id.

ALTER TABLE route_hourly_stats
  ADD COLUMN direction_id SMALLINT;

COMMENT ON COLUMN route_hourly_stats.direction_id IS 'GTFS direction_id (0 outbound, 1 inbound, null unknown)';

ALTER TABLE route_hourly_stats
  DROP CONSTRAINT IF EXISTS unique_route_hourly_stats;

ALTER TABLE route_hourly_stats
  ADD CONSTRAINT unique_route_hourly_stats UNIQUE (route_id, service_date, hour_of_day, direction_id);

-- Transit Route Frequency Analysis - Route Hourly Stats
-- Version: 1.2.0
-- Feature: 003-transit-route-frequency
-- Constitutional Compliance: PostgreSQL 17, DDD Architecture

-- Add day_type to split hourly stats by calendar-derived service day buckets.

ALTER TABLE route_hourly_stats
  ADD COLUMN day_type VARCHAR(20) NOT NULL DEFAULT 'WEEKDAY';

COMMENT ON COLUMN route_hourly_stats.day_type IS 'Service day type (WEEKDAY, SATURDAY, SUNDAY, HOLIDAY)';

ALTER TABLE route_hourly_stats
  DROP CONSTRAINT IF EXISTS unique_route_hourly_stats;

ALTER TABLE route_hourly_stats
  ADD CONSTRAINT unique_route_hourly_stats
    UNIQUE (route_id, service_date, hour_of_day, direction_id, day_type);

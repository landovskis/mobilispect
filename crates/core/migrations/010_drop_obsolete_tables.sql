-- migrations/010_drop_obsolete_tables.sql
-- Drop tables that are no longer part of the data model:
--   benchmarks         — feature removed entirely
--   feed_info          — replaced by ingest metadata columns on feeds table
--   route_daily        — replaced by route_daily_stats
--   route_speed_daily  — replaced by route_daily_stats
--   route_speed_day_type — derivable at query time from route_daily_stats

DROP TABLE IF EXISTS benchmarks;
DROP TABLE IF EXISTS feed_info;
DROP TABLE IF EXISTS route_daily;
DROP TABLE IF EXISTS route_speed_daily;
DROP TABLE IF EXISTS route_speed_day_type;

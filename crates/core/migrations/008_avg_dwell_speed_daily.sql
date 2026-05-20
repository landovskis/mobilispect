-- migrations/008_avg_dwell_speed_daily.sql
ALTER TABLE route_speed_daily ADD COLUMN avg_dwell_secs DOUBLE PRECISION;
-- Transit Route Frequency Analysis - Route Hourly Stats hour_of_day type adjustment
-- Version: 1.0.0
-- Feature: 003-transit-route-frequency

ALTER TABLE route_hourly_stats
    ALTER COLUMN hour_of_day TYPE INTEGER;

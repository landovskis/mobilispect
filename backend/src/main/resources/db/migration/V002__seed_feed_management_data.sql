-- Feed Management System Development Seed Data
-- Version: 1.0.0
-- Purpose: Sample data for development and testing

-- Metropolitan regions are NOT seeded here.
-- Regions are automatically created by FeedDiscoveryService based on operator
-- geographic data from Transit.land API. Each unique (adm0_name, adm1_name, city_name)
-- triple results in a unique region.
--
-- To populate regions and feeds, use the global discovery API endpoint:
-- POST /api/feeds/discover?spec=GTFS
--
-- This will discover all feeds from Transit.land and automatically create regions
-- based on operator places data.

-- Sample administrator accounts for testing
INSERT INTO administrators (username, email, role, active) VALUES
('admin', 'admin@mobilispect.com', 'FEED_MANAGER', true),
('operator1', 'operator1@mobilispect.com', 'FEED_OPERATOR', true),
('operator2', 'operator2@mobilispect.com', 'FEED_OPERATOR', true),
('viewer1', 'viewer1@mobilispect.com', 'FEED_VIEWER', true),
('viewer2', 'viewer2@mobilispect.com', 'FEED_VIEWER', false)
ON CONFLICT DO NOTHING;

-- NOTE: Feed data is NOT seeded here.
-- Feeds are automatically discovered from Transit.land API on application startup
-- via the FeedDiscoveryStartup component for all regions with auto_update_enabled=true.
--
-- To populate feeds manually, use the discovery API endpoint:
-- POST /api/feeds/regions/{regionId}/discover?spec=GTFS
--
-- Requires TRANSIT_LAND_API_KEY environment variable to be set.

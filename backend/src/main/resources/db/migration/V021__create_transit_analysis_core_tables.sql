-- Transit Route Frequency Analysis - Core Tables
-- Version: 1.0.0
-- Feature: 003-transit-route-frequency
-- Constitutional Compliance: PostgreSQL 17, DDD Architecture, Spring Modulith

-- This migration creates the core tables for transit route frequency analysis:
-- - agencies: Transit operators (references existing feeds table)
-- - routes: Named transit lines operated by agencies
-- - route_variants: Specific service patterns defined by unique stop sequences

-- Note on Region Relationships:
-- Agencies do NOT have a direct region relationship. Instead:
-- Agency -> Feed (via feed_onestop_id) -> Regions (via existing feed_regions junction table)
-- This leverages the existing many-to-many feed-region relationship established in V015.

-- Custom types for transit analysis domain
CREATE TYPE route_type AS ENUM (
    'TRAM',          -- 0: Streetcar, light rail
    'SUBWAY',        -- 1: Underground metro
    'RAIL',          -- 2: Intercity/commuter rail
    'BUS',           -- 3: Bus service
    'FERRY',         -- 4: Ferry service
    'CABLE_TRAM',    -- 5: Cable car
    'AERIAL_LIFT',   -- 6: Gondola, aerial tramway
    'FUNICULAR',     -- 7: Funicular
    'TROLLEYBUS',    -- 11: Electric trolleybus
    'MONORAIL'       -- 12: Monorail
);

COMMENT ON TYPE route_type IS 'GTFS route_type enumeration for transit service modes';

-- Agencies table
-- Purpose: Transit operators providing public transportation services
-- Relationship: Many agencies per feed (one feed may contain multiple agencies)
-- Region Access: Agencies inherit region membership through their feed reference
CREATE TABLE agencies (
    agency_onestop_id VARCHAR(255) PRIMARY KEY,  -- AgencyId value class (Transitland Onestop ID format: o-geohash-name)
    feed_onestop_id VARCHAR(255) NOT NULL REFERENCES feeds(feed_onestop_id) ON DELETE CASCADE,
    gtfs_agency_id VARCHAR(255) NOT NULL,  -- ID from GTFS agency.txt
    name VARCHAR(255) NOT NULL,
    website VARCHAR(512),
    phone VARCHAR(50),
    last_feed_import TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT check_agency_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

COMMENT ON TABLE agencies IS 'Transit operators providing public transportation services';
COMMENT ON COLUMN agencies.agency_onestop_id IS 'Transitland Onestop ID (o-geohash-name format) - AgencyId value class - PRIMARY KEY';
COMMENT ON COLUMN agencies.feed_onestop_id IS 'Foreign key to feeds table - agencies inherit region membership through feed';
COMMENT ON COLUMN agencies.gtfs_agency_id IS 'Agency ID from GTFS agency.txt file';
COMMENT ON COLUMN agencies.last_feed_import IS 'Timestamp of last successful GTFS import for this agency';

-- Routes table
-- Purpose: Named transit lines operated by an agency
-- Relationship: Many routes per agency
-- Validation: Either short_name or long_name must be present
CREATE TABLE routes (
    id VARCHAR(50) PRIMARY KEY,  -- RouteId value class
    agency_onestop_id VARCHAR(255) NOT NULL REFERENCES agencies(agency_onestop_id) ON DELETE CASCADE,
    gtfs_route_id VARCHAR(255) NOT NULL,  -- ID from GTFS routes.txt
    short_name VARCHAR(255),  -- e.g., "5", "Red Line"
    long_name VARCHAR(255) NOT NULL,  -- e.g., "Downtown Express"
    route_type route_type NOT NULL,
    color VARCHAR(6),  -- Hex color without # (e.g., "FF0000")
    text_color VARCHAR(6),  -- Hex color for text on route color background
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT check_route_has_name CHECK (
        (short_name IS NOT NULL AND LENGTH(TRIM(short_name)) > 0) OR
        (long_name IS NOT NULL AND LENGTH(TRIM(long_name)) > 0)
    ),
    CONSTRAINT check_route_color_format CHECK (color IS NULL OR color ~ '^[0-9A-Fa-f]{6}$'),
    CONSTRAINT check_route_text_color_format CHECK (text_color IS NULL OR text_color ~ '^[0-9A-Fa-f]{6}$')
);

COMMENT ON TABLE routes IS 'Named transit lines operated by agencies';
COMMENT ON COLUMN routes.id IS 'RouteId value class - unique identifier across system';
COMMENT ON COLUMN routes.agency_onestop_id IS 'Foreign key to agencies table using Transitland agency onestop ID';
COMMENT ON COLUMN routes.gtfs_route_id IS 'Route ID from GTFS routes.txt file';
COMMENT ON COLUMN routes.route_type IS 'GTFS route_type enumeration (bus, rail, ferry, etc.)';
COMMENT ON COLUMN routes.color IS 'Route brand color in hex format (without # prefix)';
COMMENT ON COLUMN routes.text_color IS 'Text color for display on route color background';

-- Route Variants table
-- Purpose: Specific service patterns for routes defined by unique stop sequences
-- Relationship: Many variants per route (different directions, branches, short-turns)
-- ID Strategy: SHA-256 hash of stop pattern for deterministic variant identification
CREATE TABLE route_variants (
    id VARCHAR(64) PRIMARY KEY,  -- VariantHash value class (SHA-256 hash of stop pattern)
    route_id VARCHAR(50) NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    direction_id INTEGER,  -- 0 = outbound, 1 = inbound (from GTFS trips.txt)
    headsign VARCHAR(255),  -- Destination headsign shown to passengers
    stop_pattern TEXT NOT NULL,  -- Ordered stop IDs (pipe-separated: "stop1|stop2|stop3")
    stop_count INTEGER NOT NULL,  -- Number of stops in pattern
    first_stop_id VARCHAR(255) NOT NULL,  -- First stop in pattern
    last_stop_id VARCHAR(255) NOT NULL,  -- Last stop in pattern
    active BOOLEAN NOT NULL DEFAULT true,
    first_seen TIMESTAMP WITH TIME ZONE NOT NULL,  -- When this variant was first observed
    last_seen TIMESTAMP WITH TIME ZONE NOT NULL,  -- When this variant was last observed
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT check_variant_id_is_sha256 CHECK (id ~ '^[a-f0-9]{64}$'),
    CONSTRAINT check_variant_direction_id CHECK (direction_id IS NULL OR direction_id IN (0, 1)),
    CONSTRAINT check_variant_stop_count CHECK (stop_count >= 2),
    CONSTRAINT check_variant_stop_pattern_not_empty CHECK (LENGTH(TRIM(stop_pattern)) > 0)
);

COMMENT ON TABLE route_variants IS 'Specific service patterns for routes defined by unique stop sequences';
COMMENT ON COLUMN route_variants.id IS 'SHA-256 hash of stop pattern - VariantHash value class (64 hex characters)';
COMMENT ON COLUMN route_variants.direction_id IS '0 = outbound, 1 = inbound (from GTFS trips.direction_id)';
COMMENT ON COLUMN route_variants.stop_pattern IS 'Ordered stop IDs separated by pipes (e.g., "stop1|stop2|stop3")';
COMMENT ON COLUMN route_variants.stop_count IS 'Number of stops in the pattern (must be >= 2)';
COMMENT ON COLUMN route_variants.first_seen IS 'Timestamp when this variant was first observed in GTFS data';
COMMENT ON COLUMN route_variants.last_seen IS 'Timestamp when this variant was last observed in GTFS data';

-- Transit Route Frequency Analysis - Supporting Tables
-- Version: 1.0.0
-- Feature: 003-transit-route-frequency
-- Constitutional Compliance: PostgreSQL 17, DDD Architecture

-- This migration creates supporting tables for transit analysis:
-- - common_sections: Geographic segments where multiple routes/variants overlap
-- - common_section_variants: Junction table linking common sections to variants

-- Enable PostGIS extension for geographic data (if not already enabled)
-- This is optional for Phase 1 but included for future geographic analysis
-- TEMPORARILY DISABLED: PostGIS not installed in test environment yet
-- TODO: Re-enable PostGIS extension once installed system-wide
-- CREATE EXTENSION IF NOT EXISTS postgis;
-- COMMENT ON EXTENSION postgis IS 'PostGIS geometry and geography spatial types and functions';

-- Common Sections table
-- Purpose: Geographic segments where multiple routes/variants share the same path
-- Use Case: Calculate combined frequency for corridors served by multiple routes
-- Constitutional Requirement: Minimum 3 stops per section (from spec.md)
CREATE TABLE common_sections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stop_pattern TEXT NOT NULL,  -- Ordered stop IDs (pipe-separated: "stop1|stop2|stop3")
    stop_count INTEGER NOT NULL,  -- Number of stops in the common section
    first_stop_id VARCHAR(255) NOT NULL,  -- First stop in the section
    last_stop_id VARCHAR(255) NOT NULL,  -- Last stop in the section
    geographic_extent TEXT,  -- TEMPORARILY TEXT: Will be GEOMETRY(LINESTRING, 4326) once PostGIS is enabled
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT check_common_section_stop_count CHECK (stop_count >= 3),
    CONSTRAINT check_common_section_stop_pattern_not_empty CHECK (LENGTH(TRIM(stop_pattern)) > 0)
);

COMMENT ON TABLE common_sections IS 'Geographic segments where multiple routes/variants overlap (minimum 3 stops)';
COMMENT ON COLUMN common_sections.stop_pattern IS 'Ordered stop IDs separated by pipes (e.g., "stop1|stop2|stop3|stop4")';
COMMENT ON COLUMN common_sections.stop_count IS 'Number of stops in the common section (must be >= 3)';
COMMENT ON COLUMN common_sections.first_stop_id IS 'First stop in the common section';
COMMENT ON COLUMN common_sections.last_stop_id IS 'Last stop in the common section';
COMMENT ON COLUMN common_sections.geographic_extent IS 'TEMPORARILY TEXT: Will be PostGIS LineString geometry (SRID 4326) once PostGIS is enabled';

-- Common Section Variants junction table
-- Purpose: Link common sections to the route variants that traverse them
-- Relationship: Many-to-many between common_sections and route_variants
-- Use Case: Identify all variants serving a common corridor for combined frequency calculation
CREATE TABLE common_section_variants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    common_section_id UUID NOT NULL REFERENCES common_sections(id) ON DELETE CASCADE,
    variant_id VARCHAR(64) NOT NULL REFERENCES route_variants(id) ON DELETE CASCADE,
    start_sequence INTEGER NOT NULL,  -- Position in variant's stop pattern where section starts
    end_sequence INTEGER NOT NULL,    -- Position in variant's stop pattern where section ends
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT check_common_section_variant_sequence CHECK (start_sequence < end_sequence),
    CONSTRAINT check_common_section_variant_start_positive CHECK (start_sequence >= 0),
    UNIQUE (common_section_id, variant_id)
);

COMMENT ON TABLE common_section_variants IS 'Junction table linking common sections to the route variants that traverse them';
COMMENT ON COLUMN common_section_variants.common_section_id IS 'Foreign key to common_sections';
COMMENT ON COLUMN common_section_variants.variant_id IS 'Foreign key to route_variants';
COMMENT ON COLUMN common_section_variants.start_sequence IS 'Zero-based position in variant stop pattern where common section begins';
COMMENT ON COLUMN common_section_variants.end_sequence IS 'Zero-based position in variant stop pattern where common section ends';

-- Imported Feeds table
-- Purpose: Track GTFS feed imports for historical frequency analysis
-- Relationship: Many imports per agency over time
-- Use Case: Version control for frequency calculations and historical analysis
CREATE TABLE imported_feeds (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agency_onestop_id VARCHAR(255) NOT NULL REFERENCES agencies(agency_onestop_id) ON DELETE CASCADE,
    feed_url TEXT NOT NULL,
    feed_version VARCHAR(255),  -- Version identifier from GTFS (if provided)
    file_size_bytes BIGINT,
    import_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    import_completed_at TIMESTAMP WITH TIME ZONE,
    import_duration_seconds BIGINT,  -- Duration in seconds
    status import_status NOT NULL DEFAULT 'STARTED',  -- Reuses existing import_status type from V001
    routes_processed INTEGER,
    variants_identified INTEGER,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT check_imported_feed_file_size CHECK (file_size_bytes IS NULL OR file_size_bytes > 0),
    CONSTRAINT check_imported_feed_duration CHECK (import_duration_seconds IS NULL OR import_duration_seconds >= 0),
    CONSTRAINT check_imported_feed_routes_processed CHECK (routes_processed IS NULL OR routes_processed >= 0),
    CONSTRAINT check_imported_feed_variants_identified CHECK (variants_identified IS NULL OR variants_identified >= 0)
);

-- Note: Reuses import_status enum from V001 (STARTED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED)
-- This maintains consistency with existing feed import tracking

COMMENT ON TABLE imported_feeds IS 'Historical tracking of GTFS feed imports for transit analysis';
COMMENT ON COLUMN imported_feeds.agency_onestop_id IS 'Foreign key to agencies using Transitland onestop ID - which agency this import is for';
COMMENT ON COLUMN imported_feeds.feed_url IS 'URL of the GTFS feed that was imported';
COMMENT ON COLUMN imported_feeds.feed_version IS 'Version identifier from GTFS feed_info.txt (if provided)';
COMMENT ON COLUMN imported_feeds.file_size_bytes IS 'Size of the GTFS ZIP file in bytes';
COMMENT ON COLUMN imported_feeds.import_started_at IS 'Timestamp when import process began';
COMMENT ON COLUMN imported_feeds.import_completed_at IS 'Timestamp when import process finished (NULL if still running or failed)';
COMMENT ON COLUMN imported_feeds.import_duration_seconds IS 'Total import duration in seconds';
COMMENT ON COLUMN imported_feeds.status IS 'Import status (STARTED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED)';
COMMENT ON COLUMN imported_feeds.routes_processed IS 'Number of routes processed during import';
COMMENT ON COLUMN imported_feeds.variants_identified IS 'Number of unique route variants identified during import';
COMMENT ON COLUMN imported_feeds.error_message IS 'Error details if import failed';

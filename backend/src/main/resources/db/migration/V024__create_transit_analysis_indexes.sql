-- Transit Route Frequency Analysis - Indexes and Performance Optimization
-- Version: 1.0.0
-- Feature: 003-transit-route-frequency
-- Constitutional Compliance: PostgreSQL 17, DDD Architecture

-- This migration creates all indexes for optimal query performance across
-- the transit analysis module. Indexes are created based on expected query patterns
-- defined in the data model specification.

-- ============================================================================
-- AGENCIES TABLE INDEXES
-- ============================================================================

-- Index for finding agencies by feed (most common query pattern)
CREATE INDEX idx_agencies_feed_onestop_id ON agencies(feed_onestop_id);

-- Index for finding agencies by GTFS agency ID (used during import)
CREATE INDEX idx_agencies_gtfs_agency_id ON agencies(gtfs_agency_id);

-- Partial index for active agencies (most queries filter by active status)
CREATE INDEX idx_agencies_active ON agencies(active) WHERE active = true;

COMMENT ON INDEX idx_agencies_feed_onestop_id IS 'Efficient lookup of agencies by their parent feed';
COMMENT ON INDEX idx_agencies_gtfs_agency_id IS 'Fast lookup during GTFS import and reconciliation';
COMMENT ON INDEX idx_agencies_active IS 'Partial index for active agencies (excludes inactive to save space)';

-- ============================================================================
-- ROUTES TABLE INDEXES
-- ============================================================================

-- Index for finding routes by agency (most common query pattern)
CREATE INDEX idx_routes_agency_onestop_id ON routes(agency_onestop_id);

-- Index for finding routes by GTFS route ID (used during import)
CREATE INDEX idx_routes_gtfs_route_id ON routes(gtfs_route_id);

-- Partial index for active routes (most queries filter by active status)
CREATE INDEX idx_routes_active ON routes(active) WHERE active = true;

-- Composite index for agency + route type queries (e.g., "all bus routes for agency X")
CREATE INDEX idx_routes_agency_route_type ON routes(agency_onestop_id, route_type);

COMMENT ON INDEX idx_routes_agency_onestop_id IS 'Efficient lookup of routes by agency using Transitland onestop ID';
COMMENT ON INDEX idx_routes_gtfs_route_id IS 'Fast lookup during GTFS import and reconciliation';
COMMENT ON INDEX idx_routes_active IS 'Partial index for active routes (excludes inactive to save space)';
COMMENT ON INDEX idx_routes_agency_route_type IS 'Composite index for filtering routes by agency and service mode';

-- ============================================================================
-- ROUTE VARIANTS TABLE INDEXES
-- ============================================================================

-- Index for finding variants by route (most common query pattern)
CREATE INDEX idx_route_variants_route_id ON route_variants(route_id);

-- Indexes for stop-based queries (finding variants serving specific stops)
CREATE INDEX idx_route_variants_first_stop ON route_variants(first_stop_id);
CREATE INDEX idx_route_variants_last_stop ON route_variants(last_stop_id);

-- Partial index for active variants (most queries filter by active status)
CREATE INDEX idx_route_variants_active ON route_variants(active) WHERE active = true;

-- Index for temporal queries (finding recently active variants)
CREATE INDEX idx_route_variants_last_seen ON route_variants(last_seen DESC);

-- Composite index for route + direction queries
CREATE INDEX idx_route_variants_route_direction ON route_variants(route_id, direction_id);

COMMENT ON INDEX idx_route_variants_route_id IS 'Efficient lookup of variants by route';
COMMENT ON INDEX idx_route_variants_first_stop IS 'Find variants starting at a specific stop';
COMMENT ON INDEX idx_route_variants_last_stop IS 'Find variants ending at a specific stop';
COMMENT ON INDEX idx_route_variants_active IS 'Partial index for active variants';
COMMENT ON INDEX idx_route_variants_last_seen IS 'Find recently active variants (descending order)';
COMMENT ON INDEX idx_route_variants_route_direction IS 'Filter variants by route and direction';

-- ============================================================================
-- FREQUENCIES TABLE INDEXES
-- ============================================================================

-- Unique index enforcing one frequency per (variant, date, period) combination
-- This also serves as a performance index for the most common query pattern
CREATE UNIQUE INDEX idx_frequencies_variant_date_period
    ON frequencies(variant_id, service_date, time_period);

-- Index for date-based queries (finding frequencies for a specific date)
CREATE INDEX idx_frequencies_service_date ON frequencies(service_date);

-- Index for time period analysis queries
CREATE INDEX idx_frequencies_time_period ON frequencies(time_period);

-- Index for temporal queries (most recent frequency calculations)
CREATE INDEX idx_frequencies_calculated_at ON frequencies(calculated_at DESC);

-- Composite index for variant + date range queries
CREATE INDEX idx_frequencies_variant_date_range ON frequencies(variant_id, service_date DESC);

COMMENT ON INDEX idx_frequencies_variant_date_period IS 'Unique constraint and performance index for frequency lookups';
COMMENT ON INDEX idx_frequencies_service_date IS 'Efficient date-based frequency queries';
COMMENT ON INDEX idx_frequencies_time_period IS 'Filter frequencies by time period (peak, off-peak, weekend)';
COMMENT ON INDEX idx_frequencies_calculated_at IS 'Find most recently calculated frequencies';
COMMENT ON INDEX idx_frequencies_variant_date_range IS 'Efficient date range queries for specific variants';

-- ============================================================================
-- COMMON SECTIONS TABLE INDEXES
-- ============================================================================

-- Indexes for stop-based queries (finding sections by endpoints)
CREATE INDEX idx_common_sections_first_stop ON common_sections(first_stop_id);
CREATE INDEX idx_common_sections_last_stop ON common_sections(last_stop_id);

-- Composite index for stop pair queries
CREATE INDEX idx_common_sections_stop_pair ON common_sections(first_stop_id, last_stop_id);

-- Spatial index for geographic queries (requires PostGIS)
CREATE INDEX idx_common_sections_geographic_extent
    ON common_sections USING GIST(geographic_extent);

COMMENT ON INDEX idx_common_sections_first_stop IS 'Find common sections starting at a specific stop';
COMMENT ON INDEX idx_common_sections_last_stop IS 'Find common sections ending at a specific stop';
COMMENT ON INDEX idx_common_sections_stop_pair IS 'Efficient lookup of sections by stop pair';
COMMENT ON INDEX idx_common_sections_geographic_extent IS 'Spatial index for geographic queries (PostGIS GIST)';

-- ============================================================================
-- COMMON SECTION VARIANTS TABLE INDEXES
-- ============================================================================

-- Index for finding variants in a common section
CREATE INDEX idx_common_section_variants_section
    ON common_section_variants(common_section_id);

-- Index for finding common sections for a variant
CREATE INDEX idx_common_section_variants_variant
    ON common_section_variants(variant_id);

-- Composite index for sequence-based queries
CREATE INDEX idx_common_section_variants_section_sequence
    ON common_section_variants(common_section_id, start_sequence, end_sequence);

COMMENT ON INDEX idx_common_section_variants_section IS 'Find all variants serving a common section';
COMMENT ON INDEX idx_common_section_variants_variant IS 'Find all common sections for a specific variant';
COMMENT ON INDEX idx_common_section_variants_section_sequence IS 'Sequence-based lookups within common sections';

-- ============================================================================
-- IMPORTED FEEDS TABLE INDEXES
-- ============================================================================

-- Index for finding imports by agency
CREATE INDEX idx_imported_feeds_agency_onestop_id ON imported_feeds(agency_onestop_id);

-- Index for filtering by import status
CREATE INDEX idx_imported_feeds_status ON imported_feeds(status);

-- Index for temporal queries (most recent imports first)
CREATE INDEX idx_imported_feeds_started_at ON imported_feeds(import_started_at DESC);

-- Composite index for agency + status queries
CREATE INDEX idx_imported_feeds_agency_status ON imported_feeds(agency_onestop_id, status);

-- Composite index for agency + date range queries
CREATE INDEX idx_imported_feeds_agency_date_range
    ON imported_feeds(agency_onestop_id, import_started_at DESC);

COMMENT ON INDEX idx_imported_feeds_agency_onestop_id IS 'Find all imports for a specific agency using Transitland onestop ID';
COMMENT ON INDEX idx_imported_feeds_status IS 'Filter imports by status (STARTED, IN_PROGRESS, COMPLETED, FAILED)';
COMMENT ON INDEX idx_imported_feeds_started_at IS 'Find most recent imports (descending order)';
COMMENT ON INDEX idx_imported_feeds_agency_status IS 'Filter imports by agency and status';
COMMENT ON INDEX idx_imported_feeds_agency_date_range IS 'Date range queries for agency imports';

-- ============================================================================
-- PERFORMANCE STATISTICS
-- ============================================================================

-- Analyze all tables to update query planner statistics
ANALYZE agencies;
ANALYZE routes;
ANALYZE route_variants;
ANALYZE frequencies;
ANALYZE common_sections;
ANALYZE common_section_variants;
ANALYZE imported_feeds;

-- ============================================================================
-- INDEX SUMMARY
-- ============================================================================

-- Total Indexes Created: 33
--   - agencies: 3 indexes
--   - routes: 4 indexes
--   - route_variants: 6 indexes
--   - frequencies: 5 indexes
--   - common_sections: 4 indexes
--   - common_section_variants: 3 indexes
--   - imported_feeds: 5 indexes
--
-- Index Types:
--   - B-tree indexes: 32 (standard for equality and range queries)
--   - GIST indexes: 1 (spatial index for PostGIS geometry)
--   - Partial indexes: 3 (active-only filters to reduce index size)
--   - Unique indexes: 1 (frequencies uniqueness constraint)
--
-- Performance Considerations:
--   - Partial indexes on 'active' columns save space by excluding inactive records
--   - Composite indexes support multi-column queries without separate index scans
--   - DESC indexes on timestamp columns optimize recent-first queries
--   - GIST index enables efficient spatial queries on geographic_extent column

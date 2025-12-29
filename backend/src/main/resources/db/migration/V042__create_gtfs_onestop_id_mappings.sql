-- GTFS to Transitland Onestop ID Mapping Cache
-- Version: 1.0.0
-- Feature: Cache Transit.land ID lookups for agencies, routes, and stops

CREATE TABLE gtfs_onestop_id_mappings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_onestop_id VARCHAR(512) NOT NULL REFERENCES feeds(feed_onestop_id) ON DELETE CASCADE,
    entity_type VARCHAR(20) NOT NULL,
    gtfs_id VARCHAR(255) NOT NULL,
    onestop_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_gtfs_onestop_id_mapping UNIQUE (feed_onestop_id, entity_type, gtfs_id),
    CONSTRAINT check_gtfs_onestop_entity_type CHECK (entity_type IN ('AGENCY', 'ROUTE', 'STOP'))
);

CREATE INDEX idx_gtfs_onestop_id_mappings_feed_type
    ON gtfs_onestop_id_mappings(feed_onestop_id, entity_type);

CREATE INDEX idx_gtfs_onestop_id_mappings_onestop_id
    ON gtfs_onestop_id_mappings(onestop_id);

COMMENT ON TABLE gtfs_onestop_id_mappings IS 'Cache of GTFS IDs to Transitland Onestop IDs by feed';
COMMENT ON COLUMN gtfs_onestop_id_mappings.feed_onestop_id IS 'Transitland feed onestop ID';
COMMENT ON COLUMN gtfs_onestop_id_mappings.entity_type IS 'Mapped entity type (AGENCY, ROUTE, STOP)';
COMMENT ON COLUMN gtfs_onestop_id_mappings.gtfs_id IS 'GTFS identifier from agency.txt, routes.txt, or stops.txt';
COMMENT ON COLUMN gtfs_onestop_id_mappings.onestop_id IS 'Transitland onestop ID';

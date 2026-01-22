-- Region Imports Table for Parent-Child Job Architecture
-- Version: 1.0.0
-- Constitutional Compliance: PostgreSQL, Spring Batch Integration, DDD Architecture
--
-- This migration adds tables for tracking region-level bulk imports as a parent job
-- that orchestrates multiple child feed import jobs.

-- Region import status enum - includes PARTIAL_SUCCESS for mixed results
CREATE TYPE region_import_status AS ENUM (
    'pending',
    'running',
    'completed',
    'partial_success',
    'failed',
    'cancelled'
);

-- Region imports table - tracks parent job orchestrating child feed imports
CREATE TABLE region_imports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region_onestop_id VARCHAR(512) NOT NULL,
    trigger_type import_trigger_type NOT NULL,
    status region_import_status NOT NULL DEFAULT 'pending',
    parent_job_execution_id BIGINT REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID),
    total_feeds INTEGER NOT NULL DEFAULT 0,
    started_count INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Junction table linking region imports to child feed imports
CREATE TABLE region_import_feeds (
    region_import_id UUID NOT NULL REFERENCES region_imports(id) ON DELETE CASCADE,
    feed_import_id UUID NOT NULL REFERENCES feed_imports(id) ON DELETE CASCADE,
    child_job_execution_id BIGINT REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID),
    sequence_number INTEGER NOT NULL,
    PRIMARY KEY (region_import_id, feed_import_id)
);

-- Performance indexes
CREATE INDEX idx_region_imports_region_onestop_id ON region_imports(region_onestop_id);
CREATE INDEX idx_region_imports_status ON region_imports(status);
CREATE INDEX idx_region_imports_created_at ON region_imports(created_at DESC);
CREATE INDEX idx_region_imports_trigger_type ON region_imports(trigger_type);
CREATE INDEX idx_region_imports_parent_job_execution_id ON region_imports(parent_job_execution_id);

CREATE INDEX idx_region_import_feeds_region_import_id ON region_import_feeds(region_import_id);
CREATE INDEX idx_region_import_feeds_feed_import_id ON region_import_feeds(feed_import_id);
CREATE INDEX idx_region_import_feeds_child_job_execution_id ON region_import_feeds(child_job_execution_id);

-- Unique constraint: Only one active (pending/running) region import per region at a time
-- Uses partial index for efficiency - allows multiple completed/failed/cancelled imports
CREATE UNIQUE INDEX idx_unique_active_region_import
    ON region_imports(region_onestop_id)
    WHERE status IN ('pending', 'running');

-- Comments for documentation
COMMENT ON TABLE region_imports IS 'Parent job tracking for region-level bulk imports orchestrating multiple feed imports';
COMMENT ON TABLE region_import_feeds IS 'Junction table linking region imports to child feed imports with sequencing';

COMMENT ON COLUMN region_imports.region_onestop_id IS 'Transit.land region identifier (e.g., r-9q8y-montreal)';
COMMENT ON COLUMN region_imports.parent_job_execution_id IS 'FK to Spring Batch job execution for the parent orchestration job';
COMMENT ON COLUMN region_imports.total_feeds IS 'Total number of feeds to import for this region';
COMMENT ON COLUMN region_imports.started_count IS 'Number of feed imports that have started';
COMMENT ON COLUMN region_imports.completed_count IS 'Number of feed imports that completed successfully';
COMMENT ON COLUMN region_imports.failed_count IS 'Number of feed imports that failed';
COMMENT ON COLUMN region_imports.skipped_count IS 'Number of feeds skipped (e.g., already importing)';

COMMENT ON COLUMN region_import_feeds.sequence_number IS 'Order in which feeds are processed within the region import';
COMMENT ON COLUMN region_import_feeds.child_job_execution_id IS 'FK to Spring Batch job execution for the child feed import job';

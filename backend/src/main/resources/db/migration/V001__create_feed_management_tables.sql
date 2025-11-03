-- Feed Management System Database Schema
-- Version: 1.0.0
-- Constitutional Compliance: PostgreSQL, Spring Boot, DDD Architecture

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Custom types for feed management domain
CREATE TYPE feed_spec_type AS ENUM ('gtfs', 'gtfs-rt');
CREATE TYPE feed_status AS ENUM ('active', 'inactive', 'error');
CREATE TYPE auth_type AS ENUM ('none', 'api_key', 'oauth2');
CREATE TYPE admin_role AS ENUM ('FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER');
CREATE TYPE import_trigger_type AS ENUM ('manual', 'automatic');
CREATE TYPE import_status AS ENUM ('pending', 'running', 'completed', 'failed', 'cancelled');
CREATE TYPE log_level AS ENUM ('info', 'warn', 'error');

-- Metropolitan regions table using Transit.land Onestop IDs
CREATE TABLE metropolitan_regions (
    region_onestop_id VARCHAR(255) PRIMARY KEY, -- e.g., "r-9q8y-montreal"
    name VARCHAR(255) NOT NULL,
    auto_update_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Feeds table using Transit.land Onestop IDs
CREATE TABLE feeds (
    feed_onestop_id VARCHAR(255) PRIMARY KEY, -- e.g., "f-f25d-socitdetransportdemontreal"
    region_onestop_id VARCHAR(255) NOT NULL REFERENCES metropolitan_regions(region_onestop_id),
    name VARCHAR(255) NOT NULL,
    spec_type feed_spec_type NOT NULL,
    download_url TEXT NOT NULL,
    current_version_sha1 VARCHAR(40), -- Transit.land SHA1 for change detection
    last_checked_at TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE,
    status feed_status NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Feed authentication table using Onestop ID as PK
CREATE TABLE feed_authentication (
    feed_onestop_id VARCHAR(255) PRIMARY KEY REFERENCES feeds(feed_onestop_id) ON DELETE CASCADE,
    auth_type auth_type NOT NULL DEFAULT 'none',
    encrypted_credentials TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Administrators table (UUIDs for user management)
CREATE TABLE administrators (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    role admin_role NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Feed imports table (UUIDs for unique import tracking)
-- Only persistent state - transient progress stored in Redis
CREATE TABLE feed_imports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_onestop_id VARCHAR(255) NOT NULL REFERENCES feeds(feed_onestop_id),
    administrator_id UUID REFERENCES administrators(id),
    trigger_type import_trigger_type NOT NULL,
    status import_status NOT NULL DEFAULT 'pending',
    version_sha1 VARCHAR(40), -- Transit.land SHA1 of imported version
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    file_size_bytes BIGINT,
    error_message TEXT, -- Error details if import failed
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Import logs table
CREATE TABLE import_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    import_id UUID NOT NULL REFERENCES feed_imports(id) ON DELETE CASCADE,
    level log_level NOT NULL,
    message TEXT NOT NULL,
    component VARCHAR(255),
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Performance indexes for efficient queries
CREATE INDEX idx_feeds_region_onestop_id ON feeds(region_onestop_id);
CREATE INDEX idx_feeds_status ON feeds(status);
CREATE INDEX idx_feeds_last_checked ON feeds(last_checked_at);
CREATE INDEX idx_feeds_spec_type ON feeds(spec_type);

CREATE INDEX idx_feed_imports_feed_onestop_id ON feed_imports(feed_onestop_id);
CREATE INDEX idx_feed_imports_status ON feed_imports(status);
CREATE INDEX idx_feed_imports_created_at ON feed_imports(created_at DESC);
CREATE INDEX idx_feed_imports_trigger_type ON feed_imports(trigger_type);

CREATE INDEX idx_import_logs_import_id ON import_logs(import_id);
CREATE INDEX idx_import_logs_level ON import_logs(level);
CREATE INDEX idx_import_logs_created_at ON import_logs(created_at DESC);

CREATE INDEX idx_administrators_role ON administrators(role);
CREATE INDEX idx_administrators_active ON administrators(active);

-- Add constraints for data integrity
ALTER TABLE metropolitan_regions ADD CONSTRAINT check_region_onestop_id_format
    CHECK (region_onestop_id ~ '^r-[0-9a-z]+-[a-z0-9\-]+$');

ALTER TABLE feeds ADD CONSTRAINT check_feed_onestop_id_format
    CHECK (feed_onestop_id ~ '^f-[0-9a-z]+(~[a-z]+)?-[a-z0-9\-]+$');

ALTER TABLE feeds ADD CONSTRAINT check_download_url_format
    CHECK (download_url ~ '^https?://');

ALTER TABLE feeds ADD CONSTRAINT check_sha1_format
    CHECK (current_version_sha1 IS NULL OR current_version_sha1 ~ '^[a-f0-9]{40}$');

ALTER TABLE feed_imports ADD CONSTRAINT check_version_sha1_format
    CHECK (version_sha1 IS NULL OR version_sha1 ~ '^[a-f0-9]{40}$');

-- Comments for documentation
COMMENT ON TABLE metropolitan_regions IS 'Geographic regions with available transit feeds from Transit.land';
COMMENT ON TABLE feeds IS 'Individual GTFS/GTFS-RT feeds from transit agencies';
COMMENT ON TABLE feed_authentication IS 'Authentication credentials for protected feeds';
COMMENT ON TABLE administrators IS 'Users with feed management permissions';
COMMENT ON TABLE feed_imports IS 'Persistent state for feed import operations';
COMMENT ON TABLE import_logs IS 'Detailed logs for import operations';

COMMENT ON COLUMN feeds.current_version_sha1 IS 'SHA1 from Transit.land API for change detection';
COMMENT ON COLUMN feed_imports.version_sha1 IS 'SHA1 of the specific version being imported';
COMMENT ON COLUMN feed_authentication.encrypted_credentials IS 'JSON credentials encrypted with application key';

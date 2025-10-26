-- Create feed authentication table
CREATE TABLE feed_authentication (
    feed_onestop_id VARCHAR(100) NOT NULL,
    auth_type VARCHAR(20) NOT NULL CHECK (auth_type IN ('NONE', 'BASIC', 'BEARER_TOKEN', 'API_KEY', 'OAUTH2', 'CERTIFICATE')),
    primary_credential TEXT,
    secondary_credential TEXT,
    auth_parameters TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_auth_success TIMESTAMP WITH TIME ZONE,
    last_auth_failure TIMESTAMP WITH TIME ZONE,
    failure_count INTEGER NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_feed_authentication PRIMARY KEY (feed_onestop_id),
    CONSTRAINT fk_feed_authentication_feed FOREIGN KEY (feed_onestop_id) REFERENCES feed(feed_onestop_id) ON DELETE CASCADE,
    CONSTRAINT chk_failure_count_non_negative CHECK (failure_count >= 0),
    CONSTRAINT chk_failure_count_max CHECK (failure_count <= 10)
);

-- Create indexes for performance
CREATE INDEX idx_feed_authentication_auth_type ON feed_authentication(auth_type) WHERE is_active = true;
CREATE INDEX idx_feed_authentication_expires_at ON feed_authentication(expires_at) WHERE is_active = true AND expires_at IS NOT NULL;
CREATE INDEX idx_feed_authentication_failure_count ON feed_authentication(failure_count) WHERE is_active = true;
CREATE INDEX idx_feed_authentication_last_auth_failure ON feed_authentication(last_auth_failure) WHERE is_active = true AND last_auth_failure IS NOT NULL;

-- Add trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_feed_authentication_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_feed_authentication_updated_at
    BEFORE UPDATE ON feed_authentication
    FOR EACH ROW
    EXECUTE FUNCTION update_feed_authentication_updated_at();

-- Add comments for documentation
COMMENT ON TABLE feed_authentication IS 'Stores encrypted authentication credentials for accessing protected GTFS feeds';
COMMENT ON COLUMN feed_authentication.feed_onestop_id IS 'Reference to the feed requiring authentication';
COMMENT ON COLUMN feed_authentication.auth_type IS 'Type of authentication: NONE, BASIC, BEARER_TOKEN, API_KEY, OAUTH2, CERTIFICATE';
COMMENT ON COLUMN feed_authentication.primary_credential IS 'Primary credential (username, token, api key) - encrypted';
COMMENT ON COLUMN feed_authentication.secondary_credential IS 'Secondary credential (password, client secret) - encrypted';
COMMENT ON COLUMN feed_authentication.auth_parameters IS 'Additional auth parameters as JSON - encrypted';
COMMENT ON COLUMN feed_authentication.is_active IS 'Whether authentication is currently active';
COMMENT ON COLUMN feed_authentication.expires_at IS 'When authentication expires (for tokens)';
COMMENT ON COLUMN feed_authentication.last_auth_success IS 'Last successful authentication timestamp';
COMMENT ON COLUMN feed_authentication.last_auth_failure IS 'Last authentication failure timestamp';
COMMENT ON COLUMN feed_authentication.failure_count IS 'Number of consecutive authentication failures';
COMMENT ON COLUMN feed_authentication.notes IS 'Additional notes about the authentication setup';
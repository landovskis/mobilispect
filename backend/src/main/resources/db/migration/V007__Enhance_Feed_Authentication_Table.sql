-- Enhance the feed authentication table with richer credential metadata and auditing columns
ALTER TABLE feed_authentication
    ADD COLUMN IF NOT EXISTS primary_credential TEXT,
    ADD COLUMN IF NOT EXISTS secondary_credential TEXT,
    ADD COLUMN IF NOT EXISTS auth_parameters TEXT,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS last_auth_success TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_auth_failure TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS failure_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS notes TEXT;

-- Preserve existing credentials by backfilling the new column when legacy data exists
UPDATE feed_authentication
SET primary_credential = encrypted_credentials
WHERE encrypted_credentials IS NOT NULL
  AND primary_credential IS NULL;

-- Enforce guardrails on the new failure tracking metrics
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.constraint_column_usage
        WHERE table_schema = 'public'
          AND table_name = 'feed_authentication'
          AND constraint_name = 'chk_feed_authentication_failure_count_non_negative'
    ) THEN
        ALTER TABLE feed_authentication
            ADD CONSTRAINT chk_feed_authentication_failure_count_non_negative
            CHECK (failure_count >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.constraint_column_usage
        WHERE table_schema = 'public'
          AND table_name = 'feed_authentication'
          AND constraint_name = 'chk_feed_authentication_failure_count_max'
    ) THEN
        ALTER TABLE feed_authentication
            ADD CONSTRAINT chk_feed_authentication_failure_count_max
            CHECK (failure_count <= 10);
    END IF;
END;
$$;

-- Create targeted indexes to support lookups on frequently queried state
CREATE INDEX IF NOT EXISTS idx_feed_authentication_auth_type_active
    ON feed_authentication(auth_type)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_feed_authentication_expires_at_active
    ON feed_authentication(expires_at)
    WHERE is_active = true
      AND expires_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_feed_authentication_failure_count_active
    ON feed_authentication(failure_count)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_feed_authentication_last_auth_failure_active
    ON feed_authentication(last_auth_failure)
    WHERE is_active = true
      AND last_auth_failure IS NOT NULL;

-- Keep the updated_at column in sync automatically
CREATE OR REPLACE FUNCTION update_feed_authentication_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'trigger_update_feed_authentication_updated_at'
          AND tgrelid = 'feed_authentication'::regclass
    ) THEN
        CREATE TRIGGER trigger_update_feed_authentication_updated_at
            BEFORE UPDATE ON feed_authentication
            FOR EACH ROW
            EXECUTE FUNCTION update_feed_authentication_updated_at();
    END IF;
END;
$$;

-- Documentation for new and existing columns
COMMENT ON TABLE feed_authentication IS 'Stores credential metadata required to access protected GTFS feeds';
COMMENT ON COLUMN feed_authentication.feed_onestop_id IS 'Transit.land feed Onestop ID requiring authentication';
COMMENT ON COLUMN feed_authentication.auth_type IS 'Authentication mechanism used by the feed';
COMMENT ON COLUMN feed_authentication.primary_credential IS 'Primary credential material (API key, username, bearer token) stored encrypted';
COMMENT ON COLUMN feed_authentication.secondary_credential IS 'Secondary credential material (password, client secret) stored encrypted';
COMMENT ON COLUMN feed_authentication.auth_parameters IS 'Additional authentication parameters encoded as JSON and encrypted';
COMMENT ON COLUMN feed_authentication.is_active IS 'Flag indicating whether this authentication record is currently in use';
COMMENT ON COLUMN feed_authentication.expires_at IS 'When the authentication material expires, if applicable';
COMMENT ON COLUMN feed_authentication.last_auth_success IS 'Timestamp of the most recent successful authentication';
COMMENT ON COLUMN feed_authentication.last_auth_failure IS 'Timestamp of the most recent failed authentication attempt';
COMMENT ON COLUMN feed_authentication.failure_count IS 'Number of consecutive authentication failures tracked for alerting';
COMMENT ON COLUMN feed_authentication.notes IS 'Operational notes about the authentication configuration';

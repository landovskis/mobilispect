-- Allow file:// URLs for testing and local development
-- Version: 1.0.0
-- Purpose: Update download_url constraint to allow file:// URLs for testing
--
-- The previous constraint only allowed http/https URLs, which prevented
-- using local file:// URLs for testing and development scenarios.
--
-- This migration updates the constraint to allow:
-- - http:// URLs (standard web downloads)
-- - https:// URLs (secure web downloads)
-- - file:// URLs (local filesystem for testing)
-- - Empty string (feeds without download URLs)

-- Drop the existing constraint
ALTER TABLE feeds DROP CONSTRAINT IF EXISTS check_download_url_if_present;

-- Add updated constraint allowing http(s) and file protocols
ALTER TABLE feeds ADD CONSTRAINT check_download_url_if_present
    CHECK (download_url = '' OR download_url ~ '^(https?|file)://');

-- Update comment
COMMENT ON CONSTRAINT check_download_url_if_present ON feeds IS
    'Download URL must be http(s) or file:// if provided, but can be empty string for feeds without URLs';

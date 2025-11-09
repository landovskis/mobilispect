-- Remove overly strict format constraints that reject valid Transit.land data
-- Version: 1.0.1
-- Purpose: Remove constraints that prevent valid Transit.land feeds from being saved
--
-- Transit.land feed IDs have highly variable formats:
-- Examples:
--   f-9q5-translink (standard ASCII)
--   f-dr5r-path~nj~us (multiple tildes)
--   f-xn4n-島田市 (Japanese characters)
--   f-u3z-klaipėdoskeleivinistransportas (Lithuanian with accents)
--   f-6fes-empresapublicadetransportesecirculação (Portuguese with accents)
--   f-ue5-linja~karjalaoy~jääskeläisenautooy~pohjolanliikenneoy~k... (extremely long with tildes)
--
-- Additionally, some feeds lack proper download URLs and constraint checking
-- prevents them from being saved even though they're valid Transit.land entries.
--
-- Solution: Remove overly strict regex constraints and rely on Transit.land API
-- as the source of truth for valid feed IDs and URLs.

-- Drop the overly strict feed_onestop_id constraint
ALTER TABLE feeds DROP CONSTRAINT IF EXISTS check_feed_onestop_id_format;

-- Drop the download_url format constraint (some feeds have no URL)
ALTER TABLE feeds DROP CONSTRAINT IF EXISTS check_download_url_format;

-- Add minimal validation: feed_onestop_id must start with "f-"
ALTER TABLE feeds ADD CONSTRAINT check_feed_onestop_id_prefix
    CHECK (feed_onestop_id LIKE 'f-%');

-- Add minimal validation: download_url must be http(s) IF NOT EMPTY
ALTER TABLE feeds ADD CONSTRAINT check_download_url_if_present
    CHECK (download_url = '' OR download_url ~ '^https?://');

-- Update comments
COMMENT ON CONSTRAINT check_feed_onestop_id_prefix ON feeds IS
    'Minimal validation: Transit.land feed IDs must start with f- prefix';
COMMENT ON CONSTRAINT check_download_url_if_present ON feeds IS
    'Download URL must be http(s) if provided, but can be empty string for feeds without URLs';

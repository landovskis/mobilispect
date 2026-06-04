-- migrations/016_feeds_discovery_columns.sql
-- Add columns needed for Transitland-discovered feeds.
-- name: human-readable feed name
-- transitland_onestop_id: unique Transitland feed ID (prevents duplicate discovery)
-- gtfs_api_key: optional GTFS-RT authentication key (configured post-setup)
-- timezone: IANA timezone (e.g. "America/Toronto"), replaces per-feed agency_utc_offset

ALTER TABLE feeds
    ADD COLUMN name                  TEXT,
    ADD COLUMN transitland_onestop_id TEXT UNIQUE,
    ADD COLUMN gtfs_api_key           TEXT,
    ADD COLUMN timezone               TEXT NOT NULL DEFAULT 'UTC';
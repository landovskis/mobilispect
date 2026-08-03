-- migrations/025_remix_region_tables.sql
-- Corridor Builder: a remix is a named draft of proposed street corridor
-- changes scoped to one metro region. Regions gain a bounding box for map
-- framing; corridors gain a remix_id association they didn't have before
-- (they previously lived in a flat global namespace). See
-- docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md.

ALTER TABLE regions
    ADD COLUMN min_lat DOUBLE PRECISION,
    ADD COLUMN min_lon DOUBLE PRECISION,
    ADD COLUMN max_lat DOUBLE PRECISION,
    ADD COLUMN max_lon DOUBLE PRECISION;

CREATE TABLE remixes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT NOT NULL CHECK (length(trim(name)) > 0),
    region_id   BIGINT NOT NULL REFERENCES regions(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_remixes_region ON remixes (region_id, updated_at DESC);

ALTER TABLE corridors
    ADD COLUMN remix_id BIGINT REFERENCES remixes(id);

CREATE INDEX idx_corridors_remix ON corridors (remix_id);

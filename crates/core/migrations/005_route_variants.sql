-- A variant is a unique ordered sequence of stops.
-- variant_id is the first 32 hex chars of SHA-256("{stop1_id},{stop2_id},...")
-- Intentionally excludes route_id and direction_id so the same physical stop
-- pattern gets the same variant_id regardless of how the agency numbers or
-- labels the route — enabling renaming/renumbering detection.
-- The primary variant is the one with the most trips for that (route_id, direction_id).

CREATE TABLE route_variants (
    agency_id    TEXT    NOT NULL,
    variant_id   TEXT    NOT NULL,
    route_id     TEXT    NOT NULL,
    direction_id BIGINT  NOT NULL DEFAULT 0,
    headsign     TEXT,
    stop_count   BIGINT  NOT NULL,
    trip_count   BIGINT  NOT NULL DEFAULT 0,
    is_primary   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (agency_id, route_id, direction_id, variant_id),
    FOREIGN KEY (agency_id, route_id) REFERENCES routes(agency_id, route_id)
);

CREATE INDEX idx_route_variants_route ON route_variants(agency_id, route_id, direction_id);
-- Allows cross-route lookup of the same physical pattern (renaming detection).
CREATE INDEX idx_route_variants_pattern ON route_variants(agency_id, variant_id);

-- Stop list for a pattern. No FK to route_variants: the stop sequence belongs to
-- the pattern (variant_id), not to any particular route.
CREATE TABLE route_variant_stops (
    agency_id     TEXT   NOT NULL,
    variant_id    TEXT   NOT NULL,
    stop_sequence BIGINT NOT NULL,
    stop_id       TEXT   NOT NULL,
    PRIMARY KEY (agency_id, variant_id, stop_sequence)
);

ALTER TABLE trips ADD COLUMN variant_id TEXT;

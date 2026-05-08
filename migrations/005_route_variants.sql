-- A variant is a unique ordered sequence of stops for a given route + direction.
-- variant_id is a hex-encoded SHA-256 of "{route_id}:{direction_id}:{stop1_id},{stop2_id},..."
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
    PRIMARY KEY (agency_id, variant_id),
    FOREIGN KEY (agency_id, route_id) REFERENCES routes(agency_id, route_id)
);

CREATE INDEX idx_route_variants_route ON route_variants(agency_id, route_id, direction_id);

CREATE TABLE route_variant_stops (
    agency_id     TEXT   NOT NULL,
    variant_id    TEXT   NOT NULL,
    stop_sequence BIGINT NOT NULL,
    stop_id       TEXT   NOT NULL,
    PRIMARY KEY (agency_id, variant_id, stop_sequence),
    FOREIGN KEY (agency_id, variant_id) REFERENCES route_variants(agency_id, variant_id)
);

ALTER TABLE trips ADD COLUMN variant_id TEXT;

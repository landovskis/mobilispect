-- migrations/011_canonical_entity_tables.sql
-- Replace agency_id/route_id keyed tables with Transitland Onestop ID keyed
-- canonical entity tables. Full destructive migration — re-ingest from scratch.

-- 1. Drop FK constraint from route_variants → routes (to allow dropping routes)
ALTER TABLE route_variants DROP CONSTRAINT IF EXISTS route_variants_agency_id_route_id_fkey;

-- 2. Drop old tables that are being replaced with Onestop-ID-keyed versions
DROP TABLE IF EXISTS routes CASCADE;
DROP TABLE IF EXISTS stops CASCADE;

-- 3. Create canonical entity tables

CREATE TABLE regions (
    id        BIGINT PRIMARY KEY,
    name      TEXT NOT NULL,
    timezone  TEXT NOT NULL
);

CREATE TABLE networks (
    id         BIGINT PRIMARY KEY,
    region_id  BIGINT NOT NULL REFERENCES regions(id),
    name       TEXT NOT NULL
);

CREATE TABLE feeds (
    id                              BIGINT PRIMARY KEY,
    gtfs_static_url                 TEXT NOT NULL,
    gtfs_rt_vehicle_positions_url   TEXT,
    gtfs_rt_trip_updates_url        TEXT,
    last_ingested_at                TIMESTAMPTZ,
    feed_hash                       TEXT,
    feed_version                    TEXT
);

CREATE TABLE network_feeds (
    network_id  BIGINT NOT NULL REFERENCES networks(id),
    feed_id     BIGINT NOT NULL REFERENCES feeds(id),
    PRIMARY KEY (network_id, feed_id)
);

CREATE TABLE agencies (
    onestop_id  TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    url         TEXT,
    timezone    TEXT,
    lang        TEXT,
    phone       TEXT
);

CREATE TABLE feed_agency_ids (
    feed_id         BIGINT NOT NULL REFERENCES feeds(id),
    gtfs_agency_id  TEXT NOT NULL,
    onestop_id      TEXT NOT NULL REFERENCES agencies(onestop_id),
    PRIMARY KEY (feed_id, gtfs_agency_id)
);

CREATE TABLE routes (
    onestop_id  TEXT PRIMARY KEY,
    agency_id   TEXT NOT NULL REFERENCES agencies(onestop_id),
    short_name  TEXT NOT NULL,
    long_name   TEXT NOT NULL,
    route_type  BIGINT NOT NULL
);

CREATE TABLE feed_route_ids (
    feed_id        BIGINT NOT NULL REFERENCES feeds(id),
    gtfs_route_id  TEXT NOT NULL,
    onestop_id     TEXT NOT NULL REFERENCES routes(onestop_id),
    PRIMARY KEY (feed_id, gtfs_route_id)
);

CREATE TABLE stations (
    onestop_id  TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lon         DOUBLE PRECISION NOT NULL
);

CREATE TABLE stops (
    onestop_id  TEXT PRIMARY KEY,
    station_id  TEXT REFERENCES stations(onestop_id),
    name        TEXT NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lon         DOUBLE PRECISION NOT NULL
);

CREATE TABLE feed_stop_ids (
    feed_id       BIGINT NOT NULL REFERENCES feeds(id),
    gtfs_stop_id  TEXT NOT NULL,
    onestop_id    TEXT NOT NULL REFERENCES stops(onestop_id),
    PRIMARY KEY (feed_id, gtfs_stop_id)
);

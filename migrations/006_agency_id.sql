-- Migration 006: Add agency_id to support multi-agency deployments.
--
-- SQLite cannot change PRIMARY KEY constraints via ALTER TABLE, and PRAGMA
-- foreign_keys cannot be disabled inside a transaction (sqlx wraps migrations
-- in transactions). Strategy: drop all affected tables in reverse-dependency
-- order, then recreate with the new schema.
--
-- Data loss notes:
--   • Static GTFS (routes/trips/stops/scheduled_stops): reloaded automatically
--     on next startup because the feed version key is cleared below.
--   • Real-time events (vehicle_positions, stop_time_events): fresh data
--     resumes within one poll cycle (~30 s).
--   • Computed metrics (trip_results, route_daily, route_speed*): recomputed
--     via /compute endpoint or next daily run.
--   • Benchmarks: untouched.

-- Drop tables in reverse dependency order (children before parents)
DROP TABLE IF EXISTS scheduled_stops;
DROP TABLE IF EXISTS stop_time_events;
DROP TABLE IF EXISTS vehicle_positions;
DROP TABLE IF EXISTS trip_results;
DROP TABLE IF EXISTS route_daily;
DROP TABLE IF EXISTS route_speed_daily;
DROP TABLE IF EXISTS route_speed;
DROP TABLE IF EXISTS trips;
DROP TABLE IF EXISTS stops;
DROP TABLE IF EXISTS routes;

-- Recreate static GTFS tables with agency_id in composite PKs

CREATE TABLE routes (
    agency_id       TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    short_name      TEXT NOT NULL,
    long_name       TEXT NOT NULL,
    route_type      INTEGER NOT NULL,
    PRIMARY KEY (agency_id, route_id)
);

CREATE TABLE trips (
    agency_id       TEXT NOT NULL,
    trip_id         TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    service_id      TEXT NOT NULL,
    direction_id    INTEGER,
    trip_headsign   TEXT,
    PRIMARY KEY (agency_id, trip_id)
);
CREATE INDEX idx_trips_route ON trips(agency_id, route_id);

CREATE TABLE stops (
    agency_id       TEXT NOT NULL,
    stop_id         TEXT NOT NULL,
    stop_name       TEXT NOT NULL,
    stop_lat        REAL NOT NULL,
    stop_lon        REAL NOT NULL,
    PRIMARY KEY (agency_id, stop_id)
);

CREATE TABLE scheduled_stops (
    agency_id       TEXT NOT NULL,
    trip_id         TEXT NOT NULL,
    stop_id         TEXT NOT NULL,
    stop_sequence   INTEGER NOT NULL,
    arrival_time    TEXT NOT NULL,
    departure_time  TEXT NOT NULL,
    PRIMARY KEY (agency_id, trip_id, stop_sequence)
);
CREATE INDEX idx_scheduled_stops_trip ON scheduled_stops(agency_id, trip_id);

-- Recreate real-time tables with agency_id

CREATE TABLE vehicle_positions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    agency_id       TEXT NOT NULL,
    observed_at     TEXT NOT NULL,
    trip_id         TEXT,
    vehicle_id      TEXT,
    latitude        REAL NOT NULL,
    longitude       REAL NOT NULL,
    bearing         REAL,
    speed           REAL,
    current_status  TEXT,
    stop_sequence   INTEGER
);
CREATE INDEX idx_vehicle_positions_trip    ON vehicle_positions(trip_id, observed_at);
CREATE INDEX idx_vehicle_positions_time    ON vehicle_positions(observed_at);
CREATE INDEX idx_vehicle_positions_agency  ON vehicle_positions(agency_id);

CREATE TABLE stop_time_events (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    agency_id           TEXT NOT NULL,
    observed_at         TEXT NOT NULL,
    trip_id             TEXT NOT NULL,
    stop_id             TEXT NOT NULL,
    stop_sequence       INTEGER,
    arrival_delay       INTEGER,
    departure_delay     INTEGER,
    arrival_time_unix   INTEGER,
    departure_time_unix INTEGER
);
CREATE INDEX idx_stop_time_events_trip    ON stop_time_events(trip_id, observed_at);
CREATE INDEX idx_stop_time_events_time    ON stop_time_events(observed_at);
CREATE INDEX idx_stop_time_events_agency  ON stop_time_events(agency_id);

-- Recreate computed metric tables with agency_id

CREATE TABLE trip_results (
    agency_id       TEXT NOT NULL,
    trip_id         TEXT NOT NULL,
    service_date    TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    on_time         INTEGER NOT NULL DEFAULT 0,
    avg_delay_secs  REAL,
    max_delay_secs  REAL,
    completed       INTEGER NOT NULL DEFAULT 0,
    computed_at     TEXT NOT NULL,
    PRIMARY KEY (agency_id, trip_id, service_date)
);

CREATE TABLE route_daily (
    agency_id       TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    service_date    TEXT NOT NULL,
    on_time_pct     REAL NOT NULL,
    avg_delay_secs  REAL,
    trips_run       INTEGER NOT NULL DEFAULT 0,
    trips_total     INTEGER NOT NULL DEFAULT 0,
    computed_at     TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, service_date)
);
CREATE INDEX idx_route_daily_date ON route_daily(service_date);

CREATE TABLE route_speed (
    agency_id            TEXT NOT NULL,
    route_id             TEXT NOT NULL,
    direction_id         INTEGER NOT NULL,
    scheduled_speed_mps  REAL NOT NULL,
    trip_count           INTEGER NOT NULL,
    computed_at          TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, direction_id)
);

CREATE TABLE route_speed_daily (
    agency_id         TEXT NOT NULL,
    route_id          TEXT NOT NULL,
    service_date      TEXT NOT NULL,
    direction_id      INTEGER NOT NULL,
    actual_speed_mps  REAL NOT NULL,
    trip_count        INTEGER NOT NULL,
    computed_at       TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, service_date, direction_id)
);
CREATE INDEX idx_route_speed_daily_date ON route_speed_daily(service_date);

-- Clear feed version so GTFS reloads with correct agency_id on next startup
DELETE FROM feed_info WHERE key = 'gtfs_static_version';

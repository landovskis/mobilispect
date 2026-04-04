-- Static GTFS tables (the "plan")

CREATE TABLE IF NOT EXISTS routes (
    route_id        TEXT PRIMARY KEY,
    short_name      TEXT NOT NULL,
    long_name       TEXT NOT NULL,
    route_type      INTEGER NOT NULL  -- 0=tram, 1=metro, 3=bus
);

CREATE TABLE IF NOT EXISTS trips (
    trip_id         TEXT PRIMARY KEY,
    route_id        TEXT NOT NULL REFERENCES routes(route_id),
    service_id      TEXT NOT NULL,
    direction_id    INTEGER,
    trip_headsign   TEXT
);

CREATE TABLE IF NOT EXISTS stops (
    stop_id         TEXT PRIMARY KEY,
    stop_name       TEXT NOT NULL,
    stop_lat        REAL NOT NULL,
    stop_lon        REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS scheduled_stops (
    trip_id         TEXT NOT NULL REFERENCES trips(trip_id),
    stop_id         TEXT NOT NULL REFERENCES stops(stop_id),
    stop_sequence   INTEGER NOT NULL,
    arrival_time    TEXT NOT NULL,  -- HH:MM:SS (may exceed 24h)
    departure_time  TEXT NOT NULL,
    PRIMARY KEY (trip_id, stop_sequence)
);

CREATE INDEX IF NOT EXISTS idx_scheduled_stops_trip ON scheduled_stops(trip_id);
CREATE INDEX IF NOT EXISTS idx_trips_route ON trips(route_id);

-- GTFS-RT tables (the "reality")

CREATE TABLE IF NOT EXISTS vehicle_positions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    observed_at     TEXT NOT NULL,  -- ISO 8601 UTC
    trip_id         TEXT,
    vehicle_id      TEXT,
    latitude        REAL NOT NULL,
    longitude       REAL NOT NULL,
    bearing         REAL,
    speed           REAL,
    current_status  TEXT,           -- IN_TRANSIT_TO, STOPPED_AT, INCOMING_AT
    stop_sequence   INTEGER
);

CREATE INDEX IF NOT EXISTS idx_vehicle_positions_trip ON vehicle_positions(trip_id, observed_at);
CREATE INDEX IF NOT EXISTS idx_vehicle_positions_time ON vehicle_positions(observed_at);

CREATE TABLE IF NOT EXISTS stop_time_events (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    observed_at     TEXT NOT NULL,
    trip_id         TEXT NOT NULL,
    stop_id         TEXT NOT NULL,
    stop_sequence   INTEGER,
    arrival_delay   INTEGER,        -- seconds; positive = late, negative = early
    departure_delay INTEGER
);

CREATE INDEX IF NOT EXISTS idx_stop_time_events_trip ON stop_time_events(trip_id, observed_at);
CREATE INDEX IF NOT EXISTS idx_stop_time_events_time ON stop_time_events(observed_at);

-- Performance tables (derived / computed)

CREATE TABLE IF NOT EXISTS trip_results (
    trip_id         TEXT NOT NULL,
    service_date    TEXT NOT NULL,  -- YYYY-MM-DD
    route_id        TEXT NOT NULL,
    on_time         INTEGER NOT NULL DEFAULT 0,  -- bool
    avg_delay_secs  REAL,
    max_delay_secs  REAL,
    completed       INTEGER NOT NULL DEFAULT 0,  -- bool
    computed_at     TEXT NOT NULL,
    PRIMARY KEY (trip_id, service_date)
);

CREATE TABLE IF NOT EXISTS route_daily (
    route_id        TEXT NOT NULL,
    service_date    TEXT NOT NULL,  -- YYYY-MM-DD
    on_time_pct     REAL NOT NULL,
    avg_delay_secs  REAL,
    trips_run       INTEGER NOT NULL DEFAULT 0,
    trips_total     INTEGER NOT NULL DEFAULT 0,
    computed_at     TEXT NOT NULL,
    PRIMARY KEY (route_id, service_date)
);

CREATE INDEX IF NOT EXISTS idx_route_daily_date ON route_daily(service_date);

-- Feed metadata

CREATE TABLE IF NOT EXISTS feed_info (
    key             TEXT PRIMARY KEY,
    value           TEXT NOT NULL,
    updated_at      TEXT NOT NULL
);

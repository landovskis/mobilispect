-- Static GTFS tables (the "plan")

CREATE TABLE routes (
    agency_id       TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    short_name      TEXT NOT NULL,
    long_name       TEXT NOT NULL,
    route_type      BIGINT NOT NULL,
    PRIMARY KEY (agency_id, route_id)
);

CREATE TABLE trips (
    agency_id       TEXT NOT NULL,
    trip_id         TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    service_id      TEXT NOT NULL,
    direction_id    BIGINT,
    trip_headsign   TEXT,
    PRIMARY KEY (agency_id, trip_id)
);
CREATE INDEX idx_trips_route ON trips(agency_id, route_id);

CREATE TABLE stops (
    agency_id       TEXT NOT NULL,
    stop_id         TEXT NOT NULL,
    stop_name       TEXT NOT NULL,
    stop_lat        DOUBLE PRECISION NOT NULL,
    stop_lon        DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (agency_id, stop_id)
);

CREATE TABLE scheduled_stops (
    agency_id       TEXT NOT NULL,
    trip_id         TEXT NOT NULL,
    stop_id         TEXT NOT NULL,
    stop_sequence   BIGINT NOT NULL,
    arrival_time    TEXT NOT NULL,
    departure_time  TEXT NOT NULL,
    PRIMARY KEY (agency_id, trip_id, stop_sequence)
);
CREATE INDEX idx_scheduled_stops_trip ON scheduled_stops(agency_id, trip_id);

-- GTFS-RT tables (the "reality")

CREATE TABLE vehicle_positions (
    id              BIGSERIAL PRIMARY KEY,
    agency_id       TEXT NOT NULL,
    observed_at     TEXT NOT NULL,
    trip_id         TEXT,
    vehicle_id      TEXT,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    bearing         DOUBLE PRECISION,
    speed           DOUBLE PRECISION,
    current_status  TEXT,
    stop_sequence   BIGINT
);
CREATE INDEX idx_vehicle_positions_trip   ON vehicle_positions(trip_id, observed_at);
CREATE INDEX idx_vehicle_positions_time   ON vehicle_positions(observed_at);
CREATE INDEX idx_vehicle_positions_agency ON vehicle_positions(agency_id);

CREATE TABLE stop_time_events (
    id                  BIGSERIAL PRIMARY KEY,
    agency_id           TEXT NOT NULL,
    observed_at         TEXT NOT NULL,
    trip_id             TEXT NOT NULL,
    stop_id             TEXT NOT NULL,
    stop_sequence       BIGINT,
    arrival_delay       BIGINT,
    departure_delay     BIGINT,
    arrival_time_unix   BIGINT,
    departure_time_unix BIGINT
);
CREATE INDEX idx_stop_time_events_trip   ON stop_time_events(trip_id, observed_at);
CREATE INDEX idx_stop_time_events_time   ON stop_time_events(observed_at);
CREATE INDEX idx_stop_time_events_agency ON stop_time_events(agency_id);

-- Performance tables (derived / computed)

CREATE TABLE trip_results (
    agency_id       TEXT NOT NULL,
    trip_id         TEXT NOT NULL,
    service_date    TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    on_time         BIGINT NOT NULL DEFAULT 0,
    avg_delay_secs  DOUBLE PRECISION,
    max_delay_secs  DOUBLE PRECISION,
    completed       BIGINT NOT NULL DEFAULT 0,
    computed_at     TEXT NOT NULL,
    PRIMARY KEY (agency_id, trip_id, service_date)
);

CREATE TABLE route_daily (
    agency_id       TEXT NOT NULL,
    route_id        TEXT NOT NULL,
    service_date    TEXT NOT NULL,
    on_time_pct     DOUBLE PRECISION NOT NULL,
    avg_delay_secs  DOUBLE PRECISION,
    trips_run       BIGINT NOT NULL DEFAULT 0,
    trips_total     BIGINT NOT NULL DEFAULT 0,
    computed_at     TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, service_date)
);
CREATE INDEX idx_route_daily_date ON route_daily(service_date);

CREATE TABLE route_speed (
    agency_id            TEXT NOT NULL,
    route_id             TEXT NOT NULL,
    direction_id         BIGINT NOT NULL,
    scheduled_speed_mps  DOUBLE PRECISION NOT NULL,
    trip_count           BIGINT NOT NULL,
    computed_at          TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, direction_id)
);

CREATE TABLE route_speed_daily (
    agency_id         TEXT NOT NULL,
    route_id          TEXT NOT NULL,
    service_date      TEXT NOT NULL,
    direction_id      BIGINT NOT NULL,
    actual_speed_mps  DOUBLE PRECISION NOT NULL,
    trip_count        BIGINT NOT NULL,
    computed_at       TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, service_date, direction_id)
);
CREATE INDEX idx_route_speed_daily_date ON route_speed_daily(service_date);

CREATE TABLE route_speed_hourly (
    agency_id         TEXT NOT NULL,
    route_id          TEXT NOT NULL,
    direction_id      BIGINT NOT NULL,
    hour_utc          TEXT NOT NULL,
    actual_speed_mps  DOUBLE PRECISION NOT NULL,
    trip_count        BIGINT NOT NULL,
    computed_at       TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, direction_id, hour_utc)
);
CREATE INDEX idx_route_speed_hourly_hour ON route_speed_hourly(hour_utc);

-- Feed metadata

CREATE TABLE feed_info (
    key         TEXT PRIMARY KEY,
    value       TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

-- Benchmarks

CREATE TABLE benchmarks (
    id                     BIGINT PRIMARY KEY,
    system_name            TEXT NOT NULL UNIQUE,
    city                   TEXT NOT NULL,
    on_time_pct            DOUBLE PRECISION NOT NULL,
    speed_vs_scheduled_pct DOUBLE PRECISION NOT NULL,
    source_url             TEXT NOT NULL,
    year                   INTEGER NOT NULL
);

INSERT INTO benchmarks (id, system_name, city, on_time_pct, speed_vs_scheduled_pct, source_url, year) VALUES
  (1, 'Helsinki (HSL)',          'Helsinki',  89.0, 3.0, 'https://www.hsl.fi/en/hsl/statistics-and-research', 2023),
  (2, 'Zurich (ZVV)',            'Zurich',    92.0, 1.8, 'https://www.zvv.ch/zvv/en/about-zvv/facts-and-figures.html', 2023),
  (3, 'Singapore (SBS Transit)', 'Singapore', 92.0, 2.0, 'https://www.lta.gov.sg/content/ltagov/en/getting_around/public_transport/bus.html', 2023),
  (4, 'Tokyo (Toei Bus)',        'Tokyo',     96.0, 1.5, 'https://www.kotsu.metro.tokyo.jp/eng/services/bus.html', 2023);
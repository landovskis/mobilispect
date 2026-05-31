-- migrations/012_partitioned_realtime_tables.sql
-- Introduce PostgreSQL enum types for GTFS-RT status fields, then recreate
-- vehicle_positions and stop_time_events as day-partitioned tables with
-- corrected schemas (TIMESTAMPTZ timestamps, typed enums, feed_id foreign key,
-- dwell_secs generated column). Old tables are dropped; data must be re-ingested.

-- Enum types

CREATE TYPE vehicle_stop_status AS ENUM ('INCOMING_AT', 'STOPPED_AT', 'IN_TRANSIT_TO');

CREATE TYPE occupancy_status_enum AS ENUM (
    'EMPTY', 'MANY_SEATS_AVAILABLE', 'FEW_SEATS_AVAILABLE', 'STANDING_ROOM_ONLY',
    'CRUSHED_STANDING_ROOM_ONLY', 'FULL', 'NOT_ACCEPTING_PASSENGERS',
    'NO_DATA_AVAILABLE', 'NOT_BOARDABLE'
);

CREATE TYPE congestion_level_enum AS ENUM (
    'UNKNOWN_CONGESTION_LEVEL', 'RUNNING_SMOOTHLY', 'STOP_AND_GO',
    'CONGESTION', 'SEVERE_CONGESTION'
);

CREATE TYPE stop_time_schedule_relationship AS ENUM (
    'SCHEDULED', 'SKIPPED', 'NO_DATA', 'UNSCHEDULED'
);

-- Drop existing tables (recreating as partitioned; old BIGSERIAL PK incompatible)

DROP TABLE IF EXISTS vehicle_positions;
DROP TABLE IF EXISTS stop_time_events;

-- vehicle_positions: partitioned by range on observed_at
-- No surrogate PK — append-only, never looked up by row ID.

CREATE TABLE vehicle_positions (
    feed_id           BIGINT NOT NULL,
    observed_at       TIMESTAMPTZ NOT NULL,
    trip_id           TEXT,
    vehicle_id        TEXT,
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    bearing           DOUBLE PRECISION,
    speed             DOUBLE PRECISION,
    current_status    vehicle_stop_status,
    stop_sequence     BIGINT,
    stop_id           TEXT REFERENCES stops(onestop_id),
    occupancy_status  occupancy_status_enum,
    congestion_level  congestion_level_enum
) PARTITION BY RANGE (observed_at);

CREATE INDEX idx_vehicle_positions_trip ON vehicle_positions (feed_id, trip_id, observed_at);
CREATE INDEX idx_vehicle_positions_time ON vehicle_positions (observed_at);

-- stop_time_events: partitioned by range on observed_at
-- dwell_secs is a STORED generated column; NULL inputs yield NULL (not an error).

CREATE TABLE stop_time_events (
    feed_id                BIGINT NOT NULL,
    observed_at            TIMESTAMPTZ NOT NULL,
    trip_id                TEXT NOT NULL,
    stop_id                TEXT REFERENCES stops(onestop_id),
    stop_sequence          BIGINT,
    arrival_delay          BIGINT,
    departure_delay        BIGINT,
    arrival_time           TIMESTAMPTZ,
    departure_time         TIMESTAMPTZ,
    dwell_secs             BIGINT GENERATED ALWAYS AS (
                               EXTRACT(EPOCH FROM (departure_time - arrival_time))::BIGINT
                           ) STORED,
    schedule_relationship  stop_time_schedule_relationship,
    uncertainty            INTEGER
) PARTITION BY RANGE (observed_at);

CREATE INDEX idx_stop_time_events_trip ON stop_time_events (feed_id, trip_id, observed_at);
CREATE INDEX idx_stop_time_events_time ON stop_time_events (observed_at);

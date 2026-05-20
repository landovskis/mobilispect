-- migrations/002_calendar.sql

CREATE TABLE calendar (
    agency_id   TEXT    NOT NULL,
    service_id  TEXT    NOT NULL,
    monday      BOOLEAN NOT NULL,
    tuesday     BOOLEAN NOT NULL,
    wednesday   BOOLEAN NOT NULL,
    thursday    BOOLEAN NOT NULL,
    friday      BOOLEAN NOT NULL,
    saturday    BOOLEAN NOT NULL,
    sunday      BOOLEAN NOT NULL,
    PRIMARY KEY (agency_id, service_id)
);

CREATE TABLE route_speed_day_type (
    agency_id            TEXT             NOT NULL,
    route_id             TEXT             NOT NULL,
    direction_id         BIGINT           NOT NULL,
    day_type             TEXT             NOT NULL,
    scheduled_speed_mps  DOUBLE PRECISION NOT NULL,
    trip_count           BIGINT           NOT NULL,
    computed_at          TEXT             NOT NULL,
    PRIMARY KEY (agency_id, route_id, direction_id, day_type)
);

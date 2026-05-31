-- migrations/015_computed_tables.sql
-- Update computed tables to use feed_id BIGINT, variant-level granularity,
-- and proper TIMESTAMPTZ types.
-- Covers: trip_results, route_daily_stats (new), route_speed, route_speed_hourly

-- ============================================================
-- trip_results: fix types, rename fields, add new fields
-- ============================================================
DROP INDEX IF EXISTS idx_route_daily_date;  -- from old route_daily (dropped in 010)

ALTER TABLE trip_results
    DROP COLUMN agency_id,
    DROP COLUMN route_id,              -- derivable via trip_id → variant → route
    ADD COLUMN feed_id BIGINT NOT NULL DEFAULT 0 REFERENCES feeds(id);

-- Rename columns
ALTER TABLE trip_results RENAME COLUMN on_time TO on_time_stops;
ALTER TABLE trip_results RENAME COLUMN completed TO observed_stops;

-- Add new columns
ALTER TABLE trip_results
    ADD COLUMN total_stops   BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN skipped_stops BIGINT NOT NULL DEFAULT 0;

-- Fix types
ALTER TABLE trip_results
    ALTER COLUMN service_date TYPE DATE        USING service_date::DATE,
    ALTER COLUMN computed_at  TYPE TIMESTAMPTZ USING computed_at::TIMESTAMPTZ;

-- Fix PK
ALTER TABLE trip_results DROP CONSTRAINT IF EXISTS trip_results_pkey;
ALTER TABLE trip_results ADD PRIMARY KEY (feed_id, trip_id, service_date);

-- ============================================================
-- route_daily_stats: new consolidated table
-- Replaces route_daily + route_speed_daily + route_speed_day_type
-- Grain: (feed_id, route_id, service_date, variant_id)
-- ============================================================
CREATE TABLE route_daily_stats (
    feed_id           BIGINT NOT NULL REFERENCES feeds(id),
    route_id          TEXT NOT NULL REFERENCES routes(onestop_id),
    service_date      DATE NOT NULL,
    variant_id        TEXT NOT NULL,
    on_time_stops     BIGINT NOT NULL DEFAULT 0,
    total_stops       BIGINT NOT NULL DEFAULT 0,
    skipped_stops     BIGINT NOT NULL DEFAULT 0,
    trips_run         BIGINT NOT NULL DEFAULT 0,
    trips_total       BIGINT NOT NULL DEFAULT 0,
    avg_delay_secs    DOUBLE PRECISION,
    max_delay_secs    DOUBLE PRECISION,
    actual_speed_mps  DOUBLE PRECISION,
    avg_dwell_secs    DOUBLE PRECISION,
    computed_at       TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (feed_id, route_id, service_date, variant_id)
);

CREATE INDEX idx_route_daily_stats_date ON route_daily_stats (service_date);

-- ============================================================
-- route_speed: replace direction_id with variant_id, fix timestamp
-- ============================================================
DROP TABLE IF EXISTS route_speed;

CREATE TABLE route_speed (
    feed_id               BIGINT NOT NULL REFERENCES feeds(id),
    route_id              TEXT NOT NULL REFERENCES routes(onestop_id),
    variant_id            TEXT NOT NULL,
    scheduled_speed_mps   DOUBLE PRECISION NOT NULL,
    avg_stop_spacing_m    DOUBLE PRECISION,
    trip_count            BIGINT NOT NULL,
    computed_at           TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (feed_id, route_id, variant_id)
);

-- ============================================================
-- route_speed_hourly: replace direction_id with variant_id, fix types
-- ============================================================
DROP TABLE IF EXISTS route_speed_hourly;

CREATE TABLE route_speed_hourly (
    feed_id           BIGINT NOT NULL REFERENCES feeds(id),
    route_id          TEXT NOT NULL REFERENCES routes(onestop_id),
    variant_id        TEXT NOT NULL,
    hour              TIMESTAMPTZ NOT NULL,
    actual_speed_mps  DOUBLE PRECISION NOT NULL,
    avg_dwell_secs    DOUBLE PRECISION,
    trip_count        BIGINT NOT NULL,
    computed_at       TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (feed_id, route_id, variant_id, hour)
);

CREATE INDEX idx_route_speed_hourly_hour ON route_speed_hourly (hour);

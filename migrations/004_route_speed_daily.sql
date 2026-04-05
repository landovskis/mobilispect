-- Actual average speed per route per direction per service date (derived / computed)

CREATE TABLE IF NOT EXISTS route_speed_daily (
    route_id          TEXT NOT NULL,
    service_date      TEXT NOT NULL,  -- YYYY-MM-DD
    direction_id      INTEGER NOT NULL,
    actual_speed_mps  REAL NOT NULL,  -- meters per second, averaged across trips
    trip_count        INTEGER NOT NULL,
    computed_at       TEXT NOT NULL,
    PRIMARY KEY (route_id, service_date, direction_id)
);

CREATE INDEX IF NOT EXISTS idx_route_speed_daily_date ON route_speed_daily(service_date);

-- Actual average speed per route per direction per UTC hour, computed at import time.

CREATE TABLE route_speed_hourly (
    agency_id         TEXT NOT NULL,
    route_id          TEXT NOT NULL,
    direction_id      INTEGER NOT NULL,
    hour_utc          TEXT NOT NULL,  -- 'YYYY-MM-DD HH' (UTC)
    actual_speed_mps  REAL NOT NULL,
    trip_count        INTEGER NOT NULL,
    computed_at       TEXT NOT NULL,
    PRIMARY KEY (agency_id, route_id, direction_id, hour_utc)
);
CREATE INDEX idx_route_speed_hourly_hour ON route_speed_hourly(hour_utc);
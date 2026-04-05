-- Scheduled average speed per route per direction (static, recomputed on GTFS reload)

CREATE TABLE IF NOT EXISTS route_speed (
    route_id             TEXT NOT NULL,
    direction_id         INTEGER NOT NULL,  -- 0=outbound, 1=inbound
    scheduled_speed_mps  REAL NOT NULL,     -- meters per second
    trip_count           INTEGER NOT NULL,
    computed_at          TEXT NOT NULL,
    PRIMARY KEY (route_id, direction_id)
);

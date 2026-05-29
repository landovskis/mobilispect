# Spec: Data Model Entity Refactor

## Summary

Full data model refactor based on entity-by-entity domain review. Introduces canonical
Transitland Onestop IDs for agencies, routes, and stops; promotes Region, Network, Feed,
and Agency to first-class DB entities; normalises the Route → Variant → Trip hierarchy;
consolidates computed daily stats; fixes TEXT timestamps to TIMESTAMPTZ; partitions RT
tables by day; adds missing GTFS-RT fields; and removes Benchmark and FeedInfo.

## Domain Context

- **Bounded contexts:** Feed Ingestion, Schedule, Performance, Reporting (all touched)
- **Aggregates touched:** Agency (promoted to DB, Onestop ID), Route (Onestop ID, no feed
  partition), Trip (variant_id NOT NULL, route_id dropped), RouteDailyMetrics (consolidated
  into route_daily_stats), Stop (canonical Onestop entity), Station (new)
- **New ubiquitous language terms:** Region, Network, Feed, Station, Onestop ID
  (see `docs/ddd/ubiquitous-language.md`)

---

## Final Schema

### Canonical entity tables (no feed_id partition)

```sql
-- Geographic area served by one or more networks.
CREATE TABLE regions (
    id        BIGINT PRIMARY KEY,  -- config-assigned, matches region index in config.toml
    name      TEXT NOT NULL,
    timezone  TEXT NOT NULL        -- IANA e.g. 'America/Toronto'
);

-- Transit network serving a region. A network is built from one or more feeds.
CREATE TABLE networks (
    id         BIGINT PRIMARY KEY,  -- config-assigned
    region_id  BIGINT NOT NULL REFERENCES regions(id),
    name       TEXT NOT NULL
);

-- A single GTFS data source (one zip URL). May be shared across networks.
CREATE TABLE feeds (
    id                            BIGINT PRIMARY KEY,  -- config-assigned (was AgencyConfig.id)
    gtfs_static_url               TEXT NOT NULL,
    gtfs_rt_vehicle_positions_url TEXT,
    gtfs_rt_trip_updates_url      TEXT,
    last_ingested_at              TIMESTAMPTZ,
    feed_hash                     TEXT,   -- SHA-256 of last ingested zip for change detection
    feed_version                  TEXT    -- from GTFS feed_info.txt if present
    -- API keys stay in config only, never persisted
);

-- Many-to-many: a network draws from one or more feeds; a feed may serve multiple networks.
CREATE TABLE network_feeds (
    network_id  BIGINT NOT NULL REFERENCES networks(id),
    feed_id     BIGINT NOT NULL REFERENCES feeds(id),
    PRIMARY KEY (network_id, feed_id)
);

-- Planning authority responsible for routes. Ingested from agency.txt.
-- De-duplicated across feeds via Transitland operator Onestop ID.
CREATE TABLE agencies (
    onestop_id  TEXT PRIMARY KEY,  -- e.g. o-f25d-societédetransportdemontréal
    name        TEXT NOT NULL,
    url         TEXT,
    timezone    TEXT,              -- IANA timezone from agency.txt
    lang        TEXT,
    phone       TEXT
);

-- Maps a GTFS agency_id within a feed to a canonical agency Onestop ID.
-- No Transitland match → row not inserted; dependent entities skipped.
CREATE TABLE feed_agency_ids (
    feed_id         BIGINT NOT NULL REFERENCES feeds(id),
    gtfs_agency_id  TEXT NOT NULL,
    onestop_id      TEXT NOT NULL REFERENCES agencies(onestop_id),
    PRIMARY KEY (feed_id, gtfs_agency_id)
);

-- Named route corridor. De-duplicated across feeds via Transitland route Onestop ID.
CREATE TABLE routes (
    onestop_id  TEXT PRIMARY KEY,  -- e.g. r-f25e-14
    agency_id   TEXT NOT NULL REFERENCES agencies(onestop_id),
    short_name  TEXT NOT NULL,
    long_name   TEXT NOT NULL,
    route_type  BIGINT NOT NULL
);

-- Maps a GTFS route_id within a feed to a canonical route Onestop ID.
CREATE TABLE feed_route_ids (
    feed_id        BIGINT NOT NULL REFERENCES feeds(id),
    gtfs_route_id  TEXT NOT NULL,
    onestop_id     TEXT NOT NULL REFERENCES routes(onestop_id),
    PRIMARY KEY (feed_id, gtfs_route_id)
);

-- Named interchange location containing one or more stops (GTFS location_type=1).
CREATE TABLE stations (
    onestop_id  TEXT PRIMARY KEY,  -- Transitland stop Onestop ID
    name        TEXT NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lon         DOUBLE PRECISION NOT NULL
);

-- Boarding/alighting location (GTFS location_type=0).
-- De-duplicated across feeds via Transitland stop Onestop ID.
CREATE TABLE stops (
    onestop_id  TEXT PRIMARY KEY,  -- Transitland stop Onestop ID
    station_id  TEXT REFERENCES stations(onestop_id),  -- nullable
    name        TEXT NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lon         DOUBLE PRECISION NOT NULL
);

-- Maps a GTFS stop_id within a feed to a canonical stop Onestop ID.
-- No Transitland match → row not inserted; dependent entities skipped.
CREATE TABLE feed_stop_ids (
    feed_id       BIGINT NOT NULL REFERENCES feeds(id),
    gtfs_stop_id  TEXT NOT NULL,
    onestop_id    TEXT NOT NULL REFERENCES stops(onestop_id),
    PRIMARY KEY (feed_id, gtfs_stop_id)
);
```

### Feed-scoped timetable tables (feed_id partition key)

```sql
-- Stop-sequence pattern for a route direction. variant_id = SHA-256(onestop_stop_ids)[0:32].
CREATE TABLE route_variants (
    feed_id      BIGINT NOT NULL REFERENCES feeds(id),
    variant_id   TEXT NOT NULL,
    route_id     TEXT NOT NULL REFERENCES routes(onestop_id),
    direction_id BIGINT NOT NULL DEFAULT 0,  -- stored for convenience; derivable from stop sequence
    headsign     TEXT,
    stop_count   BIGINT NOT NULL,
    trip_count   BIGINT NOT NULL DEFAULT 0,
    is_primary   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (feed_id, variant_id)
);

CREATE TABLE route_variant_stops (
    feed_id        BIGINT NOT NULL REFERENCES feeds(id),
    variant_id     TEXT NOT NULL,
    stop_sequence  BIGINT NOT NULL,
    stop_id        TEXT NOT NULL REFERENCES stops(onestop_id),
    PRIMARY KEY (feed_id, variant_id, stop_sequence),
    FOREIGN KEY (feed_id, variant_id) REFERENCES route_variants(feed_id, variant_id)
);

-- A single scheduled run. Belongs to a variant (not directly to a route).
CREATE TABLE trips (
    feed_id        BIGINT NOT NULL REFERENCES feeds(id),
    trip_id        TEXT NOT NULL,
    variant_id     TEXT NOT NULL,                  -- NOT NULL (was nullable)
    service_id     TEXT NOT NULL,
    trip_headsign  TEXT,
    -- route_id DROPPED: derivable via variant_id → route_variants.route_id
    -- direction_id DROPPED: derivable via variant_id → route_variants.direction_id
    PRIMARY KEY (feed_id, trip_id),
    FOREIGN KEY (feed_id, variant_id) REFERENCES route_variants(feed_id, variant_id)
);

CREATE INDEX idx_trips_variant ON trips (feed_id, variant_id);

-- Scheduled timetable for a trip. stop_id kept (deliberate denorm for RT join performance).
CREATE TABLE scheduled_stops (
    feed_id        BIGINT NOT NULL REFERENCES feeds(id),
    trip_id        TEXT NOT NULL,
    stop_id        TEXT NOT NULL REFERENCES stops(onestop_id),  -- denorm: also in route_variant_stops
    stop_sequence  BIGINT NOT NULL,
    arrival_time   TEXT NOT NULL,   -- HH:MM:SS (GTFS seconds-since-midnight convention)
    departure_time TEXT NOT NULL,
    PRIMARY KEY (feed_id, trip_id, stop_sequence),
    FOREIGN KEY (feed_id, trip_id) REFERENCES trips(feed_id, trip_id)
);

CREATE INDEX idx_scheduled_stops_trip ON scheduled_stops (feed_id, trip_id);

-- Service calendar (from calendar.txt). Belongs to an agency within a feed.
CREATE TABLE services (
    feed_id     BIGINT NOT NULL REFERENCES feeds(id),
    agency_id   TEXT NOT NULL REFERENCES agencies(onestop_id),
    service_id  TEXT NOT NULL,
    monday      BOOLEAN NOT NULL,
    tuesday     BOOLEAN NOT NULL,
    wednesday   BOOLEAN NOT NULL,
    thursday    BOOLEAN NOT NULL,
    friday      BOOLEAN NOT NULL,
    saturday    BOOLEAN NOT NULL,
    sunday      BOOLEAN NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    PRIMARY KEY (feed_id, service_id)
);

-- Calendar exceptions (from calendar_dates.txt).
-- exception_type: 1 = service added, 2 = service removed.
-- Rows with exception_type=2 must be excluded from trips_total in on-time computation.
CREATE TABLE service_exceptions (
    feed_id         BIGINT NOT NULL REFERENCES feeds(id),
    service_id      TEXT NOT NULL,
    date            DATE NOT NULL,
    exception_type  SMALLINT NOT NULL,
    PRIMARY KEY (feed_id, service_id, date)
);
```

### Real-time tables (feed_id, partitioned by day)

```sql
CREATE TYPE vehicle_stop_status AS ENUM ('INCOMING_AT', 'STOPPED_AT', 'IN_TRANSIT_TO');
CREATE TYPE occupancy_status AS ENUM (
    'EMPTY', 'MANY_SEATS_AVAILABLE', 'FEW_SEATS_AVAILABLE', 'STANDING_ROOM_ONLY',
    'CRUSHED_STANDING_ROOM_ONLY', 'FULL', 'NOT_ACCEPTING_PASSENGERS',
    'NO_DATA_AVAILABLE', 'NOT_BOARDABLE'
);
CREATE TYPE congestion_level AS ENUM (
    'UNKNOWN_CONGESTION_LEVEL', 'RUNNING_SMOOTHLY', 'STOP_AND_GO',
    'CONGESTION', 'SEVERE_CONGESTION'
);
CREATE TYPE stop_time_schedule_relationship AS ENUM (
    'SCHEDULED', 'SKIPPED', 'NO_DATA', 'UNSCHEDULED'
);

-- No surrogate PK: append-only, never looked up by row ID.
-- Partition by day: CREATE TABLE vehicle_positions ... PARTITION BY RANGE (observed_at)
CREATE TABLE vehicle_positions (
    feed_id           BIGINT NOT NULL,
    observed_at       TIMESTAMPTZ NOT NULL,       -- was TEXT
    trip_id           TEXT,                        -- nullable: vehicle may not yet be assigned
    vehicle_id        TEXT,
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    bearing           DOUBLE PRECISION,
    speed             DOUBLE PRECISION,
    current_status    vehicle_stop_status,         -- was TEXT
    stop_sequence     BIGINT,
    stop_id           TEXT REFERENCES stops(onestop_id),  -- added; resolved via feed_stop_ids
    occupancy_status  occupancy_status,            -- added
    congestion_level  congestion_level             -- added
) PARTITION BY RANGE (observed_at);

CREATE INDEX idx_vehicle_positions_trip ON vehicle_positions (feed_id, trip_id, observed_at);
CREATE INDEX idx_vehicle_positions_time ON vehicle_positions (observed_at);

CREATE TABLE stop_time_events (
    feed_id                BIGINT NOT NULL,
    observed_at            TIMESTAMPTZ NOT NULL,      -- was TEXT
    trip_id                TEXT NOT NULL,
    stop_id                TEXT REFERENCES stops(onestop_id),  -- resolved via feed_stop_ids
    stop_sequence          BIGINT,
    arrival_delay          BIGINT,                    -- seconds
    departure_delay        BIGINT,                    -- seconds
    arrival_time           TIMESTAMPTZ,               -- was arrival_time_unix BIGINT
    departure_time         TIMESTAMPTZ,               -- was departure_time_unix BIGINT
    dwell_secs             BIGINT GENERATED ALWAYS AS (
                               EXTRACT(EPOCH FROM (departure_time - arrival_time))::BIGINT
                           ) STORED,
    schedule_relationship  stop_time_schedule_relationship,  -- added
    uncertainty            INTEGER                    -- added; seconds
) PARTITION BY RANGE (observed_at);

CREATE INDEX idx_stop_time_events_trip ON stop_time_events (feed_id, trip_id, observed_at);
CREATE INDEX idx_stop_time_events_time ON stop_time_events (observed_at);
```

### Computed tables

```sql
-- Raw per-trip outcome. Insert-only (no UPDATE path in worker).
CREATE TABLE trip_results (
    feed_id          BIGINT NOT NULL,
    trip_id          TEXT NOT NULL,
    service_date     DATE NOT NULL,          -- was TEXT
    on_time_stops    BIGINT NOT NULL DEFAULT 0,  -- was on_time; count of stops within threshold
    observed_stops   BIGINT NOT NULL DEFAULT 0,  -- was completed; count of stops with RT data
    total_stops      BIGINT NOT NULL DEFAULT 0,  -- added; total scheduled stops
    skipped_stops    BIGINT NOT NULL DEFAULT 0,  -- added; stops with schedule_relationship=SKIPPED
    avg_delay_secs   DOUBLE PRECISION,
    max_delay_secs   DOUBLE PRECISION,
    computed_at      TIMESTAMPTZ NOT NULL,    -- was TEXT
    -- route_id DROPPED: derivable via trip_id → variant → route
    PRIMARY KEY (feed_id, trip_id, service_date)
);

-- Pre-aggregated daily metrics at variant grain.
-- Replaces: route_daily + route_speed_daily + route_speed_day_type.
-- Direction is derivable via variant_id → route_variants.direction_id.
-- Route-level rollups are computed at query time by aggregating over variant_id.
-- Insert-only (no UPDATE path).
CREATE TABLE route_daily_stats (
    feed_id           BIGINT NOT NULL,
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

-- Scheduled speed and stop spacing per variant. Recomputed on feed ingest.
CREATE TABLE route_speed (
    feed_id               BIGINT NOT NULL,
    route_id              TEXT NOT NULL REFERENCES routes(onestop_id),
    variant_id            TEXT NOT NULL,    -- was direction_id
    scheduled_speed_mps   DOUBLE PRECISION NOT NULL,
    avg_stop_spacing_m    DOUBLE PRECISION,
    trip_count            BIGINT NOT NULL,
    computed_at           TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (feed_id, route_id, variant_id)
);

-- Actual speed aggregated by hour and variant.
CREATE TABLE route_speed_hourly (
    feed_id           BIGINT NOT NULL,
    route_id          TEXT NOT NULL REFERENCES routes(onestop_id),
    variant_id        TEXT NOT NULL,        -- was direction_id
    hour              TIMESTAMPTZ NOT NULL, -- was hour_utc TEXT; truncated to hour
    actual_speed_mps  DOUBLE PRECISION NOT NULL,
    avg_dwell_secs    DOUBLE PRECISION,     -- added
    trip_count        BIGINT NOT NULL,
    computed_at       TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (feed_id, route_id, variant_id, hour)
);

CREATE INDEX idx_route_speed_hourly_hour ON route_speed_hourly (hour);
```

### Dropped tables

| Table | Reason |
|---|---|
| `benchmarks` | Feature removed |
| `feed_info` | Ingest metadata moved to `feeds.last_ingested_at` / `feed_hash` / `feed_version` |
| `route_daily` | Replaced by `route_daily_stats` |
| `route_speed_daily` | Replaced by `route_daily_stats` |
| `route_speed_day_type` | Derivable at query time from `route_daily_stats` grouped by day-of-week |

---

## ID Newtypes

| Old | New | Inner type | Notes |
|---|---|---|---|
| `AgencyId(String)` | `FeedId(i64)` | `i64` | Was the feed partition key; matched config `id: u32` |
| *(new)* | `AgencyId(String)` | `String` | Transitland operator Onestop ID |
| `RouteId(String)` | `RouteId(String)` | `String` | Now a Transitland route Onestop ID |
| `StopId(String)` | `StopId(String)` | `String` | Now a Transitland stop Onestop ID |
| *(new)* | `StationId(String)` | `String` | Transitland stop Onestop ID (location_type=1) |
| *(new)* | `RegionId(i64)` | `i64` | Config-assigned |
| *(new)* | `NetworkId(i64)` | `i64` | Config-assigned |
| `VariantId(String)` | `VariantId(String)` | `String` | SHA-256 now over Onestop stop IDs (canonical) |

`DirectionId`, `TripId`, `ServiceId`, `VehicleId` unchanged.

---

## Config Changes

`AgencyConfig` → `FeedConfig`. Fields renamed; `agency_utc_offset` removed (timezone from
`agencies.timezone` ingested via agency.txt, falls back to `RegionConfig.timezone`).

```toml
[region]
name = "Montreal"
timezone = "America/Toronto"

[[region.networks]]
id = 0
name = "Montreal Transit"

[[region.networks.feeds]]
id = 0
name = "STM"
gtfs_static_url = "..."
gtfs_rt_vehicle_positions_url = "..."
gtfs_rt_trip_updates_url = "..."
gtfs_api_key_env = "STM_GTFS_RT_API_KEY"
```

On startup, the worker upserts Region → Network → Feed rows from config before ingesting.

---

## Worker Ingest Flow (updated)

Three Transitland resolution steps now precede any domain data write:

```
1. Resolve agencies:  gtfs_agency_id → agencies.onestop_id  (via Transitland /agencies API)
                      insert into feed_agency_ids
                      skip feeds with unresolvable agencies

2. Resolve routes:    gtfs_route_id  → routes.onestop_id    (via Transitland /routes API)
                      insert into feed_route_ids
                      skip routes with unresolvable agency

3. Resolve stops:     gtfs_stop_id   → stops.onestop_id     (via Transitland /stops API)
                      insert feed_stop_ids; also resolve station if parent_station present
                      skip stops with no Transitland match
```

Downstream entities (variants, trips, scheduled_stops, stop_time_events) reference canonical
Onestop IDs. Any row referencing a skipped stop or route is also skipped.

`VariantId` computation changes: SHA-256 is now over the ordered canonical `stops.onestop_id`
values (not GTFS stop_id strings). Existing variant_id values are invalidated — a full
re-ingest is required (see Migration Strategy).

---

## ACL Updates

Transitland is a new external source. All Transitland API calls happen exclusively in
`crates/worker/src/transitland/`. No `reqwest` calls to Transitland may appear in
`crates/core/` or `crates/server/`.

Translation boundary:
- Transitland JSON response fields → domain Onestop ID newtypes at the boundary
- `onestop_id: String` from JSON → `AgencyId` / `RouteId` / `StopId` / `StationId`

Document in `docs/ddd/acl.md`.

---

## Migration Strategy

This is a breaking schema change. Existing data cannot be migrated in place because:
1. `agency_id TEXT` (e.g. "0") → `feed_id BIGINT` (e.g. 0) across all tables
2. Canonical entity IDs (routes, stops) change from GTFS strings to Onestop IDs
3. `variant_id` values change (SHA-256 input changes from GTFS stop_ids to Onestop IDs)
4. Multiple tables consolidated or dropped

**Approach:** destructive migration followed by full re-ingest.

SQL migrations:
1. Drop `benchmarks`, `feed_info`, `route_daily`, `route_speed_daily`, `route_speed_day_type`
2. Drop existing `vehicle_positions`, `stop_time_events` (recreate partitioned)
3. Create `regions`, `networks`, `network_feeds`, `feeds`
4. Create `agencies`, `feed_agency_ids`
5. Recreate `routes` (Onestop PK), `feed_route_ids`
6. Recreate `stops` (Onestop PK), `stations`, `feed_stop_ids`
7. Alter `route_variants`: `agency_id TEXT` → `feed_id BIGINT`, `route_id` → Onestop FK
8. Alter `route_variant_stops`: `agency_id` → `feed_id`, `stop_id` → Onestop FK
9. Alter `trips`: `agency_id` → `feed_id`, drop `route_id`, make `variant_id` NOT NULL
10. Alter `scheduled_stops`: `agency_id` → `feed_id`, `stop_id` → Onestop FK
11. Alter `services` (was `calendar`): `agency_id` → `feed_id` + agency FK, add `start_date`/`end_date`
12. Create `service_exceptions`
13. Alter `trip_results`: fix types, rename fields, add `total_stops`/`skipped_stops`
14. Create `route_daily_stats`, `route_speed` (updated), `route_speed_hourly` (updated)
15. Recreate `vehicle_positions`, `stop_time_events` (partitioned, new columns)

After migrations, re-ingest all feeds from scratch via the worker.

---

## Task List

Execute using `superpowers:subagent-driven-development`. One subagent per task.

### Phase 1 — DDD & Config
- [ ] T1: Update `docs/ddd/ubiquitous-language.md`, `aggregate-specs.md`, `acl.md`, `bounded-context-canvas.md`
- [ ] T2: Update `config.rs`: `AgencyConfig` → `FeedConfig`, `RegionConfig` gains networks, startup upsert logic

### Phase 2 — ID Newtypes
- [ ] T3: Update `crates/core/src/ids.rs`: rename `AgencyId` → `FeedId`, add new `AgencyId`/`StationId`/`RegionId`/`NetworkId`, update tests

### Phase 3 — Migrations (in order)
- [ ] T4: Drop obsolete tables (`benchmarks`, `feed_info`, `route_daily`, `route_speed_daily`, `route_speed_day_type`)
- [ ] T5: Create canonical entity tables (`regions`, `networks`, `network_feeds`, `feeds`, `agencies`, `feed_agency_ids`, `stations`, updated `stops`, `feed_stop_ids`, updated `routes`, `feed_route_ids`)
- [ ] T6: Create enum types; recreate `vehicle_positions` and `stop_time_events` as partitioned tables with new schema
- [ ] T7: Alter timetable tables (`route_variants`, `route_variant_stops`, `trips`, `scheduled_stops`)
- [ ] T8: Alter/create service tables (`services` updated, `service_exceptions` new)
- [ ] T9: Alter `trip_results`; create `route_daily_stats`, updated `route_speed`, `route_speed_hourly`

### Phase 4 — Worker
- [ ] T10: Add `crates/worker/src/transitland/` module with Transitland API client (agencies, routes, stops resolution)
- [ ] T11: Update static feed ingestor to resolve Onestop IDs, upsert canonical entities, skip unresolvable records
- [ ] T12: Update RT feed ingestor to resolve stop Onestop IDs via `feed_stop_ids`
- [ ] T13: Update computed metrics writer to use new `route_daily_stats` schema and `FeedId` partition key

### Phase 5 — Core & Server
- [ ] T14: Update all `crates/core/` query modules to new schema (`FeedId`, Onestop FK columns, `route_daily_stats`)
- [ ] T15: Update `crates/server/` handlers and templates (remove benchmark UI, update any agency_id references)

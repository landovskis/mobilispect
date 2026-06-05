-- migrations/013_alter_timetable_tables.sql
-- Alter route_variants, route_variant_stops, trips, and scheduled_stops to use
-- feed_id BIGINT instead of agency_id TEXT, reference canonical Onestop IDs for
-- routes and stops, and enforce the normalized Route → Variant → Trip hierarchy.
-- Data must be re-ingested after this migration.

-- Clear timetable data before restructuring so the new feed_id FK (DEFAULT 0)
-- does not violate feeds(id) — there is no sentinel row with id = 0.
-- No cross-table FKs exist between these four tables at this point in the
-- migration history, so the order does not matter.
TRUNCATE route_variants, route_variant_stops, trips, scheduled_stops;

-- ============================================================
-- route_variants
-- ============================================================

-- Drop old indexes first (they reference the old PK columns)
DROP INDEX IF EXISTS idx_route_variants_route;
DROP INDEX IF EXISTS idx_route_variants_pattern;

-- Replace agency_id with feed_id BIGINT
ALTER TABLE route_variants
    DROP COLUMN agency_id,
    ADD COLUMN feed_id BIGINT NOT NULL DEFAULT 0 REFERENCES feeds(id);

-- Drop old composite PK (agency_id, route_id, direction_id, variant_id) and
-- replace with (feed_id, variant_id)
ALTER TABLE route_variants DROP CONSTRAINT IF EXISTS route_variants_pkey;
ALTER TABLE route_variants ADD PRIMARY KEY (feed_id, variant_id);

-- Recreate indexes on new key columns
CREATE INDEX idx_route_variants_route   ON route_variants (feed_id, route_id);
CREATE INDEX idx_route_variants_pattern ON route_variants (feed_id, variant_id);

-- ============================================================
-- route_variant_stops
-- ============================================================

ALTER TABLE route_variant_stops
    DROP COLUMN agency_id,
    ADD COLUMN feed_id BIGINT NOT NULL DEFAULT 0 REFERENCES feeds(id);

ALTER TABLE route_variant_stops DROP CONSTRAINT IF EXISTS route_variant_stops_pkey;
ALTER TABLE route_variant_stops ADD PRIMARY KEY (feed_id, variant_id, stop_sequence);

-- stop_id now references stops.onestop_id (TEXT PK from migration 011)
ALTER TABLE route_variant_stops
    ADD CONSTRAINT route_variant_stops_stop_id_fkey
        FOREIGN KEY (stop_id) REFERENCES stops(onestop_id);

-- ============================================================
-- trips
-- ============================================================

DROP INDEX IF EXISTS idx_trips_route;

-- Drop columns that are normalized out:
--   route_id    → derivable via variant_id → route_variants.route_id
--   direction_id → derivable via variant_id → route_variants.direction_id
-- Replace agency_id with feed_id BIGINT.
ALTER TABLE trips
    DROP COLUMN agency_id,
    DROP COLUMN route_id,
    DROP COLUMN direction_id,
    ADD COLUMN feed_id BIGINT NOT NULL DEFAULT 0 REFERENCES feeds(id);

-- variant_id must now be non-nullable (every trip belongs to a variant)
ALTER TABLE trips ALTER COLUMN variant_id SET NOT NULL;

ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_pkey;
ALTER TABLE trips ADD PRIMARY KEY (feed_id, trip_id);

-- Enforce referential integrity to the parent variant
ALTER TABLE trips
    ADD CONSTRAINT trips_variant_fkey
        FOREIGN KEY (feed_id, variant_id) REFERENCES route_variants(feed_id, variant_id);

CREATE INDEX idx_trips_variant ON trips (feed_id, variant_id);

-- ============================================================
-- scheduled_stops
-- ============================================================

DROP INDEX IF EXISTS idx_scheduled_stops_trip;

ALTER TABLE scheduled_stops
    DROP COLUMN agency_id,
    ADD COLUMN feed_id BIGINT NOT NULL DEFAULT 0 REFERENCES feeds(id);

ALTER TABLE scheduled_stops DROP CONSTRAINT IF EXISTS scheduled_stops_pkey;
ALTER TABLE scheduled_stops ADD PRIMARY KEY (feed_id, trip_id, stop_sequence);

-- stop_id now references stops.onestop_id
ALTER TABLE scheduled_stops
    ADD CONSTRAINT scheduled_stops_stop_id_fkey
        FOREIGN KEY (stop_id) REFERENCES stops(onestop_id);

-- Enforce referential integrity to the parent trip
ALTER TABLE scheduled_stops
    ADD CONSTRAINT scheduled_stops_trip_fkey
        FOREIGN KEY (feed_id, trip_id) REFERENCES trips(feed_id, trip_id);

CREATE INDEX idx_scheduled_stops_trip ON scheduled_stops (feed_id, trip_id);

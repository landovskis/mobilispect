-- Backfill route_variants and route_variant_stops from existing trips + scheduled_stops.
-- Produces variant_ids that match what load_variants() in static_feed.rs produces:
--   first 32 hex chars of SHA-256("{stop1_id},{stop2_id},...")
-- This is a no-op for new deployments where load_variants() already ran.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

WITH trip_patterns AS (
    -- Build the ordered stop sequence string for every trip.
    SELECT
        t.agency_id,
        t.trip_id,
        t.route_id,
        COALESCE(t.direction_id, 0)                                          AS direction_id,
        t.trip_headsign,
        string_agg(ss.stop_id, ',' ORDER BY ss.stop_sequence)               AS stop_ids_csv,
        COUNT(ss.stop_id)                                                    AS stop_count
    FROM trips t
    JOIN scheduled_stops ss ON ss.agency_id = t.agency_id AND ss.trip_id = t.trip_id
    WHERE t.variant_id IS NULL   -- skip trips already assigned by load_variants()
    GROUP BY t.agency_id, t.trip_id, t.route_id, t.direction_id, t.trip_headsign
),
patterns_with_id AS (
    SELECT
        agency_id,
        trip_id,
        route_id,
        direction_id,
        trip_headsign,
        stop_ids_csv,
        stop_count::BIGINT AS stop_count,
        -- Match Rust: SHA-256(stop_ids_csv), first 16 bytes → 32 hex chars
        substring(encode(digest(stop_ids_csv, 'sha256'), 'hex'), 1, 32) AS variant_id
    FROM trip_patterns
),
variant_counts AS (
    -- Aggregate to one row per (agency, route, direction, variant).
    SELECT
        agency_id,
        route_id,
        direction_id,
        variant_id,
        stop_ids_csv,
        stop_count,
        -- Most frequent headsign for this variant
        (array_agg(trip_headsign ORDER BY trip_headsign NULLS LAST))[1] AS headsign,
        COUNT(*)                                                          AS trip_count
    FROM patterns_with_id
    GROUP BY agency_id, route_id, direction_id, variant_id, stop_ids_csv, stop_count
),
primary_flags AS (
    -- For each (agency, route, direction), flag the highest-trip-count variant as primary.
    -- Ties broken by lexicographic variant_id for determinism.
    SELECT
        agency_id, route_id, direction_id, variant_id,
        ROW_NUMBER() OVER (
            PARTITION BY agency_id, route_id, direction_id
            ORDER BY trip_count DESC, variant_id
        ) = 1 AS is_primary
    FROM variant_counts
)
INSERT INTO route_variants
    (agency_id, variant_id, route_id, direction_id, headsign, stop_count, trip_count, is_primary)
SELECT
    vc.agency_id,
    vc.variant_id,
    vc.route_id,
    vc.direction_id,
    vc.headsign,
    vc.stop_count,
    vc.trip_count,
    pf.is_primary
FROM variant_counts vc
JOIN primary_flags pf USING (agency_id, route_id, direction_id, variant_id)
ON CONFLICT (agency_id, route_id, direction_id, variant_id) DO NOTHING;

-- Populate route_variant_stops for each newly inserted variant.
-- One representative trip per variant is sufficient — all trips with the same
-- variant_id have identical stop sequences by construction.
WITH trip_patterns AS (
    SELECT
        t.agency_id,
        t.trip_id,
        substring(encode(digest(
            string_agg(ss.stop_id, ',' ORDER BY ss.stop_sequence),
            'sha256'), 'hex'), 1, 32) AS variant_id
    FROM trips t
    JOIN scheduled_stops ss ON ss.agency_id = t.agency_id AND ss.trip_id = t.trip_id
    WHERE t.variant_id IS NULL
    GROUP BY t.agency_id, t.trip_id
),
rep_trip AS (
    -- Pick one trip per (agency, variant) — any will do since stop sequences are identical.
    SELECT DISTINCT ON (agency_id, variant_id) agency_id, trip_id, variant_id
    FROM trip_patterns
    ORDER BY agency_id, variant_id, trip_id
)
INSERT INTO route_variant_stops (agency_id, variant_id, stop_sequence, stop_id)
SELECT rt.agency_id, rt.variant_id, ss.stop_sequence, ss.stop_id
FROM rep_trip rt
JOIN scheduled_stops ss ON ss.agency_id = rt.agency_id AND ss.trip_id = rt.trip_id
ON CONFLICT (agency_id, variant_id, stop_sequence) DO NOTHING;

-- Link every trip to its variant.
UPDATE trips t
SET variant_id = (
    SELECT substring(encode(digest(
        string_agg(ss.stop_id, ',' ORDER BY ss.stop_sequence),
        'sha256'), 'hex'), 1, 32)
    FROM scheduled_stops ss
    WHERE ss.agency_id = t.agency_id AND ss.trip_id = t.trip_id
)
WHERE t.variant_id IS NULL;

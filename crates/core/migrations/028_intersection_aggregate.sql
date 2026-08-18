-- migrations/028_intersection_aggregate.sql
-- Corridor Design: replaces the implicit "corridor endpoint stands in for
-- intersection" convention with a real, shared Intersection aggregate. See
-- docs/superpowers/specs/2026-08-12-corridor-intersection-aggregate-design.md.
--
-- Depends on migration 027 (intersection_treatments, cross_sections.bus_stop)
-- already being applied -- this migration moves that data across and drops
-- both, replacing them with the schema below.

CREATE TABLE intersections (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lat           DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lon           DOUBLE PRECISION NOT NULL CHECK (lon BETWEEN -180 AND 180),
    bus_gate      TEXT CHECK (bus_gate IN ('signal_controlled', 'yield_controlled')),
    turn_conflict TEXT CHECK (turn_conflict IN (
                      'indirect_left_via_alternative', 'indirect_left_within_intersection',
                      'right_in_right_out', 'dead_end_lateral_street'
                  )),
    bus_stop      TEXT CHECK (bus_stop IN ('bus_bulb', 'signal_protected_platform'))
);

-- One row per OSM node an Intersection was matched from. Usually one row per
-- Intersection; more than one after a dual-carriageway merge. A private
-- (manual-corridor) Intersection has zero rows here -- this table, not a
-- nullable column on `intersections`, is the source of truth for "is this
-- Intersection linked to any OSM node(s), and which."
CREATE TABLE intersection_osm_nodes (
    intersection_id BIGINT NOT NULL REFERENCES intersections(id) ON DELETE CASCADE,
    osm_node_id     BIGINT NOT NULL UNIQUE,
    PRIMARY KEY (intersection_id, osm_node_id)
);

-- Nullable, no CHECK/trigger enforcing "endpoint has one, interior doesn't" --
-- that invariant is enforced entirely in application code (repository.rs),
-- per this design's Open Points: a per-corridor MIN/MAX-position CHECK would
-- need a trigger (CHECK constraints can't reference sibling rows), and the
-- added complexity isn't justified for a first version.
ALTER TABLE cross_sections ADD COLUMN intersection_id BIGINT REFERENCES intersections(id);

CREATE TABLE turn_movements (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    intersection_id BIGINT NOT NULL REFERENCES intersections(id) ON DELETE CASCADE,
    from_lane_id    BIGINT NOT NULL REFERENCES lanes(id) ON DELETE CASCADE,
    to_lane_id      BIGINT NOT NULL REFERENCES lanes(id) ON DELETE CASCADE,
    source          TEXT NOT NULL CHECK (source IN ('inferred', 'manual')),
    UNIQUE (intersection_id, from_lane_id, to_lane_id)
);

-- Audit log for automatic dual-carriageway merges (Task 6). No FK to the
-- absorbed Intersection -- that row is deleted in the same transaction that
-- inserts this log entry.
CREATE TABLE intersection_merges (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    surviving_intersection_id  BIGINT NOT NULL REFERENCES intersections(id) ON DELETE CASCADE,
    absorbed_osm_node_ids      BIGINT[] NOT NULL,
    treatment_conflict         BOOLEAN NOT NULL DEFAULT FALSE,
    merged_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Backfill: one Intersection per existing corridor endpoint (its first and
-- last cross-section by `position`; the same row for a single-cross-section
-- corridor). Endpoints sharing a non-null osm_node_id are matched onto the
-- SAME Intersection via intersection_osm_nodes' UNIQUE(osm_node_id); this is
-- an imperative loop (not a set-based INSERT...SELECT) specifically so that
-- matching doesn't rely on joining rows back together by lat/lon, which is
-- fragile once floating-point coordinates are involved. This backfill does
-- NOT run the dual-carriageway merge heuristic (Task 6) -- it only matches
-- exact existing osm_node_id collisions; new imports going forward get the
-- heuristic pass, existing data does not get retroactively re-evaluated.
--
-- DATA-PRESERVATION EXCEPTION: the loop also picks up any cross-section that
-- carries #027 treatment data (an `intersection_treatments` row, or a
-- non-null `bus_stop`) even when it is NOT a corridor endpoint. #027 imposed
-- no endpoint restriction -- `intersection_treatments`' primary key is a bare
-- `cross_section_id` FK and `PATCH /api/cross-sections/:id/bus-stop` was
-- wired into the lane editor, which operates on ANY selected cross-section --
-- so interior cross-sections can legitimately hold treatment data. Without
-- this clause the carry-over UPDATEs below would only see the endpoint subset
-- and the rest would be destroyed silently by this migration's DROPs. Such an
-- interior cross-section therefore gets its own Intersection and a
-- `cross_sections.intersection_id` link, deliberately breaking the "only
-- endpoints carry intersection_id" convention this migration otherwise
-- delegates to application code: losing an analyst's treatment data is worse
-- than a one-time backfill leaving an interior link behind, and the analyst
-- can see and clean up the extra Intersection in the editor.
DO $$
DECLARE
    r RECORD;
    matched_intersection_id BIGINT;
    new_intersection_id BIGINT;
BEGIN
    FOR r IN
        SELECT cs.id AS cross_section_id, cs.lat, cs.lon, cs.osm_node_id
        FROM cross_sections cs
        WHERE cs.position = (
                  SELECT MIN(c2.position) FROM cross_sections c2 WHERE c2.corridor_id = cs.corridor_id
              )
           OR cs.position = (
                  SELECT MAX(c2.position) FROM cross_sections c2 WHERE c2.corridor_id = cs.corridor_id
              )
           OR cs.bus_stop IS NOT NULL
           OR EXISTS (
                  SELECT 1 FROM intersection_treatments it
                  WHERE it.cross_section_id = cs.id
                    AND (it.bus_gate IS NOT NULL OR it.turn_conflict IS NOT NULL)
              )
        ORDER BY cs.corridor_id, cs.position
    LOOP
        matched_intersection_id := NULL;

        IF r.osm_node_id IS NOT NULL THEN
            SELECT intersection_id INTO matched_intersection_id
            FROM intersection_osm_nodes WHERE osm_node_id = r.osm_node_id;
        END IF;

        IF matched_intersection_id IS NULL THEN
            INSERT INTO intersections (lat, lon) VALUES (r.lat, r.lon)
            RETURNING id INTO new_intersection_id;
            IF r.osm_node_id IS NOT NULL THEN
                INSERT INTO intersection_osm_nodes (intersection_id, osm_node_id)
                VALUES (new_intersection_id, r.osm_node_id);
            END IF;
            matched_intersection_id := new_intersection_id;
        END IF;

        UPDATE cross_sections SET intersection_id = matched_intersection_id
        WHERE id = r.cross_section_id;
    END LOOP;
END $$;

-- Move #027's per-cross-section treatment data onto the Intersection each
-- cross-section now references. Thanks to the data-preservation exception in
-- the backfill above, every cross-section that had treatment data has a
-- non-null `intersection_id` by this point, so nothing is left behind when the
-- DROPs below run.
--
-- TIE-BREAK: several cross-sections can resolve onto ONE Intersection (two
-- corridors meeting at the same osm_node_id, or a dual carriageway's two
-- endpoints), and each of them may carry its own, different #027 treatment
-- values. `DISTINCT ON (cs.intersection_id) ... ORDER BY cs.intersection_id,
-- cs.id` makes the winner the LOWEST `cross_sections.id` -- i.e. the
-- oldest-created cross-section at that intersection -- rather than whatever
-- order Postgres happened to produce rows in, which is what the previous plain
-- correlated UPDATE relied on. Rows losing the tie-break are dropped; this is
-- deliberately simpler than `merge_intersections`' `treatment_conflict`
-- auditing, which is for ongoing merges rather than a one-time backfill.
UPDATE intersections i
SET bus_gate = src.bus_gate, turn_conflict = src.turn_conflict
FROM (
    SELECT DISTINCT ON (cs.intersection_id)
           cs.intersection_id, it.bus_gate, it.turn_conflict
    FROM intersection_treatments it
    JOIN cross_sections cs ON cs.id = it.cross_section_id
    WHERE cs.intersection_id IS NOT NULL
      AND (it.bus_gate IS NOT NULL OR it.turn_conflict IS NOT NULL)
    ORDER BY cs.intersection_id, cs.id
) src
WHERE i.id = src.intersection_id;

UPDATE intersections i
SET bus_stop = src.bus_stop
FROM (
    SELECT DISTINCT ON (cs.intersection_id) cs.intersection_id, cs.bus_stop
    FROM cross_sections cs
    WHERE cs.intersection_id IS NOT NULL AND cs.bus_stop IS NOT NULL
    ORDER BY cs.intersection_id, cs.id
) src
WHERE i.id = src.intersection_id;

DROP TABLE intersection_treatments;
ALTER TABLE cross_sections DROP COLUMN bus_stop;

-- Indexes on the new hot foreign keys. `cross_sections.intersection_id` is the
-- filter column for `corridors_at_intersection`, `merge_intersections`'
-- re-point UPDATE, and the per-row LATERAL join in
-- `run_dual_carriageway_merge_pass`. `turn_movements.from_lane_id`/`to_lane_id`
-- are `ON DELETE CASCADE` FKs, so without these every `DELETE FROM lanes`
-- sequentially scans `turn_movements`. Naming follows migration 021's
-- `idx_cross_sections_corridor` / 026's `idx_lanes_cross_section`.
CREATE INDEX idx_cross_sections_intersection ON cross_sections (intersection_id);
CREATE INDEX idx_turn_movements_from_lane ON turn_movements (from_lane_id);
CREATE INDEX idx_turn_movements_to_lane ON turn_movements (to_lane_id);

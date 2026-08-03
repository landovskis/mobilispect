-- migrations/026_corridor_lanes.sql
-- Corridor Segment Editor: a cross-section is a lane-by-lane arrangement, not
-- just a labeled point. A lane's access is one or more time-windowed rules
-- (NULL day/start/end = always active). See
-- docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md.

CREATE TABLE lanes (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cross_section_id  BIGINT NOT NULL REFERENCES cross_sections(id) ON DELETE CASCADE,
    position          NUMERIC NOT NULL,
    lane_type         TEXT NOT NULL CHECK (lane_type IN (
                          'travel', 'turn', 'transit', 'queue_jump', 'cycle_lane',
                          'cycle_track', 'parking', 'sidewalk', 'median', 'buffer'
                      )),
    width_meters      DOUBLE PRECISION NOT NULL CHECK (width_meters > 0),
    direction         TEXT NOT NULL CHECK (direction IN ('forward', 'backward', 'both', 'none')),
    UNIQUE (cross_section_id, position)
);

CREATE INDEX idx_lanes_cross_section ON lanes (cross_section_id, position);

CREATE TABLE lane_access_rules (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lane_id        BIGINT NOT NULL REFERENCES lanes(id) ON DELETE CASCADE,
    days           TEXT,
    start_time     TIME,
    end_time       TIME,
    allowed_modes  TEXT[] NOT NULL
);

CREATE INDEX idx_lane_access_rules_lane ON lane_access_rules (lane_id);

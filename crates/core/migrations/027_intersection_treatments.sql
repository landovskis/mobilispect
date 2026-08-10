-- migrations/027_intersection_treatments.sql
-- Corridor Segment Editor: intersection treatments (bus gate, turn-conflict
-- type) and a cross-section's optional bus-stop platform type. See
-- docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md's
-- "Intersection Treatments" section.

ALTER TABLE cross_sections
    ADD COLUMN bus_stop TEXT CHECK (bus_stop IN ('bus_bulb', 'signal_protected_platform'));

CREATE TABLE intersection_treatments (
    cross_section_id  BIGINT PRIMARY KEY REFERENCES cross_sections(id) ON DELETE CASCADE,
    bus_gate          TEXT CHECK (bus_gate IN ('signal_controlled', 'yield_controlled')),
    turn_conflict     TEXT CHECK (turn_conflict IN (
                          'indirect_left_via_alternative', 'indirect_left_within_intersection',
                          'right_in_right_out', 'dead_end_lateral_street'
                      ))
);

-- migrations/022_cross_section_fractional_position.sql
-- Changes cross_sections.position from a dense INTEGER sequence to a fractional
-- key (see corridor_design::position::assign_position) so inserting anywhere in
-- a corridor's sequence -- including REQ-005's reorder -- is an O(1) write.
--
-- The old UNIQUE(corridor_id, position) constraint from 021 was declared without
-- an explicit name, so Postgres auto-named it `cross_sections_corridor_id_position_key`
-- (`<table>_<col1>_<col2>_key`, the default table-level UNIQUE naming convention).
-- Verified against a throwaway Postgres 16 instance before writing this migration
-- rather than assumed. It's dropped and re-added under an explicit name so future
-- migrations don't have to guess an auto-generated name again.
--
-- No new index is added: 021's `idx_cross_sections_corridor` already covers
-- (corridor_id, position), so a second index on the same columns would be a
-- redundant duplicate.
ALTER TABLE cross_sections DROP CONSTRAINT cross_sections_corridor_id_position_key;

ALTER TABLE cross_sections ALTER COLUMN position TYPE NUMERIC USING position::numeric;

ALTER TABLE cross_sections
    ADD CONSTRAINT cross_sections_corridor_position_unique UNIQUE (corridor_id, position);

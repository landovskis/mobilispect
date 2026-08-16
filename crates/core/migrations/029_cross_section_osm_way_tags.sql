-- migrations/029_cross_section_osm_way_tags.sql
-- Persists the originating OSM way's `oneway`/`name`/`ref` tags onto each
-- cross-section at import time, the same way `osm_way_id`/`osm_node_id`
-- already are (migration 021). Needed by
-- `repository::run_dual_carriageway_merge_pass` (Task 8), which must compare
-- an *existing* intersection's real street identity against a newly-arriving
-- endpoint's -- before this migration there was nowhere to read that identity
-- back from, so the merge heuristic's name/ref check was silently comparing
-- the candidate's own tags against itself.
--
-- Nullable: manually-traced cross-sections (`insert_cross_section`) never
-- populate these columns and stay NULL, same posture as `osm_way_id`/
-- `osm_node_id` on that path.

ALTER TABLE cross_sections
    ADD COLUMN osm_way_oneway BOOLEAN,
    ADD COLUMN osm_way_name   TEXT,
    ADD COLUMN osm_way_ref    TEXT;

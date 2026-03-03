-- V067__add_shape_id_to_route_variants.sql
-- Adds shape_id from GTFS trips.txt to track which GTFS shape corresponds to each route variant.
-- A shape_id may be NULL for feeds that do not include shapes.txt.

ALTER TABLE route_variants ADD COLUMN shape_id VARCHAR(255);

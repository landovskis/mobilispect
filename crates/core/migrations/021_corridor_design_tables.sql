-- migrations/021_corridor_design_tables.sql
-- Corridor Design: a corridor is an ordered sequence of cross-sections, defined by
-- road geometry that is either imported (GIS/OSM) or built manually by an analyst.
-- A corridor's geometry_source records which; position is a fractional-key column
-- (see 022_cross_section_fractional_position.sql) enabling O(1) insert/reorder.

CREATE TABLE corridors (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             TEXT NOT NULL CHECK (length(trim(name)) > 0),
    geometry_source  TEXT NOT NULL CHECK (geometry_source IN ('imported', 'manual')),
    import_format    TEXT CHECK (import_format IN ('geojson_osm_export')),  -- NULL for manual
    osm_attribution  TEXT,        -- populated on import; NULL for manual
    osm_fetched_at   TIMESTAMPTZ, -- when source data was retrieved, for attribution/audit
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cross_sections (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    corridor_id   BIGINT NOT NULL REFERENCES corridors(id) ON DELETE CASCADE,
    position      INTEGER NOT NULL CHECK (position >= 0),
    lat           DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lon           DOUBLE PRECISION NOT NULL CHECK (lon BETWEEN -180 AND 180),
    osm_way_id    BIGINT,   -- source way this point derives from; NULL for manually drawn points
    osm_node_id   BIGINT,   -- source node id when available; NULL if interpolated or manually drawn
    UNIQUE (corridor_id, position)
);

CREATE INDEX idx_cross_sections_corridor ON cross_sections (corridor_id, position);

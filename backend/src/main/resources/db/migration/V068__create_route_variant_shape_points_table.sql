-- V068__create_route_variant_shape_points_table.sql
-- Stores matched and original GTFS shape points for each route variant.
-- original_lat/lon: raw coordinates from shapes.txt
-- matched_lat/lon: coordinates snapped to the road network by OTP (NULL when OTP unavailable)
-- street_name: primary street name returned by OTP for this point (NULL when OTP unavailable)

CREATE TABLE route_variant_shape_points (
    variant_id      VARCHAR(64)              NOT NULL,
    sequence        INTEGER                  NOT NULL,
    original_lat    DOUBLE PRECISION         NOT NULL,
    original_lon    DOUBLE PRECISION         NOT NULL,
    matched_lat     DOUBLE PRECISION,
    matched_lon     DOUBLE PRECISION,
    street_name     VARCHAR(512),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (variant_id, sequence),
    CONSTRAINT fk_shape_points_variant
        FOREIGN KEY (variant_id)
        REFERENCES route_variants (id)
        ON DELETE CASCADE
);

-- Airflow pipeline user needs full write access for the import tasks.
GRANT SELECT, INSERT, UPDATE, DELETE ON route_variant_shape_points TO airflow;

-- Spring backend (mobilispect role) needs read access to serve shape data via the API.
GRANT SELECT ON route_variant_shape_points TO mobilispect;

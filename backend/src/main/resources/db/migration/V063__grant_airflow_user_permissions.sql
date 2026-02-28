-- V063__grant_airflow_user_permissions.sql
-- Grant the airflow user read/write access to the tables it needs for pipeline DAGs.
-- The airflow user was created in the devcontainer init with CONNECT only.

GRANT SELECT, INSERT, UPDATE, DELETE ON
    metropolitan_regions,
    feeds,
    feed_regions,
    feed_imports,
    region_imports,
    region_import_feeds,
    agencies,
    routes,
    route_variants,
    route_common_sections,
    stops,
    route_variant_stops,
    stop_spacing,
    frequencies
TO airflow;

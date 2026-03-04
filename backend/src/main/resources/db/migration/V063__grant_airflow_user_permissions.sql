-- V063__grant_airflow_user_permissions.sql
-- Grant the airflow user read/write access to the tables it needs for pipeline DAGs.
-- The airflow user was created in the devcontainer init with CONNECT only.
-- The DO block makes this safe in environments where the role does not exist (e.g. Testcontainers).

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'airflow') THEN
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
    END IF;
END$$;

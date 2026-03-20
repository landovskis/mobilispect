-- beforeMigrate__create_airflow_role.sql
-- Create the airflow role if it doesn't already exist.
-- This ensures V063 (which grants permissions to airflow) succeeds in all environments,
-- including Testcontainers-based integration tests that start with a fresh PostgreSQL instance.
-- In devcontainer/production, the role already exists and this is a no-op.

DO $$
BEGIN
    CREATE ROLE airflow;
EXCEPTION WHEN duplicate_object THEN
    NULL;
END
$$;

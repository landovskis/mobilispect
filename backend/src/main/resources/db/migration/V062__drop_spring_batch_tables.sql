-- V062__drop_spring_batch_tables.sql
-- Remove Spring Batch metadata tables and sequences (created by V017 and V019).
-- Spring Batch has been replaced by Apache Airflow.
--
-- Also drops FK columns referencing batch tables that were added in V054.
-- These columns are no longer meaningful without Spring Batch.

-- Drop FK constraints and columns from region_import_feeds
ALTER TABLE region_import_feeds DROP COLUMN IF EXISTS child_job_execution_id;

-- Drop FK constraints and columns from region_imports
DROP INDEX IF EXISTS idx_region_imports_parent_job_execution_id;
ALTER TABLE region_imports DROP COLUMN IF EXISTS parent_job_execution_id;

-- Drop Spring Batch tables (leaf tables first, then parent tables)
DROP TABLE IF EXISTS BATCH_STEP_EXECUTION_CONTEXT;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION_CONTEXT;
DROP TABLE IF EXISTS BATCH_STEP_EXECUTION;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION_PARAMS;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION;
DROP TABLE IF EXISTS BATCH_JOB_INSTANCE;

DROP SEQUENCE IF EXISTS BATCH_STEP_EXECUTION_SEQ;
DROP SEQUENCE IF EXISTS BATCH_JOB_EXECUTION_SEQ;
DROP SEQUENCE IF EXISTS BATCH_JOB_SEQ;

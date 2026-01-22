-- Drop foreign key to batch job execution for region imports
ALTER TABLE region_imports
  DROP CONSTRAINT IF EXISTS region_imports_parent_job_execution_id_fkey;

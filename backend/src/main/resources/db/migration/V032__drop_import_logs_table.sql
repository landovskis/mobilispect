-- Drop import_logs table and related database elements
-- Version: 1.0.0

-- Drop the import_logs table (CASCADE will drop dependent objects like foreign keys)
DROP TABLE IF EXISTS import_logs CASCADE;

-- Drop the log_level enum type
DROP TYPE IF EXISTS log_level CASCADE;

-- Note: Indexes on import_logs are automatically dropped when the table is dropped
-- (idx_import_logs_import_id, idx_import_logs_level, idx_import_logs_created_at)

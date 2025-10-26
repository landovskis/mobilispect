-- Create audit log table for comprehensive audit trails
CREATE TABLE audit_log (
    audit_log_id VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    level VARCHAR(20) NOT NULL CHECK (level IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL')),
    category VARCHAR(30) NOT NULL CHECK (category IN (
        'AUTHENTICATION', 'AUTHORIZATION', 'DATA_ACCESS', 'DATA_MODIFICATION',
        'FEED_IMPORT', 'FEED_MANAGEMENT', 'SYSTEM_CONFIG', 'USER_ACTION',
        'SYSTEM_EVENT', 'SECURITY', 'PERFORMANCE', 'ERROR_HANDLING'
    )),
    action VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    administrator_id VARCHAR(100),
    username VARCHAR(100),
    session_id VARCHAR(100),
    ip_address VARCHAR(45), -- Supports both IPv4 and IPv6
    user_agent VARCHAR(500),
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    old_values TEXT,
    new_values TEXT,
    context_data TEXT,
    source_component VARCHAR(100),
    request_id VARCHAR(100),
    duration_ms BIGINT,
    success BOOLEAN NOT NULL DEFAULT true,
    error_message TEXT,
    stack_trace TEXT,
    tags VARCHAR(500),

    CONSTRAINT pk_audit_log PRIMARY KEY (audit_log_id),
    CONSTRAINT chk_audit_log_duration_non_negative CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

-- Create indexes for optimal query performance
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);
CREATE INDEX idx_audit_log_category ON audit_log(category);
CREATE INDEX idx_audit_log_level ON audit_log(level);
CREATE INDEX idx_audit_log_administrator ON audit_log(administrator_id);
CREATE INDEX idx_audit_log_resource ON audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_log_session ON audit_log(session_id);
CREATE INDEX idx_audit_log_ip ON audit_log(ip_address);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_success ON audit_log(success);
CREATE INDEX idx_audit_log_source ON audit_log(source_component);

-- Composite indexes for common query patterns
CREATE INDEX idx_audit_log_category_timestamp ON audit_log(category, timestamp DESC);
CREATE INDEX idx_audit_log_level_timestamp ON audit_log(level, timestamp DESC);
CREATE INDEX idx_audit_log_user_timestamp ON audit_log(administrator_id, timestamp DESC);
CREATE INDEX idx_audit_log_resource_timestamp ON audit_log(resource_type, resource_id, timestamp DESC);
CREATE INDEX idx_audit_log_security_events ON audit_log(category, level, timestamp DESC)
    WHERE category IN ('AUTHENTICATION', 'AUTHORIZATION', 'SECURITY') OR level = 'CRITICAL';
CREATE INDEX idx_audit_log_data_modifications ON audit_log(category, timestamp DESC)
    WHERE category = 'DATA_MODIFICATION' OR old_values IS NOT NULL;
CREATE INDEX idx_audit_log_failed_operations ON audit_log(success, timestamp DESC) WHERE success = false;

-- Partial indexes for performance on filtered queries
CREATE INDEX idx_audit_log_failed_auth ON audit_log(timestamp DESC)
    WHERE category = 'AUTHENTICATION' AND success = false;
CREATE INDEX idx_audit_log_high_duration ON audit_log(duration_ms DESC, timestamp DESC)
    WHERE duration_ms > 10000;

-- Full-text search index for description and action (PostgreSQL specific)
CREATE INDEX idx_audit_log_fulltext ON audit_log USING gin(to_tsvector('english', description || ' ' || action));

-- Add comments for documentation
COMMENT ON TABLE audit_log IS 'Comprehensive audit trail for all system activities and user actions';
COMMENT ON COLUMN audit_log.audit_log_id IS 'Unique identifier for the audit log entry';
COMMENT ON COLUMN audit_log.timestamp IS 'When the audited event occurred';
COMMENT ON COLUMN audit_log.level IS 'Severity level: DEBUG, INFO, WARN, ERROR, CRITICAL';
COMMENT ON COLUMN audit_log.category IS 'Category of the audited event for organization';
COMMENT ON COLUMN audit_log.action IS 'The specific action that was performed';
COMMENT ON COLUMN audit_log.description IS 'Human-readable description of the event';
COMMENT ON COLUMN audit_log.administrator_id IS 'ID of the user who performed the action (null for system events)';
COMMENT ON COLUMN audit_log.username IS 'Username for display purposes (cached for performance)';
COMMENT ON COLUMN audit_log.session_id IS 'Session ID for tracking user sessions';
COMMENT ON COLUMN audit_log.ip_address IS 'IP address of the request origin';
COMMENT ON COLUMN audit_log.user_agent IS 'User agent string from web requests';
COMMENT ON COLUMN audit_log.resource_type IS 'Type of resource affected (Feed, Import, etc.)';
COMMENT ON COLUMN audit_log.resource_id IS 'ID of the specific resource affected';
COMMENT ON COLUMN audit_log.old_values IS 'Previous state of the resource (JSON format)';
COMMENT ON COLUMN audit_log.new_values IS 'New state of the resource (JSON format)';
COMMENT ON COLUMN audit_log.context_data IS 'Additional context information (JSON format)';
COMMENT ON COLUMN audit_log.source_component IS 'Component or service that generated the log';
COMMENT ON COLUMN audit_log.request_id IS 'Request ID for distributed tracing';
COMMENT ON COLUMN audit_log.duration_ms IS 'Duration of the operation in milliseconds';
COMMENT ON COLUMN audit_log.success IS 'Whether the operation completed successfully';
COMMENT ON COLUMN audit_log.error_message IS 'Error message if the operation failed';
COMMENT ON COLUMN audit_log.stack_trace IS 'Stack trace for debugging failed operations';
COMMENT ON COLUMN audit_log.tags IS 'Comma-separated tags for categorization and searching';

-- Create a view for security-sensitive events
CREATE VIEW security_audit_events AS
SELECT
    audit_log_id,
    timestamp,
    level,
    category,
    action,
    description,
    administrator_id,
    username,
    session_id,
    ip_address,
    user_agent,
    resource_type,
    resource_id,
    success,
    error_message,
    context_data
FROM audit_log
WHERE category IN ('AUTHENTICATION', 'AUTHORIZATION', 'SECURITY')
   OR level = 'CRITICAL'
   OR success = false
ORDER BY timestamp DESC;

COMMENT ON VIEW security_audit_events IS 'View of security-sensitive audit events for monitoring and analysis';

-- Create a view for data modification events
CREATE VIEW data_modification_events AS
SELECT
    audit_log_id,
    timestamp,
    action,
    description,
    administrator_id,
    username,
    resource_type,
    resource_id,
    old_values,
    new_values,
    context_data
FROM audit_log
WHERE category = 'DATA_MODIFICATION'
   OR old_values IS NOT NULL
   OR new_values IS NOT NULL
ORDER BY timestamp DESC;

COMMENT ON VIEW data_modification_events IS 'View of data modification events for compliance and change tracking';
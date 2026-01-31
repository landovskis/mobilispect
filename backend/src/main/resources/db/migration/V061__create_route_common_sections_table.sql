-- Create route_common_sections table to store the longest continuous section
-- shared by all variants in a given direction for a route

CREATE TABLE route_common_sections (
    id VARCHAR(64) PRIMARY KEY,                     -- SHA-256 hash of route_id + direction_id + stop_pattern
    route_id VARCHAR(50) NOT NULL,
    direction_id INTEGER,                           -- 0=outbound, 1=inbound, NULL=unknown
    stop_pattern TEXT NOT NULL,                     -- Pipe-separated stop IDs (e.g., "stop1|stop2|stop3")
    stop_name_pattern TEXT NOT NULL,                -- Pipe-separated stop names for display
    stop_count INTEGER NOT NULL,                    -- Number of stops in common section
    first_stop_id VARCHAR(255) NOT NULL,            -- First stop in common section
    last_stop_id VARCHAR(255) NOT NULL,             -- Last stop in common section
    variant_count INTEGER NOT NULL DEFAULT 0,       -- Number of variants sharing this section
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT route_common_sections_route_fk
        FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE CASCADE,

    CONSTRAINT route_common_sections_direction_check
        CHECK (direction_id IS NULL OR direction_id IN (0, 1)),

    CONSTRAINT route_common_sections_stop_count_check
        CHECK (stop_count >= 0)
);

-- Create unique index to ensure one common section per route per direction
CREATE UNIQUE INDEX idx_route_common_sections_route_direction
    ON route_common_sections (route_id, direction_id);

-- Create index for querying by route
CREATE INDEX idx_route_common_sections_route_id
    ON route_common_sections (route_id);

-- Create index for timestamp-based queries
CREATE INDEX idx_route_common_sections_updated_at
    ON route_common_sections (updated_at);

-- Add comment to table
COMMENT ON TABLE route_common_sections IS
    'Stores the longest continuous section of stops shared by all variants of a route in a given direction';

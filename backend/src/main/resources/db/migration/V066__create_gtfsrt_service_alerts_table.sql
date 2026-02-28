-- Create service_alerts table for GTFS-RT realtime service alerts
-- Per ADR 0011: GTFS-RT Parallel Ingestion Architecture

CREATE TABLE IF NOT EXISTS service_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feed_id VARCHAR(128) NOT NULL,
    alert_id VARCHAR(128) NOT NULL,
    cause VARCHAR(32),
    effect VARCHAR(32),
    header_text TEXT,
    description_text TEXT,
    url TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_service_alerts_feed ON service_alerts(feed_id);
CREATE INDEX idx_service_alerts_alert ON service_alerts(feed_id, alert_id);
CREATE INDEX idx_service_alerts_timestamp ON service_alerts(timestamp DESC);

COMMENT ON TABLE service_alerts IS 'Realtime service alerts from GTFS-RT feeds';
COMMENT ON COLUMN service_alerts.feed_id IS 'Source feed Onestop ID';
COMMENT ON COLUMN service_alerts.alert_id IS 'Alert entity ID from GTFS-RT feed';

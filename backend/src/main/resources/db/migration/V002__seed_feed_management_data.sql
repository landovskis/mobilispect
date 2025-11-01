-- Feed Management System Development Seed Data
-- Version: 1.0.0
-- Purpose: Sample data for development and testing

-- Sample metropolitan regions (real Transit.land regions)
INSERT INTO metropolitan_regions (region_onestop_id, name, auto_update_enabled) VALUES
('r-9q8y-montreal', 'Montreal', true),
('r-9q9-toronto', 'Toronto', true),
('r-9q5-vancouver', 'Vancouver', true),
('r-9q8z-ottawa', 'Ottawa', false),
('r-f25c-sanfranciscobayarea', 'San Francisco Bay Area', true);

-- Sample administrator accounts for testing
INSERT INTO administrators (username, email, role, active) VALUES
('admin', 'admin@mobilispect.com', 'FEED_MANAGER', true),
('operator1', 'operator1@mobilispect.com', 'FEED_OPERATOR', true),
('operator2', 'operator2@mobilispect.com', 'FEED_OPERATOR', true),
('viewer1', 'viewer1@mobilispect.com', 'FEED_VIEWER', true),
('viewer2', 'viewer2@mobilispect.com', 'FEED_VIEWER', false);

-- Sample feeds for Montreal (these would normally be discovered from Transit.land)
INSERT INTO feeds (feed_onestop_id, region_onestop_id, name, spec_type, download_url, status) VALUES
('f-f25d-socitdetransportdemontreal', 'r-9q8y-montreal', 'STM GTFS', 'gtfs', 'https://www.stm.info/sites/default/files/gtfs/gtfs_stm.zip', 'active'),
('f-f25d~rt-socitdetransportdemontreal', 'r-9q8y-montreal', 'STM GTFS-RT', 'gtfs-rt', 'https://api.stm.info/pub/od/gtfs-rt/ic/v2/vehiclePositions', 'active'),
('f-f25d-resautransportlongueuil', 'r-9q8y-montreal', 'RTL GTFS', 'gtfs', 'https://www.rtl-longueuil.qc.ca/transit/latestfeed/RTL.zip', 'active');

-- Sample feeds for Toronto
INSERT INTO feeds (feed_onestop_id, region_onestop_id, name, spec_type, download_url, status) VALUES
('f-dpz8-toronttransitcommission', 'r-9q9-toronto', 'TTC GTFS', 'gtfs', 'https://ckan0.cf.opendata.inter.prod-toronto.ca/dataset/7795b45e-e65a-4465-81fc-c36b9dfff169/resource/cfb6b2b8-6191-41b3-bda1-b175c51148cb/download/TTC_GTFS.zip', 'active'),
('f-dpz8~rt-toronttransitcommission', 'r-9q9-toronto', 'TTC GTFS-RT', 'gtfs-rt', 'https://gtfs.ttc.ca/gtfs-realtime/TripUpdate.pb', 'active');

-- Sample feeds for Vancouver
INSERT INTO feeds (feed_onestop_id, region_onestop_id, name, spec_type, download_url, status) VALUES
('f-c28n-translink', 'r-9q5-vancouver', 'TransLink GTFS', 'gtfs', 'https://gtfs.translink.ca/static/latest', 'active'),
('f-c28n~rt-translink', 'r-9q5-vancouver', 'TransLink GTFS-RT', 'gtfs-rt', 'https://gtfs.translink.ca/gtfs-realtime?apikey=YOUR_API_KEY', 'inactive');

-- Sample feed authentication (TransLink requires API key)
INSERT INTO feed_authentication (feed_onestop_id, auth_type, encrypted_credentials) VALUES
('f-c28n~rt-translink', 'api_key', '{"apiKey": "SAMPLE_ENCRYPTED_KEY", "headerName": "apikey"}');

-- Sample historical import records
DO $$
DECLARE
    admin_id UUID;
    import_id UUID;
BEGIN
    -- Get admin ID for sample imports
    SELECT id INTO admin_id FROM administrators WHERE username = 'admin' LIMIT 1;

    -- Sample completed import for STM GTFS
    INSERT INTO feed_imports (id, feed_onestop_id, administrator_id, trigger_type, status, version_sha1, started_at, completed_at, file_size_bytes)
    VALUES (uuid_generate_v4(), 'f-f25d-socitdetransportdemontreal', admin_id, 'manual', 'completed', '5817a9f002832e405651ccdd7e929d3c10590d25', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour 45 minutes', 15728640) -- pragma: allowlist secret
    RETURNING id INTO import_id;

    -- Sample logs for the completed import
    INSERT INTO import_logs (import_id, level, message, component, details) VALUES
    (import_id, 'info', 'Import started for STM GTFS feed', 'FeedImportService', '{"feedSize": "15MB", "expectedDuration": "10-15 minutes"}'),
    (import_id, 'info', 'Downloaded feed successfully', 'FileDownloadService', '{"downloadTime": "45 seconds", "fileSize": 15728640}'),
    (import_id, 'info', 'Validated GTFS structure', 'GTFSValidator', '{"agencies": 1, "routes": 168, "stops": 9842, "trips": 28567}'),
    (import_id, 'info', 'Import completed successfully', 'FeedImportService', '{"totalDuration": "14 minutes 23 seconds", "recordsProcessed": 156789}');

    -- Sample failed import for TTC GTFS
    INSERT INTO feed_imports (id, feed_onestop_id, administrator_id, trigger_type, status, started_at, completed_at, error_message)
    VALUES (uuid_generate_v4(), 'f-dpz8-toronttransitcommission', admin_id, 'automatic', 'failed', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '5 hours 45 minutes', 'Download timeout: Connection timed out after 30 seconds')
    RETURNING id INTO import_id;

    -- Sample logs for the failed import
    INSERT INTO import_logs (import_id, level, message, component, details) VALUES
    (import_id, 'info', 'Automatic import triggered for TTC GTFS', 'ScheduledImportService', '{"reason": "SHA1 change detected", "oldSHA1": "abc123...", "newSHA1": "def456..."}'),
    (import_id, 'warn', 'Download taking longer than expected', 'FileDownloadService', '{"downloadTime": "25 seconds", "expectedMax": "20 seconds"}'),
    (import_id, 'error', 'Download failed due to timeout', 'FileDownloadService', '{"error": "Connection timeout", "retryAttempt": 3, "maxRetries": 3}');

    -- Sample running import for Vancouver
    INSERT INTO feed_imports (id, feed_onestop_id, administrator_id, trigger_type, status, started_at)
    VALUES (uuid_generate_v4(), 'f-c28n-translink', NULL, 'automatic', 'running', NOW() - INTERVAL '5 minutes')
    RETURNING id INTO import_id;

    -- Sample logs for the running import
    INSERT INTO import_logs (import_id, level, message, component, details) VALUES
    (import_id, 'info', 'Automatic import started for TransLink GTFS', 'ScheduledImportService', '{"scheduledTime": "02:00:00", "actualTime": "02:00:15"}'),
    (import_id, 'info', 'Downloading feed from TransLink', 'FileDownloadService', '{"url": "https://gtfs.translink.ca/static/latest", "progress": "45%"}');
END $$;

-- STM provides absolute arrival timestamps, not delays.
-- Store them so we can compute delay = actual - scheduled.
ALTER TABLE stop_time_events ADD COLUMN arrival_time_unix INTEGER;
ALTER TABLE stop_time_events ADD COLUMN departure_time_unix INTEGER;

-- migrations/003_dwell_time.sql

ALTER TABLE stop_time_events
  ADD COLUMN dwell_secs BIGINT
    GENERATED ALWAYS AS (departure_time_unix - arrival_time_unix) STORED;

-- migrations/014_service_tables.sql
-- Rename calendar → services, migrate to feed_id-keyed schema,
-- add optional agency_id FK, add start_date/end_date from calendar.txt,
-- and create service_exceptions for calendar_dates.txt.
-- route_speed_day_type was already dropped in 010_drop_obsolete_tables.sql.

-- Rename calendar → services
ALTER TABLE calendar RENAME TO services;

-- Replace (agency_id TEXT, service_id TEXT) PK with (feed_id BIGINT, service_id TEXT) PK:
--   1. Drop the old primary key (was named calendar_pkey, now services_pkey after rename)
--   2. Drop the old agency_id TEXT column
--   3. Add feed_id BIGINT FK (DEFAULT 0 only to satisfy NOT NULL during ALTER; no existing rows expected)
--   4. Add agency_id as nullable FK → agencies(onestop_id)
--   5. Add start_date / end_date as nullable DATE columns (populated on re-ingest)
ALTER TABLE services
    DROP CONSTRAINT IF EXISTS calendar_pkey,
    DROP CONSTRAINT IF EXISTS services_pkey,
    DROP COLUMN agency_id,
    ADD COLUMN feed_id     BIGINT NOT NULL DEFAULT 0 REFERENCES feeds(id),
    ADD COLUMN agency_id   TEXT REFERENCES agencies(onestop_id),
    ADD COLUMN start_date  DATE,
    ADD COLUMN end_date    DATE;

-- Remove the bootstrap default now that the column exists
ALTER TABLE services ALTER COLUMN feed_id DROP DEFAULT;

ALTER TABLE services ADD PRIMARY KEY (feed_id, service_id);

-- service_exceptions from calendar_dates.txt
-- exception_type: 1 = service added, 2 = service removed
CREATE TABLE service_exceptions (
    feed_id         BIGINT   NOT NULL REFERENCES feeds(id),
    service_id      TEXT     NOT NULL,
    date            DATE     NOT NULL,
    exception_type  SMALLINT NOT NULL CHECK (exception_type IN (1, 2)),
    PRIMARY KEY (feed_id, service_id, date)
);

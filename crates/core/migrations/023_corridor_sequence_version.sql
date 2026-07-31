-- migrations/023_corridor_sequence_version.sql
-- Optimistic-concurrency counter for REQ-005 reorder: bumped on every successful
-- reorder so a stale client's PATCH can be rejected with 409 rather than silently
-- clobbering a concurrent edit.
ALTER TABLE corridors ADD COLUMN sequence_version BIGINT NOT NULL DEFAULT 0;

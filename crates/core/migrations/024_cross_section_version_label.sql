-- REQ-006: per-cross-section optimistic-concurrency counter (distinct from and in
-- addition to corridors.sequence_version from 023 -- that one tracks cross-section
-- ORDER changes; this one tracks a single cross-section's CONTENT changes) and a
-- descriptive label field.
ALTER TABLE cross_sections ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE cross_sections ADD COLUMN label TEXT;

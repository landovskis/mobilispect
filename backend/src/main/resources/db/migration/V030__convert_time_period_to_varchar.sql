-- Convert time_period from custom enum to VARCHAR
-- This allows Hibernate to work with the column without requiring custom type handling

-- Convert time_period column
ALTER TABLE frequencies
  ALTER COLUMN time_period TYPE VARCHAR(50);

-- Add check constraint to ensure only valid time periods are stored
ALTER TABLE frequencies
  ADD CONSTRAINT check_time_period_valid
  CHECK (time_period IN ('EARLY_MORNING', 'AM_PEAK', 'MIDDAY', 'PM_PEAK', 'EVENING', 'LATE_NIGHT', 'WEEKEND_MORNING', 'WEEKEND_AFTERNOON', 'WEEKEND_EVENING', 'WEEKDAY_OFF_PEAK'));

-- Drop the custom enum type (only after the column is converted)
DROP TYPE IF EXISTS time_period;

COMMENT ON COLUMN frequencies.time_period IS 'Time period classification as VARCHAR with check constraint for valid values';

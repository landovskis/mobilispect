-- Fix time_period constraint to match TimePeriod enum values
-- The V030 migration introduced a mismatch between the database constraint
-- and the actual TimePeriod enum values used in the application code.

-- Drop the incorrect constraint
ALTER TABLE frequencies
  DROP CONSTRAINT IF EXISTS check_time_period_valid;

-- Add the correct constraint matching TimePeriod enum
ALTER TABLE frequencies
  ADD CONSTRAINT check_time_period_valid
  CHECK (time_period IN ('WEEKDAY_AM_PEAK', 'WEEKDAY_PM_PEAK', 'WEEKDAY_OFF_PEAK', 'WEEKEND', 'HOLIDAY'));

COMMENT ON COLUMN frequencies.time_period IS 'Time period classification: WEEKDAY_AM_PEAK, WEEKDAY_PM_PEAK, WEEKDAY_OFF_PEAK, WEEKEND, or HOLIDAY';

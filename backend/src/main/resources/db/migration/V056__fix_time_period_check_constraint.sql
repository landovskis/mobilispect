-- Align time_period check constraint with TimePeriod enum values

ALTER TABLE frequencies
  DROP CONSTRAINT IF EXISTS check_time_period_valid;

ALTER TABLE frequencies
  ADD CONSTRAINT check_time_period_valid
  CHECK (time_period IN (
    'WEEKDAY_AM_PEAK',
    'WEEKDAY_PM_PEAK',
    'WEEKDAY_OFF_PEAK',
    'WEEKEND',
    'HOLIDAY'
  ));

COMMENT ON COLUMN frequencies.time_period IS 'Time period classification as VARCHAR with check constraint for valid values';

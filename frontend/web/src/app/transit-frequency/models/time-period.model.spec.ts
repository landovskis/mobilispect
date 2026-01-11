import {
  getAllTimePeriods,
  getTimePeriodLabel,
  TimePeriod,
} from './time-period.model';

describe('Time period helpers', () => {
  it('maps labels for known and unknown periods', () => {
    expect(getTimePeriodLabel(TimePeriod.WEEKDAY_AM_PEAK)).toBe(
      'Weekday AM Peak',
    );
    expect(getTimePeriodLabel('CUSTOM' as TimePeriod)).toBe('CUSTOM');
  });

  it('returns all time period values', () => {
    const periods = getAllTimePeriods();
    expect(periods).toContain(TimePeriod.WEEKEND);
    expect(periods).toContain(TimePeriod.HOLIDAY);
  });
});

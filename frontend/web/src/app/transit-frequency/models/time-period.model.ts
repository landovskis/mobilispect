/**
 * Time period classifications for service frequency analysis.
 *
 * These periods segment frequency data to enable period-specific analysis
 * of service patterns, headway variations, and schedule changes across
 * different times of day and days of the week.
 *
 * Reference: Backend TimePeriod enum at com.mobilispect.backend.transitanalysis.domain.model.TimePeriod
 */
export enum TimePeriod {
  /**
   * Weekday morning peak hours (6:00 AM - 9:00 AM).
   * Typically the highest frequency period for commute traffic.
   */
  WEEKDAY_AM_PEAK = 'WEEKDAY_AM_PEAK',

  /**
   * Weekday evening peak hours (4:00 PM - 7:00 PM).
   * Return commute period with high frequency.
   */
  WEEKDAY_PM_PEAK = 'WEEKDAY_PM_PEAK',

  /**
   * Weekday off-peak hours (all other weekday times).
   * Includes midday, evening, and early morning service periods.
   */
  WEEKDAY_OFF_PEAK = 'WEEKDAY_OFF_PEAK',

  /**
   * Weekend service (Saturday and Sunday, all day).
   * Typically lower frequency than weekday peak periods.
   */
  WEEKEND = 'WEEKEND',

  /**
   * Holiday service (based on calendar_dates.txt from GTFS).
   * Includes federal holidays and observed days off.
   */
  HOLIDAY = 'HOLIDAY',
}

/**
 * Maps time period enum values to human-readable labels.
 */
export const TimePeriodLabels: Record<TimePeriod, string> = {
  [TimePeriod.WEEKDAY_AM_PEAK]: 'Weekday AM Peak',
  [TimePeriod.WEEKDAY_PM_PEAK]: 'Weekday PM Peak',
  [TimePeriod.WEEKDAY_OFF_PEAK]: 'Weekday Off-Peak',
  [TimePeriod.WEEKEND]: 'Weekend',
  [TimePeriod.HOLIDAY]: 'Holiday',
};

/**
 * Gets the human-readable label for a time period.
 * @param period The time period enum value
 * @returns The display label for the period
 */
export function getTimePeriodLabel(period: TimePeriod): string {
  return TimePeriodLabels[period] || period;
}

/**
 * Retrieves all available time periods.
 * @returns Array of all TimePeriod enum values
 */
export function getAllTimePeriods(): TimePeriod[] {
  return Object.values(TimePeriod);
}

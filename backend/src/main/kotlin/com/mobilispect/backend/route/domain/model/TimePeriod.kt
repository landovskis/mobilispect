package com.mobilispect.backend.route.domain.model

/**
 * Enum representing distinct time periods for service frequency analysis.
 *
 * These periods are used to segment frequency data and allow for period-specific analysis of
 * service patterns and headway variations.
 *
 * @property value Database enum string for persistence
 */
enum class TimePeriod(val value: String) {
  /**
   * Weekday morning peak hours (6:00 AM - 9:00 AM). Typically highest frequency period for commute
   * traffic.
   */
  WEEKDAY_AM_PEAK("WEEKDAY_AM_PEAK"),

  /** Weekday evening peak hours (4:00 PM - 7:00 PM). Return commute with high frequency. */
  WEEKDAY_PM_PEAK("WEEKDAY_PM_PEAK"),

  /**
   * Weekday off-peak hours (all other weekday times). Includes midday, evening, and early morning
   * service.
   */
  WEEKDAY_OFF_PEAK("WEEKDAY_OFF_PEAK"),

  /**
   * Weekend service (Saturday and Sunday, all day). Typically lower frequency than peak periods.
   */
  WEEKEND("WEEKEND"),

  /**
   * Holiday service (based on calendar_dates.txt from GTFS). Includes federal holidays and observed
   * days off.
   */
  HOLIDAY("HOLIDAY");

  companion object {
    /**
     * Retrieves a TimePeriod by its database value string.
     *
     * @param value The database enum string
     * @return The matching TimePeriod
     * @throws IllegalArgumentException if value does not match any period
     */
    fun fromValue(value: String): TimePeriod {
      return entries.find { it.value == value }
        ?: throw IllegalArgumentException(
          "Unknown TimePeriod value: $value. Valid values: ${entries.joinToString { it.value }}"
        )
    }
  }
}

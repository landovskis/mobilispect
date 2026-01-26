package com.mobilispect.backend.route.domain.service

import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalTime
import kotlin.math.abs

/**
 * Detects clock-face scheduling patterns in departure times.
 *
 * Clock-face scheduling (also called cyclic scheduling) means departures occur at consistent
 * intervals that divide evenly into 60 minutes, making schedules easy to memorize. Common
 * clock-face intervals are every 10, 12, 15, 20, 30, or 60 minutes.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Clock-face_scheduling">Clock-face scheduling</a>
 */
@Service
class ClockFaceDetector {

  companion object {
    /** Valid clock-face intervals in minutes (divisors of 60). */
    val VALID_INTERVALS = listOf(10, 12, 15, 20, 30, 60)

    /** Minimum number of departures required for reliable detection. */
    const val MIN_DEPARTURES = 6

    /** Tolerance in minutes for interval matching. */
    const val TOLERANCE_MINUTES = 2

    /** Minimum percentage of intervals that must match the pattern. */
    const val MIN_CONSISTENCY_RATIO = 0.80

    /** Maximum gap in minutes before considering it a service break. */
    const val MAX_GAP_MINUTES = 90
  }

  /**
   * Detects if the given departure times follow a clock-face schedule.
   *
   * @param departures List of departure times from the first stop
   * @return The detected clock-face interval in minutes, or null if no pattern found
   */
  fun detect(departures: List<LocalTime>): Int? {
    if (departures.size < MIN_DEPARTURES) {
      return null
    }

    val sortedDepartures = departures.sorted()
    val intervals = calculateIntervals(sortedDepartures)
      .filter { it in 1..MAX_GAP_MINUTES }

    if (intervals.size < MIN_DEPARTURES - 1) {
      return null
    }

    // Check each valid clock-face interval, starting from smallest
    for (targetInterval in VALID_INTERVALS) {
      val matchingCount = intervals.count { interval ->
        abs(interval - targetInterval) <= TOLERANCE_MINUTES
      }
      val matchRatio = matchingCount.toDouble() / intervals.size

      if (matchRatio >= MIN_CONSISTENCY_RATIO) {
        return targetInterval
      }
    }

    return null
  }

  private fun calculateIntervals(sortedDepartures: List<LocalTime>): List<Int> {
    return sortedDepartures.zipWithNext { a, b ->
      Duration.between(a, b).toMinutes().toInt()
    }
  }
}

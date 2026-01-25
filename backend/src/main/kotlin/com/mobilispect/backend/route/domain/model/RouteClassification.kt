package com.mobilispect.backend.route.domain.model

/**
 * Classification of a route variant based on average stop spacing.
 *
 * Stop spacing is a key indicator of service type:
 * - LOCAL: Frequent stops typical of urban local bus service
 * - LIMITED: Limited-stop service with some stops skipped
 * - RAPID: Rapid transit with significant stop spacing
 * - SUBURBAN: Suburban service with moderate spacing
 * - REGIONAL: Regional service connecting communities
 * - EXPRESS: Express service with few stops
 * - REGIONAL_EXPRESS: Long-distance express with very wide spacing
 * - UNKNOWN: Classification cannot be determined (no spacing data)
 *
 * @property minMeters Minimum average stop spacing for this classification (inclusive)
 * @property maxMeters Maximum average stop spacing for this classification (exclusive), null if no
 *   upper bound
 */
enum class RouteClassification(val minMeters: Double, val maxMeters: Double?) {
  LOCAL(0.0, 400.0),
  LIMITED(400.0, 800.0),
  RAPID(800.0, 1500.0),
  SUBURBAN(1500.0, 3000.0),
  REGIONAL(3000.0, 5000.0),
  EXPRESS(5000.0, 10000.0),
  REGIONAL_EXPRESS(10000.0, null),
  UNKNOWN(Double.NaN, null);

  companion object {
    private val byName = entries.associateBy { it.name }

    /**
     * Determines the route classification based on average stop spacing.
     *
     * @param meters Average stop spacing in meters, or null if not available
     * @return The appropriate classification, or UNKNOWN if spacing is null, NaN, or negative
     */
    fun fromAverageSpacing(meters: Double?): RouteClassification {
      if (meters == null || meters.isNaN() || meters < 0) {
        return UNKNOWN
      }

      return when {
        meters < LOCAL.maxMeters!! -> LOCAL
        meters < LIMITED.maxMeters!! -> LIMITED
        meters < RAPID.maxMeters!! -> RAPID
        meters < SUBURBAN.maxMeters!! -> SUBURBAN
        meters < REGIONAL.maxMeters!! -> REGIONAL
        meters < EXPRESS.maxMeters!! -> EXPRESS
        else -> REGIONAL_EXPRESS
      }
    }

    /**
     * Retrieves a RouteClassification by its name string.
     *
     * @param value The classification name (e.g., "LOCAL", "RAPID")
     * @return The matching RouteClassification
     * @throws IllegalArgumentException if value does not match any classification
     */
    fun fromValue(value: String): RouteClassification {
      return byName[value]
        ?: throw IllegalArgumentException(
          "Unknown RouteClassification value: $value. Valid values: ${entries.joinToString { it.name }}"
        )
    }
  }
}

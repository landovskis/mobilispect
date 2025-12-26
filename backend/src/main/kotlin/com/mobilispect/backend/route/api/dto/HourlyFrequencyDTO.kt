package com.mobilispect.backend.route.api.dto

/**
 * Hourly frequency data for a specific route variant.
 *
 * Represents service frequency metrics in 1-hour intervals (00:00-01:00, 01:00-02:00, ..., 23:00-24:00).
 * This provides finer granularity than the standard TimePeriod-based frequency data.
 *
 * @property variantId SHA-256 hash of the stop pattern identifying the variant
 * @property serviceDate ISO-formatted date string (e.g., "2025-01-15")
 * @property hourOfDay Hour of day (0-23) when this frequency applies
 * @property averageHeadwayMinutes Average time in minutes between consecutive trips, null if irregular
 * @property minHeadwayMinutes Minimum observed headway in minutes
 * @property maxHeadwayMinutes Maximum observed headway in minutes
 * @property tripCount Number of trips during this hour
 * @property isIrregular True if the service does not follow a fixed schedule pattern
 */
data class HourlyFrequencyDTO(
    val variantId: String,
    val serviceDate: String,
    val hourOfDay: Int,
    val averageHeadwayMinutes: Double?,
    val minHeadwayMinutes: Double?,
    val maxHeadwayMinutes: Double?,
    val tripCount: Int,
    val isIrregular: Boolean
)

/**
 * Aggregated hourly frequency data across all variants for a route.
 *
 * Used when displaying route-level frequency patterns, combining metrics from all
 * variants operating on the route during each hour.
 *
 * @property routeId Onestop ID of the route
 * @property serviceDate ISO-formatted date string (e.g., "2025-01-15")
 * @property hourOfDay Hour of day (0-23) when this frequency applies
 * @property averageHeadwayMinutes Average time in minutes between consecutive trips across all variants, null if irregular
 * @property minHeadwayMinutes Minimum observed headway across all variants in minutes
 * @property maxHeadwayMinutes Maximum observed headway across all variants in minutes
 * @property tripCount Total number of trips across all variants during this hour
 * @property variantCount Number of active variants contributing trips during this hour
 * @property isIrregular True if any variant operates without a fixed schedule pattern
 */
data class RouteHourlyFrequencyDTO(
    val routeId: String,
    val serviceDate: String,
    val hourOfDay: Int,
    val averageHeadwayMinutes: Double?,
    val minHeadwayMinutes: Double?,
    val maxHeadwayMinutes: Double?,
    val tripCount: Int,
    val variantCount: Int,
    val isIrregular: Boolean
)

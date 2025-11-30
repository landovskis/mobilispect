package com.mobilispect.backend.transitanalysis.domain.service

/**
 * Service for calculating transit frequency (headway) metrics.
 *
 * Calculates average, minimum, and maximum headways for route variants
 * during different time periods (peak, off-peak, weekend).
 *
 * Constitutional Requirements:
 * - FR-008: Calculate average headway for each variant and time period
 * - FR-009: Calculate min/max headway values
 * - FR-010: Mark irregular schedules (no fixed pattern)
 *
 * Module Boundary: Domain service in transit-analysis module
 *
 * TODO: Implement following TDD - tests exist in FrequencyCalculationServiceTest
 */
interface FrequencyCalculationService {
    // TODO: Define interface methods based on frequency calculation requirements
}

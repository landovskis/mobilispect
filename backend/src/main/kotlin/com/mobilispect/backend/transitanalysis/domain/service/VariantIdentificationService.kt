package com.mobilispect.backend.transitanalysis.domain.service

import com.mobilispect.backend.transitanalysis.domain.model.RouteVariant

/**
 * Service for identifying unique route variants from trip patterns.
 *
 * A route variant is defined by its unique sequence of stops.
 * This service analyzes trip data to identify distinct stop patterns
 * and generates SHA-256 hashes for deterministic variant identification.
 *
 * Constitutional Requirements:
 * - FR-006: Identify route variants by unique stop sequences
 * - FR-007: Use SHA-256 hash of stop pattern as variant identifier
 *
 * Module Boundary: Domain service in transit-analysis module
 *
 * TODO: Implement following TDD - tests exist in VariantIdentificationServiceTest
 */
interface VariantIdentificationService {

    /**
     * Identify unique route variants from parsed route data.
     *
     * @param routes List of parsed routes with trip/stop data
     * @return List of identified route variants with SHA-256 hashed IDs
     */
    fun identifyVariants(routes: List<Any>): List<RouteVariant>
}

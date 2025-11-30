package com.mobilispect.backend.transitanalysis.domain.service

import com.mobilispect.backend.feed.model.FeedEntity
import java.nio.file.Path

/**
 * Service for orchestrating GTFS feed imports into the transit analysis module.
 *
 * This service coordinates between:
 * - GtfsParser: Parses GTFS feed archive files
 * - VariantIdentificationService: Identifies unique route variants from trip patterns
 * - FrequencyCalculationService: Calculates headways and frequency metrics
 * - Repositories: Persists agencies, routes, variants, and frequencies
 *
 * Constitutional Requirements:
 * - FR-001a: Query Transitland API to discover GTFS feed URLs
 * - FR-002: Parse and extract route, trip, stop, and schedule information
 * - FR-020: Persist imported feed data for historical analysis
 * - FR-023: Emit structured logs for all import operations
 * - FR-024: Collect metrics (duration, file size, routes, variants, errors)
 * - FR-025: Generate distributed traces for import workflows
 *
 * Module Boundary: Domain service in transit-analysis module
 *
 * TODO: Implement following TDD - tests exist in FeedImportServiceTest
 */
interface FeedImportService {

    /**
     * Import a GTFS feed from the specified archive path.
     *
     * @param feedPath Path to the GTFS ZIP archive
     * @param feedEntity Feed metadata entity
     * @return Result containing import metrics on success, or error on failure
     */
    fun importFeed(feedPath: Path, feedEntity: FeedEntity): Result<ImportResult>

    /**
     * Import result containing metrics about the import operation.
     *
     * Used for monitoring, logging, and reporting (FR-024).
     */
    data class ImportResult(
        val agenciesProcessed: Int,
        val routesProcessed: Int,
        val variantsIdentified: Int,
        val durationMillis: Long
    )
}

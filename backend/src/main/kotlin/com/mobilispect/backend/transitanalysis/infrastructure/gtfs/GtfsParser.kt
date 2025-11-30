package com.mobilispect.backend.transitanalysis.infrastructure.gtfs

import java.nio.file.Path

/**
 * GTFS feed parser interface.
 *
 * Responsible for parsing GTFS ZIP archives and extracting transit data
 * using the OneBusAway GTFS library.
 *
 * Constitutional Requirements:
 * - FR-002: Parse and extract route, trip, stop, and schedule information
 *
 * Module Boundary: Infrastructure layer in transit-analysis module
 *
 * TODO: Implement using OneBusAway GTFS library v1.3.4
 */
interface GtfsParser {

    /**
     * Parse a GTFS feed archive.
     *
     * @param feedPath Path to the GTFS ZIP file
     * @return Result containing parsed data on success, or error on failure
     */
    fun parse(feedPath: Path): Result<ParsedGtfsData>
}

/**
 * Placeholder for parsed GTFS data.
 * Will be replaced with actual structure once GtfsParser is implemented.
 *
 * TODO: Define proper structure based on OneBusAway GTFS library
 */
data class ParsedGtfsData(
    val agencies: List<Any>,
    val routes: List<Any>
)

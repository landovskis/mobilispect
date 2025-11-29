package com.mobilispect.backend.transitanalysis

import org.springframework.modulith.ApplicationModule

/**
 * Transit Analysis Module - Bounded Context for Transit Route Frequency Analysis
 *
 * Responsibilities:
 * - Import and process transit feed data from GTFS sources
 * - Calculate service frequencies by route variant and time period
 * - Identify route variants through stop pattern analysis
 * - Detect common sections where routes overlap
 * - Provide frequency analysis API for transit planning
 *
 * Public API:
 * - REST endpoints for region/agency/route/frequency queries
 * - Feed import operations and status tracking
 *
 * Events Published:
 * - FeedImportCompleted: When GTFS feed processing finishes
 * - FrequencyCalculationCompleted: When frequency calculations complete
 * - RouteVariantIdentified: When new route variant is discovered
 *
 * Events Consumed:
 * - None (foundational analysis module)
 *
 * Database Ownership:
 * - agencies: Transit agencies operating within regions
 * - routes: Transit routes offered by agencies
 * - route_variants: Unique service patterns identified by stop sequences
 * - frequencies: Service headways by variant and time period
 * - common_sections: Geographic segments where routes overlap
 * - common_section_variants: Many-to-many between common sections and variants
 *
 * Dependencies:
 * - feed module: References FeedEntity via feed_onestop_id FK, MetropolitanRegion via foreign key
 * - No cross-module database access (communicates via events and REST APIs)
 */
@ApplicationModule(
    displayName = "Transit Analysis",
    allowedDependencies = ["feed"]
)
class TransitAnalysisModule

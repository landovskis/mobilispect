# Research: Transit Route Frequency Analysis

**Feature**: 003-transit-route-frequency
**Date**: 2025-11-27
**Phase**: 0 - Technical Research

## Overview

This document consolidates research findings for implementing transit route frequency analysis. Research focused on GTFS data processing, route variant identification algorithms, frequency calculation methodologies, and integration with Transitland API.

## Research Areas

### 1. GTFS Data Processing Libraries

**Decision**: Use OneBusAway GTFS library for Java/Kotlin

**Rationale**:
- **Mature & Maintained**: Active development since 2008, widely used in transit industry
- **Complete GTFS Support**: Handles all GTFS specification files (agency, routes, trips, stops, stop_times, calendar, calendar_dates, shapes)
- **Kotlin Compatibility**: Java library works seamlessly with Kotlin on JVM
- **Validation Built-in**: Includes GTFS validation rules to detect malformed feeds
- **Performance**: Optimized for parsing large feeds (handles multi-MB ZIP files efficiently)
- **Type Safety**: Strong typing aligns with constitutional requirement for value classes

**Alternatives Considered**:
- **Custom Parser**: Rejected due to complexity of GTFS spec (14+ file types, complex relationships)
- **Python GTFS-kit**: Rejected due to language mismatch (requires separate service)
- **CSV Parser + Manual Mapping**: Rejected due to lack of validation and relationship handling

**Implementation Notes**:
```kotlin
// Dependency
implementation("org.onebusaway:onebusaway-gtfs:1.4.15")

// Usage pattern
val reader = GtfsReader()
reader.setInputLocation(feedFile)
val dao = reader.run()
val routes = dao.getAllRoutes()
val trips = dao.getAllTrips()
val stopTimes = dao.getStopTimesForTrip(trip)
```

### 2. Route Variant Identification Algorithm

**Decision**: Hash-based identification using ordered stop sequence with SHA-256

**Rationale**:
- **Stability**: Hash remains constant when stop pattern unchanged across feed updates
- **Uniqueness**: SHA-256 provides collision resistance for variant identification
- **Deterministic**: Same stop sequence always produces same hash
- **Efficient**: O(n) complexity for hash generation, O(1) for variant lookup
- **Alignment**: Supports clarification decision to use content-based identifiers

**Algorithm Design**:
```kotlin
fun generateVariantHash(stopIds: List<StopId>): VariantHash {
    val concatenated = stopIds.joinToString(separator = "|") { it.value }
    val bytes = concatenated.toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(bytes)
    return VariantHash(hashBytes.toHexString())
}
```

**Alternatives Considered**:
- **Agency-Provided Trip IDs**: Rejected due to inconsistency across feed versions
- **Sequential Integer IDs**: Rejected due to instability when new variants inserted
- **Stop Pattern Comparison**: Rejected due to O(n²) complexity for variant matching

**Edge Cases Handled**:
- Variants with minimal differences (1-2 stops): Still create separate hashes, flag as similar
- Circular routes with different starting points: Normalize to canonical starting point
- Bi-directional routes: Include direction indicator in hash input

### 3. Frequency Calculation Methodology

**Decision**: Average headway by time period using scheduled departure times

**Rationale**:
- **Industry Standard**: Average headway is standard transit metric (matches agency publications)
- **Time Period Granularity**: Weekday peak/off-peak/weekend aligns with service planning practices
- **Accurate Representation**: Uses actual schedule data from stop_times.txt
- **User Expectation**: Matches how transit planners think about service levels

**Calculation Formula**:
```kotlin
fun calculateFrequency(departures: List<LocalTime>, period: TimePeriod): Frequency {
    val periodDepartures = departures.filter { it in period.timeRange }
    if (periodDepartures.size < 2) return Frequency.IRREGULAR

    val headways = periodDepartures.zipWithNext { a, b ->
        Duration.between(a, b).toMinutes()
    }

    val averageHeadway = headways.average()
    val minHeadway = headways.minOrNull() ?: 0.0
    val maxHeadway = headways.maxOrNull() ?: 0.0

    return Frequency(
        average = averageHeadway,
        min = minHeadway,
        max = maxHeadway,
        variability = maxHeadway - minHeadway
    )
}
```

**Time Period Definitions** (from spec clarifications):
- Weekday AM Peak: 6:00-9:00 AM
- Weekday PM Peak: 4:00-7:00 PM
- Weekday Off-Peak: All other weekday hours (9:00 AM-4:00 PM, 7:00 PM-6:00 AM)
- Weekend: Saturday-Sunday all day
- Holiday: Based on calendar_dates.txt exception types

**Alternatives Considered**:
- **Median Headway**: Rejected as less intuitive for users than average
- **Peak Frequency Only**: Rejected as doesn't show service variability
- **Real-time Vehicle Positions**: Rejected as out of scope (scheduled data only)

### 4. Common Section Detection Algorithm

**Decision**: Longest Common Subsequence (LCS) with minimum threshold of 3 consecutive stops

**Rationale**:
- **Robust**: LCS algorithm handles stop pattern variations gracefully
- **Configurable Threshold**: 3-stop minimum balances noise vs meaningful overlaps
- **Direction-Aware**: Requires same direction/sequence (not just common stops)
- **Efficient**: Dynamic programming implementation O(m*n) for two routes

**Algorithm Design**:
```kotlin
fun detectCommonSections(
    variant1: RouteVariant,
    variant2: RouteVariant
): List<CommonSection> {
    val stops1 = variant1.stopPattern
    val stops2 = variant2.stopPattern

    val lcs = longestCommonSubsequences(stops1, stops2)

    return lcs
        .filter { it.length >= 3 } // Minimum 3 consecutive stops
        .map { subsequence ->
            CommonSection(
                stops = subsequence.stops,
                startStop = subsequence.first,
                endStop = subsequence.last,
                contributingVariants = setOf(variant1.id, variant2.id)
            )
        }
}
```

**Alternatives Considered**:
- **Simple Set Intersection**: Rejected as doesn't preserve sequence/direction
- **Spatial Distance Clustering**: Rejected as too complex and error-prone with GPS coordinates
- **2-Stop Minimum**: Rejected as creates too many trivial common sections

**Performance Optimization**:
- Pre-filter route pairs by geographic bounding box before LCS computation
- Cache common sections to avoid recomputation on each frequency query
- Batch process common section detection during feed import (not real-time)

### 5. Transitland API Integration

**Decision**: Use Transitland v2 REST API with HTTP client (OkHttp + Retrofit)

**Rationale**:
- **Official Catalog**: Transitland maintains curated GTFS feed registry with quality metadata
- **Region Support**: Provides metro area definitions matching spec requirements
- **Feed Metadata**: Includes feed URLs, update schedules, bounding boxes
- **Well-Documented**: Comprehensive API documentation with examples
- **Rate Limits**: Reasonable limits (1000 requests/hour) sufficient for batch operations

**API Endpoints Used**:
- `GET /api/v2/rest/feeds`: List feeds by geographic region
- `GET /api/v2/rest/feeds/{id}`: Get specific feed details
- `GET /api/v2/rest/metro_areas`: List metropolitan areas

**Authentication**: API key required (free tier available, stored in application secrets)

**Implementation Pattern**:
```kotlin
interface TransitlandClient {
    @GET("/api/v2/rest/feeds")
    suspend fun getFeedsByRegion(
        @Query("bbox") boundingBox: String,
        @Query("spec") spec: String = "gtfs"
    ): List<FeedResponse>

    @GET("/api/v2/rest/metro_areas")
    suspend fun getMetroAreas(): List<MetroAreaResponse>
}
```

**Alternatives Considered**:
- **Manual Feed URL Configuration**: Rejected as requires ongoing maintenance
- **MobilityData Catalog API**: Considered but Transitland has better region metadata
- **Direct Agency Websites**: Rejected as inconsistent formats and availability

**Error Handling**:
- Retry logic with exponential backoff for transient failures
- Fallback to cached feed URLs if Transitland API unavailable
- Alert operators when feeds haven't updated in expected timeframe

### 6. Caching Strategy

**Decision**: Redis for computed frequencies and feed metadata with TTL-based invalidation

**Rationale**:
- **Performance**: Avoid recomputing frequencies on every query (computationally expensive)
- **Constitutional Alignment**: Redis 8.2 specified in tech stack
- **TTL Support**: Automatic cache invalidation after feed update periods
- **Structured Data**: Redis supports complex data types (hashes, sets) for frequency data

**Cache Keys**:
```kotlin
// Frequency cache (TTL: 24 hours)
frequency:{variantHash}:{timePeriod}:{date}

// Feed metadata cache (TTL: 1 hour)
feed:metadata:{agencyId}

// Region data cache (TTL: 7 days)
region:metro:{regionId}
```

**Invalidation Strategy**:
- Time-based: TTL set based on feed update frequency (daily feeds = 24h TTL)
- Event-based: Invalidate on `FeedImportCompleted` event
- Manual: Admin endpoint to force cache clear for specific region/agency

**Alternatives Considered**:
- **In-Memory Cache (Caffeine)**: Rejected as doesn't persist across application restarts
- **No Caching**: Rejected due to performance impact (recompute on every query)
- **Database Materialized Views**: Rejected as less flexible than Redis

### 7. Observability Implementation

**Decision**: Micrometer + OpenTelemetry for metrics/traces, Logback for structured logs

**Rationale**:
- **Spring Boot Integration**: Micrometer natively integrated with Spring Boot Actuator
- **Grafana Cloud Compatible**: Micrometer exports to Prometheus format (Grafana Cloud ingests)
- **OpenTelemetry Standard**: Industry standard for distributed tracing
- **Structured Logging**: Logback with JSON encoder for machine-parseable logs

**Metrics to Collect** (FR-024):
```kotlin
// Feed processing metrics
"feed.processing.duration" (Timer)
"feed.processing.size.bytes" (Distribution Summary)
"feed.processing.routes.count" (Counter)
"feed.processing.variants.identified" (Counter)
"feed.processing.errors" (Counter)

// Frequency calculation metrics
"frequency.calculation.duration" (Timer)
"frequency.query.cache.hits" (Counter)
"frequency.query.cache.misses" (Counter)
```

**Trace Spans** (FR-025):
```kotlin
@Traced // OpenTelemetry annotation
fun importFeed(feedUrl: String): FeedImportResult {
    tracer.span("fetch-feed") { fetchFeedFile(feedUrl) }
    tracer.span("parse-gtfs") { parseGtfsData(file) }
    tracer.span("identify-variants") { identifyRouteVariants(routes) }
    tracer.span("calculate-frequencies") { calculateAllFrequencies(variants) }
    tracer.span("detect-common-sections") { detectCommonSections(variants) }
}
```

**Log Structure** (FR-023):
```json
{
  "timestamp": "2025-11-27T10:15:30.456Z",
  "level": "INFO",
  "logger": "FeedImportService",
  "message": "Feed import completed",
  "context": {
    "feedUrl": "https://example.com/gtfs.zip",
    "agencyId": "agency-123",
    "processingDuration": "45.2s",
    "routesProcessed": 125,
    "variantsIdentified": 387
  },
  "traceId": "abc123...",
  "spanId": "def456..."
}
```

**Alternatives Considered**:
- **Direct Grafana Cloud SDK**: Rejected as less portable than OpenTelemetry standard
- **Custom Metrics**: Rejected as Micrometer provides better tooling and dashboards

## Summary

All research areas have clear decisions with rationale documented. Key technical choices:

1. **GTFS Processing**: OneBusAway library (mature, validated, Kotlin-compatible)
2. **Variant Identification**: SHA-256 hash of ordered stop sequences (stable, unique)
3. **Frequency Calculation**: Average headway by time period (industry standard)
4. **Common Sections**: LCS algorithm with 3-stop minimum (robust, efficient)
5. **Transitland Integration**: REST API with OkHttp/Retrofit (official catalog, well-documented)
6. **Caching**: Redis with TTL-based invalidation (performant, constitutional alignment)
7. **Observability**: Micrometer + OpenTelemetry + Logback (Spring Boot native, Grafana compatible)

All decisions align with constitutional requirements (Spring Boot, Kotlin, PostgreSQL, Redis, Grafana Cloud) and satisfy functional requirements from the specification. No open questions or "NEEDS CLARIFICATION" items remain.

**Next Phase**: Proceed to Phase 1 (Design & Contracts) for data model design and API contract generation.

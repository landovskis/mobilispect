# ADR 0006: Transitland API Integration for Feed Discovery

**Date**: 2025-11-27
**Status**: Accepted
**Feature**: 003-transit-route-frequency

## Context

The transit route frequency feature must identify and discover GTFS feeds for analysis. Manually maintaining a list of feed URLs for 1000+ transit agencies is unsustainable; feeds change URLs, cease operation, or launch regularly.

The team needs:
1. **Feed Discovery**: Find GTFS feeds by geographic region (metro area, city, county)
2. **Feed Metadata**: Access feed information (agency, update frequency, bounding box, URL)
3. **Quality Signal**: Identify maintained vs abandoned feeds
4. **Programmatic Access**: API-based discovery for automated batch processing

Multiple options exist:
- Transitland: Community-curated GTFS feed registry
- MobilityData Catalog: Global transit data catalog
- Manual feed configuration: Direct agency URLs
- Transitland is the most mature, well-documented, and region-aware option

## Decision

**Use Transitland v2 REST API with Spring WebClient for GTFS feed discovery and metadata retrieval.**

### Rationale

1. **Official Catalog**: Transitland maintains curated registry of 1000+ public GTFS feeds; continuously updated by community and agencies
2. **Region Support**: Provides metro area definitions and geographic bounding boxes matching spec regional requirements
3. **Feed Metadata**: Returns feed URLs, update frequency, geographic bounds, operator information
4. **Well-Documented**: Comprehensive API documentation with examples; low learning curve
5. **Rate Limits**: Reasonable limits (1000 requests/hour) sufficient for batch operations during imports
6. **Spring Native Integration**: Spring WebClient is core Spring Boot component providing:
   - Better integration with Spring Observability (Micrometer, OpenTelemetry)
   - Configuration via Spring properties (no separate client setup)
   - Dependency injection for lifecycle management
   - Reactive streams support for non-blocking I/O
7. **Kotlin Coroutines**: Native support via `awaitBody()` for suspending functions
8. **Constitutional Alignment**: Matches Spring Boot 3.5.3+ stack requirement; automatic observability integration

### Implementation

```kotlin
// Spring configuration
@Configuration
class WebClientConfig {
    @Bean
    fun transitlandWebClient(builder: WebClientBuilder): WebClient =
        builder
            .baseUrl("https://api.transitland.app")
            .defaultHeader("Authorization", "Bearer ${apiKey}")
            .build()
}

// Service implementation with Kotlin coroutines
@Service
class TransitlandClient(private val webClient: WebClient) {

    suspend fun getFeedsByRegion(bbox: BoundingBox): List<FeedResponse> {
        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v2/rest/feeds")
                    .queryParam("bbox", bbox.toQueryString())
                    .queryParam("spec", "gtfs")
                    .build()
            }
            .retrieve()
            .bodyToMono(FeedsResponse::class.java)
            .awaitSingle()
            .feeds
    }

    suspend fun getMetroAreas(): List<MetroAreaResponse> {
        return webClient.get()
            .uri("/api/v2/rest/metro_areas")
            .retrieve()
            .bodyToMono(MetroAreasResponse::class.java)
            .awaitSingle()
            .metroAreas
    }

    suspend fun getFeed(feedId: String): FeedResponse {
        return webClient.get()
            .uri("/api/v2/rest/feeds/{id}", feedId)
            .retrieve()
            .bodyToMono(FeedResponse::class.java)
            .awaitSingle()
    }
}

// Usage in feed discovery service
@Service
class FeedDiscoveryService(private val transitlandClient: TransitlandClient) {

    suspend fun discoverFeedsForRegion(regionId: String): List<DiscoveredFeed> {
        val region = transitlandClient.getMetroArea(regionId)
        val feeds = transitlandClient.getFeedsByRegion(region.bbox)

        return feeds.map { feed ->
            DiscoveredFeed(
                agencyId = feed.operators.first().id,
                feedUrl = feed.urls.gtfs,
                updateFrequency = feed.updateFrequency,
                lastUpdate = feed.lastUpdated,
                boundingBox = feed.bbox
            )
        }
    }
}
```

## API Endpoints Used

1. **List Feeds by Region**
   ```
   GET /api/v2/rest/feeds?bbox=min_lon,min_lat,max_lon,max_lat&spec=gtfs
   ```
   Returns feeds in geographic area with full metadata

2. **Get Feed Details**
   ```
   GET /api/v2/rest/feeds/{feed_id}
   ```
   Returns specific feed metadata including URLs and update schedule

3. **List Metro Areas**
   ```
   GET /api/v2/rest/metro_areas
   ```
   Returns all metro area definitions for region-based discovery

## Authentication & Rate Limits

- **Auth**: Free API key required (obtain at transitland.app/register)
- **Storage**: Key stored in application secrets (Spring Cloud Config or environment variable)
- **Rate Limit**: 1000 requests/hour (sufficient for batch imports)
- **Retry Strategy**: Exponential backoff (1s → 2s → 4s) for 429 responses

## Error Handling & Resilience

```kotlin
// Retry logic with exponential backoff
suspend fun <T> withRetry(
    maxRetries: Int = 3,
    block: suspend () -> T
): T {
    var lastException: Exception? = null

    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: HttpServerErrorException) {
            lastException = e
            val delayMs = 1000L * (2 shl attempt)  // Exponential backoff
            delay(delayMs)
        }
    }

    throw lastException ?: Exception("All retries failed")
}

// Fallback to cached feed URLs if API unavailable
suspend fun getFeedsWithFallback(regionId: String): List<DiscoveredFeed> {
    return try {
        transitlandClient.getFeedsByRegion(regionId)
    } catch (e: Exception) {
        logger.warn("Transitland API unavailable, using cached feeds", e)
        feedCache.getCachedFeeds(regionId)
    }
}
```

## Observability

Automatic Spring WebClient observability:
- Request duration metrics (Micrometer Timer)
- HTTP status code distribution
- Exception rate tracking
- Distributed trace spans via OpenTelemetry

## Consequences

### Positive

1. **Reliable Feed Discovery**: Eliminates manual URL maintenance; automatically finds new agencies
2. **Region Support**: Geographic metadata matches spec requirements for metro area analysis
3. **Quality Metadata**: Feed update frequency helps schedule appropriate refresh intervals
4. **Low Operational Burden**: Transitland maintained by community; no data management on our side
5. **Spring Integration**: WebClient provides observability and configuration benefits over HTTP libraries
6. **Non-Blocking I/O**: Reactive WebClient prevents thread starvation in high-concurrency scenarios
7. **Historical Stability**: Transitland maintains historical feed data for archive/research

### Negative

1. **External Dependency**: Reliant on Transitland API availability (SLA managed by others)
   - Mitigation: Caching strategy and fallback to previously cached feed URLs
2. **Rate Limit Throttling**: 1000 req/hr limit may be exceeded if discovering 1000+ feeds
   - Mitigation: Batch discovery operations during off-peak, cache results for 24hrs
3. **Data Accuracy**: Feed URLs may become stale (agencies change URLs without notice)
   - Mitigation: Validate URLs during import; alert on 404 responses

## Alternatives Considered

### 1. Retrofit + OkHttp (Rejected)

**Rationale**: Not ideal for Spring Boot ecosystem
- Not native to Spring; requires separate HTTP client configuration
- Less integrated with Spring Actuator and Micrometer observability
- Additional dependency management overhead
- No native Kotlin coroutine support (requires kotlinx-coroutines-reactor bridge)

### 2. RestTemplate (Deprecated) (Rejected)

**Rationale**: Deprecated in favor of WebClient
- Spring Team deprecated RestTemplate in Spring 6.0 announcements
- No reactive support; synchronous only
- No native Kotlin integration

### 3. Manual Feed Configuration (Rejected)

**Rationale**: Unsustainable at scale
- Requires operators to manually research and maintain feed URLs
- Misses new agencies launching transit service
- Stale URLs and dead feeds accumulate over time
- Does not support geographic region discovery

### 4. MobilityData Catalog API (Considered but Rejected)

**Rationale**: Transitland offers better region metadata
- MobilityData is more comprehensive globally but less region-focused
- No metro area definitions; only individual feed locations
- Heavier API with more complex response structures

### 5. Direct Agency Websites (Rejected)

**Rationale**: Inconsistent, unmaintainable, unreliable
- GTFS URLs vary wildly (some agencies: /gtfs.zip, others: /feeds/latest, others: /schedule-data/gtfs)
- Websites change without notice
- No machine-readable feed list; manual research required for each agency

## Related Decisions

- **ADR 0004**: OneBusAway GTFS library parses feeds discovered via Transitland
- **ADR 0007**: Frequency calculation depends on importing feeds sourced from Transitland

## Open Questions

None. Implementation ready.

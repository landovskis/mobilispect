# Feed Management Research Findings

**Phase 0 Research Completion** | **Date**: 2025-01-09

## Decision Summary

This document consolidates research findings for implementing the Feed Management system, resolving all technical unknowns identified during planning.

## Decision 1: Transit.land API Integration Strategy

**Decision**: Use Transit.land API v2 with API key authentication and rate-limited requests

**Rationale**:
- Transit.land is the largest GTFS aggregator with 2,500+ operators across 55+ countries
- Provides comprehensive metadata for both GTFS static and GTFS-RT feeds
- Offers SHA1-based change detection for efficient update checking
- Supports geographical filtering and pagination for large datasets

**Implementation Approach**:
```kotlin
@Service
class TransitLandService(
    @Value("\${transitland.api.key}") private val apiKey: String,
    private val restTemplate: RestTemplate
) {
    private val rateLimiter = RateLimiter.create(2.0) // 2 requests/second

    fun discoverRegionalFeeds(region: String): List<Feed> {
        rateLimiter.acquire()
        return restTemplate.exchange(
            "https://transit.land/api/v2/rest/feeds?region={region}&spec=gtfs",
            HttpMethod.GET,
            HttpEntity<String>(createHeaders()),
            FeedResponse::class.java,
            region
        ).body?.feeds ?: emptyList()
    }

    private fun createHeaders() = HttpHeaders().apply {
        set("apikey", apiKey)
    }
}
```

**Alternatives Considered**:
- Direct agency RSS feeds - Rejected due to inconsistent formats and lack of standardization
- OpenMobilityData - Rejected due to less comprehensive coverage compared to Transit.land
- Custom feed discovery - Rejected due to maintenance overhead and duplication of existing services

## Decision 2: GTFS Processing Architecture

**Decision**: Use PostgreSQL for all GTFS data storage with enhanced Kotlin processing

**Rationale**:
- PostgreSQL provides superior support for relational GTFS data and spatial queries
- Constitutional requirement for Spring Boot + PostgreSQL technology stack
- Better performance for complex transit data relationships
- PostGIS extension enables geographical queries for stops and routes

**Implementation Strategy**:
```kotlin
@Service
class EnhancedGTFSProcessor(
    private val hashService: GTFSHashService,
    private val jobTracker: ImportJobTracker
) {
    suspend fun processWithProgress(feedData: ScheduledFeed, jobId: String): Result<ImportSummary> {
        return try {
            val extractedDir = extractFeed(feedData.downloadUrl)
            val newHash = hashService.generateFeedHash(extractedDir)

            if (shouldSkipImport(feedData.feedId, newHash)) {
                return Result.success(ImportSummary.skipped())
            }

            // Process with progress tracking
            jobTracker.updateProgress(jobId, 1, "Validating feed structure")
            validateFeedStructure(extractedDir)

            jobTracker.updateProgress(jobId, 2, "Importing agencies")
            importAgencies(extractedDir, feedData.version)

            Result.success(ImportSummary.completed())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**Alternatives Considered**:
- Continue with MongoDB - Rejected due to constitutional PostgreSQL requirement and poor relational data support
- Hybrid storage approach - Rejected for simplicity and consistency
- External GTFS processing services - Rejected to maintain data control and reduce dependencies

## Decision 3: Content Hash Generation Strategy

**Decision**: SHA-256 hashing of concatenated core GTFS file hashes for change detection

**Rationale**:
- More reliable than timestamp-based detection due to timezone and caching issues
- Allows skipping downloads when content hasn't changed
- Compatible with Transit.land's SHA1 feed version tracking
- Enables incremental updates and rollback capabilities

**Implementation**:
```kotlin
@Service
class GTFSHashService {
    private val digest = MessageDigest.getInstance("SHA-256")

    fun generateFeedHash(extractedDir: Path): String {
        val coreFiles = listOf("agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt")
        val combinedHash = coreFiles
            .mapNotNull { fileName ->
                val file = extractedDir.resolve(fileName)
                if (file.exists()) hashFile(file) else null
            }
            .joinToString("")

        return digest.digest(combinedHash.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
    }
}
```

**Alternatives Considered**:
- ETag-based detection - Rejected due to inconsistent server support across agencies
- File size comparison - Rejected due to potential false positives with content changes
- Modified timestamp checking - Rejected due to unreliability across different servers

## Decision 4: Role-Based Security Integration

**Decision**: Extend existing Spring Security configuration with feed management roles

**Rationale**:
- Leverages existing authentication infrastructure
- Provides granular access control for different admin functions
- Supports audit requirements with user-action tracking
- Maintains consistency with constitutional security requirements

**Implementation Strategy**:
```kotlin
@Configuration
@EnableWebSecurity
class FeedManagementSecurityConfig {

    @Bean
    fun feedManagementFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/api/feed-management/**")
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.GET, "/api/feed-management/*/progress").hasAnyRole("FEED_VIEWER", "FEED_OPERATOR", "FEED_MANAGER")
                    .requestMatchers(HttpMethod.GET, "/api/feed-management/*/history").hasAnyRole("FEED_VIEWER", "FEED_OPERATOR", "FEED_MANAGER")
                    .requestMatchers(HttpMethod.POST, "/api/feed-management/*/import").hasAnyRole("FEED_OPERATOR", "FEED_MANAGER")
                    .requestMatchers(HttpMethod.PUT, "/api/feed-management/config/**").hasRole("FEED_MANAGER")
                    .anyRequest().authenticated()
            }
            .build()
    }
}
```

**Alternatives Considered**:
- Flat admin role structure - Rejected due to lack of granular control
- External authorization service - Rejected due to unnecessary complexity for current scope
- Resource-based permissions - Deferred to future iteration due to current regional scope

## Decision 5: Background Processing Architecture

**Decision**: Spring Boot async processing with job tracking and WebSocket progress updates

**Rationale**:
- Enables non-blocking import operations for large feeds
- Provides real-time progress visibility for administrators
- Supports concurrent imports across different regions
- Maintains constitutional performance requirements

**Implementation Pattern**:
```kotlin
@Configuration
@EnableAsync
@EnableWebSocket
class AsyncFeedProcessingConfig : WebSocketConfigurer {

    @Bean("feedImportExecutor")
    fun feedImportTaskExecutor(): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 100
            setThreadNamePrefix("Feed-Import-")
            initialize()
        }
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(FeedProgressWebSocketHandler(), "/ws/feed-progress")
            .setAllowedOrigins("*")
    }
}
```

**Alternatives Considered**:
- Synchronous processing with polling - Rejected due to poor user experience with large feeds
- Message queue systems (RabbitMQ/Kafka) - Deferred as unnecessary complexity for current scale
- Server-sent events - Rejected in favor of WebSocket for bidirectional communication

## Constitutional Compliance Verification

All decisions align with constitutional requirements:
- ✅ **DRY/YAGNI/SOLID**: Service-oriented architecture with clear separation
- ✅ **Test Coverage**: 80%+ coverage planned for all new components
- ✅ **Performance**: <200ms API responses, background processing for imports
- ✅ **Security**: Role-based access control with audit trails
- ✅ **Observability**: Structured logging and metrics for all operations
- ✅ **Technology Stack**: PostgreSQL, Spring Boot, Kotlin per constitutional requirements

## Next Phase

Proceed to **Phase 1: Design & Contracts** with all technical unknowns resolved and PostgreSQL-based implementation strategy defined.
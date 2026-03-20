# ADR 0011: GTFS-RT Parallel Ingestion Architecture

**Date**: 2026-02-03
**Status**: Proposed
**Related**: ADR 0003 - Spring Batch Feed Discovery, ADR 0009 - Spring Modulith Module Boundaries, ADR 0010 - API-Driven Module Communication
**Constitutional Requirement**: Principle I - Modular Monolith Ownership, Principle IV - Performance & Reliability Targets

## Context

The platform needs to ingest GTFS Realtime (GTFS-RT) data — vehicle positions, trip updates, and service alerts — from potentially hundreds of transit feeds. These feeds are protobuf-encoded HTTP endpoints that must be polled at regular intervals (typically every 15–60 seconds) to provide near-real-time transit information.

### Key Challenges

1. **Scale**: Hundreds of feeds must be polled concurrently. Sequential polling would take minutes per cycle, far exceeding acceptable staleness.
2. **Redundant Processing**: Many feeds do not change between polling intervals. Decoding protobuf and writing to the database for unchanged data wastes CPU and I/O.
3. **Resilience**: Individual feeds fail independently (network errors, server outages, rate limiting). A single failing feed must not block or degrade ingestion of others.
4. **Discovery**: GTFS-RT feed URLs are already captured by the existing daily feed discovery pipeline (ADR 0003) as `realtimeFeedUrl` on the `Feed` entity. A separate discovery mechanism would duplicate infrastructure and risk inconsistency.
5. **Module Boundaries**: The ingestion logic must respect modulith boundaries (ADR 0009), depending on the `feed` module only through its public API and shared domain types.

### Existing Infrastructure

The existing feed discovery pipeline (`FeedDiscoveryReader` → `FeedDiscoveryProcessor` → `FeedDiscoveryWriter`) already:

- Fetches feed metadata from Transit.land including `realtimeFeedUrl`
- Persists `realtimeFeedUrl` on `FeedEntity`
- Stores `FeedAuthentication` credentials (API keys, OAuth2)
- Tracks feed status via `FeedStatus` (ACTIVE, INACTIVE, ERROR)

The `Feed` domain model already has all fields needed:

```kotlin
data class Feed(
    val feedId: FeedId,
    val specType: FeedSpecType,       // GTFS or GTFS_RT
    val downloadUrl: String,
    val realtimeFeedUrl: String?,     // GTFS-RT endpoint (already populated)
    val status: FeedStatus,
    // ...
)
```

## Decision

### 1. On-Demand Discovery Integration

GTFS-RT feed discovery reuses the existing feed discovery pipeline. At ingestion time, the service queries for feeds with realtime URLs:

```kotlin
feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE)
```

**If no feeds are found**, the ingestion service triggers feed discovery before proceeding. This ensures the system bootstraps itself without manual intervention or waiting for the daily discovery schedule.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Ingestion Cycle (every 30 seconds)                        │
│                                                                              │
│  ┌───────────────────────────┐                                              │
│  │ Query feeds with          │                                              │
│  │ realtimeFeedUrl != null   │                                              │
│  └─────────────┬─────────────┘                                              │
│                │                                                             │
│                ▼                                                             │
│        ┌───────────────┐                                                    │
│        │ Feeds found?  │                                                    │
│        └───────┬───────┘                                                    │
│                │                                                             │
│       ┌────────┴────────┐                                                   │
│       │ No              │ Yes                                               │
│       ▼                 ▼                                                   │
│  ┌─────────────┐   ┌─────────────────────────────────────────────────────┐  │
│  │ Trigger     │   │ Parallel Fetch → Dedupe → Process → Persist        │  │
│  │ Discovery   │   └─────────────────────────────────────────────────────┘  │
│  │ (async)     │                                                            │
│  └──────┬──────┘                                                            │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────┐                                                            │
│  │ Wait for    │                                                            │
│  │ completion  │                                                            │
│  └──────┬──────┘                                                            │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────┐                                                            │
│  │ Re-query    │──────▶ Proceed with ingestion                              │
│  │ feeds       │                                                            │
│  └─────────────┘                                                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

No new discovery scheduler, API client, or batch job is introduced. The existing `FeedDiscoveryBatchService.discoverAll()` is invoked when needed.

### 2. On-Demand Discovery Implementation

```kotlin
@Service
class GtfsRtIngestionService(
    private val feedRepository: FeedRepository,
    private val feedDiscoveryBatchService: FeedDiscoveryBatchService,
    private val fetcher: ParallelGtfsRtFetcher,
    private val processor: GtfsRtProcessingService,
    private val meterRegistry: MeterRegistry
) {
    private val logger = KotlinLogging.logger {}
    private val discoveryInProgress = AtomicBoolean(false)

    @Scheduled(fixedRateString = "\${gtfsrt.ingestion.interval-ms:30000}")
    fun ingest() = runBlocking {
        val feeds = getOrDiscoverFeeds()

        if (feeds.isEmpty()) {
            logger.info { "No GTFS-RT feeds available after discovery attempt" }
            return@runBlocking
        }

        // Proceed with parallel ingestion
        fetcher.fetchAllFeeds(feeds).collect { result ->
            processor.process(result)
        }
    }

    private suspend fun getOrDiscoverFeeds(): List<Feed> {
        val feeds = feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE)

        if (feeds.isNotEmpty()) {
            return feeds
        }

        // No feeds found — trigger discovery if not already running
        if (discoveryInProgress.compareAndSet(false, true)) {
            try {
                logger.info { "No GTFS-RT feeds found, triggering feed discovery" }
                meterRegistry.counter("gtfsrt.discovery.triggered").increment()

                val result = feedDiscoveryBatchService.discoverAll()

                logger.info {
                    mapOf(
                        "event" to "gtfsrt_discovery_complete",
                        "feeds_discovered" to result.feedsFound,
                        "regions_discovered" to result.regionsFound
                    )
                }
            } finally {
                discoveryInProgress.set(false)
            }
        } else {
            logger.info { "Discovery already in progress, waiting..." }
            // Wait for in-progress discovery to complete
            while (discoveryInProgress.get()) {
                delay(1000)
            }
        }

        // Re-query after discovery
        return feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE)
    }
}
```

### 3. Parallel Fetch with Kotlin Coroutines

Use Kotlin coroutines with a bounded IO dispatcher and semaphore for backpressure to fetch feeds concurrently:

```kotlin
private val dispatcher = Dispatchers.IO.limitedParallelism(parallelism = 50)
private val semaphore = Semaphore(permits = 100)

suspend fun fetchAllFeeds(feeds: List<Feed>): Flow<GtfsRtFetchResult> = channelFlow {
    feeds
        .mapNotNull { feed ->
            feed.realtimeFeedUrl?.let { url -> feed to url }
        }
        .map { (feed, url) ->
            async(dispatcher) {
                semaphore.withPermit {
                    fetchWithResilience(feed, url)
                }
            }
        }
        .forEach { send(it.await()) }
}
```

### 4. Three-Layer Deduplication to Skip Unchanged Feeds

Processing is skipped when the downloaded content has not changed, using three checks applied in order of cost:

```
HTTP 304 (free) → Content Hash (cheap) → GTFS-RT Timestamp (after decode)
```

**Layer 1 — HTTP Conditional Requests**: Send `If-None-Match` (ETag) and `If-Modified-Since` headers. A `304 Not Modified` response skips all further processing at zero transfer cost.

**Layer 2 — Content Hash**: SHA-256 hash of the response body compared against the last known hash stored in Redis. Skips protobuf decoding and persistence if the content is byte-identical.

**Layer 3 — GTFS-RT Header Timestamp**: After decoding the protobuf, compare `header.timestamp` against the last processed timestamp. Skips persistence if the feed producer regenerated identical content with a new HTTP response.

Per-feed deduplication state is stored in Redis with a 24-hour TTL:

```kotlin
data class GtfsRtFeedState(
    val feedId: FeedId,
    val contentHash: String?,       // SHA-256 of last response body
    val etag: String?,              // HTTP ETag header
    val lastModified: String?,      // HTTP Last-Modified header
    val gtfsRtTimestamp: Long?,     // header.timestamp from protobuf
    val lastFetchedAt: Instant,
    val lastProcessedAt: Instant?
)
```

### 5. Per-Feed Circuit Breakers

Each feed gets an independent circuit breaker keyed by `FeedId` to isolate failures:

```kotlin
CircuitBreakerConfig.custom()
    .failureRateThreshold(50f)
    .waitDurationInOpenState(Duration.ofSeconds(30))
    .slidingWindowSize(10)
    .build()
```

Combined with retry (3 attempts, exponential backoff with jitter) and per-request timeouts.

### 6. Parallel Fetch, Sequential Process

Fetching is parallelized across all feeds. Processing (protobuf decode → persist) is sequential within the `.collect {}` block:

```
Fetch:   [A][B][C][D][E]  ← 50 concurrent coroutines
              │
Process: [A]→[B]→[C]→[D]→[E]  ← sequential in collect
```

The bottleneck is network I/O (100–500ms per fetch), not processing (single-digit milliseconds for decode, batched writes). Sequential processing avoids DB write contention and keeps the implementation simple.

### 7. New `gtfsrt` Module

```
backend/src/main/kotlin/com/mobilispect/backend/
├── feed/                          # Existing — no changes
│   ├── domain/model/Feed.kt      # Already has realtimeFeedUrl
│   ├── batch/discovery/           # Already persists realtimeFeedUrl
│   └── ...
│
└── gtfsrt/                        # New module — ingestion only
    ├── application/
    │   └── GtfsRtIngestionService.kt
    ├── domain/
    │   ├── VehiclePosition.kt
    │   ├── TripUpdate.kt
    │   ├── ServiceAlert.kt
    │   ├── GtfsRtFetchResult.kt
    │   └── GtfsRtFeedState.kt
    └── infrastructure/
        ├── ParallelGtfsRtFetcher.kt
        ├── FeedCircuitBreakerRegistry.kt
        ├── GtfsRtProtobufDecoder.kt
        └── RedisGtfsRtFeedStateRepository.kt
```

The `gtfsrt` module depends on the `feed` module only through:

- `FeedQueryApi` (ADR 0010) for querying active feeds with realtime URLs
- `FeedId` value class for keying circuit breakers, metrics, and cache state
- `FeedAuthentication` for applying auth headers to HTTP requests

No cross-module database access.

## Rationale

### Why On-Demand Discovery?

1. **Self-Bootstrapping**: The service starts ingesting immediately after deployment without manual intervention or waiting for scheduled discovery.
2. **Single Source of Truth**: Feed metadata (URLs, auth, status) is already managed by the feed discovery pipeline. Invoking it on-demand avoids duplication.
3. **Transit.land Already Provides RT URLs**: The `realtimeFeedUrl` field is populated during metadata fetch in Phase 2 of `FeedDiscoveryReader`. No additional API calls needed.
4. **Operational Simplicity**: One discovery pipeline to monitor, debug, and maintain. The ingestion service simply triggers it when needed.

### Why Kotlin Coroutines Over Other Concurrency Models?

1. **Lightweight**: Coroutines are cheap to create (thousands concurrent without thread exhaustion), unlike thread-per-feed models.
2. **Structured Concurrency**: `coroutineScope` and `async` ensure proper cancellation and error propagation. No orphaned background work.
3. **Backpressure via Semaphore**: Simple, explicit concurrency control without reactive stream complexity.
4. **Native Kotlin**: The backend is Kotlin-first. Coroutines integrate naturally with the language and Spring's coroutine support.

### Why Three-Layer Deduplication?

1. **HTTP 304 is Free**: Avoids transferring the response body entirely. Many GTFS-RT servers support conditional requests.
2. **Content Hash Catches the Rest**: For servers that don't support ETags/Last-Modified, SHA-256 comparison is fast and definitive.
3. **Timestamp as Final Guard**: Protects against edge cases where the HTTP response differs (e.g., different compression) but the feed data is unchanged.
4. **Cost-Ordered**: Each layer is cheaper to check than the next. Most feeds are eliminated by Layer 1 or 2, avoiding protobuf decode entirely.

### Why Sequential Processing?

1. **Network I/O is the Bottleneck**: Fetching takes 100–500ms; decoding + writing takes <10ms per feed. Parallelizing processing yields minimal throughput gain.
2. **No DB Contention**: Sequential writes avoid lock contention on vehicle position and trip update tables.
3. **Simpler Error Handling**: A failed write doesn't interfere with other feeds' writes.
4. **Measurable Upgrade Path**: If profiling later shows processing as a bottleneck, fan-out to a bounded processing dispatcher is a localized change.

## Consequences

### Positive

1. **Self-Bootstrapping**: Service discovers feeds on-demand if none exist, enabling zero-configuration deployment.
2. **High Throughput**: 500 feeds polled in ~5–10 seconds per cycle with 50 parallel workers.
3. **Low Waste**: ~60–80% of polling cycles skip processing due to deduplication, reducing CPU and database I/O.
4. **Fault Isolation**: Per-feed circuit breakers prevent cascading failures. One failing feed doesn't affect others.
5. **No New Discovery Infrastructure**: Reuses existing `FeedDiscoveryBatchService`. Zero additional operational overhead.
6. **Modulith Compliant**: Clean module boundary via `FeedQueryApi`. No cross-module DB access.
7. **Observable**: Metrics per feed (fetch duration, cache hits, circuit breaker state, skip reasons) enable targeted debugging.

### Negative

1. **Redis Dependency**: Deduplication state requires Redis. If Redis is unavailable, all feeds are processed every cycle (safe but wasteful).
   - Mitigation: Graceful fallback to process-all mode. Redis is already required for caching.
2. **First-Run Discovery Delay**: On first startup with no feeds, the initial discovery takes 1–5 minutes before ingestion can begin.
   - Mitigation: One-time cost; subsequent cycles use cached feeds. Discovery runs in background if already triggered.
3. **Coroutine Complexity**: Developers must understand structured concurrency, `Flow`, and `channelFlow`.
   - Mitigation: Concurrency logic is isolated in `ParallelGtfsRtFetcher`. Application-layer code is straightforward.
4. **Sequential Processing Ceiling**: If individual feed processing becomes slow (very large feeds), sequential processing could become a bottleneck.
   - Mitigation: Defer until measured. Fan-out is a localized refactor if needed.

## Alternatives Considered

### 1. Separate GTFS-RT Discovery Pipeline (Rejected)

**Approach**: A dedicated scheduler querying Transit.land specifically for GTFS-RT feeds.

**Rejected Because**:

- Duplicates operator/metadata fetching already done by the GTFS discovery pipeline
- Creates two sources of truth for feed URLs and authentication
- Additional Transit.land API quota consumption
- More infrastructure to monitor and maintain
- `realtimeFeedUrl` is already persisted by the existing pipeline

### 2. Spring Batch for GTFS-RT Ingestion (Rejected)

**Approach**: Use Spring Batch (like the existing discovery pipeline) for GTFS-RT polling.

**Rejected Because**:

- Spring Batch is designed for finite, large-volume ETL jobs — not continuous 30-second polling cycles
- Job startup/teardown overhead is significant relative to a 30-second cycle
- Chunk-oriented processing doesn't map well to independent feed fetches
- Coroutines provide lighter-weight concurrency for IO-bound polling

### 3. Reactive Streams (Project Reactor) for Fetching (Rejected)

**Approach**: Use `Flux` and `Mono` with `WebClient` for reactive feed fetching.

**Rejected Because**:

- Steeper learning curve for the team compared to coroutines
- Debugging reactive chains is harder (stack traces, breakpoints)
- Coroutines achieve equivalent non-blocking IO with imperative-style code
- Spring's coroutine support bridges to reactive internals when needed

### 4. Virtual Threads (Project Loom) (Deferred)

**Approach**: Use JVM virtual threads with blocking HTTP clients.

**Deferred Because**:

- Viable alternative with similar performance characteristics
- Less ecosystem maturity than coroutines in Kotlin/Spring
- Coroutines provide structured concurrency (cancellation, scoping) that virtual threads lack
- Can be revisited if Kotlin coroutine-virtual thread integration matures

### 5. Parallel Processing Pipeline (Deferred)

**Approach**: Fan out processing to a bounded dispatcher alongside parallel fetching.

**Deferred Because**:

- Processing is not currently a bottleneck (network IO dominates)
- Adds DB write contention complexity
- Can be introduced later as a localized refactor in `GtfsRtIngestionService.collect {}` if profiling warrants it

## Related Decisions

- **ADR 0003**: Spring Batch Feed Discovery — establishes the discovery pipeline that populates `realtimeFeedUrl`
- **ADR 0009**: Spring Modulith Module Boundaries — defines the `gtfsrt` module boundary
- **ADR 0010**: API-Driven Module Communication — `gtfsrt` module uses `FeedQueryApi` to access feed data
- Constitutional Principle I: Modular Monolith Ownership
- Constitutional Principle IV: Performance & Reliability Targets

## Implementation Checklist

### Phase 1: Core Fetcher

- [ ] Add `findByStatusAndRealtimeFeedUrlNotNull` to `FeedRepository`
- [ ] Implement `ParallelGtfsRtFetcher` with coroutines dispatcher and semaphore
- [ ] Implement `GtfsRtProtobufDecoder` for GTFS-RT protobuf parsing
- [ ] Add HTTP conditional request support (`If-None-Match`, `If-Modified-Since`)
- [ ] Integrate `FeedAuthentication` for authenticated feeds
- [ ] Write unit tests for fetcher, decoder, and deduplication logic

### Phase 2: Deduplication and Resilience

- [ ] Implement `GtfsRtFeedState` model and `RedisGtfsRtFeedStateRepository`
- [ ] Add SHA-256 content hashing with cache comparison
- [ ] Add GTFS-RT header timestamp comparison
- [ ] Implement `FeedCircuitBreakerRegistry` with per-feed circuit breakers
- [ ] Add retry with exponential backoff and jitter
- [ ] Write unit tests for deduplication layers and circuit breaker behavior

### Phase 3: Ingestion Service

- [ ] Implement `GtfsRtIngestionService` with `@Scheduled` polling
- [ ] Add on-demand discovery via `FeedDiscoveryBatchService.discoverAll()` when no feeds found
- [ ] Add `AtomicBoolean` guard to prevent concurrent discovery triggers
- [ ] Implement `GtfsRtProcessingService` (decode → validate → persist)
- [ ] Define `VehiclePosition`, `TripUpdate`, `ServiceAlert` domain models
- [ ] Add batch persistence for RT entities
- [ ] Wire feed status updates (`lastCheckedAt`, `lastUpdatedAt`)
- [ ] Write integration tests with Testcontainers (including empty-DB bootstrap scenario)

### Phase 4: Observability

- [ ] Add per-feed metrics (fetch duration, cache hit/miss, skip reasons)
- [ ] Add circuit breaker state metrics
- [ ] Add structured logging for ingestion cycle statistics
- [ ] Add ingestion health endpoint
- [ ] Verify ≥80% test coverage via `scripts/validate-coverage.sh`

## Notes for Implementation Team

- **On-Demand Discovery**: The ingestion service triggers `FeedDiscoveryBatchService.discoverAll()` when no feeds with `realtimeFeedUrl` are found. Use an `AtomicBoolean` to prevent concurrent discovery runs.
- **Feed URLs**: Use `Feed.realtimeFeedUrl` for GTFS-RT endpoints. For feeds with `specType == GTFS_RT`, `Feed.downloadUrl` may also be the RT endpoint.
- **Authentication**: Always check `FeedAuthentication` before fetching. Some GTFS-RT feeds require API keys.
- **Redis Keys**: Namespace deduplication state under `gtfsrt:state:{feedId}` with 24-hour TTL.
- **Metrics Tags**: Always tag with `feed_id` for per-feed drill-down. Include `skip_reason` on skip counters. Add `gtfsrt.discovery.triggered` counter.
- **Graceful Degradation**: If Redis is unavailable, process all feeds (skip deduplication). Log a warning, don't fail the cycle.
- **Dispatcher Tuning**: Start with parallelism=50, semaphore=100. Adjust based on observed connection pool usage and memory.
- **Testing**: Mock HTTP responses with `MockWebServer`. Use `TestCoroutineDispatcher` for deterministic coroutine testing. Include test for empty-DB bootstrap scenario.

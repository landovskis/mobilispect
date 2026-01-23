# Architecture Exploration: Module-Based Feed Registration

## Executive Summary

This document explores an architecture that allows each module in the Mobilispect Spring Modulith application to dynamically register for the GTFS feed data it is interested in, rather than being hardcoded into the feed import pipeline.

**Current State:** Feed import uses a rigid Spring Batch pipeline with hardcoded steps for each module (agency, route, stop, etc.).

**Proposed State:** Modules dynamically register their data interests, and the feed import system distributes data via events, maintaining Spring Modulith boundaries.

---

## 1. Current Architecture Analysis

### 1.1 Current Feed Import Flow

```
FeedImportController (POST /api/feeds/{feedId}/import)
    ↓
FeedImportService (creates FeedImport record, launches batch job)
    ↓
FeedImportJobConfig (static pipeline)
    ├── feedImportStep (GTFSFeedReader parses entire GTFS feed)
    ├── agencyProcessingStep (AgencyReader → AgencyProcessor → AgencyWriter)
    ├── routeProcessingStep (RouteReader → RouteProcessor → RouteWriter)
    ├── routeVariantProcessingStep
    ├── stopSpacingProcessingStep
    └── frequencyProcessingStep
```

**Current Location:** `/backend/src/main/kotlin/com/mobilispect/backend/feed/batch/import/FeedImportJobConfig.kt`

### 1.2 Problems with Current Architecture

1. **Tight Coupling:** Adding a new module requires modifying `FeedImportJobConfig`
2. **Violation of Open/Closed Principle:** Not open for extension without modification
3. **Module Boundary Leakage:** Feed module must know about all consumer modules
4. **Scalability Issues:** Linear pipeline doesn't allow parallel processing by module
5. **No Module Autonomy:** Modules can't control their own data ingestion lifecycle

### 1.3 Current Event Model

**Events Published:**
- `FeedImportStartedEvent` - Job begins
- `FeedImportStepStartedEvent` - Each step begins
- `FeedImportStepCompleted` - Each step completes (published by Writers)
- `FeedImportCompletedEvent` - Entire job completes
- `FeedImportFailedEvent` - Job fails

**Current Event Usage:**
- WebSocket progress tracking (`FeedImportProgressEventListener`)
- Region discovery (`RegionEventListener`)
- **NOT used for data distribution**

---

## 2. Proposed Architecture: Event-Driven Module Registration

### 2.1 Core Principles

1. **Module Autonomy:** Each module declares what GTFS data it needs
2. **Loose Coupling:** Feed module publishes data events; modules subscribe independently
3. **Spring Modulith Compliance:** Communication via events only, no cross-module DB access
4. **Incremental Adoption:** Can coexist with current batch pipeline during migration
5. **Observability:** Maintain detailed progress tracking and error handling

### 2.2 Registration Mechanism Options

#### **Option A: Annotation-Based Registration (Recommended)**

Modules use custom annotations to declare their data interests:

```kotlin
// In Feed module API
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class FeedDataConsumer(
    val dataTypes: Array<GTFSDataType>,
    val priority: Int = 0
)

enum class GTFSDataType {
    AGENCY, ROUTE, TRIP, STOP, SHAPE, STOP_TIME, FREQUENCY, CALENDAR
}

// In Agency module
@Component
@FeedDataConsumer(dataTypes = [GTFSDataType.AGENCY])
class AgencyFeedDataHandler(
    private val agencyService: AgencyCommandService
) : FeedDataHandler<GTFSAgency> {

    override fun handle(
        feedId: FeedId,
        data: List<GTFSAgency>,
        context: ImportContext
    ): ImportResult {
        // Process agencies
        val agencies = data.map { it.toDomain(feedId) }
        agencyService.importAgencies(agencies)
        return ImportResult.success(agencies.size)
    }
}
```

**Pros:**
- Explicit, type-safe declarations
- Discoverable via Spring component scanning
- Self-documenting code
- IDE support for finding handlers

**Cons:**
- Requires reflection for handler discovery
- Slightly more complex initial setup

#### **Option B: Interface-Based Registration**

Modules implement a standard interface that the feed module queries:

```kotlin
// In Feed module API
interface FeedDataHandler<T> {
    fun dataType(): GTFSDataType
    fun priority(): Int = 0
    fun handle(feedId: FeedId, data: List<T>, context: ImportContext): ImportResult
}

// In Route module
@Component
class RouteFeedDataHandler(
    private val routeService: RouteCommandService
) : FeedDataHandler<GTFSRoute> {

    override fun dataType() = GTFSDataType.ROUTE

    override fun handle(
        feedId: FeedId,
        data: List<GTFSRoute>,
        context: ImportContext
    ): ImportResult {
        val routes = data.map { it.toDomain(feedId) }
        routeService.importRoutes(routes)
        return ImportResult.success(routes.size)
    }
}
```

**Pros:**
- Simple, standard Spring pattern
- Easy handler discovery via `List<FeedDataHandler<*>>`
- No reflection needed
- Type-safe at compile time

**Cons:**
- Single data type per handler (can't easily register for multiple types)
- Less declarative than annotations

#### **Option C: Event Listener Registration (Simplest)**

Use Spring's existing event mechanism with specialized event types:

```kotlin
// In Feed module API
sealed class GTFSDataPublishedEvent(
    open val feedId: FeedId,
    open val importId: ImportId,
)

data class AgencyDataPublishedEvent(
    override val feedId: FeedId,
    override val importId: ImportId,
    val agencies: List<GTFSAgency>
) : GTFSDataPublishedEvent(feedId, importId)

data class RouteDataPublishedEvent(
    override val feedId: FeedId,
    override val importId: ImportId,
    val routes: List<GTFSRoute>
) : GTFSDataPublishedEvent(feedId, importId)

// In Agency module
@Component
class AgencyDataEventListener(
    private val agencyService: AgencyCommandService
) {
    @EventListener
    @Async
    fun handleAgencyData(event: AgencyDataPublishedEvent) {
        val agencies = event.agencies.map { it.toDomain(event.feedId) }
        agencyService.importAgencies(agencies)
    }
}
```

**Pros:**
- Uses existing Spring infrastructure
- Zero custom framework code
- Most familiar to Spring developers
- Async processing built-in with `@Async`

**Cons:**
- Harder to discover all handlers (IDE search for `@EventListener`)
- No explicit handler priority control
- Event payload size concerns (large datasets)

### 2.3 Recommended Approach: Hybrid (Interface + Events)

Combine the best of both worlds:

1. **Handlers implement `FeedDataHandler` interface** for discovery and type safety
2. **Feed module publishes events** to trigger handlers asynchronously
3. **Handlers registered via Spring component scanning**
4. **Batch job replaced with event-driven orchestration**

```kotlin
// Feed module publishes events after GTFS parsing
@Component
class FeedDataOrchestrator(
    private val handlers: List<FeedDataHandler<*>>,
    private val eventPublisher: ApplicationEventPublisher
) {
    fun distributeGTFSData(feedId: FeedId, importId: ImportId, gtfsData: GTFSData) {
        val context = ImportContext(importId, Instant.now())

        // Publish events for each data type
        eventPublisher.publishEvent(
            AgencyDataAvailableEvent(feedId, importId, gtfsData.agencies)
        )
        eventPublisher.publishEvent(
            RouteDataAvailableEvent(feedId, importId, gtfsData.routes)
        )
        eventPublisher.publishEvent(
            StopDataAvailableEvent(feedId, importId, gtfsData.stops)
        )
        // ... other data types
    }
}

// Modules listen to events and delegate to handlers
@Component
class AgencyDataEventHandler(
    private val handler: AgencyFeedDataHandler
) {
    @EventListener
    @Async
    @Transactional
    fun onAgencyDataAvailable(event: AgencyDataAvailableEvent) {
        try {
            val result = handler.handle(
                event.feedId,
                event.agencies,
                event.context
            )
            // Publish completion event
            eventPublisher.publishEvent(
                FeedImportStepCompleted(event.feedId, "agency", result)
            )
        } catch (e: Exception) {
            eventPublisher.publishEvent(
                FeedImportStepFailed(event.feedId, "agency", e.message)
            )
        }
    }
}
```

---

## 3. Detailed Design

### 3.1 API Contracts (Feed Module)

```kotlin
package com.mobilispect.backend.feed.api

import java.time.Instant

// Core handler interface
interface FeedDataHandler<T> {
    fun dataType(): GTFSDataType
    fun priority(): Int = 0
    fun handle(feedId: FeedId, data: List<T>, context: ImportContext): ImportResult
}

// Data type enumeration
enum class GTFSDataType {
    AGENCY, ROUTE, TRIP, STOP, SHAPE, STOP_TIME, FREQUENCY, CALENDAR, FARE
}

// Import context
data class ImportContext(
    val importId: ImportId,
    val timestamp: Instant,
    val metadata: Map<String, Any> = emptyMap()
)

// Import result
sealed class ImportResult {
    data class Success(val recordsProcessed: Int) : ImportResult()
    data class PartialSuccess(
        val recordsProcessed: Int,
        val errors: List<ImportError>
    ) : ImportResult()
    data class Failure(val error: ImportError) : ImportResult()
}

data class ImportError(
    val recordId: String?,
    val message: String,
    val exception: Throwable?
)

// Events published by Feed module
sealed class FeedDataEvent(
    open val feedId: FeedId,
    open val importId: ImportId
)

data class AgencyDataAvailableEvent(
    override val feedId: FeedId,
    override val importId: ImportId,
    val agencies: List<GTFSAgency>,
    val context: ImportContext
) : FeedDataEvent(feedId, importId)

data class RouteDataAvailableEvent(
    override val feedId: FeedId,
    override val importId: ImportId,
    val routes: List<GTFSRoute>,
    val context: ImportContext
) : FeedDataEvent(feedId, importId)

// ... similar events for other data types

// Step completion events
data class FeedDataHandlerCompleted(
    val feedId: FeedId,
    val importId: ImportId,
    val dataType: GTFSDataType,
    val result: ImportResult
)

data class FeedDataHandlerFailed(
    val feedId: FeedId,
    val importId: ImportId,
    val dataType: GTFSDataType,
    val error: ImportError
)
```

### 3.2 Feed Import Orchestration

```kotlin
package com.mobilispect.backend.feed.service

@Service
class FeedImportOrchestrator(
    private val feedService: FeedQueryService,
    private val gtfsReader: GTFSFeedReader,
    private val eventPublisher: ApplicationEventPublisher,
    private val importRepository: FeedImportRepository,
    private val handlerRegistry: FeedDataHandlerRegistry
) {

    @Async
    fun importFeed(feedId: FeedId): ImportId {
        val importId = ImportId.generate()
        val import = createImport(feedId, importId)

        try {
            // Update status
            import.markAsRunning()
            importRepository.save(import)

            // Publish started event
            eventPublisher.publishEvent(FeedImportStartedEvent(feedId, importId))

            // Parse GTFS feed
            val feed = feedService.findById(feedId)
            val gtfsData = gtfsReader.readFeed(feed.url)

            // Distribute data to registered handlers
            distributeData(feedId, importId, gtfsData)

            // Wait for all handlers to complete (with timeout)
            awaitCompletion(importId)

            // Mark as completed
            import.markAsCompleted()
            importRepository.save(import)

            eventPublisher.publishEvent(
                FeedImportCompletedEvent(feedId, importId, import.stats)
            )

        } catch (e: Exception) {
            import.markAsFailed(e.message)
            importRepository.save(import)

            eventPublisher.publishEvent(
                FeedImportFailedEvent(feedId, importId, e.message)
            )
        }

        return importId
    }

    private fun distributeData(
        feedId: FeedId,
        importId: ImportId,
        gtfsData: GTFSData
    ) {
        val context = ImportContext(importId, Instant.now())

        // Publish events for each registered data type
        if (handlerRegistry.hasHandlersFor(GTFSDataType.AGENCY)) {
            eventPublisher.publishEvent(
                AgencyDataAvailableEvent(feedId, importId, gtfsData.agencies, context)
            )
        }

        if (handlerRegistry.hasHandlersFor(GTFSDataType.ROUTE)) {
            eventPublisher.publishEvent(
                RouteDataAvailableEvent(feedId, importId, gtfsData.routes, context)
            )
        }

        if (handlerRegistry.hasHandlersFor(GTFSDataType.STOP)) {
            eventPublisher.publishEvent(
                StopDataAvailableEvent(feedId, importId, gtfsData.stops, context)
            )
        }

        // ... distribute other data types based on registered handlers
    }

    private fun awaitCompletion(importId: ImportId) {
        // Implementation using CountDownLatch or reactive streams
        // Track completion events from all handlers
    }
}
```

### 3.3 Handler Registry

```kotlin
package com.mobilispect.backend.feed.internal

@Component
class FeedDataHandlerRegistry(
    handlers: List<FeedDataHandler<*>>
) {
    private val handlersByType: Map<GTFSDataType, List<FeedDataHandler<*>>>

    init {
        // Group handlers by data type and sort by priority
        handlersByType = handlers
            .groupBy { it.dataType() }
            .mapValues { (_, handlers) ->
                handlers.sortedByDescending { it.priority() }
            }
    }

    fun hasHandlersFor(dataType: GTFSDataType): Boolean {
        return handlersByType.containsKey(dataType)
    }

    fun getHandlersFor(dataType: GTFSDataType): List<FeedDataHandler<*>> {
        return handlersByType[dataType] ?: emptyList()
    }

    fun getAllRegisteredTypes(): Set<GTFSDataType> {
        return handlersByType.keys
    }
}
```

### 3.4 Example Module Implementation (Agency Module)

```kotlin
package com.mobilispect.backend.agency.internal

// Handler implementation
@Component
class AgencyFeedDataHandler(
    private val agencyRepository: AgencyRepository,
    private val eventPublisher: ApplicationEventPublisher
) : FeedDataHandler<GTFSAgency> {

    override fun dataType() = GTFSDataType.AGENCY

    override fun priority() = 0

    override fun handle(
        feedId: FeedId,
        data: List<GTFSAgency>,
        context: ImportContext
    ): ImportResult {
        val errors = mutableListOf<ImportError>()
        var processed = 0

        data.forEach { gtfsAgency ->
            try {
                val agency = gtfsAgency.toDomainModel(feedId)
                agencyRepository.save(agency)
                processed++
            } catch (e: Exception) {
                errors.add(
                    ImportError(
                        recordId = gtfsAgency.agencyId,
                        message = "Failed to import agency: ${e.message}",
                        exception = e
                    )
                )
            }
        }

        return when {
            errors.isEmpty() -> ImportResult.Success(processed)
            processed == 0 -> ImportResult.Failure(errors.first())
            else -> ImportResult.PartialSuccess(processed, errors)
        }
    }
}

// Event listener that delegates to handler
@Component
class AgencyDataEventListener(
    private val handler: AgencyFeedDataHandler,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Async
    @Transactional
    fun onAgencyDataAvailable(event: AgencyDataAvailableEvent) {
        logger.info("Processing {} agencies for feed {}",
            event.agencies.size, event.feedId)

        try {
            val result = handler.handle(
                event.feedId,
                event.agencies,
                event.context
            )

            eventPublisher.publishEvent(
                FeedDataHandlerCompleted(
                    event.feedId,
                    event.importId,
                    GTFSDataType.AGENCY,
                    result
                )
            )

            logger.info("Completed processing agencies for feed {}: {}",
                event.feedId, result)

        } catch (e: Exception) {
            logger.error("Failed to process agencies for feed {}", event.feedId, e)

            eventPublisher.publishEvent(
                FeedDataHandlerFailed(
                    event.feedId,
                    event.importId,
                    GTFSDataType.AGENCY,
                    ImportError(null, e.message ?: "Unknown error", e)
                )
            )
        }
    }
}
```

---

## 4. Data Flow Architecture

### 4.1 Sequence Diagram

```
Client              FeedImportService    GTFSFeedReader    FeedDataOrchestrator    AgencyModule    RouteModule
  |                        |                   |                    |                    |              |
  |--POST /import--------->|                   |                    |                    |              |
  |                        |                   |                    |                    |              |
  |                        |--readFeed()------>|                    |                    |              |
  |                        |                   |--parse GTFS------->|                    |              |
  |                        |                   |                    |                    |              |
  |                        |                   |<--GTFSData---------|                    |              |
  |                        |                   |                    |                    |              |
  |                        |<--GTFSData--------|                    |                    |              |
  |                        |                   |                    |                    |              |
  |                        |--distributeData()-|------------------>|                    |              |
  |                        |                   |                    |                    |              |
  |                        |                   |                    |--AgencyDataEvent-->|              |
  |                        |                   |                    |                    |              |
  |                        |                   |                    |--RouteDataEvent--------------->|  |
  |                        |                   |                    |                    |              |
  |                        |                   |                    |                    |--process---->|
  |                        |                   |                    |                    |              |
  |                        |                   |                    |                    |              |--process
  |                        |                   |                    |                    |              |
  |                        |                   |                    |<--Completed--------|              |
  |                        |                   |                    |                    |              |
  |                        |                   |                    |<--Completed----------------------|
  |                        |                   |                    |                    |              |
  |<--202 Accepted---------|                   |                    |                    |              |
```

### 4.2 Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Feed Module                             │
│                                                                 │
│  ┌──────────────────┐      ┌─────────────────────────────┐   │
│  │ FeedImport       │      │ FeedDataOrchestrator        │   │
│  │ Service          │─────>│  - distributeData()         │   │
│  └──────────────────┘      │  - awaitCompletion()        │   │
│                            └─────────────────────────────┘   │
│                                       │                       │
│                                       │ publishes             │
│                                       ▼                       │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │            ApplicationEventPublisher                     │ │
│  │  - AgencyDataAvailableEvent                             │ │
│  │  - RouteDataAvailableEvent                              │ │
│  │  - StopDataAvailableEvent                               │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                   │
                                   │ @EventListener
                                   ▼
    ┌──────────────────────────────────────────────────────────┐
    │                   Module Boundary                        │
    └──────────────────────────────────────────────────────────┘
                                   │
           ┌───────────────────────┼─────────────────────┐
           │                       │                     │
           ▼                       ▼                     ▼
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│   Agency Module     │  │   Route Module      │  │   Stop Module       │
│                     │  │                     │  │                     │
│ ┌─────────────────┐ │  │ ┌─────────────────┐ │  │ ┌─────────────────┐ │
│ │ AgencyData      │ │  │ │ RouteData       │ │  │ │ StopData        │ │
│ │ EventListener   │ │  │ │ EventListener   │ │  │ │ EventListener   │ │
│ └─────────────────┘ │  │ └─────────────────┘ │  │ └─────────────────┘ │
│         │           │  │         │           │  │         │           │
│         ▼           │  │         ▼           │  │         ▼           │
│ ┌─────────────────┐ │  │ ┌─────────────────┐ │  │ ┌─────────────────┐ │
│ │ AgencyFeedData  │ │  │ │ RouteFeedData   │ │  │ │ StopFeedData    │ │
│ │ Handler         │ │  │ │ Handler         │ │  │ │ Handler         │ │
│ └─────────────────┘ │  │ └─────────────────┘ │  │ └─────────────────┘ │
│         │           │  │         │           │  │         │           │
│         ▼           │  │         ▼           │  │         ▼           │
│ ┌─────────────────┐ │  │ ┌─────────────────┐ │  │ ┌─────────────────┐ │
│ │ Agency          │ │  │ │ Route           │ │  │ │ Stop            │ │
│ │ Repository      │ │  │ │ Repository      │ │  │ │ Repository      │ │
│ └─────────────────┘ │  │ └─────────────────┘ │  │ └─────────────────┘ │
└─────────────────────┘  └─────────────────────┘  └─────────────────────┘
```

---

## 5. Migration Strategy

### 5.1 Phase 1: Parallel Implementation (Weeks 1-2)

**Goal:** Introduce registration API without breaking existing batch pipeline

1. **Add new API contracts** to Feed module:
   - `FeedDataHandler` interface
   - `GTFSDataType` enum
   - Event classes (`AgencyDataAvailableEvent`, etc.)
   - `FeedDataHandlerRegistry`

2. **Implement handler registry** with component scanning

3. **Create wrapper handlers** for existing batch processors:
   ```kotlin
   @Component
   class AgencyBatchHandlerAdapter(
       private val agencyProcessor: AgencyProcessor,
       private val agencyRepository: AgencyRepository
   ) : FeedDataHandler<GTFSAgency> {
       override fun dataType() = GTFSDataType.AGENCY

       override fun handle(
           feedId: FeedId,
           data: List<GTFSAgency>,
           context: ImportContext
       ): ImportResult {
           // Delegate to existing batch processor
           val agencies = data.map { agencyProcessor.process(it) }
           agencyRepository.saveAll(agencies)
           return ImportResult.Success(agencies.size)
       }
   }
   ```

4. **Add feature flag** to toggle between batch and event-driven modes:
   ```yaml
   mobilispect:
     feed:
       import:
         mode: BATCH  # or EVENT_DRIVEN
   ```

### 5.2 Phase 2: Event-Driven Implementation (Weeks 3-4)

**Goal:** Implement full event-driven orchestration alongside batch

1. **Implement `FeedDataOrchestrator`** service
2. **Add event listeners** in each module
3. **Implement completion tracking** mechanism
4. **Add observability** (metrics, traces, logs)
5. **Run both pipelines in parallel** for validation

### 5.3 Phase 3: Cutover and Cleanup (Week 5)

**Goal:** Switch to event-driven mode and remove batch code

1. **Enable event-driven mode** in production
2. **Monitor performance and errors**
3. **Deprecate batch pipeline** after validation period
4. **Remove batch-specific code**:
   - `FeedImportJobConfig`
   - Batch readers/processors/writers
   - Batch step listeners

### 5.4 Rollback Plan

- Feature flag allows instant rollback to batch mode
- Keep batch code for 2 release cycles before deletion
- Database schema unchanged (no migration needed)

---

## 6. Benefits and Trade-offs

### 6.1 Benefits

1. **Extensibility:** New modules can register without modifying Feed module
2. **Loose Coupling:** Modules only depend on Feed API, not each other
3. **Parallel Processing:** Handlers execute asynchronously
4. **Module Autonomy:** Each module controls its own import logic
5. **Testability:** Handlers are simple, testable components
6. **Observability:** Event-driven model provides better tracking
7. **Constitutional Compliance:** Strict module boundaries maintained

### 6.2 Trade-offs

1. **Complexity:** Event-driven architecture is more complex than linear batch
2. **Debugging:** Asynchronous flow harder to trace than sequential steps
3. **Error Handling:** Must handle partial failures across modules
4. **Eventual Consistency:** Data may not be immediately consistent across modules
5. **Memory Usage:** Large GTFS datasets published as events require memory
6. **Testing:** More integration tests needed for event flows

### 6.3 Mitigations

- **Complexity:** Provide clear documentation and examples
- **Debugging:** Add distributed tracing (Spring Cloud Sleuth)
- **Error Handling:** Implement retry logic and dead letter queues
- **Consistency:** Use saga pattern for multi-module operations
- **Memory:** Chunk large datasets or use streaming events
- **Testing:** Create test harness for event-driven flows

---

## 7. Performance Considerations

### 7.1 Scalability

**Current Batch Pipeline:**
- Sequential processing: ~5-10 minutes for large feed
- Single-threaded per step
- Blocking I/O

**Event-Driven Pipeline:**
- Parallel processing: ~2-5 minutes for same feed
- Multi-threaded across modules
- Non-blocking with `@Async`

### 7.2 Resource Usage

**Memory:**
- Batch: Stores entire GTFS in job context (~50-100MB)
- Event: Publishes data in chunks, releases immediately (~20-50MB)

**Database:**
- Batch: Single transaction per step
- Event: Multiple transactions, one per handler (better isolation)

### 7.3 Backpressure

Implement backpressure for large feeds:

```kotlin
@Configuration
class AsyncConfig : AsyncConfigurer {
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 4
        executor.maxPoolSize = 8
        executor.queueCapacity = 25  // Limit queue to prevent OOM
        executor.setThreadNamePrefix("feed-handler-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.initialize()
        return executor
    }
}
```

---

## 8. Testing Strategy

### 8.1 Unit Tests

Test handlers in isolation:

```kotlin
@Test
fun `should import agencies successfully`() {
    val feedId = FeedId("test-feed")
    val gtfsAgencies = listOf(
        GTFSAgency(agencyId = "agency-1", name = "Test Agency")
    )
    val context = ImportContext(ImportId.generate(), Instant.now())

    val result = agencyHandler.handle(feedId, gtfsAgencies, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(1)
    verify(agencyRepository).save(any())
}
```

### 8.2 Integration Tests

Test event flow end-to-end:

```kotlin
@SpringBootTest
@Testcontainers
class FeedImportIntegrationTest {

    @Autowired
    lateinit var feedImportOrchestrator: FeedImportOrchestrator

    @Autowired
    lateinit var agencyRepository: AgencyRepository

    @Test
    fun `should distribute GTFS data to all handlers`() {
        val feedId = FeedId("test-feed")
        val importId = feedImportOrchestrator.importFeed(feedId)

        // Wait for async processing
        await().atMost(30, TimeUnit.SECONDS)
            .until { feedImportRepository.findById(importId).status == COMPLETED }

        // Verify agencies were imported
        val agencies = agencyRepository.findByFeedId(feedId)
        assertThat(agencies).isNotEmpty()
    }
}
```

### 8.3 Contract Tests

Verify module interfaces:

```kotlin
@Test
fun `AgencyFeedDataHandler should implement FeedDataHandler contract`() {
    val handler = AgencyFeedDataHandler(mockRepository, mockPublisher)

    assertThat(handler).isInstanceOf(FeedDataHandler::class.java)
    assertThat(handler.dataType()).isEqualTo(GTFSDataType.AGENCY)
    assertThat(handler.priority()).isGreaterThanOrEqualTo(0)
}
```

---

## 9. Observability

### 9.1 Metrics

```kotlin
@Component
class FeedImportMetrics(private val meterRegistry: MeterRegistry) {

    private val importDuration = Timer.builder("feed.import.duration")
        .tag("module", "feed")
        .register(meterRegistry)

    private val handlerDuration = Timer.builder("feed.handler.duration")
        .tag("module", "module")
        .register(meterRegistry)

    private val recordsProcessed = Counter.builder("feed.records.processed")
        .tag("data_type", "type")
        .register(meterRegistry)

    private val handlerErrors = Counter.builder("feed.handler.errors")
        .tag("module", "module")
        .register(meterRegistry)
}
```

### 9.2 Distributed Tracing

```kotlin
@EventListener
@Async
@NewSpan("agency-data-handler")
fun onAgencyDataAvailable(@SpanTag("feedId") event: AgencyDataAvailableEvent) {
    // Processing logic with automatic trace propagation
}
```

### 9.3 Structured Logging

```kotlin
logger.info(
    "Processing {} agencies for feed {} in import {}",
    kv("agencyCount", event.agencies.size),
    kv("feedId", event.feedId.value),
    kv("importId", event.importId.value)
)
```

---

## 10. Security Considerations

### 10.1 Module Isolation

- Handlers execute in module-owned transactions
- No cross-module database access
- Events are immutable value objects

### 10.2 Input Validation

```kotlin
override fun handle(
    feedId: FeedId,
    data: List<GTFSAgency>,
    context: ImportContext
): ImportResult {
    // Validate input
    require(data.all { it.agencyId.isNotBlank() }) {
        "Agency ID cannot be blank"
    }

    // Process...
}
```

### 10.3 Error Isolation

- Handler failures don't affect other modules
- Partial failures logged and tracked
- Retry logic per module

---

## 11. Future Enhancements

### 11.1 Dynamic Module Loading

Allow modules to register at runtime without restart:

```kotlin
interface DynamicFeedDataHandler : FeedDataHandler<*> {
    fun canHandle(dataType: GTFSDataType): Boolean
}
```

### 11.2 Streaming Events

For very large feeds, stream events in chunks:

```kotlin
@EventListener
fun onAgencyDataStream(event: AgencyDataStreamEvent) {
    event.agencyStream
        .buffer(100)
        .subscribe { chunk ->
            handler.handle(event.feedId, chunk, event.context)
        }
}
```

### 11.3 Event Sourcing

Persist all import events for audit and replay:

```kotlin
@Component
class FeedImportEventStore(
    private val eventRepository: FeedImportEventRepository
) {
    fun save(event: FeedDataEvent) {
        eventRepository.save(event.toEntity())
    }

    fun replay(importId: ImportId): Flow<FeedDataEvent> {
        return eventRepository.findByImportId(importId)
            .map { it.toEvent() }
    }
}
```

---

## 12. Recommended Next Steps

1. **Review this document** with the team and gather feedback
2. **Create ADR** documenting the architectural decision
3. **Implement Phase 1** (parallel implementation with feature flag)
4. **Add tests** for new handler interfaces
5. **Migrate one module** as proof of concept (Agency module recommended)
6. **Measure performance** comparing batch vs. event-driven
7. **Proceed with full migration** if POC succeeds

---

## 13. References

- **Spring Modulith Documentation:** https://docs.spring.io/spring-modulith/reference/
- **Spring Events:** https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html
- **Spring Batch:** https://docs.spring.io/spring-batch/reference/
- **Domain Events Pattern:** https://martinfowler.com/eaaDev/DomainEvent.html
- **Saga Pattern:** https://microservices.io/patterns/data/saga.html

---

## Appendix A: Full Code Example

See `/backend/src/main/kotlin/com/mobilispect/backend/feed/example/` for complete working example implementation (to be added).

## Appendix B: Performance Benchmarks

Performance comparison to be added after POC implementation.

## Appendix C: ADR Template

```markdown
# ADR-NNNN: Module-Based Feed Data Registration

## Status
Proposed

## Context
Currently, the feed import pipeline is hardcoded in FeedImportJobConfig...

## Decision
We will implement an event-driven architecture that allows modules to register...

## Consequences
### Positive
- Improved extensibility
- Better module autonomy
...

### Negative
- Increased complexity
- More testing required
...

## Alternatives Considered
1. Keep batch pipeline as-is
2. Use Spring Integration
...
```

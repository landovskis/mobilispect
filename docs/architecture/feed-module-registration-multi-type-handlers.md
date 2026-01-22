# Multi-Type Handler Support: Design Patterns and Examples

## Overview

This document extends the [Module-Based Feed Registration Architecture](./feed-module-registration.md) to explore how a single handler can subscribe to multiple GTFS data types.

**Use Case:** Some modules need to process multiple related data types together:
- Route module may want `ROUTE`, `TRIP`, and `SHAPE` data together to build route variants
- Stop module may want `STOP` and `STOP_TIME` data together for spacing analysis
- Frequency module may want `TRIP` and `FREQUENCY` data together

---

## Current Design Limitation

The originally proposed `FeedDataHandler` interface only supports a single data type:

```kotlin
interface FeedDataHandler<T> {
    fun dataType(): GTFSDataType  // ❌ Returns single type
    fun priority(): Int = 0
    fun handle(feedId: FeedId, data: List<T>, context: ImportContext): ImportResult
}
```

**Problem:** A module must create multiple handlers even when processing logic is tightly coupled.

---

## Design Options for Multi-Type Support

### Option 1: Multiple Handlers Per Module (Current Design)

**Pattern:** Create separate handlers for each data type, coordinate via shared state or sequential processing.

```kotlin
// Route module - Handler 1
@Component
class RouteDataHandler(
    private val routeDataCache: RouteDataCache
) : FeedDataHandler<GTFSRoute> {

    override fun dataType() = GTFSDataType.ROUTE
    override fun priority() = 10  // Process first

    override fun handle(
        feedId: FeedId,
        data: List<GTFSRoute>,
        context: ImportContext
    ): ImportResult {
        // Cache routes for later processing by variant handler
        routeDataCache.store(feedId, data)
        return ImportResult.Success(data.size)
    }
}

// Route module - Handler 2
@Component
class TripDataHandler(
    private val routeDataCache: RouteDataCache
) : FeedDataHandler<GTFSTrip> {

    override fun dataType() = GTFSDataType.TRIP
    override fun priority() = 9  // Process after routes

    override fun handle(
        feedId: FeedId,
        data: List<GTFSTrip>,
        context: ImportContext
    ): ImportResult {
        // Access cached routes
        val routes = routeDataCache.get(feedId)

        // Build route variants using both routes and trips
        val variants = buildRouteVariants(routes, data)

        return ImportResult.Success(variants.size)
    }
}
```

**Pros:**
- ✅ Works with existing interface design
- ✅ Clear single responsibility per handler
- ✅ Type-safe at compile time

**Cons:**
- ❌ Requires shared state/cache between handlers
- ❌ Complex coordination logic
- ❌ Priority-based ordering is brittle
- ❌ Not truly independent handlers

---

### Option 2: Handler Returns Collection of Types ⭐ **Recommended**

**Pattern:** Change interface to return multiple data types, receive all data in single invocation.

```kotlin
// Updated interface
interface FeedDataHandler {
    fun dataTypes(): Set<GTFSDataType>  // ✅ Returns multiple types
    fun priority(): Int = 0
    fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult
}

// Data bundle containing all requested types
data class GTFSDataBundle(
    val feedId: FeedId,
    val agencies: List<GTFSAgency> = emptyList(),
    val routes: List<GTFSRoute> = emptyList(),
    val trips: List<GTFSTrip> = emptyList(),
    val stops: List<GTFSStop> = emptyList(),
    val shapes: Map<String, List<GTFSShapePoint>> = emptyMap(),
    val stopTimes: List<GTFSStopTime> = emptyList(),
    val frequencies: List<GTFSFrequency> = emptyList(),
    val calendars: List<GTFSCalendar> = emptyList()
) {
    // Helper to check if data type is present
    fun has(type: GTFSDataType): Boolean {
        return when (type) {
            GTFSDataType.AGENCY -> agencies.isNotEmpty()
            GTFSDataType.ROUTE -> routes.isNotEmpty()
            GTFSDataType.TRIP -> trips.isNotEmpty()
            GTFSDataType.STOP -> stops.isNotEmpty()
            GTFSDataType.SHAPE -> shapes.isNotEmpty()
            GTFSDataType.STOP_TIME -> stopTimes.isNotEmpty()
            GTFSDataType.FREQUENCY -> frequencies.isNotEmpty()
            GTFSDataType.CALENDAR -> calendars.isNotEmpty()
        }
    }
}

// Example: Single-type handler (Agency)
@Component
class AgencyDataHandler(
    private val agencyService: AgencyCommandService
) : FeedDataHandler {

    override fun dataTypes() = setOf(GTFSDataType.AGENCY)

    override fun handle(
        feedId: FeedId,
        data: GTFSDataBundle,
        context: ImportContext
    ): ImportResult {
        val agencies = data.agencies.map { it.toDomainModel(feedId) }
        agencyService.importAgencies(agencies)
        return ImportResult.Success(agencies.size)
    }
}

// Example: Multi-type handler (Route Variants)
@Component
class RouteVariantDataHandler(
    private val routeService: RouteCommandService
) : FeedDataHandler {

    // ✅ Register for multiple types
    override fun dataTypes() = setOf(
        GTFSDataType.ROUTE,
        GTFSDataType.TRIP,
        GTFSDataType.SHAPE
    )

    override fun priority() = 5  // After agency processing

    override fun handle(
        feedId: FeedId,
        data: GTFSDataBundle,
        context: ImportContext
    ): ImportResult {
        // All required data available in single call
        val routeVariants = data.routes.map { route ->
            val routeTrips = data.trips.filter { it.routeId == route.routeId }
            val shapes = routeTrips.mapNotNull { trip ->
                trip.shapeId?.let { shapeId -> data.shapes[shapeId] }
            }

            RouteVariant.fromGTFS(route, routeTrips, shapes)
        }

        routeService.importRouteVariants(routeVariants)
        return ImportResult.Success(routeVariants.size)
    }
}
```

**Feed Data Orchestrator Implementation:**

```kotlin
@Component
class FeedDataOrchestrator(
    private val handlers: List<FeedDataHandler>,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun distributeGTFSData(feedId: FeedId, importId: ImportId, gtfsData: GTFSData) {
        val context = ImportContext(importId, Instant.now())

        // Create full data bundle
        val bundle = GTFSDataBundle(
            feedId = feedId,
            agencies = gtfsData.agencies,
            routes = gtfsData.routes,
            trips = gtfsData.trips,
            stops = gtfsData.stops,
            shapes = gtfsData.shapes,
            stopTimes = gtfsData.stopTimes,
            frequencies = gtfsData.frequencies,
            calendars = gtfsData.calendars
        )

        // Sort handlers by priority (highest first)
        val sortedHandlers = handlers.sortedByDescending { it.priority() }

        // Execute handlers in parallel (async)
        sortedHandlers.forEach { handler ->
            CompletableFuture.runAsync {
                try {
                    val result = handler.handle(feedId, bundle, context)

                    eventPublisher.publishEvent(
                        FeedDataHandlerCompleted(
                            feedId, importId, handler.dataTypes(), result
                        )
                    )
                } catch (e: Exception) {
                    eventPublisher.publishEvent(
                        FeedDataHandlerFailed(
                            feedId, importId, handler.dataTypes(),
                            ImportError(null, e.message ?: "Unknown error", e)
                        )
                    )
                }
            }
        }
    }
}
```

**Updated Event Model:**

```kotlin
// Single event with full data bundle
data class GTFSDataAvailableEvent(
    val feedId: FeedId,
    val importId: ImportId,
    val dataBundle: GTFSDataBundle,
    val context: ImportContext
)

// Completion event now includes multiple data types
data class FeedDataHandlerCompleted(
    val feedId: FeedId,
    val importId: ImportId,
    val dataTypes: Set<GTFSDataType>,  // ✅ Multiple types
    val result: ImportResult
)
```

**Pros:**
- ✅ Natural support for multi-type handlers
- ✅ No coordination needed between handlers
- ✅ All data available simultaneously
- ✅ Type-safe with data class
- ✅ Easy to test (provide complete bundle)

**Cons:**
- ❌ Memory usage: entire GTFS dataset in single bundle
- ❌ Less granular - can't partially process data types
- ❌ Handler must filter data it doesn't need

**Mitigation for Memory Concerns:**

```kotlin
// Lazy-loading bundle for large datasets
data class LazyGTFSDataBundle(
    val feedId: FeedId,
    private val gtfsData: GTFSData
) {
    val agencies: List<GTFSAgency> by lazy { gtfsData.agencies }
    val routes: List<GTFSRoute> by lazy { gtfsData.routes }
    val trips: List<GTFSTrip> by lazy { gtfsData.trips }
    // ... other lazy properties
}
```

---

### Option 3: Multiple Event Listeners Per Handler

**Pattern:** Use standard Spring event listeners with multiple `@EventListener` methods.

```kotlin
@Component
class RouteVariantEventHandler(
    private val routeService: RouteCommandService
) {
    private val routeCache = ConcurrentHashMap<FeedId, List<GTFSRoute>>()
    private val tripCache = ConcurrentHashMap<FeedId, List<GTFSTrip>>()
    private val shapeCache = ConcurrentHashMap<FeedId, Map<String, List<GTFSShapePoint>>>()

    @EventListener
    @Async
    fun onRouteData(event: RouteDataAvailableEvent) {
        routeCache[event.feedId] = event.routes
        tryProcess(event.feedId, event.importId)
    }

    @EventListener
    @Async
    fun onTripData(event: TripDataAvailableEvent) {
        tripCache[event.feedId] = event.trips
        tryProcess(event.feedId, event.importId)
    }

    @EventListener
    @Async
    fun onShapeData(event: ShapeDataAvailableEvent) {
        shapeCache[event.feedId] = event.shapes
        tryProcess(event.feedId, event.importId)
    }

    @Synchronized
    private fun tryProcess(feedId: FeedId, importId: ImportId) {
        // Only process when all required data is available
        val routes = routeCache[feedId] ?: return
        val trips = tripCache[feedId] ?: return
        val shapes = shapeCache[feedId] ?: return

        // Build route variants
        val variants = routes.map { route ->
            val routeTrips = trips.filter { it.routeId == route.routeId }
            val routeShapes = routeTrips.mapNotNull { trip ->
                trip.shapeId?.let { shapeId -> shapes[shapeId] }
            }
            RouteVariant.fromGTFS(route, routeTrips, routeShapes)
        }

        routeService.importRouteVariants(variants)

        // Cleanup
        routeCache.remove(feedId)
        tripCache.remove(feedId)
        shapeCache.remove(feedId)

        // Publish completion
        eventPublisher.publishEvent(
            FeedImportStepCompleted(feedId, "route-variants")
        )
    }
}
```

**Pros:**
- ✅ Uses standard Spring patterns
- ✅ Granular event handling
- ✅ Can process data types as they arrive

**Cons:**
- ❌ Complex synchronization logic required
- ❌ Cache management complexity
- ❌ Race conditions possible
- ❌ Harder to test (async coordination)
- ❌ Memory leaks if cleanup fails

---

### Option 4: Composite Event Pattern

**Pattern:** Create specialized composite events for common multi-type scenarios.

```kotlin
// Composite event for related data
data class RouteCompleteDataAvailableEvent(
    val feedId: FeedId,
    val importId: ImportId,
    val routes: List<GTFSRoute>,
    val trips: List<GTFSTrip>,
    val shapes: Map<String, List<GTFSShapePoint>>,
    val context: ImportContext
)

data class StopCompleteDataAvailableEvent(
    val feedId: FeedId,
    val importId: ImportId,
    val stops: List<GTFSStop>,
    val stopTimes: List<GTFSStopTime>,
    val context: ImportContext
)

// Handler listens to composite event
@Component
class RouteVariantEventHandler(
    private val routeService: RouteCommandService
) {
    @EventListener
    @Async
    @Transactional
    fun onRouteCompleteData(event: RouteCompleteDataAvailableEvent) {
        val variants = event.routes.map { route ->
            val routeTrips = event.trips.filter { it.routeId == route.routeId }
            val shapes = routeTrips.mapNotNull { trip ->
                trip.shapeId?.let { shapeId -> event.shapes[shapeId] }
            }
            RouteVariant.fromGTFS(route, routeTrips, shapes)
        }

        routeService.importRouteVariants(variants)
    }
}

// Feed orchestrator publishes composite events
@Component
class FeedDataOrchestrator(
    private val eventPublisher: ApplicationEventPublisher
) {
    fun distributeGTFSData(feedId: FeedId, importId: ImportId, gtfsData: GTFSData) {
        val context = ImportContext(importId, Instant.now())

        // Publish composite events
        eventPublisher.publishEvent(
            RouteCompleteDataAvailableEvent(
                feedId, importId,
                gtfsData.routes,
                gtfsData.trips,
                gtfsData.shapes,
                context
            )
        )

        eventPublisher.publishEvent(
            StopCompleteDataAvailableEvent(
                feedId, importId,
                gtfsData.stops,
                gtfsData.stopTimes,
                context
            )
        )

        // Also publish individual events for single-type handlers
        eventPublisher.publishEvent(
            AgencyDataAvailableEvent(feedId, importId, gtfsData.agencies, context)
        )
    }
}
```

**Pros:**
- ✅ Simple event listening (no coordination needed)
- ✅ Type-safe composite data
- ✅ Explicit relationships in event names
- ✅ Can coexist with single-type events

**Cons:**
- ❌ Must create event class for each combination
- ❌ Not flexible (handlers can't choose arbitrary combinations)
- ❌ Feed module must know which combinations to publish
- ❌ Violates extensibility goal

---

## Recommended Approach: Option 2 with Enhancements

### Enhanced Design: Selective Data Bundle

Improve Option 2 by only including data types requested by handlers:

```kotlin
interface FeedDataHandler {
    fun dataTypes(): Set<GTFSDataType>
    fun priority(): Int = 0
    fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult
}

@Component
class FeedDataOrchestrator(
    private val handlers: List<FeedDataHandler>,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun distributeGTFSData(feedId: FeedId, importId: ImportId, gtfsData: GTFSData) {
        val context = ImportContext(importId, Instant.now())

        // Group handlers by required data types
        val handlerGroups = groupHandlersByDataRequirements(handlers)

        handlerGroups.forEach { (requiredTypes, handlersForGroup) ->
            // Create bundle with only required data
            val bundle = createSelectiveBundle(feedId, gtfsData, requiredTypes)

            // Execute handlers for this group in parallel
            handlersForGroup.forEach { handler ->
                executeHandlerAsync(handler, feedId, importId, bundle, context)
            }
        }
    }

    private fun groupHandlersByDataRequirements(
        handlers: List<FeedDataHandler>
    ): Map<Set<GTFSDataType>, List<FeedDataHandler>> {
        // Optimization: handlers with same data requirements share bundle
        return handlers.groupBy { it.dataTypes() }
    }

    private fun createSelectiveBundle(
        feedId: FeedId,
        gtfsData: GTFSData,
        requiredTypes: Set<GTFSDataType>
    ): GTFSDataBundle {
        return GTFSDataBundle(
            feedId = feedId,
            agencies = if (GTFSDataType.AGENCY in requiredTypes) gtfsData.agencies else emptyList(),
            routes = if (GTFSDataType.ROUTE in requiredTypes) gtfsData.routes else emptyList(),
            trips = if (GTFSDataType.TRIP in requiredTypes) gtfsData.trips else emptyList(),
            stops = if (GTFSDataType.STOP in requiredTypes) gtfsData.stops else emptyList(),
            shapes = if (GTFSDataType.SHAPE in requiredTypes) gtfsData.shapes else emptyMap(),
            stopTimes = if (GTFSDataType.STOP_TIME in requiredTypes) gtfsData.stopTimes else emptyList(),
            frequencies = if (GTFSDataType.FREQUENCY in requiredTypes) gtfsData.frequencies else emptyList(),
            calendars = if (GTFSDataType.CALENDAR in requiredTypes) gtfsData.calendars else emptyMap()
        )
    }

    @Async
    private fun executeHandlerAsync(
        handler: FeedDataHandler,
        feedId: FeedId,
        importId: ImportId,
        bundle: GTFSDataBundle,
        context: ImportContext
    ) {
        try {
            val result = handler.handle(feedId, bundle, context)

            eventPublisher.publishEvent(
                FeedDataHandlerCompleted(
                    feedId, importId, handler.dataTypes(), result
                )
            )
        } catch (e: Exception) {
            eventPublisher.publishEvent(
                FeedDataHandlerFailed(
                    feedId, importId, handler.dataTypes(),
                    ImportError(null, e.message ?: "Unknown error", e)
                )
            )
        }
    }
}
```

### Benefits of Enhanced Design

1. **Memory Efficient:** Only loads data types that handlers need
2. **Shared Bundles:** Handlers with same requirements share bundle instances
3. **Simple Handler API:** Handlers just declare types and receive data
4. **No Coordination Needed:** All required data available in single call
5. **Type-Safe:** Compile-time checking of data bundle access

---

## Complete Example: Route Module with Multi-Type Handler

### Handler Implementation

```kotlin
package com.mobilispect.backend.route.internal

import com.mobilispect.backend.feed.api.*
import com.mobilispect.backend.route.domain.RouteVariant
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

@Component
class RouteVariantDataHandler(
    private val routeRepository: RouteRepository,
    private val routeVariantRepository: RouteVariantRepository
) : FeedDataHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    // ✅ Declare multiple data types
    override fun dataTypes() = setOf(
        GTFSDataType.ROUTE,
        GTFSDataType.TRIP,
        GTFSDataType.SHAPE
    )

    override fun priority() = 5  // After agency processing

    override fun handle(
        feedId: FeedId,
        data: GTFSDataBundle,
        context: ImportContext
    ): ImportResult {
        logger.info(
            "Processing route variants for feed {}: {} routes, {} trips, {} shapes",
            feedId.value, data.routes.size, data.trips.size, data.shapes.size
        )

        val errors = mutableListOf<ImportError>()
        var processed = 0

        // Process each route
        data.routes.forEach { gtfsRoute ->
            try {
                // Find all trips for this route
                val routeTrips = data.trips.filter { it.routeId == gtfsRoute.routeId }

                if (routeTrips.isEmpty()) {
                    logger.warn("No trips found for route {}", gtfsRoute.routeId)
                    return@forEach
                }

                // Group trips by shape to identify variants
                val tripsByShape = routeTrips.groupBy { it.shapeId }

                tripsByShape.forEach { (shapeId, trips) ->
                    val shapePoints = shapeId?.let { data.shapes[it] } ?: emptyList()

                    val variant = RouteVariant(
                        routeId = RouteId(gtfsRoute.routeId),
                        feedId = feedId,
                        shapeId = shapeId,
                        shapePoints = shapePoints.map { it.toDomainModel() },
                        tripIds = trips.map { it.tripId },
                        directionId = trips.first().directionId
                    )

                    routeVariantRepository.save(variant)
                    processed++
                }

            } catch (e: Exception) {
                logger.error("Failed to process route {}", gtfsRoute.routeId, e)
                errors.add(
                    ImportError(
                        recordId = gtfsRoute.routeId,
                        message = "Failed to create route variants: ${e.message}",
                        exception = e
                    )
                )
            }
        }

        return when {
            errors.isEmpty() -> {
                logger.info("Successfully processed {} route variants", processed)
                ImportResult.Success(processed)
            }
            processed == 0 -> {
                logger.error("Failed to process any route variants")
                ImportResult.Failure(errors.first())
            }
            else -> {
                logger.warn("Partially processed {} variants with {} errors", processed, errors.size)
                ImportResult.PartialSuccess(processed, errors)
            }
        }
    }
}
```

### Handler Registry Detection

```kotlin
package com.mobilispect.backend.feed.internal

@Component
class FeedDataHandlerRegistry(
    handlers: List<FeedDataHandler>
) {

    private val handlersByType: Map<GTFSDataType, List<FeedDataHandler>>
    private val allHandlers: List<FeedDataHandler>

    init {
        allHandlers = handlers.sortedByDescending { it.priority() }

        // Create inverse index: each data type → handlers that need it
        handlersByType = GTFSDataType.entries
            .associateWith { dataType ->
                handlers.filter { dataType in it.dataTypes() }
                    .sortedByDescending { it.priority() }
            }
    }

    fun getAllHandlers(): List<FeedDataHandler> = allHandlers

    fun getHandlersRequiring(dataType: GTFSDataType): List<FeedDataHandler> {
        return handlersByType[dataType] ?: emptyList()
    }

    fun getRequiredDataTypes(): Set<GTFSDataType> {
        return allHandlers.flatMap { it.dataTypes() }.toSet()
    }

    fun logRegisteredHandlers() {
        logger.info("Registered {} feed data handlers:", allHandlers.size)
        allHandlers.forEach { handler ->
            logger.info(
                "  - {}: {} (priority: {})",
                handler::class.simpleName,
                handler.dataTypes().joinToString(),
                handler.priority()
            )
        }
    }
}
```

### Testing Multi-Type Handler

```kotlin
package com.mobilispect.backend.route.internal

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThat
import org.mockito.kotlin.*

class RouteVariantDataHandlerTest {

    private val routeRepository = mock<RouteRepository>()
    private val routeVariantRepository = mock<RouteVariantRepository>()
    private val handler = RouteVariantDataHandler(routeRepository, routeVariantRepository)

    @Test
    fun `should declare multiple data types`() {
        val types = handler.dataTypes()

        assertThat(types).containsExactlyInAnyOrder(
            GTFSDataType.ROUTE,
            GTFSDataType.TRIP,
            GTFSDataType.SHAPE
        )
    }

    @Test
    fun `should process route variants with all required data`() {
        val feedId = FeedId("test-feed")
        val context = ImportContext(ImportId.generate(), Instant.now())

        val routes = listOf(
            GTFSRoute(routeId = "R1", shortName = "Route 1", longName = "Test Route")
        )

        val trips = listOf(
            GTFSTrip(routeId = "R1", tripId = "T1", shapeId = "S1", directionId = 0),
            GTFSTrip(routeId = "R1", tripId = "T2", shapeId = "S1", directionId = 0),
            GTFSTrip(routeId = "R1", tripId = "T3", shapeId = "S2", directionId = 1)
        )

        val shapes = mapOf(
            "S1" to listOf(
                GTFSShapePoint(shapeId = "S1", lat = 37.0, lon = -122.0, sequence = 1),
                GTFSShapePoint(shapeId = "S1", lat = 37.1, lon = -122.1, sequence = 2)
            ),
            "S2" to listOf(
                GTFSShapePoint(shapeId = "S2", lat = 37.2, lon = -122.2, sequence = 1)
            )
        )

        val bundle = GTFSDataBundle(
            feedId = feedId,
            routes = routes,
            trips = trips,
            shapes = shapes
        )

        val result = handler.handle(feedId, bundle, context)

        // Should create 2 variants (one for each shape)
        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(2)

        verify(routeVariantRepository, times(2)).save(any())
    }

    @Test
    fun `should handle missing trips gracefully`() {
        val feedId = FeedId("test-feed")
        val context = ImportContext(ImportId.generate(), Instant.now())

        val bundle = GTFSDataBundle(
            feedId = feedId,
            routes = listOf(GTFSRoute(routeId = "R1", shortName = "Route 1")),
            trips = emptyList(),  // No trips
            shapes = emptyMap()
        )

        val result = handler.handle(feedId, bundle, context)

        // Should succeed with 0 processed (logged warning)
        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(0)

        verifyNoInteractions(routeVariantRepository)
    }
}
```

---

## Migration from Single-Type to Multi-Type

### Step 1: Update Interface

```kotlin
// Before
interface FeedDataHandler<T> {
    fun dataType(): GTFSDataType
    fun handle(feedId: FeedId, data: List<T>, context: ImportContext): ImportResult
}

// After
interface FeedDataHandler {
    fun dataTypes(): Set<GTFSDataType>  // Changed to collection
    fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult
}
```

### Step 2: Update Existing Single-Type Handlers

```kotlin
// Before
@Component
class AgencyDataHandler : FeedDataHandler<GTFSAgency> {
    override fun dataType() = GTFSDataType.AGENCY

    override fun handle(feedId: FeedId, data: List<GTFSAgency>, context: ImportContext) {
        // ...
    }
}

// After (minimal changes)
@Component
class AgencyDataHandler : FeedDataHandler {
    override fun dataTypes() = setOf(GTFSDataType.AGENCY)  // Return set

    override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext) {
        val agencies = data.agencies  // Extract from bundle
        // ... rest of logic unchanged
    }
}
```

### Step 3: Add Multi-Type Handlers

```kotlin
// New multi-type handler
@Component
class RouteVariantDataHandler : FeedDataHandler {
    override fun dataTypes() = setOf(
        GTFSDataType.ROUTE,
        GTFSDataType.TRIP,
        GTFSDataType.SHAPE
    )

    override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext) {
        // Access multiple data types
        val routes = data.routes
        val trips = data.trips
        val shapes = data.shapes

        // Process together
    }
}
```

---

## Performance Considerations

### Memory Usage Comparison

**Single-Type Events (Original):**
```
AgencyDataAvailableEvent: ~1MB (list of agencies)
RouteDataAvailableEvent: ~5MB (list of routes)
TripDataAvailableEvent: ~50MB (list of trips)
ShapeDataAvailableEvent: ~100MB (map of shapes)
---
Total in memory: ~156MB (if all events published simultaneously)
```

**Multi-Type Bundle:**
```
GTFSDataBundle: ~156MB (all data in single object)
Selective bundle (routes+trips+shapes): ~155MB
Selective bundle (agencies only): ~1MB
---
Total depends on handler requirements
```

**Optimization: Lazy Loading**
```kotlin
data class LazyGTFSDataBundle(
    private val gtfsData: GTFSData
) {
    val agencies: List<GTFSAgency> by lazy { gtfsData.agencies }
    val trips: List<GTFSTrip> by lazy { gtfsData.trips }  // Only loaded if accessed
    // ...
}
```

### Concurrency Model

**Single-Type (Sequential):**
```
Agency handler → Route handler → Trip handler → Stop handler
Total time: 4 × avg_time
```

**Multi-Type (Parallel):**
```
Agency handler ─┐
Route+Trip handler ─┤→ All complete
Stop+StopTime handler ─┘
Total time: max(handler_times)
```

---

## Recommendation Summary

**For most use cases:** Use **Option 2 (Handler Returns Collection)** with selective bundling.

**When to use each option:**

1. **Option 2 (Recommended):** Default choice for most modules
   - Simple to implement
   - No coordination needed
   - Type-safe

2. **Option 1 (Multiple Handlers):** When data types are truly independent
   - Agency processing (standalone)
   - Simple lookups with no relationships

3. **Option 3 (Event Listeners):** When you need streaming/progressive processing
   - Very large datasets
   - Need to process data as it arrives
   - Willing to manage synchronization complexity

4. **Option 4 (Composite Events):** When relationships are well-known and fixed
   - Predefined combinations
   - Not extensible - avoid for this use case

---

## Next Steps

1. **Update Feed Module API:**
   - Change `FeedDataHandler.dataType()` → `dataTypes()`
   - Replace typed `List<T>` → `GTFSDataBundle`
   - Add `GTFSDataBundle` data class

2. **Update Feed Orchestrator:**
   - Implement selective bundle creation
   - Group handlers by data requirements
   - Optimize memory usage

3. **Migrate Existing Handlers:**
   - Update single-type handlers to use bundle
   - Add multi-type handlers for route variants, frequencies, etc.

4. **Add Tests:**
   - Test single-type handlers
   - Test multi-type handlers
   - Test handler coordination

5. **Document Patterns:**
   - Add examples to developer guide
   - Document when to use multi-type vs. single-type

# ADR 0010: API-Driven Module Communication

**Date**: 2025-12-25
**Status**: Accepted
**Related**: ADR 0009 - Spring Modulith Module Boundaries
**Constitutional Requirement**: Principle I - Modular Monolith Ownership

## Context

With Spring Modulith module boundaries established (ADR 0009), we needed to define HOW modules communicate with each other. The constitutional requirement states:

> **No cross-module DB access; ports/events only**

Two primary patterns exist for inter-module communication:

### Pattern 1: Synchronous Query APIs

- Module A calls `ModuleBQueryApi.getSomething()` directly
- Immediate response
- Simple request-response semantics
- Works well for read operations

### Pattern 2: Asynchronous Domain Events

- Module A publishes `SomethingHappened` event
- Module B listens and reacts
- Eventual consistency
- Decouples modules (publisher doesn't know subscribers)
- Works well for notifications and side effects

Without a clear decision:

- Developers would use patterns inconsistently
- Some might use events for simple queries (over-engineering)
- Others might use APIs for notifications (tight coupling)
- Module interfaces would be unpredictable

## Decision

**Use synchronous Query APIs for read operations; reserve domain events for state change notifications only.**

### Communication Patterns

```
┌─────────────────────────────────────────────────────────┐
│ When to Use Query APIs (Synchronous)                   │
├─────────────────────────────────────────────────────────┤
│ ✅ Reading data from another module                     │
│ ✅ Validating references (e.g., "does this feed exist?") │
│ ✅ Aggregating data across modules for queries          │
│ ✅ User-facing API endpoints requiring immediate response│
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ When to Use Events (Asynchronous)                      │
├─────────────────────────────────────────────────────────┤
│ ✅ Notifying other modules of state changes             │
│ ✅ Triggering side effects (e.g., cache invalidation)   │
│ ✅ Cross-cutting concerns (e.g., audit logging)         │
│ ✅ Long-running operations (e.g., feed import)           │
│ ✅ Breaking circular dependencies (future: feed imports)│
└─────────────────────────────────────────────────────────┘
```

### Query API Pattern

**Module Exposes**:

```kotlin
// feed/api/FeedQueryApi.kt (public interface)
package com.mobilispect.backend.feed.api

interface FeedQueryApi {
    /**
     * Find a feed by its onestop ID.
     *
     * @param feedId The unique identifier for the feed
     * @return The feed DTO if found, null otherwise
     */
    fun findFeedById(feedId: FeedId): FeedDTO?

    /**
     * Find all feeds associated with a specific region.
     *
     * @param regionId The region identifier
     * @return List of feeds in the region
     */
    fun findFeedsByRegion(regionId: RegionId): List<FeedDTO>
}

// feed/api/FeedDTO.kt (public DTO)
data class FeedDTO(
    val feedId: FeedId,
    val name: String?,
    val specType: FeedSpecType,
    val downloadUrl: String,
    val currentVersionSha1: String?,
    val status: FeedStatus,
    val regionIds: Set<RegionId>,  // ✅ DTOs, not entity references
    val lastCheckedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

// feed/internal/FeedQueryApiImpl.kt (implementation)
@Component
internal class FeedQueryApiImpl(
    private val feedRepository: FeedRepository
) : FeedQueryApi {
    override fun findFeedById(feedId: FeedId): FeedDTO? {
        return feedRepository.findByFeedOnestopId(feedId.value)
            .map { FeedMapper.toDTO(it) }
            .orElse(null)
    }

    override fun findFeedsByRegion(regionId: RegionId): List<FeedDTO> {
        return feedRepository.findAllByRegionRegionOnestopId(regionId)
            .map { FeedMapper.toDTO(it) }
    }
}
```

**Module Consumes**:

```kotlin
// agency/application/AgencyQueryService.kt (consumer)
@Service
class AgencyQueryService(
    private val agencyRepository: AgencyRepository,
    private val feedQueryApi: FeedQueryApi  // ✅ Inject API, not repository
) {
    fun getAgenciesByRegion(regionId: RegionId): Page<AgencyDTO> {
        // Use API instead of direct repository access
        val feeds = feedQueryApi.findFeedsByRegion(regionId)

        val agencies = feeds.flatMap { feed ->
            agencyRepository.findByFeedId(feed.feedId, Pageable.unpaged()).content
        }.distinctBy { it.agencyOnestopId }

        return paginate(agencies)
    }

    private fun mapAgency(agency: Agency): AgencyDTO {
        val feed = feedQueryApi.findFeedById(agency.feedId)  // ✅ API call
        val regionIds = feed?.regionIds?.map { it.value }?.toSet() ?: emptySet()

        return AgencyDTO(
            id = agency.agencyOnestopId.value,
            name = agency.name,
            feedOnestopId = agency.feedId.value,
            regionIds = regionIds  // ✅ Data from DTO, not entity navigation
        )
    }
}
```

### Event Pattern

**Module Publishes**:

```kotlin
// feed/domain/events/FeedImportCompleted.kt
package com.mobilispect.backend.feed.domain.events

/**
 * Published when a feed import completes successfully.
 *
 * Listeners can react by:
 * - Invalidating caches
 * - Updating statistics
 * - Notifying users
 */
data class FeedImportCompleted(
    val feedId: FeedId,
    val importId: ImportId,
    val agenciesProcessed: Int,
    val routesProcessed: Int,
    val stopsProcessed: Int,
    val variantsIdentified: Int,
    val completedAt: Instant
) {
    companion object {
        fun from(feedId: FeedId, result: ImportResult): FeedImportCompleted {
            return FeedImportCompleted(
                feedId = feedId,
                importId = result.importId,
                agenciesProcessed = result.agenciesProcessed,
                routesProcessed = result.routesProcessed,
                stopsProcessed = result.stopsProcessed,
                variantsIdentified = result.variantsIdentified,
                completedAt = Instant.now()
            )
        }
    }
}

// feed/service/FeedImportService.kt
@Service
class FeedImportService(
    private val eventPublisher: ApplicationEventPublisher
) {
    suspend fun importFeed(path: Path, feedId: FeedId): ImportResult {
        val result = performImport(path, feedId)

        // Publish event for other modules to react
        eventPublisher.publishEvent(
            FeedImportCompleted.from(feedId, result)
        )

        return result
    }
}
```

**Module Listens**:

```kotlin
// agency/internal/AgencyCacheInvalidationListener.kt
package com.mobilispect.backend.agency.internal

@Component
internal class AgencyCacheInvalidationListener(
    private val cacheManager: CacheManager
) {
    /**
     * Invalidate agency cache when feed import completes.
     *
     * Agency data may have changed (new agencies, updated routes),
     * so we clear the cache to ensure fresh data on next query.
     */
    @EventListener
    fun onFeedImportCompleted(event: FeedImportCompleted) {
        cacheManager.getCache(RedisConfiguration.AGENCY_CACHE)?.clear()
        logger.info("Invalidated agency cache after feed import: ${event.feedId}")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgencyCacheInvalidationListener::class.java)
    }
}
```

### Naming Conventions

**Query APIs**:

- Interface: `{Module}QueryApi` (e.g., `FeedQueryApi`, `AgencyQueryApi`)
- Implementation: `{Module}QueryApiImpl` (marked `internal`)
- Location: `{module}/api/` package
- DTOs: `{Entity}DTO` (e.g., `FeedDTO`, `AgencyDTO`)

**Domain Events**:

- Event class: `{Entity}{PastTenseVerb}` (e.g., `FeedImportCompleted`, `AgencyCreated`)
- Location: `{module}/domain/events/` or `{module}/events/` package
- Listener: `{Purpose}Listener` (e.g., `AgencyCacheInvalidationListener`)

## Rationale

### Why Query APIs for Reads?

1. **Immediate Response**: User-facing endpoints need instant data (can't wait for async events)
2. **Simple Semantics**: "Get feed by ID" is a simple request-response operation
3. **Transactional Consistency**: Query sees current state, not eventually-consistent stale data
4. **Error Handling**: Caller gets immediate feedback (null, exception) vs event delivery uncertainty
5. **Debugging**: Stack traces show direct call path, easier to debug than event chains

### Why Events for State Changes?

1. **Decoupling**: Publisher doesn't know who's listening (can add listeners without changing publisher)
2. **Multiple Reactions**: One event can trigger many side effects (cache invalidation, notifications, metrics)
3. **Async Processing**: Long-running reactions don't block the original operation
4. **Breaking Cycles**: Future use for breaking feed → transitanalysis dependency (feed publishes event, transitanalysis listens)
5. **Audit Trail**: Events can be logged/stored for compliance and debugging

### Hybrid Approach Benefits

Using BOTH patterns strategically:

- Query APIs keep read paths simple and fast
- Events keep write side decoupled and extensible
- Matches CQRS pattern (Command Query Responsibility Segregation)
- Each pattern used where it excels

## Consequences

### Positive

1. **Clear Guidelines**: Developers know when to use APIs vs events
2. **Consistent Interfaces**: All Query APIs follow same pattern
3. **Performance**: Synchronous queries avoid event overhead for reads
4. **Decoupled Reactions**: Events allow many listeners without changing publisher
5. **Testability**: Can mock Query APIs easily; can verify events published
6. **Future-Proof**: Event infrastructure ready for breaking cycles (future phase)

### Negative

1. **Dual Patterns**: Developers must learn both APIs and events
2. **Potential Misuse**: Developers might use wrong pattern if not educated
   - Mitigation: Code review, architectural guidelines
3. **No Eventual Consistency for Queries**: Queries always see current state (can't optimize with stale cache)
   - Mitigation: Use Redis caching for query results (already implemented)

## Examples

### Example 1: Get Agencies by Region (Query API)

**Why API**: User request needs immediate response with current data

```kotlin
@GetMapping("/api/regions/{regionId}/agencies")
fun getAgenciesByRegion(@PathVariable regionId: String): Page<AgencyDTO> {
    // Synchronous API call - immediate response
    return agencyQueryService.getAgenciesByRegion(RegionId(regionId), pageable)
}
```

### Example 2: Feed Import Completed (Event)

**Why Event**: Multiple modules need to react (cache invalidation, metrics, notifications)

```kotlin
// Publisher (feed module)
eventPublisher.publishEvent(FeedImportCompleted.from(feedId, result))

// Listener 1: Cache invalidation (agency module)
@EventListener
fun onFeedImportCompleted(event: FeedImportCompleted) {
    cacheManager.getCache("agencies")?.clear()
}

// Listener 2: Metrics (monitoring module)
@EventListener
fun onFeedImportCompleted(event: FeedImportCompleted) {
    metrics.recordImport(event.agenciesProcessed, event.routesProcessed)
}

// Listener 3: User notification (websocket module)
@EventListener
fun onFeedImportCompleted(event: FeedImportCompleted) {
    websocket.broadcast("Import completed: ${event.feedId}")
}
```

### Example 3: Validate Feed Exists (Query API)

**Why API**: Need immediate validation result (can't wait for async event)

```kotlin
// agency/data/repository/AgencyRepositoryImpl.kt
override fun save(agency: Agency): Agency {
    // Validate feed exists before saving agency
    val feed = feedQueryApi.findFeedById(agency.feedId)
        ?: throw IllegalArgumentException("Feed not found: ${agency.feedId}")

    val entity = mapper.toEntity(agency)
    val saved = jpaRepository.save(entity)
    return mapper.toDomain(saved)
}
```

## Alternatives Considered

### 1. Events Only (Rejected)

**Rationale**: Over-engineering for simple queries

- "Get feed by ID" doesn't need event bus overhead
- Eventual consistency inappropriate for user-facing queries
- Harder to debug (event delivery vs direct call)
- Performance overhead for simple reads

### 2. APIs Only (Rejected)

**Rationale**: Tight coupling for notifications

- Publisher must know all consumers (tight coupling)
- Can't add listeners without changing publisher
- Harder to break circular dependencies (future need)
- No natural way to handle multi-subscriber reactions

### 3. Shared Database Access (Rejected)

**Rationale**: Violates module boundaries

- Constitutional requirement: "No cross-module DB access"
- Prevents module extraction to microservices
- No clear ownership boundaries
- Tight coupling through schema

## Related Decisions

- **ADR 0009**: Spring Modulith Module Boundaries (establishes module structure)
- Constitutional Principle I: Modular Monolith Ownership

## Implementation Patterns

### Query API Checklist

When creating a new Query API:

- [ ] Create `{Module}QueryApi.kt` interface in `api/` package
- [ ] Define clear method signatures with value class parameters
- [ ] Create `{Entity}DTO.kt` DTOs (no entity exposure)
- [ ] Implement `{Module}QueryApiImpl.kt` in `internal/` package
- [ ] Mark implementation `@Component` or `@Service`
- [ ] Document each method with `@param` and `@return`
- [ ] Consider caching with `@Cacheable` if appropriate
- [ ] Write integration tests for API implementation

### Event Checklist

When creating a new domain event:

- [ ] Create `{Entity}{PastTense}.kt` event class in `events/` package
- [ ] Include all necessary context data (IDs, counts, timestamps)
- [ ] Make event immutable (`data class` with `val`)
- [ ] Publisher calls `eventPublisher.publishEvent(event)`
- [ ] Listeners use `@EventListener` annotation
- [ ] Listeners marked `internal` (not exposed outside module)
- [ ] Consider `@Async` for long-running listeners
- [ ] Log event publication and handling for observability
- [ ] Write tests verifying event is published and handled

## Future Enhancements

### Phase: Break Circular Dependencies

**Problem**: feed → transitanalysis → agency creates cycle

**Solution**: Replace `FeedManagementImportProcessor` direct call with event:

```kotlin
// Before (synchronous, creates cycle)
val result = feedImportService.importFeed(path, feedId)

// After (event-driven, breaks cycle)
eventPublisher.publishEvent(FeedImportRequested(feedId, path))
// transitanalysis listens and performs import asynchronously
```

This future enhancement will fully leverage the event infrastructure established by this ADR.

## Notes for Implementation Team

- **API Methods**: Use value classes (e.g., `FeedId`) not raw strings
- **DTOs**: Never expose JPA entities across module boundaries
- **Caching**: Query APIs are cached with Redis (24-hour TTL)
- **Events**: Use Spring's `ApplicationEventPublisher` (already available)
- **Testing**: Mock Query APIs with `@MockBean`, verify events with `@EventListener` test listener
- **Logging**: Log API calls and events for observability
- **Performance**: Query APIs should be fast (<50ms); use async events for slow operations
- **Consistency**: Follow naming conventions strictly for discoverability

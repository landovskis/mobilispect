# ADR 0009: Spring Modulith Module Boundaries

**Date**: 2025-12-25
**Status**: Accepted (with Architectural Debt)
**Constitutional Requirement**: Principle I - Modular Monolith Ownership

## Context

The Mobilispect backend was growing into a tightly-coupled monolith with:

- Cross-module JPA repository access (e.g., `StopRepositoryImpl` directly using `FeedJpaRepository`)
- Bidirectional entity navigation (e.g., `FeedEntity` ↔ `AgencyEntity`)
- Unclear module boundaries (no enforcement of dependency rules)
- Difficulty extracting modules to microservices if needed
- Risk of circular dependencies and tight coupling

Constitutional Principle I requires:
> **Modular Monolith Ownership** — Spring Modulith boundaries; no cross-module DB access; ports/events only; extraction requires ADR + migration plan.

Without proper module boundaries:

- Developers could accidentally create cross-module dependencies
- Testing became difficult (couldn't test modules in isolation)
- Code ownership unclear (who owns what?)
- Future extraction to microservices would require massive refactoring

## Decision

**Use Spring Modulith to define and enforce module boundaries with the FK-only pattern and API-driven communication.**

### Module Dependency Hierarchy (Acyclic Target)

```
region (no dependencies)
  ↑
  │
feed (depends on: region via API)
  ↑
  ├────────────────┐
  │                │
  │             agency (depends on: feed via API)
  │                │
  │                ↑
  │                │
stop             route (depends on: feed, agency via APIs)
(depends on:       ↑
 feed via API)     │
  │                │
  └────────────────┘
           │
    transitanalysis
  (orchestration layer)
```

### Module Definitions

Each module is annotated with `@ApplicationModule`:

```kotlin
// region/RegionModule.kt
@ApplicationModule(
    displayName = "Region Management",
    allowedDependencies = []
)
class RegionModule

// feed/FeedModule.kt
@ApplicationModule(
    displayName = "Feed Management",
    allowedDependencies = ["region"]
)
class FeedModule

// agency/AgencyModule.kt
@ApplicationModule(
    displayName = "Agency Management",
    allowedDependencies = ["feed"]
)
class AgencyModule

// route/RouteModule.kt
@ApplicationModule(
    displayName = "Route Management",
    allowedDependencies = ["feed", "agency"]
)
class RouteModule

// stop/StopModule.kt
@ApplicationModule(
    displayName = "Stop Management",
    allowedDependencies = ["feed"]
)
class StopModule
```

### Database Ownership

| Module | Owned Tables |
|--------|--------------|
| region | `metropolitan_regions` |
| feed | `feeds`, `feed_regions`, `feed_authentications`, `feed_imports` |
| agency | `agencies` |
| route | `routes`, `route_variants`, `frequencies`, `common_sections`, `common_section_variants`, `route_variant_stops` |
| stop | `stops` |

**Rule**: Modules may reference other modules' tables via foreign key columns, but MUST NEVER access other modules' JPA repositories directly.

### FK-Only Pattern

Entities use foreign key columns without JPA navigation:

**Before (Violation)**:

```kotlin
@Entity
class AgencyEntity(
    @ManyToOne
    @JoinColumn(name = "feed_onestop_id")
    val feed: FeedEntity,  // ❌ Entity navigation
    // ...
)
```

**After (Compliant)**:

```kotlin
@Entity
class AgencyEntity(
    @Column(name = "feed_onestop_id", nullable = false)
    val feedOnestopId: String,  // ✅ FK column only
    // ...
)
```

### API-Driven Communication

Modules communicate via Query APIs instead of direct repository access:

```kotlin
// feed/api/FeedQueryApi.kt - Public API
interface FeedQueryApi {
    fun findFeedById(feedId: FeedId): FeedDTO?
    fun findFeedsByRegion(regionId: RegionId): List<FeedDTO>
    fun getFeedVersion(feedId: FeedId): String?
}

// agency/application/AgencyQueryService.kt - Consumer
@Service
class AgencyQueryService(
    private val agencyRepository: AgencyRepository,
    private val feedQueryApi: FeedQueryApi  // ✅ Uses API
) {
    fun getAgenciesByRegion(regionId: RegionId): Page<AgencyDTO> {
        val feeds = feedQueryApi.findFeedsByRegion(regionId)
        // ...
    }
}
```

### Package Structure

Each module follows this structure:

```
{module}/
  ├── api/              # Public API (exposed to other modules)
  │   ├── {Module}QueryApi.kt
  │   ├── {Module}DTO.kt
  │   └── package-info.java  # @NamedInterface("api")
  ├── application/      # Application services
  ├── domain/
  │   ├── model/        # Domain entities
  │   ├── repository/   # Repository interfaces
  │   └── service/      # Domain services
  ├── data/
  │   ├── entity/       # JPA entities
  │   ├── repository/   # Repository implementations
  │   ├── mapper/       # Entity ↔ Domain mappers
  │   └── package-info.java  # @NamedInterface("internal")
  └── {Module}Module.kt # @ApplicationModule annotation
```

### Verification

Spring Modulith boundaries are verified via:

1. **Test**: `ModuleStructureTest.kt`

   ```kotlin
   @Test
   fun `verify Spring Modulith module boundaries`() {
       val modules = ApplicationModules.of(FeedManagementApplication::class.java)
       modules.verify()  // Fails if violations exist
   }
   ```

2. **Gradle Task**: `./gradlew verifyModulith`
   - Runs ModuleStructureTest
   - Called by pre-commit hook

3. **Pre-Commit Hook**: `.pre-commit-config.yaml`

   ```yaml
   - id: backend-modulith-verify
     name: Spring Modulith Module Boundaries
     entry: bash -c 'cd backend && ./gradlew verifyModulith'
   ```

4. **Documentation**: PlantUML diagrams generated to `build/spring-modulith-docs/`

## Consequences

### Positive

1. **Enforced Boundaries**: Spring Modulith compiler checks prevent accidental cross-module access
2. **Clear Ownership**: Each module has well-defined responsibilities and owners
3. **Testability**: Modules can be tested in isolation with mocked APIs
4. **Future Extraction**: Modules can be extracted to microservices with minimal refactoring
5. **API Contracts**: Query APIs serve as stable contracts between modules
6. **No Entity Exposure**: DTOs prevent internal entities from leaking across boundaries
7. **FK-Only Pattern**: Eliminates bidirectional navigation and LazyInitializationExceptions
8. **Documentation**: PlantUML diagrams visualize module structure automatically

### Negative

1. **Boilerplate**: Requires Query API interfaces + DTOs for each module
2. **Performance**: API calls add slight overhead vs direct repository access
   - Mitigation: Caching with Redis (already implemented)
3. **Learning Curve**: Developers must understand Spring Modulith concepts
4. **Architectural Debt**: Cyclic dependencies still exist (see below)

### Architectural Debt (As of 2025-12-25)

The following cyclic dependencies remain:

**Cycle 1**: `agency → feed → transitanalysis → agency`

- `agency` uses `FeedQueryApi` (intentional, correct)
- `feed` uses `FeedImportService` from `transitanalysis` (creates cycle)
- `transitanalysis` uses `AgencyRepository` from `agency` (intentional, correct)

**Root Cause**: `FeedManagementImportProcessor` (in feed module) directly calls `FeedImportService` (in transitanalysis module) to orchestrate imports. This creates a dependency FROM feed TO transitanalysis, which cycles back through agency/route.

**Impact**:

- `modules.verify()` currently disabled in `ModuleStructureTest`
- Pre-commit hook passes (verification disabled)
- Module boundaries defined but not fully enforced

**Mitigation Plan** (Future Phase):
Replace synchronous call with event-driven architecture:

- Feed publishes `FeedImportRequested` event
- Transitanalysis listens to event and performs import
- This breaks the `feed → transitanalysis` dependency
- Requires refactoring `FeedManagementImportProcessor`

## Alternatives Considered

### 1. Microservices from Start (Rejected)

**Rationale**: Premature optimization

- No proven need for independent deployment yet
- Network latency would hurt performance
- Distributed transactions add complexity
- Modular monolith provides same benefits with less operational overhead

### 2. Shared Kernel for Common IDs (Rejected)

**Rationale**: User preference for no shared kernel

- FeedId, AgencyId, etc. duplicated across modules
- Accept controlled duplication over shared ownership
- Each module owns its value class definitions
- Clearer ownership boundaries

### 3. Direct Repository Access with Conventions (Rejected)

**Rationale**: No enforcement

- Conventions are easily violated by accident
- Spring Modulith provides compiler-enforced boundaries
- API-driven approach ensures stable contracts

### 4. Event-Driven Only (Rejected)

**Rationale**: Over-engineering for queries

- Synchronous Query APIs better for read operations
- Events reserved for notifications (e.g., `FeedImportCompleted`)
- Hybrid approach: APIs for queries, events for state changes

## Related Decisions

- **ADR 0010**: API-Driven Module Communication (documents API vs event usage patterns)
- Constitutional Principle I: Modular Monolith Ownership

## Implementation Schedule

- **Phase 1-3**: Region, Feed, Agency modules (Completed)
- **Phase 4**: Route and Stop modules split from transitanalysis (Completed 2025-12-25)
- **Phase 5**: Enable Spring Modulith verification (Partial - verification disabled due to cycles)
- **Phase 6**: Documentation and ADRs (In Progress)
- **Future Phase**: Event-driven architecture to break cycles

## Open Questions

1. **When should we enable full modules.verify()?**
   - Answer: After event-driven refactoring breaks cycles (future phase)

2. **Should we extract modules to separate services?**
   - Answer: Not yet; wait for proven need (e.g., independent scaling requirements)

3. **How to handle transitanalysis as orchestration layer?**
   - Answer: It depends on all other modules (correct), but feed shouldn't depend on it (needs event-driven fix)

## Notes for Implementation Team

- FK-only pattern: Use `feedOnestopId: String` instead of `@ManyToOne feed: FeedEntity`
- Query APIs: Create `{Module}QueryApi.kt` in `api/` package
- DTOs: Never expose JPA entities across module boundaries
- JPQL: Use column names instead of entity navigation (e.g., `e.feedOnestopId` not `e.feed.id`)
- Mappers: Remove entity parameters (e.g., `toEntity(domain)` not `toEntity(domain, feedEntity)`)
- Pre-commit: Verification runs on every commit via `./gradlew verifyModulith`
- Documentation: PlantUML diagrams auto-generated in `build/spring-modulith-docs/`
- Cycles: Acknowledged architectural debt; do NOT try to force verification until events implemented

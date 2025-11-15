# ADR-0003: Spring Batch for Feed Discovery Processing

## Status
Accepted

## Context
The Feed Management System performs daily discovery of transit feeds from Transit.land, processing potentially
thousands of feeds. The original implementation used direct service-based processing, which had several limitations:

- Sequential processing of large feed datasets
- Limited fault tolerance and error recovery
- No built-in job monitoring or execution history
- Manual transaction management complexity
- Difficulty resuming failed discovery jobs

### Requirements
- Constitutional requirement for 200ms API response times (maintained)
- Constitutional requirement for test-driven development with 80%+ coverage
- Process thousands of feeds efficiently without memory issues
- Handle transient failures gracefully (network, API rate limits, database conflicts)
- Provide visibility into job execution status and history
- Support both global (all feeds) and regional (specific region) discovery
- Maintain observability with structured logging and metrics

### Challenges with Original Implementation
1. **Memory Constraints**: Loading all feeds into memory before processing
2. **Error Handling**: Single feed failure could impact entire discovery job
3. **Transaction Boundaries**: Manual management across large datasets
4. **Restart Capability**: No way to resume from failure point
5. **Monitoring**: Limited visibility into job progress and performance
6. **Scalability**: Difficult to add parallel processing or partitioning

## Decision
Migrate feed discovery processing to Spring Batch framework with chunk-oriented processing.

### Architecture Components

#### 1. Batch Jobs
**Global Feed Discovery Job**:
- Discovers all feeds from Transit.land
- Automatically assigns feeds to regions based on operator geography

**Regional Feed Discovery Job**:
- Discovers feeds for a specific metropolitan region
- Used by API endpoints for on-demand discovery

#### 2. Job Steps
Both jobs use a standard Reader-Processor-Writer pattern:

**ItemReader** (`FeedDiscoveryReader` / `RegionalFeedDiscoveryReader`):
- Fetches feeds from Transit.land API with pagination
- Uses `ConcurrentLinkedQueue` for thread-safe feed caching
- Lazy initialization on first read

**ItemProcessor** (`FeedDiscoveryProcessor`):
- Extracts regions from operator geographic data
- Creates or updates feed entities
- Handles region assignment (single or multiple regions per feed)
- Returns `ProcessedFeed` with upsert result and authentication info

**ItemWriter** (`FeedDiscoveryWriter`):
- Updates feed authentication credentials
- Records metrics for monitoring (created, updated, unchanged counts)
- Handles optimistic locking conflicts with retry logic

#### 3. Configuration
- **Chunk Size**: 10 feeds per chunk for balanced performance
- **Fault Tolerance**: Skip up to 100 failed feeds (global) or 50 (regional)
- **Transaction Scope**: Per chunk (automatic rollback on chunk failure)
- **Job Parameters**: Spec type, max feeds, region info (for regional jobs)

### Implementation Details

```kotlin
// Job configuration
@Bean
fun feedDiscoveryJob(
    jobRepository: JobRepository,
    feedDiscoveryStep: Step
): Job {
    return JobBuilder(FEED_DISCOVERY_JOB_NAME, jobRepository)
        .incrementer(RunIdIncrementer())
        .start(feedDiscoveryStep)
        .build()
}

// Step configuration with fault tolerance
@Bean
fun feedDiscoveryStep(...): Step {
    return StepBuilder("feedDiscoveryStep", jobRepository)
        .chunk<TransitLandFeedSummary, FeedEntity>(CHUNK_SIZE, transactionManager)
        .reader(feedDiscoveryReader)
        .processor(feedDiscoveryProcessor)
        .writer(feedDiscoveryWriter)
        .faultTolerant()
        .skip(Exception::class.java)
        .skipLimit(100)
        .build()
}
```

### Service API
`FeedDiscoveryBatchService` provides high-level API:
- `discoverAll(specType, maxFeeds)`: Launch global discovery
- `discover(regionId, regionName, specType)`: Launch regional discovery
- Returns `FeedDiscoveryResult` with counts and errors

### Integration Points
- **Scheduled Jobs**: `FeedDiscoveryScheduler` runs daily at 01:15 AM
- **REST API**: `RegionController` endpoint for on-demand regional discovery
- **Metrics**: Micrometer counters and timers for monitoring

## Consequences

### Positive
1. **Memory Efficiency**: Chunked processing prevents OutOfMemoryErrors
2. **Fault Tolerance**: Individual feed failures don't abort entire job
3. **Observability**: Built-in job execution history and metrics
4. **Restartability**: Can resume failed jobs from last successful chunk
5. **Testability**: Clear separation of concerns (reader/processor/writer)
6. **Scalability**: Foundation for future parallel processing/partitioning
7. **Transaction Management**: Automatic per-chunk transactions
8. **Standards Compliance**: Uses industry-standard batch processing framework

### Negative
1. **Complexity**: Additional layer of abstraction vs. simple service calls
2. **Learning Curve**: Team needs familiarity with Spring Batch concepts
3. **Testing Overhead**: Requires mocking batch infrastructure components
4. **Database Requirements**: Spring Batch stores job metadata in database tables

### Neutral
1. **Performance**: Similar to original for small datasets, better for large datasets
2. **Dependencies**: Spring Batch already in project (used for feed imports)
3. **API Compatibility**: Regional discovery still supported via batch service

## Alternatives Considered

### 1. Keep Original Service-Based Approach
**Pros**:
- Simpler code structure
- No additional framework overhead

**Cons**:
- Scalability limitations
- Manual error handling complexity
- No built-in monitoring or restart capability

**Decision**: Rejected due to scalability and fault tolerance concerns

### 2. Custom Chunking Implementation
**Pros**:
- Full control over processing logic
- No framework dependency

**Cons**:
- Reimplementing Spring Batch features
- Higher maintenance burden
- Missing standard monitoring integration

**Decision**: Rejected in favor of proven, well-supported framework

### 3. Message Queue (Kafka/RabbitMQ) Based Processing
**Pros**:
- Native async processing
- Event-driven architecture

**Cons**:
- Additional infrastructure complexity
- Overkill for scheduled batch jobs
- More moving parts to monitor

**Decision**: Rejected - Spring Batch sufficient for current needs

## Implementation

### Files Created/Modified
- `FeedDiscoveryBatchConfiguration.kt`: Job and step definitions
- `FeedDiscoveryBatchService.kt`: High-level service API
- `FeedDiscoveryReader.kt`: Global feed reader
- `RegionalFeedDiscoveryReader.kt`: Regional feed reader
- `FeedDiscoveryProcessor.kt`: Feed processing logic
- `FeedDiscoveryWriter.kt`: Authentication update and metrics
- `FeedDiscoveryScheduler.kt`: Updated to use batch service
- `RegionController.kt`: Updated to use batch service for regional discovery
- `FeedManagementApplication.kt`: Added `@EnableBatchProcessing`

### Test Coverage
- `FeedDiscoveryBatchServiceTest.kt`: Service layer tests (8 test cases)
- `FeedDiscoveryReaderTest.kt`: Reader tests (6 test cases)
- `RegionalFeedDiscoveryReaderTest.kt`: Regional reader tests (7 test cases)

**Total Test Coverage**: 21 test cases covering core batch functionality

### Original Service Retention
The original `FeedDiscoveryService` is retained for:
- Backward compatibility
- Direct programmatic access if needed
- Comparison reference during migration period

May be deprecated in future after batch implementation is proven stable.

## References
- [Spring Batch Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)
- ADR-0001: Transit.land API v2 Integration Strategy
- Constitutional Requirement: Test-Driven Development (80%+ coverage)
- Constitutional Requirement: Observability & Monitoring

## Decision Date
2025-01-15

## Decision Makers
- Development Team
- Technical Lead

## Review Date
2025-04-15 (3 months after implementation)

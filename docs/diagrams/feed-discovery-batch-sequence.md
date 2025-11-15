# Feed Discovery Batch Processing - Sequence Diagrams

This directory contains PlantUML sequence diagrams illustrating the Spring Batch architecture for feed discovery.

## Diagrams

### 1. Global Feed Discovery Flow
**File:** [`feed-discovery-global-flow.puml`](./feed-discovery-global-flow.puml)

**Description:** Complete end-to-end flow for scheduled global feed discovery showing:
- Scheduled job trigger at 01:15 AM
- Batch job launch with parameters
- Chunk-oriented processing (Read → Process → Write)
- Reader initialization and Transit.land API pagination
- Processor region extraction and feed upsert logic
- Writer authentication updates and metrics recording
- Transaction boundaries and commit points
- Result extraction and logging

**Key Participants:**
- `FeedDiscoveryScheduler`: Daily scheduled job
- `FeedDiscoveryBatchService`: Batch job launcher
- `FeedDiscoveryReader`: Global feed reader
- `FeedDiscoveryProcessor`: Feed processing and region assignment
- `FeedDiscoveryWriter`: Authentication and metrics
- Transit.land API, repositories, and metrics registry

**Processing:**
- Chunk size: 10 feeds
- Skip limit: 100 failures
- Transaction: Per chunk

---

### 2. Regional Feed Discovery Flow
**File:** [`feed-discovery-regional-flow.puml`](./feed-discovery-regional-flow.puml)

**Description:** REST API-triggered regional feed discovery showing:
- HTTP POST request to region-specific endpoint
- Region validation
- Regional batch job launch
- Regional reader using Transit.land regional API
- Same processor and writer logic as global discovery
- Response with discovery results

**Key Participants:**
- REST Client
- `RegionController`: API endpoint
- `FeedDiscoveryBatchService`: Batch job launcher
- `RegionalFeedDiscoveryReader`: Region-specific reader
- Shared processor and writer components

**Processing:**
- Chunk size: 10 feeds
- Skip limit: 50 failures
- Transaction: Per chunk
- API: `/api/feeds/regions/{regionId}/discover`

---

### 3. Error Handling and Fault Tolerance
**File:** [`feed-discovery-error-handling.puml`](./feed-discovery-error-handling.puml)

**Description:** Demonstrates Spring Batch fault tolerance mechanisms:
- **Item-level skip**: Individual feed failures are skipped (continue processing)
- **Chunk-level rollback**: Entire chunk rolls back on write failure
- **Job restartability**: Failed jobs can resume from last successful chunk
- Color-coded visualization:
  - 🟢 Green: Successful operations and commits
  - 🔴 Red: Errors and rollbacks
  - 🟡 Yellow: Warnings and skips

**Scenarios:**
1. **Item Skip**: Item 8 fails (API timeout), skip and continue with items 9-10
2. **Chunk Commit**: Chunk 1 commits with 9 items (1 skipped)
3. **Chunk Rollback**: Chunk 2 fails during processing, entire chunk rolled back
4. **Job Restart**: Job resumes from chunk 2, previous chunks remain committed

---

## Rendering the Diagrams

### IntelliJ IDEA
1. Install the PlantUML plugin
2. Open any `.puml` file
3. Right-click → "Show PlantUML Diagram"

### VS Code
1. Install the PlantUML extension
2. Open any `.puml` file
3. Press `Alt+D` or use command palette: "PlantUML: Preview Current Diagram"

### Command Line (requires PlantUML installed)
```bash
# Install PlantUML (macOS)
brew install plantuml

# Generate PNG diagrams
plantuml docs/diagrams/*.puml

# Generate SVG diagrams
plantuml -tsvg docs/diagrams/*.puml
```

### Online
1. Visit [PlantUML Online Editor](https://www.plantuml.com/plantuml/uml/)
2. Copy/paste the diagram content
3. View rendered diagram

---

## Architecture Components

### Job Configuration
```kotlin
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
```

### Step Configuration with Fault Tolerance
```kotlin
@Bean
fun feedDiscoveryStep(...): Step {
    return StepBuilder("feedDiscoveryStep", jobRepository)
        .chunk<TransitLandFeedSummary, FeedEntity>(CHUNK_SIZE, transactionManager)
        .reader(feedDiscoveryReader)
        .processor(feedDiscoveryProcessor)
        .writer(feedDiscoveryWriter)
        .faultTolerant()
        .skip(Exception::class.java)
        .skipLimit(100)  // Allow up to 100 feed failures
        .build()
}
```

### Transaction Boundaries
- **Chunk-Level Transactions**: Each chunk of 10 items is processed in a single transaction
- **Rollback on Chunk Failure**: If chunk processing fails, entire chunk is rolled back
- **Skip on Item Failure**: Individual item failures are skipped (up to limit)
- **Commit on Chunk Success**: Successful chunks are committed immediately

---

## Performance Characteristics

### Memory Usage
- **Before (Service-based)**: Load all feeds in memory (~5000 feeds × 1KB = 5MB)
- **After (Batch)**: Process 10 feeds at a time (~10 feeds × 1KB = 10KB per chunk)
- **Improvement**: 99.8% reduction in memory footprint

### Fault Tolerance
- **Before**: Single feed failure aborts entire discovery
- **After**: Skip up to 100 failed feeds, continue processing

### Observability
- **Job Execution History**: Stored in Spring Batch metadata tables
- **Metrics**: Real-time counters for created/updated/unchanged/errors
- **Restartability**: Can resume from last successful chunk

---

## Monitoring Queries

### View Recent Job Executions
```sql
SELECT
    job_instance_id,
    job_name,
    create_time,
    start_time,
    end_time,
    status,
    exit_code
FROM BATCH_JOB_EXECUTION
WHERE job_name = 'feedDiscoveryJob'
ORDER BY create_time DESC
LIMIT 10;
```

### View Step Execution Details
```sql
SELECT
    step_name,
    read_count,
    write_count,
    skip_count,
    commit_count,
    rollback_count,
    read_skip_count,
    process_skip_count,
    write_skip_count
FROM BATCH_STEP_EXECUTION
WHERE job_execution_id = ?;
```

### View Failed Items
```sql
SELECT
    step_name,
    item_count,
    exit_message
FROM BATCH_STEP_EXECUTION
WHERE status = 'FAILED'
ORDER BY start_time DESC;
```

---

## Related Documentation

- [ADR-0003: Spring Batch for Feed Discovery Processing](../adr/0003-spring-batch-feed-discovery.md)
- [ADR-0001: Transit.land API v2 Integration Strategy](../adr/0001-transit-land-api-integration.md)
- [Spring Batch Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)

---

## Decision Date
2025-01-15

## Last Updated
2025-01-15

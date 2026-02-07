# ADR-0011: Replace Spring Batch with Apache Airflow

## Status
Proposed

## Context

The backend uses Spring Batch as the orchestration and execution framework for
three distinct pipelines. This ADR proposes removing Spring Batch entirely and
replacing it with Apache Airflow for orchestration while keeping the existing
Kotlin service layer as the execution backend.

### Current Spring Batch Footprint

Spring Batch touches **37 files** across three pipelines:

| Pipeline | Purpose | Batch Classes | Non-Batch Alternative Exists? |
|---|---|---|---|
| **Region Import** | Bulk-import all GTFS feeds for a region | 8 (`RegionImportJobConfig`, `RegionImportBatchConfig`, `FeedPartitioner`, `RegionImportInitializationTasklet`, `FeedImportWorkerTasklet`, `RegionImportFinalizationTasklet`, `RegionImportOrchestrationTasklet`, `RegionImportJobExecutionListener`) | Yes — `FeedImportSyncService` |
| **Feed Import** | Import a single GTFS feed (7-step pipeline) | 8 (`FeedImportJobConfig`, `GTFSFeedReader`, `FeedImportWriter`, `FeedImportTasklet`, `FeedImportJobExecutionListener`, `FeedImportStepExecutionListener`, `RouteVariantFeedDataTasklet`) + 6 tasklets from other modules | Yes — `FeedImportSyncService` |
| **Feed Discovery** | Discover feeds from Transit.land API | 5 (`SimplifiedFeedDiscoveryJobConfig`, `FeedDiscoveryBatchService`, `FeedDiscoveryReader`, `FeedDiscoveryProcessor`, `FeedDiscoveryWriter`, `FeedDiscoveryJobListener`) | No — logic embedded in batch components |

Plus shared infrastructure:
- `RateLimitedJobLauncher` — Semaphore-based rate limiter wrapping `JobLauncher`
- `FeedImportService` — Async service that launches `feedImportJob` via batch
- `@EnableBatchProcessing` on `MobilispectApplication`
- 2 DB migrations (`V017`, `V019`) creating `BATCH_*` metadata tables
- `spring-boot-starter-batch` and `spring-batch-test` Gradle dependencies

### Pain Points Across All Three Pipelines

1. **Orchestration complexity without proportional value** — The region import
   alone has 8 classes for what is a fan-out/fan-in pattern. The feed import has
   7 sequential steps modeled as separate `Step` beans with tasklets, yet
   `FeedImportSyncService` already implements the identical pipeline as direct
   service calls in ~90 lines. Spring Batch adds configuration overhead without
   adding value for these workloads.

2. **No native scheduling for region imports** — `FeedDiscoveryScheduler` and
   `FeedUpdateScheduler` use `@Scheduled` with cron expressions, but region
   imports have no scheduler. The `SCHEDULED` trigger type exists in the enum
   but is unimplemented. There is no centralized view of all schedules.

3. **No retry with backoff** — The constitutional requirement for "retries with
   exponential backoff and jitter" is unimplemented for all three pipelines.
   Failed feed imports are marked as failed with no automatic retry. Stale
   imports are detected after 1 hour and marked failed.

4. **No operational dashboard** — Spring Batch metadata tables track job
   executions, but there is no UI for viewing DAG-level progress, historical
   run durations, failure trends, or task-level logs. Operators query the
   database or grep logs.

5. **Tight coupling to application lifecycle** — All three pipelines run inside
   the Spring Boot JVM. A pod restart or OOM kill cancels in-progress imports
   with no recovery. The `RateLimitedJobLauncher` serializes job launches to
   work around Spring Batch concurrency bugs, creating a bottleneck.

6. **Single-node parallelism** — Region import uses a `ForkJoinPool` limited to
   one JVM's CPU cores. Feed discovery processes chunks sequentially. There is
   no way to distribute work across nodes.

7. **Batch metadata table overhead** — `BATCH_JOB_INSTANCE`,
   `BATCH_JOB_EXECUTION`, `BATCH_JOB_EXECUTION_PARAMS`,
   `BATCH_STEP_EXECUTION`, `BATCH_STEP_EXECUTION_CONTEXT`, and
   `BATCH_JOB_EXECUTION_CONTEXT` tables accumulate rows indefinitely. The
   application already tracks import status in its own `feed_imports` and
   `region_imports` tables, making the batch tables redundant.

8. **Duplicate execution paths** — `FeedImportService` (async, batch-based) and
   `FeedImportSyncService` (synchronous, no batch) implement the same pipeline.
   Both must be kept in sync. The sync service was introduced specifically
   because the batch approach was too heavy for parallel feed processing within
   a region import.

## Decision

Remove the `spring-boot-starter-batch` dependency entirely and replace all
three pipelines with Apache Airflow DAGs that call the existing Kotlin service
layer via HTTP.

### Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Apache Airflow                               │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  region_import_dag (per region, scheduled or manual)         │    │
│  │                                                              │    │
│  │  [create_import] ──► [discover_feeds] ──► [fan_out_feeds]    │    │
│  │                                               │              │    │
│  │                 ┌─────────────────────────┬────┘              │    │
│  │                 ▼           ▼             ▼                   │    │
│  │           [import_A]  [import_B]  ... [import_N]             │    │
│  │                 │           │             │                   │    │
│  │                 └───────────┴─────────────┘                   │    │
│  │                             ▼                                 │    │
│  │                   [finalize_import]                           │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  feed_import_dag (triggered per feed, standalone)            │    │
│  │                                                              │    │
│  │  [import_feed] ──► [verify_result]                           │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  feed_discovery_dag (daily, global or per-region)            │    │
│  │                                                              │    │
│  │  [discover_feeds] ──► [process_results]                      │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  feed_update_check_dag (daily, checks for new versions)      │    │
│  │                                                              │    │
│  │  [list_regions] ──► [check_versions] ──► [trigger_imports]   │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
└───────────────────────┬──────────────────────────────────────────────┘
                        │  HTTP calls
                        ▼
┌──────────────────────────────────────────────────────────────────────┐
│                Spring Boot Backend (service layer)                    │
│                                                                      │
│  Unchanged:                                                          │
│    FeedImportSyncService, AgencyImportService, RouteImportService,   │
│    RouteVariantImportService, StopSpacingImportService,              │
│    FrequencyImportService, GTFSFeedDownloader,                       │
│    RouteClassificationFeedDataHandler, FeedVersionService            │
│                                                                      │
│  New internal endpoints (thin HTTP wrappers):                        │
│    InternalImportController, InternalDiscoveryController             │
└──────────────────────────────────────────────────────────────────────┘
```

### Integration Strategy: HTTP Operator

Airflow tasks call the Spring Boot backend via HTTP using
`SimpleHttpOperator` / `HttpHook`. The backend exposes thin internal endpoints
that delegate to existing services. This approach:

- **Preserves all existing service logic** — No changes to
  `FeedImportSyncService`, `AgencyImportService`, `RouteImportService`, etc.
- **Maintains module boundaries** — Airflow never accesses the database
  directly; it goes through the API layer per ADR-0010.
- **Supports gradual migration** — Both paths can coexist during transition.

### New Backend Endpoints Required

#### Import Endpoints (`InternalImportController`)

| Endpoint | Method | Purpose | Delegates To |
|---|---|---|---|
| `/api/internal/feeds/{feedId}/import` | POST | Trigger synchronous single-feed import | `FeedImportSyncService.importSync()` |
| `/api/internal/feeds/{feedId}/import/{importId}/status` | GET | Poll import status | `FeedImportRepository.findByImportId()` |
| `/api/internal/regions/{regionId}/feeds/active` | GET | List active feeds for region | `FeedApi.findActiveFeedsByRegion()` |
| `/api/internal/regions/{regionId}/import` | POST | Create RegionImport entity | `RegionImportRepository.save()` |
| `/api/internal/regions/{regionId}/import/{importId}/finalize` | PUT | Finalize region import status | Finalization logic (from `RegionImportFinalizationTasklet`) |

#### Discovery Endpoints (`InternalDiscoveryController`)

| Endpoint | Method | Purpose | Delegates To |
|---|---|---|---|
| `/api/internal/discovery/feeds` | POST | Discover all feeds from Transit.land | New `FeedDiscoveryService.discoverAll()` |
| `/api/internal/discovery/feeds/region/{regionId}` | POST | Discover feeds for a region | New `FeedDiscoveryService.discoverForRegion()` |

#### Feed Update Endpoints

| Endpoint | Method | Purpose | Delegates To |
|---|---|---|---|
| `/api/internal/regions/auto-update` | GET | List regions with auto-update | `MetropolitanRegionRepository.findAllByAutoUpdateEnabled()` |
| `/api/internal/feeds/{feedId}/check-update` | GET | Check if feed has new version | `FeedVersionService.checkForUpdates()` |

These are `/api/internal/` endpoints secured by network policy (only Airflow
pods can reach them), not exposed on the public ingress.

### Pipeline-by-Pipeline Migration

#### Pipeline 1: Region Import

**Current**: `RegionImportService` → `RateLimitedJobLauncher` →
`regionImportJob` (Spring Batch) → `FeedImportWorkerTasklet` →
`FeedApi.importSync()` → `FeedImportSyncService`

**Proposed**: Airflow `region_import_dag` → HTTP calls →
`InternalImportController` → `FeedImportSyncService`

**What changes**: Orchestration moves to Airflow. `FeedImportSyncService`
(already working, battle-tested) becomes the sole execution path.

**Classes removed**: `RegionImportJobConfig`, `RegionImportBatchConfig`,
`FeedPartitioner`, `RegionImportInitializationTasklet`,
`FeedImportWorkerTasklet`, `RegionImportFinalizationTasklet`,
`RegionImportOrchestrationTasklet`, `RegionImportJobExecutionListener`

**`RegionImportService` changes**: Remove `RateLimitedJobLauncher` and
`regionImportJob` dependencies. The `import()` method creates the
`RegionImport` entity and returns — Airflow handles the rest. Alternatively,
Airflow calls the create endpoint directly and `RegionImportService` becomes a
pure query service.

#### Pipeline 2: Feed Import (Single Feed)

**Current**: `FeedImportService.import()` → `RateLimitedJobLauncher` →
`feedImportJob` (Spring Batch, 7 steps) → tasklets

**Proposed**: Airflow `feed_import_dag` → HTTP call →
`InternalImportController` → `FeedImportSyncService.importSync()`

**What changes**: `FeedImportService` (the async batch launcher) is deleted.
`FeedImportSyncService` already implements the full pipeline without batch.

**Classes removed**: `FeedImportJobConfig`, `GTFSFeedReader`,
`FeedImportWriter`, `FeedImportTasklet`, `FeedImportJobExecutionListener`,
`FeedImportStepExecutionListener`, `RouteVariantFeedDataTasklet`,
`FeedImportService`, `RateLimitedJobLauncher`

**Tasklets that become unused**: `AgencyImportTasklet`, `RouteImportTasklet`,
`RouteVariantImportTasklet`, `StopSpacingImportTasklet`,
`RouteClassificationTasklet`, `FrequencyImportTasklet`. These are thin wrappers
that delegate to their respective `*ImportService` classes.
`FeedImportSyncService` already calls the `*ImportService` classes directly, so
the tasklets serve no purpose once Spring Batch is removed.

#### Pipeline 3: Feed Discovery

**Current**: `FeedDiscoveryScheduler` → `FeedDiscoveryBatchService` →
`simplifiedFeedDiscoveryJob` (Spring Batch, chunked reader/processor/writer)

**Proposed**: Airflow `feed_discovery_dag` → HTTP call →
`InternalDiscoveryController` → `FeedDiscoveryService` (new, extracted from
batch components)

**What changes**: The logic currently split across `FeedDiscoveryReader`,
`FeedDiscoveryProcessor`, and `FeedDiscoveryWriter` is consolidated into a new
`FeedDiscoveryService` that processes feeds iteratively with the same chunking
semantics but without Spring Batch. The Transit.land pagination, rate limiting,
and retry logic already live in `TransitLandAPI`/Resilience4j and are
unaffected.

**Classes removed**: `SimplifiedFeedDiscoveryJobConfig`,
`FeedDiscoveryBatchService`, `FeedDiscoveryReader`, `FeedDiscoveryProcessor`,
`FeedDiscoveryWriter`, `FeedDiscoveryJobListener`

**New class**: `FeedDiscoveryService` — consolidates the read → process → write
loop. Returns `FeedDiscoveryResult` (same data model as
`FeedDiscoveryJobResult` without the Spring Batch job execution fields).

#### Pipeline 4: Feed Update Check

**Current**: `FeedUpdateScheduler` → `FeedImportService.import()` →
Spring Batch `feedImportJob`

**Proposed**: Airflow `feed_update_check_dag` → HTTP calls →
`InternalImportController` → `FeedImportSyncService.importSync()`

**What changes**: `FeedUpdateScheduler` is deleted. Its logic (list regions →
check versions → trigger imports) becomes an Airflow DAG with three tasks.
`FeedVersionService` is called via HTTP. Triggered imports go through the
same `feed_import_dag`.

**Classes removed**: `FeedUpdateScheduler`, `FeedDiscoveryScheduler`

### DAG Definitions

#### `region_import_dag.py`

```python
from airflow import DAG
from airflow.decorators import task
from airflow.providers.http.operators.http import SimpleHttpOperator
from datetime import datetime, timedelta
import json

default_args = {
    "owner": "mobilispect",
    "retries": 3,
    "retry_delay": timedelta(minutes=2),
    "retry_exponential_backoff": True,
    "max_retry_delay": timedelta(minutes=30),
    "execution_timeout": timedelta(hours=2),
}

def create_region_import_dag(region_id: str, schedule: str | None):
    dag = DAG(
        dag_id=f"region_import_{region_id}",
        default_args=default_args,
        description=f"Import all GTFS feeds for region {region_id}",
        schedule=schedule,
        start_date=datetime(2026, 1, 1),
        catchup=False,
        max_active_runs=1,
        tags=["region-import", region_id],
    )

    with dag:
        create_import = SimpleHttpOperator(
            task_id="create_region_import",
            http_conn_id="mobilispect_backend",
            endpoint=f"/api/internal/regions/{region_id}/import",
            method="POST",
            data=json.dumps({"triggerType": "SCHEDULED"}),
            headers={"Content-Type": "application/json"},
            response_filter=lambda r: r.json(),
        )

        discover_feeds = SimpleHttpOperator(
            task_id="discover_active_feeds",
            http_conn_id="mobilispect_backend",
            endpoint=f"/api/internal/regions/{region_id}/feeds/active",
            method="GET",
            response_filter=lambda r: r.json(),
        )

        @task
        def import_feed(feed: dict, region_import_id: str):
            from airflow.providers.http.hooks.http import HttpHook
            hook = HttpHook(http_conn_id="mobilispect_backend", method="POST")
            response = hook.run(
                endpoint=f"/api/internal/feeds/{feed['feedId']}/import",
                data=json.dumps({"triggerType": "SCHEDULED"}),
                headers={"Content-Type": "application/json"},
            )
            return response.json()

        finalize = SimpleHttpOperator(
            task_id="finalize_region_import",
            http_conn_id="mobilispect_backend",
            endpoint=(
                f"/api/internal/regions/{region_id}/import/"
                "{{ ti.xcom_pull(task_ids='create_region_import')"
                "['regionImportId'] }}/finalize"
            ),
            method="PUT",
            trigger_rule="all_done",
        )

        create_import >> discover_feeds
        feeds = discover_feeds.output
        region_import_id = create_import.output["regionImportId"]
        feed_results = import_feed.expand(
            feed=feeds["feeds"],
            region_import_id=region_import_id,
        )
        feed_results >> finalize

    return dag

# Region list loaded from Airflow Variables or config
REGIONS = {
    "r-f25d-montral": "0 3 * * *",
    "r-f256-toronto": "0 4 * * *",
    "r-dr5r-newyorkcity": "0 2 * * *",
}
for region_id, schedule in REGIONS.items():
    globals()[f"region_import_{region_id}"] = (
        create_region_import_dag(region_id, schedule)
    )
```

#### `feed_import_dag.py`

```python
from airflow import DAG
from airflow.providers.http.operators.http import SimpleHttpOperator
from datetime import datetime, timedelta
import json

dag = DAG(
    dag_id="feed_import_single",
    description="Import a single GTFS feed",
    schedule=None,
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=5,
    params={"feed_id": "", "trigger_type": "MANUAL"},
    tags=["feed-import"],
    default_args={
        "owner": "mobilispect",
        "retries": 3,
        "retry_delay": timedelta(minutes=2),
        "retry_exponential_backoff": True,
        "max_retry_delay": timedelta(minutes=30),
        "execution_timeout": timedelta(hours=1),
    },
)

with dag:
    SimpleHttpOperator(
        task_id="import_feed",
        http_conn_id="mobilispect_backend",
        endpoint="/api/internal/feeds/{{ params.feed_id }}/import",
        method="POST",
        data=json.dumps({"triggerType": "{{ params.trigger_type }}"}),
        headers={"Content-Type": "application/json"},
        response_filter=lambda r: r.json(),
    )
```

#### `feed_discovery_dag.py`

```python
from airflow import DAG
from airflow.providers.http.operators.http import SimpleHttpOperator
from datetime import datetime, timedelta
import json

dag = DAG(
    dag_id="feed_discovery_global",
    description="Discover all GTFS feeds from Transit.land",
    schedule="15 1 * * *",          # Daily at 1:15 AM (matches old scheduler)
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["feed-discovery"],
    default_args={
        "owner": "mobilispect",
        "retries": 2,
        "retry_delay": timedelta(minutes=5),
        "retry_exponential_backoff": True,
        "execution_timeout": timedelta(hours=4),
    },
)

with dag:
    SimpleHttpOperator(
        task_id="discover_feeds",
        http_conn_id="mobilispect_backend",
        endpoint="/api/internal/discovery/feeds",
        method="POST",
        data=json.dumps({"specType": "gtfs"}),
        headers={"Content-Type": "application/json"},
        response_filter=lambda r: r.json(),
    )
```

#### `feed_update_check_dag.py`

```python
from airflow import DAG
from airflow.decorators import task
from airflow.providers.http.operators.http import SimpleHttpOperator
from datetime import datetime, timedelta
import json

dag = DAG(
    dag_id="feed_update_check",
    description="Check for GTFS feed updates and trigger imports",
    schedule="0 2 * * *",           # Daily at 2 AM (matches old scheduler)
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["feed-update"],
    default_args={
        "owner": "mobilispect",
        "retries": 2,
        "retry_delay": timedelta(minutes=5),
        "retry_exponential_backoff": True,
        "execution_timeout": timedelta(hours=2),
    },
)

with dag:
    list_regions = SimpleHttpOperator(
        task_id="list_auto_update_regions",
        http_conn_id="mobilispect_backend",
        endpoint="/api/internal/regions/auto-update",
        method="GET",
        response_filter=lambda r: r.json(),
    )

    @task
    def check_and_import(region: dict):
        from airflow.providers.http.hooks.http import HttpHook
        hook = HttpHook(http_conn_id="mobilispect_backend", method="GET")
        result = hook.run(
            endpoint=(
                f"/api/internal/feeds/region/"
                f"{region['regionOnestopId']}/check-updates"
            ),
        ).json()
        # Trigger imports for feeds with updates
        if result.get("feedsWithUpdates"):
            post_hook = HttpHook(
                http_conn_id="mobilispect_backend", method="POST"
            )
            for feed_id in result["feedsWithUpdates"]:
                post_hook.run(
                    endpoint=f"/api/internal/feeds/{feed_id}/import",
                    data=json.dumps({"triggerType": "AUTOMATIC"}),
                    headers={"Content-Type": "application/json"},
                )
        return result

    regions = list_regions.output["regions"]
    check_and_import.expand(region=regions)
```

### Complete Removal Inventory

#### Files Deleted (30 files)

**Region Import Batch** (8 files):
- `backend/src/main/kotlin/.../region/batch/RegionImportJobConfig.kt`
- `backend/src/main/kotlin/.../region/batch/RegionImportBatchConfig.kt`
- `backend/src/main/kotlin/.../region/batch/FeedPartitioner.kt`
- `backend/src/main/kotlin/.../region/batch/RegionImportInitializationTasklet.kt`
- `backend/src/main/kotlin/.../region/batch/FeedImportWorkerTasklet.kt`
- `backend/src/main/kotlin/.../region/batch/RegionImportFinalizationTasklet.kt`
- `backend/src/main/kotlin/.../region/batch/RegionImportOrchestrationTasklet.kt`
- `backend/src/main/kotlin/.../region/batch/RegionImportJobExecutionListener.kt`

**Feed Import Batch** (7 files):
- `backend/src/main/kotlin/.../feed/batch/import/FeedImportJobConfig.kt`
- `backend/src/main/kotlin/.../feed/batch/import/GTFSFeedReader.kt`
- `backend/src/main/kotlin/.../feed/batch/import/FeedImportWriter.kt`
- `backend/src/main/kotlin/.../feed/batch/import/FeedImportTasklet.kt`
- `backend/src/main/kotlin/.../feed/batch/import/FeedImportJobExecutionListener.kt`
- `backend/src/main/kotlin/.../feed/batch/import/FeedImportStepExecutionListener.kt`
- `backend/src/main/kotlin/.../feed/batch/import/RouteVariantFeedDataTasklet.kt`

**Feed Discovery Batch** (6 files):
- `backend/src/main/kotlin/.../feed/batch/discovery/FeedDiscoveryJobConfig.kt`
- `backend/src/main/kotlin/.../feed/batch/discovery/FeedDiscoveryBatchService.kt`
- `backend/src/main/kotlin/.../feed/batch/discovery/FeedDiscoveryReader.kt`
- `backend/src/main/kotlin/.../feed/batch/discovery/FeedDiscoveryProcessor.kt`
- `backend/src/main/kotlin/.../feed/batch/discovery/FeedDiscoveryWriter.kt`
- `backend/src/main/kotlin/.../feed/batch/discovery/FeedDiscoveryJobListener.kt`

**Import Tasklets** (6 files):
- `backend/src/main/kotlin/.../agency/batch/import/AgencyImportTasklet.kt`
- `backend/src/main/kotlin/.../route/batch/import/RouteImportTasklet.kt`
- `backend/src/main/kotlin/.../route/batch/variant/RouteVariantImportTasklet.kt`
- `backend/src/main/kotlin/.../route/batch/spacing/StopSpacingImportTasklet.kt`
- `backend/src/main/kotlin/.../route/batch/classification/RouteClassificationTasklet.kt`
- `backend/src/main/kotlin/.../route/batch/frequency/FrequencyImportTasklet.kt`

**Shared Infrastructure** (3 files):
- `backend/src/main/kotlin/.../feed/service/RateLimitedJobLauncher.kt`
- `backend/src/main/kotlin/.../feed/service/FeedImportService.kt`
- `backend/src/main/kotlin/.../feed/service/FeedDiscoveryScheduler.kt`

#### Files Modified (4 files)

| File | Change |
|---|---|
| `MobilispectApplication.kt` | Remove `@EnableBatchProcessing` |
| `RegionImportService.kt` | Remove `RateLimitedJobLauncher`, `regionImportJob`, and `launchRegionImportJob()`. Becomes query-only or is replaced by internal controller. |
| `FeedUpdateScheduler.kt` | Deleted — logic moves to `feed_update_check_dag` |
| `build.gradle.kts` | Remove `spring-boot-starter-batch` and `spring-batch-test` dependencies |

#### Files Created (5 files)

| File | Purpose |
|---|---|
| `InternalImportController.kt` | HTTP endpoints for Airflow to trigger imports |
| `InternalDiscoveryController.kt` | HTTP endpoints for Airflow to trigger discovery |
| `FeedDiscoveryService.kt` | Non-batch feed discovery (extracted from reader/processor/writer) |
| `airflow/dags/*.py` (4 DAG files) | Airflow DAG definitions |

#### Files Unchanged (core services kept as-is)

| File | Reason |
|---|---|
| `FeedImportSyncService` | Core import pipeline — becomes the single execution path |
| `AgencyImportService` | Domain processing logic (called by `FeedImportSyncService`) |
| `RouteImportService` | Domain processing logic |
| `RouteVariantImportService` | Domain processing logic |
| `StopSpacingImportService` | Domain processing logic |
| `FrequencyImportService` | Domain processing logic |
| `GTFSFeedDownloader` | GTFS download and parsing |
| `RouteClassificationFeedDataHandler` | Route classification logic |
| `RouteCommonSectionFeedDataHandler` | Common section detection |
| `FeedVersionService` | SHA-1 version comparison |
| `RegionImport` / `FeedImport` (domain) | Status tracking (kept in application DB) |
| `RegionImportRepository` / `FeedImportRepository` | Database access |
| `RegionController` (query endpoints) | Public API for import status |

#### Database Changes

**New migration** (`V0XX__drop_spring_batch_tables.sql`):
```sql
-- Drop Spring Batch metadata tables (data is redundant with
-- feed_imports and region_imports tables)
DROP TABLE IF EXISTS BATCH_STEP_EXECUTION_CONTEXT CASCADE;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION_CONTEXT CASCADE;
DROP TABLE IF EXISTS BATCH_STEP_EXECUTION CASCADE;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION_PARAMS CASCADE;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION CASCADE;
DROP TABLE IF EXISTS BATCH_JOB_INSTANCE CASCADE;
DROP SEQUENCE IF EXISTS BATCH_JOB_INSTANCE_SEQ;
DROP SEQUENCE IF EXISTS BATCH_JOB_EXECUTION_SEQ;
DROP SEQUENCE IF EXISTS BATCH_STEP_EXECUTION_SEQ;
DROP SEQUENCE IF EXISTS BATCH_JOB_SEQ;
DROP SEQUENCE IF EXISTS BATCH_STEP_SEQ;
```

### Deployment

#### Airflow Infrastructure (Kubernetes)

```
backend/deploy/base/airflow/
├── kustomization.yaml
├── webserver-deployment.yaml       # Airflow UI
├── scheduler-deployment.yaml       # DAG scheduler
├── configmap.yaml                  # airflow.cfg
├── dags-configmap.yaml             # DAG files (or use git-sync sidecar)
├── service.yaml                    # Internal service for webserver
└── network-policy.yaml             # Only Airflow pods can reach /api/internal/*
```

**Recommended executor**: `KubernetesExecutor` — each task runs in its own pod.
- Per-task resource limits (CPU/memory)
- Natural isolation between feed imports
- Scales to zero when idle
- No persistent worker pods needed

**Airflow metadata database**: Separate PostgreSQL database (or schema in
existing instance) for Airflow's own metadata.

**DAG deployment**: Git-sync sidecar on scheduler pod watches the `airflow/dags/`
directory in the repository.

#### Network Security

```yaml
# NetworkPolicy: only Airflow pods can reach internal endpoints
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: internal-api-access
spec:
  podSelector:
    matchLabels:
      app: mobilispect-api
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: airflow
    ports:
    - port: 8080
      protocol: TCP
```

### Migration Plan

| Phase | Duration | Actions |
|---|---|---|
| **Phase 1: Extract Discovery** | 1 week | Create `FeedDiscoveryService` by extracting logic from batch reader/processor/writer. Verify with unit tests. Both batch and non-batch paths coexist. |
| **Phase 2: Deploy Airflow** | 1 week | Deploy Airflow infrastructure. Create internal endpoints. Deploy all 4 DAGs. Run alongside existing Spring Batch/schedulers. |
| **Phase 3: Validate** | 2 weeks | Run both paths in parallel. Compare results. Monitor Airflow dashboards. Build confidence. |
| **Phase 4: Switch Over** | 1 week | Disable `@Scheduled` methods and Spring Batch job launches. Airflow becomes the sole orchestrator. Spring Batch code still present but unused. |
| **Phase 5: Remove Spring Batch** | 1 week | Delete 30 files. Remove Gradle dependencies. Remove `@EnableBatchProcessing`. Add migration to drop `BATCH_*` tables. |

### Test Impact

#### Tests Deleted
- `RegionImportJobConfigTest` — tests Spring Batch job structure
- Batch-specific assertions in `FeedImportEndToEndIntegrationTest`

#### Tests Modified
- `RegionImportServiceTest` — remove mocks for `RateLimitedJobLauncher` and
  `regionImportJob`; test the simplified service

#### Tests Added
- `InternalImportControllerTest` — unit tests for internal HTTP endpoints
- `InternalDiscoveryControllerTest` — unit tests for discovery endpoints
- `FeedDiscoveryServiceTest` — unit tests for extracted discovery logic
- Contract tests between Airflow DAGs and internal API endpoints (optional,
  recommended)

## Consequences

### Positive

1. **Unified scheduling** — All 4 pipelines (region import, feed import, feed
   discovery, feed update check) have cron schedules visible in one Airflow UI.
   No more scattered `@Scheduled` annotations.

2. **Retry with backoff** — `retries=3`, `retry_exponential_backoff=True`,
   `max_retry_delay=timedelta(minutes=30)` are first-class Airflow features.
   Fulfills the constitutional requirement across all pipelines.

3. **Operational dashboard** — Airflow web UI provides DAG run history, task
   duration trends, Gantt charts, log streaming, and failure alerting.

4. **Horizontal scaling** — `KubernetesExecutor` runs each feed import in its
   own pod. Parallelism scales to cluster capacity instead of JVM thread count.

5. **Decoupled from application lifecycle** — Spring Boot deployments do not
   cancel in-progress imports. Airflow retries on backend errors.

6. **30 fewer Kotlin files** — Removes ~2,500 lines of orchestration code.
   Spring Boot focuses on domain logic and data access.

7. **Single execution path** — `FeedImportSyncService` becomes the only import
   implementation. No more keeping `FeedImportService` (batch) and
   `FeedImportSyncService` (non-batch) in sync.

8. **No more batch metadata tables** — 6 `BATCH_*` tables and 5 sequences
   removed. Import tracking already lives in `feed_imports` and
   `region_imports`.

9. **Alerting** — `on_failure_callback`, email, Slack, PagerDuty integrations.

10. **Cross-region deduplication** — Airflow pool slots and
    `max_active_runs` prevent duplicate work.

### Negative

1. **New infrastructure** — Airflow webserver, scheduler, metadata database.
   Increases operational surface area.

2. **Two language runtimes** — Python DAGs + Kotlin services. Developers need
   familiarity with both.

3. **Network dependency** — HTTP between Airflow and backend. Mitigated by
   retries. Latency is negligible for batch workloads.

4. **Loss of in-process transactions** — Spring Batch steps run within Spring
   transactions. Airflow HTTP calls are not transactional. Mitigated by
   idempotent endpoints (`FeedImportSyncService` already checks for active
   imports).

5. **DAG deployment** — Requires git-sync sidecar or baked into container image.

6. **Feed discovery extraction** — The logic in `FeedDiscoveryReader`,
   `FeedDiscoveryProcessor`, and `FeedDiscoveryWriter` must be consolidated into
   `FeedDiscoveryService`. This is new code that needs thorough testing.

### Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Airflow SPOF | HA deployment (multiple schedulers, webserver replicas) |
| API contract drift | Version internal endpoints; contract tests |
| Import idempotency | `FeedImportSyncService` already enforces single-active-import |
| Discovery service regression | Extract with comprehensive tests; parallel run in Phase 3 |
| Region config drift | Load region list from backend API, not hardcoded in DAGs |
| HTTP latency | Negligible for multi-minute batch operations |

## Alternatives Considered

### 1. Keep Spring Batch, Add @Scheduled and Dashboard

Add `@Scheduled` triggers plus Spring Boot Admin or custom dashboard.

**Pros**: No new infrastructure. Incremental.
**Cons**: Does not address retry with backoff, horizontal scaling, lifecycle
coupling, dual execution paths, or metadata table bloat.

**Decision**: Rejected — addresses 2 of 8 pain points.

### 2. Spring Cloud Data Flow

**Pros**: Spring ecosystem. Dashboard. Scheduling.
**Cons**: Smaller community than Airflow. Heavier footprint. Limited dynamic
task mapping.

**Decision**: Rejected.

### 3. Temporal.io

**Pros**: Durable workflows. Kotlin/JVM SDK (no second runtime). Retry built-in.
**Cons**: Weaker scheduling support. Smaller community. No scheduling dashboard.

**Decision**: Viable alternative. Revisit if Airflow proves too heavy.

### 4. Prefect

**Pros**: Simpler than Airflow. Managed cloud option.
**Cons**: Smaller community. Less battle-tested self-hosted.

**Decision**: Rejected.

### 5. Remove Spring Batch, Use Only @Scheduled + Coroutines (No Orchestrator)

Extract batch logic into plain services. Use `@Scheduled` with Kotlin
coroutines for parallelism. No external orchestrator.

**Pros**: Zero new infrastructure. Simplest operational model. Single runtime.
**Cons**: No dashboard. No centralized retry policy. No alerting. Scheduling
still scattered in code. Coupled to app lifecycle.

**Decision**: Rejected — reintroduces pain points 2-5.

## References

- [Apache Airflow Documentation](https://airflow.apache.org/docs/)
- [KubernetesExecutor](https://airflow.apache.org/docs/apache-airflow/stable/core-concepts/executor/kubernetes.html)
- ADR-0003: Spring Batch for Feed Discovery Processing (superseded by this ADR)
- ADR-0009: Spring Modulith Module Boundaries
- ADR-0010: API-Driven Module Communication

## Decision Date
2026-02-07

## Decision Makers
- Development Team
- Technical Lead

## Review Date
2026-05-07 (3 months after proposal)

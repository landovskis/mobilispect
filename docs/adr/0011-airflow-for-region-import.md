# ADR-0011: Apache Airflow for Region Import Orchestration

## Status
Proposed

## Context

The region import system bulk-imports GTFS transit feeds for all feeds within a
geographic region. It currently uses Spring Batch with a parent-child job
architecture:

1. **RegionImportJobConfig** defines a three-step flow: Initialization →
   Partitioned Feed Import (parallel via ForkJoinPool) → Finalization.
2. **FeedImportJobConfig** defines a sequential seven-step pipeline per feed:
   Download → Agencies → Routes → Route Variants → Stop Spacing →
   Classification → Frequency.
3. **RegionImportService** creates a `RegionImport` entity, then launches the
   parent job asynchronously via a `RateLimitedJobLauncher`.

This works but has accumulated several pain points:

### Current Pain Points

1. **No native scheduling** — There is no `@Scheduled` trigger for region
   imports. The `SCHEDULED` trigger type exists in the enum but is unimplemented.
   Scheduling must be added manually per region and has no centralized
   management.

2. **Limited visibility** — Spring Batch metadata tables track job executions,
   but there is no dashboard showing DAG-level progress, historical run
   durations, retry counts, or feed-level failure trends. Operators must query
   the database directly or rely on log aggregation.

3. **No built-in retry with backoff** — The constitutional requirement for
   "retries with exponential backoff and jitter" is not implemented for feed
   imports. A stale-import detector marks imports as failed after 1 hour, but
   there is no automatic retry of failed feeds.

4. **Coarse parallelism control** — The ForkJoinPool work-stealing executor runs
   inside the JVM. Parallelism is limited to the cores on a single node. There
   is no way to distribute feed imports across multiple worker nodes.

5. **Tight coupling to application lifecycle** — The import job runs inside the
   Spring Boot application process. A deployment, OOM kill, or pod restart
   cancels in-progress imports with no automatic recovery. The
   `RateLimitedJobLauncher` serializes job launches to avoid Spring Batch
   concurrency issues, creating a bottleneck.

6. **Complex Spring Batch wiring** — The region import alone involves 8
   configuration/tasklet classes (`RegionImportJobConfig`,
   `RegionImportBatchConfig`, `FeedPartitioner`,
   `RegionImportInitializationTasklet`, `FeedImportWorkerTasklet`,
   `RegionImportFinalizationTasklet`, `RegionImportJobExecutionListener`,
   `RegionImportOrchestrationTasklet` [deprecated]). The abstractions add
   cognitive overhead without providing proportional value for what is
   fundamentally an orchestration problem.

7. **No cross-region coordination** — If multiple regions share feeds, there is
   no deduplication. Each region import independently downloads and processes
   shared feeds.

### Why Consider Airflow

Apache Airflow is purpose-built for orchestrating data pipelines with:
- DAG-based workflow definitions with explicit task dependencies
- Built-in scheduler with cron expressions per DAG
- Web UI with real-time progress, Gantt charts, task logs, and historical trends
- Native retry policies with exponential backoff
- Worker pool scaling (Celery, Kubernetes, or local executors)
- Sensor tasks for triggering on external events
- Cross-DAG deduplication via `ExternalTaskSensor`
- REST API for programmatic triggering (replacing the current REST endpoint)

## Decision

Replace the Spring Batch region import orchestration with Apache Airflow DAGs
while keeping the existing Spring Boot import services as the execution backend.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Apache Airflow                              │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  region_import_dag (per region, scheduled or manual)    │    │
│  │                                                         │    │
│  │  [start] ──► [discover_feeds] ──► [fan_out_feeds]       │    │
│  │                                        │                │    │
│  │              ┌─────────────────────────┼──────────┐     │    │
│  │              ▼             ▼           ▼          ▼     │    │
│  │        [import_feed_A] [import_feed_B] ... [import_N]   │    │
│  │              │             │           │          │     │    │
│  │              └─────────────┼───────────┘          │     │    │
│  │                            ▼                      ▼     │    │
│  │                      [finalize_region_import]           │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  feed_import_dag (triggered per feed, or standalone)    │    │
│  │                                                         │    │
│  │  [download_gtfs] ──► [process_agencies]                 │    │
│  │                        ──► [process_routes]             │    │
│  │                        ──► [process_variants]           │    │
│  │                        ──► [process_stop_spacing]       │    │
│  │                        ──► [classify_routes]            │    │
│  │                        ──► [process_frequencies]        │    │
│  │                        ──► [mark_complete]              │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
└────────────────────────┬────────────────────────────────────────┘
                         │  HTTP calls
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              Spring Boot Backend (unchanged services)           │
│                                                                 │
│  AgencyImportService, RouteImportService,                       │
│  RouteVariantImportService, StopSpacingImportService,           │
│  FrequencyImportService, GTFSFeedDownloader,                    │
│  RouteClassificationFeedDataHandler, FeedImportRepository       │
└─────────────────────────────────────────────────────────────────┘
```

### Integration Strategy: HTTP Operator

Airflow tasks call the Spring Boot backend via HTTP using the
`SimpleHttpOperator`. The backend exposes thin orchestration endpoints that
delegate to the existing import services. This approach:

- **Preserves existing service logic** — No changes to `FeedImportSyncService`,
  `AgencyImportService`, `RouteImportService`, etc.
- **Maintains module boundaries** — Airflow never accesses the database directly;
  it goes through the existing API layer.
- **Supports gradual migration** — The manual REST trigger
  (`POST /api/feeds/regions/{regionId}/import-all`) can coexist with the Airflow
  DAG during transition.

#### New Backend Endpoints Required

The existing `FeedImportSyncService.importSync()` already contains the full
import pipeline. A small set of new endpoints would expose finer-grained
control:

| Endpoint | Purpose | Existing Code |
|---|---|---|
| `POST /api/internal/feeds/{feedId}/import` | Trigger synchronous single-feed import | Wraps `FeedImportSyncService.importSync()` |
| `GET /api/internal/feeds/{feedId}/import/{importId}/status` | Poll import status | Wraps `FeedImportRepository.findByImportId()` |
| `GET /api/internal/regions/{regionId}/feeds/active` | List active feeds for a region | Wraps `FeedApi.findActiveFeedsByRegion()` |
| `POST /api/internal/regions/{regionId}/import` | Create RegionImport tracking entity | Wraps `RegionImportRepository.save()` |
| `PUT /api/internal/regions/{regionId}/import/{importId}/finalize` | Finalize region import status | Wraps finalization logic |

These are `/api/internal/` endpoints, not exposed publicly. They are called only
by Airflow workers.

### DAG Definitions

#### `region_import_dag.py`

```python
from airflow import DAG
from airflow.decorators import task
from airflow.providers.http.operators.http import SimpleHttpOperator
from airflow.utils.task_group import TaskGroup
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

# One DAG per region, generated dynamically
def create_region_import_dag(region_id: str, schedule: str | None):
    dag = DAG(
        dag_id=f"region_import_{region_id}",
        default_args=default_args,
        description=f"Import all GTFS feeds for region {region_id}",
        schedule=schedule,       # e.g., "0 3 * * *" for 3 AM daily
        start_date=datetime(2026, 1, 1),
        catchup=False,
        max_active_runs=1,       # Replaces unique active import constraint
        tags=["region-import", region_id],
    )

    with dag:
        # Step 1: Create region import tracking entity
        create_import = SimpleHttpOperator(
            task_id="create_region_import",
            http_conn_id="mobilispect_backend",
            endpoint=f"/api/internal/regions/{region_id}/import",
            method="POST",
            data=json.dumps({"triggerType": "SCHEDULED"}),
            headers={"Content-Type": "application/json"},
            response_filter=lambda r: r.json(),
        )

        # Step 2: Discover active feeds
        discover_feeds = SimpleHttpOperator(
            task_id="discover_active_feeds",
            http_conn_id="mobilispect_backend",
            endpoint=f"/api/internal/regions/{region_id}/feeds/active",
            method="GET",
            response_filter=lambda r: r.json(),
        )

        # Step 3: Fan out — dynamic task mapping over feeds
        @task
        def import_feed(feed: dict, region_import_id: str):
            """Trigger feed import via HTTP and poll until complete."""
            from airflow.providers.http.hooks.http import HttpHook
            hook = HttpHook(http_conn_id="mobilispect_backend", method="POST")

            # Trigger import
            response = hook.run(
                endpoint=f"/api/internal/feeds/{feed['feedId']}/import",
                data=json.dumps({"triggerType": "SCHEDULED"}),
                headers={"Content-Type": "application/json"},
            )
            return response.json()

        # Step 4: Finalize — determine COMPLETED / PARTIAL_SUCCESS / FAILED
        finalize = SimpleHttpOperator(
            task_id="finalize_region_import",
            http_conn_id="mobilispect_backend",
            endpoint=(
                f"/api/internal/regions/{region_id}/import/"
                "{{ ti.xcom_pull(task_ids='create_region_import')['regionImportId'] }}"
                "/finalize"
            ),
            method="PUT",
            trigger_rule="all_done",  # Run even if some feeds failed
        )

        # Wire dependencies
        feeds = discover_feeds.output
        region_import_id = create_import.output["regionImportId"]
        feed_results = import_feed.expand(
            feed=feeds["feeds"],
            region_import_id=region_import_id,
        )
        feed_results >> finalize

    return dag


# Generate DAGs for each configured region
# In production, this list comes from a config file or Airflow Variable
REGIONS = {
    "r-f25d-montral": "0 3 * * *",         # Daily at 3 AM
    "r-f256-toronto": "0 4 * * *",          # Daily at 4 AM
    "r-dr5r-newyorkcity": "0 2 * * *",      # Daily at 2 AM
}

for region_id, schedule in REGIONS.items():
    globals()[f"region_import_{region_id}"] = (
        create_region_import_dag(region_id, schedule)
    )
```

#### `feed_import_dag.py` (optional, for standalone single-feed imports)

```python
from airflow import DAG
from airflow.providers.http.operators.http import SimpleHttpOperator
from datetime import datetime, timedelta
import json

dag = DAG(
    dag_id="feed_import_single",
    description="Import a single GTFS feed (triggered manually or by API)",
    schedule=None,                # Only triggered externally
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=5,            # Allow 5 concurrent single-feed imports
    params={"feed_id": "", "trigger_type": "MANUAL"},
    tags=["feed-import"],
)

with dag:
    import_feed = SimpleHttpOperator(
        task_id="import_feed",
        http_conn_id="mobilispect_backend",
        endpoint="/api/internal/feeds/{{ params.feed_id }}/import",
        method="POST",
        data=json.dumps({"triggerType": "{{ params.trigger_type }}"}),
        headers={"Content-Type": "application/json"},
        response_filter=lambda r: r.json(),
        retries=3,
        retry_delay=timedelta(minutes=2),
        retry_exponential_backoff=True,
    )
```

### Deployment

#### Infrastructure Addition

Add an Airflow instance to the Kubernetes deployment:

```yaml
# backend/deploy/base/airflow/
├── kustomization.yaml
├── webserver-deployment.yaml
├── scheduler-deployment.yaml
├── worker-deployment.yaml        # If using CeleryExecutor/K8sExecutor
├── configmap.yaml                # airflow.cfg
├── dags-configmap.yaml           # DAG files
└── service.yaml
```

**Recommended executor**: `KubernetesExecutor` — each Airflow task runs in its
own pod. This provides:
- Per-task resource limits (CPU/memory)
- Natural isolation between feed imports
- Automatic cleanup of completed task pods
- Scales to zero when no imports are running

#### Airflow Metadata Database

Airflow requires its own PostgreSQL database (or schema). Options:
1. **Separate database** (recommended for production) — isolates Airflow
   metadata from application data.
2. **Separate schema in existing PostgreSQL** — simpler for development but
   risks resource contention.

### Migration Plan

| Phase | Duration | Description |
|---|---|---|
| **Phase 1: Coexistence** | 2-4 weeks | Deploy Airflow alongside Spring Batch. Both paths work. Airflow DAGs call the same backend endpoints. Validate correctness by comparing results. |
| **Phase 2: Airflow Primary** | 2 weeks | Route all new region imports through Airflow. Keep Spring Batch as fallback. Monitor Airflow dashboards for reliability. |
| **Phase 3: Spring Batch Removal** | 1-2 weeks | Remove `RegionImportJobConfig`, `RegionImportBatchConfig`, `FeedPartitioner`, `RegionImportInitializationTasklet`, `FeedImportWorkerTasklet`, `RegionImportFinalizationTasklet`, `RegionImportOrchestrationTasklet`, `RegionImportJobExecutionListener`, `RateLimitedJobLauncher`. Keep `FeedImportSyncService` — it becomes the sole execution backend. |

### What Gets Removed from Spring Boot

After full migration, these classes are deleted:

| Class | Reason |
|---|---|
| `RegionImportJobConfig` | Orchestration moves to Airflow DAG |
| `RegionImportBatchConfig` | ForkJoinPool executor replaced by Airflow workers |
| `FeedPartitioner` | Dynamic task mapping replaces partitioning |
| `RegionImportInitializationTasklet` | `create_region_import` Airflow task replaces this |
| `FeedImportWorkerTasklet` | `import_feed` Airflow task replaces this |
| `RegionImportFinalizationTasklet` | `finalize_region_import` Airflow task replaces this |
| `RegionImportOrchestrationTasklet` | Already deprecated |
| `RegionImportJobExecutionListener` | Airflow callbacks replace event publishing |
| `RateLimitedJobLauncher` | Airflow's `max_active_runs` + pool slots replace rate limiting |

### What Stays in Spring Boot

| Class | Reason |
|---|---|
| `FeedImportSyncService` | Core import logic; called by Airflow via HTTP |
| `AgencyImportService` | Processing logic |
| `RouteImportService` | Processing logic |
| `RouteVariantImportService` | Processing logic |
| `StopSpacingImportService` | Processing logic |
| `FrequencyImportService` | Processing logic |
| `GTFSFeedDownloader` | GTFS download and parsing |
| `RegionImport` (domain model) | Status tracking persisted to database |
| `FeedImport` (domain model) | Status tracking persisted to database |
| `RegionImportRepository` | Database access for tracking |
| `FeedImportRepository` | Database access for tracking |
| `RegionController` (query endpoints) | Status query endpoints remain |

## Consequences

### Positive

1. **Native scheduling** — Cron-based scheduling per region DAG with timezone
   support. The `SCHEDULED` trigger type gets a real implementation.

2. **Built-in retry with backoff** — `retries=3`,
   `retry_exponential_backoff=True`, `max_retry_delay=timedelta(minutes=30)` are
   first-class Airflow features. Fulfills the constitutional requirement.

3. **Operational visibility** — Airflow web UI provides DAG run history, task
   duration trends, Gantt charts, log streaming, and failure alerting out of the
   box.

4. **Horizontal scaling** — With `KubernetesExecutor`, each feed import runs in
   its own pod. Parallelism is limited by Kubernetes cluster capacity, not JVM
   thread count.

5. **Decoupled from application lifecycle** — Airflow scheduler and workers run
   independently. A Spring Boot deployment does not cancel in-progress imports.
   Airflow detects the backend returning errors and retries.

6. **Simpler Spring Boot codebase** — 8 orchestration classes are replaced by 2
   Python DAG files (~150 lines total). The Spring Boot application focuses on
   what it does well: domain logic and data access.

7. **Cross-region deduplication** — Airflow's `max_active_runs=1` per region DAG
   plus pool-level concurrency limits prevent duplicate imports. Shared feeds
   across regions can be deduplicated using Airflow's `ExternalTaskSensor` or a
   shared task pool.

8. **Alerting** — Airflow supports `on_failure_callback`, email, Slack, and
   PagerDuty integrations for import failures.

### Negative

1. **New infrastructure component** — Airflow requires its own deployment
   (webserver, scheduler, worker pods, metadata database). This increases
   operational complexity and infrastructure cost.

2. **Two runtimes** — The system now spans Kotlin/JVM and Python. DAGs are
   written in Python while business logic stays in Kotlin. Developers need
   familiarity with both ecosystems.

3. **Network dependency** — Airflow tasks call the backend over HTTP. Network
   partitions between Airflow and the backend can cause task failures (mitigated
   by retries). Latency is higher than in-process calls.

4. **Airflow metadata database** — Requires an additional PostgreSQL database
   (or schema). Needs its own backups and maintenance.

5. **DAG deployment** — DAG files must be synced to the Airflow scheduler and
   workers. This requires either a shared filesystem (NFS, PVC), Git-sync
   sidecar, or packaging DAGs into the Airflow container image.

6. **Loss of transactional guarantees** — Spring Batch steps run within Spring
   transactions. Airflow HTTP calls are not transactional. If the backend
   completes processing but Airflow fails to record the task success, the task
   may be retried (must ensure idempotency).

### Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Airflow becomes a single point of failure | Deploy Airflow HA (multiple schedulers, webserver replicas) |
| Backend endpoint changes break DAGs | Version internal API endpoints (`/api/internal/v1/...`); contract tests |
| Feed import idempotency | `FeedImportSyncService` already checks for active imports and handles stale imports; extend to be fully idempotent |
| DAG configuration drift from database | Load region list from backend API or Airflow Variables; avoid hardcoding |
| Increased latency from HTTP overhead | Acceptable for batch imports (minutes per feed vs milliseconds of HTTP overhead) |

## Alternatives Considered

### 1. Add @Scheduled Triggers to Spring Batch (Incremental Fix)

Add `@Scheduled` methods to `RegionImportService` with cron expressions per
region.

**Pros**: No new infrastructure. Minimal code change.
**Cons**: Does not address retry with backoff, visibility, horizontal scaling,
or lifecycle coupling. Scheduling configuration is buried in code or properties
files with no UI.

**Decision**: Rejected — addresses only one of the seven pain points.

### 2. Spring Cloud Data Flow

Use Spring Cloud Data Flow to orchestrate Spring Batch jobs with a web UI and
scheduling.

**Pros**: Stays in the Spring ecosystem. Provides a dashboard. Supports
scheduling.
**Cons**: Less mature ecosystem than Airflow. Smaller community. Heavier
operational footprint than standalone Airflow. Limited dynamic task mapping
support.

**Decision**: Rejected — Airflow has a larger ecosystem, better documentation,
and more operational tooling.

### 3. Temporal.io

Use Temporal for durable workflow execution with automatic retries and
visibility.

**Pros**: Strong retry and durability guarantees. SDK available for Kotlin/JVM.
No separate language runtime needed. Built-in visibility UI.
**Cons**: Requires Temporal server deployment (similar infra overhead to
Airflow). Smaller community than Airflow. Less mature scheduling support (relies
on cron schedules via workflow starters). Team has no existing Temporal
experience.

**Decision**: Viable alternative worth revisiting if Airflow proves too
heavyweight. Temporal's JVM SDK would avoid the two-runtime concern. However,
Airflow's maturity for data pipeline orchestration and its scheduling-first
design make it the stronger choice for this use case.

### 4. Prefect

Modern Python workflow orchestration with a managed cloud option.

**Pros**: Simpler than Airflow. Pythonic API. Managed cloud option reduces
operational burden.
**Cons**: Smaller community than Airflow. Vendor lock-in risk with managed
cloud. Self-hosted option less battle-tested than Airflow.

**Decision**: Rejected — Airflow's maturity and community support outweigh
Prefect's simplicity advantages.

## References

- [Apache Airflow Documentation](https://airflow.apache.org/docs/)
- [Airflow on Kubernetes](https://airflow.apache.org/docs/apache-airflow/stable/administration-and-deployment/kubernetes.html)
- [KubernetesExecutor](https://airflow.apache.org/docs/apache-airflow/stable/core-concepts/executor/kubernetes.html)
- ADR-0003: Spring Batch for Feed Discovery Processing (predecessor decision)
- ADR-0009: Spring Modulith Module Boundaries
- ADR-0010: API-Driven Module Communication

## Decision Date
2026-02-07

## Decision Makers
- Development Team
- Technical Lead

## Review Date
2026-05-07 (3 months after proposal)

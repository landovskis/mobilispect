# Remove Spring Batch — Design

**Date:** 2026-02-22
**Branch:** `refactor/remove-spring-batch`
**Status:** Approved

## Context

Apache Airflow DAGs (`feed_import.py`, `region_import.py`) now own all batch
orchestration and business logic via the Airflow Python pipeline
(`airflow/pipeline/`). Spring Boot is purely an API/UI layer. Spring Batch is
dead weight.

## Scope

### Remove

- All Spring Batch orchestration code (job configs, tasklets, readers, writers,
  processors, listeners, batch models, batch services) across 5 backend modules
- Service classes that are dead code (only ever called from tasklets)
- Schedulers, CLI commands, and REST endpoints that triggered batch jobs
- `@EnableBatchProcessing` from `MobilispectApplication.kt`
- Spring Batch Gradle dependencies (`spring.boot.batch`, `spring.batch.test`)
- Flyway migration to drop all `BATCH_*` metadata tables

### Keep

Any service or repository also called from REST API endpoints or other
non-batch code paths.

## Approach: Module-by-Module Removal

Removal proceeds from simplest to most complex module. Unit tests run after
each module to catch regressions before proceeding.

### Module 1 — `agency`

- `batch/import/AgencyImportTasklet.kt` + test
- `AgencyImportService.kt` + test

### Module 2 — `route`

- `batch/import/RouteImportTasklet.kt` + test
- `batch/variant/RouteVariantImportTasklet.kt` + `RouteVariantBatchModels.kt` + tests
- `batch/spacing/StopSpacingImportTasklet.kt` + `StopSpacingBatchModels.kt` + tests
- `batch/classification/RouteClassificationTasklet.kt` + test
- `batch/frequency/FrequencyImportTasklet.kt` + `FrequencyBatchModels.kt` + tests
- Dead-code services: RouteImportService, RouteVariantImportService,
  StopSpacingImportService, FrequencyImportService + their tests

### Module 3 — `feed`

- `batch/discovery/` — all 6 files + tests
- `batch/import/` — all 7 files + tests
- Dead-code services: FeedImportService, FeedImportSyncService (if not used by
  API) + their tests

### Module 4 — `region`

- `batch/` — all 8 files + tests
- Dead-code region import services + their tests

### Module 5 — `config` + application + callers

- `config/BatchDataSourceLogging.kt`
- Remove `@EnableBatchProcessing` from `MobilispectApplication.kt`
- Remove schedulers, CLI commands, REST endpoints that triggered batch jobs
  (exact files confirmed during implementation)

### Module 6 — Dependencies + schema

- Remove `spring.boot.batch` and `spring.batch.test` from `build.gradle.kts`
- New Flyway migration dropping BATCH_* tables in dependency order:
  `BATCH_STEP_EXECUTION_CONTEXT`, `BATCH_JOB_EXECUTION_CONTEXT`,
  `BATCH_STEP_EXECUTION`, `BATCH_JOB_EXECUTION_PARAMS`,
  `BATCH_JOB_EXECUTION`, `BATCH_JOB_INSTANCE`

## Testing Strategy

After each module:

```bash
./backend/gradlew -p backend test -x integrationTest
```

After all modules:

```bash
./backend/gradlew ktlintFormat
./backend/gradlew detekt
./scripts/validate-coverage.sh backend
pre-commit run --all-files
```

Coverage is expected to remain stable or improve — removing dead implementation
and dead tests together is neutral to the ratio. If coverage drops below 80%,
add tests on remaining code before committing.

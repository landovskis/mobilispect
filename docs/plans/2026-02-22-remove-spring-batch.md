# Remove Spring Batch Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove all Spring Batch orchestration code, schedulers, services, and dependencies replaced by the
Airflow Python pipeline, leaving Spring Boot as a pure API/UI layer.

**Architecture:** Work top-down: first remove the callers that trigger batch jobs (schedulers, sync service,
REST endpoints), then delete the batch infrastructure module-by-module. Each step compiles and tests pass
before proceeding to the next. Finish by removing dependencies and adding a schema migration to drop
BATCH_* tables.

**Tech Stack:** Kotlin/Spring Boot, Gradle (libs.versions.toml), Flyway migrations, JUnit 5

---

## Verification command (run after every task)

```bash
./backend/gradlew -p backend test -x integrationTest
```

Expected: BUILD SUCCESSFUL

---

## Task 0: Remove feed schedulers

These schedulers trigger Spring Batch jobs on a cron schedule. Airflow DAGs own scheduling now.

**Files:**

- Delete: `backend/src/main/kotlin/com/mobilispect/backend/feed/service/FeedUpdateScheduler.kt`
- Delete: `backend/src/main/kotlin/com/mobilispect/backend/feed/service/FeedDiscoveryScheduler.kt`
- Delete any test files for these (grep: `find backend/src/test -name "FeedUpdateSchedulerTest*" -o -name "FeedDiscoverySchedulerTest*"`)

**Step 1: Delete the scheduler files**

```bash
rm backend/src/main/kotlin/com/mobilispect/backend/feed/service/FeedUpdateScheduler.kt
rm backend/src/main/kotlin/com/mobilispect/backend/feed/service/FeedDiscoveryScheduler.kt
```

**Step 2: Delete their test files if they exist**

```bash
find backend/src/test -name "FeedUpdateSchedulerTest*" -delete
find backend/src/test -name "FeedDiscoverySchedulerTest*" -delete
```

**Step 3: Verify no remaining references**

```bash
grep -r "FeedUpdateScheduler\|FeedDiscoveryScheduler" backend/src --include="*.kt"
```

Expected: no output (zero references)

**Step 4: Run tests**

```bash
./backend/gradlew -p backend test -x integrationTest
```

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove feed schedulers replaced by Airflow DAGs"
```

---

## Task 1: Remove Spring Batch job launchers from the feed module

`FeedImportService` launches the Spring Batch `feedImportJob`. `RateLimitedJobLauncher` wraps Spring Batch's
`JobLauncher`. Both are dead.

**Files:**

- Delete: `backend/src/main/kotlin/com/mobilispect/backend/feed/service/FeedImportService.kt`
- Delete: `backend/src/main/kotlin/com/mobilispect/backend/feed/service/RateLimitedJobLauncher.kt`
- Delete any test files: `find backend/src/test -name "FeedImportServiceTest*" -o -name "RateLimitedJobLauncherTest*"`

**Step 1: Delete the files**

```bash
rm backend/src/main/kotlin/com/mobilispect/backend/feed/service/FeedImportService.kt
rm backend/src/main/kotlin/com/mobilispect/backend/feed/service/RateLimitedJobLauncher.kt
```

**Step 2: Delete their tests if they exist**

```bash
find backend/src/test -name "FeedImportServiceTest*" -delete
find backend/src/test -name "RateLimitedJobLauncherTest*" -delete
```

**Step 3: Verify no remaining references**

```bash
grep -r "FeedImportService\|RateLimitedJobLauncher" backend/src --include="*.kt" \
  | grep -v "FeedImportSyncService"
```

Expected: no output (FeedImportSyncService is a different class, handled in Task 2)

**Step 4: Run tests**

```bash
./backend/gradlew -p backend test -x integrationTest
```

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove Spring Batch feed job launcher"
```

---

## Task 2: Remove the region import() endpoint and region Spring Batch job

`RegionImportService.import()` launches the Spring Batch `regionImportJob`. The REST endpoint in
`RegionController` calls it. Airflow now handles region import orchestration. The query methods on
`RegionImportService` (`getRegionImport`, `getActiveImportForRegion`, `getActiveRegionImports`,
`failRegionImport`) must be kept — the REST API uses them to expose import status written by Airflow.

**Files:**

- Modify: `backend/src/main/kotlin/com/mobilispect/backend/region/service/RegionImportService.kt`
- Modify: `backend/src/main/kotlin/com/mobilispect/backend/region/controller/RegionController.kt`
- Modify tests: `backend/src/test/kotlin/com/mobilispect/backend/region/service/RegionImportServiceTest.kt`
- Modify tests: `backend/src/test/kotlin/com/mobilispect/backend/feed/controller/RegionControllerTest.kt`

**Step 1: Find the import() endpoint in RegionController**

```bash
grep -n "import\|regionImportService\." \
  backend/src/main/kotlin/com/mobilispect/backend/region/controller/RegionController.kt
```

Remove the REST handler method that calls `regionImportService.import(...)` and its
`@PostMapping`/`@RequestMapping` annotation. Keep any GET handler methods that call `getRegionImport`,
`getActiveImportForRegion`, or `getActiveRegionImports`.

**Step 2: Remove Spring Batch wiring from RegionImportService**

Open `backend/src/main/kotlin/com/mobilispect/backend/region/service/RegionImportService.kt`.

Remove:

- The constructor parameter for the Spring Batch `Job` (regionImportJob) and `JobLauncher`/`JobOperator`
- Any `import org.springframework.batch.*` imports
- The `import(regionId, triggerType)` method entirely
- Any `TransactionSynchronizationManager` usage associated with job launching

Keep: `failRegionImport`, `getRegionImport`, `getActiveImportForRegion`, `getActiveRegionImports` and their dependencies.

**Step 3: Update RegionImportServiceTest**

Remove test cases that test the `import()` method. Keep tests for query methods.

**Step 4: Update RegionControllerTest**

Remove test cases for the deleted import endpoint. Update the mock setup so `regionImportService` no longer
needs to be mocked with a `Job` bean.

**Step 5: Run tests**

```bash
./backend/gradlew -p backend test -x integrationTest
```

**Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove Spring Batch region import job launcher"
```

---

## Task 3: Remove FeedImportSyncService

`FeedImportSyncService` orchestrates feed imports synchronously — it calls all the import services
(Agency, Route, Variant, Spacing, Frequency). It is called from `FeedApiImpl`. Once we remove it,
those downstream import services become dead code (handled in Tasks 4–6).

**Files:**

- Modify: `backend/src/main/kotlin/com/mobilispect/backend/feed/internal/FeedApiImpl.kt`
- Delete: `backend/src/main/kotlin/com/mobilispect/backend/feed/service/FeedImportSyncService.kt`
- Delete any test: `find backend/src/test -name "FeedImportSyncServiceTest*"`

**Step 1: Find FeedImportSyncService usage in FeedApiImpl**

```bash
grep -n "feedImportSyncService\|FeedImportSyncService\|importSync" \
  backend/src/main/kotlin/com/mobilispect/backend/feed/internal/FeedApiImpl.kt
```

Remove the injected `feedImportSyncService` constructor parameter and any method calls to it. If the method
that called it is now empty or only returns a stub, remove it entirely or replace it with a no-op/exception.

**Step 2: Delete FeedImportSyncService**

```bash
rm backend/src/main/kotlin/com/mobilispect/backend/feed/service/FeedImportSyncService.kt
find backend/src/test -name "FeedImportSyncServiceTest*" -delete
```

**Step 3: Verify no remaining references**

```bash
grep -r "FeedImportSyncService\|importSync" backend/src --include="*.kt"
```

Expected: no output

**Step 4: Run tests**

```bash
./backend/gradlew -p backend test -x integrationTest
```

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove FeedImportSyncService replaced by Airflow pipeline"
```

---

## Task 4: Remove agency batch code

The `AgencyImportTasklet` and `AgencyImportService` are now dead code (their only caller,
`FeedImportSyncService`, was removed in Task 3).

**Files:**

- Delete: `backend/src/main/kotlin/com/mobilispect/backend/agency/batch/import/AgencyImportTasklet.kt`
- Delete: `backend/src/main/kotlin/com/mobilispect/backend/agency/batch/import/AgencyImportService.kt`
- Delete any tests: `find backend/src/test -path "*/agency/batch/*"`

**Step 1: Verify these classes have no remaining callers**

```bash
grep -r "AgencyImportTasklet\|AgencyImportService" backend/src --include="*.kt"
```

Expected: only the class definitions themselves (no external callers).

**Step 2: Delete the files**

```bash
rm backend/src/main/kotlin/com/mobilispect/backend/agency/batch/import/AgencyImportTasklet.kt
rm backend/src/main/kotlin/com/mobilispect/backend/agency/batch/import/AgencyImportService.kt
find backend/src/test -path "*/agency/batch/*" -name "*.kt" -delete
```

**Step 3: Remove the now-empty batch directory**

```bash
rmdir backend/src/main/kotlin/com/mobilispect/backend/agency/batch/import 2>/dev/null || true
rmdir backend/src/main/kotlin/com/mobilispect/backend/agency/batch 2>/dev/null || true
```

**Step 4: Run tests**

```bash
./backend/gradlew -p backend test -x integrationTest
```

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove agency batch import code"
```

---

## Task 5: Remove route batch code

All route batch files and their underlying services are dead code.

**Files to delete:**

- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/import/RouteImportTasklet.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/import/RouteImportService.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/variant/RouteVariantImportTasklet.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/variant/RouteVariantImportService.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/variant/RouteVariantBatchModels.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/spacing/StopSpacingImportTasklet.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/spacing/StopSpacingImportService.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/spacing/StopSpacingBatchModels.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/frequency/FrequencyImportTasklet.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/frequency/FrequencyImportService.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/frequency/FrequencyBatchModels.kt`
- `backend/src/main/kotlin/com/mobilispect/backend/route/batch/classification/RouteClassificationTasklet.kt`

**Step 1: Verify no remaining callers**

```bash
grep -r "RouteImportTasklet\|RouteImportService\|RouteVariantImportTasklet\|RouteVariantImportService\|StopSpacingImportTasklet\|StopSpacingImportService\|FrequencyImportTasklet\|FrequencyImportService\|RouteClassificationTasklet" \
  backend/src --include="*.kt"
```

Expected: only the class definitions themselves.

**Step 2: Delete all route batch files**

```bash
find backend/src -path "*/route/batch/*" -name "*.kt" -delete
find backend/src/test -path "*/route/batch/*" -name "*.kt" -delete
```

**Step 3: Remove empty directories**

```bash
find backend/src -path "*/route/batch" -type d -empty -delete 2>/dev/null || true
find backend/src -path "*/route/batch/*" -type d -empty -delete 2>/dev/null || true
```

**Step 4: Run tests**

```bash
./backend/gradlew -p backend test -x integrationTest
```

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove route batch import code"
```

---

## Task 6: Remove feed batch code

All feed batch files (discovery pipeline and import pipeline) are dead code.

**Files to delete (all files under these directories):**

- `backend/src/main/kotlin/com/mobilispect/backend/feed/batch/discovery/` (9 files:
  FeedDiscoveryBatchService, FeedDiscoveryJobConfig, FeedDiscoveryProcessor, FeedDiscoveryReader,
  FeedDiscoveryWriter, FeedDiscoveryResult, FeedMetadata, RegionalFeedGroup, TransitLandAPIKey)
- `backend/src/main/kotlin/com/mobilispect/backend/feed/batch/import/` (7 files:
  FeedImportJobConfig, FeedImportTasklet, FeedImportJobExecutionListener,
  FeedImportStepExecutionListener, FeedImportWriter, GTFSFeedReader, RouteVariantFeedDataTasklet)

**Step 1: Verify no remaining callers**

```bash
grep -r "FeedDiscoveryBatchService\|FeedDiscoveryJobConfig\|FeedImportJobConfig\|GTFSFeedReader\|FeedImportWriter\|RouteVariantFeedDataTasklet" \
  backend/src --include="*.kt"
```

Expected: only the class definitions themselves.

**Step 2: Delete all feed batch files**

```bash
find backend/src -path "*/feed/batch/*" -name "*.kt" -delete
find backend/src/test -path "*/feed/batch/*" -name "*.kt" -delete
```

**Step 3: Remove empty directories**

```bash
find backend/src -path "*/feed/batch" -type d | xargs rm -rf 2>/dev/null || true
```

**Step 4: Run tests**

```bash
./backend/gradlew -p backend test -x integrationTest
```

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove feed batch discovery and import code"
```

---

## Task 7: Remove region batch code

All region batch orchestration files are dead code.

**Files to delete (all files under):**

- `backend/src/main/kotlin/com/mobilispect/backend/region/batch/` (8 files:
  RegionImportJobConfig, RegionImportBatchConfig, FeedPartitioner, FeedImportWorkerTasklet,
  RegionImportInitializationTasklet, RegionImportFinalizationTasklet,
  RegionImportOrchestrationTasklet, RegionImportJobExecutionListener)

**Step 1: Verify no remaining callers**

```bash
grep -r "RegionImportJobConfig\|RegionImportBatchConfig\|FeedPartitioner\|FeedImportWorkerTasklet\|RegionImportInitializationTasklet\|RegionImportFinalizationTasklet\|RegionImportOrchestrationTasklet\|RegionImportJobExecutionListener" \
  backend/src --include="*.kt"
```

Expected: only the class definitions themselves.

**Step 2: Delete all region batch files**

```bash
find backend/src -path "*/region/batch/*" -name "*.kt" -delete
```

Also delete the one known test:

```bash
rm -f backend/src/test/kotlin/com/mobilispect/backend/region/batch/RegionImportJobConfigTest.kt
```

**Step 3: Remove empty directories**

```bash
find backend/src -path "*/region/batch" -type d | xargs rm -rf 2>/dev/null || true
```

**Step 4: Run tests**

```bash
./backend/gradlew -p backend test -x integrationTest
```

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove region batch orchestration code"
```

---

## Task 8: Remove BatchDataSourceLogging and @EnableBatchProcessing

**Files:**

- Delete: `backend/src/main/kotlin/com/mobilispect/backend/config/BatchDataSourceLogging.kt`
- Modify: `backend/src/main/kotlin/com/mobilispect/backend/MobilispectApplication.kt`

**Step 1: Delete BatchDataSourceLogging**

```bash
rm backend/src/main/kotlin/com/mobilispect/backend/config/BatchDataSourceLogging.kt
find backend/src/test -name "BatchDataSourceLoggingTest*" -delete
```

**Step 2: Remove @EnableBatchProcessing from MobilispectApplication**

Open `backend/src/main/kotlin/com/mobilispect/backend/MobilispectApplication.kt`.

Remove the `@EnableBatchProcessing` annotation and its import line:

```kotlin
// Remove this import:
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing

// Remove this annotation from the class:
@EnableBatchProcessing
```

**Step 3: Verify no remaining Spring Batch references in main source**

```bash
grep -r "springframework.batch\|EnableBatchProcessing\|JobLauncher\|JobOperator\|JobBuilder\|StepBuilder\|ItemReader\|ItemWriter\|ItemProcessor\|Tasklet\|Partitioner\|JobRepository" \
  backend/src/main --include="*.kt"
```

Expected: no output (all Spring Batch references gone from main source).

**Step 4: Run tests**

```bash
./backend/gradlew -p backend test -x integrationTest
```

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove @EnableBatchProcessing and BatchDataSourceLogging"
```

---

## Task 9: Remove Spring Batch Gradle dependencies

**Files:**

- Modify: `backend/build.gradle.kts`
- Modify: `backend/gradle/libs.versions.toml`

**Step 1: Remove from build.gradle.kts**

Open `backend/build.gradle.kts`. Remove these two lines from the `dependencies` block:

```kotlin
implementation(libs.spring.boot.batch)
testImplementation(libs.spring.batch.test)
```

**Step 2: Remove from libs.versions.toml**

Open `backend/gradle/libs.versions.toml`. Remove these two lines from the `[libraries]` section:

```toml
spring-boot-batch = { module = "org.springframework.boot:spring-boot-starter-batch" }
spring-batch-test = { module = "org.springframework.batch:spring-batch-test" }
```

**Step 3: Sync dependencies**

```bash
./backend/gradlew -p backend dependencies --configuration compileClasspath | grep -i batch
```

Expected: no spring-batch entries in the output.

**Step 4: Run tests**

```bash
./backend/gradlew -p backend test -x integrationTest
```

**Step 5: Run static analysis**

```bash
./backend/gradlew -p backend ktlintFormat
./backend/gradlew -p backend detekt
```

Fix any violations reported by detekt.

**Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove Spring Batch Gradle dependencies"
```

---

## Task 10: Add DB migration to drop BATCH_* tables

Create a Flyway migration to drop the Spring Batch metadata tables (created by V017 and V019) and their sequences.

**Files:**

- Create: `backend/src/main/resources/db/migration/V062__drop_spring_batch_tables.sql`

**Step 1: Create the migration file**

```sql
-- V062__drop_spring_batch_tables.sql
-- Remove Spring Batch metadata tables and sequences (created by V017 and V019).
-- Spring Batch has been replaced by Apache Airflow.

DROP TABLE IF EXISTS BATCH_STEP_EXECUTION_CONTEXT;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION_CONTEXT;
DROP TABLE IF EXISTS BATCH_STEP_EXECUTION;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION_PARAMS;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION;
DROP TABLE IF EXISTS BATCH_JOB_INSTANCE;

DROP SEQUENCE IF EXISTS BATCH_STEP_EXECUTION_SEQ;
DROP SEQUENCE IF EXISTS BATCH_JOB_EXECUTION_SEQ;
DROP SEQUENCE IF EXISTS BATCH_JOB_SEQ;
```

**Step 2: Run integration tests to validate migration**

```bash
./backend/gradlew -p backend integrationTest --tests "*MigrationTest*"
```

If no migration-specific test exists, run the full integration suite:

```bash
./backend/gradlew -p backend integrationTest
```

Expected: BUILD SUCCESSFUL (Flyway runs V062 cleanly against test DB)

**Step 3: Validate coverage**

```bash
./scripts/validate-coverage.sh backend
```

Expected: ≥80% coverage. If below, add tests on remaining code.

**Step 4: Run pre-commit hooks**

```bash
pre-commit run --all-files
```

All hooks must pass.

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: drop Spring Batch metadata tables via V062 migration"
```

---

## Final verification

```bash
# No Spring Batch references anywhere in main source
grep -r "springframework.batch" backend/src/main --include="*.kt"
# Expected: no output

# No Spring Batch in dependencies
grep -i "spring.*batch\|batch.*spring" backend/gradle/libs.versions.toml backend/build.gradle.kts
# Expected: no output

# All unit tests pass
./backend/gradlew -p backend test -x integrationTest

# Full pre-commit validation
pre-commit run --all-files
```

# Tasks: Average Stop Spacing Tracking

**Input**: Design documents from `/specs/002-stop-spacing-tracking/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included per Constitution Principle II (Test-Driven Development - NON-NEGOTIABLE)

**Organization**: Tasks grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: `backend/src/main/kotlin/com/mobilispect/backend/`
- **Backend Tests**: `backend/src/test/kotlin/com/mobilispect/backend/`
- **Frontend**: `frontend/web/src/app/`
- **Migrations**: `backend/src/main/resources/db/migration/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, dependencies, and module structure

- [ ] T001 Add GeographicLib dependency to backend/gradle/libs.versions.toml (`geographiclib = { module = "net.sf.geographiclib:geographiclib-java", version = "2.3" }`)
- [ ] T002 Add GeographicLib to backend/build.gradle.kts (`implementation(libs.geographiclib)`)
- [ ] T003 Create ADR for geodesic library choice in docs/adr/NNNN-geodesic-distance-library.md
- [ ] T004 [P] Create stopspacing module package structure at backend/src/main/kotlin/com/mobilispect/backend/stopspacing/
- [ ] T005 [P] Create StopSpacingModule.kt module marker with @ApplicationModule annotation in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/StopSpacingModule.kt
- [ ] T006 [P] Create Angular stop-spacing feature module structure at frontend/web/src/app/stop-spacing/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Database Schema

- [ ] T007 Create database migration V021__add_stop_spacing_statistics.sql in backend/src/main/resources/db/migration/
- [ ] T008 Run migration and verify tables created (`./gradlew flywayMigrate`)

### Value Classes & Enums (Shared across all stories)

- [ ] T009 [P] Create ServiceType enum in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/model/ServiceType.kt
- [ ] T010 [P] Create Distance value class in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/model/Distance.kt
- [ ] T011 [P] Create RouteVariantSpacing data class in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/model/RouteVariantSpacing.kt

### Core Service Infrastructure

- [ ] T012 Create GeodesicDistanceCalculator service in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/service/GeodesicDistanceCalculator.kt
- [ ] T013 Create GeodesicDistanceCalculatorTest in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/service/GeodesicDistanceCalculatorTest.kt

### DTOs (Shared across endpoints)

- [ ] T014 [P] Create DistanceValueDTO in backend/src/main/kotlin/com/mobilispect/backend/api/dto/stopspacing/DistanceValueDTO.kt
- [ ] T015 [P] Create ErrorResponse DTO (if not exists) for stop spacing errors

### Frontend Shared Components

- [ ] T016 [P] Create stop-spacing.model.ts TypeScript interfaces in frontend/web/src/app/stop-spacing/models/stop-spacing.model.ts
- [ ] T017 [P] Create service-type.model.ts TypeScript enum in frontend/web/src/app/stop-spacing/models/service-type.model.ts
- [ ] T018 [P] Create unit-toggle.component.ts for km/mi switching in frontend/web/src/app/shared/components/unit-toggle.component.ts

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - View Route Stop Spacing Statistics (Priority: P1) 🎯 MVP

**Goal**: Display stop spacing statistics (avg, min, max, std dev) for individual routes with unit conversion

**Independent Test**: Select any route and verify statistics are displayed accurately in user's preferred unit

### Tests for User Story 1

> **TDD: Write tests FIRST, ensure they FAIL before implementation**

- [ ] T019 [P] [US1] Create StopSpacingCalculationServiceTest in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/service/StopSpacingCalculationServiceTest.kt
- [ ] T020 [P] [US1] Create StopSpacingRepositoryTest in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/repository/StopSpacingRepositoryTest.kt
- [ ] T021 [P] [US1] Create RouteStopSpacingControllerTest in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/controller/RouteStopSpacingControllerTest.kt

### Backend Implementation for User Story 1

- [ ] T022 [P] [US1] Create StopSpacingStatistics domain model in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/model/StopSpacingStatistics.kt
- [ ] T023 [P] [US1] Create StopSpacingStatisticsEntity JPA entity in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/model/StopSpacingStatisticsEntity.kt
- [ ] T024 [US1] Create StopSpacingRepository interface in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/repository/StopSpacingRepository.kt
- [ ] T025 [US1] Create StopSpacingCalculationService in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/service/StopSpacingCalculationService.kt
- [ ] T026 [US1] Implement stop sequence extraction from schedule module (via public API) in StopSpacingCalculationService
- [ ] T027 [US1] Implement variant detection and weighted average calculation in StopSpacingCalculationService
- [ ] T028 [P] [US1] Create StopSpacingDTO response in backend/src/main/kotlin/com/mobilispect/backend/api/dto/stopspacing/StopSpacingDTO.kt
- [ ] T029 [US1] Create RouteStopSpacingController GET /routes/{routeId}/stop-spacing in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/controller/RouteStopSpacingController.kt
- [ ] T030 [US1] Create FeedImportCompletedListener to trigger calculation on feed import in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/event/FeedImportCompletedListener.kt
- [ ] T031a [US1] **RESEARCH**: Check if FeedImportCompletedEvent exists in feed module (`grep -r "FeedImportCompletedEvent" backend/`)
- [ ] T031b [US1] If not exists: Create FeedImportCompletedEvent in backend/src/main/kotlin/com/mobilispect/backend/feed/event/FeedImportCompletedEvent.kt
- [ ] T031c [US1] If exists: Verify event contains feedId and is published at end of feed import process

### Frontend Implementation for User Story 1

- [ ] T032 [P] [US1] Create stop-spacing.service.ts HTTP client in frontend/web/src/app/stop-spacing/services/stop-spacing.service.ts
- [ ] T033 [P] [US1] Create route-spacing-card.component.ts display component in frontend/web/src/app/stop-spacing/components/route-spacing-card.component.ts
- [ ] T034 [US1] Create route-details.component.ts page in frontend/web/src/app/stop-spacing/pages/route-details.component.ts
- [ ] T035 [US1] Add routing for /routes/:routeId/stop-spacing in frontend/web/src/app/stop-spacing/stop-spacing-routing.module.ts
- [ ] T036 [US1] Integrate unit-toggle.component with route-spacing-card for km/mi switching

### Integration for User Story 1

- [ ] T037 [US1] Create StopSpacingModuleIntegrationTest verifying module boundaries in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/integration/StopSpacingModuleIntegrationTest.kt
- [ ] T038 [US1] Verify Spring Modulith boundaries pass (`./gradlew verifyModulith`)

**Checkpoint**: User Story 1 complete - route statistics viewable with unit conversion

---

## Phase 4: User Story 2 - Route Service Type Classification (Priority: P2)

**Goal**: Classify routes as local/rapid/express based on configurable thresholds

**Independent Test**: View routes and verify classification labels match stop spacing characteristics

### Tests for User Story 2

- [ ] T039 [P] [US2] Create ServiceTypeClassifierTest in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/service/ServiceTypeClassifierTest.kt
- [ ] T040 [P] [US2] Create ClassificationThresholdRepositoryTest in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/repository/ClassificationThresholdRepositoryTest.kt
- [ ] T041 [P] [US2] Create ThresholdsControllerTest in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/controller/ThresholdsControllerTest.kt

### Backend Implementation for User Story 2

- [ ] T042 [P] [US2] Create ClassificationThreshold domain model in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/model/ClassificationThreshold.kt
- [ ] T043 [P] [US2] Create ClassificationThresholdEntity JPA entity in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/model/ClassificationThresholdEntity.kt
- [ ] T044 [US2] Create ClassificationThresholdRepository in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/repository/ClassificationThresholdRepository.kt
- [ ] T045 [US2] Create ServiceTypeClassifier service in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/service/ServiceTypeClassifier.kt
- [ ] T046 [US2] Implement threshold boundary logic (≥ boundary = higher category) in ServiceTypeClassifier
- [ ] T047 [US2] Integrate ServiceTypeClassifier into StopSpacingCalculationService
- [ ] T048 [P] [US2] Create ThresholdsDTO response in backend/src/main/kotlin/com/mobilispect/backend/api/dto/stopspacing/ThresholdsDTO.kt
- [ ] T049 [US2] Create ThresholdsController GET /regions/{regionId}/thresholds in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/controller/ThresholdsController.kt
- [ ] T050 [US2] Create ThresholdsController GET /thresholds/default endpoint
- [ ] T051 [US2] Create ThresholdsController PUT /regions/{regionId}/thresholds for admin updates

### Frontend Implementation for User Story 2

- [ ] T052 [P] [US2] Create service-type-badge.component.ts in frontend/web/src/app/stop-spacing/components/service-type-badge.component.ts
- [ ] T053 [US2] Add service type badge to route-spacing-card.component.ts
- [ ] T054 [US2] Add threshold display to route details showing classification criteria
- [ ] T055 [US2] Update stop-spacing.service.ts with getThresholds() method

**Checkpoint**: User Story 2 complete - routes display classification with visible thresholds

---

## Phase 5: User Story 3 - Agency-Level Service Type Comparison (Priority: P2)

**Goal**: Compare stop spacing statistics across service types within a single agency

**Independent Test**: Select agency, view aggregated metrics by service type (local/rapid/express) side-by-side

### Tests for User Story 3

- [ ] T056 [P] [US3] Create StopSpacingAggregationServiceTest for agency aggregation in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/service/StopSpacingAggregationServiceTest.kt
- [ ] T057 [P] [US3] Create AgencyComparisonControllerTest in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/controller/AgencyComparisonControllerTest.kt

### Backend Implementation for User Story 3

- [ ] T058 [P] [US3] Create AgencyStopSpacingComparison domain model in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/model/AgencyStopSpacingComparison.kt
- [ ] T059 [P] [US3] Create ServiceTypeAggregation domain model in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/model/ServiceTypeAggregation.kt
- [ ] T060 [US3] Create StopSpacingAggregationService in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/service/StopSpacingAggregationService.kt
- [ ] T061 [US3] Implement agency-level aggregation logic (group routes by service type, calculate aggregates)
- [ ] T062 [US3] Handle edge case: agency with only one service type (show available types only)
- [ ] T063 [P] [US3] Create AgencyComparisonDTO in backend/src/main/kotlin/com/mobilispect/backend/api/dto/stopspacing/AgencyComparisonDTO.kt
- [ ] T064 [US3] Create AgencyComparisonController GET /agencies/{agencyId}/stop-spacing/comparison in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/controller/AgencyComparisonController.kt
- [ ] T065 [US3] Add Redis caching for agency aggregations (6-hour TTL)

### Frontend Implementation for User Story 3

- [ ] T066 [P] [US3] Create agency-comparison-table.component.ts in frontend/web/src/app/stop-spacing/components/agency-comparison-table.component.ts
- [ ] T067 [US3] Create agency-comparison.component.ts page in frontend/web/src/app/stop-spacing/pages/agency-comparison.component.ts
- [ ] T068 [US3] Add routing for /agencies/:agencyId/stop-spacing in stop-spacing-routing.module.ts
- [ ] T069 [US3] Update stop-spacing.service.ts with getAgencyComparison() method

**Checkpoint**: User Story 3 complete - agency comparison view functional

---

## Phase 6: User Story 4 - Regional Cross-Agency Comparison (Priority: P3)

**Goal**: Compare stop spacing across agencies within a metropolitan region with regional averages

**Independent Test**: Select region, view all agencies' metrics by service type with regional averages

### Tests for User Story 4

- [ ] T070 [P] [US4] Create StopSpacingAggregationServiceTest for regional aggregation in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/service/StopSpacingAggregationServiceRegionalTest.kt
- [ ] T071 [P] [US4] Create RegionalComparisonControllerTest in backend/src/test/kotlin/com/mobilispect/backend/stopspacing/controller/RegionalComparisonControllerTest.kt

### Backend Implementation for User Story 4

- [ ] T072 [P] [US4] Create RegionalStopSpacingComparison domain model in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/model/RegionalStopSpacingComparison.kt
- [ ] T073 [US4] Extend StopSpacingAggregationService with regional aggregation logic
- [ ] T074 [US4] Implement regional averages calculation across all agencies
- [ ] T075 [US4] Handle edge case: single-agency region (regional avg = agency values)
- [ ] T076 [P] [US4] Create RegionalComparisonDTO in backend/src/main/kotlin/com/mobilispect/backend/api/dto/stopspacing/RegionalComparisonDTO.kt
- [ ] T077 [US4] Create RegionalComparisonController GET /regions/{regionId}/stop-spacing/comparison in backend/src/main/kotlin/com/mobilispect/backend/stopspacing/controller/RegionalComparisonController.kt
- [ ] T078 [US4] Add Redis caching for regional aggregations (6-hour TTL)
- [ ] T079 [US4] Implement cache invalidation on FeedImportCompletedEvent

### Frontend Implementation for User Story 4

- [ ] T080 [P] [US4] Create regional-comparison-chart.component.ts in frontend/web/src/app/stop-spacing/components/regional-comparison-chart.component.ts
- [ ] T081 [US4] Create regional-comparison.component.ts page in frontend/web/src/app/stop-spacing/pages/regional-comparison.component.ts
- [ ] T082 [US4] Add routing for /regions/:regionId/stop-spacing in stop-spacing-routing.module.ts
- [ ] T083 [US4] Update stop-spacing.service.ts with getRegionalComparison() method

**Checkpoint**: User Story 4 complete - regional comparison view functional

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Quality assurance, performance, observability, accessibility

### Performance & Caching

- [ ] T084 [P] Verify <200ms p95 response time for route statistics endpoint
- [ ] T085 [P] Verify <2s response time for regional comparisons
- [ ] T086 Add cache hit/miss metrics for Grafana dashboard

### Observability

- [ ] T087 [P] Add structured logging for stop spacing calculations
- [ ] T088 [P] Add metrics for calculation duration and route count
- [ ] T089 Create Grafana dashboard for stop spacing module metrics

### Accessibility (WCAG 2.1 AA)

- [ ] T090 [P] Add ARIA labels to route-spacing-card.component
- [ ] T091 [P] Add ARIA labels to agency-comparison-table.component
- [ ] T092 [P] Ensure unit-toggle.component keyboard accessible
- [ ] T093 Run axe accessibility scan on all stop-spacing pages

### End-to-End Testing (Playwright - Constitutional Requirement)

- [ ] T098 [P] Create Playwright E2E test for route stop spacing view in frontend/web/e2e/stop-spacing/route-details.spec.ts
- [ ] T099 [P] Create Playwright E2E test for agency comparison view in frontend/web/e2e/stop-spacing/agency-comparison.spec.ts
- [ ] T100 [P] Create Playwright E2E test for regional comparison view in frontend/web/e2e/stop-spacing/regional-comparison.spec.ts
- [ ] T101 Run Playwright tests across Chrome, Firefox, Safari (`npx playwright test`)

### Architecture Documentation (Constitutional Requirement)

- [ ] T102 [P] Create C4 Container diagram for stop-spacing module in docs/architecture/stop-spacing-container.puml
- [ ] T103 [P] Create C4 Component diagram showing stopspacing module internals in docs/architecture/stop-spacing-component.puml

### Documentation & Cleanup

- [ ] T104 [P] Update API documentation with new endpoints
- [ ] T105 Run quickstart.md validation steps
- [ ] T106 Verify all tests pass (`./gradlew test --tests "*StopSpacing*"`)
- [ ] T107 Verify module boundaries (`./gradlew verifyModulith`)
- [ ] T108 Generate Spring Modulith module documentation (`./gradlew generateModulithDocs`)

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup) ──────────────────────────────────┐
                                                   │
Phase 2 (Foundational) ◄──────────────────────────┘
         │
         │ BLOCKS ALL USER STORIES
         ▼
    ┌────┴────┬────────────┬────────────┐
    │         │            │            │
    ▼         ▼            ▼            ▼
Phase 3   Phase 4      Phase 5      Phase 6
 (US1)     (US2)        (US3)        (US4)
  MVP    ◄─depends─┘      │            │
    │         │           │            │
    │    US2 needs US1    │      US4 needs US3
    │    classification   │      aggregation
    │                     │            │
    └─────────┬───────────┴────────────┘
              │
              ▼
         Phase 7 (Polish)
```

### User Story Dependencies

| Story | Depends On | Can Start After | Independent Test |
|-------|------------|-----------------|------------------|
| US1 (P1) | Phase 2 | T018 complete | Select route, view statistics |
| US2 (P2) | US1 (T025) | US1 checkpoint | View route classification |
| US3 (P2) | Phase 2 | T018 complete | Select agency, view comparison |
| US4 (P3) | US3 (T060) | US3 checkpoint | Select region, view comparison |

### Parallel Opportunities

**Within Phase 2 (Foundational)**:

- T009, T010, T011 (value classes) - parallel
- T014, T015 (DTOs) - parallel
- T016, T017, T018 (frontend) - parallel

**Within User Story 1**:

- T019, T020, T021 (tests) - parallel
- T022, T023 (models) - parallel
- T032, T033 (frontend components) - parallel

**Within User Story 2**:

- T039, T040, T041 (tests) - parallel
- T042, T043 (models) - parallel

**Across Stories (with multiple developers)**:

- US1 and US3 can start in parallel after Phase 2
- US2 depends on US1 calculation service
- US4 depends on US3 aggregation service

---

## Parallel Example: User Story 1

```bash
# Launch all tests for US1 together (TDD - write first):
Task: "T019 [P] [US1] Create StopSpacingCalculationServiceTest"
Task: "T020 [P] [US1] Create StopSpacingRepositoryTest"
Task: "T021 [P] [US1] Create RouteStopSpacingControllerTest"

# Launch all models for US1 together:
Task: "T022 [P] [US1] Create StopSpacingStatistics domain model"
Task: "T023 [P] [US1] Create StopSpacingStatisticsEntity JPA entity"

# Launch frontend components together:
Task: "T032 [P] [US1] Create stop-spacing.service.ts"
Task: "T033 [P] [US1] Create route-spacing-card.component.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T006)
2. Complete Phase 2: Foundational (T007-T018) - **CRITICAL BLOCKER**
3. Complete Phase 3: User Story 1 (T019-T038)
4. **STOP and VALIDATE**: Test route statistics independently
5. Deploy/demo if ready - users can view route stop spacing

### Incremental Delivery

| Increment | Stories | User Value |
|-----------|---------|------------|
| MVP | US1 | View route stop spacing statistics |
| +Classification | US1 + US2 | See route service type (local/rapid/express) |
| +Agency Comparison | US1-US3 | Compare within agency |
| Complete | US1-US4 | Regional benchmarking |

### Parallel Team Strategy

With 2 developers:

1. Both complete Phase 1 + 2 together
2. Once Foundational done:
   - Dev A: US1 → US2 (classification depends on calculation)
   - Dev B: US3 → US4 (regional depends on agency aggregation)
3. Merge and Polish together

---

## Notes

- Constitution mandates TDD - tests MUST fail before implementation
- Spring Modulith verification required before merge
- All value classes must use @JvmInline pattern
- Redis caching TTL: 6 hours for aggregations
- Invalidate cache on FeedImportCompletedEvent
- WCAG 2.1 AA accessibility required for all UI components

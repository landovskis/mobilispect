# Tasks: Transit Route Frequency Analysis

**Input**: Design documents from `/specs/003-transit-route-frequency/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Constitution Principle II requires TDD (Test-Driven Development - NON-NEGOTIABLE)

**Organization**: Tasks grouped by user story for independent implementation and testing.

## ⚠️ Existing Infrastructure to Reuse

The following components **already exist** in the codebase and should be **reused** (not duplicated):

### From `feed` Module

- ✅ **RegionId** value class (`feed.model.ids.RegionId`)
- ✅ **FeedId** value class (`feed.model.ids.FeedId`)
- ✅ **MetropolitanRegion** entity with `metropolitan_regions` table
- ✅ **FeedEntity** entity with `feeds` table and **many-to-many with MetropolitanRegion via `feed_regions` junction table**
- ✅ **MetropolitanRegionRepository** for region queries
- ✅ **FeedRepository** for feed queries
- ✅ **RegionController** REST API at `/api/feeds/regions` (GET regions, GET region details, PATCH region, GET region feeds, POST discover feeds)
- ✅ **MetropolitanRegionDTO** for region API responses
- ✅ **FeedImport** entity for tracking imports with `feed_imports` table
- ✅ **FeedImportService** for feed orchestration
- ✅ **Administrator** entity for user management

### From `schedule.transit_land` Package

- ✅ **TransitLandClient** with Spring WebClient, rate limiting (6 req/s), concurrency control
- ✅ **Feed discovery DTOs** (ScheduledFeed, TransitLandFeedResponse, TransitLandOperatorResponse)
- ✅ **Region sync** from Transit.land already implemented

### From `schedule.gtfs` Package (Custom CSV Parsing)

- ✅ **GTFS data models**: GTFSRoute, GTFSTrip, GTFSStop, GTFSCalendar, GTFSCalendarDate (lightweight DTOs for CSV deserialization)
- ✅ **GTFS data sources**: GTFSRouteDataSource, GTFSStopDataSource, GTFSScheduledTripDataSource (custom kotlinx.serialization CSV parser)
- ⚠️ **NOT using OneBusAway library** - current implementation uses kotlinx-serialization-csv
- ⚠️ **Decision**: Add OneBusAway library (T001-T002) for more comprehensive GTFS parsing (handles stop_times.txt, shapes.txt, calendar logic)

### Integration Approach

- `transitanalysis` module will **reference** `FeedEntity` via `feed_onestop_id` foreign key in Agency entity
- Agency inherits **many-to-many region membership** through Feed → `feed_regions` → MetropolitanRegion relationship chain
- `transitanalysis` module will **listen** to `FeedImportCompleted` events from feed module
- `transitanalysis` module will **query** TransitLandClient for additional metadata if needed
- `transitanalysis` module will **use OneBusAway library** for comprehensive GTFS parsing (complements existing CSV parsers)
- NO duplication of regions, feeds, or Transit.land client logic
- NO separate `agency_regions` junction table needed (use existing `feed_regions`)

### Critical Data Model Updates

- **Agency-Region Relationship**: Agency → Feed (ManyToOne) → Regions (existing feed_regions junction table provides many-to-many)
- **Agency-Feed Relationship**: Agency references existing `feeds` table via `feed_onestop_id` ManyToOne FK
- **Query Pattern**: To get agencies by region, join through feed: `agencies -> feeds -> feed_regions -> metropolitan_regions`

## Format: `[ID] [P?] [Story?] Description`

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

- [X] T001 Add OneBusAway GTFS library dependency to backend/gradle/libs.versions.toml (`onebusaway-gtfs = { module = "org.onebusaway:onebusaway-gtfs", version = "1.4.15" }`)
- [X] T002 Add OneBusAway GTFS library to backend/build.gradle.kts (`implementation(libs.onebusaway.gtfs)`)
- [X] T003 Add Spring WebFlux dependency for reactive HTTP client to backend/build.gradle.kts (`implementation("org.springframework.boot:spring-boot-starter-webflux")`)
- [X] T004 Add Kotlin coroutines reactor for WebClient suspend support to backend/build.gradle.kts (`implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")`)
- [X] T005 Create ADR for GTFS library choice (OneBusAway vs custom parser vs alternatives) in docs/adr/NNNN-gtfs-library-selection.md
- [X] T006 Create ADR for hash-based variant identification in docs/adr/NNNN-route-variant-identification.md
- [X] T007 Create ADR for Transitland API integration (Spring WebClient vs Retrofit vs alternatives) in docs/adr/NNNN-transitland-api-integration.md
- [X] T008 Create ADR for common section detection algorithm (LCS with 3-stop minimum) in docs/adr/NNNN-common-section-detection.md
- [X] T009 Create ADR for frequency calculation methodology in docs/adr/NNNN-frequency-calculation-method.md
- [X] T010 [P] Create transitanalysis module package structure at backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/
- [X] T011 [P] Create TransitAnalysisModule.kt module marker with @ApplicationModule annotation in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/TransitAnalysisModule.kt
- [X] T012 [P] Create Angular transit-frequency feature module structure at frontend/web/src/app/transit-frequency/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Database Schema

- [X] T013 Create database migration V021__create_transit_analysis_core_tables.sql (agencies with feed_onestop_id FK to existing feeds table, routes, route_variants) in backend/src/main/resources/db/migration/
- [X] T014 Create database migration V022__create_transit_analysis_frequency_tables.sql (frequencies table) in backend/src/main/resources/db/migration/
- [X] T015 Create database migration V023__create_transit_analysis_supporting_tables.sql (common_sections, common_section_variants) in backend/src/main/resources/db/migration/
- [X] T016 Create database migration V024__create_transit_analysis_indexes.sql (all indexes from data-model.md) in backend/src/main/resources/db/migration/
- [X] T017 Run migrations and verify tables created (`./gradlew flywayMigrate`)

### Value Classes & Enums (Shared across all stories)

- [X] T018 **SKIP** - RegionId already exists in feed module at backend/src/main/kotlin/com/mobilispect/backend/feed/model/ids/RegionId.kt (reuse via module public API)
- [X] T019 [P] Create AgencyId value class in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/valueobjects/AgencyId.kt
- [X] T020 [P] Create RouteId value class in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/valueobjects/RouteId.kt
- [X] T021 [P] Create VariantHash value class in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/valueobjects/VariantHash.kt
- [X] T022 [P] Create TimePeriod enum in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/TimePeriod.kt
- [X] T023 [P] Create RouteType enum in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/RouteType.kt
- [X] T024 [P] Create ImportStatus enum in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/ImportStatus.kt

### Core Domain Models (Required by all stories)

- [X] T025 **NOTE** - MetropolitanRegion already exists in feed module (reuse via foreign key reference to region_onestop_id)
- [X] T026 [P] Create Agency entity in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/Agency.kt (ManyToOne with FeedEntity via feed_onestop_id FK - inherits region membership through Feed's existing feed_regions relationship)
- [X] T027 [P] Create Route entity in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/Route.kt
- [X] T028 [P] Create RouteVariant entity in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/RouteVariant.kt
- [X] T029 [P] Create Frequency entity in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/Frequency.kt
- [X] T030 [P] Create CommonSection entity in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/CommonSection.kt
- [X] T031 [P] Create CommonSectionVariant entity in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/model/CommonSectionVariant.kt
- [X] T032 **NOTE** - Feed import tracking handled by existing FeedImport entity in feed module (reuse via published events)

### Repositories (Shared Infrastructure)

- [X] T033 **NOTE** - Region data queried via feed module's public API or direct reference to metropolitan_regions table via foreign key
- [X] T034 [P] Create AgencyRepository interface in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/repository/AgencyRepository.kt
- [X] T035 [P] Create RouteRepository interface in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/repository/RouteRepository.kt
- [X] T036 [P] Create RouteVariantRepository interface in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/repository/RouteVariantRepository.kt
- [X] T037 [P] Create FrequencyRepository interface in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/repository/FrequencyRepository.kt
- [X] T038 [P] Create CommonSectionRepository interface in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/repository/CommonSectionRepository.kt

### Frontend Shared Components & Models

- [X] T041 [P] Create transit-frequency.model.ts TypeScript interfaces in frontend/web/src/app/transit-frequency/models/transit-frequency.model.ts
- [X] T042 [P] Create time-period.model.ts TypeScript enum in frontend/web/src/app/transit-frequency/models/time-period.model.ts
- [X] T043 [P] Create route-type.model.ts TypeScript enum in frontend/web/src/app/transit-frequency/models/route-type.model.ts

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 4 - Import and Process Regional Transit Data (Priority: P4) 🏗️ Infrastructure

**Goal**: Import GTFS feeds for regions and process route/variant/frequency data

**Independent Test**: Import feeds for a test region and verify routes, variants, and frequencies are correctly calculated

**Note**: While P4 priority, this is foundational infrastructure for all other user stories and must be completed first

### Tests for User Story 4

> **TDD: Write tests FIRST, ensure they FAIL before implementation**

- [X] T044 [P] [US4] Create TransitlandClientTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/infrastructure/transitland/TransitlandClientTest.kt
- [X] T045 [P] [US4] Create FeedImportServiceTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/domain/service/FeedImportServiceTest.kt
- [X] T046 [P] [US4] Create VariantIdentificationServiceTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/domain/service/VariantIdentificationServiceTest.kt
- [X] T047 [P] [US4] Create FrequencyCalculationServiceTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/domain/service/FrequencyCalculationServiceTest.kt
- [X] T048 [P] [US4] Create CommonSectionDetectionServiceTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/domain/service/CommonSectionDetectionServiceTest.kt

### Backend Infrastructure for User Story 4

- [ ]egi49 **SKIP** - TransitLandClient already exists in schedule module at backend/src/main/kotlin/com/mobilispect/backend/schedule/transit_land/TransitLandClient.kt (reuse with WebClient, rate limiting, concurrency control)
- [X] T050 [P] [US4] Verify TransitLandClient has methods needed for metro areas discovery (check if metro_areas endpoint exists, add if missing)
- [ ] T051 **NOTE** - Feed discovery DTOs already exist (ScheduledFeed, TransitLandFeedResponse, TransitLandOperatorResponse) - reuse existing types
- [X] T053 [P] [US4] Create GtfsParser service in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/infrastructure/gtfs/GtfsParser.kt
- [X] T054 [US4] Implement GtfsParser using OneBusAway library to extract routes, trips, stops, stop_times
- [X] T055 [P] [US4] Create VariantHashGenerator utility in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/service/VariantHashGenerator.kt
- [X] T056 [US4] Implement SHA-256 hash generation for stop patterns in VariantHashGenerator
- [X] T057 [US4] Create VariantIdentificationService in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/service/VariantIdentificationService.kt
- [X] T058 [US4] Implement variant detection logic by grouping trips with identical stop patterns
- [X] T059 [US4] Create FrequencyCalculationService in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/service/FrequencyCalculationService.kt
- [X] T060 [US4] Implement average headway calculation by time period using scheduled departure times
- [X] T061 [US4] Implement edge case handling for irregular schedules (< 2 trips in period)
- [X] T062 [US4] Create CommonSectionDetectionService in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/service/CommonSectionDetectionService.kt
- [X] T063 [US4] Implement LCS (Longest Common Subsequence) algorithm for stop pattern matching
- [X] T064 [US4] Filter common sections to minimum 3 consecutive stops
- [X] T065 [US4] Create FeedImportService in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/service/FeedImportService.kt
- [X] T066 [US4] Implement orchestration logic: fetch feed → parse GTFS → identify variants → calculate frequencies → detect common sections
- [X] T067 [US4] Add error handling and retry logic with exponential backoff for Transitland API failures
- [X] T068 [US4] Add structured logging for all feed import stages (FR-023)
- [X] T069 [US4] Add metrics collection for processing duration, feed size, route count, variant count, error rates (FR-024)
- [X] T070 [US4] Add distributed tracing spans for feed import workflow stages (FR-025)
- [X] T071 [US4] Create FeedImportCompleted domain event in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/events/FeedImportCompleted.kt
- [X] T072 [US4] Create FrequencyCalculationCompleted domain event in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/events/FrequencyCalculationCompleted.kt
- [X] T073 [US4] Create RouteVariantIdentified domain event in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/events/RouteVariantIdentified.kt
- [X] T074 [US4] Publish domain events at appropriate stages in FeedImportService

### Application Services for User Story 4

- [ ] T075 **NOTE** - Feed import orchestration handled by existing feed module (FeedImportService, FeedImportTasklet)
- [ ] T076 **NOTE** - Region sync already handled by feed module (MetropolitanRegion + TransitLandClient integration)
- [ ] T077 [US4] Create FrequencyManagementService in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/application/FrequencyManagementService.kt
- [ ] T078 [US4] Implement calculateFrequenciesForFeed(feedId) method to orchestrate variant identification and frequency calculation
- [ ] T079 [US4] Implement recalculateFrequencies(routeId) method for selective recalculation

### Integration for User Story 4

- [ ] T080 [US4] Create TransitAnalysisModuleTest using @ModuleTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/module/TransitAnalysisModuleTest.kt
- [ ] T081 [US4] Verify Spring Modulith boundaries pass (`./gradlew verifyModulith`)
- [ ] T082 [US4] Create integration test for full feed import workflow with test GTFS data in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/integration/FeedImportIntegrationTest.kt

**Checkpoint**: User Story 4 complete - feed import infrastructure functional

---

## Phase 4: User Story 1 - View Regional Transit Frequency Overview (Priority: P1) 🎯 MVP

**Goal**: Display regional dashboard with all agencies grouped by region with aggregate frequency metrics

**Independent Test**: Select a region and view all agencies with route counts and basic frequency statistics

### Tests for User Story 1

> **TDD: Write tests FIRST, ensure they FAIL before implementation**

- [ ] T083 **SKIP** - Region queries handled by existing feed module (no RegionService needed in transitanalysis module)
- [X] T084 [P] [US1] Create AgencyQueryServiceTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/application/AgencyQueryServiceTest.kt
- [X] T085 [P] [US1] Create FrequencyAnalysisControllerTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/api/FrequencyAnalysisControllerTest.kt

### Backend Implementation for User Story 1

- [ ] T086 **NOTE** - MetropolitanRegionDTO already exists in feed module (`backend/src/main/kotlin/com/mobilispect/backend/api/dto/MetropolitanRegionDTO.kt`) - reuse or extend for frequency-specific fields
- [X] T087 [P] [US1] Create AgencyDTO in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/dto/AgencyDTO.kt (with regions: Set<RegionId>, feedOnestopId, route count)
- [X] T088 [P] [US1] Create AgencySummaryDTO with route count and aggregate frequency metrics in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/dto/AgencySummaryDTO.kt
- [X] T089 [US1] Create AgencyQueryService in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/application/AgencyQueryService.kt (frequency-focused queries, NOT general agency management)
- [ ] T090 [US1] Implement getAgenciesByRegion(regionId) method with sorting by route count and frequency aggregates
- [ ] T091 [US1] Implement getAgencyById(agencyId) method with route/frequency summary
- [ ] T092 **SKIP** - Region listing already handled by existing feed.controller.RegionController at /api/feeds/regions (reuse for region selection in UI)
- [X] T093 [US1] Create FrequencyAnalysisController in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/FrequencyAnalysisController.kt
- [ ] T094 [US1] Create FrequencyAnalysisController GET /api/v1/frequency/regions/{regionId}/agencies endpoint (agencies with frequency summary)
- [X] T095 [US1] Create FrequencyAnalysisController GET /api/v1/frequency/agencies/{agencyId} endpoint (agency details with routes)
- [ ] T096 [US1] Add caching for agency and frequency queries using Redis with 24-hour TTL

### Frontend Implementation for User Story 1

- [X] T099 [P] [US1] Create region.service.ts HTTP client in frontend/web/src/app/transit-frequency/services/region.service.ts
- [X] T100 [P] [US1] Create agency.service.ts HTTP client in frontend/web/src/app/transit-frequency/services/agency.service.ts
- [X] T101 [P] [US1] Create region-list component in frontend/web/src/app/transit-frequency/pages/region-list/region-list.component.ts
- [X] T102 [P] [US1] Create region-list component template in frontend/web/src/app/transit-frequency/pages/region-list/region-list.component.html
- [X] T103 [P] [US1] Create agency-summary-card component in frontend/web/src/app/transit-frequency/components/agency-summary-card/agency-summary-card.component.ts
- [X] T104 [US1] Add routing for /regions in frontend/web/src/app/transit-frequency/transit-frequency-routing.module.ts
- [ ] T105 [US1] Add routing for /regions/:regionId in transit-frequency-routing.module.ts
- [ ] T106 [US1] Implement agency sorting by route count in region-list component
- [ ] T107 [US1] Add light/dark mode support for all US1 components (constitutional requirement)
- [ ] T108 [US1] Add ARIA labels for accessibility (WCAG 2.1 AA) to region-list and agency-summary-card

### Integration for User Story 1

- [ ] T109 [US1] Create contract test for region API endpoints in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/contract/RegionApiContractTest.kt
- [ ] T110 [US1] Verify API responses match OpenAPI spec from contracts/region-api.yaml

**Checkpoint**: User Story 1 complete - regional overview functional with agency listings

---

## Phase 5: User Story 2 - Analyze Route Variants and Frequencies (Priority: P2)

**Goal**: Display route details with all variants, stop patterns, and frequencies by time period

**Independent Test**: Select any route and view variants with frequency calculations

### Tests for User Story 2

> **TDD: Write tests FIRST, ensure they FAIL before implementation**

- [ ] T111 [P] [US2] Create FrequencyQueryServiceTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/application/FrequencyQueryServiceTest.kt
- [ ] T112 [P] [US2] Create FrequencyControllerTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/api/FrequencyControllerTest.kt

### Backend Implementation for User Story 2

- [ ] T113 [P] [US2] Create RouteDTO in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/dto/RouteDTO.kt
- [ ] T114 [P] [US2] Create RouteVariantDTO in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/dto/RouteVariantDTO.kt
- [ ] T115 [P] [US2] Create FrequencyDTO in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/dto/FrequencyDTO.kt
- [ ] T116 [US2] Create FrequencyQueryService in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/application/FrequencyQueryService.kt
- [ ] T117 [US2] Implement getRouteById(routeId) method
- [ ] T118 [US2] Implement getVariantsByRoute(routeId) method
- [ ] T119 [US2] Implement getFrequenciesForVariant(variantId, date) method filtering by time period
- [ ] T120 [US2] Create FrequencyController GET /api/v1/routes/{routeId} in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/FrequencyController.kt
- [ ] T121 [US2] Create FrequencyController GET /api/v1/routes/{routeId}/variants endpoint
- [ ] T122 [US2] Create FrequencyController GET /api/v1/variants/{variantId}/frequencies endpoint with date parameter
- [ ] T123 [US2] Add Redis caching for frequency queries with 1-hour TTL
- [ ] T124 [US2] Implement cache invalidation on FeedImportCompleted event

### Frontend Implementation for User Story 2

- [ ] T125 [P] [US2] Create frequency.service.ts HTTP client in frontend/web/src/app/transit-frequency/services/frequency.service.ts
- [ ] T126 [P] [US2] Create route-frequency component in frontend/web/src/app/transit-frequency/pages/route-frequency/route-frequency.component.ts
- [ ] T127 [P] [US2] Create variant-list component in frontend/web/src/app/transit-frequency/components/variant-list/variant-list.component.ts
- [ ] T128 [P] [US2] Create frequency-chart component (headway by time period) in frontend/web/src/app/transit-frequency/components/frequency-chart/frequency-chart.component.ts
- [ ] T129 [US2] Add routing for /routes/:routeId in transit-frequency-routing.module.ts
- [ ] T130 [US2] Display stop pattern for each variant in variant-list component
- [ ] T131 [US2] Add date picker for frequency queries in route-frequency component
- [ ] T132 [US2] Display "Variable Schedule" indicator for irregular frequencies
- [ ] T133 [US2] Add light/dark mode support for all US2 components
- [ ] T134 [US2] Add ARIA labels and keyboard navigation for variant-list and frequency-chart

### Integration for User Story 2

- [ ] T135 [US2] Create contract test for frequency API endpoints in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/contract/FrequencyApiContractTest.kt
- [ ] T136 [US2] Verify API responses match OpenAPI spec from contracts/frequency-api.yaml

**Checkpoint**: User Story 2 complete - route variant analysis functional with frequency display

---

## Phase 6: User Story 3 - Identify Common Sections with Combined Frequency (Priority: P3)

**Goal**: Display common sections where routes overlap with combined frequency calculations

**Independent Test**: Select routes with known overlapping segments and verify common sections with combined frequencies

### Tests for User Story 3

> **TDD: Write tests FIRST, ensure they FAIL before implementation**

- [ ] T137 [P] [US3] Create CommonSectionServiceTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/application/CommonSectionServiceTest.kt
- [ ] T138 [P] [US3] Create CommonSectionControllerTest in backend/src/test/kotlin/com/mobilispect/backend/transitanalysis/api/CommonSectionControllerTest.kt

### Backend Implementation for User Story 3

- [ ] T139 [P] [US3] Create CommonSectionDTO in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/dto/CommonSectionDTO.kt
- [ ] T140 [P] [US3] Create CombinedFrequencyDTO in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/dto/CombinedFrequencyDTO.kt
- [ ] T141 [US3] Create CommonSectionService in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/application/CommonSectionService.kt
- [ ] T142 [US3] Implement getCommonSectionsForRoute(routeId) method
- [ ] T143 [US3] Implement getCombinedFrequency(commonSectionId, timePeriod) method summing frequencies from all contributing variants
- [ ] T144 [US3] Implement getContributingRoutes(commonSectionId) method
- [ ] T145 [US3] Create CommonSectionController GET /api/v1/routes/{routeId}/common-sections in backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/CommonSectionController.kt
- [ ] T146 [US3] Create CommonSectionController GET /api/v1/common-sections/{sectionId}/frequency endpoint
- [ ] T147 [US3] Create CommonSectionController GET /api/v1/common-sections/{sectionId}/contributing-routes endpoint
- [ ] T148 [US3] Add Redis caching for common section queries with 6-hour TTL

### Frontend Implementation for User Story 3

- [ ] T149 [P] [US3] Create common-section.service.ts HTTP client in frontend/web/src/app/transit-frequency/services/common-section.service.ts
- [ ] T150 [P] [US3] Create common-section-display component in frontend/web/src/app/transit-frequency/components/common-section-display/common-section-display.component.ts
- [ ] T151 [US3] Display common sections on route-frequency page
- [ ] T152 [US3] Show combined frequency with individual route contributions
- [ ] T153 [US3] Highlight common section geographic extent on map (if map integration exists)
- [ ] T154 [US3] Add visual indicator for which variants participate in common sections
- [ ] T155 [US3] Add light/dark mode support for common-section-display component
- [ ] T156 [US3] Add ARIA labels and accessible table markup for common section data

**Checkpoint**: User Story 3 complete - common section analysis functional with combined frequencies

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Quality assurance, performance, observability, accessibility, documentation

### Performance & Caching

- [ ] T157 [P] Verify <200ms p95 response time for region/agency/route endpoints using load testing
- [ ] T158 [P] Verify <2s response time for regional overview with 20+ agencies
- [ ] T159 [P] Verify system processes 20-agency region within 5 minutes (SC-005)
- [ ] T160 Add cache hit/miss metrics for Redis caching to Grafana dashboard
- [ ] T161 Add API latency metrics to Grafana dashboard

### Observability (Constitutional Requirement)

- [ ] T162 [P] Create Grafana dashboard for feed processing health (import duration, error rates, feed sizes)
- [ ] T163 [P] Create Grafana dashboard for frequency calculation performance (calculation time, cache hits)
- [ ] T164 [P] Create Grafana dashboard for API latency and throughput
- [ ] T165 Configure alerts for feed import failures in Grafana Cloud
- [ ] T166 Configure alerts for API p95 latency exceeding 200ms threshold

### Accessibility (WCAG 2.1 AA - Constitutional Requirement)

- [ ] T167 [P] Run axe accessibility scan on all transit-frequency pages
- [ ] T168 [P] Test keyboard navigation for all interactive components
- [ ] T169 [P] Test screen reader compatibility (NVDA/JAWS on Windows, VoiceOver on macOS)
- [ ] T170 Verify color contrast ratios meet WCAG 2.1 AA standards (4.5:1 for normal text)
- [ ] T171 Ensure all form inputs have associated labels
- [ ] T172 Add skip links for keyboard navigation
- [ ] T173 Document accessibility acceptance criteria in feature plan

### End-to-End Testing (Playwright - Constitutional Requirement)

- [ ] T174 [P] Create Playwright E2E test for region list and agency selection in frontend/web/e2e/transit-frequency/region-overview.spec.ts
- [ ] T175 [P] Create Playwright E2E test for route variant display and frequency analysis in frontend/web/e2e/transit-frequency/route-frequency.spec.ts
- [ ] T176 [P] Create Playwright E2E test for common section identification in frontend/web/e2e/transit-frequency/common-sections.spec.ts
- [ ] T177 Run Playwright tests across Chrome, Firefox, Safari (`npx playwright test`)
- [ ] T178 Enable parallel execution for Playwright tests in CI/CD

### Architecture Documentation (C4 Model - Constitutional Requirement)

- [ ] T179 [P] Create C4 Container diagram for transit-analysis module in docs/architecture/transit-analysis-container.puml
- [ ] T180 [P] Create C4 Component diagram showing module internals in docs/architecture/transit-analysis-component.puml
- [ ] T181 [P] Create sequence diagram for feed import workflow in docs/architecture/feed-import-sequence.puml
- [ ] T182 [P] Create entity relationship diagram for data model in docs/architecture/transit-analysis-erd.puml

### Module Documentation & Verification

- [ ] T183 Generate Spring Modulith module documentation (`./gradlew generateModulithDocs`)
- [ ] T184 Verify all module boundaries pass verification (`./gradlew verifyModulith`)
- [ ] T185 Document module public APIs in module README
- [ ] T186 Document published events and their schemas

### Documentation & Cleanup

- [ ] T187 [P] Update API documentation with all new endpoints in OpenAPI specs
- [ ] T188 [P] Create user guide for feed import process
- [ ] T189 Run quickstart.md validation steps
- [ ] T190 Verify all unit tests pass (`./gradlew test`)
- [ ] T191 Verify all integration tests pass (`./gradlew integrationTest`)
- [ ] T192 Run SonarQube analysis and resolve critical issues
- [ ] T193 Verify 80%+ test coverage for all new code (constitutional requirement)

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
    Phase 3 (US4 - Infrastructure)
         │
         │ BLOCKS ALL DISPLAY FEATURES
         ▼
    ┌────┴────┬────────────┐
    │         │            │
    ▼         ▼            ▼
Phase 4   Phase 5      Phase 6
 (US1)     (US2)        (US3)
  MVP    ◄─depends─┐      │
    │         │    │      │
    │    US2 needs │ US3 needs
    │    regions+  │ variants+
    │    agencies  │ frequencies
    │         │    │      │
    └─────────┴────┴──────┘
              │
              ▼
         Phase 7 (Polish)
```

### User Story Dependencies

| Story | Depends On | Can Start After | Independent Test |
|-------|------------|-----------------|------------------|
| US4 (P4) | Phase 2 | T043 complete | Import feeds, verify data processed |
| US1 (P1) | US4 (T082) | US4 checkpoint | Select region, view agencies |
| US2 (P2) | US1 (T110) | US1 checkpoint | Select route, view variants/frequencies |
| US3 (P3) | US2 (T136) | US2 checkpoint | View common sections with combined frequency |

### Parallel Opportunities

**Within Phase 2 (Foundational)**:

- T018-T025 (value classes & enums) - all parallel
- T026-T033 (entities) - all parallel
- T034-T040 (repositories) - all parallel
- T041-T043 (frontend models) - all parallel

**Within User Story 4**:

- T044-T048 (tests) - all parallel
- T049, T051-T053, T055 (infrastructure components) - parallel

**Within User Story 1**:

- T083-T085 (tests) - all parallel
- T086-T088 (DTOs) - all parallel
- T099-T103 (frontend components) - parallel

**Across Stories (with multiple developers)**:

- US4 must complete before any other user story
- US1 can start immediately after US4
- US2 and US3 are sequential (US2 → US3)

---

## Parallel Example: User Story 1

```bash
# Launch all tests for US1 together (TDD - write first):
Task: "T083 [P] [US1] Create RegionServiceTest"
Task: "T084 [P] [US1] Create AgencyServiceTest"
Task: "T085 [P] [US1] Create RegionControllerTest"

# Launch all DTOs for US1 together:
Task: "T086 [P] [US1] Create RegionDTO"
Task: "T087 [P] [US1] Create AgencyDTO"
Task: "T088 [P] [US1] Create AgencySummaryDTO"

# Launch frontend components together:
Task: "T099 [P] [US1] Create region.service.ts"
Task: "T100 [P] [US1] Create agency.service.ts"
Task: "T101 [P] [US1] Create region-list component"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T012)
2. Complete Phase 2: Foundational (T013-T043) - **CRITICAL BLOCKER**
3. Complete Phase 3: User Story 4 Infrastructure (T044-T082) - **REQUIRED FOR ALL FEATURES**
4. Complete Phase 4: User Story 1 (T083-T110)
5. **STOP and VALIDATE**: Test regional overview independently
6. Deploy/demo if ready - users can view regions and agencies

### Incremental Delivery

| Increment | Stories | User Value |
|-----------|---------|------------|
| Infrastructure | US4 | Feed import capability (admin-facing) |
| MVP | US1 | View regional transit overview with agency listings |
| +Route Analysis | US1 + US2 | Analyze individual routes with variant frequencies |
| Complete | US1-US3 | Full common section analysis with combined frequencies |

### Parallel Team Strategy

With 2 developers:

1. Both complete Phase 1 + 2 together
2. Both complete Phase 3 (US4) together - infrastructure is complex
3. Once US4 done:
   - Dev A: US1 (regional overview)
   - Dev B: US2 (route analysis)
4. Both work on US3 together (builds on US2)
5. Merge and Polish together

---

## Notes

- Constitution mandates TDD - tests MUST fail before implementation
- Spring Modulith verification required before merge
- All value classes must use @JvmInline pattern
- Redis caching TTL: 24 hours for regions/agencies, 1 hour for frequencies, 6 hours for common sections
- Invalidate cache on FeedImportCompleted event
- WCAG 2.1 AA accessibility required for all UI components
- Playwright E2E tests must cover Chrome, Firefox, Safari
- All observability signals sent to Grafana Cloud per constitutional requirements
- Module boundaries enforced via Spring Modulith - no cross-module database access

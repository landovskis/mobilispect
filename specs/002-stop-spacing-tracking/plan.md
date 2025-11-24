# Implementation Plan: Average Stop Spacing Tracking

**Branch**: `002-stop-spacing-tracking` | **Date**: 2025-11-23 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-stop-spacing-tracking/spec.md`

## Summary

Implement stop spacing statistics tracking for transit routes, including per-route metrics
(average, min, max, std dev), automatic service type classification (local/rapid/express),
and comparison views at agency and metropolitan region levels. Statistics are calculated
during GTFS feed ingestion and stored for fast retrieval. Classification thresholds are
configurable at the regional level.

## Technical Context

**Language/Version**: Kotlin 2.0.21 with Spring Boot 3.5.3
**Primary Dependencies**: Spring Modulith, Spring Data JPA, Spring Batch, PostgreSQL 17, Redis 8.2
**Storage**: PostgreSQL 17 for persistence, Redis for caching aggregated statistics
**Testing**: JUnit 5 with Spring Test, MockK, Testcontainers (PostgreSQL, Redis)
**Target Platform**: Linux server (backend), Angular 19 (web frontend)
**Project Type**: Web application (backend + frontend)
**Performance Goals**: <200ms p95 for route statistics queries, <2s for regional comparisons
**Constraints**: Statistics calculated during feed ingestion (not on-demand), geodesic distance calculation
**Scale/Scope**: 500+ routes per agency, multiple agencies per region

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality First | ✅ PASS | Will use value classes for IDs (RouteId, StopId), follow DRY/YAGNI/SOLID |
| II. Test-Driven Development | ✅ PASS | Tests required before implementation, 80%+ coverage target |
| III. Cross-Platform UX Consistency | ✅ PASS | Unit conversion (km/mi) supported, WCAG 2.1 AA compliance |
| IV. Performance Standards | ✅ PASS | <200ms p95 API response, pre-calculated statistics |
| V. Observability & Monitoring | ✅ PASS | Structured logging for calculations, metrics for cache hits |
| VI. Architecture Decision Records | ✅ PASS | ADR required for geodesic calculation library choice |
| VII. Modular Monolith Architecture | ✅ PASS | New module `stopspacing` following Spring Modulith conventions |

### Module Boundary Design

- **New Module**: `stopspacing` - owns stop spacing statistics, service type classification, thresholds
- **Dependencies**:
  - Consumes events from `feed` module (FeedImportCompletedEvent)
  - Reads from `schedule` module (Route, Trip data via published APIs)
  - Reads from `infrastructure` module (Stop coordinates via published APIs)
- **Exposes**: StopSpacingStatistics, ServiceType, agency/region comparison APIs

## Project Structure

### Documentation (this feature)

```text
specs/002-stop-spacing-tracking/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (OpenAPI specs)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
backend/
├── src/main/kotlin/com/mobilispect/backend/
│   ├── stopspacing/                    # NEW MODULE
│   │   ├── StopSpacingModule.kt        # Module marker
│   │   ├── model/
│   │   │   ├── StopSpacingStatistics.kt
│   │   │   ├── ServiceType.kt
│   │   │   ├── ClassificationThreshold.kt
│   │   │   └── ids/
│   │   │       └── StopSpacingId.kt
│   │   ├── repository/
│   │   │   ├── StopSpacingRepository.kt
│   │   │   └── ClassificationThresholdRepository.kt
│   │   ├── service/
│   │   │   ├── StopSpacingCalculationService.kt
│   │   │   ├── ServiceTypeClassifier.kt
│   │   │   ├── GeodesicDistanceCalculator.kt
│   │   │   └── StopSpacingAggregationService.kt
│   │   ├── event/
│   │   │   └── FeedImportCompletedListener.kt
│   │   └── controller/
│   │       ├── RouteStopSpacingController.kt
│   │       ├── AgencyComparisonController.kt
│   │       ├── RegionalComparisonController.kt
│   │       └── ThresholdsController.kt
│   ├── api/dto/
│   │   └── stopspacing/
│   │       ├── StopSpacingDTO.kt
│   │       ├── ServiceTypeDTO.kt
│   │       ├── AgencyComparisonDTO.kt
│   │       └── RegionalComparisonDTO.kt
│   └── feed/
│       └── event/
│           └── FeedImportCompletedEvent.kt  # Existing/enhanced
└── src/test/kotlin/com/mobilispect/backend/
    └── stopspacing/
        ├── service/
        │   ├── StopSpacingCalculationServiceTest.kt
        │   ├── ServiceTypeClassifierTest.kt
        │   └── GeodesicDistanceCalculatorTest.kt
        ├── repository/
        │   └── StopSpacingRepositoryTest.kt
        └── integration/
            └── StopSpacingModuleIntegrationTest.kt

frontend/web/src/app/
├── stop-spacing/                        # NEW FEATURE MODULE
│   ├── components/
│   │   ├── route-spacing-card.component.ts
│   │   ├── service-type-badge.component.ts
│   │   ├── agency-comparison-table.component.ts
│   │   └── regional-comparison-chart.component.ts
│   ├── models/
│   │   ├── stop-spacing.model.ts
│   │   └── service-type.model.ts
│   ├── services/
│   │   └── stop-spacing.service.ts
│   └── pages/
│       ├── route-details.component.ts
│       ├── agency-comparison.component.ts
│       └── regional-comparison.component.ts
└── shared/
    └── components/
        └── unit-toggle.component.ts     # km/mi toggle
```

**Structure Decision**: Web application structure with new `stopspacing` backend module
following Spring Modulith conventions. Module communicates via events with existing `feed`
module and reads from `schedule`/`infrastructure` modules through their public APIs.

## Complexity Tracking

> No constitution violations requiring justification.

| Aspect | Decision | Rationale |
|--------|----------|-----------|
| New module | `stopspacing` | Encapsulates stop spacing domain; follows modular monolith principle |
| Event-driven | FeedImportCompletedEvent | Decouples calculation trigger from feed module |
| Pre-calculation | During ingestion | Meets <200ms query requirement; avoids N+1 on reads |

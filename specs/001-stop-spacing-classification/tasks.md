# Tasks: Average Stop Spacing

**Input**: Design documents from `/specs/001-stop-spacing-classification/`
**Prerequisites**: plan.md (required), spec.md (required for user stories),
research.md, data-model.md, contracts/

**Tests**: Not requested in spec; no test tasks included.

**Organization**: Tasks are grouped by user story to enable independent
implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Align documentation and contracts before implementation

- [x] T001 Verify API contract fields in
  `specs/001-stop-spacing-classification/contracts/route-variants.yaml` match
  planned DTO changes

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core backend changes required before any UI work

- [x] T002 Add average stop spacing column migration in
  `backend/src/main/resources/db/migration/`
  `V029__add_route_variant_stop_spacing.sql`
- [x] T003 Update RouteVariant entity with averageStopSpacingKm in
  `backend/src/main/kotlin/com/mobilispect/backend/`
  `transitanalysis/domain/model/RouteVariant.kt`
- [x] T004 Extend parsed GTFS stop time model to include shape distance in
  `backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/`
  `infrastructure/gtfs/GtfsParser.kt`
- [x] T005 Populate parsed shape distance (and shape fallback inputs) in
  `backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/`
  `infrastructure/gtfs/ConveyalGtfsParser.kt`
- [x] T006 Implement stop spacing calculator for along-route distances in
  `backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/`
  `service/StopSpacingCalculationService.kt`

---

## Phase 3: User Story 1 - View stop spacing per variant (Priority: P1) 🎯 MVP

**Goal**: Show average stop spacing and classification per route variant on the
route detail page.

**Independent Test**: Open a route detail page with variants and confirm
spacing + classification display per variant.

### Implementation

- [x] T007 [US1] Compute and persist averageStopSpacingKm during variant
  identification/import in
  `backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/`
  `service/VariantIdentificationService.kt`
- [x] T008 [US1] Add spacing + classification fields to RouteVariantDTO in
  `backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/dto/`
  `RouteVariantDTO.kt`
- [x] T009 [US1] Map spacing + classification into API responses in
  `backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/application/`
  `FrequencyQueryService.kt`
- [x] T010 [US1] Extend frontend RouteVariantDto interface in
  `frontend/web/src/app/transit-frequency/services/frequency.service.ts`
- [x] T011 [US1] Render spacing + classification in variant list UI in
  `frontend/web/src/app/transit-frequency/components/variant-list/`
  `variant-list.component.ts`

---

## Phase 4: User Story 2 - Compare variants consistently (Priority: P2)

**Goal**: Ensure spacing values use a consistent unit and precision.

**Independent Test**: Verify all variants show spacing in km with two decimal
places and consistent labels.

### Implementation (US2)

- [x] T012 [US2] Format spacing values to two decimals and append "km" in
  `frontend/web/src/app/transit-frequency/components/variant-list/`
  `variant-list.component.ts`

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Observability and documentation alignment

- [x] T013 Add structured log for spacing calculation outcomes in
  `backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/domain/`
  `service/StopSpacingCalculationService.kt`
- [x] T014 Validate quickstart steps and adjust if needed in
  `specs/001-stop-spacing-classification/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Phase 1
- **User Story 1 (Phase 3)**: Depends on Phase 2
- **User Story 2 (Phase 4)**: Depends on Phase 3 (UI formatting builds on US1
  rendering)
- **Polish (Phase 5)**: Depends on Phases 3–4

### User Story Dependencies

- **User Story 1 (P1)**: Must complete before User Story 2
- **User Story 2 (P2)**: Depends on User Story 1 UI output

---

## Parallel Execution Examples

### User Story 1

- [x] T008 [US1] Add spacing + classification fields to RouteVariantDTO in
  `backend/src/main/kotlin/com/mobilispect/backend/transitanalysis/api/dto/`
  `RouteVariantDTO.kt`
- [x] T010 [US1] Extend frontend RouteVariantDto interface in
  `frontend/web/src/app/transit-frequency/services/frequency.service.ts`

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate the route detail page shows spacing + classification per variant

### Incremental Delivery

1. Deliver User Story 1 (spacing + classification display)
2. Deliver User Story 2 (consistent unit formatting)
3. Apply polish/observability updates

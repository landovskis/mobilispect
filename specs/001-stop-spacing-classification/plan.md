# Implementation Plan: Average Stop Spacing

**Branch**: `001-stop-spacing-classification` | **Date**: 2025-12-20 |
**Spec**:
/Users/alex/src/mobilispect/spring/specs/001-stop-spacing-classification/spec.md
**Input**: Feature specification from
`/specs/001-stop-spacing-classification/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See
`.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Add average stop spacing (km) and classification (local/rapid/express) per route
variant, derived from GTFS along-route distances during feed import and
surfaced on the route detail page.

## Technical Context

**Language/Version**: Kotlin (Spring Boot) backend, TypeScript (Angular)
frontend
**Primary Dependencies**: Spring Boot, JPA/Hibernate, PostgreSQL, Angular,
Angular Material, Tailwind CSS
**Storage**: PostgreSQL
**Testing**: JUnit 5, Testcontainers (backend); Angular/Karma unit tests
(frontend)
**Target Platform**: Web app + API server
**Project Type**: Web application (frontend + backend)
**Performance Goals**: API p95 ≤ 200ms; UI interactions maintain 60fps
**Constraints**: Spring Modulith boundaries; no cross-module DB access;
Tailwind for layout; Angular Material for UI components; secrets outside VCS
**Scale/Scope**: Hundreds of feeds; thousands of routes/variants per import

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- UI styling: Tailwind CSS for layout/spacing; Angular Material components for
  controls.
- Modular monolith: respect Modulith boundaries; no cross-module DB access.
- Test-driven quality: tests first; maintain ≥80% coverage per component.
- Observability: structured logs/metrics for new import calculations.
- Security: secrets outside VCS; follow existing auth/audit patterns for
  sensitive paths.

Status: PASS (no violations anticipated)

## Project Structure

### Documentation (this feature)

```text
specs/001-stop-spacing-classification/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created
                         # by /speckit.plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/kotlin/com/mobilispect/backend/transitanalysis/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/gtfs/
└── src/test/kotlin/com/mobilispect/backend/

frontend/web/
├── src/app/transit-frequency/
│   ├── components/
│   ├── pages/
│   └── services/
└── src/app/shared/
```

**Structure Decision**: Web application with Spring Boot backend and Angular
frontend.

## Complexity Tracking

None.

# Implementation Plan: Isochrone Map Builder

**Branch**: `claude/isochrone-map-builder-Adv0B` | **Date**: 2026-02-19
**Spec**: `specs/claude/isochrone-map-builder-Adv0B/spec.md`
**Input**: Feature specification — isochrone map for transit, walk, and bike times

---

## Summary

Build an interactive isochrone map that visualises areas reachable from a selected
origin point within configurable time thresholds (15/30/45/60 min) for three travel
modes — transit, walking, and cycling.

**Technical approach**:
- **Routing engine**: OpenTripPlanner (OTP) 2.x as a sidecar container; consumes GTFS
  feeds already imported into Mobilispect + OSM extracts
- **Backend**: New Spring Modulith module `isochrone` — proxies OTP, caches results
  in Redis (1 h transit / 24 h walk+bike), exposes `GET /api/v1/isochrones`
- **Frontend**: New Angular feature module `isochrone-map` using MapLibre GL JS;
  renders GeoJSON bands with colour-coded time rings; full light/dark and
  keyboard/screen-reader support

---

## Technical Context

**Language/Version**: Kotlin 2.3, Java 25 (backend); TypeScript / Angular 21 (frontend)
**Primary Dependencies**:
  - Backend: Spring Boot 4.0, Spring Modulith, Resilience4j (circuit breaker for OTP calls), Redis (Spring Data Redis / Lettuce)
  - Frontend: MapLibre GL JS 4.x (`maplibre-gl` npm), Angular Material 21, RxJS 7.8
**Storage**: No new database tables; Redis for isochrone response cache
**Testing**: Backend — JUnit 5 / MockK / Testcontainers (PostgreSQL); Frontend — Jest / Playwright (Chromium, Firefox, WebKit)
**Target Platform**: Linux server (OTP sidecar), web browser (Angular SPA)
**Project Type**: Web (frontend + backend)
**Performance Goals**: API p95 ≤ 200 ms (cache hit); first computation ≤ 5 s (OTP call, acceptable for cold start)
**Constraints**: OTP must be isolated behind backend proxy (not internet-accessible); WCAG 2.1 AA; 60 fps map interactions
**Scale/Scope**: Same 5 metro regions as current (Montreal, Toronto, Vancouver, Ottawa, SF Bay); up to ~1 000 unique origin points per hour per region

---

## Constitution Check

### Simplicity

- **Projects**: 2 — `backend` (Kotlin Spring), `frontend/web` (Angular). OTP is infrastructure, not a new project. ✓ (≤ 3 max)
- **Framework direct**: OTP called via its own REST API — no wrapper class hierarchy beyond a thin `OtpClient` data-fetcher. ✓
- **Single data model**: `IsochroneResult` used end-to-end; separate `OtpIsochroneResponse` only because OTP's schema differs from our API (unavoidable serialisation boundary). ✓
- **Avoiding patterns**: No Repository pattern (no DB); no Unit of Work. Redis accessed via `StringRedisTemplate` directly. ✓

### Architecture

- **Spring Modulith boundary**: New module `isochrone` — public API is `IsochroneQueryService` only. No cross-module DB access. ✓
- **Libraries**: `isochrone` module (Spring Modulith) + `isochrone-map` Angular feature module. Both are self-contained. ✓
- **Frontend module**: Angular lazy-loaded feature module at `/isochrone` route. ✓

### Testing (NON-NEGOTIABLE)

- RED-GREEN-Refactor: All tests written and confirmed failing before implementation ✓ (enforced in task order)
- Commit order: Contract test commit precedes implementation commits ✓
- Test order: Contract → Integration → Unit (per constitutional requirement) ✓
- Real dependencies: Testcontainers PostgreSQL for integration tests; WireMock for OTP HTTP stub in contract tests ✓
- E2E: Playwright tests for isochrone map page across Chromium, Firefox, WebKit ✓

### Observability

- Structured logging: computation start/end, cache hit/miss, OTP latency — all via SLF4J MDC ✓
- Metrics: `isochrone.computation.duration`, `isochrone.cache.hits`, `isochrone.cache.misses`, `isochrone.otp.errors` via Micrometer ✓
- Traces: OpenTelemetry span from HTTP request → OTP call → cache write ✓

### Versioning

- No breaking change to existing API — additive new endpoint ✓
- `isochrone` module versioned as 1.0.0 ✓

---

## Project Structure

### Documentation (this feature)

```
specs/claude/isochrone-map-builder-Adv0B/
├── plan.md              ← this file
├── research.md          ← Phase 0 ✓
├── data-model.md        ← Phase 1 ✓
├── quickstart.md        ← Phase 1 ✓
├── contracts/
│   └── isochrone-api.yaml   ← Phase 1 ✓
└── tasks.md             ← Phase 2 (/tasks command)
```

### Backend Source (new isochrone module)

```
backend/src/main/kotlin/com/mobilispect/backend/isochrone/
├── IsochroneModule.kt              ← Spring Modulith module marker
├── domain/
│   ├── IsochroneRequest.kt         ← value object
│   ├── IsochroneResult.kt          ← value object
│   ├── IsochroneBand.kt            ← value object
│   └── TravelMode.kt               ← enum
├── application/
│   └── IsochroneQueryService.kt    ← public module API
├── internal/
│   ├── OtpClient.kt                ← HTTP client to OTP (WebClient)
│   ├── OtpIsochroneRequest.kt      ← OTP-specific request shape
│   ├── OtpIsochroneResponse.kt     ← OTP-specific response shape
│   └── IsochroneCache.kt           ← Redis cache adapter
└── api/
    ├── IsochroneController.kt      ← REST controller
    └── dto/
        ├── IsochroneRequestParams.kt
        └── IsochroneResponseDto.kt

backend/src/test/kotlin/com/mobilispect/backend/isochrone/
├── application/
│   └── IsochroneQueryServiceTest.kt         ← unit tests
├── internal/
│   ├── OtpClientTest.kt                     ← unit test with WireMock
│   └── IsochroneCacheTest.kt               ← unit test with embedded Redis
└── api/
    └── IsochroneControllerContractTest.kt   ← contract test (MockMvc)

backend/src/integrationTest/kotlin/com/mobilispect/backend/isochrone/
└── IsochroneIntegrationTest.kt             ← full stack with Testcontainers + WireMock OTP
```

### Backend Infrastructure (OTP config)

```
backend/src/main/resources/
└── application.yml   ← new `mobilispect.otp.*` config block

docker-compose.yml    ← new `otp` service
.otp/
└── graphs/
    └── default/      ← GTFS + OSM files for local dev (gitignored)
.gitignore            ← add .otp/graphs/**/*.pbf, .otp/graphs/**/*.zip
```

### Frontend Source (new isochrone-map module)

```
frontend/web/src/app/isochrone-map/
├── isochrone-map.routes.ts              ← lazy-loaded route definition
├── models/
│   ├── isochrone-request.ts
│   └── isochrone-response.ts
├── services/
│   └── isochrone.service.ts            ← HTTP calls to backend /api/v1/isochrones
├── components/
│   ├── isochrone-map/
│   │   ├── isochrone-map.component.ts  ← MapLibre GL JS container
│   │   ├── isochrone-map.component.html
│   │   └── isochrone-map.component.scss
│   ├── travel-mode-selector/
│   │   ├── travel-mode-selector.component.ts
│   │   └── travel-mode-selector.component.html
│   ├── cutoff-selector/
│   │   ├── cutoff-selector.component.ts
│   │   └── cutoff-selector.component.html
│   └── isochrone-legend/
│       ├── isochrone-legend.component.ts
│       └── isochrone-legend.component.html
└── pages/
    └── isochrone-page/
        ├── isochrone-page.component.ts
        └── isochrone-page.component.html

frontend/web/src/app/isochrone-map/
└── (spec files alongside each component)

frontend/web/e2e/
└── isochrone-map.spec.ts               ← Playwright E2E
```

### ADRs (required before implementation)

```
docs/adr/
├── 0011-otp-as-routing-engine.md
└── 0012-maplibre-gl-js-map-library.md
```

---

## Phase 0: Research — COMPLETE

See `research.md`. All unknowns resolved:
- [x] Routing engine: OpenTripPlanner 2.x
- [x] Map library: MapLibre GL JS 4.x
- [x] Cache strategy: Redis, 1 h / 24 h TTL
- [x] OTP deployment: Docker Compose sidecar
- [x] Tile provider: OpenFreeMap (free, no key)
- [x] Spring Modulith boundary: new `isochrone` module
- [x] Travel modes: TRANSIT, WALK, BICYCLE

---

## Phase 1: Design and Contracts — COMPLETE

See `data-model.md`, `contracts/isochrone-api.yaml`, and `quickstart.md`.

**Entities designed**:
- `IsochroneRequest`, `IsochroneResult`, `IsochroneBand`, `TravelMode` (backend)
- `IsochroneRequest`, `IsochroneResponse`, `IsochroneBand` (frontend TypeScript)
- OTP internal DTOs: `OtpIsochroneRequest`, `OtpIsochroneResponse`

**Contract**: OpenAPI 3.1 — `GET /api/v1/isochrones` with full request/response schema

**No DB migrations required** — isochrone computation is stateless (Redis cache only)

**Infrastructure additions**:
- OTP sidecar in `docker-compose.yml`
- `mobilispect.otp.base-url` and `mobilispect.otp.timeout-seconds` in `application.yml`
- `maplibre-gl` npm package in `frontend/web/package.json`

### Post-Phase 1 Constitution Re-Check

All checks still pass. No violations introduced. OTP circuit breaker (Resilience4j,
already a project dependency) protects against OTP unavailability — no new
infrastructure library needed.

---

## Phase 2: Task Planning Approach

*This section describes what the `/tasks` command will generate — not executed here.*

### Task Generation Strategy

Base: `templates/tasks-template.md`

**Source inputs**:
- `contracts/isochrone-api.yaml` → contract test tasks
- `data-model.md` → model/DTO creation tasks
- `quickstart.md` → integration scenario tasks
- Architecture above → component implementation tasks

### Task Categories and Ordering

**Chunk A — Foundation (no dependencies)**
1. Write ADR 0011 (OTP routing engine)
2. Write ADR 0012 (MapLibre GL JS)
3. Add OTP sidecar to `docker-compose.yml`
4. Add `.otp/` local dev setup to `.gitignore`

**Chunk B — Backend RED (tests before implementation)**
5. Write `IsochroneControllerContractTest` — must FAIL
6. Write `IsochroneQueryServiceTest` — must FAIL
7. Write `OtpClientTest` with WireMock — must FAIL
8. Write `IsochroneCacheTest` — must FAIL
9. Write `IsochroneIntegrationTest` (Testcontainers) — must FAIL

**Chunk C — Backend GREEN (implementation)**
10. Create domain value objects (`IsochroneRequest`, `IsochroneResult`, `IsochroneBand`, `TravelMode`)
11. Implement `OtpClient` (WebClient, Resilience4j circuit breaker)
12. Implement `IsochroneCache` (Redis, key builder, TTL by mode)
13. Implement `IsochroneQueryService` (orchestrates OTP + cache)
14. Implement `IsochroneController` + DTOs
15. Add `application.yml` OTP config block
16. Run all backend tests → all GREEN

**Chunk D — Frontend RED (tests before implementation)**
17. Write `IsochroneService` spec — must FAIL
18. Write `IsochroneMapComponent` spec — must FAIL
19. Write `TravelModeSelectorComponent` spec — must FAIL
20. Write `CutoffSelectorComponent` spec — must FAIL
21. Write `IsochroneLegendComponent` spec — must FAIL
22. Write Playwright E2E test (`isochrone-map.spec.ts`) — must FAIL

**Chunk E — Frontend GREEN (implementation)**
23. Install `maplibre-gl` npm package
24. Create isochrone-map feature module + lazy route
25. Implement `IsochroneService` (HTTP calls)
26. Implement `IsochroneMapComponent` (MapLibre GL JS)
27. Implement `TravelModeSelectorComponent`
28. Implement `CutoffSelectorComponent`
29. Implement `IsochroneLegendComponent`
30. Implement `IsochronePageComponent` (assembles all components)
31. Wire up dark/light theme for map tiles
32. Add keyboard navigation + ARIA labels
33. Run all frontend tests → all GREEN

**Chunk F — Validation**
34. Run `scripts/validate-coverage.sh backend` → ≥ 80%
35. Run `scripts/validate-coverage.sh frontend/web` → ≥ 80%
36. Run Playwright E2E across Chromium, Firefox, WebKit
37. Run Spring Modulith boundary tests
38. Run `pre-commit run --all-files`

**Estimated tasks**: 38 numbered, dependency-ordered tasks
**Parallel opportunities** [P]: ADR writing (1+2 parallel), backend RED tests (5-9 parallel once domain objects exist), frontend RED tests (17-22 parallel), frontend GREEN components (26-29 parallel)

---

## Complexity Tracking

| Item | Justification |
|---|---|
| OTP sidecar container | External routing engine is industry standard; building a BFS transit graph is 10× more work and less accurate. ADR 0011 documents decision. |
| Two OTP-specific internal DTOs | OTP's API schema differs from Mobilispect's API schema. Serialisation boundary is unavoidable. |
| MapLibre GL JS | No existing map library in project. ADR 0012 required. Leaflet rejected (no WebGL; 60fps target at risk). |

---

## Progress Tracking

**Phase Status**:
- [x] Phase 0: Research complete (/plan command)
- [x] Phase 1: Design complete (/plan command)
- [x] Phase 2: Task planning approach described (/plan command)
- [x] Phase 3: Tasks generated (/tasks command)
- [ ] Phase 4: Implementation complete
- [ ] Phase 5: Validation passed

**Gate Status**:
- [x] Initial Constitution Check: PASS
- [x] Post-Design Constitution Check: PASS
- [x] All NEEDS CLARIFICATION resolved
- [x] Complexity deviations documented

---

*Based on Mobilispect Constitution v2.2.0 — See `CLAUDE.md` and `.specify/memory/constitution.md`*

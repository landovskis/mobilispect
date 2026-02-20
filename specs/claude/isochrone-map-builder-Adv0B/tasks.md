# Tasks: Isochrone Map Builder

**Branch**: `claude/isochrone-map-builder-Adv0B`
**Input**: `specs/claude/isochrone-map-builder-Adv0B/` — plan.md, research.md, data-model.md,
contracts/isochrone-api.yaml, quickstart.md
**Generated**: 2026-02-20

> **Default dateTime requirement**: When no `dateTime` is supplied by the caller,
> the backend resolves to **12:00:00 on the next weekday** (Monday–Friday) from the
> current server date. This ensures transit isochrones reflect midday weekday service,
> not weekend or off-peak schedules.

---

## Phase 3.1 — Setup & Foundation

- [ ] T001 [P] Write ADR `docs/adr/0011-otp-as-routing-engine.md` documenting the
  decision to use OpenTripPlanner 2.x as the routing/isochrone engine (sections:
  Title, Status, Context, Decision, Consequences, Alternatives — see constitution).

- [ ] T002 [P] Write ADR `docs/adr/0012-maplibre-gl-js-map-library.md` documenting
  the decision to use MapLibre GL JS as the frontend map library (same required
  sections; note Leaflet/OpenLayers/Google Maps alternatives considered).

- [ ] T003 Add `otp` service to `docker-compose.yml`. Use image
  `docker.io/opentripplanner/opentripplanner:2` with volume mount
  `./.otp/graphs:/var/opentripplanner` and port `8080:8080`. Add healthcheck:
  `curl -f http://localhost:8080/otp/routers || exit 1`.

- [ ] T004 Update `.gitignore` to exclude OTP graph build artefacts:
  `.otp/graphs/**/*.pbf`, `.otp/graphs/**/*.zip`, `.otp/graphs/**/*.obj`,
  `.otp/graphs/**/Graph.obj`. Create placeholder `.otp/graphs/default/.gitkeep`.

- [ ] T005 Add OTP configuration block to
  `backend/src/main/resources/application.yml`:
  ```yaml
  mobilispect:
    otp:
      base-url: ${OTP_BASE_URL:http://localhost:8080}
      timeout-seconds: ${OTP_TIMEOUT_SECONDS:10}
  ```
  Add corresponding env var defaults to `docker-compose.yml` for the backend service.

- [ ] T006 Install `maplibre-gl` npm package in `frontend/web`:
  `npm install maplibre-gl@^4` from the `frontend/web` directory.
  Verify it appears in `package.json` dependencies.

---

## Phase 3.2 — Backend Tests First (RED) ⚠️

**CRITICAL: Write every test below and confirm it FAILS before writing any
implementation. Run each with `./backend/gradlew -p backend test --tests '<ClassName>'`
and verify a red (compilation or assertion) failure.**

- [ ] T007 [P] Write contract test
  `backend/src/test/kotlin/com/mobilispect/backend/isochrone/api/IsochroneControllerContractTest.kt`
  using MockMvc. Cover:
  - `GET /api/v1/isochrones?lat=45.5017&lon=-73.5673` → 200, response has `origin`,
    `mode`, `bands`, `computedAt`, `cached` fields.
  - `GET /api/v1/isochrones?lat=45.5017&lon=-73.5673&mode=TRANSIT&cutoffMinutes=15,30`
    → 200, `bands` has 2 entries with correct `cutoffMinutes` values.
  - `GET /api/v1/isochrones?lat=45.5017&lon=-73.5673` with no `dateTime` param →
    200, resolved `dateTime` in response is **12:00:00 on a weekday** (Monday–Friday);
    assert `DayOfWeek.of(response.dateTime.dayOfWeek) in MONDAY..FRIDAY` and
    `response.dateTime.hour == 12`.
  - `GET /api/v1/isochrones?lat=999&lon=0` → 400 with `error: "INVALID_PARAMETERS"`.
  - `GET /api/v1/isochrones?lat=45.5&lon=-73.5&mode=BAD` → 400.
  - `GET /api/v1/isochrones?lat=45.5&lon=-73.5&cutoffMinutes=1,2,3,4,5,6,7,8,9`
    → 400 (too many cutoffs).
  - Simulate OTP unavailable (mock `IsochroneQueryService` throws
    `RoutingEngineUnavailableException`) → 502.
  - Simulate OTP timeout (mock throws `RoutingEngineTimeoutException`) → 504.
  Mock `IsochroneQueryService` via `@MockBean`.

- [ ] T008 [P] Write unit test
  `backend/src/test/kotlin/com/mobilispect/backend/isochrone/application/IsochroneQueryServiceTest.kt`
  using MockK. Cover:
  - Cache hit: when `IsochroneCache.get(request)` returns a result, `OtpClient` is
    never called and the cached result is returned with `cached = true`.
  - Cache miss: `IsochroneCache.get` returns null → `OtpClient.fetchIsochrone` called
    → result stored in cache → returned with `cached = false`.
  - OTP error: `OtpClient` throws → exception propagates (not swallowed).
  - `dateTime` null: service resolves to **next weekday at 12:00** before building
    the `IsochroneRequest`; assert the resolved `dateTime.hour == 12` and
    `dateTime.dayOfWeek` is Monday–Friday.

- [ ] T009 [P] Write unit test
  `backend/src/test/kotlin/com/mobilispect/backend/isochrone/internal/OtpClientTest.kt`
  using WireMock (`com.github.tomakehurst:wiremock-standalone`). Cover:
  - Happy path TRANSIT: stub OTP `GET /otp/traveltime/isochrone` returning a
    FeatureCollection with two features (`cutoffSec: 900`, `cutoffSec: 1800`).
    Assert `OtpClient.fetchIsochrone(request)` returns `IsochroneResult` with 2 bands
    with `cutoffMinutes` 15 and 30.
  - Happy path WALK: stub OTP returning one feature; assert mode maps to `"WALK"` in
    outgoing query.
  - Happy path BICYCLE: assert mode maps to `"BICYCLE"`.
  - TRANSIT mode: assert outgoing OTP query contains `mode=TRANSIT%2CWALK`
    (TRANSIT,WALK — access/egress walk required by OTP).
  - OTP returns 500: assert `RoutingEngineUnavailableException` thrown.
  - OTP takes > timeout: assert `RoutingEngineTimeoutException` thrown.
  - Circuit breaker open (Resilience4j): after N failures assert subsequent call is
    rejected immediately.

- [ ] T010 [P] Write unit test
  `backend/src/test/kotlin/com/mobilispect/backend/isochrone/internal/IsochroneCacheTest.kt`
  using `EmbeddedRedis` or a Testcontainers Redis container. Cover:
  - `set(result)` then `get(request)` returns equal result.
  - TRANSIT mode: TTL on the Redis key is ≤ 3600 s.
  - WALK mode: TTL is ≤ 86400 s.
  - BICYCLE mode: TTL is ≤ 86400 s.
  - Cache key includes rounded lat/lon (5 dp), sorted cutoffs, date, and hour.
  - Different requests produce different cache keys (assert `get` returns null for
    a request with a different origin).
  - Expired entry: `get` returns null after TTL elapses (use short TTL override in
    test).

- [ ] T011 Write integration test
  `backend/src/integrationTest/kotlin/com/mobilispect/backend/isochrone/IsochroneIntegrationTest.kt`
  using Testcontainers (PostgreSQL) + WireMock (OTP). Cover:
  - Full HTTP round-trip: `GET /api/v1/isochrones?lat=45.5017&lon=-73.5673` returns
    200 with a valid `IsochroneResponse` body.
  - Cache behaviour: second identical request returns `cached: true`.
  - OTP stub down → 502 response.
  - Default `dateTime` resolves to next weekday 12:00 when not supplied.
  - Spring Modulith module boundary: call
    `ApplicationModules.of(MobilispectApplication::class.java).verify()` inside the
    test to assert no illegal cross-module access.

---

## Phase 3.3 — Backend Domain Objects

*(Run after T007–T011 are confirmed RED)*

- [ ] T012 [P] Create
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/domain/TravelMode.kt`
  — Kotlin enum with values `TRANSIT`, `WALK`, `BICYCLE`. Add companion function
  `fun otpModeString(): String` returning `"TRANSIT,WALK"` for TRANSIT, `"WALK"`
  for WALK, `"BICYCLE"` for BICYCLE.

- [ ] T013 [P] Create
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/domain/IsochroneBand.kt`
  — data class with `cutoffMinutes: Int` and `geojson: String` (raw GeoJSON as
  string). No annotations needed.

- [ ] T014 Create
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/domain/IsochroneRequest.kt`
  — data class with `latitude: Double`, `longitude: Double`, `mode: TravelMode`,
  `cutoffMinutes: List<Int>`, `dateTime: LocalDateTime`. Add `init` block validating:
  latitude ∈ [-90, 90]; longitude ∈ [-180, 180]; cutoffMinutes non-empty, each ∈
  [1, 240], max 8 entries. Throw `IllegalArgumentException` with descriptive message
  on violation. *(Depends on T012)*

- [ ] T015 Create
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/domain/IsochroneResult.kt`
  — data class with `request: IsochroneRequest`, `bands: List<IsochroneBand>`,
  `computedAt: Instant`, `cached: Boolean`. *(Depends on T013, T014)*

- [ ] T016 Create
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/domain/NextWeekdayNoon.kt`
  — top-level function `fun nextWeekdayNoon(from: LocalDate = LocalDate.now()): LocalDateTime`
  that returns the next Monday–Friday at 12:00:00. If `from` is already a weekday,
  return `from` at 12:00. Otherwise advance to Monday. Used by both
  `IsochroneQueryService` and the controller to resolve a missing `dateTime`. Add
  unit tests in
  `backend/src/test/kotlin/com/mobilispect/backend/isochrone/domain/NextWeekdayNoonTest.kt`
  covering: weekday input, Saturday input (→ Monday), Sunday input (→ Monday),
  Friday input (→ Friday same day).

- [ ] T017 Create
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/IsochroneModule.kt` —
  Spring Modulith module marker (empty `@ApplicationModule`-annotated object or
  package-info style). Ensures the `isochrone` package is recognised as a Spring
  Modulith module with only `IsochroneQueryService` as its public API.

---

## Phase 3.4 — Backend Internal Layer

- [ ] T018 Create OTP internal DTO files in
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/internal/`:
  - `OtpIsochroneResponse.kt` — `@Serializable internal data class` with
    `type: String` and `features: List<OtpFeature>`.
  - `OtpFeature.kt` — `@Serializable internal data class` with `type: String`,
    `geometry: JsonObject`, `properties: OtpFeatureProperties`.
  - `OtpFeatureProperties.kt` — `@Serializable internal data class` with
    `cutoffSec: Int`.
  *(No `OtpIsochroneRequest` DTO needed — OTP accepts plain query params)*

- [ ] T019 Create
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/internal/RoutingEngineException.kt`
  — sealed class hierarchy:
  ```kotlin
  sealed class RoutingEngineException(message: String) : RuntimeException(message)
  class RoutingEngineUnavailableException(cause: Throwable? = null) : RoutingEngineException("OTP unavailable")
  class RoutingEngineTimeoutException(cause: Throwable? = null) : RoutingEngineException("OTP timed out")
  ```

- [ ] T020 Implement
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/internal/OtpClient.kt`
  — `@Component internal class OtpClient`. Inject `WebClient` (configured with
  `base-url` from `mobilispect.otp.base-url`) and `@Value` timeout. Call
  `GET /otp/traveltime/isochrone` with query params: `place={lat},{lon}`,
  `time={ISO datetime}`, `modes={TravelMode.otpModeString()}`,
  `cutoffSec={each cutoff * 60}`. Deserialise response to `OtpIsochroneResponse`.
  Map each feature to `IsochroneBand(cutoffMinutes = cutoffSec/60, geojson = ...)`.
  Wrap 5xx responses as `RoutingEngineUnavailableException`; wrap timeout as
  `RoutingEngineTimeoutException`. Wrap call in Resilience4j `CircuitBreaker`
  (name `"otp"`); add `CircuitBreakerConfig` bean with failure-rate threshold 50%,
  wait 30s. Record Micrometer metric `isochrone.otp.errors` on failure.
  *(Depends on T012, T013, T014, T018, T019)*

- [ ] T021 Implement
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/internal/IsochroneCache.kt`
  — `@Component internal class IsochroneCache`. Inject `StringRedisTemplate`.
  - `fun get(request: IsochroneRequest): IsochroneResult?` — build cache key
    `isochrone:{mode}:{lat5}:{lon5}:{cutoffs}:{date}:{hour}` (lat/lon rounded to
    5 dp; cutoffs sorted and hyphen-joined; date `YYYY-MM-DD`; hour `HH`); look up
    key; deserialise JSON if present; increment `isochrone.cache.hits` Micrometer
    counter; return null on miss and increment `isochrone.cache.misses`.
  - `fun set(result: IsochroneResult)` — serialise to JSON; store with TTL: 3600 s
    for TRANSIT, 86400 s for WALK/BICYCLE.
  *(Depends on T012, T014, T015)*

---

## Phase 3.5 — Backend Application & API Layer

- [ ] T022 Implement
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/application/IsochroneQueryService.kt`
  — `@Service class IsochroneQueryService`. Public API of the module. Inject
  `OtpClient` and `IsochroneCache` via constructor. Method
  `fun query(request: IsochroneRequest): IsochroneResult`:
  1. `IsochroneCache.get(request)` → if hit, return result with `cached = true`.
  2. `OtpClient.fetchIsochrone(request)` → on success store in cache and return
     with `cached = false`.
  Add SLF4J MDC keys: `isochrone.mode`, `isochrone.lat`, `isochrone.lon`,
  `isochrone.cutoffs`. Log at INFO: cache hit/miss, OTP call start/end with
  duration. Record Micrometer timer `isochrone.computation.duration` tagged by
  `mode` and `cached`.
  *(Depends on T020, T021)*

- [ ] T023 Create API DTOs in
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/api/dto/`:
  - `IsochroneRequestParams.kt` — data class with nullable fields matching query
    params (`lat: Double`, `lon: Double`, `mode: String? = "TRANSIT"`,
    `cutoffMinutes: String? = "15,30,45,60"`, `dateTime: String? = null`).
    Include a `fun toDomain(): IsochroneRequest` that: parses `cutoffMinutes` CSV;
    resolves `dateTime` to `nextWeekdayNoon()` if null; converts `mode` String to
    `TravelMode` enum; constructs `IsochroneRequest` (validation fires in `init`).
  - `IsochroneResponseDto.kt` — data class with `origin`, `mode`, `dateTime`,
    `bands: List<IsochroneBandDto>`, `computedAt`, `cached`.
  - `IsochroneBandDto.kt` — data class with `cutoffMinutes`, `color` (hex from
    colour table), `geojson` (deserialised from raw JSON string to `Any`).
  - `ErrorResponseDto.kt` — data class with `error: String` and `message: String`.
  Include colour mapping:
  ```kotlin
  private val BAND_COLORS = mapOf(15 to "#1a9641", 30 to "#a6d96a", 45 to "#ffffbf",
      60 to "#fdae61", 90 to "#d7191c")
  fun colorFor(cutoffMinutes: Int): String =
      BAND_COLORS[cutoffMinutes] ?: BAND_COLORS.entries.minByOrNull {
          abs(it.key - cutoffMinutes) }!!.value
  ```
  *(Depends on T014, T015, T016)*

- [ ] T024 Implement
  `backend/src/main/kotlin/com/mobilispect/backend/isochrone/api/IsochroneController.kt`
  — `@RestController @RequestMapping("/api/v1/isochrones")`. Inject
  `IsochroneQueryService`. Single handler:
  ```kotlin
  @GetMapping fun get(@ModelAttribute params: IsochroneRequestParams): ResponseEntity<*>
  ```
  - Call `params.toDomain()` — catch `IllegalArgumentException` → 400 with
    `ErrorResponseDto("INVALID_PARAMETERS", ex.message)`.
  - Call `IsochroneQueryService.query(request)`.
  - Catch `RoutingEngineUnavailableException` → 502 with
    `ErrorResponseDto("ROUTING_ENGINE_UNAVAILABLE", ...)`.
  - Catch `RoutingEngineTimeoutException` → 504 with
    `ErrorResponseDto("ROUTING_ENGINE_TIMEOUT", ...)`.
  - Map `IsochroneResult` → `IsochroneResponseDto` → 200.
  *(Depends on T022, T023)*

- [ ] T025 Run all backend unit and contract tests:
  ```bash
  ./backend/gradlew -p backend test -x integrationTest
  ```
  All T007–T011 tests must now be GREEN. Fix any compilation or logic errors
  before proceeding. Then run:
  ```bash
  ./backend/gradlew ktlintFormat
  ./backend/gradlew detekt
  ```
  *(Depends on T024)*

---

## Phase 3.6 — Frontend Tests First (RED) ⚠️

**Write each spec file and confirm it FAILS (`npm test -- --watchAll=false
--testPathPattern=isochrone`) before writing any implementation.**

- [ ] T026 [P] Write
  `frontend/web/src/app/isochrone-map/services/isochrone.service.spec.ts`.
  Cover:
  - `getIsochrones({ lat, lon, mode: 'TRANSIT' })` makes `GET /api/v1/isochrones`
    with correct query params and returns an `Observable<IsochroneResponse>`.
  - When no `dateTime` is passed, the service omits the param (backend resolves
    to weekday noon).
  - HTTP 400 → observable errors with a typed error.
  - HTTP 502 → observable errors.
  Use `HttpClientTestingModule` and `HttpTestingController`.

- [ ] T027 [P] Write
  `frontend/web/src/app/isochrone-map/components/isochrone-map/isochrone-map.component.spec.ts`.
  Cover:
  - Component renders a `<div>` with `id="map"`.
  - On `@Input() bands` change, calls `addIsochroneLayers()` (spy on method).
  - On map click event, emits `originSelected` `EventEmitter` with `{lat, lon}`.
  - When `loading` input is true, a spinner element is present in the DOM.
  Mock MapLibre GL JS: `jest.mock('maplibre-gl', () => ({ Map: jest.fn(...) }))`.

- [ ] T028 [P] Write
  `frontend/web/src/app/isochrone-map/components/travel-mode-selector/travel-mode-selector.component.spec.ts`.
  Cover:
  - Renders three buttons/tabs labelled "Transit", "Walk", "Bike".
  - Clicking "Walk" emits `modeChange` output with value `'WALK'`.
  - Default selected mode is `'TRANSIT'`.
  - All buttons are keyboard-reachable (have `tabindex` or are native `<button>`).

- [ ] T029 [P] Write
  `frontend/web/src/app/isochrone-map/components/cutoff-selector/cutoff-selector.component.spec.ts`.
  Cover:
  - Renders checkboxes for 15, 30, 45, 60 min; all checked by default.
  - Unchecking 30 min emits `cutoffsChange` output without 30 in the array.
  - Emits sorted ascending array.
  - At least one cutoff must remain checked (assert deselecting all re-checks last
    one).

- [ ] T030 [P] Write
  `frontend/web/src/app/isochrone-map/components/isochrone-legend/isochrone-legend.component.spec.ts`.
  Cover:
  - Renders a legend entry for each band in `@Input() bands`.
  - Each entry shows the colour swatch and the label "≤ N min".
  - Has `role="img"` and `aria-label` for screen reader description.
  - Updates when `bands` input changes.

- [ ] T031 [P] Write Playwright E2E test
  `frontend/web/e2e/isochrone-map.spec.ts`. Cover:
  - Navigate to `/isochrone`; assert map container is visible.
  - Click on the map; assert loading spinner appears, then polygons render
    (wait for `[data-testid="isochrone-band"]` elements).
  - Switch to "Walk" mode; assert bands update.
  - Uncheck "30 min" cutoff; assert 30-min band disappears.
  - Toggle dark mode; assert map tile URL changes to dark style.
  - Keyboard: Tab to travel mode selector; use arrow keys; assert selected mode
    changes via keyboard only.
  - Accessibility: run `@axe-core/playwright` check on the page; assert 0 violations.
  Use `page.route('**/api/v1/isochrones**', ...)` to stub backend responses with
  fixture GeoJSON data — tests must not require a live backend.

---

## Phase 3.7 — Frontend Module & Models

*(Run after T026–T031 are confirmed RED)*

- [ ] T032 Create the `isochrone-map` feature module structure:
  - `frontend/web/src/app/isochrone-map/isochrone-map.routes.ts` — exports
    `ISOCHRONE_MAP_ROUTES: Routes` with a single route `path: ''` pointing to
    `IsochronePageComponent`, loaded lazily.
  - Add to `frontend/web/src/app/app.routes.ts`:
    `{ path: 'isochrone', loadChildren: () => import('./isochrone-map/isochrone-map.routes') }`.
  - Add "Isochrone Map" link to the app shell navigation
    (`frontend/web/src/app/shared/components/app-shell.component.ts`).

- [ ] T033 [P] Create model files:
  - `frontend/web/src/app/isochrone-map/models/isochrone-request.ts` — export
    `IsochroneRequest` interface and `TravelMode` type as defined in data-model.md.
  - `frontend/web/src/app/isochrone-map/models/isochrone-response.ts` — export
    `IsochroneResponse`, `IsochroneBand`, `GeoJsonFeature`, `GeoJsonGeometry`
    interfaces as defined in data-model.md.

---

## Phase 3.8 — Frontend Service

- [ ] T034 Implement
  `frontend/web/src/app/isochrone-map/services/isochrone.service.ts` —
  `@Injectable({ providedIn: 'root' }) class IsochroneService`. Inject `HttpClient`.
  Method `getIsochrones(req: IsochroneRequest): Observable<IsochroneResponse>`:
  - Build `HttpParams` from `req` fields; omit `dateTime` if not supplied (backend
    defaults to next weekday noon).
  - Call `this.http.get<IsochroneResponse>('/api/v1/isochrones', { params })`.
  *(Depends on T033)*

---

## Phase 3.9 — Frontend Components

- [ ] T035 [P] Implement
  `frontend/web/src/app/isochrone-map/components/travel-mode-selector/travel-mode-selector.component.ts`
  and `.html`. Use Angular Material `mat-button-toggle-group` with three toggles:
  Transit / Walk / Bike. `@Input() selectedMode: TravelMode = 'TRANSIT'`.
  `@Output() modeChange = new EventEmitter<TravelMode>()`. Add `role="radiogroup"`,
  `aria-label="Travel mode"` to the group; each toggle has visible label text (no
  icon-only).

- [ ] T036 [P] Implement
  `frontend/web/src/app/isochrone-map/components/cutoff-selector/cutoff-selector.component.ts`
  and `.html`. Render a `mat-checkbox` for each of [15, 30, 45, 60] minutes.
  `@Input() selectedCutoffs: number[] = [15, 30, 45, 60]`.
  `@Output() cutoffsChange = new EventEmitter<number[]>()`. Enforce minimum one
  selection (re-check the last checkbox if user tries to deselect all). Each
  checkbox labelled "N min".

- [ ] T037 [P] Implement
  `frontend/web/src/app/isochrone-map/components/isochrone-legend/isochrone-legend.component.ts`
  and `.html`. `@Input() bands: IsochroneBand[]`. Render a vertical list of colour
  swatches (`background-color: band.color`, `opacity: 0.65`) with labels "≤ N min".
  Add `role="img"` and `aria-label="Isochrone legend: travel time bands"` to the
  container.

- [ ] T038 Implement
  `frontend/web/src/app/isochrone-map/components/isochrone-map/isochrone-map.component.ts`
  and `.html`. Use `ViewChild` on `<div #mapContainer>`.
  - In `ngAfterViewInit`: initialise `maplibregl.Map` with OpenFreeMap Liberty style
    (`https://tiles.openfreemap.org/styles/liberty`).
  - `@Input() bands: IsochroneBand[]`: on change, call `updateIsochroneLayers()` which
    removes all existing `isochrone-*` layers/sources from the map, then adds one
    `geojson` source + `fill` layer per band (fill-color from `band.color`,
    fill-opacity 0.35).
  - `@Input() loading: boolean`: show/hide a spinner overlay (`data-testid="loading"`).
  - `@Input() darkMode: boolean`: on change, switch map style between
    OpenFreeMap Liberty (light) and `https://tiles.openfreemap.org/styles/dark`
    (dark) while preserving current isochrone layers.
  - On map `click` event: emit `@Output() originSelected = new EventEmitter<{lat:
    number; lon: number}>()` with the clicked coordinates.
  - Add `data-testid="isochrone-band"` attribute to each polygon layer via feature
    state or overlay `<div>` (Playwright relies on this selector).
  - Call `map.remove()` in `ngOnDestroy`.
  *(Depends on T033)*

- [ ] T039 Implement
  `frontend/web/src/app/isochrone-map/pages/isochrone-page/isochrone-page.component.ts`
  and `.html`. Compose all child components. State (RxJS):
  - `origin$: BehaviorSubject<{lat: number; lon: number} | null>`
  - `mode$: BehaviorSubject<TravelMode>` (default `'TRANSIT'`)
  - `cutoffs$: BehaviorSubject<number[]>` (default `[15, 30, 45, 60]`)
  - `isochrones$`: derived via `combineLatest([origin$, mode$, cutoffs$]).pipe(
      filter(([o]) => o !== null),
      switchMap(([o, m, c]) => this.isochroneService.getIsochrones({ lat: o!.lat,
        lon: o!.lon, mode: m, cutoffMinutes: c })),
      share())`
  - `loading$`: `true` from switchMap start until `isochrones$` emits.
  - Pass `bands` from `isochrones$` to `<app-isochrone-map>` and
    `<app-isochrone-legend>`.
  - Use `async` pipe throughout (no manual subscriptions).
  - Inject `ThemeService` (existing) or listen to `@HostListener` on body class; pass
    `darkMode` boolean to `<app-isochrone-map>`.
  *(Depends on T034, T035, T036, T037, T038)*

- [ ] T040 Run all frontend unit tests and fix failures:
  ```bash
  cd frontend/web && npm test -- --watchAll=false --testPathPattern=isochrone
  ```
  All T026–T030 specs must be GREEN. Then format and lint:
  ```bash
  npm run format
  npm run lint -- --fix
  npm run ng lint
  ```
  *(Depends on T039)*

---

## Phase 3.10 — Validation

- [ ] T041 Validate backend test coverage meets the ≥ 80% constitutional threshold:
  ```bash
  ./scripts/validate-coverage.sh backend
  ```
  If below threshold, add targeted unit tests to the weakest area (most likely
  `IsochroneCache` or `OtpClient`) and re-run.

- [ ] T042 Validate frontend test coverage meets the ≥ 80% constitutional threshold:
  ```bash
  ./scripts/validate-coverage.sh frontend/web
  ```
  Add tests if below threshold.

- [ ] T043 Run Playwright E2E tests across all three browsers:
  ```bash
  cd frontend/web && npm run e2e -- --grep="isochrone"
  ```
  Tests run against Chromium, Firefox, and WebKit (configured in `playwright.config.ts`).
  All must pass. Fix any failures — do not skip browsers.

- [ ] T044 Run Spring Modulith boundary verification:
  ```bash
  ./backend/gradlew -p backend test --tests '*ModularityTests'
  ```
  Assert no illegal cross-module access introduced by the new `isochrone` module.
  If `ModularityTests` doesn't exist yet, create it at
  `backend/src/test/kotlin/com/mobilispect/backend/ModularityTests.kt`.

- [ ] T045 Run all pre-commit hooks on all files:
  ```bash
  pre-commit run --all-files
  ```
  All hooks must pass (ktlint, detekt, Prettier, ESLint, ng lint, test execution,
  coverage validation). Fix any remaining issues.

---

## Dependency Graph

```
T001,T002 (ADRs)           ← no deps, [P] together
T003,T004,T005,T006        ← no deps, [P] together
   ↓
T007,T008,T009,T010        ← RED backend tests, [P] together
T011                       ← RED integration test (after T007-T010)
   ↓
T012,T013                  ← domain enums/value objects, [P]
T014                       ← depends on T012
T015                       ← depends on T013,T014
T016                       ← depends on T012 (TravelMode)
T017                       ← no domain dep
T018,T019                  ← internal layer setup, [P]
T020                       ← depends on T012,T013,T014,T018,T019
T021                       ← depends on T012,T014,T015
T022                       ← depends on T020,T021
T023                       ← depends on T022
T024                       ← depends on T023 (DTOs and controller)
T025 (backend GREEN)       ← depends on T024
   ↓
T026,T027,T028,T029,       ← RED frontend tests, [P] together
T030,T031
   ↓
T032                       ← module scaffold
T033                       ← model files, [P] with T032
T034                       ← service, depends on T033
T035,T036,T037             ← leaf components, [P]
T038                       ← map component, depends on T033
T039                       ← page, depends on T034-T038
T040 (frontend GREEN)      ← depends on T039
   ↓
T041,T042,T043,T044,T045   ← validation, sequential order shown
```

---

## Parallel Execution Examples

### Chunk 1 — ADRs (T001 + T002)
```
Task agent A: "Write docs/adr/0011-otp-as-routing-engine.md"
Task agent B: "Write docs/adr/0012-maplibre-gl-js-map-library.md"
```

### Chunk 2 — Infrastructure (T003 + T004 + T005 + T006)
```
Task agent A: "Add OTP sidecar to docker-compose.yml (T003)"
Task agent B: "Update .gitignore for .otp/ artefacts (T004)"
Task agent C: "Add OTP config block to application.yml (T005)"
Task agent D: "Install maplibre-gl npm package (T006)"
```

### Chunk 3 — Backend RED tests (T007 + T008 + T009 + T010)
```
Task agent A: "Write IsochroneControllerContractTest — confirm FAIL"
Task agent B: "Write IsochroneQueryServiceTest — confirm FAIL"
Task agent C: "Write OtpClientTest with WireMock — confirm FAIL"
Task agent D: "Write IsochroneCacheTest — confirm FAIL"
```

### Chunk 4 — Domain objects (T012 + T013)
```
Task agent A: "Create TravelMode.kt"
Task agent B: "Create IsochroneBand.kt"
```

### Chunk 5 — Frontend RED tests (T026–T031)
```
Task agent A: "Write IsochroneService spec — confirm FAIL"
Task agent B: "Write IsochroneMapComponent spec — confirm FAIL"
Task agent C: "Write TravelModeSelectorComponent spec — confirm FAIL"
Task agent D: "Write CutoffSelectorComponent spec — confirm FAIL"
(T030, T031 sequential after agent capacity)
```

### Chunk 6 — Frontend leaf components (T035 + T036 + T037)
```
Task agent A: "Implement TravelModeSelectorComponent"
Task agent B: "Implement CutoffSelectorComponent"
Task agent C: "Implement IsochroneLegendComponent"
```

---

## Validation Checklist
*(Gate before marking tasks complete)*

- [x] All contracts have corresponding tests — `isochrone-api.yaml` → T007 covers
  all 8 response scenarios (200, 400×3, 502, 504, cache, weekday-noon default)
- [x] All entities have model tasks — IsochroneRequest (T014), IsochroneResult (T015),
  IsochroneBand (T013), TravelMode (T012), NextWeekdayNoon (T016), OTP DTOs (T018),
  TS models (T033)
- [x] All tests come before implementation — T007–T011 (RED) before T012–T025 (GREEN);
  T026–T031 (RED) before T032–T040 (GREEN)
- [x] Parallel tasks are truly independent — verified by file-path inspection above
- [x] Each task specifies exact file path
- [x] No [P] task modifies the same file as another [P] task
- [x] `dateTime` default (next weekday 12:00) covered in: T007 (contract), T008
  (service), T011 (integration), T016 (implementation), T023 (DTO), T026 (frontend)

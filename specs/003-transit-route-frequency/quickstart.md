# Quickstart: Transit Route Frequency Analysis

**Feature**: 003-transit-route-frequency
**Date**: 2025-11-27
**Target Audience**: Developers implementing this feature

## Overview

This quickstart guide provides step-by-step instructions for implementing the transit route frequency analysis feature following Test-Driven Development (TDD) principles and constitutional requirements.

## Prerequisites

- JDK 25+ installed
- Kotlin 2.0+ configured
- PostgreSQL 17 running locally (or via Docker)
- Redis 8.2 running locally (or via Docker)
- Node.js 20+ for frontend development
- Angular CLI 19 installed globally
- Git repository cloned

## Development Environment Setup

### 1. Start Local Dependencies

```bash
# Start PostgreSQL and Redis using Docker Compose
cd .devcontainer
docker-compose up -d postgres redis

# Verify services are running
docker ps | grep -E 'postgres|redis'
```

### 2. Configure Application

Create `backend/src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mobilispect
    username: mobilispect
    password: dev_password
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  data:
    redis:
      host: localhost
      port: 6379

transitland:
  api:
    baseUrl: https://transit.land/api/v2/rest
    apiKey: ${TRANSITLAND_API_KEY}

grafana:
  cloud:
    apiKey: ${GRAFANA_API_KEY}
    endpoint: ${GRAFANA_ENDPOINT}
```

Set environment variables:

```bash
export TRANSITLAND_API_KEY="your_api_key_here"
export GRAFANA_API_KEY="your_grafana_key_here"
export GRAFANA_ENDPOINT="https://your-instance.grafana.net"
```

### 3. Run Database Migrations

```bash
cd backend
./gradlew flywayMigrate
```

## Implementation Phases

### Phase 1: Domain Model (TDD)

**Goal**: Implement core domain entities with value classes

**Steps**:

1. **Write Entity Tests First** (TDD):

```kotlin
// backend/src/test/kotlin/com/mobilispect/transitanalysis/domain/model/RouteVariantTest.kt
class RouteVariantTest {
    @Test
    fun `should generate stable hash for identical stop patterns`() {
        val stops1 = listOf("stop1", "stop2", "stop3")
        val stops2 = listOf("stop1", "stop2", "stop3")

        val hash1 = VariantHash.from(stops1)
        val hash2 = VariantHash.from(stops2)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `should generate different hash for different stop patterns`() {
        val stops1 = listOf("stop1", "stop2", "stop3")
        val stops2 = listOf("stop1", "stop3", "stop2")

        val hash1 = VariantHash.from(stops1)
        val hash2 = VariantHash.from(stops2)

        assertNotEquals(hash1, hash2)
    }
}
```

2. **Implement Value Classes**:

```kotlin
// backend/src/main/kotlin/com/mobilispect/transitanalysis/domain/model/valueobjects/VariantHash.kt
@JvmInline
value class VariantHash(val value: String) {
    init {
        require(value.matches(Regex("^[a-f0-9]{64}$"))) {
            "VariantHash must be 64-character hex string"
        }
    }

    companion object {
        fun from(stopIds: List<String>): VariantHash {
            val concatenated = stopIds.joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(concatenated.toByteArray())
            return VariantHash(hashBytes.toHexString())
        }
    }
}
```

3. **Implement Domain Entities** (see data-model.md for complete definitions)

4. **Run Tests**:

```bash
./gradlew test --tests "*RouteVariantTest"
```

### Phase 2: Repository Layer (TDD)

**Goal**: Implement Spring Data JPA repositories

**Steps**:

1. **Write Repository Tests**:

```kotlin
// backend/src/test/kotlin/com/mobilispect/transitanalysis/domain/repository/RouteVariantRepositoryTest.kt
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RouteVariantRepositoryTest {
    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:17-alpine")

    @Autowired
    lateinit var routeVariantRepository: RouteVariantRepository

    @Test
    fun `should find variants by route id`() {
        // Given
        val route = createTestRoute()
        val variant1 = createTestVariant(route)
        val variant2 = createTestVariant(route)
        routeVariantRepository.saveAll(listOf(variant1, variant2))

        // When
        val found = routeVariantRepository.findByRouteId(route.id)

        // Then
        assertEquals(2, found.size)
    }
}
```

2. **Implement Repositories**:

```kotlin
// backend/src/main/kotlin/com/mobilispect/transitanalysis/domain/repository/RouteVariantRepository.kt
interface RouteVariantRepository : JpaRepository<RouteVariant, VariantHash> {
    fun findByRouteId(routeId: RouteId): List<RouteVariant>
    fun findByRouteIdAndActive(routeId: RouteId, active: Boolean): List<RouteVariant>
}
```

3. **Run Integration Tests**:

```bash
./gradlew integrationTest
```

### Phase 3: Domain Services (TDD)

**Goal**: Implement business logic for variant identification and frequency calculation

**Steps**:

1. **Write Service Tests**:

```kotlin
// backend/src/test/kotlin/com/mobilispect/transitanalysis/domain/service/VariantIdentificationServiceTest.kt
@SpringBootTest
class VariantIdentificationServiceTest {
    @Autowired
    lateinit var service: VariantIdentificationService

    @Test
    fun `should identify variants from GTFS trips`() {
        // Given
        val trips = loadTestGtfsTrips()

        // When
        val variants = service.identifyVariants(trips)

        // Then
        assertTrue(variants.size >= 2) // At least inbound/outbound
        variants.forEach { variant ->
            assertTrue(variant.stopCount >= 2)
            assertNotNull(variant.id.value)
        }
    }
}
```

2. **Implement Services** (see research.md for algorithms):

```kotlin
// backend/src/main/kotlin/com/mobilispect/transitanalysis/domain/service/VariantIdentificationService.kt
@Service
class VariantIdentificationService {
    fun identifyVariants(route: Route, trips: List<GtfsTrip>): List<RouteVariant> {
        val variantMap = mutableMapOf<VariantHash, RouteVariant>()

        trips.forEach { trip ->
            val stopPattern = trip.stopTimes
                .sortedBy { it.stopSequence }
                .map { it.stopId }

            val hash = VariantHash.from(stopPattern)

            if (!variantMap.containsKey(hash)) {
                variantMap[hash] = RouteVariant(
                    id = hash,
                    route = route,
                    directionId = trip.directionId,
                    headsign = trip.tripHeadsign,
                    stopPattern = stopPattern.joinToString("|"),
                    stopCount = stopPattern.size,
                    // ... other fields
                )
            }
        }

        return variantMap.values.toList()
    }
}
```

3. **Run Service Tests**:

```bash
./gradlew test --tests "*Service*"
```

### Phase 4: Infrastructure Layer

**Goal**: Implement Transitland API client and GTFS parser

**Steps**:

1. **Configure Retrofit for Transitland API**:

```kotlin
// backend/src/main/kotlin/com/mobilispect/transitanalysis/infrastructure/transitland/TransitlandConfig.kt
@Configuration
class TransitlandConfig {
    @Bean
    fun transitlandClient(
        @Value("\${transitland.api.baseUrl}") baseUrl: String,
        @Value("\${transitland.api.apiKey}") apiKey: String
    ): TransitlandClient {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("apikey", apiKey)
                    .build()
                chain.proceed(request)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(TransitlandClient::class.java)
    }
}
```

2. **Implement GTFS Parser**:

```kotlin
// backend/src/main/kotlin/com/mobilispect/transitanalysis/infrastructure/gtfs/GtfsParser.kt
@Component
class GtfsParser {
    fun parseFeed(feedFile: File): GtfsFeed {
        val reader = GtfsReader()
        reader.setInputLocation(feedFile)
        val dao = reader.run()

        return GtfsFeed(
            agency = dao.getAllAgencies(),
            routes = dao.getAllRoutes(),
            trips = dao.getAllTrips(),
            stopTimes = dao.getAllStopTimes(),
            // ... other data
        )
    }
}
```

### Phase 5: Application Services & REST Controllers

**Goal**: Expose REST APIs with contract testing

**Steps**:

1. **Write Controller Tests** (Contract Tests):

```kotlin
// backend/src/test/kotlin/com/mobilispect/transitanalysis/api/FrequencyControllerTest.kt
@WebMvcTest(FrequencyController::class)
@AutoConfigureRestDocs
class FrequencyControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var frequencyQueryService: FrequencyQueryService

    @Test
    fun `GET variant frequency should return 200 with frequency data`() {
        // Given
        val variantHash = "abc123..."
        val frequency = createTestFrequency()
        whenever(frequencyQueryService.getFrequency(any(), any())).thenReturn(frequency)

        // When & Then
        mockMvc.perform(get("/api/v1/variants/{hash}/frequency", variantHash))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.variantId").value(variantHash))
            .andDo(document("get-variant-frequency"))
    }
}
```

2. **Implement Controllers**:

```kotlin
// backend/src/main/kotlin/com/mobilispect/transitanalysis/api/FrequencyController.kt
@RestController
@RequestMapping("/api/v1")
class FrequencyController(
    private val frequencyQueryService: FrequencyQueryService
) {
    @GetMapping("/variants/{variantHash}/frequency")
    fun getVariantFrequency(
        @PathVariable variantHash: String,
        @RequestParam(required = false) date: LocalDate?,
        @RequestParam(required = false) timePeriod: TimePeriod?
    ): FrequencyResponse {
        val serviceDate = date ?: LocalDate.now()
        return frequencyQueryService.getFrequency(VariantHash(variantHash), serviceDate, timePeriod)
    }
}
```

3. **Run Contract Tests**:

```bash
./gradlew contractTest
```

### Phase 6: Frontend Implementation

**Goal**: Build responsive Angular UI with accessibility

**Steps**:

1. **Generate Feature Module**:

```bash
cd frontend
ng generate module transit-frequency --routing
ng generate component transit-frequency/pages/region-list
ng generate component transit-frequency/pages/route-frequency
ng generate component transit-frequency/components/frequency-chart
```

2. **Implement Services**:

```typescript
// frontend/src/app/transit-frequency/services/frequency.service.ts
@Injectable({ providedIn: 'root' })
export class FrequencyService {
  private apiUrl = '/api/v1';

  constructor(private http: HttpClient) {}

  getRouteFrequency(routeId: string, date?: string): Observable<RouteFrequencySummary> {
    const params = date ? { date } : {};
    return this.http.get<RouteFrequencySummary>(
      `${this.apiUrl}/routes/${routeId}/frequency/summary`,
      { params }
    );
  }
}
```

3. **Write Component Tests**:

```typescript
// frontend/src/app/transit-frequency/pages/route-frequency/route-frequency.component.spec.ts
describe('RouteFrequencyComponent', () => {
  let component: RouteFrequencyComponent;
  let fixture: ComponentFixture<RouteFrequencyComponent>;
  let frequencyService: jasmine.SpyObj<FrequencyService>;

  beforeEach(() => {
    const spy = jasmine.createSpyObj('FrequencyService', ['getRouteFrequency']);

    TestBed.configureTestingModule({
      declarations: [RouteFrequencyComponent],
      providers: [{ provide: FrequencyService, useValue: spy }]
    });

    fixture = TestBed.createComponent(RouteFrequencyComponent);
    component = fixture.componentInstance;
    frequencyService = TestBed.inject(FrequencyService) as jasmine.SpyObj<FrequencyService>;
  });

  it('should display frequency data', () => {
    const mockData = createMockFrequency();
    frequencyService.getRouteFrequency.and.returnValue(of(mockData));

    fixture.detectChanges();

    expect(component.frequency).toEqual(mockData);
  });
});
```

4. **Run Frontend Tests**:

```bash
npm test
```

### Phase 7: End-to-End Testing

**Goal**: Playwright tests for complete user journeys

**Steps**:

1. **Write E2E Tests**:

```typescript
// e2e/transit-frequency.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Transit Frequency Analysis', () => {
  test('should display frequency data for route', async ({ page }) => {
    // Navigate to region list
    await page.goto('/transit-frequency/regions');

    // Select a region
    await page.click('text=San Francisco Bay Area');

    // Select an agency
    await page.click('text=SF Muni');

    // Select a route
    await page.click('text=38 Geary');

    // Verify frequency data is displayed
    await expect(page.locator('[data-testid="frequency-chart"]')).toBeVisible();
    await expect(page.locator('text=Weekday AM Peak')).toBeVisible();
    await expect(page.locator('[data-testid="average-headway"]')).toContainText('min');
  });

  test('should show common sections', async ({ page }) => {
    // ... test common section display
  });
});
```

2. **Run E2E Tests**:

```bash
npx playwright test
```

## Module Testing (Spring Modulith)

**Verify Module Boundaries**:

```kotlin
// backend/src/test/kotlin/com/mobilispect/transitanalysis/module/TransitAnalysisModuleTest.kt
@ModuleTest
class TransitAnalysisModuleTest {
    @Test
    fun `module structure should be valid`() {
        val modules = ApplicationModules.of(MobilispectApplication::class.java)
        modules.verify()
    }

    @Test
    fun `module should not have circular dependencies`() {
        val modules = ApplicationModules.of(MobilispectApplication::class.java)
        modules.forEach { module ->
            assertFalse(module.getBootstrapDependencies().any { it == module })
        }
    }
}
```

## Running the Application

### Backend

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

Verify: http://localhost:8080/actuator/health

### Frontend

```bash
cd frontend
npm start
```

Verify: http://localhost:4200

## Monitoring & Observability

### View Metrics

Micrometer metrics exposed at: http://localhost:8080/actuator/metrics

Example metrics:
- `feed.processing.duration`
- `frequency.calculation.duration`
- `frequency.query.cache.hits`

### View Traces

Distributed traces sent to Grafana Cloud (configured in application.yml)

### View Logs

Structured JSON logs written to `backend-dev.log`:

```bash
tail -f backend-dev.log | jq '.'
```

## Common Issues & Troubleshooting

### Issue: Database Migration Fails

**Solution**: Ensure PostgreSQL 17 is running and connection details are correct

```bash
docker ps | grep postgres
psql -h localhost -U mobilispect -d mobilispect
```

### Issue: Redis Connection Error

**Solution**: Start Redis container

```bash
docker start mobilispect-redis
redis-cli ping
```

### Issue: Transitland API Rate Limit

**Solution**: Implement exponential backoff (already in TransitlandClient)

### Issue: Module Boundary Violation

**Solution**: Check Spring Modulith verification errors

```bash
./gradlew test --tests "*ModuleTest*"
```

## Next Steps

1. Review ADRs in `docs/adr/` for architectural decisions
2. Follow TDD workflow for all new features
3. Ensure 80%+ test coverage before pushing code
4. Run pre-commit hooks: `pre-commit run --all-files`
5. Create pull request with comprehensive description

## Resources

- [Spring Modulith Documentation](https://spring.io/projects/spring-modulith)
- [OneBusAway GTFS Library](https://github.com/OneBusAway/onebusaway-gtfs-modules)
- [Transitland API Docs](https://www.transit.land/documentation)
- [Playwright Testing Guide](https://playwright.dev/docs/intro)
- [Project Constitution](../.specify/memory/constitution.md)

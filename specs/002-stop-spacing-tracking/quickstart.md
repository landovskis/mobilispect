# Quickstart: Average Stop Spacing Tracking

**Feature Branch**: `002-stop-spacing-tracking`
**Date**: 2025-11-23

## Prerequisites

Before starting development:

1. **Local Environment**
   - JDK 21+
   - Docker & Docker Compose (for PostgreSQL 17 and Redis 8.2)
   - Node.js 20+ and npm (for Angular frontend)

2. **Dependencies**
   - Backend services running (`docker-compose up -d`)
   - Database migrations applied (`./gradlew flywayMigrate`)

3. **ADR Created**
   - Create ADR for GeographicLib library choice before implementation

## Quick Setup

```bash
# 1. Switch to feature branch
git checkout 002-stop-spacing-tracking

# 2. Start infrastructure
docker-compose up -d postgres redis

# 3. Add GeographicLib dependency
# Edit backend/gradle/libs.versions.toml:
# geographiclib = { module = "net.sf.geographiclib:geographiclib-java", version = "2.3" }

# Edit backend/build.gradle.kts:
# implementation(libs.geographiclib)

# 4. Run database migrations
cd backend && ./gradlew flywayMigrate

# 5. Verify setup
./gradlew test --tests "*StopSpacing*"
```

## Module Structure

Create the new `stopspacing` module in the backend:

```
backend/src/main/kotlin/com/mobilispect/backend/stopspacing/
├── StopSpacingModule.kt          # Module marker annotation
├── model/
│   ├── StopSpacingStatistics.kt  # Domain model
│   ├── StopSpacingStatisticsEntity.kt  # JPA entity
│   ├── ServiceType.kt            # Enum
│   ├── ClassificationThreshold.kt
│   └── ids/
│       └── StopSpacingId.kt      # Value class
├── repository/
│   ├── StopSpacingRepository.kt
│   └── ClassificationThresholdRepository.kt
├── service/
│   ├── StopSpacingCalculationService.kt
│   ├── ServiceTypeClassifier.kt
│   ├── GeodesicDistanceCalculator.kt
│   └── StopSpacingAggregationService.kt
├── event/
│   └── FeedImportCompletedListener.kt
└── controller/
    ├── RouteStopSpacingController.kt
    ├── AgencyComparisonController.kt
    └── RegionalComparisonController.kt
```

## Key Implementation Steps

### 1. Create Module Marker

```kotlin
// StopSpacingModule.kt
package com.mobilispect.backend.stopspacing

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    displayName = "Stop Spacing Statistics",
    allowedDependencies = ["feed", "schedule", "infrastructure"]
)
class StopSpacingModule
```

### 2. Implement Geodesic Distance Calculator

```kotlin
// GeodesicDistanceCalculator.kt
package com.mobilispect.backend.stopspacing.service

import net.sf.geographiclib.Geodesic
import net.sf.geographiclib.GeodesicMask
import org.springframework.stereotype.Component

@Component
class GeodesicDistanceCalculator {

    fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val result = Geodesic.WGS84.inverse(
            lat1, lon1, lat2, lon2,
            GeodesicMask.DISTANCE
        )
        return result.s12
    }
}
```

### 3. Listen for Feed Import Events

```kotlin
// FeedImportCompletedListener.kt
package com.mobilispect.backend.stopspacing.event

import com.mobilispect.backend.feed.event.FeedImportCompletedEvent
import com.mobilispect.backend.stopspacing.service.StopSpacingCalculationService
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class FeedImportCompletedListener(
    private val calculationService: StopSpacingCalculationService
) {

    @Async
    @EventListener
    fun onFeedImportCompleted(event: FeedImportCompletedEvent) {
        calculationService.calculateForFeed(event.feedId)
    }
}
```

### 4. Create REST Controllers

```kotlin
// RouteStopSpacingController.kt
package com.mobilispect.backend.stopspacing.controller

import com.mobilispect.backend.stopspacing.service.StopSpacingService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class RouteStopSpacingController(
    private val service: StopSpacingService
) {

    @GetMapping("/routes/{routeId}/stop-spacing")
    fun getRouteStopSpacing(@PathVariable routeId: String) =
        service.getByRouteId(routeId)
}
```

## Testing Strategy

### Unit Tests (TDD - Write First)

```kotlin
// StopSpacingCalculationServiceTest.kt
@ExtendWith(MockKExtension::class)
class StopSpacingCalculationServiceTest {

    @MockK
    lateinit var distanceCalculator: GeodesicDistanceCalculator

    @MockK
    lateinit var repository: StopSpacingRepository

    @InjectMockKs
    lateinit var service: StopSpacingCalculationService

    @Test
    fun `should calculate average spacing for route with multiple stops`() {
        // Given
        val stops = listOf(
            Stop(lat = 45.5, lon = -73.5),
            Stop(lat = 45.6, lon = -73.6),
            Stop(lat = 45.7, lon = -73.7)
        )
        every { distanceCalculator.calculateDistanceMeters(any(), any(), any(), any()) } returns 500.0

        // When
        val result = service.calculateForStops(stops)

        // Then
        assertThat(result.averageSpacingMeters).isEqualTo(500.0)
    }

    @Test
    fun `should classify route as LOCAL when spacing below threshold`() {
        // Given
        val averageSpacing = 400.0
        val threshold = ClassificationThreshold(localUpperBound = 500, rapidUpperBound = 1500)

        // When
        val result = ServiceTypeClassifier.classify(averageSpacing, threshold)

        // Then
        assertThat(result).isEqualTo(ServiceType.LOCAL)
    }
}
```

### Integration Tests

```kotlin
// StopSpacingModuleIntegrationTest.kt
@ModuleTest
class StopSpacingModuleIntegrationTest {

    @Test
    fun `module should expose only public APIs`() {
        // Spring Modulith verifies encapsulation
    }

    @Test
    fun `should calculate statistics on feed import event`() {
        // Test event-driven calculation
    }
}
```

### Contract Tests

```kotlin
// StopSpacingContractTest.kt
@SpringBootTest(webEnvironment = RANDOM_PORT)
class StopSpacingContractTest {

    @Test
    fun `GET routes-routeId-stop-spacing returns valid response`() {
        // Verify OpenAPI contract compliance
    }
}
```

## API Usage Examples

### Get Route Statistics

```bash
curl -X GET "http://localhost:8080/api/v1/routes/r-dpz8-1/stop-spacing" \
  -H "Accept: application/json"
```

Response:

```json
{
  "routeId": "r-dpz8-1",
  "feedId": "f-dpz8-ttc",
  "averageSpacing": {
    "meters": 450.5,
    "kilometers": 0.4505,
    "miles": 0.28
  },
  "serviceType": "LOCAL",
  "stopCount": 45,
  "calculatedAt": "2025-11-23T10:30:00Z"
}
```

### Get Agency Comparison

```bash
curl -X GET "http://localhost:8080/api/v1/agencies/o-dpz8-ttc/stop-spacing/comparison" \
  -H "Accept: application/json"
```

### Get Regional Comparison

```bash
curl -X GET "http://localhost:8080/api/v1/regions/montreal/stop-spacing/comparison" \
  -H "Accept: application/json"
```

## Frontend Integration

### Angular Service

```typescript
// stop-spacing.service.ts
@Injectable({ providedIn: 'root' })
export class StopSpacingService {
  private readonly baseUrl = '/api/v1';

  constructor(private http: HttpClient) {}

  getRouteStopSpacing(routeId: string): Observable<StopSpacingResponse> {
    return this.http.get<StopSpacingResponse>(
      `${this.baseUrl}/routes/${routeId}/stop-spacing`
    );
  }

  getAgencyComparison(agencyId: string): Observable<AgencyComparisonResponse> {
    return this.http.get<AgencyComparisonResponse>(
      `${this.baseUrl}/agencies/${agencyId}/stop-spacing/comparison`
    );
  }
}
```

### Unit Toggle Component

```typescript
// unit-toggle.component.ts
@Component({
  selector: 'app-unit-toggle',
  template: `
    <mat-button-toggle-group [value]="unit()" (change)="onUnitChange($event)">
      <mat-button-toggle value="km">km</mat-button-toggle>
      <mat-button-toggle value="mi">mi</mat-button-toggle>
    </mat-button-toggle-group>
  `
})
export class UnitToggleComponent {
  unit = signal<'km' | 'mi'>(localStorage.getItem('distanceUnit') as 'km' | 'mi' || 'km');

  onUnitChange(event: MatButtonToggleChange) {
    this.unit.set(event.value);
    localStorage.setItem('distanceUnit', event.value);
  }
}
```

## Common Commands

```bash
# Run all tests
./gradlew test

# Run stop spacing tests only
./gradlew test --tests "*StopSpacing*"

# Verify module boundaries
./gradlew verifyModulith

# Generate API documentation
./gradlew generateOpenApiDocs

# Start backend
./gradlew bootRun

# Start frontend
cd frontend/web && npm start
```

## Troubleshooting

### Common Issues

1. **Module boundary violation**

   ```
   Error: Module 'stopspacing' has illegal dependency on internal 'feed.model'
   ```

   Solution: Use published events and public APIs only

2. **Statistics not calculated**
   - Verify FeedImportCompletedEvent is being published
   - Check async processing is enabled (`@EnableAsync`)

3. **Distance calculation accuracy**
   - Use GeographicLib, not Haversine
   - Verify coordinates are in WGS84 (decimal degrees)

## Next Steps

After completing implementation:

1. Run `/speckit.tasks` to generate detailed task breakdown
2. Create ADR: `docs/adr/NNNN-geodesic-distance-library.md`
3. Add Playwright E2E tests for frontend
4. Configure Grafana dashboards for stop spacing metrics

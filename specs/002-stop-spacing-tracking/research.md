# Research: Average Stop Spacing Tracking

**Feature Branch**: `002-stop-spacing-tracking`
**Date**: 2025-11-23

## Research Tasks

### 1. Geodesic Distance Calculation Library

**Decision**: GeographicLib Java v2.3

**Rationale**:

- WGS84 ellipsoid precision (±0.5 microns) - critical for accurate stop spacing
- Apache 2.0 License - compatible with project requirements
- Minimal dependency footprint - single JAR
- Actively maintained (last update 2024)
- Kotlin-friendly Java API
- No existing geodesic calculation in codebase to replace

**Alternatives Considered**:

| Library | Verdict | Reason |
|---------|---------|--------|
| JTS | Rejected | Overkill for distance-only calculations, LGPL license |
| Apache SIS | Rejected | Heavy dependency, steep learning curve, LGPL |
| Custom Haversine | Rejected for backend | ~0.5% error unacceptable for analytics; fine for mobile UI |
| Java Geodesy | Rejected | No longer maintained |

**Implementation**:

```kotlin
// backend/gradle/libs.versions.toml
geographiclib = { module = "net.sf.geographiclib:geographiclib-java", version = "2.3" }

// Usage
import net.sf.geographiclib.Geodesic
import net.sf.geographiclib.GeodesicMask

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val result = Geodesic.WGS84.inverse(lat1, lon1, lat2, lon2, GeodesicMask.DISTANCE)
    return result.s12 // meters
}
```

**ADR Required**: Yes - `docs/adr/NNNN-geodesic-distance-library.md`

---

### 2. GTFS Data Access Pattern

**Decision**: Access GTFS data through existing `schedule` and `infrastructure` modules via published APIs

**Rationale**:

- Follows Spring Modulith module boundary rules (Constitution Principle VII)
- Route/Trip/Stop data already parsed during feed import
- Existing `schedule` module has Route, ScheduledTrip, ScheduledStop entities
- Existing `infrastructure` module has Stop with coordinates

**Data Flow**:

```
GTFS Feed Import (feed module)
    ↓ FeedImportCompletedEvent
Stop Spacing Calculation (stopspacing module)
    ↓ Read via public APIs
schedule.RouteRepository → Routes with trips
infrastructure.StopRepository → Stops with coordinates
```

**Key Entities from Existing Modules**:

- `schedule.Route` - route_id, agency reference
- `schedule.ScheduledTrip` - trip patterns, stop sequences
- `schedule.ScheduledStop` - stop references with sequence
- `infrastructure.Stop` - coordinates (lat/lon)

---

### 3. Stop Sequence Extraction from GTFS

**Decision**: Use `stop_times.txt` ordering via existing ScheduledTrip/ScheduledStop entities

**Rationale**:

- GTFS `stop_times.txt` contains `stop_sequence` field (ascending order of stops)
- Existing `ScheduledStop` captures this relationship
- Trip variants handled by grouping stop sequences by unique stop pattern

**Algorithm**:

1. For each Route, get all Trips
2. Group Trips by unique stop sequence (variant detection)
3. For each variant:
   - Order stops by `stop_sequence`
   - Calculate distance between consecutive stops
   - Store per-variant statistics
4. Calculate weighted average across variants (by trip count)

---

### 4. Service Type Classification Strategy

**Decision**: Threshold-based classification with regional configurability

**Rationale**:

- Simple, deterministic, auditable
- Matches transit industry practices
- Regional configuration allows for geographic differences (dense European cities vs sprawling North American metros)

**Default Thresholds** (meters):
| Service Type | Lower Bound | Upper Bound |
|--------------|-------------|-------------|
| Local | 0 | < 500 |
| Rapid | 500 | < 1500 |
| Express | 1500 | ∞ |

**Boundary Rule**: Values exactly on threshold classify into higher category (≥500m = Rapid)

**Configuration Model**:

```kotlin
data class ClassificationThreshold(
    val regionId: RegionId?,      // null = global default
    val localUpperBound: Int,     // meters
    val rapidUpperBound: Int      // meters
)
```

---

### 5. Caching Strategy

**Decision**: Redis cache for aggregated statistics with 6-hour TTL

**Rationale**:

- Statistics change only on feed re-import (typically weekly/monthly)
- Regional comparisons aggregate multiple agencies (expensive query)
- 6-hour TTL balances freshness with performance
- Existing Redis infrastructure in place

**Cache Keys**:

```
stopspacing:route:{routeId}           # Route-level stats
stopspacing:agency:{agencyId}         # Agency aggregation
stopspacing:region:{regionId}         # Regional aggregation
```

**Invalidation**: On FeedImportCompletedEvent for affected agency

---

### 6. Statistics Calculation Formulas

**Decision**: Standard statistical measures stored as pre-calculated values

**Metrics per Route**:

```kotlin
data class StopSpacingStatistics(
    val routeId: RouteId,
    val averageSpacing: Double,        // meters, weighted by trip count
    val minimumSpacing: Double,        // meters, across all variants
    val maximumSpacing: Double,        // meters, across all variants
    val standardDeviation: Double,     // meters
    val serviceType: ServiceType,      // derived from averageSpacing
    val stopCount: Int,                // total unique stops
    val variantCount: Int,             // number of trip patterns
    val calculatedAt: Instant
)
```

**Aggregation Metrics** (Agency/Region):

```kotlin
data class ServiceTypeAggregation(
    val serviceType: ServiceType,
    val routeCount: Int,
    val averageSpacing: Double,        // mean of route averages
    val minSpacing: Double,            // min across all routes
    val maxSpacing: Double             // max across all routes
)
```

---

### 7. Unit Conversion Strategy

**Decision**: Store in meters, convert on API response

**Rationale**:

- Single source of truth (meters)
- Conversion is trivial and stateless
- User preference stored in frontend (localStorage)
- No backend storage of user preferences needed

**Conversion**:

```kotlin
fun metersToKilometers(meters: Double): Double = meters / 1000.0
fun metersToMiles(meters: Double): Double = meters / 1609.344
```

**API Response**:

```json
{
  "averageSpacing": {
    "meters": 450.5,
    "kilometers": 0.4505,
    "miles": 0.28
  }
}
```

---

## Summary

All technical unknowns have been resolved:

| Topic | Decision | ADR Required |
|-------|----------|--------------|
| Geodesic calculation | GeographicLib Java 2.3 | Yes |
| GTFS data access | Via schedule/infrastructure module APIs | No |
| Stop sequence | From ScheduledStop via stop_sequence | No |
| Classification | Threshold-based, regionally configurable | No |
| Caching | Redis with 6-hour TTL | No |
| Statistics | Pre-calculated avg/min/max/stddev | No |
| Units | Store meters, convert on response | No |

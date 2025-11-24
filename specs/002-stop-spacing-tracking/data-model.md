# Data Model: Average Stop Spacing Tracking

**Feature Branch**: `002-stop-spacing-tracking`
**Date**: 2025-11-23

## Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           stopspacing Module                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────┐       ┌─────────────────────────────────┐     │
│  │ ClassificationThreshold │       │     StopSpacingStatistics       │     │
│  ├─────────────────────────┤       ├─────────────────────────────────┤     │
│  │ id: UUID (PK)           │       │ id: UUID (PK)                   │     │
│  │ region_id: String (FK)? │       │ route_id: String (FK)           │     │
│  │ local_upper_bound: Int  │       │ feed_id: String (FK)            │     │
│  │ rapid_upper_bound: Int  │       │ average_spacing_meters: Double  │     │
│  │ created_at: Timestamp   │       │ min_spacing_meters: Double      │     │
│  │ updated_at: Timestamp   │       │ max_spacing_meters: Double      │     │
│  └─────────────────────────┘       │ std_deviation_meters: Double    │     │
│           │                        │ service_type: ServiceType       │     │
│           │ applies to             │ stop_count: Int                 │     │
│           ▼                        │ variant_count: Int              │     │
│  ┌─────────────────────────┐       │ calculated_at: Timestamp        │     │
│  │   MetropolitanRegion    │       └─────────────────────────────────┘     │
│  │   (from feed module)    │                    │                          │
│  └─────────────────────────┘                    │ belongs to               │
│           │                                     ▼                          │
│           │ contains                   ┌─────────────────────────┐         │
│           ▼                            │   Route (from schedule) │         │
│  ┌─────────────────────────┐           └─────────────────────────┘         │
│  │   Agency (from feed)    │                    │                          │
│  └─────────────────────────┘                    │ has                      │
│           │                                     ▼                          │
│           │ operates           ┌────────────────────────────────────┐      │
│           └───────────────────▶│ RouteVariantSpacing (embedded)     │      │
│                                ├────────────────────────────────────┤      │
│                                │ variant_hash: String               │      │
│                                │ trip_count: Int                    │      │
│                                │ average_spacing_meters: Double     │      │
│                                │ stop_sequence: List<StopId>        │      │
│                                └────────────────────────────────────┘      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Entities

### StopSpacingStatistics (New Entity)

Primary entity storing calculated stop spacing metrics per route.

**Table**: `stop_spacing_statistics`

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK | Auto-generated primary key |
| `route_id` | VARCHAR(255) | NOT NULL, UNIQUE, INDEX | OneStop route ID (e.g., `r-dpz8-1`) |
| `feed_id` | VARCHAR(255) | NOT NULL, INDEX, FK→feeds | OneStop feed ID |
| `average_spacing_meters` | DOUBLE | NOT NULL | Weighted average across all variants |
| `min_spacing_meters` | DOUBLE | NOT NULL | Minimum spacing across all stop pairs |
| `max_spacing_meters` | DOUBLE | NOT NULL | Maximum spacing across all stop pairs |
| `std_deviation_meters` | DOUBLE | NOT NULL | Standard deviation of all spacings |
| `service_type` | VARCHAR(20) | NOT NULL | Enum: LOCAL, RAPID, EXPRESS |
| `stop_count` | INT | NOT NULL | Total unique stops on route |
| `variant_count` | INT | NOT NULL | Number of distinct trip patterns |
| `variant_details` | JSONB | NOT NULL | Array of RouteVariantSpacing |
| `calculated_at` | TIMESTAMP | NOT NULL | When statistics were calculated |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Record creation time |
| `updated_at` | TIMESTAMP | NOT NULL | Last update time |

**Indexes**:

- `idx_stop_spacing_route_id` on `route_id` (unique)
- `idx_stop_spacing_feed_id` on `feed_id`
- `idx_stop_spacing_service_type` on `service_type`

**Kotlin Entity**:

```kotlin
@Entity
@Table(name = "stop_spacing_statistics")
data class StopSpacingStatisticsEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "route_id", nullable = false, unique = true)
    val routeId: String,

    @Column(name = "feed_id", nullable = false)
    val feedId: String,

    @Column(name = "average_spacing_meters", nullable = false)
    val averageSpacingMeters: Double,

    @Column(name = "min_spacing_meters", nullable = false)
    val minSpacingMeters: Double,

    @Column(name = "max_spacing_meters", nullable = false)
    val maxSpacingMeters: Double,

    @Column(name = "std_deviation_meters", nullable = false)
    val stdDeviationMeters: Double,

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    val serviceType: ServiceType,

    @Column(name = "stop_count", nullable = false)
    val stopCount: Int,

    @Column(name = "variant_count", nullable = false)
    val variantCount: Int,

    @Type(JsonBinaryType::class)
    @Column(name = "variant_details", columnDefinition = "jsonb", nullable = false)
    val variantDetails: List<RouteVariantSpacing>,

    @Column(name = "calculated_at", nullable = false)
    val calculatedAt: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
```

---

### ClassificationThreshold (New Entity)

Stores configurable thresholds for service type classification per region.

**Table**: `classification_thresholds`

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK | Auto-generated primary key |
| `region_id` | VARCHAR(255) | NULLABLE, UNIQUE, FK→metropolitan_regions | NULL = global default |
| `local_upper_bound_meters` | INT | NOT NULL, DEFAULT 500 | Upper bound for local classification |
| `rapid_upper_bound_meters` | INT | NOT NULL, DEFAULT 1500 | Upper bound for rapid classification |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Record creation time |
| `updated_at` | TIMESTAMP | NOT NULL | Last update time |

**Constraints**:

- `CHECK (local_upper_bound_meters > 0)`
- `CHECK (rapid_upper_bound_meters > local_upper_bound_meters)`
- Only one record where `region_id IS NULL` (global default)

**Kotlin Entity**:

```kotlin
@Entity
@Table(name = "classification_thresholds")
data class ClassificationThresholdEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "region_id", unique = true)
    val regionId: String?,

    @Column(name = "local_upper_bound_meters", nullable = false)
    val localUpperBoundMeters: Int = 500,

    @Column(name = "rapid_upper_bound_meters", nullable = false)
    val rapidUpperBoundMeters: Int = 1500,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
```

---

### ServiceType (Enum)

Classification of route service type based on stop spacing.

```kotlin
enum class ServiceType {
    LOCAL,      // High stop density, short spacing
    RAPID,      // Medium stop density
    EXPRESS,    // Low stop density, long spacing
    INSUFFICIENT_DATA  // Routes with < 2 stops
}
```

---

### RouteVariantSpacing (Embedded/JSONB)

Per-variant spacing details stored as JSONB within StopSpacingStatistics.

```kotlin
@Serializable
data class RouteVariantSpacing(
    val variantHash: String,           // SHA256 of stop sequence
    val tripCount: Int,                // Number of trips using this pattern
    val averageSpacingMeters: Double,  // Average for this variant
    val stopSequence: List<String>     // Ordered stop IDs
)
```

---

## Domain Models (Non-Persisted)

### StopSpacingStatistics (Domain)

Rich domain model used in business logic.

```kotlin
data class StopSpacingStatistics(
    val routeId: RouteId,
    val feedId: FeedId,
    val averageSpacing: Distance,
    val minimumSpacing: Distance,
    val maximumSpacing: Distance,
    val standardDeviation: Distance,
    val serviceType: ServiceType,
    val stopCount: Int,
    val variantCount: Int,
    val variants: List<RouteVariantSpacing>,
    val calculatedAt: Instant
)

@JvmInline
value class Distance(val meters: Double) {
    fun toKilometers(): Double = meters / 1000.0
    fun toMiles(): Double = meters / 1609.344
}
```

---

### AgencyStopSpacingComparison (DTO/Domain)

Aggregated comparison data for an agency.

```kotlin
data class AgencyStopSpacingComparison(
    val agencyId: AgencyId,
    val agencyName: String,
    val byServiceType: Map<ServiceType, ServiceTypeAggregation>,
    val totalRoutes: Int,
    val routesWithInsufficientData: Int
)

data class ServiceTypeAggregation(
    val serviceType: ServiceType,
    val routeCount: Int,
    val averageSpacing: Distance,
    val minSpacing: Distance,
    val maxSpacing: Distance
)
```

---

### RegionalStopSpacingComparison (DTO/Domain)

Aggregated comparison data for a metropolitan region.

```kotlin
data class RegionalStopSpacingComparison(
    val regionId: RegionId,
    val regionName: String,
    val agencies: List<AgencyStopSpacingComparison>,
    val regionalAverages: Map<ServiceType, ServiceTypeAggregation>,
    val thresholds: ClassificationThreshold
)
```

---

## Value Classes (IDs)

Following Constitution Principle I (value classes for entity IDs):

```kotlin
@JvmInline
value class StopSpacingId(val value: UUID) {
    companion object {
        fun generate(): StopSpacingId = StopSpacingId(UUID.randomUUID())
    }
}

@JvmInline
value class RouteId(val value: String) {
    init {
        require(value.startsWith("r-")) { "RouteId must follow OneStop format: r-{geohash}-{name}" }
    }
}

@JvmInline
value class FeedId(val value: String) {
    init {
        require(value.startsWith("f-")) { "FeedId must follow OneStop format: f-{geohash}-{name}" }
    }
}

@JvmInline
value class RegionId(val value: String)

@JvmInline
value class AgencyId(val value: String) {
    init {
        require(value.startsWith("o-")) { "AgencyId must follow OneStop format: o-{geohash}-{name}" }
    }
}
```

---

## Database Migration

**File**: `V021__add_stop_spacing_statistics.sql`

```sql
-- Service type enum
CREATE TYPE service_type AS ENUM ('LOCAL', 'RAPID', 'EXPRESS', 'INSUFFICIENT_DATA');

-- Stop spacing statistics table
CREATE TABLE stop_spacing_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id VARCHAR(255) NOT NULL UNIQUE,
    feed_id VARCHAR(255) NOT NULL,
    average_spacing_meters DOUBLE PRECISION NOT NULL,
    min_spacing_meters DOUBLE PRECISION NOT NULL,
    max_spacing_meters DOUBLE PRECISION NOT NULL,
    std_deviation_meters DOUBLE PRECISION NOT NULL,
    service_type service_type NOT NULL,
    stop_count INTEGER NOT NULL,
    variant_count INTEGER NOT NULL,
    variant_details JSONB NOT NULL DEFAULT '[]',
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_stop_spacing_feed
        FOREIGN KEY (feed_id) REFERENCES feeds(onestop_id)
);

CREATE INDEX idx_stop_spacing_feed_id ON stop_spacing_statistics(feed_id);
CREATE INDEX idx_stop_spacing_service_type ON stop_spacing_statistics(service_type);

-- Classification thresholds table
CREATE TABLE classification_thresholds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region_id VARCHAR(255) UNIQUE,
    local_upper_bound_meters INTEGER NOT NULL DEFAULT 500,
    rapid_upper_bound_meters INTEGER NOT NULL DEFAULT 1500,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_threshold_region
        FOREIGN KEY (region_id) REFERENCES metropolitan_regions(onestop_id),
    CONSTRAINT chk_local_positive
        CHECK (local_upper_bound_meters > 0),
    CONSTRAINT chk_rapid_greater_than_local
        CHECK (rapid_upper_bound_meters > local_upper_bound_meters)
);

-- Insert global default threshold
INSERT INTO classification_thresholds (region_id, local_upper_bound_meters, rapid_upper_bound_meters)
VALUES (NULL, 500, 1500);
```

---

## Validation Rules

| Entity | Field | Rule |
|--------|-------|------|
| StopSpacingStatistics | averageSpacing | Must be ≥ 0 |
| StopSpacingStatistics | stopCount | Must be ≥ 0 |
| StopSpacingStatistics | variantCount | Must be ≥ 1 if stopCount ≥ 2 |
| ClassificationThreshold | localUpperBound | Must be > 0 |
| ClassificationThreshold | rapidUpperBound | Must be > localUpperBound |
| RouteId | value | Must match OneStop format `r-*` |
| FeedId | value | Must match OneStop format `f-*` |

---

## State Transitions

### StopSpacingStatistics Lifecycle

```
                    ┌─────────────────┐
                    │   Not Exists    │
                    └────────┬────────┘
                             │ Feed imported (first time)
                             ▼
                    ┌─────────────────┐
                    │    Calculated   │
                    └────────┬────────┘
                             │ Feed re-imported
                             ▼
                    ┌─────────────────┐
                    │    Recalculated │ (same state, updated values)
                    └────────┬────────┘
                             │ Feed deleted
                             ▼
                    ┌─────────────────┐
                    │     Deleted     │
                    └─────────────────┘
```

Statistics are immutable between feed imports - they are replaced entirely on recalculation.

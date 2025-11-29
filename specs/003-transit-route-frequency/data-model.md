# Data Model: Transit Route Frequency Analysis

**Feature**: 003-transit-route-frequency
**Date**: 2025-11-27
**Phase**: 1 - Design & Contracts

## Overview

This document defines the data model for transit route frequency analysis. The model follows Domain-Driven Design principles with value classes for all entity IDs (constitutional requirement) and clear bounded context boundaries enforced by Spring Modulith.

## Entity Relationship Diagram

```
┌─────────────────────┐         ┌────────────┐         ┌────────────┐         ┌────────────┐
│ MetropolitanRegion  │*      * │    Feed    │1      * │   Agency   │1      * │   Route    │
│  (existing table)   │◇────────│ (existing) ├─────────│            ├─────────│            │
│ - region_onestop_id │         │ - id       │         │ - id       │         │ - id       │
│ - name              │         │ - name     │         │ - name     │         │ - number   │
│ - adm0_name         │         │ - url      │         │ - gtfsId   │         │ - name     │
└─────────────────────┘         └────────────┘         └────────────┘         └────────────┘
         │                             │
         │                             │
         └─────────────────────────────┘
                      │
                ┌─────────────┐
                │ feed_regions │ (existing junction table)
                │              │
                │ - feed_id    │
                │ - region_id  │
                └─────────────┘

Note: Agency inherits region membership through Feed relationship
                                                     │1
                                                     │
                                                     │*
                                              ┌──────────────┐
                                              │RouteVariant  │
                                              │              │
                                              │ - id (hash)  │
                                              │ - stopPattern│
                                              │ - direction  │
                                              └──────┬───────┘
                                                     │*
                                                     │
                                                     │1
                                              ┌──────────────┐
                                              │  Frequency   │
                                              │              │
                                              │ - variant    │
                                              │ - timePeriod │
                                              │ - avgHeadway │
                                              └──────────────┘

        ┌───────────────────┐
        │  CommonSection    │
        │                   │*
        │ - id              ├──────────┐
        │ - stopPattern     │          │
        │ - startStop       │          │*
        │ - endStop         │   ┌──────────────────────┐
        └───────────────────┘   │ CommonSectionVariant │
                                │                      │
                                │ - commonSectionId    │
                                │ - variantId          │
                                └──────────────────────┘
```

## Core Entities

### MetropolitanRegion (Existing Table)

**Purpose**: Geographic area containing one or more transit agencies (metro area)

**Note**: This entity **already exists** in the `feed` module and should be **referenced**, not duplicated.

**Existing Attributes** (from `backend/src/main/kotlin/com/mobilispect/backend/feed/model/MetropolitanRegion.kt`):

```kotlin
@Entity
@Table(name = "metropolitan_regions")
class MetropolitanRegion(
    @Id
    @Convert(converter = RegionIdConverter::class)
    @Column(name = "region_onestop_id", nullable = false, updatable = false, length = 255)
    val regionOnestopId: RegionId, // Value class (existing: feed.model.ids.RegionId)

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(name = "adm0_name", nullable = true, length = 255)
    var adm0Name: String?, // Country name

    @Column(name = "adm1_name", nullable = true, length = 255)
    var adm1Name: String?, // State/province name

    @Column(name = "auto_update_enabled", nullable = false)
    var autoUpdateEnabled: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime
)
```

**Integration**:

- `transitanalysis` module references `metropolitan_regions` table via foreign key in `agency_regions` junction table
- No duplication of region data
- RegionId value class from `feed.model.ids` is reused

### Agency

**Purpose**: Transit operator providing public transportation service

**Note**: An agency may belong to **multiple regions** via its associated feed. The feed-region many-to-many relationship (`feed_regions` junction table) already exists, so agencies inherit region membership through their feed reference.

**Relationship Chain**: `Agency -> Feed (via feed_onestop_id) -> Regions (via feed_regions table)`

**Attributes**:

```kotlin
@Entity
@Table(name = "agencies")
data class Agency(
    @Id
    @Column(name = "id", columnDefinition = "VARCHAR(255)")
    val id: AgencyId, // Value class (Onestop ID format: o-geohash-name)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_onestop_id", nullable = false)
    val feed: FeedEntity, // Foreign key to existing feeds table - feed already has many-to-many with regions

    @Column(name = "gtfs_agency_id", nullable = false, length = 255)
    val gtfsAgencyId: String, // ID from GTFS agency.txt

    @Column(name = "name", nullable = false, length = 255)
    val name: String,

    @Column(name = "website", length = 512)
    val website: String?,

    @Column(name = "phone", length = 50)
    val phone: String?,

    @Column(name = "last_feed_import")
    val lastFeedImport: Instant?,

    @Column(name = "active", nullable = false)
    val active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant
)

@JvmInline
value class AgencyId(val value: String)
```

**Validation Rules**:

- `name` must be non-empty
- `feedUrl` must be valid HTTP(S) URL if provided
- `gtfsAgencyId` must match agency.txt format

**Indexes**:

```sql
CREATE INDEX idx_agencies_feed_onestop_id ON agencies(feed_onestop_id);
CREATE INDEX idx_agencies_gtfs_agency_id ON agencies(gtfs_agency_id);
CREATE INDEX idx_agencies_active ON agencies(active) WHERE active = true;
```

**Region Membership Query**:
To get all regions for an agency, join through feed:

```sql
SELECT mr.*
FROM metropolitan_regions mr
JOIN feed_regions fr ON mr.region_onestop_id = fr.region_onestop_id
JOIN agencies a ON fr.feed_onestop_id = a.feed_onestop_id
WHERE a.id = ?;
```

### Route

**Purpose**: Named transit line operated by an agency

**Attributes**:

```kotlin
@Entity
@Table(name = "routes")
data class Route(
    @Id
    @Column(name = "id", columnDefinition = "VARCHAR(50)")
    val id: RouteId, // Value class

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = false)
    val agency: Agency,

    @Column(name = "gtfs_route_id", nullable = false)
    val gtfsRouteId: String, // ID from routes.txt

    @Column(name = "short_name")
    val shortName: String?, // e.g., "5", "Red Line"

    @Column(name = "long_name", nullable = false)
    val longName: String, // e.g., "Downtown Express"

    @Column(name = "route_type", nullable = false)
    val routeType: RouteType, // GTFS route type enum

    @Column(name = "color")
    val color: String?, // Hex color (e.g., "FF0000")

    @Column(name = "text_color")
    val textColor: String?,

    @Column(name = "active", nullable = false)
    val active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant
)

@JvmInline
value class RouteId(val value: String)

enum class RouteType {
    TRAM, SUBWAY, RAIL, BUS, FERRY, CABLE_TRAM, AERIAL_LIFT, FUNICULAR, TROLLEYBUS, MONORAIL
}
```

**Validation Rules**:

- Either `shortName` or `longName` must be present
- `color` must be valid hex color if provided
- `routeType` must match GTFS route_type values

**Indexes**:

```sql
CREATE INDEX idx_routes_agency_id ON routes(agency_id);
CREATE INDEX idx_routes_gtfs_route_id ON routes(gtfs_route_id);
CREATE INDEX idx_routes_active ON routes(active) WHERE active = true;
```

### RouteVariant

**Purpose**: Specific service pattern for a route defined by unique stop sequence

**Attributes**:

```kotlin
@Entity
@Table(name = "route_variants")
data class RouteVariant(
    @Id
    @Column(name = "id", columnDefinition = "VARCHAR(64)")
    val id: VariantHash, // SHA-256 hash of stop pattern (value class)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    val route: Route,

    @Column(name = "direction_id")
    val directionId: Int?, // 0 = outbound, 1 = inbound (from trips.txt)

    @Column(name = "headsign")
    val headsign: String?, // Destination headsign

    @Column(name = "stop_pattern", columnDefinition = "TEXT", nullable = false)
    val stopPattern: String, // Ordered stop IDs (pipe-separated: "stop1|stop2|stop3")

    @Column(name = "stop_count", nullable = false)
    val stopCount: Int,

    @Column(name = "first_stop_id")
    val firstStopId: String,

    @Column(name = "last_stop_id")
    val lastStopId: String,

    @Column(name = "active", nullable = false)
    val active: Boolean = true,

    @Column(name = "first_seen")
    val firstSeen: Instant,

    @Column(name = "last_seen")
    val lastSeen: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant
)

@JvmInline
value class VariantHash(val value: String) // SHA-256 hash (64 hex characters)
```

**Validation Rules**:

- `id` must be 64-character hex string (SHA-256 output)
- `stopPattern` must contain at least 2 stops
- `stopCount` must match actual count in `stopPattern`
- `directionId` must be 0 or 1 if provided

**Indexes**:

```sql
CREATE INDEX idx_route_variants_route_id ON route_variants(route_id);
CREATE INDEX idx_route_variants_first_stop ON route_variants(first_stop_id);
CREATE INDEX idx_route_variants_last_stop ON route_variants(last_stop_id);
CREATE INDEX idx_route_variants_active ON route_variants(active) WHERE active = true;
```

### Frequency

**Purpose**: Service headway for a route variant during specific time period

**Attributes**:

```kotlin
@Entity
@Table(name = "frequencies")
data class Frequency(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    val variant: RouteVariant,

    @Column(name = "service_date", nullable = false)
    val serviceDate: LocalDate,

    @Column(name = "time_period", nullable = false)
    @Enumerated(EnumType.STRING)
    val timePeriod: TimePeriod,

    @Column(name = "average_headway_minutes")
    val averageHeadway: Double?, // Null if irregular schedule

    @Column(name = "min_headway_minutes")
    val minHeadway: Double?,

    @Column(name = "max_headway_minutes")
    val maxHeadway: Double?,

    @Column(name = "trip_count", nullable = false)
    val tripCount: Int, // Number of trips in this period

    @Column(name = "is_irregular", nullable = false)
    val isIrregular: Boolean = false, // True if no fixed pattern

    @Column(name = "calculated_at", nullable = false)
    val calculatedAt: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
)

enum class TimePeriod {
    WEEKDAY_AM_PEAK,      // 6:00-9:00 AM
    WEEKDAY_PM_PEAK,      // 4:00-7:00 PM
    WEEKDAY_OFF_PEAK,     // All other weekday hours
    WEEKEND,              // Saturday-Sunday all day
    HOLIDAY               // Based on calendar_dates.txt
}
```

**Validation Rules**:

- If `isIrregular` is false, `averageHeadway` must be present
- `tripCount` must be >= 0
- `averageHeadway`, `minHeadway`, `maxHeadway` must be positive if present

**Indexes**:

```sql
CREATE UNIQUE INDEX idx_frequencies_variant_date_period
    ON frequencies(variant_id, service_date, time_period);
CREATE INDEX idx_frequencies_service_date ON frequencies(service_date);
CREATE INDEX idx_frequencies_time_period ON frequencies(time_period);
```

### CommonSection

**Purpose**: Geographic segment where multiple routes/variants overlap

**Attributes**:

```kotlin
@Entity
@Table(name = "common_sections")
data class CommonSection(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID,

    @Column(name = "stop_pattern", columnDefinition = "TEXT", nullable = false)
    val stopPattern: String, // Ordered stop IDs (pipe-separated)

    @Column(name = "stop_count", nullable = false)
    val stopCount: Int, // Must be >= 3

    @Column(name = "first_stop_id", nullable = false)
    val firstStopId: String,

    @Column(name = "last_stop_id", nullable = false)
    val lastStopId: String,

    @Column(name = "geographic_extent", columnDefinition = "GEOMETRY(LINESTRING, 4326)")
    val geographicExtent: LineString?, // PostGIS geometry

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant
)

@Entity
@Table(name = "common_section_variants")
data class CommonSectionVariant(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "common_section_id", nullable = false)
    val commonSection: CommonSection,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    val variant: RouteVariant,

    @Column(name = "start_sequence", nullable = false)
    val startSequence: Int, // Position in variant's stop pattern

    @Column(name = "end_sequence", nullable = false)
    val endSequence: Int
)
```

**Validation Rules**:

- `stopCount` must be >= 3 (constitutional requirement from spec)
- `stopPattern` must contain at least 3 stops
- `startSequence` < `endSequence` in CommonSectionVariant

**Indexes**:

```sql
CREATE INDEX idx_common_sections_first_stop ON common_sections(first_stop_id);
CREATE INDEX idx_common_sections_last_stop ON common_sections(last_stop_id);
CREATE INDEX idx_common_section_variants_section
    ON common_section_variants(common_section_id);
CREATE INDEX idx_common_section_variants_variant
    ON common_section_variants(variant_id);
```

## Supporting Entities

### ImportedFeed

**Purpose**: Track imported GTFS feed files for historical analysis

**Attributes**:

```kotlin
@Entity
@Table(name = "imported_feeds")
data class ImportedFeed(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = false)
    val agency: Agency,

    @Column(name = "feed_url", nullable = false)
    val feedUrl: String,

    @Column(name = "feed_version")
    val feedVersion: String?,

    @Column(name = "file_size_bytes")
    val fileSizeBytes: Long,

    @Column(name = "import_started_at", nullable = false)
    val importStartedAt: Instant,

    @Column(name = "import_completed_at")
    val importCompletedAt: Instant?,

    @Column(name = "import_duration_seconds")
    val importDurationSeconds: Long?,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    val status: ImportStatus,

    @Column(name = "routes_processed")
    val routesProcessed: Int?,

    @Column(name = "variants_identified")
    val variantsIdentified: Int?,

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
)

enum class ImportStatus {
    STARTED, IN_PROGRESS, COMPLETED, FAILED
}
```

**Indexes**:

```sql
CREATE INDEX idx_imported_feeds_agency_id ON imported_feeds(agency_id);
CREATE INDEX idx_imported_feeds_status ON imported_feeds(status);
CREATE INDEX idx_imported_feeds_started_at ON imported_feeds(import_started_at);
```

## Database Schema Summary

**Total Tables**: 8

- Core Entities: Region, Agency, Route, RouteVariant, Frequency, CommonSection
- Supporting: CommonSectionVariant, ImportedFeed

**Storage Estimates** (for 50 regions, 1000 agencies, 50K routes, 150K variants):

- Regions: ~50 rows × 500 bytes = 25 KB
- Agencies: ~1K rows × 1 KB = 1 MB
- Routes: ~50K rows × 500 bytes = 25 MB
- RouteVariants: ~150K rows × 1 KB = 150 MB
- Frequencies: ~150K variants × 5 periods × 365 days = 273M rows × 150 bytes = ~41 GB (2 years)
- CommonSections: ~10K rows × 500 bytes = 5 MB
- ImportedFeeds: ~1K agencies × 365 imports/year × 2 years = 730K rows × 500 bytes = 365 MB

**Total Storage**: ~42 GB (with 2 years historical frequency data)

## Value Classes (Constitutional Requirement)

All entity IDs use Kotlin inline value classes for type safety:

```kotlin
@JvmInline value class RegionId(val value: String)
@JvmInline value class AgencyId(val value: String)
@JvmInline value class RouteId(val value: String)
@JvmInline value class VariantHash(val value: String)
```

**Benefits**:

- Compile-time type safety (prevents mixing AgencyId with RouteId)
- Zero runtime overhead (inline classes compile to primitives)
- Explicit domain concepts in code
- Prevents ID confusion bugs across boundaries

## Migration Strategy

**Phase 1**: Create core tables (Region, Agency, Route, RouteVariant)
**Phase 2**: Create frequency and common section tables
**Phase 3**: Create supporting tables (ImportedFeed)
**Phase 4**: Add indexes and constraints
**Phase 5**: Add PostGIS extension for geographic queries (optional, future enhancement)

**Note**: Existing migrations in the project go up to V020, so transit-analysis module migrations start at V021.

**Flyway Migration Files**:

- `V021__create_transit_analysis_core_tables.sql`
- `V022__create_transit_analysis_frequency_tables.sql`
- `V023__create_transit_analysis_supporting_tables.sql`
- `V024__create_transit_analysis_indexes.sql`

## Next Steps

Proceed to API contract generation in `contracts/` directory.

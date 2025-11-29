# ADR 0004: GTFS Data Processing Library Selection

**Date**: 2025-11-27
**Status**: Accepted
**Feature**: 003-transit-route-frequency

## Context

The transit route frequency feature requires parsing and processing GTFS (General Transit Feed Specification) data files from multiple transit agencies. GTFS is a complex format with 14+ file types, intricate relationships between entities, and detailed validation rules. A robust, well-maintained library is needed to handle the full GTFS specification efficiently.

The team must balance:
- Completeness of GTFS support (all file types and relationships)
- Library maturity and maintenance status
- Kotlin compatibility (project uses Kotlin on JVM)
- Performance for large feeds (multi-MB ZIP files)
- Built-in validation capabilities

## Decision

**Adopt OneBusAway GTFS library (version 1.4.15+) for Java/Kotlin GTFS data processing.**

### Implementation

```kotlin
// build.gradle.kts
implementation("org.onebusaway:onebusaway-gtfs:1.4.15")

// Usage pattern
val reader = GtfsReader()
reader.setInputLocation(feedFile)
val dao = reader.run()
val routes = dao.getAllRoutes()
val trips = dao.getAllTrips()
val stopTimes = dao.getStopTimesForTrip(trip)
```

## Consequences

### Positive

1. **Mature & Proven**: Active development since 2008, widely adopted in transit industry by agencies, researchers, and developers
2. **Complete GTFS Support**: Handles all GTFS specification files:
   - Core: agency, routes, trips, stops, stop_times, calendar, calendar_dates
   - Optional: shapes, transfers, feed_info, translations
   - Extensions: GTFS-Realtime compatibility layer
3. **Kotlin Compatibility**: Pure Java implementation runs seamlessly on JVM with Kotlin; no special adaptations needed
4. **Built-in Validation**: Includes GTFS validation rules to detect and report malformed feeds with helpful error messages
5. **Performance**: Optimized for parsing large feeds; efficiently loads feeds with hundreds of routes and thousands of trips
6. **Type Safety**: Strong typing with domain objects aligns with constitutional requirement for value classes

### Negative

1. **External Dependency**: Adds third-party dependency (18KB JAR); requires dependency management and security monitoring
2. **Learning Curve**: Team must familiarize with API; not internal code we can easily modify
3. **Upgrade Dependency**: Updates to GTFS spec may lag behind library releases; may need workarounds for new extensions
4. **Memory Footprint**: In-memory DAO for entire feed can be memory-intensive for very large feeds (100K+ stops); may need streaming approach for edge cases

## Alternatives Considered

### 1. Custom Parser (Rejected)

**Rationale**: Too complex to implement robustly
- GTFS spec is extensive with complex entity relationships (trips → stop_times → stops → transfers)
- Validation rules require understanding subtle constraints (service calendars, calendar_dates precedence)
- Risk of bugs and edge cases in custom implementation
- Ongoing maintenance burden as GTFS spec evolves

### 2. Python GTFS-kit (Rejected)

**Rationale**: Language/architecture mismatch
- Project uses Kotlin/Spring Boot on JVM; requires Python service interop
- Adds operational complexity (separate process, network latency, deployment overhead)
- Worse observability (harder to trace errors across services)
- OneBusAway on JVM provides simpler integration

### 3. CSV Parser + Manual Mapping (Rejected)

**Rationale**: Loss of critical features
- No validation of GTFS constraints
- Manual relationship handling error-prone (orphaned trips, invalid stop references)
- No timezone handling for calendar_dates
- Essentially reimplements OneBusAway without maturity guarantees

## Decision Records

This decision assumes:
- Project stack is Spring Boot with Kotlin on JVM (ADR 0001: use-kotlin-for-backend)
- PostgreSQL used for persistence (separate from in-memory GTFS DAO)
- Frequency calculations will work with OneBusAway domain objects (trips, stop_times)

## Related ADRs

- ADR 0005: Route variant identification relies on OneBusAway Trip and StopTime objects
- ADR 0007: Frequency calculation uses OneBusAway departure time data

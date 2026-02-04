# ADR 0012: GTFS-RT Map Matching Strategy

**Date**: 2026-02-04
**Status**: Proposed
**Related**: ADR 0011 - GTFS-RT Parallel Ingestion Architecture
**Constitutional Requirement**: Principle III - Observability & Operational Insight, Principle IV - Performance & Reliability Targets

## Context

GTFS-RT `VehiclePosition` messages contain raw GPS coordinates (`latitude`, `longitude`) from vehicle AVL (Automatic Vehicle Location) systems. These coordinates have several issues that make them unsuitable for direct use in user-facing applications:

### Problems with Raw GPS

| Issue | Description | Impact |
|-------|-------------|--------|
| **GPS noise** | Consumer-grade GPS has 5-15m error; urban canyons cause 50m+ drift | Vehicle appears to jump between lanes, sidewalks, or buildings |
| **Off-network display** | Raw coordinates may fall on buildings, rivers, or empty lots | Confusing UX — "why is my bus in the river?" |
| **No route context** | Coordinates alone don't indicate progress along route | Can't calculate ETA or distance to next stop |
| **Detours** | Vehicle deviates from published GTFS shape | Shape-based projection gives wrong position |
| **Deadheading** | Vehicle traveling between assignments with no active trip | No shape to project onto |

### What Map Matching Provides

Map matching snaps raw GPS coordinates to the underlying transport network, providing:

1. **Corrected position** — on the road/track, not in a building
2. **Network location** — which road segment or route shape
3. **Distance along route** — progress from origin to destination
4. **Bearing** — direction of travel derived from network geometry
5. **Confidence/offset** — how far the raw GPS was from the matched position

### Constraint: GTFS Shapes vs. Road Network

Transit vehicles follow two overlapping but distinct networks:

| Network | Source | Coverage |
|---------|--------|----------|
| **GTFS shapes** | `shapes.txt` from transit agency | Only covers scheduled routes; may be outdated or missing |
| **Road network** | OpenStreetMap via Valhalla/OSRM | All roads; includes detour paths; doesn't know transit routes |

A vehicle on its normal route should match to the GTFS shape (more accurate for transit). A vehicle on a detour should match to the road network (only option when off-shape).

## Decision

### Hybrid Map Matching: Shape-First with Road Network Fallback

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Map Matching Pipeline                                │
│                                                                              │
│  ┌─────────────────┐                                                        │
│  │ VehiclePosition │                                                        │
│  │ (raw GPS)       │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                  │
│           ▼                                                                  │
│  ┌─────────────────┐     ┌──────────────────────────────────────────────┐   │
│  │ Has trip_id &   │─No─▶│ Road Network Match (Valhalla)                │   │
│  │ shape exists?   │     │ → Returns position on nearest road           │   │
│  └────────┬────────┘     └──────────────────────────────────────────────┘   │
│           │ Yes                                                              │
│           ▼                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Shape Projection                                                     │    │
│  │ Project GPS onto GTFS shape polyline for trip's shape_id            │    │
│  └────────┬────────────────────────────────────────────────────────────┘    │
│           │                                                                  │
│           ▼                                                                  │
│  ┌─────────────────┐                                                        │
│  │ Offset < 50m?   │─Yes─▶ Return shape-matched position                    │
│  └────────┬────────┘       (source: SHAPE)                                  │
│           │ No                                                               │
│           ▼                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Road Network Match (Valhalla)                                        │    │
│  │ Vehicle likely on detour — snap to actual road                      │    │
│  └────────┬────────────────────────────────────────────────────────────┘    │
│           │                                                                  │
│           ▼                                                                  │
│  ┌─────────────────┐                                                        │
│  │ Match found?    │─Yes─▶ Return road-matched position                     │
│  └────────┬────────┘       (source: ROAD_NETWORK, flag: DETOUR)             │
│           │ No                                                               │
│           ▼                                                                  │
│  Return raw GPS position                                                     │
│  (source: RAW, flag: UNMATCHED)                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Domain Model

```kotlin
/**
 * Result of map matching a raw GPS position to the transport network.
 */
data class MatchedPosition(
    val latitude: Double,
    val longitude: Double,
    val bearing: Double?,                    // Heading in degrees (0-360)
    val source: MatchSource,
    val flags: Set<MatchFlag>,
    val shapeMatch: ShapeMatchDetails?,      // Present when source = SHAPE
    val roadMatch: RoadMatchDetails?         // Present when source = ROAD_NETWORK
)

enum class MatchSource {
    SHAPE,          // Matched to GTFS shape polyline
    ROAD_NETWORK,   // Matched to road via Valhalla/OSRM
    RAW             // No match possible, raw GPS returned
}

enum class MatchFlag {
    DETOUR,         // Vehicle off published route
    LOW_CONFIDENCE, // High offset from matched position
    DEADHEADING,    // No active trip
    LOOP_AMBIGUITY  // Route loops back on itself, match may be wrong segment
}

/**
 * Details when matched to GTFS shape.
 */
data class ShapeMatchDetails(
    val shapeId: ShapeId,
    val distanceAlongShape: Double,          // Meters from shape start
    val totalShapeLength: Double,            // Total shape length in meters
    val progressFraction: Double,            // 0.0 to 1.0
    val offsetFromShape: Double,             // Perpendicular distance from shape (GPS error)
    val nearestShapePointIndex: Int
)

/**
 * Details when matched to road network.
 */
data class RoadMatchDetails(
    val wayId: Long?,                        // OSM way ID if available
    val roadName: String?,                   // Street name if available
    val roadClass: String?,                  // highway=primary, secondary, etc.
    val offsetFromRoad: Double               // Distance from matched road centerline
)
```

### Shape Projection Algorithm

```kotlin
/**
 * Projects a GPS point onto a GTFS shape polyline.
 * Returns null if no valid projection (e.g., shape is empty).
 */
fun projectOntoShape(
    latitude: Double,
    longitude: Double,
    shapePoints: List<ShapePoint>
): ShapeProjectionResult? {
    if (shapePoints.size < 2) return null

    var bestSegmentIndex = 0
    var bestProjection: GeoPoint? = null
    var bestDistance = Double.MAX_VALUE

    // Find the segment with the closest projection
    for (i in 0 until shapePoints.size - 1) {
        val segmentStart = shapePoints[i].toGeoPoint()
        val segmentEnd = shapePoints[i + 1].toGeoPoint()

        val projection = projectPointOntoSegment(
            point = GeoPoint(latitude, longitude),
            segmentStart = segmentStart,
            segmentEnd = segmentEnd
        )

        val distance = haversineDistance(
            GeoPoint(latitude, longitude),
            projection
        )

        if (distance < bestDistance) {
            bestDistance = distance
            bestProjection = projection
            bestSegmentIndex = i
        }
    }

    val projection = bestProjection ?: return null

    // Calculate distance along shape to the projection point
    val distanceToSegmentStart = shapePoints
        .take(bestSegmentIndex + 1)
        .zipWithNext()
        .sumOf { (a, b) -> haversineDistance(a.toGeoPoint(), b.toGeoPoint()) }

    val distanceWithinSegment = haversineDistance(
        shapePoints[bestSegmentIndex].toGeoPoint(),
        projection
    )

    val distanceAlongShape = distanceToSegmentStart + distanceWithinSegment
    val totalShapeLength = calculateTotalShapeLength(shapePoints)

    // Calculate bearing from the matched segment
    val bearing = calculateBearing(
        shapePoints[bestSegmentIndex].toGeoPoint(),
        shapePoints[bestSegmentIndex + 1].toGeoPoint()
    )

    return ShapeProjectionResult(
        projectedPoint = projection,
        distanceAlongShape = distanceAlongShape,
        totalShapeLength = totalShapeLength,
        offsetFromShape = bestDistance,
        segmentIndex = bestSegmentIndex,
        bearing = bearing
    )
}

/**
 * Projects a point onto a line segment, clamping to segment endpoints.
 */
private fun projectPointOntoSegment(
    point: GeoPoint,
    segmentStart: GeoPoint,
    segmentEnd: GeoPoint
): GeoPoint {
    // Convert to local Cartesian coordinates for projection math
    // (acceptable approximation for short segments)
    val dx = segmentEnd.lng - segmentStart.lng
    val dy = segmentEnd.lat - segmentStart.lat

    if (dx == 0.0 && dy == 0.0) {
        return segmentStart // Degenerate segment
    }

    val t = ((point.lng - segmentStart.lng) * dx + (point.lat - segmentStart.lat) * dy) /
            (dx * dx + dy * dy)

    val tClamped = t.coerceIn(0.0, 1.0)

    return GeoPoint(
        lat = segmentStart.lat + tClamped * dy,
        lng = segmentStart.lng + tClamped * dx
    )
}
```

### Handling Edge Cases

#### Loop Routes

When a route shape crosses itself (e.g., a figure-8 or lollipop route), the GPS point may be equidistant from multiple segments.

```kotlin
fun resolveLoopAmbiguity(
    vehiclePosition: VehiclePosition,
    shapePoints: List<ShapePoint>,
    candidates: List<ShapeProjectionResult>
): ShapeProjectionResult {
    // Use GTFS-RT current_stop_sequence to disambiguate
    val currentStopSequence = vehiclePosition.currentStopSequence

    if (currentStopSequence != null) {
        // Find the candidate whose segment is closest to the expected position
        // based on stop sequence progress
        val expectedProgress = estimateProgressFromStopSequence(
            currentStopSequence,
            vehiclePosition.tripId
        )

        return candidates.minByOrNull { candidate ->
            abs(candidate.progressFraction - expectedProgress)
        } ?: candidates.first()
    }

    // Fallback: use shape_dist_traveled if available in GTFS-RT
    val distTraveled = vehiclePosition.position.odometer
    if (distTraveled != null) {
        return candidates.minByOrNull { candidate ->
            abs(candidate.distanceAlongShape - distTraveled)
        } ?: candidates.first()
    }

    // Last resort: return first candidate, flag as ambiguous
    return candidates.first().copy(
        flags = candidates.first().flags + MatchFlag.LOOP_AMBIGUITY
    )
}
```

#### Deadheading (No Active Trip)

```kotlin
fun matchDeadheadingVehicle(
    latitude: Double,
    longitude: Double
): MatchedPosition {
    // No trip/shape to match against — go directly to road network
    val roadMatch = valhallaClient.matchToRoad(latitude, longitude)

    return if (roadMatch != null) {
        MatchedPosition(
            latitude = roadMatch.latitude,
            longitude = roadMatch.longitude,
            bearing = roadMatch.bearing,
            source = MatchSource.ROAD_NETWORK,
            flags = setOf(MatchFlag.DEADHEADING),
            shapeMatch = null,
            roadMatch = roadMatch.toDetails()
        )
    } else {
        MatchedPosition(
            latitude = latitude,
            longitude = longitude,
            bearing = null,
            source = MatchSource.RAW,
            flags = setOf(MatchFlag.DEADHEADING, MatchFlag.UNMATCHED),
            shapeMatch = null,
            roadMatch = null
        )
    }
}
```

#### Detour Detection

```kotlin
private const val DETOUR_THRESHOLD_METERS = 50.0
private const val LOW_CONFIDENCE_THRESHOLD_METERS = 25.0

fun isDetour(offsetFromShape: Double): Boolean =
    offsetFromShape > DETOUR_THRESHOLD_METERS

fun isLowConfidence(offsetFromShape: Double): Boolean =
    offsetFromShape > LOW_CONFIDENCE_THRESHOLD_METERS
```

### Valhalla Integration

Valhalla is the recommended road network map matching service. It's open source, self-hosted, and provides accurate results using Hidden Markov Model (HMM) based matching.

#### Valhalla Client

```kotlin
@Component
class ValhallaMapMatchingClient(
    private val webClient: WebClient,
    @Value("\${valhalla.base-url}") private val baseUrl: String,
    private val meterRegistry: MeterRegistry
) {
    /**
     * Matches a single GPS point to the road network.
     * Uses Valhalla's /locate endpoint for single-point matching.
     */
    suspend fun matchToRoad(
        latitude: Double,
        longitude: Double
    ): RoadMatchResult? {
        val request = ValhallaLocateRequest(
            locations = listOf(
                ValhallaLocation(lat = latitude, lon = longitude)
            ),
            costing = "auto",  // Use road network suitable for vehicles
            verbose = true
        )

        return try {
            val response = webClient.post()
                .uri("$baseUrl/locate")
                .bodyValue(request)
                .retrieve()
                .awaitBody<ValhallaLocateResponse>()

            response.toRoadMatchResult()
        } catch (e: Exception) {
            logger.warn("Valhalla match failed: ${e.message}")
            meterRegistry.counter("map_matching.valhalla.errors").increment()
            null
        }
    }

    /**
     * Matches a trace of GPS points to the road network.
     * Uses Valhalla's /trace_attributes endpoint for trajectory matching.
     * More accurate than single-point matching when multiple positions available.
     */
    suspend fun matchTrace(
        positions: List<TimestampedPosition>
    ): List<RoadMatchResult> {
        if (positions.size < 2) {
            return positions.mapNotNull { matchToRoad(it.latitude, it.longitude) }
        }

        val request = ValhallaTraceRequest(
            shape = positions.map { ValhallaLocation(lat = it.latitude, lon = it.longitude) },
            costing = "auto",
            shape_match = "map_snap",
            filters = ValhallaFilters(
                attributes = listOf("edge.way_id", "edge.names", "edge.road_class"),
                action = "include"
            )
        )

        return try {
            val response = webClient.post()
                .uri("$baseUrl/trace_attributes")
                .bodyValue(request)
                .retrieve()
                .awaitBody<ValhallaTraceResponse>()

            response.toRoadMatchResults()
        } catch (e: Exception) {
            logger.warn("Valhalla trace match failed: ${e.message}")
            emptyList()
        }
    }
}
```

#### Valhalla Infrastructure

```yaml
# docker-compose.yml (development)
services:
  valhalla:
    image: ghcr.io/gis-ops/docker-valhalla/valhalla:latest
    ports:
      - "8002:8002"
    volumes:
      - ./valhalla-tiles:/custom_files
    environment:
      - tile_urls=https://download.geofabrik.de/north-america-latest.osm.pbf
      - serve_tiles=True
      - build_admins=True
      - build_time_zones=True
```

```yaml
# Kubernetes (production)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: valhalla
spec:
  replicas: 2
  template:
    spec:
      containers:
        - name: valhalla
          image: ghcr.io/gis-ops/docker-valhalla/valhalla:latest
          ports:
            - containerPort: 8002
          resources:
            requests:
              memory: "4Gi"
              cpu: "1000m"
            limits:
              memory: "8Gi"
              cpu: "2000m"
          volumeMounts:
            - name: valhalla-tiles
              mountPath: /custom_files
      volumes:
        - name: valhalla-tiles
          persistentVolumeClaim:
            claimName: valhalla-tiles-pvc
```

### Map Matching Service

```kotlin
@Service
class MapMatchingService(
    private val shapeRepository: ShapeRepository,
    private val tripRepository: TripRepository,
    private val valhallaClient: ValhallaMapMatchingClient,
    private val meterRegistry: MeterRegistry
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Matches a GTFS-RT vehicle position to the transport network.
     */
    suspend fun match(vehiclePosition: VehiclePosition): MatchedPosition {
        val timer = Timer.start(meterRegistry)

        try {
            // Check if we have a trip and shape to match against
            val tripId = vehiclePosition.trip?.tripId
            val shapeId = tripId?.let { tripRepository.findShapeIdByTripId(it) }
            val shapePoints = shapeId?.let { shapeRepository.findByShapeId(it) }

            return if (shapePoints != null && shapePoints.size >= 2) {
                matchWithShape(vehiclePosition, shapePoints)
            } else {
                matchWithRoadNetwork(vehiclePosition, deadheading = tripId == null)
            }
        } finally {
            timer.stop(meterRegistry.timer("map_matching.duration"))
        }
    }

    private suspend fun matchWithShape(
        vehiclePosition: VehiclePosition,
        shapePoints: List<ShapePoint>
    ): MatchedPosition {
        val lat = vehiclePosition.position.latitude
        val lng = vehiclePosition.position.longitude

        val projection = projectOntoShape(lat, lng, shapePoints)

        if (projection == null) {
            logger.warn("Shape projection failed for vehicle ${vehiclePosition.vehicle.id}")
            return matchWithRoadNetwork(vehiclePosition, deadheading = false)
        }

        // Check if vehicle is on-route or on detour
        return if (isDetour(projection.offsetFromShape)) {
            logger.info(
                "Vehicle {} is {}m off route, matching to road network",
                vehiclePosition.vehicle.id,
                projection.offsetFromShape.toInt()
            )
            meterRegistry.counter("map_matching.detour_detected").increment()

            val roadMatch = matchWithRoadNetwork(vehiclePosition, deadheading = false)
            roadMatch.copy(flags = roadMatch.flags + MatchFlag.DETOUR)
        } else {
            meterRegistry.counter("map_matching.source", "source", "shape").increment()

            val flags = buildSet {
                if (isLowConfidence(projection.offsetFromShape)) {
                    add(MatchFlag.LOW_CONFIDENCE)
                }
            }

            MatchedPosition(
                latitude = projection.projectedPoint.lat,
                longitude = projection.projectedPoint.lng,
                bearing = projection.bearing,
                source = MatchSource.SHAPE,
                flags = flags,
                shapeMatch = ShapeMatchDetails(
                    shapeId = shapePoints.first().shapeId,
                    distanceAlongShape = projection.distanceAlongShape,
                    totalShapeLength = projection.totalShapeLength,
                    progressFraction = projection.distanceAlongShape / projection.totalShapeLength,
                    offsetFromShape = projection.offsetFromShape,
                    nearestShapePointIndex = projection.segmentIndex
                ),
                roadMatch = null
            )
        }
    }

    private suspend fun matchWithRoadNetwork(
        vehiclePosition: VehiclePosition,
        deadheading: Boolean
    ): MatchedPosition {
        val lat = vehiclePosition.position.latitude
        val lng = vehiclePosition.position.longitude

        val roadMatch = valhallaClient.matchToRoad(lat, lng)

        return if (roadMatch != null) {
            meterRegistry.counter("map_matching.source", "source", "road_network").increment()

            val flags = buildSet {
                if (deadheading) add(MatchFlag.DEADHEADING)
            }

            MatchedPosition(
                latitude = roadMatch.latitude,
                longitude = roadMatch.longitude,
                bearing = roadMatch.bearing,
                source = MatchSource.ROAD_NETWORK,
                flags = flags,
                shapeMatch = null,
                roadMatch = RoadMatchDetails(
                    wayId = roadMatch.wayId,
                    roadName = roadMatch.roadName,
                    roadClass = roadMatch.roadClass,
                    offsetFromRoad = roadMatch.offset
                )
            )
        } else {
            meterRegistry.counter("map_matching.source", "source", "raw").increment()

            MatchedPosition(
                latitude = lat,
                longitude = lng,
                bearing = null,
                source = MatchSource.RAW,
                flags = setOf(MatchFlag.LOW_CONFIDENCE),
                shapeMatch = null,
                roadMatch = null
            )
        }
    }
}
```

## Rationale

### Why Shape-First?

1. **More Accurate for Transit**: GTFS shapes represent the exact path the vehicle should take, including transit-only lanes, busways, and rail alignments that may not be in OSM.
2. **Route Context**: Shape matching provides `distanceAlongShape` which enables progress tracking and ETA calculation. Road network matching doesn't know which route the vehicle is on.
3. **No External Call**: Shape projection is a pure geometric calculation. Avoiding Valhalla calls for on-route vehicles reduces latency and infrastructure load.
4. **Authoritative Data**: The transit agency published the shape — it's the ground truth for normal operations.

### Why Road Network Fallback?

1. **Detours Are Real**: Construction, accidents, and special events cause detours. Projecting a detoured vehicle onto the wrong shape gives incorrect positions.
2. **Incomplete GTFS**: Some agencies don't publish shapes, or shapes are outdated. Road network is always available via OSM.
3. **Deadheading**: Vehicles traveling between assignments have no associated trip/shape. Road network is the only option.
4. **Better Than Raw GPS**: Even on a detour, snapping to the road is more accurate than showing raw GPS in a building.

### Why Valhalla?

1. **Open Source**: No licensing costs, full control over infrastructure.
2. **HMM-Based**: Hidden Markov Model matching handles GPS noise better than simple nearest-road approaches.
3. **Self-Hosted**: No external API dependency, no per-request costs, predictable latency.
4. **Active Community**: Well-maintained, good documentation, Docker images available.
5. **Trace Matching**: Can match a sequence of points, improving accuracy when historical positions are available.

### Why 50m Detour Threshold?

- Urban GPS error is typically 10-30m
- Transit lanes/busways may be 10-20m from the shape centerline
- 50m provides margin for GPS error while detecting true detours
- Can be tuned per-region based on observed data

## Consequences

### Positive

1. **Accurate Positions**: Vehicles display on roads/tracks, not in buildings
2. **Detour Handling**: Real-world deviations are handled gracefully
3. **Route Progress**: Shape matching enables progress tracking and ETA
4. **Resilient**: Multiple fallback layers ensure a position is always returned
5. **Observable**: Source and flags indicate match quality for debugging

### Negative

1. **Infrastructure Requirement**: Valhalla requires deployment, OSM tile management, and ongoing maintenance
   - Mitigation: Docker images simplify deployment; tiles can be updated monthly
2. **Latency**: Valhalla calls add 10-50ms per match for detoured/deadheading vehicles
   - Mitigation: Most vehicles are on-route (shape match, no external call); batch trace matching for efficiency
3. **Tile Storage**: OSM tiles for Valhalla require significant disk (North America: ~50GB)
   - Mitigation: Use regional extracts; cloud block storage is cheap
4. **Stale Shapes**: If GTFS shapes are outdated, vehicles may appear off-route incorrectly
   - Mitigation: Detour threshold provides tolerance; agencies should update shapes regularly

## Alternatives Considered

### 1. Shape-Only Matching (Rejected)

**Approach**: Always project onto GTFS shape, ignore detours.

**Rejected Because**:

- Detoured vehicles show at wrong positions
- Deadheading vehicles have no shape to match
- Fails silently — users see incorrect data without warning

### 2. Road Network Only (Rejected)

**Approach**: Always use Valhalla, ignore GTFS shapes.

**Rejected Because**:

- Loses route context (which route is the vehicle on?)
- Can't calculate progress along route
- Higher latency for all matches (external call required)
- Road network may not include transit-only paths

### 3. OSRM Instead of Valhalla (Deferred)

**Approach**: Use OSRM for road network matching.

**Deferred Because**:

- OSRM is faster but less accurate for map matching (designed for routing)
- Valhalla's HMM-based matching handles GPS noise better
- Can be added as an alternative backend if Valhalla proves problematic

### 4. Cloud Map Matching API (Rejected)

**Approach**: Use Mapbox or Google Maps for map matching.

**Rejected Because**:

- Per-request costs at scale (millions of matches/day)
- External dependency for critical path
- Latency variability
- Data sent to third party

## Related Decisions

- **ADR 0011**: GTFS-RT Parallel Ingestion Architecture — ingestion pipeline that feeds into map matching
- **ADR 0009**: Spring Modulith Module Boundaries — map matching as part of `gtfsrt` module
- Constitutional Principle III: Observability & Operational Insight
- Constitutional Principle IV: Performance & Reliability Targets

## Implementation Checklist

### Phase 1: Shape Projection

- [ ] Implement `ShapePoint` domain model and `ShapeRepository`
- [ ] Implement `projectOntoShape()` algorithm with segment projection
- [ ] Implement `haversineDistance()` and `calculateBearing()` utilities
- [ ] Add loop route disambiguation using `current_stop_sequence`
- [ ] Write unit tests with various shape geometries (straight, curved, loop)
- [ ] Verify ≥80% test coverage

### Phase 2: Valhalla Integration

- [ ] Set up Valhalla Docker container for local development
- [ ] Implement `ValhallaMapMatchingClient` with WebClient
- [ ] Implement single-point `/locate` endpoint integration
- [ ] Implement trace `/trace_attributes` endpoint integration
- [ ] Add circuit breaker for Valhalla calls
- [ ] Add retry with backoff for transient failures
- [ ] Write integration tests with Testcontainers

### Phase 3: Map Matching Service

- [ ] Implement `MapMatchingService` with hybrid logic
- [ ] Implement `MatchedPosition` domain model
- [ ] Add detour detection with configurable threshold
- [ ] Add deadheading detection (no trip_id)
- [ ] Wire into GTFS-RT processing pipeline (ADR 0011)
- [ ] Write integration tests for full matching flow

### Phase 4: Infrastructure

- [ ] Create Kubernetes manifests for Valhalla deployment
- [ ] Set up OSM tile download and update pipeline
- [ ] Configure PersistentVolumeClaim for tile storage
- [ ] Add Valhalla health check to application health endpoint
- [ ] Document tile update procedure

### Phase 5: Observability

- [ ] Add metrics: match duration, source distribution, detour rate
- [ ] Add structured logging for match decisions
- [ ] Create Grafana dashboard for map matching health
- [ ] Add alerting for high raw-match rate (indicates problems)

## Notes for Implementation Team

- **Coordinate Systems**: All calculations use WGS84 (EPSG:4326). Haversine formula is acceptable for segment-level distances; use proper geodesic for long distances if needed.
- **Shape Caching**: Cache GTFS shapes in Redis keyed by `shape_id`. Shapes change only on GTFS static import.
- **Valhalla Timeouts**: Set aggressive timeouts (100ms). Better to return raw GPS than block on slow Valhalla.
- **Batch Matching**: For efficiency, consider batching multiple vehicle positions per Valhalla trace request when processing feeds with many vehicles.
- **Threshold Tuning**: The 50m detour threshold should be configurable per-region. Dense urban areas may need lower thresholds; rural areas higher.
- **Metrics Cardinality**: Don't tag metrics with `feed_id` or `vehicle_id` — cardinality explosion. Use `source` and `flags` tags only.

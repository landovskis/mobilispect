# Data Model: Isochrone Map Builder

**Feature**: Isochrone Map Builder
**Branch**: `claude/isochrone-map-builder-Adv0B`
**Date**: 2026-02-19

---

## Overview

The isochrone feature does **not introduce new database tables**. All spatial computation is delegated to
OpenTripPlanner (OTP). Results are cached in Redis. The data model describes in-memory domain objects, DTOs,
and cache structures only.

---

## Backend Domain Objects (Kotlin)

### `IsochroneRequest`

```kotlin
// com.mobilispect.backend.isochrone.domain.IsochroneRequest
data class IsochroneRequest(
    val latitude: Double,      // WGS 84, -90..90
    val longitude: Double,     // WGS 84, -180..180
    val mode: TravelMode,
    val cutoffMinutes: List<Int>,  // e.g. [15, 30, 45, 60], 1..240 each
    val dateTime: LocalDateTime    // departure time (defaults to next weekday at 12:00)
)
```

**Validation rules**:

- `latitude` ∈ [-90, 90]
- `longitude` ∈ [-180, 180]
- `cutoffMinutes`: non-empty, each value ∈ [1, 240], max 8 values
- `dateTime`: not in the past by more than 24 hours

---

### `TravelMode` (enum)

```kotlin
// com.mobilispect.backend.isochrone.domain.TravelMode
enum class TravelMode {
    TRANSIT,   // OTP: TRANSIT,WALK
    WALK,      // OTP: WALK
    BICYCLE    // OTP: BICYCLE
}
```

---

### `IsochroneResult`

```kotlin
// com.mobilispect.backend.isochrone.domain.IsochroneResult
data class IsochroneResult(
    val request: IsochroneRequest,
    val bands: List<IsochroneBand>,
    val computedAt: Instant
)
```

---

### `IsochroneBand`

```kotlin
// com.mobilispect.backend.isochrone.domain.IsochroneBand
data class IsochroneBand(
    val cutoffMinutes: Int,
    val geojson: String         // GeoJSON Polygon or MultiPolygon as raw string
)
```

---

## API DTOs (Request/Response)

### `IsochroneRequestDto` (query params)

| Parameter | Type | Required | Default | Constraints |
| --- | --- | --- | --- | --- |
| `lat` | Double | Yes | — | -90 to 90 |
| `lon` | Double | Yes | — | -180 to 180 |
| `mode` | String | No | `TRANSIT` | `TRANSIT`, `WALK`, `BICYCLE` |
| `cutoffMinutes` | String | No | `15,30,45,60` | comma-separated ints, max 8 |
| `dateTime` | String | No | next weekday at 12:00 | ISO-8601 datetime |

---

### `IsochroneResponseDto` (JSON response body)

```json
{
  "origin": {
    "latitude": 45.5017,
    "longitude": -73.5673
  },
  "mode": "TRANSIT",
  "dateTime": "2026-02-19T09:00:00",
  "bands": [
    {
      "cutoffMinutes": 15,
      "color": "#1a9641",
      "geojson": {
        "type": "Feature",
        "geometry": {
          "type": "MultiPolygon",
          "coordinates": [...]
        },
        "properties": {
          "cutoffMinutes": 15
        }
      }
    },
    {
      "cutoffMinutes": 30,
      "color": "#a6d96a",
      "geojson": { ... }
    }
  ],
  "computedAt": "2026-02-19T09:00:01Z",
  "cached": true
}
```

---

## Cache Structure (Redis)

**Key format**: `isochrone:{mode}:{lat5}:{lon5}:{cutoffs}:{date}:{hour}`

- `lat5` / `lon5` = latitude/longitude rounded to 5 decimal places
- `cutoffs` = hyphen-separated sorted cutoff minutes, e.g. `15-30-45-60`
- `date` = `YYYY-MM-DD`
- `hour` = `HH` (transit isochrones vary by time of day)

**Value**: Serialized `IsochroneResult` as JSON string

**TTL**:

- `TRANSIT`: 3600 seconds (1 hour)
- `WALK`: 86400 seconds (24 hours)
- `BICYCLE`: 86400 seconds (24 hours)

---

## OTP Client Objects (internal)

### `OtpIsochroneRequest` (internal HTTP call to OTP)

```kotlin
// com.mobilispect.backend.isochrone.internal.OtpIsochroneRequest
internal data class OtpIsochroneRequest(
    val place: String,              // "{lat},{lon}"
    val time: String,               // ISO-8601 datetime
    val mode: String,               // "TRANSIT,WALK" | "WALK" | "BICYCLE"
    val cutoffSec: List<Int>,       // cutoffMinutes * 60
    val arriveBy: Boolean = false
)
```

### `OtpIsochroneResponse` (parsed from OTP)

```kotlin
// com.mobilispect.backend.isochrone.internal.OtpIsochroneResponse
@Serializable
internal data class OtpIsochroneResponse(
    val type: String,               // "FeatureCollection"
    val features: List<OtpFeature>
)

@Serializable
internal data class OtpFeature(
    val type: String,               // "Feature"
    val geometry: JsonObject,       // raw GeoJSON geometry
    val properties: OtpFeatureProperties
)

@Serializable
internal data class OtpFeatureProperties(
    val cutoffSec: Int              // seconds — divide by 60 for minutes
)
```

---

## Frontend Models (TypeScript)

### `IsochroneRequest`

```typescript
// frontend/web/src/app/isochrone-map/models/isochrone-request.ts
export interface IsochroneRequest {
  lat: number;
  lon: number;
  mode: TravelMode;
  cutoffMinutes: number[];
  dateTime?: string; // ISO-8601; if omitted, backend defaults to next weekday at 12:00
}

export type TravelMode = 'TRANSIT' | 'WALK' | 'BICYCLE';
```

### `IsochroneResponse`

```typescript
// frontend/web/src/app/isochrone-map/models/isochrone-response.ts
export interface IsochroneResponse {
  origin: { latitude: number; longitude: number };
  mode: TravelMode;
  dateTime: string;
  bands: IsochroneBand[];
  computedAt: string;
  cached: boolean;
}

export interface IsochroneBand {
  cutoffMinutes: number;
  color: string;     // hex color for this time band
  geojson: GeoJsonFeature;
}

export interface GeoJsonFeature {
  type: 'Feature';
  geometry: GeoJsonGeometry;
  properties: Record<string, unknown>;
}

export interface GeoJsonGeometry {
  type: 'MultiPolygon' | 'Polygon';
  coordinates: number[][][][];
}
```

---

## Color Scheme for Time Bands

| Cutoff (min) | Hex Color | Meaning |
| --- | --- | --- |
| 15 | `#1a9641` | Dark green — most accessible |
| 30 | `#a6d96a` | Light green |
| 45 | `#ffffbf` | Yellow |
| 60 | `#fdae61` | Orange |
| 90 | `#d7191c` | Red — least accessible |

Colors are selected from ColorBrewer diverging scale for WCAG contrast compliance.
Opacity: 0.35 for inner bands, incrementally increasing outward so overlap remains legible.

---

## No New Database Migrations Required

The isochrone feature:

- Does **not** add PostgreSQL tables
- Does **not** modify existing tables
- Uses existing Redis (already deployed) for caching
- Introduces a new OTP container (infrastructure concern, not schema)

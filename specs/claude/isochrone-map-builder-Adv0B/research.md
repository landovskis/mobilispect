# Research: Isochrone Map for Transit, Walk, and Bike Times

**Feature**: Isochrone Map Builder
**Branch**: `claude/isochrone-map-builder-Adv0B`
**Date**: 2026-02-19

---

## Decision 1: Isochrone Computation Engine

**Decision**: Use **OpenTripPlanner (OTP) 2.x** as the routing/isochrone engine.

**Rationale**:

- OTP is the industry-standard open-source multimodal trip planner
- Native GTFS ingestion — the codebase already imports GTFS feeds via TransitLand; those feeds can be fed directly to OTP
- Supports TRANSIT, WALK, and BICYCLE travel modes natively
- Exposes a mature Isochrone API (`/otp/traveltime/isochrone`) returning GeoJSON polygons
- Runs as a separate JVM service — fits alongside the Spring Boot backend in Docker Compose / Kubernetes
- No need to build a graph-based BFS/Dijkstra + alpha-shape pipeline from scratch

**Alternatives considered**:

| Alternative | Reason Rejected |
| --- | --- |
| Custom BFS over GTFS stop graph | Extremely complex; transit isochrones require timetable awareness (waiting time, transfer penalties); far more than a sprint's worth of work |
| Valhalla (C++) | Excellent for walk/bike; poor GTFS transit support; C++ dependency is foreign to the JVM-centric team |
| GraphHopper | Good routing; isochrone support is weaker for transit; less mature multimodal stack |
| OpenRouteService (hosted) | Vendor dependency; external API costs; not under team control |
| Mapbox Isochrone API | SaaS cost; vendor lock-in; no transit mode beyond drive |

**OTP Integration Approach**:

- OTP runs as a **sidecar container** in the same Docker Compose / Kubernetes manifest
- Mobilispect backend calls OTP via HTTP REST (`OtpClient` in the `isochrone` module)
- Redis already in the stack — cached at the Mobilispect backend layer (not OTP level)
- OTP is seeded with: GTFS feeds from active regions + OSM extract for each metro area

---

## Decision 2: Caching Strategy

**Decision**: Cache isochrone GeoJSON results in **Redis** with a TTL of **1 hour** (transit) / **24 hours** (walk+bike).

**Rationale**:

- OTP isochrone computation is expensive (500ms–3s depending on graph size)
- p95 ≤ 200ms API target (constitutional requirement) cannot be met via live OTP calls
- Redis already deployed in the stack (no new infrastructure)
- Transit isochrones change when GTFS schedules change (daily at most); 1-hour TTL is conservative
- Walk/bike isochrones depend only on OSM (changes rarely); 24-hour TTL is safe

**Cache Key**: `isochrone:{mode}:{lat5}:{lon5}:{cutoffs}:{date}:{hour}`
where `lat5`/`lon5` are rounded to 5 decimal places (~1 m precision), `cutoffs` is the sorted hyphen-joined cutoff list
(e.g. `15-30-45-60`), `date` is `YYYY-MM-DD`, and `hour` is `HH`

---

## Decision 3: Frontend Map Library

**Decision**: Use **MapLibre GL JS** (via `maplibre-gl` npm package) with Angular wrapper.

**Rationale**:

- Open-source, MIT-licensed fork of Mapbox GL JS — no API key required
- Vector tile rendering at 60fps (constitutional UX target)
- GeoJSON layer support — isochrone polygons rendered natively
- Active ecosystem; widely used in transit/mobility applications
- Angular integration via direct DOM ref (`ViewChild` + `afterViewInit`)

**Tile Provider**: **OpenFreeMap** (free, no key) or self-hosted MapTiler/Protomaps tiles — to be finalised in quickstart.

**Alternatives considered**:

| Alternative | Reason Rejected |
| --- | --- |
| Leaflet | No WebGL; lower performance with large GeoJSON; 60fps target at risk |
| OpenLayers | Heavier bundle; complex API; fewer momentum-driven features |
| Google Maps JS | API key required; cost; vendor lock-in |
| Mapbox GL JS | Mapbox ToS requires API key; proprietary after v2 |

---

## Decision 4: Spring Modulith Module Boundary

**Decision**: Create a new Spring Modulith module named **`isochrone`** under `com.mobilispect.backend.isochrone`.

**Rationale**:

- Isochrone concerns are distinct from stop, route, feed, region, and agency modules
- Module exposes a single public API: `IsochroneQueryService`
- Internal: `OtpClient`, `IsochroneCache`, `IsochroneController`
- No cross-module DB access — `isochrone` does not touch stop/route/feed tables directly
- Reads origin coordinates only (passed in by caller); does not query internal modules

---

## Decision 5: Travel Modes

**Decision**: Support **TRANSIT**, **WALK**, and **BICYCLE** modes mapped to OTP's `TraverseMode` enum.

**OTP mode mapping**:

| UI Label | OTP Modes |
| --- | --- |
| Transit | `TRANSIT,WALK` (transit + walk for access/egress) |
| Walk | `WALK` |
| Bike | `BICYCLE` |

**Cutoff Times (default options)**: 15, 30, 45, 60 minutes — configurable in request.

---

## Decision 6: API Design

**Decision**: New REST endpoint `GET /api/v1/isochrones` on Mobilispect backend (proxy + cache layer over OTP).

**Why backend proxy instead of direct OTP calls from frontend**:

- Keeps OTP internal (not exposed to internet)
- Applies Mobilispect authn/authz
- Centralises caching
- Allows future rate-limiting and cost control

---

## Decision 7: ADR Requirement

An ADR must be created for:

1. **OTP as routing engine** (significant architectural decision — external service dependency)
2. **MapLibre GL JS** (first map library in the frontend — significant new dependency)

These ADRs must be reviewed before implementation begins (constitutional requirement).

---

## Unknowns Resolved

| Unknown | Resolution |
| --- | --- |
| PostGIS needed? | No — OTP handles spatial computations; polygons delivered as GeoJSON |
| Angular map library | MapLibre GL JS (see Decision 3) |
| OTP deployment | Sidecar container in Docker Compose; fed from existing GTFS export pipeline |
| Cache TTL | 1 hour transit; 24 hours walk/bike |
| Tile provider | OpenFreeMap (free, no key) — finalised in quickstart |
| OSM data source | Geofabrik daily extracts for each metro region (Montreal, Toronto, Vancouver, Ottawa, SF Bay) |
| Frontend component | New Angular feature module `isochrone-map` |
| Auth on isochrone endpoint | Same session-based auth as other Mobilispect API endpoints |

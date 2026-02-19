# Quickstart: Isochrone Map Builder

**Feature**: Isochrone Map Builder
**Branch**: `claude/isochrone-map-builder-Adv0B`
**Date**: 2026-02-19

---

## What You're Building

An interactive isochrone map that shows areas reachable from any point in a transit
region within configurable time thresholds, for transit, walking, and cycling.

**User Journey**:
1. User opens the Isochrone Map page (new route: `/isochrone`)
2. User clicks a point on the map (or types an address)
3. User selects travel mode: Transit / Walk / Bike
4. User selects time cutoffs: 15 / 30 / 45 / 60 min (defaults)
5. Coloured polygons appear on the map showing reachable areas
6. User can toggle modes/times and polygons update

---

## Prerequisites

### 1. OTP Container Running

```bash
# Start OTP alongside other services
docker compose up otp -d

# Verify OTP is healthy
curl http://localhost:8080/otp/routers | jq '.routerInfo[].routerId'
# Expected: "default"
```

OTP needs GTFS + OSM data loaded. For local development with Montreal:

```bash
# Download Montreal OSM extract (Geofabrik)
mkdir -p .otp/graphs/default
curl -L https://download.geofabrik.de/north-america/canada/quebec-latest.osm.pbf \
  -o .otp/graphs/default/quebec.osm.pbf

# Copy a GTFS feed (after running a Mobilispect region import, or download directly)
# STM Montreal GTFS
curl -L https://www.stm.info/sites/default/files/gtfs/gtfs_stm.zip \
  -o .otp/graphs/default/stm-gtfs.zip

# Restart OTP to build the graph (takes 2-5 minutes)
docker compose restart otp

# Watch the build
docker compose logs -f otp | grep -E "(BUILD|ERROR|Graph built)"
```

### 2. Redis Running

```bash
docker compose up redis -d
redis-cli ping  # → PONG
```

### 3. Mobilispect Backend Running

```bash
./backend/gradlew -p backend bootRun
```

### 4. Angular Dev Server Running

```bash
cd frontend/web && npm start
# → http://localhost:4200
```

---

## Validating the Backend API

### Happy Path — Transit Isochrone (Montreal downtown)

```bash
curl -s "http://localhost:8090/api/v1/isochrones?lat=45.5017&lon=-73.5673&mode=TRANSIT&cutoffMinutes=15,30" \
  | jq '{mode: .mode, bands: [.bands[] | {cutoff: .cutoffMinutes, type: .geojson.geometry.type}], cached: .cached}'
```

**Expected**:
```json
{
  "mode": "TRANSIT",
  "bands": [
    { "cutoff": 15, "type": "MultiPolygon" },
    { "cutoff": 30, "type": "MultiPolygon" }
  ],
  "cached": false
}
```

### Second Request — Must Be Cached

```bash
curl -s "http://localhost:8090/api/v1/isochrones?lat=45.5017&lon=-73.5673&mode=TRANSIT&cutoffMinutes=15,30" \
  | jq '.cached'
# Expected: true
```

### Walk Mode

```bash
curl -s "http://localhost:8090/api/v1/isochrones?lat=45.5017&lon=-73.5673&mode=WALK&cutoffMinutes=15,30" \
  | jq '.mode, (.bands | length)'
# Expected: "WALK", 2
```

### Bike Mode

```bash
curl -s "http://localhost:8090/api/v1/isochrones?lat=45.5017&lon=-73.5673&mode=BICYCLE&cutoffMinutes=15,30" \
  | jq '.mode, (.bands | length)'
# Expected: "BICYCLE", 2
```

### Invalid Coordinates → 400

```bash
curl -s -o /dev/null -w "%{http_code}" \
  "http://localhost:8090/api/v1/isochrones?lat=999&lon=-73.5673"
# Expected: 400
```

### OTP Down → 502

```bash
docker compose stop otp
curl -s -o /dev/null -w "%{http_code}" \
  "http://localhost:8090/api/v1/isochrones?lat=45.5017&lon=-73.5673"
# Expected: 502
docker compose start otp
```

---

## Validating the Frontend

### 1. Navigate to the Isochrone Map

Open `http://localhost:4200/isochrone`

Expected: Map renders with base tiles (OpenFreeMap), no polygons yet.

### 2. Click a Point on the Map

Click anywhere on the Montreal area.

Expected:
- Loading spinner appears
- Coloured polygons appear within 3 seconds (first call, uncached)
- 4 colour bands visible (15/30/45/60 min)
- Legend visible showing time ↔ colour mapping

### 3. Switch Travel Mode

Click "Walk" tab.

Expected:
- Previous polygons fade out
- New walk-mode polygons appear (typically smaller, more circular than transit)
- Legend updates to reflect walk mode

### 4. Switch Travel Mode — Bike

Click "Bike" tab.

Expected:
- Bike polygons appear (larger than walk, directional)

### 5. Keyboard Navigation (Accessibility)

Tab through controls:
- Travel mode selector: focusable, arrow-key navigable
- Time cutoff checkboxes: all focusable, Enter/Space toggles
- Origin clear button: focusable, Enter clears selection

Screen reader: All controls have accessible labels; polygon layers have ARIA description.

### 6. Dark Mode

Toggle dark mode (theme button in app shell).

Expected:
- Map tiles switch to dark style
- Polygon colours remain distinguishable (WCAG 2.1 AA contrast maintained)
- Controls follow Material dark theme

---

## Running the Tests

### Backend Unit Tests (fast)

```bash
./backend/gradlew -p backend test --tests '*isochrone*' -x integrationTest
```

### Backend Contract Tests

```bash
./backend/gradlew -p backend test --tests '*IsochroneControllerContractTest'
```

### Backend Integration Tests (with Testcontainers)

```bash
./backend/gradlew -p backend integrationTest --tests '*IsochroneIntegrationTest'
```

### Frontend Unit Tests

```bash
cd frontend/web && npm test -- --testPathPattern=isochrone --watchAll=false
```

### E2E Tests (Playwright)

```bash
cd frontend/web && npm run e2e -- --grep="isochrone"
```

---

## Architecture Validation

### Verify Spring Modulith Boundary

```bash
./backend/gradlew -p backend test --tests '*ModularityTests'
```

Expected: All module boundary checks pass; no illegal cross-module access.

### Verify Cache Hit Rate (Prometheus metrics)

```bash
curl -s http://localhost:8090/actuator/metrics/isochrone.cache.hits | jq '.measurements[0].value'
curl -s http://localhost:8090/actuator/metrics/isochrone.cache.misses | jq '.measurements[0].value'
```

### Verify p95 Response Time (with warm cache)

```bash
# Run 20 requests against cached result
for i in $(seq 1 20); do
  curl -s -o /dev/null -w "%{time_total}\n" \
    "http://localhost:8090/api/v1/isochrones?lat=45.5017&lon=-73.5673&mode=TRANSIT&cutoffMinutes=15,30"
done
# p95 should be < 200ms (0.200 seconds)
```

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| 502 from backend | OTP not running | `docker compose up otp -d` |
| OTP returns empty features | Graph not built for that region | Ensure GTFS + OSM files in `.otp/graphs/default/` |
| Map tiles don't load | No internet / tile provider down | Check browser console for tile URL errors |
| 504 from backend | OTP graph too large / slow machine | Increase `otp.timeout-seconds` in `application.yml` |
| Cache not working | Redis not running | `docker compose up redis -d` |
| Angular build error | maplibre-gl not installed | `cd frontend/web && npm install` |

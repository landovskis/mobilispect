# Data Model: Average Stop Spacing

## Entities

### RouteVariant

- **Existing fields**: id, routeId, directionId, headsign, stopPattern,
  stopCount, firstStopId, lastStopId, active, firstSeen, lastSeen, createdAt,
  updatedAt
- **New fields**:
  - `averageStopSpacingKm` (decimal, nullable): average distance between
    consecutive stops in kilometers.

### StopSpacingSummary (DTO view)

- **Fields**:
  - `variantId` (string)
  - `averageStopSpacingKm` (decimal, nullable)
  - `classification` (enum: local, rapid, express, nullable when spacing not
    available)

## Relationships

- RouteVariant remains associated with Route; stop spacing is stored per
  RouteVariant.

## Validation Rules

- `averageStopSpacingKm` is nullable when fewer than two stops or missing shape
  distance data.
- Classification is derived from `averageStopSpacingKm` with thresholds:
  - local < 0.5 km
  - rapid 0.5–1.0 km (inclusive)
  - express > 1.0 km

## State Transitions

- Recomputed on each feed import for the variant's current stop pattern.

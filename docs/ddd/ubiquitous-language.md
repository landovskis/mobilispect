# Ubiquitous Language

Terms used consistently across code, specs, and conversations. When a new feature introduces a term not listed here, add it before writing implementation code.

| Term | Rust Type | Definition |
|------|-----------|------------|
| Agency | `AgencyId` | A transit operating company (e.g. STM). Top-level grouping for all routes. |
| Route | `RouteId` | A named service corridor. Has one or more variants. |
| Variant | `VariantId` | A specific stop-sequence pattern within a route. Different variants of the same route may serve different stops. |
| Trip | `TripId` | A single scheduled run of a route on a specific service day. |
| Stop | `StopId` | A physical or logical transit stop where passengers board or alight. |
| Service | `ServiceId` | A GTFS service calendar that defines which calendar days a trip operates. |
| Vehicle | `VehicleId` | A physical vehicle serving a trip in real time. |
| Direction | `DirectionId` | Inbound (0) or outbound (1) for a route. |
| Delay | `i64` (seconds) | Actual arrival minus scheduled arrival. Positive = late, negative = early. |
| Headway | seconds | Time gap between consecutive vehicles on the same route at a stop. |
| On-time Rate | `f64` (0.0–1.0) | Fraction of trips arriving within the configured early/late thresholds. |
| Speed | km/h | Average speed of a vehicle over a route segment or full run. |
| Dwell Time | seconds | Time a vehicle spends stopped at a station. |
| Route Daily | `(RouteId, Date)` | Aggregated performance metrics for a route on a specific calendar date. Computed from all trip results for that day. |
| Trip Result | — | The computed outcome of a single trip: on-time classification, avg delay, max delay. |
| Schedule Card | — | UI component displaying per-day-type headway statistics (Mon–Fri, Sat, Sun) for a route. |
| GTFS Static Feed | — | A ZIP archive of CSVs describing the planned timetable. Published by transit agencies. |
| GTFS-RT Feed | — | A protobuf stream of real-time vehicle positions and delay updates. |
| Early Threshold | `i64` (seconds) | How many seconds early a trip can arrive and still be considered on-time. Configured per agency. |
| Late Threshold | `i64` (seconds) | How many seconds late a trip can arrive and still be considered on-time. Configured per agency. |

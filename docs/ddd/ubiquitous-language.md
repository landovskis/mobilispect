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
| Direction | `DirectionId` | Outbound (0) or inbound (1) for a route. |
| Delay | `i64` (seconds) | Actual arrival minus scheduled arrival. Positive = late, negative = early. |
| Headway | minutes | Time gap between consecutive scheduled trips on the same route. Computed from the GTFS static timetable, not from real-time observations. |
| On-time Rate | `f64` (0–100) | Percentage of trips arriving within the configured early/late thresholds. |
| Speed | `f64` (m/s internally, displayed as km/h) | Average speed of a vehicle over a route segment or full run. |
| Dwell Time | seconds | Time a vehicle spends stopped at a station. |
| Route Daily | DB: `route_daily` | Aggregated performance metrics for a route on a specific calendar date. Computed from all trip results for that day. |
| Trip Result | `TripResult` | The computed outcome of a single trip: on-time flag (1/0), avg delay, and max delay in seconds. |
| Route Summary | `RouteSummary` | Aggregated per-route performance view over a date window: avg on-time %, avg delay, trips run/total, days measured. |
| Route Headway Row | `RouteHeadwayRow` | Scheduled headway statistics for a route broken down by day type (weekday, Saturday, Sunday), including median, top-decile, max, and service span. |
| Route Speed Summary | `RouteSpeedSummary` | Scheduled vs actual vs live speed for a route+direction, stored in m/s. |
| Route Speed Card | `RouteSpeedCard` | UI card aggregating scheduled speed, actual speed, avg stop spacing, dwell time, and classification across all directions and day types for a route. |
| Route Class | `RouteClass` | Classification of a route by avg stop spacing: Local (<500 m), Rapid (500–1500 m), Express (≥1500 m). |
| Daily Trend Point | `DailyTrendPoint` | One day of combined on-time and speed data for a route, used in trend charts. |
| Route Trend | `RouteTrend` | Ordered time series of `DailyTrendPoint` for a route, with derived speed-change percentage. |
| Schedule Card | — | UI component displaying per-day-type headway statistics (Mon–Fri, Sat, Sun) for a route. |
| GTFS Static Feed | — | A ZIP archive of CSVs describing the planned timetable. Published by transit agencies. |
| GTFS-RT Feed | — | A protobuf stream of real-time vehicle positions and delay updates. |
| Early Threshold | `i64` (seconds) | How many seconds early a trip can arrive and still be considered on-time. Configured per agency. |
| Late Threshold | `i64` (seconds) | How many seconds late a trip can arrive and still be considered on-time. Configured per agency. |

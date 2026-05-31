# Ubiquitous Language

Terms used consistently across code, specs, and conversations. When a new feature introduces a term not listed here, add it before writing implementation code.

| Term | Rust Type | Definition |
|------|-----------|------------|
| Region | `RegionId` | A geographic area served by one or more transit networks (e.g. Greater Montreal). Stored in DB; config-assigned id. |
| Network | `NetworkId` | A transit network serving a region, built from one or more feeds (e.g. Montreal Transit). Stored in DB; config-assigned id. |
| Feed | `FeedId` | A single GTFS data source: one static zip URL plus optional RT URLs. The ingest unit and partition key for all operational data. Replaces the former AgencyConfig concept. |
| Agency | `AgencyId` | The planning authority responsible for a set of routes. Ingested from GTFS `agency.txt`. Keyed by Transitland operator Onestop ID (e.g. `o-f25d-stm`). Distinct from operator (who physically runs vehicles, not modelled). |
| Station | `StationId` | A named interchange location (GTFS location_type=1) that contains one or more stops. Keyed by Transitland stop Onestop ID. |
| Route | `RouteId` | A named service corridor. Has one or more variants. Keyed by Transitland route Onestop ID (e.g. `r-f25e-14`). |
| Variant | `VariantId` | A specific stop-sequence pattern within a route direction. SHA-256 of the ordered canonical stop Onestop IDs. The same physical pattern retains its id across route renames or feed versions. |
| Trip | `TripId` | A single scheduled run of a variant on a specific service day. Belongs to a variant (not directly to a route). |
| Stop | `StopId` | A boarding/alighting location (GTFS location_type=0). Keyed by Transitland stop Onestop ID. De-duplicated across feeds. |
| Service | `ServiceId` | A GTFS service calendar that defines which calendar days a trip operates. Belongs to an agency within a feed. |
| Onestop ID | `String` | Transitland's globally stable canonical identifier. Prefixes: `o-` agency, `r-` route, `s-` stop/station. Used as the primary key for Agency, Route, Stop, and Station. |
| Vehicle | `VehicleId` | A physical vehicle serving a trip in real time. |
| Direction | `DirectionId` | Outbound (0) or inbound (1) for a route. |
| Delay | `i64` (seconds) | Actual arrival minus scheduled arrival. Positive = late, negative = early. |
| Headway | minutes | Time gap between consecutive scheduled trips on the same route. Computed from the GTFS static timetable, not from real-time observations. |
| On-time Rate | `f64` (0–100) | Percentage of trips arriving within the configured early/late thresholds. |
| Speed | `f64` (m/s internally, displayed as km/h) | Average speed of a vehicle over a route segment or full run. |
| Dwell Time | seconds | Time a vehicle spends stopped at a station. |
| Route Daily Stats | DB: `route_daily_stats` | Pre-aggregated performance metrics at the variant+date grain. Consolidates on-time, speed, and dwell metrics. Replaces `route_daily`, `route_speed_daily`, `route_speed_day_type`. |
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

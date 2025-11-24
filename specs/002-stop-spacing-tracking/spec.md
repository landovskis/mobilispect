# Feature Specification: Average Stop Spacing Tracking

**Feature Branch**: `002-stop-spacing-tracking`
**Created**: 2025-11-23
**Status**: Draft
**Input**: User description: "Average Stop Spacing Tracking. Track statistics for stop
spacing per route. Classify each route as local, rapid, or express. Enable agency-level
comparison of stop spacing across categories. Support cross-agency comparison within
metropolitan regions."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Route Stop Spacing Statistics (Priority: P1)

As a transit analyst, I want to view stop spacing statistics for individual routes so that I can understand service density and accessibility characteristics of each route.

**Why this priority**: Stop spacing is the foundational metric that enables all other analysis. Without per-route stop spacing data, no comparisons or classifications are possible.

**Independent Test**: Can be fully tested by selecting any route and verifying that stop spacing statistics (average, minimum, maximum, standard deviation) are displayed accurately.

**Acceptance Scenarios**:

1. **Given** a route with multiple stops, **When** I view the route details, **Then** I see the average stop spacing distance displayed
2. **Given** a route with multiple stops, **When** I view stop spacing statistics, **Then** I see minimum, maximum, and standard deviation of stop spacing
3. **Given** a route with stops, **When** I view the route, **Then** stop spacing is displayed in the user's preferred unit of measurement (kilometers or miles)

---

### User Story 2 - Route Service Type Classification (Priority: P2)

As a transit analyst, I want routes to be classified as local, rapid, or express based on their stop spacing characteristics so that I can quickly understand the service type and compare similar routes.

**Why this priority**: Classification enables meaningful comparisons between routes of similar service types and provides context for the spacing data.

**Independent Test**: Can be fully tested by viewing routes and verifying each has a classification label (local/rapid/express) that corresponds to its stop spacing characteristics.

**Acceptance Scenarios**:

1. **Given** a route with short average stop spacing (high frequency of stops), **When** I view the route, **Then** it is classified as "local"
2. **Given** a route with medium average stop spacing, **When** I view the route, **Then** it is classified as "rapid"
3. **Given** a route with long average stop spacing (limited stops), **When** I view the route, **Then** it is classified as "express"
4. **Given** any route, **When** I view its classification, **Then** I can see the criteria/thresholds used for the classification

---

### User Story 3 - Agency-Level Service Type Comparison (Priority: P2)

As a transit analyst, I want to compare stop spacing statistics across service types within a single agency so that I can understand how an agency differentiates its local, rapid, and express services.

**Why this priority**: Agency-level comparison provides actionable insights for transit planners evaluating service differentiation strategies.

**Independent Test**: Can be fully tested by selecting an agency and viewing aggregated stop spacing metrics broken down by service type (local/rapid/express).

**Acceptance Scenarios**:

1. **Given** an agency with routes of multiple service types, **When** I view the agency comparison, **Then** I see average stop spacing for local, rapid, and express routes displayed side-by-side
2. **Given** an agency comparison view, **When** I examine the data, **Then** I see the number of routes in each service type category
3. **Given** an agency with only one service type, **When** I view the agency comparison, **Then** I see data for the available service type with appropriate indication that other types are not present

---

### User Story 4 - Regional Cross-Agency Comparison (Priority: P3)

As a transit analyst, I want to compare stop spacing statistics across agencies within a metropolitan region so that I can benchmark agencies against each other and identify regional patterns.

**Why this priority**: Regional comparison enables benchmarking and strategic planning across a metropolitan area, but requires the foundational agency-level analysis to be complete first.

**Independent Test**: Can be fully tested by selecting a metropolitan region and viewing aggregated stop spacing metrics for each agency within that region, broken down by service type.

**Acceptance Scenarios**:

1. **Given** a metropolitan region with multiple agencies, **When** I view the regional comparison, **Then** I see stop spacing statistics for each agency organized by service type
2. **Given** a regional comparison view, **When** I examine the data, **Then** I can identify which agencies have the tightest local stop spacing and which have the longest express spacing
3. **Given** a regional comparison, **When** I view the results, **Then** I see regional averages for each service type alongside individual agency data

---

### Edge Cases

- **Insufficient stops**: Routes with fewer than 2 stops are marked as "insufficient data" (FR-008)
- **Route variants/branches**: Stop spacing is calculated per-variant and displayed as weighted average by trip count
- **Missing service types**: Agency comparison shows available service types with indication that others are not present
- **Threshold boundaries**: Routes on exact threshold boundaries are classified into the higher category (e.g., exactly 500m = rapid, not local)
- **Single-agency region**: Regional comparison still displays the single agency's data with regional averages equal to agency values
- **Directional differences**: Both directions are averaged together; variant weighting accounts for trip frequency in each direction

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST calculate average stop spacing (distance between consecutive stops) for each route, using weighted average by trip count when multiple route variants exist
- **FR-002**: System MUST calculate minimum, maximum, and standard deviation of stop spacing for each route
- **FR-003**: System MUST classify each route into one of three service types: local, rapid, or express
- **FR-004**: System MUST use configurable thresholds for service type classification boundaries at the metropolitan region level (global defaults apply when no regional override exists)
- **FR-005**: System MUST aggregate stop spacing statistics by service type at the agency level
- **FR-006**: System MUST aggregate stop spacing statistics by service type at the metropolitan region level
- **FR-007**: System MUST display stop spacing in user-selectable units (kilometers or miles)
- **FR-008**: System MUST handle routes with fewer than 2 stops by marking them as "insufficient data" rather than failing
- **FR-009**: System MUST calculate stop spacing based on actual geographic distance between stop locations
- **FR-010**: System MUST allow users to view the classification thresholds being applied
- **FR-011**: System MUST provide count of routes per service type in agency and regional comparisons
- **FR-012**: System MUST calculate regional averages for each service type across all agencies in the region

### Key Entities

- **Route**: A transit route operated by an agency; has multiple stops and belongs to a service type classification
- **Stop**: A location where passengers can board or alight; has geographic coordinates
- **Stop Spacing**: The distance between two consecutive stops on a route; measured in distance units
- **Service Type**: Classification of a route (local, rapid, or express) based on stop spacing characteristics
- **Agency**: A transit operator that manages multiple routes
- **Metropolitan Region**: A geographic area containing multiple transit agencies for cross-agency comparison
- **Classification Threshold**: Configurable boundaries that determine service type classification based on average stop spacing

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can view stop spacing statistics for any route within 2 seconds of selection
- **SC-002**: Route classification accuracy matches manual expert classification for 95% of routes when using appropriate threshold settings
- **SC-003**: Agency comparison view displays all service types with aggregated statistics in a single screen
- **SC-004**: Regional comparison successfully aggregates data from all agencies within the selected region
- **SC-005**: Users can switch between kilometers and miles without page reload
- **SC-006**: System handles agencies with 500+ routes without performance degradation

## Clarifications

### Session 2025-11-23

- Q: When should stop spacing statistics be calculated/recalculated? → A: Calculate during GTFS feed ingestion; recalculate on feed updates
- Q: At what level can classification thresholds be customized? → A: Per-region configurable thresholds (agencies in same region share thresholds)
- Q: How should routes with multiple variants/patterns be handled? → A: Calculate per-variant, display weighted average by trip count

## Assumptions

- Stop location data (geographic coordinates) is already available in the system from GTFS feeds
- Stop spacing statistics are calculated during GTFS feed ingestion and recalculated only when the feed is updated
- Route-to-stop relationships are already established in the system
- Metropolitan region boundaries and agency memberships are predefined in the system
- Default classification thresholds will be provided but are configurable:
  - Local: average stop spacing < 500 meters
  - Rapid: average stop spacing 500m - 1500 meters
  - Express: average stop spacing > 1500 meters
- Stop spacing is calculated using straight-line (geodesic) distance between consecutive stops
- Both directions of a route are considered together for stop spacing calculation (averaged)

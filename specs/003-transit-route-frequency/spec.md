# Feature Specification: Transit Route Frequency Analysis

**Feature Branch**: `003-transit-route-frequency`
**Created**: 2025-11-27
**Status**: Draft
**Input**: User description: "Transit frequency . You will import all the feeds for a region. You will group routes by agency. You will split routes into route variants.  You will show the frequency for each route variant and for the common section if there is one. You will group agencies by region"

## Clarifications

### Session 2025-11-27

- Q: How should route variants be uniquely identified in the system? → A: Generate a deterministic hash from the ordered stop sequence (content-based identifier stable across feed updates)
- Q: How will the system discover and locate GTFS feeds for agencies in a region? → A: Use Transitland API to discover feeds by region
- Q: What platform(s) should provide the user interface for this feature? → A: Web application with responsive design (works on desktop and mobile browsers)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Regional Transit Frequency Overview (Priority: P1)

Transit planners and analysts need to quickly understand service frequency patterns across all agencies operating within a region to identify service gaps and optimize resource allocation.

**Why this priority**: This is the core value proposition - providing a regional view of transit frequency enables informed decision-making about service planning and resource distribution. Without this foundational view, analysts cannot perform effective regional transit analysis.

**Independent Test**: Can be fully tested by selecting a region and viewing all agencies with their route counts and aggregate frequency statistics, delivering immediate value for regional service assessment.

**Acceptance Scenarios**:

1. **Given** a user selects a metropolitan region, **When** they view the regional dashboard, **Then** they see all transit agencies grouped and organized by that region with basic frequency metrics
2. **Given** a region with multiple agencies, **When** the user views agency details, **Then** they see all routes operated by each agency with frequency indicators
3. **Given** a user needs to compare agencies, **When** they view the regional overview, **Then** agencies are sorted by total routes and service frequency for easy comparison

---

### User Story 2 - Analyze Route Variants and Frequencies (Priority: P2)

Users need to understand how individual routes operate with different service patterns (route variants) and the specific frequency of each variant to identify where service is concentrated or lacking.

**Why this priority**: Route variant analysis provides the detailed operational view needed for service optimization. This builds on P1 by adding granular route-level insights that help identify specific improvement opportunities.

**Independent Test**: Can be independently tested by selecting any route and viewing its variants with frequency calculations, proving value for detailed route analysis even without the common section analysis.

**Acceptance Scenarios**:

1. **Given** a user selects a specific route, **When** they view route details, **Then** they see all variants of that route with their distinct stop patterns and frequencies
2. **Given** a route with multiple directional variants (inbound/outbound), **When** the user examines variants, **Then** each direction shows its own frequency calculated from scheduled trips
3. **Given** a route variant operates on different schedules, **When** frequency is displayed, **Then** users see frequency broken down by time period (weekday peak, weekday off-peak, weekend)
4. **Given** a user wants to understand variant differences, **When** viewing route variants, **Then** they can see the specific stop sequence that defines each variant

---

### User Story 3 - Identify Common Sections with Combined Frequency (Priority: P3)

Advanced users need to identify route segments where multiple routes or variants overlap (common sections) to understand true service availability and passenger experience along shared corridors.

**Why this priority**: Common section analysis provides sophisticated corridor-level insights that reveal the actual service level passengers experience when multiple routes serve the same segment. This is valuable for advanced planning but not essential for basic frequency analysis.

**Independent Test**: Can be independently tested by selecting routes with known overlapping segments and verifying that common sections are identified with combined frequency calculations, delivering specialized value for corridor planning.

**Acceptance Scenarios**:

1. **Given** multiple routes share 3 or more consecutive stops, **When** a user views route details, **Then** the common section is identified and highlighted with its geographic extent
2. **Given** a common section exists between routes, **When** frequency is calculated, **Then** the system shows combined frequency by summing individual route frequencies during each time period
3. **Given** a user examines a common section, **When** they view details, **Then** they see which routes contribute to the combined frequency with individual and total headways
4. **Given** route variants have different common sections, **When** analyzing variants, **Then** each variant's participation in common sections is clearly indicated

---

### User Story 4 - Import and Process Regional Transit Data (Priority: P4)

System administrators need to import and process transit feed data for regions to ensure frequency analysis is based on current schedule information.

**Why this priority**: This is foundational infrastructure that enables all other user stories but doesn't directly provide user-facing value. It's necessary but should be automated and require minimal user intervention.

**Independent Test**: Can be independently tested by importing feeds for a test region and verifying that routes, variants, and frequencies are correctly calculated and available for display.

**Acceptance Scenarios**:

1. **Given** a user specifies a region, **When** they initiate feed import, **Then** the system fetches all transit feeds for agencies operating in that region
2. **Given** feeds are imported, **When** processing completes, **Then** routes are organized by agency and route variants are identified based on stop patterns
3. **Given** feed data contains schedule information, **When** processing occurs, **Then** frequency is calculated for each route variant by time period
4. **Given** feeds contain overlapping route segments, **When** processing completes, **Then** common sections are identified and combined frequencies are calculated

---

### Edge Cases

- What happens when a region has no transit agencies? Display an informative message indicating no transit service data is available for the selected region.
- What happens when an agency has no active routes? Show the agency in the list but indicate no current service with a clear status message.
- What happens when a route has irregular schedules with no fixed frequency? Display "Variable Schedule" instead of frequency and provide next departure times.
- What happens when route variants have minimal differences (only 1-2 stops different)? Still treat as separate variants but provide visual indication that variants are similar.
- What happens when common sections have complex overlapping patterns with multiple routes? Display all contributing routes and show frequency ranges if patterns vary significantly by time period.
- What happens when feed data is incomplete or contains errors? Log specific errors, process valid data, and flag problematic routes/agencies with warning indicators.
- What happens when importing feeds for large regions with many agencies? Provide progress indicators and allow background processing to prevent timeouts.
- How does the system handle timezone differences across regions? Store all times in UTC internally and display in the local timezone of the selected region.
- What happens when feeds are updated with schedule changes? Allow incremental updates that refresh affected routes without reprocessing entire regions.
- What happens when users select date ranges spanning service calendar changes? Display frequency based on the service calendar active for the selected date/time period.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST import transit feed data for specified geographic regions from standard GTFS feed sources
- **FR-001a**: System MUST query Transitland API to discover GTFS feed URLs for agencies operating within a specified region
- **FR-002**: System MUST parse and extract route, trip, stop, and schedule information from imported feeds
- **FR-003**: System MUST organize and display transit agencies grouped by their operating region
- **FR-004**: System MUST display all routes grouped under their respective transit agencies
- **FR-005**: System MUST identify route variants by analyzing stop patterns and trip sequences for each route
- **FR-005a**: System MUST generate deterministic identifiers for route variants by hashing the ordered stop sequence to ensure stable identification across feed updates
- **FR-006**: System MUST calculate service frequency (headway) for each route variant based on scheduled trip times
- **FR-007**: System MUST calculate frequency separately for different time periods: weekday peak hours (6-9 AM, 4-7 PM), weekday off-peak hours, and weekend/holiday schedules
- **FR-008**: System MUST identify common sections where multiple routes or variants share 3 or more consecutive stops in the same sequence
- **FR-009**: System MUST calculate combined frequency for common sections by summing the frequency of all contributing routes/variants during each time period
- **FR-010**: Users MUST be able to select a geographic region and view all agencies operating within that region
- **FR-011**: Users MUST be able to select an agency and view all routes operated by that agency with frequency indicators
- **FR-012**: Users MUST be able to select a route and view all variants with their stop patterns and individual frequencies
- **FR-013**: Users MUST be able to view common sections for routes and see which routes contribute to the combined frequency
- **FR-014**: System MUST handle feeds with varying data quality by processing valid data and flagging errors without failing completely
- **FR-015**: System MUST store all schedule times in UTC internally and convert to regional timezone for display
- **FR-016**: System MUST provide progress indicators during feed import and processing operations
- **FR-017**: System MUST support incremental feed updates to refresh changed routes without full reprocessing
- **FR-018**: System MUST display clear status indicators for routes with irregular schedules that have no fixed frequency pattern
- **FR-019**: System MUST allow users to select date ranges and display frequency based on the service calendar active during that period
- **FR-020**: System MUST persist imported feed data and calculated frequencies for historical analysis and comparison
- **FR-021**: System MUST provide a responsive web interface that adapts to desktop and mobile browser viewports
- **FR-022**: Web interface MUST support common desktop browsers (Chrome, Firefox, Safari, Edge) and mobile browsers (iOS Safari, Chrome Mobile)

### Key Entities

- **Region**: Geographic area containing one or more transit agencies; defined by administrative boundaries (metropolitan areas, counties, states); serves as the top-level organizational unit for transit analysis
- **Agency**: Transit operator providing public transportation service; has unique identifier, name, and operates within one or more regions; owns multiple routes
- **Route**: Named transit line operated by an agency (e.g., "Route 5 Downtown Express"); identified by route number/name and has one or more variants based on service patterns
- **Route Variant**: Specific service pattern for a route defined by its unique sequence of stops; uniquely identified by a deterministic hash of the ordered stop sequence; represents different directional paths (inbound/outbound) or branching patterns; has distinct frequency calculations; identifier remains stable across feed updates when stop pattern is unchanged
- **Common Section**: Geographic segment where multiple routes or variants overlap; defined by 3 or more consecutive shared stops in the same sequence; has combined frequency calculated from all contributing routes
- **Frequency**: Service headway representing average time between vehicle departures; calculated separately for different time periods (peak, off-peak, weekend); measured in minutes and may vary throughout the day
- **Stop Pattern**: Ordered sequence of stops served by a route variant; used to identify variants and detect common sections; includes stop identifiers and geographic coordinates
- **Time Period**: Temporal classification for frequency calculation; includes weekday AM peak (6-9 AM), weekday PM peak (4-7 PM), weekday off-peak, weekend, and holiday schedules
- **Transit Feed**: Standard GTFS data package containing routes, stops, trips, and schedules for an agency; imported and processed to calculate frequencies; has effective date range and version information

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can select a region and view all agencies with their routes within 2 seconds of request
- **SC-002**: Frequency calculations are accurate to within 1 minute compared to scheduled departure times in source feeds
- **SC-003**: System correctly identifies route variants with 95% accuracy when compared to agency-published service patterns
- **SC-004**: Common sections are correctly identified when routes share 3 or more consecutive stops in the same direction and sequence
- **SC-005**: System processes feed data for a metropolitan region containing up to 20 agencies within 5 minutes
- **SC-006**: Users can complete the workflow of selecting a region, finding an agency, viewing a route, and seeing its frequency analysis within 30 seconds
- **SC-007**: 90% of users successfully understand route frequency patterns on first viewing without requiring additional help or documentation
- **SC-008**: System handles feed data updates and incrementally refreshes affected routes within 10 minutes
- **SC-009**: Interface displays frequency data for up to 100 routes simultaneously without performance degradation
- **SC-010**: System maintains 99.5% uptime during feed import and processing operations

## Dependencies *(if applicable)*

### External Systems

- **GTFS Feed Sources**: System depends on availability and quality of standard GTFS (General Transit Feed Specification) feeds from transit agencies; feeds must be accessible via public URLs or APIs
- **Transitland API**: System uses Transitland API to discover GTFS feed URLs for agencies within specified regions; provides curated feed catalog with metadata and quality indicators
- **Geographic Data**: Requires geographic boundary definitions for regions (metropolitan areas, counties) to group agencies appropriately
- **Timezone Data**: Depends on standard timezone databases to correctly convert schedule times for display

### Data Requirements

- **Valid GTFS Feeds**: Each transit agency must provide feeds containing at minimum: agency.txt, routes.txt, trips.txt, stops.txt, stop_times.txt, and calendar.txt files
- **Stop Coordinates**: Stop data must include accurate latitude/longitude coordinates for geographic analysis and common section detection
- **Schedule Data**: Feed data must include detailed stop_times.txt with arrival/departure times for frequency calculations
- **Service Calendar**: Calendar.txt and calendar_dates.txt files must accurately represent service availability by date and day of week

## Assumptions

- **Frequency Calculation Method**: Frequency will be calculated as the average headway during defined time periods (weekday peak, off-peak, weekend) using scheduled departure times from GTFS stop_times data
- **Common Section Definition**: Common sections are defined as 3 or more consecutive stops shared between routes in the same sequence and direction; shorter overlaps are not considered significant
- **Region Definitions**: Regions are predefined based on standard metropolitan statistical areas, counties, or state boundaries; custom user-defined regions are not supported in the initial implementation
- **Feed Update Frequency**: Transit feeds will be updated on-demand or daily, depending on agency publication schedules; real-time updates are not included in this feature scope
- **Time Period Definitions**: Standard time periods are defined as: Weekday AM Peak (6:00-9:00 AM), Weekday PM Peak (4:00-7:00 PM), Weekday Off-Peak (all other weekday hours), Weekend (Saturday-Sunday all day), Holiday (based on calendar_dates.txt)
- **Route Variant Identification**: Route variants are identified algorithmically by comparing stop patterns; agency-provided variant names or descriptions are not relied upon
- **Frequency Display**: When frequency varies significantly within a time period, the system will display average frequency with a range indicator
- **Language Support**: All interface text and agency data will be displayed in English; multilingual support is not included in this feature scope
- **Historical Data**: System will retain imported feed data and frequency calculations for up to 2 years for historical comparison and trend analysis
- **User Authentication**: Access to frequency analysis features does not require user authentication; all data is considered public information from publicly available GTFS feeds
- **User Interface Platform**: Feature will be delivered as a responsive web application accessible through desktop and mobile browsers; native mobile apps are not included in this scope

## Module Ownership *(Spring Modulith)*

This feature will be implemented within the following Spring Modulith module:

- **Module Name**: `transit-analysis` (or `schedule-analysis` if renaming existing module)
- **Responsibilities**: Import and process transit feed data, calculate service frequencies, identify route variants, detect common sections, provide API for frequency analysis queries
- **Public API**: Exposes REST endpoints for region/agency/route/frequency queries and feed import operations
- **Events Published**: `FeedImportCompleted`, `FrequencyCalculationCompleted`, `RouteVariantIdentified`
- **Events Consumed**: None (this is a foundational analysis module)
- **Database Access**: Owns tables for regions, agencies, routes, route_variants, common_sections, frequencies, and imported feed data
- **Dependencies**: No cross-module database access; communicates with other modules via published events and REST APIs

**Module Boundary Enforcement**: All access to transit frequency data from other modules must occur through the public REST API. Direct database access from other modules is prohibited by Spring Modulith architecture constraints.

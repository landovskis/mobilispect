# Feature Specification: Average Stop Spacing

**Feature Branch**: `001-stop-spacing-classification`
**Created**: 2025-12-20
**Status**: Draft
**Input**: User description: "Average Stop Spacing. Display the average on a
per route variant basis on the route detail page. Based on the route variants
you will display the local, rapid or express classification"

## Clarifications

### Session 2025-12-20

- Q: What distance basis should be used for spacing calculations? → A:
  Along-route distance between stops.
- Q: How should spacing values be rounded for display? → A: Two decimal places.
- Q: Should classification allow manual overrides? → A: No, classification is
  derived solely from spacing thresholds.
- Q: How should spacing thresholds treat boundary values? → A: Use upper-bound
  inclusive ranges (e.g., rapid includes 1.0 km).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View stop spacing per variant (Priority: P1)

As a service planner viewing a route detail page, I want to see the average
stop spacing for each route variant and its service classification so I can
quickly understand the service type for that variant.

**Why this priority**: This is the core value of the feature and provides
immediate insight for route analysis.

**Independent Test**: Can be fully tested by opening a route detail page with
multiple variants and verifying the spacing and classification display for
each variant.

**Acceptance Scenarios**:

1. **Given** a route detail page with route variants that have stop spacing
   data, **When** I view the variants list, **Then** each variant shows its
   average stop spacing value and a classification label (local, rapid, or
   express).
2. **Given** a route detail page with a variant lacking sufficient stop spacing
   data, **When** I view the variants list, **Then** the spacing value is shown
   as "Not available" and no classification label is shown.

---

### User Story 2 - Compare variants consistently (Priority: P2)

As a service planner comparing multiple variants, I want the spacing values to
use a consistent unit so comparisons are clear and reliable.

**Why this priority**: Consistent units prevent confusion and support accurate
side-by-side comparison.

**Independent Test**: Can be tested by verifying that all displayed spacing
values on a route detail page use the same unit label.

**Acceptance Scenarios**:

1. **Given** a route detail page with multiple variants, **When** I review
   spacing values, **Then** each value is labeled with the same unit.

---

### Edge Cases

- What happens when a variant has fewer than two stops and no spacing can be
  calculated?
- How does the system handle variants with missing or incomplete stop data?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST display the average stop spacing for each route
  variant on the route detail page.
- **FR-002**: The system MUST display a service classification label (local,
  rapid, or express) for each variant when spacing data is available.
- **FR-003**: The system MUST use a single, consistent unit for all displayed
  spacing values on the route detail page.
- **FR-004**: The system MUST show "Not available" and omit classification when
  a variant lacks sufficient spacing data.
- **FR-005**: The system MUST classify variants using these spacing thresholds
  with upper-bound inclusive ranges: local < 0.5 km, rapid 0.5–1.0 km
  (inclusive), express > 1.0 km.
- **FR-006**: The system MUST label spacing values in kilometers (km).
- **FR-007**: The system MUST compute average stop spacing using along-route
  distance between consecutive stops.
- **FR-008**: The system MUST display spacing values rounded to two decimal
  places.
- **FR-009**: The system MUST derive classification solely from spacing
  thresholds without manual overrides.

### Key Entities *(include if feature involves data)*

- **Route Variant**: A unique service pattern for a route, with associated
  stops and a derived average stop spacing value.
- **Stop Spacing Summary**: The average spacing value for a variant plus its
  service classification label.

### Assumptions

- Route variants are already displayed on the route detail page and can be
  extended with additional summary data.
- Variants with fewer than two stops do not have a meaningful spacing value.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of route variants with sufficient stop data display an
  average stop spacing value and classification label.
- **SC-002**: 100% of route variants with insufficient stop data display "Not
  available" and no classification label.
- **SC-003**: Users can identify the service classification for all variants
  of a route with up to 10 variants in under 10 seconds during usability
  testing.

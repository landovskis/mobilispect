package com.mobilispect.backend.transitanalysis.domain.service

/**
 * Service for detecting common sections where multiple routes/variants overlap.
 *
 * A common section is a sequence of 3 or more consecutive stops shared between
 * multiple route variants. These sections are useful for calculating combined
 * frequency on corridors served by multiple routes.
 *
 * Constitutional Requirements:
 * - FR-013: Identify common sections with minimum 3 consecutive stops
 * - FR-014: Common sections defined by exact stop sequence and order
 * - FR-015: Multiple variants can share the same common section
 * - SC-004: Correctly identify sections when routes share 3+ consecutive stops
 * - Constitutional Constraint: Minimum 3 stops per section (DB check constraint)
 *
 * Module Boundary: Domain service in transit-analysis module
 *
 * TODO: Implement following TDD - tests exist in CommonSectionDetectionServiceTest
 */
interface CommonSectionDetectionService {
    // TODO: Define interface methods based on common section detection requirements
}

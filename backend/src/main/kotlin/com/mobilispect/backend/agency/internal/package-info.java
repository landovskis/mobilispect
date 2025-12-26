/**
 * Internal implementation details for the Agency module.
 *
 * This package contains service implementations and internal logic that should
 * not be accessed directly by other modules. Other modules must use the
 * AgencyQueryApi in the agency.api package instead.
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 */
@org.springframework.modulith.NamedInterface("internal")
package com.mobilispect.backend.agency.internal;

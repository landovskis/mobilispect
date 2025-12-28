package com.mobilispect.backend.region

import org.springframework.modulith.ApplicationModule

/**
 * Region Management Module.
 *
 * This module is responsible for managing metropolitan regions and their metadata. It is a
 * foundational module with no dependencies on other domain modules.
 *
 * Public API:
 * - region.api.RegionQueryApi: Query interface for region data
 * - region.api.RegionDTO: Data transfer object for cross-module communication
 *
 * Database Ownership:
 * - metropolitan_regions table
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 * - This module has no domain dependencies
 */
@ApplicationModule(displayName = "Region Management", allowedDependencies = []) class RegionModule

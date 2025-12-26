package com.mobilispect.backend.stop

import org.springframework.modulith.ApplicationModule

/**
 * Stop Management Module
 *
 * Responsible for managing transit stop locations and metadata.
 * Extracted from transitanalysis module as part of modular monolith refactoring.
 *
 * This module:
 * - Manages stop entities (physical locations where passengers board/alight)
 * - Provides stop query APIs for cross-module communication
 * - Validates stop data and locations
 * - Tracks stop lifecycle (first seen, last seen, active status)
 *
 * Dependencies:
 * - feed: For feed association and validation (via FeedQueryApi)
 *
 * Constitutional Requirement: Modular Monolith Ownership
 * - No cross-module database access
 * - Communication via ports (StopQueryApi) only
 * - Module boundaries enforced by Spring Modulith
 */
@ApplicationModule(
    displayName = "Stop Management",
    allowedDependencies = ["feed"]
)
class StopModule

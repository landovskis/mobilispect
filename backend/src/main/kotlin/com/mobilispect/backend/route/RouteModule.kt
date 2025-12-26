package com.mobilispect.backend.route

import org.springframework.modulith.ApplicationModule

/**
 * Route Module - Manages routes, route variants, frequencies, and common sections.
 *
 * This module is responsible for:
 * - Managing transit routes and their variants
 * - Calculating route frequencies
 * - Detecting common sections between routes
 * - Identifying route variants based on stop patterns
 *
 * Dependencies:
 * - feed: For feed-related data validation
 * - agency: For agency-related data validation
 *
 * Exposed API:
 * - RouteQueryApi: Public API for querying routes
 *
 * Internal packages (not accessible by other modules):
 * - data: Data layer with JPA entities and repositories
 * - domain: Domain models, repositories, and services
 * - internal: Internal API implementations
 */
@ApplicationModule(
    displayName = "Route Management",
    allowedDependencies = ["feed", "agency"]
)
class RouteModule

package com.mobilispect.backend.security

import org.springframework.context.annotation.Configuration

/**
 * Security configuration for feed management endpoints.
 *
 * Task T006: Implement authentication/authorization for feed management roles
 *
 * This configuration defines the security requirements for feed management operations based on the
 * role-based access control model specified in FR-014.
 *
 * Access Control Matrix:
 * - /api/feeds/regions (GET): VIEWER, OPERATOR, MANAGER, ADMIN
 * - /api/feeds/regions/{id} (GET): VIEWER, OPERATOR, MANAGER, ADMIN
 * - /api/feeds/regions/{id} (PATCH): MANAGER, ADMIN
 * - /api/feeds/regions/{id}/discover (POST): OPERATOR, MANAGER, ADMIN
 * - /api/feeds/feeds/{id}/import (POST): OPERATOR, MANAGER, ADMIN
 * - /api/feeds/imports (GET): VIEWER, OPERATOR, MANAGER, ADMIN, AUDITOR
 * - /api/feeds/imports/{id} (DELETE): OPERATOR, MANAGER, ADMIN
 * - /api/feeds/imports/{id}/logs (GET): VIEWER, OPERATOR, MANAGER, ADMIN, AUDITOR
 *
 * Note: This is a placeholder configuration. Full Spring Security integration requires:
 * 1. SecurityFilterChain bean configuration
 * 2. AuthenticationManager configuration
 * 3. UserDetailsService implementation
 * 4. @PreAuthorize annotations on controller methods
 * 5. Integration with existing authentication system
 *
 * Implementation of these components should be coordinated with existing security infrastructure to
 * avoid conflicts.
 */
@Configuration
class FeedManagementSecurityConfiguration {

  /** Returns the list of roles that have viewer access. */
  fun viewerRoles(): List<String> =
    listOf(
      FeedManagementRoles.VIEWER,
      FeedManagementRoles.OPERATOR,
      FeedManagementRoles.MANAGER,
      FeedManagementRoles.ADMIN,
    )

  /** Returns the list of roles that have operator access. */
  fun operatorRoles(): List<String> =
    listOf(FeedManagementRoles.OPERATOR, FeedManagementRoles.MANAGER, FeedManagementRoles.ADMIN)

  /** Returns the list of roles that have manager access. */
  fun managerRoles(): List<String> = listOf(FeedManagementRoles.MANAGER, FeedManagementRoles.ADMIN)

  /** Returns the list of roles that have auditor access. */
  fun auditorRoles(): List<String> = listOf(FeedManagementRoles.AUDITOR, FeedManagementRoles.ADMIN)
}

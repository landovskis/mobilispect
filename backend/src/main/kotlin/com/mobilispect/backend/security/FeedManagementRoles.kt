package com.mobilispect.backend.security

/**
 * Role definitions for feed management system access control.
 *
 * Task T006: Implement authentication/authorization for feed management roles Per FR-014: Viewers
 * can monitor progress and history, operators can initiate imports, managers can configure
 * automatic updates
 */
object FeedManagementRoles {
  /**
   * VIEWER role: Can monitor import progress and view history
   * - View regions and feeds
   * - View import history
   * - Monitor import progress
   * - View system dashboards
   */
  const val VIEWER = "ROLE_FEED_VIEWER"

  /**
   * OPERATOR role: Can initiate and cancel imports (includes all VIEWER permissions)
   * - All VIEWER permissions
   * - Initiate feed imports
   * - Cancel in-progress imports
   * - Trigger manual feed discovery
   */
  const val OPERATOR = "ROLE_FEED_OPERATOR"

  /**
   * MANAGER role: Can configure system settings (includes all OPERATOR permissions)
   * - All OPERATOR permissions
   * - Configure automatic update settings
   * - Manage feed authentication credentials
   * - Configure region settings
   * - Manage scheduled jobs
   */
  const val MANAGER = "ROLE_FEED_MANAGER"

  /**
   * ADMIN role: Full system access (includes all MANAGER permissions)
   * - All MANAGER permissions
   * - System administration
   * - User management
   */
  const val ADMIN = "ROLE_ADMIN"

  /**
   * AUDITOR role: Read-only access to all audit logs and history
   * - View all import logs
   * - View audit trails
   * - Export audit reports
   */
  const val AUDITOR = "ROLE_AUDITOR"
}

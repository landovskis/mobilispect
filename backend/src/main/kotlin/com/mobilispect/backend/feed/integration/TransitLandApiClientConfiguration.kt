package com.mobilispect.backend.feed.integration

import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.springframework.context.annotation.Configuration

/**
 * Configuration for Transit.land API client integration within the feed management domain.
 *
 * This configuration provides the feed management module with access to the Transit.land API
 * for feed discovery and version checking operations.
 *
 * Task T008: Setup Transit.land API client configuration
 */
@Configuration
class TransitLandApiClientConfiguration(
    private val transitLandAPI: TransitLandAPI,
    private val credentialsRepository: TransitLandCredentialsRepository
) {
    /**
     * Returns the configured Transit.land API client for feed operations.
     */
    fun apiClient(): TransitLandAPI = transitLandAPI

    /**
     * Returns the credentials repository for Transit.land API authentication.
     */
    fun credentials(): TransitLandCredentialsRepository = credentialsRepository
}

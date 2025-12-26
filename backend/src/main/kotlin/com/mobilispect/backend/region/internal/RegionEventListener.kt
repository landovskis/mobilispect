package com.mobilispect.backend.region.internal

import com.mobilispect.backend.feed.events.RegionDiscoveredEvent
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import com.mobilispect.backend.region.domain.MetropolitanRegion
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Event listener for Region module domain events.
 *
 * This listener handles events published by other modules (like Feed discovery)
 * and performs region-related operations within proper module boundaries.
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - Region module owns region data
 * - Other modules communicate via events
 * - No cross-module database access
 */
@Component
internal class RegionEventListener(
    private val regionRepository: MetropolitanRegionRepository
) {
    private val logger = LoggerFactory.getLogger(RegionEventListener::class.java)

    /**
     * Handles region discovery events from feed discovery process.
     *
     * Creates new regions or updates existing ones based on discovered metadata.
     */
    @EventListener
    @Transactional
    fun handleRegionDiscovered(event: RegionDiscoveredEvent) {
        logger.debug("Handling RegionDiscoveredEvent for region: {}", event.regionId)

        val existingRegion = regionRepository.findByRegionOnestopId(event.regionId)
            .orElse(null)

        val regionEntity = if (existingRegion != null) {
            // Update existing region
            logger.debug("Updating existing region: {}", event.name)
            existingRegion.apply {
                name = event.name
                adm0Name = event.adm0Name
                adm1Name = event.adm1Name
                updatedAt = Instant.now()
            }
        } else {
            // Create new region
            logger.info("Creating new region: {} ({})", event.name, event.regionId)
            MetropolitanRegion(
                regionOnestopId = event.regionId,
                name = event.name,
                adm0Name = event.adm0Name,
                adm1Name = event.adm1Name,
                autoUpdateEnabled = true
            )
        }

        regionRepository.save(regionEntity)
        logger.debug("Region saved: {}", event.regionId)
    }
}

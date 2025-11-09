package com.mobilispect.backend.feed.service

import com.mobilispect.backend.TransitLandOperator
import com.mobilispect.backend.TransitLandPlace
import com.mobilispect.backend.feed.integration.TransitLandApiClient
import com.mobilispect.backend.feed.model.MetropolitanRegion
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service to migrate orphaned feeds (feeds with no region assignments) to their appropriate regions.
 * Uses the same geohash extraction logic as FeedDiscoveryService.
 */
@Service
class FeedRegionMigrationService(
    private val feedRepository: FeedRepository,
    private val regionRepository: MetropolitanRegionRepository,
    private val transitLandApiClient: TransitLandApiClient
) {
    private val logger = LoggerFactory.getLogger(FeedRegionMigrationService::class.java)

    /**
     * Migrate all orphaned feeds to appropriate regions.
     * Returns the number of feeds migrated.
     */
    @Transactional
    suspend fun migrateOrphanedFeeds(): MigrationResult {
        logger.info("Starting migration of orphaned feeds...")

        // Find all feeds with no region assignments
        val allFeeds = feedRepository.findAll()
        val orphanedFeeds = allFeeds.filter { it.regions.isEmpty() }

        logger.info("Found {} orphaned feeds out of {} total feeds", orphanedFeeds.size, allFeeds.size)

        if (orphanedFeeds.isEmpty()) {
            logger.info("No orphaned feeds to migrate")
            return MigrationResult(0, 0, emptyList())
        }

        // Build operator places map
        val operatorPlacesMap = try {
            logger.info("Fetching operator data from Transit.land...")
            val operators = transitLandApiClient.discoverAllOperators()
            buildOperatorPlacesMap(operators)
        } catch (ex: Exception) {
            logger.warn("Failed to fetch operators, using fallback region extraction", ex)
            emptyMap<String, List<TransitLandPlace>>()
        }

        logger.info("Built operator places map for {} operators", operatorPlacesMap.size)

        var migratedCount = 0
        val errors = mutableListOf<String>()

        orphanedFeeds.forEach { feed ->
            try {
                val regionIds = extractRegionsFromPlaces(
                    feed.feedOnestopId,
                    feed.name,
                    operatorPlacesMap
                )

                // Ensure all regions exist
                val regionRefs = regionIds.mapNotNull { regionId ->
                    regionRepository.findById(regionId).orElse(null)
                }

                if (regionRefs.isNotEmpty()) {
                    feed.regions.addAll(regionRefs)
                    feedRepository.save(feed)
                    migratedCount++
                    logger.debug("Migrated feed {} to regions: {}", feed.feedOnestopId, regionIds.joinToString(", "))
                } else {
                    logger.warn("No valid regions found for feed {}", feed.feedOnestopId)
                    errors.add("No valid regions for ${feed.feedOnestopId}")
                }
            } catch (ex: Exception) {
                logger.error("Failed to migrate feed {}", feed.feedOnestopId, ex)
                errors.add("${feed.feedOnestopId}: ${ex.message}")
            }
        }

        logger.info("Migration complete: {} feeds migrated, {} errors", migratedCount, errors.size)

        return MigrationResult(
            totalOrphaned = orphanedFeeds.size,
            migrated = migratedCount,
            errors = errors
        )
    }

    private fun buildOperatorPlacesMap(
        operators: List<TransitLandOperator>
    ): Map<String, List<TransitLandPlace>> {
        return operators.associate { operator ->
            val allPlaces = operator.agencies
                ?.flatMap { agency -> agency.places ?: emptyList() }
                ?: emptyList()
            operator.onestop_id.orEmpty() to allPlaces
        }.filterValues { it.isNotEmpty() }
    }

    private fun extractRegionsFromPlaces(
        feedOnestopId: String,
        feedName: String,
        operatorPlacesMap: Map<String, List<TransitLandPlace>>
    ): List<String> {
        val operatorId = feedOnestopId.replaceFirst("f-", "o-")
        val places = operatorPlacesMap[operatorId]

        if (places.isNullOrEmpty()) {
            logger.debug("No places found for operator {}, using fallback for feed {}", operatorId, feedOnestopId)
            return listOf(extractRegionFromFeedIdFallback(feedOnestopId, feedName))
        }

        val regionIds = mutableListOf<String>()
        val seenRegions = mutableSetOf<String>()

        places.forEach { place ->
            val regionParts = mutableListOf<String>()

            place.adm0_name?.let { regionParts.add(slugify(it)) }
            place.adm1_name?.let { regionParts.add(slugify(it)) }
            place.city_name?.let { regionParts.add(slugify(it)) }

            if (regionParts.isNotEmpty()) {
                val regionId = "r-${regionParts.joinToString("-")}"

                if (seenRegions.add(regionId)) {
                    val regionName = place.city_name ?: place.adm1_name ?: place.adm0_name ?: "Unknown"
                    val createdRegionId = ensureRegionExists(regionId, regionName, place.adm0_name, place.adm1_name)
                    regionIds.add(createdRegionId)
                }
            }
        }

        if (regionIds.isEmpty()) {
            logger.warn("No valid places for operator {}, using fallback", operatorId)
            return listOf(extractRegionFromFeedIdFallback(feedOnestopId, feedName))
        }

        return regionIds
    }

    private fun extractRegionFromFeedIdFallback(feedOnestopId: String, feedName: String): String {
        val parts = feedOnestopId.split("-", limit = 3)

        if (parts.size < 2) {
            val cleanName = slugify(feedName)
            return if (cleanName.isNotBlank()) {
                ensureRegionExists("r-$cleanName-auto", feedName)
            } else {
                "r-global-auto"
            }
        }

        val geohash = parts[1].split("~").first().take(30)

        if (geohash.isBlank()) {
            return "r-global-auto"
        }

        val normalizedGeohash = normalizeGeohashToMetroArea(geohash)
        val regionId = "r-$normalizedGeohash-auto"
        val regionName = getRegionNameFromGeohash(normalizedGeohash) ?: "Auto-region: $normalizedGeohash"

        return ensureRegionExists(regionId, regionName)
    }

    private fun normalizeGeohashToMetroArea(geohash: String): String {
        return when {
            geohash.startsWith("f25") -> "f25d"
            geohash.startsWith("9q8") || geohash.startsWith("9q9") -> "9q9"
            geohash.startsWith("9q5") -> "9q5"
            geohash.startsWith("f244") -> "f244"
            geohash.length >= 3 -> geohash.substring(0, 3)
            else -> geohash
        }
    }

    private fun getRegionNameFromGeohash(normalizedGeohash: String): String? {
        return when (normalizedGeohash) {
            "f25d" -> "Greater Montreal"
            "9q9" -> "San Francisco Bay Area"
            "9q5" -> "Vancouver"
            "f244" -> "Ottawa"
            else -> null
        }
    }

    private fun ensureRegionExists(
        regionId: String,
        regionName: String,
        adm0Name: String? = null,
        adm1Name: String? = null
    ): String {
        return regionRepository.findById(regionId).orElseGet {
            val region = MetropolitanRegion(
                regionOnestopId = regionId,
                name = regionName,
                adm0Name = adm0Name,
                adm1Name = adm1Name
            )
            regionRepository.save(region)
            logger.info("Created new region: {} ({})", regionId, regionName)
            region
        }.regionOnestopId
    }

    private fun slugify(name: String): String {
        return name
            .lowercase()
            .replace("united states of america", "usa")
            .replace("united kingdom", "uk")
            .replace(Regex("[àáâãäå]"), "a")
            .replace(Regex("[èéêë]"), "e")
            .replace(Regex("[ìíîï]"), "i")
            .replace(Regex("[òóôõö]"), "o")
            .replace(Regex("[ùúûü]"), "u")
            .replace(Regex("[ñ]"), "n")
            .replace(Regex("[ç]"), "c")
            .replace(Regex("[^a-z0-9]+"), "")
            .take(30)
    }

    data class MigrationResult(
        val totalOrphaned: Int,
        val migrated: Int,
        val errors: List<String>
    )
}

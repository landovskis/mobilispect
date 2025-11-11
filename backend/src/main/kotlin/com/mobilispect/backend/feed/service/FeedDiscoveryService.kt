package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.integration.TransitLandApiClient
import com.mobilispect.backend.feed.integration.TransitLandApiException
import com.mobilispect.backend.feed.integration.TransitLandAuthorizationSummary
import com.mobilispect.backend.feed.integration.TransitLandFeedSummary
import com.mobilispect.backend.feed.model.AuthType
import com.mobilispect.backend.feed.model.FeedAuthentication
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.MetropolitanRegion
import com.mobilispect.backend.feed.repository.FeedAuthenticationRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

data class FeedDiscoveryResult(
    val regionOnestopId: String,
    val feedsDiscovered: Int,
    val feedsCreated: Int,
    val feedsUpdated: Int,
    val errors: List<String>
)

@Service
class FeedDiscoveryService(
    private val regionRepository: MetropolitanRegionRepository,
    private val feedRepository: FeedRepository,
    private val feedAuthenticationRepository: FeedAuthenticationRepository,
    private val transitLandApiClient: TransitLandApiClient,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(FeedDiscoveryService::class.java)

    /**
     * Discover ALL feeds from Transit.land regardless of region.
     * Automatically extracts and assigns regions based on operator geographic metadata.
     */
    @Transactional
    suspend fun discoverAll(
        specType: FeedSpecType = FeedSpecType.GTFS,
        maxFeeds: Int = Int.MAX_VALUE
    ): FeedDiscoveryResult {
        val timerSample = Timer.start(meterRegistry)
        val globalRegionId = "global"

        // First, discover all operators to build a mapping of operator onestop_id to places
        logger.info("Discovering operators to build geographic metadata mapping...")
        val operatorPlacesMap = try {
            val operators = transitLandApiClient.discoverAllOperators()
            buildOperatorPlacesMap(operators)
        } catch (ex: Exception) {
            logger.warn("Failed to discover operators, proceeding with feed-only discovery", ex)
            emptyMap<String, List<com.mobilispect.backend.feed.integration.PlaceSummary>>()
        }
        logger.info("Built operator places mapping for {} operators", operatorPlacesMap.size)

        val feeds = try {
            transitLandApiClient.discoverAllFeeds(specType, maxFeeds)
        } catch (ex: TransitLandApiException) {
            logger.error("Transit.land global discovery failed", ex)
            recordDiscoveryDuration(timerSample, globalRegionId, specType, success = false)
            incrementFeedCounter(globalRegionId, "error")
            return FeedDiscoveryResult(
                regionOnestopId = globalRegionId,
                feedsDiscovered = 0,
                feedsCreated = 0,
                feedsUpdated = 0,
                errors = listOf(ex.message ?: "Transit.land discovery failed")
            )
        }

        var created = 0
        var updated = 0
        val errors = mutableListOf<String>()
        val now = Instant.now(clock)

        feeds.forEach { summary ->
            runCatching {
                // Extract ALL regions from operator places, using feed onestop ID as fallback
                val regionIds = extractRegionsFromPlaces(summary.feedOnestopId, summary.name, operatorPlacesMap)

                val (result, entity) = upsertFeed(regionIds, summary, now)
                when (result) {
                    UpsertResult.CREATED -> {
                        created++
                        incrementFeedCounter(globalRegionId, "created")
                    }

                    UpsertResult.UPDATED -> {
                        updated++
                        incrementFeedCounter(globalRegionId, "updated")
                    }

                    UpsertResult.NO_CHANGE ->
                        incrementFeedCounter(globalRegionId, "unchanged")
                }

                summary.authorization?.let { updateAuthentication(entity, it) }
            }.onFailure { throwable ->
                logger.error(
                    "Failed to upsert feed {}",
                    summary.feedOnestopId,
                    throwable
                )
                errors.add("Failed to upsert ${summary.feedOnestopId}: ${throwable.message ?: "unknown error"}")
                incrementFeedCounter(globalRegionId, "error")
            }
        }

        recordDiscoveryDuration(timerSample, globalRegionId, specType, success = errors.isEmpty())

        return FeedDiscoveryResult(
            regionOnestopId = globalRegionId,
            feedsDiscovered = feeds.size,
            feedsCreated = created,
            feedsUpdated = updated,
            errors = errors
        )
    }

    @Transactional
    suspend fun discover(
        regionOnestopId: String,
        specType: FeedSpecType = FeedSpecType.GTFS
    ): FeedDiscoveryResult {
        val timerSample = Timer.start(meterRegistry)
        val region = regionRepository.findById(regionOnestopId)
            .orElseThrow { IllegalArgumentException("Region not found: $regionOnestopId") }

        val feeds = try {
            transitLandApiClient.discoverRegionalFeeds(region.name, specType)
        } catch (ex: TransitLandApiException) {
            logger.error("Transit.land discovery failed for region {}", regionOnestopId, ex)
            recordDiscoveryDuration(timerSample, regionOnestopId, specType, success = false)
            incrementFeedCounter(regionOnestopId, "error")
            return FeedDiscoveryResult(
                regionOnestopId = regionOnestopId,
                feedsDiscovered = 0,
                feedsCreated = 0,
                feedsUpdated = 0,
                errors = listOf(ex.message ?: "Transit.land discovery failed")
            )
        }

        var created = 0
        var updated = 0
        val errors = mutableListOf<String>()
        val now = Instant.now(clock)

        feeds.forEach { summary ->
            runCatching {
                val (result, entity) = upsertFeed(region.regionOnestopId, summary, now)
                when (result) {
                    UpsertResult.CREATED -> {
                        created++
                        incrementFeedCounter(regionOnestopId, "created")
                    }

                    UpsertResult.UPDATED -> {
                        updated++
                        incrementFeedCounter(regionOnestopId, "updated")
                    }

                    UpsertResult.NO_CHANGE ->
                        incrementFeedCounter(regionOnestopId, "unchanged")
                }

                summary.authorization?.let { updateAuthentication(entity, it) }
            }.onFailure { throwable ->
                logger.error(
                    "Failed to upsert feed {} for region {}",
                    summary.feedOnestopId,
                    regionOnestopId,
                    throwable
                )
                errors.add("Failed to upsert ${summary.feedOnestopId}: ${throwable.message ?: "unknown error"}")
                incrementFeedCounter(regionOnestopId, "error")
            }
        }

        recordDiscoveryDuration(timerSample, regionOnestopId, specType, success = errors.isEmpty())

        return FeedDiscoveryResult(
            regionOnestopId = regionOnestopId,
            feedsDiscovered = feeds.size,
            feedsCreated = created,
            feedsUpdated = updated,
            errors = errors
        )
    }

    private enum class UpsertResult {
        CREATED,
        UPDATED,
        NO_CHANGE
    }

    /**
     * Upsert a feed with multiple region associations.
     * Creates or updates the feed and assigns it to all specified regions.
     */
    private fun upsertFeed(
        regionOnestopIds: List<String>,
        summary: TransitLandFeedSummary,
        now: Instant
    ): Pair<UpsertResult, FeedEntity> {
        val existing = feedRepository.findById(summary.feedOnestopId)

        // Ensure all regions exist before creating references
        regionOnestopIds.forEach { regionId ->
            if (!regionRepository.existsById(regionId)) {
                logger.error("Region {} does not exist for feed {}", regionId, summary.feedOnestopId)
                throw IllegalStateException("Region $regionId must exist before upserting feed ${summary.feedOnestopId}")
            }
        }

        // Get all region references
        val regionRefs = regionOnestopIds.map { regionRepository.getReferenceById(it) }.toMutableSet()

        val entity = existing.orElseGet {
            FeedEntity(
                feedOnestopId = summary.feedOnestopId,
                regions = regionRefs,
                name = summary.name.ifBlank { inferFeedName(summary.feedOnestopId) },
                specType = summary.specType,
                downloadUrl = selectDownloadUrl(summary, fallback = ""),
                currentVersionSha1 = summary.latestVersionSha1,
                lastCheckedAt = now,
                lastUpdatedAt = summary.latestVersionFetchedAt ?: now,
                lastDiscoveredAt = now,
                status = FeedStatus.ACTIVE
            ).apply {
                staticFeedUrl = summary.staticFeedUrl
                realtimeFeedUrl = summary.realtimeFeedUrl
                operatorName = summary.operatorName
            }
        }

        val originalSha = entity.currentVersionSha1
        val originalDownload = entity.downloadUrl
        val originalStatic = entity.staticFeedUrl
        val originalRealtime = entity.realtimeFeedUrl
        val originalOperator = entity.operatorName
        val originalRegions = entity.regions.map { it.regionOnestopId }.toSet()

        // Update regions - clear and re-add to handle removed regions
        entity.regions.clear()
        entity.regions.addAll(regionRefs)

        entity.name = summary.name.ifBlank { inferFeedName(summary.feedOnestopId) }
        entity.specType = summary.specType
        entity.downloadUrl = selectDownloadUrl(summary, originalDownload)
        entity.staticFeedUrl = summary.staticFeedUrl ?: entity.staticFeedUrl
        entity.realtimeFeedUrl = summary.realtimeFeedUrl ?: entity.realtimeFeedUrl
        entity.operatorName = summary.operatorName ?: entity.operatorName
        entity.lastCheckedAt = now
        entity.lastDiscoveredAt = now
        entity.status = FeedStatus.ACTIVE

        summary.latestVersionSha1?.let { sha ->
            if (sha != originalSha) {
                entity.currentVersionSha1 = sha
                entity.lastUpdatedAt = summary.latestVersionFetchedAt ?: now
            }
        }

        val saved = feedRepository.save(entity)

        val currentRegions = entity.regions.map { it.regionOnestopId }.toSet()
        val regionsChanged = originalRegions != currentRegions

        val metadataChanged = (entity.downloadUrl != originalDownload) ||
            (entity.staticFeedUrl != originalStatic) ||
            (entity.realtimeFeedUrl != originalRealtime) ||
            (entity.operatorName != originalOperator) ||
            regionsChanged

        val outcome = when {
            existing.isEmpty -> UpsertResult.CREATED
            entity.currentVersionSha1 != originalSha -> UpsertResult.UPDATED
            metadataChanged -> UpsertResult.UPDATED
            else -> UpsertResult.NO_CHANGE
        }

        return outcome to saved
    }

    /**
     * Upsert a feed with a single region association (used by regional discovery).
     */
    private fun upsertFeed(
        regionOnestopId: String,
        summary: TransitLandFeedSummary,
        now: Instant
    ): Pair<UpsertResult, FeedEntity> {
        return upsertFeed(listOf(regionOnestopId), summary, now)
    }

    private fun updateAuthentication(
        feed: FeedEntity,
        authorization: TransitLandAuthorizationSummary
    ) {
        val authType = mapAuthType(authorization.type)
        if (authType == AuthType.NONE) {
            return
        }

        val existing = feedAuthenticationRepository.findById(feed.feedOnestopId)
        val entity = existing.orElseGet {
            FeedAuthentication(feedOnestopId = feed.feedOnestopId).apply {
                this.feed = feed
            }
        }

        entity.feed = feed
        entity.authType = authType
        entity.headerName = authorization.parameterName?.takeIf { it.isNotBlank() }
        entity.isActive = true

        authorization.infoUrl?.takeIf { it.isNotBlank() }?.let { infoUrl ->
            val note = "Transit.land auth info: $infoUrl"
            if (entity.notes.isNullOrBlank() || entity.notes != note) {
                entity.notes = note
            }
        }

        feedAuthenticationRepository.save(entity)
    }

    private fun recordDiscoveryDuration(
        sample: Timer.Sample,
        regionOnestopId: String,
        specType: FeedSpecType,
        success: Boolean
    ) {
        sample.stop(
            Timer.builder("feed.discovery.duration")
                .description("Duration of feed discovery runs")
                .tag("region", regionOnestopId)
                .tag("spec", specType.name)
                .tag("status", if (success) "success" else "error")
                .register(meterRegistry)
        )
    }

    private fun incrementFeedCounter(regionOnestopId: String, outcome: String) {
        meterRegistry.counter(
            "feed.discovery.feeds",
            "region",
            regionOnestopId,
            "outcome",
            outcome
        ).increment()
    }

    private fun selectDownloadUrl(
        summary: TransitLandFeedSummary,
        fallback: String
    ): String {
        // For GTFS feeds, prefer static URL, then version URL, then fallback
        // For GTFS-RT feeds, use realtime URL, then fallback
        // If no URL is available, return empty string (database allows this)
        return when (summary.specType) {
            FeedSpecType.GTFS -> summary.staticFeedUrl
                ?: summary.latestVersionUrl
                ?: fallback.takeIf { it.isNotBlank() }
                ?: ""

            FeedSpecType.GTFS_RT -> summary.realtimeFeedUrl
                ?: fallback.takeIf { it.isNotBlank() }
                ?: ""
        }
    }

    private fun mapAuthType(type: String?): AuthType {
        return when (type?.lowercase()) {
            "api_key", "api-key", "apikey", "http-header", "header" -> AuthType.API_KEY
            "oauth", "oauth2" -> AuthType.OAUTH2
            else -> AuthType.NONE
        }
    }

    private fun inferFeedName(feedOnestopId: String): String {
        val parts = feedOnestopId.split("-")
        return if (parts.size >= 3) {
            parts.drop(2).joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        } else {
            feedOnestopId
        }
    }

    /**
     * Build a mapping of operator onestop IDs to their geographic places.
     */
    private fun buildOperatorPlacesMap(
        operators: List<com.mobilispect.backend.TransitLandOperator>
    ): Map<String, List<com.mobilispect.backend.feed.integration.PlaceSummary>> {
        val map = mutableMapOf<String, List<com.mobilispect.backend.feed.integration.PlaceSummary>>()

        operators.forEach { operator ->
            operator.onestop_id?.let { operatorId ->
                val places = operator.agencies?.flatMap { agency ->
                    agency.places?.map { place ->
                        com.mobilispect.backend.feed.integration.PlaceSummary(
                            adm0Name = place.adm0_name,
                            adm1Name = place.adm1_name,
                            cityName = place.city_name
                        )
                    } ?: emptyList()
                } ?: emptyList()

                if (places.isNotEmpty()) {
                    map[operatorId] = places
                }
            }
        }

        return map
    }

    /**
     * Extract ALL region onestop IDs from operator geographic places.
     * Creates one region per unique (adm0_name, adm1_name, city_name) triple.
     * Falls back to feed onestop ID parsing if no places data is available.
     *
     * This allows feeds to belong to multiple regions (e.g., Caltrain serves
     * San Francisco, San Jose, Palo Alto, etc.)
     *
     * Examples:
     * - Places: [USA/CA/SF, USA/CA/SJ] → [r-usa-california-sanfrancisco, r-usa-california-sanjose]
     * - Places: [Japan/Tokyo] → [r-japan-tokyo]
     * - No places, f-9q5-translink → [r-9q5-auto]
     */
    private fun extractRegionsFromPlaces(
        feedOnestopId: String,
        feedName: String,
        operatorPlacesMap: Map<String, List<com.mobilispect.backend.feed.integration.PlaceSummary>>
    ): List<String> {
        // Derive operator ID from feed ID
        // Feed IDs like "f-9q9-caltrain" map to operator "o-9q9-caltrain"
        val operatorId = deriveOperatorIdFromFeedId(feedOnestopId)

        // Look up places for this operator
        val places = operatorPlacesMap[operatorId]

        if (places.isNullOrEmpty()) {
            logger.debug("No places found for operator {}, using feed ID fallback for feed {}", operatorId, feedOnestopId)
            return listOf(extractRegionFromFeedIdFallback(feedOnestopId, feedName))
        }

        // Create a region for EACH unique place the operator serves
        val regionIds = mutableListOf<String>()
        val seenRegions = mutableSetOf<String>() // Track duplicates

        places.forEach { place ->
            // Build region ID and name from geographic data
            val regionParts = mutableListOf<String>()

            place.adm0Name?.let { country ->
                regionParts.add(slugify(country))
            }

            place.adm1Name?.let { state ->
                regionParts.add(slugify(state))
            }

            place.cityName?.let { city ->
                regionParts.add(slugify(city))
            }

            if (regionParts.isNotEmpty()) {
                val regionId = "r-${regionParts.joinToString("-")}"

                // Only add if we haven't seen this region yet (avoid duplicates)
                if (seenRegions.add(regionId)) {
                    // Use city name as the primary region name, fall back to state, then country
                    val regionName = place.cityName
                        ?: place.adm1Name
                        ?: place.adm0Name
                        ?: "Unknown"

                    val createdRegionId = ensureRegionExists(regionId, regionName, place.adm0Name, place.adm1Name)
                    regionIds.add(createdRegionId)
                }
            }
        }

        // If no valid regions were created from places, use fallback
        if (regionIds.isEmpty()) {
            logger.warn("No valid places data for operator {}, using feed ID fallback", operatorId)
            return listOf(extractRegionFromFeedIdFallback(feedOnestopId, feedName))
        }

        logger.info("Feed {} assigned to {} regions: {}", feedOnestopId, regionIds.size, regionIds.joinToString(", "))
        return regionIds
    }

    /**
     * Derive operator onestop ID from feed onestop ID.
     * Converts "f-9q9-caltrain" to "o-9q9-caltrain"
     */
    private fun deriveOperatorIdFromFeedId(feedOnestopId: String): String {
        return feedOnestopId.replaceFirst("f-", "o-")
    }

    /**
     * Slugify a geographic name for use in region IDs.
     * Examples:
     * - "United States of America" → "usa"
     * - "São Paulo" → "saopaulo"
     * - "Île-de-France" → "iledefrance"
     */
    private fun slugify(name: String): String {
        return name
            .lowercase()
            // Replace common abbreviations
            .replace("united states of america", "usa")
            .replace("united kingdom", "uk")
            // Remove accents and special characters
            .replace(Regex("[àáâãäå]"), "a")
            .replace(Regex("[èéêë]"), "e")
            .replace(Regex("[ìíîï]"), "i")
            .replace(Regex("[òóôõö]"), "o")
            .replace(Regex("[ùúûü]"), "u")
            .replace(Regex("[ñ]"), "n")
            .replace(Regex("[ç]"), "c")
            // Remove non-alphanumeric characters
            .replace(Regex("[^a-z0-9]+"), "")
            .take(30) // Limit length
    }

    /**
     * Fallback to extract region from feed onestop ID when no places data is available.
     */
    private fun extractRegionFromFeedIdFallback(feedOnestopId: String, feedName: String): String {
        val parts = feedOnestopId.split("-", limit = 3)

        if (parts.size < 2) {
            logger.warn("Feed onestop ID '{}' has no region component, using feed name fallback", feedOnestopId)
            val cleanName = slugify(feedName)
            return if (cleanName.isNotBlank()) {
                ensureRegionExists("r-$cleanName-auto", feedName)
            } else {
                ensureGlobalRegionExists()
            }
        }

        val geohash = parts[1].split("~").first().take(30)

        if (geohash.isBlank()) {
            logger.warn("Feed onestop ID '{}' has blank identifier, using global region", feedOnestopId)
            return ensureGlobalRegionExists()
        }

        // Normalize geohash to recognize metropolitan areas
        // Many cities span multiple precise geohashes, so we normalize to the primary region
        val normalizedGeohash = normalizeGeohashToMetroArea(geohash)
        val regionId = "r-$normalizedGeohash-auto"

        // Try to find a more specific region name from known geohash patterns
        val regionName = getRegionNameFromGeohash(normalizedGeohash)
            ?: "Auto-region: $normalizedGeohash"

        return ensureRegionExists(regionId, regionName)
    }

    /**
     * Normalize geohash to group feeds from the same metropolitan area.
     *
     * Examples:
     * - f25d, f25e, f25f, f25g, f253, f256, etc. → f25d (Greater Montreal)
     * - 9q8y, 9q8z, 9q8v, 9q8w → 9q8 (San Francisco Bay Area)
     */
    private fun normalizeGeohashToMetroArea(geohash: String): String {
        return when {
            // Greater Montreal: All f25* geohashes map to f25d (Montreal core)
            geohash.startsWith("f25") -> "f25d"

            // San Francisco Bay Area: All 9q8* and 9q9* geohashes
            geohash.startsWith("9q8") || geohash.startsWith("9q9") -> "9q9"

            // Toronto area: 9q5* geohashes
            geohash.startsWith("9q5") -> "9q5"

            // Ottawa area: f244* geohashes
            geohash.startsWith("f244") -> "f244"

            // For other areas, use the first 3 characters as the metro area identifier
            // This groups nearby locations without being too broad
            geohash.length >= 3 -> geohash.substring(0, 3)

            else -> geohash
        }
    }

    /**
     * Get a human-readable region name from a normalized geohash.
     */
    private fun getRegionNameFromGeohash(normalizedGeohash: String): String? {
        return when (normalizedGeohash) {
            "f25d" -> "Greater Montreal"
            "9q9" -> "San Francisco Bay Area"
            "9q5" -> "Vancouver"
            "f244" -> "Ottawa"
            else -> null
        }
    }

    /**
     * Ensure a region exists with the given ID and name, creating it if necessary.
     *
     * This ensures one region per unique (adm0_name, adm1_name, city_name) triple.
     * The regionId is constructed from all three components to guarantee uniqueness:
     * - adm0_name (country): e.g., "USA", "Canada"
     * - adm1_name (state/province): e.g., "California", "Ontario"
     * - city_name (stored in 'name' field): e.g., "San Jose", "Toronto"
     *
     * Example region IDs:
     * - r-usa-california-sanjose
     * - r-canada-ontario-toronto
     * - r-japan-tokyo (no adm1)
     *
     * If the region already exists, it updates the geographic fields if they differ.
     */
    private fun ensureRegionExists(
        regionId: String,
        regionName: String,
        adm0Name: String? = null,
        adm1Name: String? = null
    ): String {
        val existing = regionRepository.findById(regionId)

        if (existing.isPresent) {
            val region = existing.get()
            var updated = false

            // Update geographic fields if they've changed
            if (region.adm0Name != adm0Name) {
                region.adm0Name = adm0Name
                updated = true
            }
            if (region.adm1Name != adm1Name) {
                region.adm1Name = adm1Name
                updated = true
            }
            if (region.name != regionName) {
                region.name = regionName
                updated = true
            }

            if (updated) {
                regionRepository.save(region)
                logger.info("Updated region geographic data: {} ({}, {}, {})", regionId, regionName, adm1Name, adm0Name)
            }
        } else {
            if (regionId.endsWith("-auto")) {
                logger.info("Auto region {} prevented; using global fallback", regionId)
                return ensureGlobalRegionExists()
            }
            val region = MetropolitanRegion(
                regionOnestopId = regionId,
                name = regionName,
                adm0Name = adm0Name,
                adm1Name = adm1Name,
                autoUpdateEnabled = false
            )
            regionRepository.save(region)
            logger.info("Auto-created region: {} ({}, {}, {})", regionId, regionName, adm1Name, adm0Name)
        }

        return regionId
    }

    /**
     * Ensure a global region exists for feeds that can't be matched to a specific region.
     */
    private fun ensureGlobalRegionExists(): String {
        val globalId = "r-global-worldwide"
        if (!regionRepository.existsById(globalId)) {
            val globalRegion = MetropolitanRegion(
                regionOnestopId = globalId,
                name = "Worldwide",
                autoUpdateEnabled = false
            )
            regionRepository.save(globalRegion)
            logger.info("Created global region: {}", globalId)
        }
        return globalId
    }
}

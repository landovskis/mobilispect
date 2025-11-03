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

    private fun upsertFeed(
        regionOnestopId: String,
        summary: TransitLandFeedSummary,
        now: Instant
    ): Pair<UpsertResult, FeedEntity> {
        val existing = feedRepository.findById(summary.feedOnestopId)
        val regionRef = regionRepository.getReferenceById(regionOnestopId)

        val entity = existing.orElseGet {
            FeedEntity(
                feedOnestopId = summary.feedOnestopId,
                region = regionRef,
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

        entity.region = regionRef
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

        val metadataChanged = (entity.downloadUrl != originalDownload) ||
            (entity.staticFeedUrl != originalStatic) ||
            (entity.realtimeFeedUrl != originalRealtime) ||
            (entity.operatorName != originalOperator)

        val outcome = when {
            existing.isEmpty -> UpsertResult.CREATED
            entity.currentVersionSha1 != originalSha -> UpsertResult.UPDATED
            metadataChanged -> UpsertResult.UPDATED
            else -> UpsertResult.NO_CHANGE
        }

        return outcome to saved
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
        return summary.staticFeedUrl
            ?: summary.latestVersionUrl
            ?: summary.realtimeFeedUrl
            ?: if (fallback.isNotBlank()) fallback else summary.feedOnestopId
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
}

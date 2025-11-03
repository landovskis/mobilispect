package com.mobilispect.backend.feed.integration

import com.mobilispect.backend.TransitLandFeed
import com.mobilispect.backend.TransitLandFeedResponse
import com.mobilispect.backend.feed.model.FeedSpecType
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.util.retry.Retry
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime

private const val DEFAULT_LIMIT = 100
private const val HEADER_API_KEY = "apikey"

/**
 * Transit.land API client providing strongly typed access for feed discovery workflows.
 *
 * Implements FR-020 requirement for automatic feed discovery via Transit.land REST API.
 */
@Component
class TransitLandApiClient(
    @Value("\${app.transit-land.api-key:}") private val apiKey: String?,
    @Value("\${app.transit-land.base-url:https://transit.land/api/v2/rest}") private val baseUrl: String,
    builder: WebClient.Builder
) {
    private val logger = LoggerFactory.getLogger(TransitLandApiClient::class.java)
    private val webClient: WebClient = builder
        .baseUrl(baseUrl.trimEnd('/'))
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    /**
     * Discover feeds for the given region name filtered by specification type.
     *
     * @param regionName Human-readable region name (e.g., "Montreal")
     * @param specType Feed specification (GTFS or GTFS-RT)
     */
    suspend fun discoverRegionalFeeds(
        regionName: String,
        specType: FeedSpecType
    ): List<TransitLandFeedSummary> {
        require(regionName.isNotBlank()) { "Region name must not be blank" }

        val encodedRegion = URLEncoder.encode(regionName.trim(), StandardCharsets.UTF_8)
        val uri = "/feeds.json?search=$encodedRegion&spec=${specType.dbValue}&limit=$DEFAULT_LIMIT"

        return executeRequest(uri)
            .mapNotNull { it.toSummary(specType) }
            .also {
                logger.debug(
                    "Transit.land discovery for region '{}' returned {} feeds (spec={})",
                    regionName,
                    it.size,
                    specType
                )
            }
    }

    /**
     * Execute a GET request against the Transit.land API and return a list of feeds.
     */
    private suspend fun executeRequest(uri: String): Collection<TransitLandFeed> {
        val request = webClient.get()
            .uri(uri)
            .accept(MediaType.APPLICATION_JSON)

        if (!apiKey.isNullOrBlank()) {
            request.header(HEADER_API_KEY, apiKey)
        } else {
            logger.warn("Transit.land API key is not configured; relying on anonymous access limits")
        }

        return try {
            request.retrieve()
                .onStatus({ status -> status.isError }) { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .map { body ->
                            val message = "Transit.land request failed (${response.statusCode()}) $body"
                            TransitLandApiException(message, response.statusCode())
                        }
                }
                .bodyToMono(TransitLandFeedResponse::class.java)
                .retryWhen(
                    Retry.backoff(3, Duration.ofMillis(250))
                        .filter { throwable ->
                            (throwable as? TransitLandApiException)?.statusCode == HttpStatus.TOO_MANY_REQUESTS
                        }
                        .doBeforeRetry { signal ->
                            logger.warn(
                                "Transit.land rate limit hit (attempt {}), backing off...",
                                signal.totalRetries() + 1
                            )
                        }
                )
                .awaitSingle()
                .feeds
        } catch (ex: TransitLandApiException) {
            logger.error("Transit.land responded with an error: {}", ex.message)
            throw ex
        } catch (ex: WebClientResponseException) {
            val message = "Transit.land responded with status ${ex.statusCode}: ${ex.responseBodyAsString}"
            logger.error(message, ex)
            throw TransitLandApiException(message, ex.statusCode, ex)
        } catch (ex: WebClientRequestException) {
            logger.error("Failed to reach Transit.land API: {}", ex.message)
            throw TransitLandApiException("Failed to reach Transit.land API: ${ex.message}", HttpStatus.SERVICE_UNAVAILABLE, ex)
        }
    }
}

/**
 * Convert the raw Transit.land feed into a simplified summary object.
 */
private fun TransitLandFeed.toSummary(specOverride: FeedSpecType): TransitLandFeedSummary? {
    val onestopId = onestop_id ?: return null
    val spec = FeedSpecType.fromDb(spec) ?: specOverride

    val latestVersion = feed_versions.maxByOrNull { record ->
        record.fetched_at ?: ""
    }

    val sha1 = latestVersion?.sha1
    val fetchedAt = latestVersion?.fetched_at?.let(::parseInstant)
    val versionUrl = latestVersion?.url

    val staticUrl = urls?.static_current ?: versionUrl
    val realtimeUrl = urls?.realtime_trip_updates
        ?: urls?.realtime_vehicle_positions
        ?: urls?.realtime_alerts

    val operatorDisplayName = name?.takeIf { it.isNotBlank() }

    val authorizationSummary = authorization?.let {
        TransitLandAuthorizationSummary(
            type = it.type ?: "none",
            parameterName = it.param_name,
            infoUrl = it.info_url
        )
    }

    return TransitLandFeedSummary(
        feedOnestopId = onestopId,
        name = operatorDisplayName ?: onestopId,
        specType = spec,
        staticFeedUrl = staticUrl,
        realtimeFeedUrl = realtimeUrl,
        latestVersionSha1 = sha1,
        latestVersionUrl = versionUrl,
        latestVersionFetchedAt = fetchedAt,
        operatorName = operatorDisplayName,
        authorization = authorizationSummary
    )
}

/**
 * Parse Transit.land timestamps that may be ISO-8601 with or without timezone.
 */
private fun parseInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) {
        return null
    }

    return runCatching { Instant.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { ZonedDateTime.parse(value).toInstant() }
        .getOrNull()
}

class TransitLandApiException(
    message: String,
    val statusCode: HttpStatusCode,
    cause: Throwable? = null
) : RuntimeException(message, cause)

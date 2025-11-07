package com.mobilispect.backend.feed.integration

import com.mobilispect.backend.TransitLandFeed
import com.mobilispect.backend.TransitLandFeedResponse
import com.mobilispect.backend.feed.model.FeedSpecType
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
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
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val webClient: WebClient = builder
        .baseUrl(baseUrl.trimEnd('/'))
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .codecs { configurer ->
            configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) // 16MB buffer
            configurer.defaultCodecs().kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(json))
            configurer.defaultCodecs().kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(json))
        }
        .build()

    /**
     * Discover ALL feeds from Transit.land filtered by specification type.
     * Handles pagination automatically to retrieve all available feeds.
     *
     * @param specType Feed specification (GTFS or GTFS-RT)
     * @param maxFeeds Maximum number of feeds to retrieve (default: unlimited)
     */
    suspend fun discoverAllFeeds(
        specType: FeedSpecType,
        maxFeeds: Int = Int.MAX_VALUE
    ): List<TransitLandFeedSummary> {
        val allFeeds = mutableListOf<TransitLandFeedSummary>()
        var after: Int? = null
        var hasMore = true

        logger.info("Starting global feed discovery for spec={}", specType)

        while (hasMore && allFeeds.size < maxFeeds) {
            val remaining = maxFeeds - allFeeds.size
            val pageLimit = minOf(DEFAULT_LIMIT, remaining)

            val uri = buildString {
                append("/feeds.json?spec=${specType.dbValue}&limit=$pageLimit")
                after?.let { append("&after=$it") }
            }

            val response = executeRequestWithMeta(uri)
            val pageFeedSummaries = response.feeds.mapNotNull { it.toSummary(specType) }

            allFeeds.addAll(pageFeedSummaries)

            // Check if there's more data
            after = response.meta?.after
            hasMore = response.meta?.next != null && allFeeds.size < maxFeeds

            logger.debug(
                "Transit.land global discovery: fetched {} feeds (total so far: {}, hasMore: {})",
                pageFeedSummaries.size,
                allFeeds.size,
                hasMore
            )
        }

        logger.info(
            "Transit.land global discovery completed: {} feeds discovered (spec={})",
            allFeeds.size,
            specType
        )

        return allFeeds
    }

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
     * Get operator information including geographic places for a specific operator.
     *
     * @param operatorOnestopId Operator onestop ID (e.g., "o-9q9-caltrain")
     * @return Operator information with places, or null if not found
     */
    suspend fun getOperator(operatorOnestopId: String): com.mobilispect.backend.TransitLandOperator? {
        require(operatorOnestopId.isNotBlank()) { "Operator onestop ID must not be blank" }

        val uri = "/operators/$operatorOnestopId.json"

        return try {
            val response = executeOperatorRequest(uri)
            response.operators.firstOrNull()
        } catch (ex: TransitLandApiException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND) {
                logger.debug("Operator {} not found", operatorOnestopId)
                null
            } else {
                throw ex
            }
        }
    }

    /**
     * Discover ALL operators from Transit.land with their geographic places.
     * Handles pagination automatically to retrieve all available operators.
     *
     * @param maxOperators Maximum number of operators to retrieve (default: unlimited)
     */
    suspend fun discoverAllOperators(maxOperators: Int = Int.MAX_VALUE): List<com.mobilispect.backend.TransitLandOperator> {
        val allOperators = mutableListOf<com.mobilispect.backend.TransitLandOperator>()
        var after: Int? = null
        var hasMore = true

        logger.info("Starting global operator discovery")

        while (hasMore && allOperators.size < maxOperators) {
            val remaining = maxOperators - allOperators.size
            val pageLimit = minOf(DEFAULT_LIMIT, remaining)

            val uri = buildString {
                append("/operators.json?limit=$pageLimit")
                after?.let { append("&after=$it") }
            }

            val response = executeOperatorRequest(uri)
            allOperators.addAll(response.operators)

            // Check if there's more data
            after = response.meta?.after
            hasMore = response.meta?.next != null && allOperators.size < maxOperators

            logger.debug(
                "Transit.land operator discovery: fetched {} operators (total so far: {}, hasMore: {})",
                response.operators.size,
                allOperators.size,
                hasMore
            )
        }

        logger.info("Transit.land operator discovery completed: {} operators discovered", allOperators.size)

        return allOperators
    }

    /**
     * Execute a GET request and return the full response including metadata for pagination.
     */
    private suspend fun executeRequestWithMeta(uri: String): TransitLandFeedResponse {
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

    /**
     * Execute a GET request against the Transit.land API and return operators response.
     */
    private suspend fun executeOperatorRequest(uri: String): com.mobilispect.backend.TransitLandOperatorResponse {
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
                .bodyToMono(com.mobilispect.backend.TransitLandOperatorResponse::class.java)
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
        authorization = authorizationSummary,
        places = emptyList() // Will be populated from operator data
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

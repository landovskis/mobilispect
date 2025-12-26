package com.mobilispect.backend.schedule.transit_land

import com.mobilispect.backend.AgencyResult
import com.mobilispect.backend.AgencyResultItem

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.FeedVersion
import com.mobilispect.backend.util.GenericError
import com.mobilispect.backend.TransitLandFeedResponse
import com.mobilispect.backend.TransitLandOperatorResponse
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.infastructure.transit_land.FeedMetadataResult
import com.mobilispect.backend.infastructure.transit_land.MetroAreaResult
import com.mobilispect.backend.infastructure.transit_land.MetroAreaResultItem
import com.mobilispect.backend.infastructure.transit_land.OperatorsResult
import com.mobilispect.backend.infastructure.transit_land.RouteResult
import com.mobilispect.backend.infastructure.transit_land.RouteResultItem
import com.mobilispect.backend.infastructure.transit_land.StopResultItem
import com.mobilispect.backend.infastructure.transit_land.TransitLandAPI
import com.mobilispect.backend.infastructure.transit_land.TransitLandStopResponse
import com.mobilispect.backend.schedule.ScheduledFeed
import com.mobilispect.backend.transit_land.PagingParameters
import com.mobilispect.backend.transit_land.agency.TransitLandAgencyResponse

import com.mobilispect.backend.util.NetworkError
import com.mobilispect.backend.util.TooManyRequests
import com.mobilispect.backend.util.Unauthorized
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import kotlinx.serialization.ExperimentalSerializationApi
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.HttpClient
import java.time.LocalDate
import java.util.concurrent.Semaphore

private const val CONNECT_TIMEOUT_ms = 5_000
private const val CONCURRENCY_LIMIT = 6

/**
 * A client to access the transitland API with rate limiting.
 * Rate limited to 6 requests per second per Transit.land API documentation.
 */
@OptIn(ExperimentalSerializationApi::class)
@Component
class TransitLandClient(
    builder: WebClient.Builder,
    rateLimiterRegistry: RateLimiterRegistry
) : TransitLandAPI {
    private val logger = LoggerFactory.getLogger(TransitLandClient::class.java)

    private var webClient: WebClient
    private val rateLimiter: RateLimiter = rateLimiterRegistry.rateLimiter("transitland")

    // Semaphore to limit concurrent API requests (prevents bursting past rate limit)
    private val concurrencyLimit = Semaphore(CONCURRENCY_LIMIT)

    init {
        val httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_ms)
            .doOnConnected { connection ->
                connection.addHandlerLast(ReadTimeoutHandler(30))
                connection.addHandlerLast(WriteTimeoutHandler(30))
            }.doOnRequest { request, _ ->
                logger.trace(
                    "${
                        request.method().name()
                    } ${request.uri()} ${request.fullPath()} ->"
                )
            }.doOnResponse { response, _ ->
                logger.trace(
                    "<- {}: {} {}", response.uri(), response.status().codeAsText(), response.status().reasonPhrase()
                )
            }

        webClient = builder.baseUrl("https://transit.land/api/v2/rest")
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs { configurer ->
                // Increase buffer limit to 5MB to handle large feed responses
                configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024)
            }
            .build()
    }

    /**
     * Retrieve the feed identified by [feedID].
     */
    override fun feed(apiKey: String, feedID: String): Result<ScheduledFeed> {
        return handleError {
            val uri = "/feeds.json?onestop_id=$feedID"
            val response = get(uri, apiKey, TransitLandFeedResponse::class.java)
            val feeds = response?.feeds?.mapNotNull { remote ->
                val latestVersion = remote.feed_versions.firstOrNull() ?: return@mapNotNull null
                ScheduledFeed(
                    feed = FeedEntity(
                        feedOnestopId = feedID, downloadUrl = latestVersion.url
                    ),
                    version = FeedVersion(
                        uid = latestVersion.sha1,
                        feedID = FeedId(feedID),
                        startsOn = LocalDate.parse(latestVersion.earliest_calendar_date),
                        endsOn = LocalDate.parse(latestVersion.latest_calendar_date)
                    ),
                )
            } ?: emptyList()
            return@handleError Result.success(feeds.first())
        }
    }

    /**
     * Retrieve all feeds that match the given [search] criteria.
     */
    override fun feeds(apiKey: String, search: String, spec: String): Result<Collection<ScheduledFeed>> {
        return handleError {
            val uri = "/feeds.json?search=$search&spec=$spec&limit=100"
            val response = get(uri, apiKey, TransitLandFeedResponse::class.java)
            val feeds = response?.feeds?.mapNotNull { remote ->
                val feedOnestopId = remote.onestop_id ?: return@mapNotNull null
                val latestVersion = remote.feed_versions.firstOrNull() ?: return@mapNotNull null

                ScheduledFeed(
                    feed = FeedEntity(
                        feedOnestopId = feedOnestopId,
                        downloadUrl = latestVersion.url
                    ),
                    version = FeedVersion(
                        uid = latestVersion.sha1,
                        feedID = FeedId(feedOnestopId),
                        startsOn = LocalDate.parse(latestVersion.earliest_calendar_date),
                        endsOn = LocalDate.parse(latestVersion.latest_calendar_date)
                    ),
                )
            } ?: emptyList()
            logger.debug("Found {} feeds for search '{}'", feeds.size, search)
            return@handleError Result.success(feeds)
        }
    }

    /**
     * Retrieve all feeds within a geographic area.
     */


    /**
     * Retrieve all feeds within a geographic area.
     */
    override fun feedsByCoordinates(apiKey: String, lat: Double, lon: Double, radius: Int, spec: String): Result<Collection<ScheduledFeed>> {
        return handleError {
            val uri = "/feeds.json?lat=$lat&lon=$lon&radius=$radius&spec=$spec&limit=100"
            val response = get(uri, apiKey, TransitLandFeedResponse::class.java)
            val feeds = response?.feeds?.mapNotNull { remote ->
                val feedOnestopId = remote.onestop_id ?: return@mapNotNull null
                val latestVersion = remote.feed_versions.firstOrNull() ?: return@mapNotNull null

                ScheduledFeed(
                    feed = FeedEntity(
                        feedOnestopId = feedOnestopId,
                        downloadUrl = latestVersion.url
                    ),
                    version = FeedVersion(
                        uid = latestVersion.sha1,
                        feedID = FeedId(feedOnestopId),
                        startsOn = LocalDate.parse(latestVersion.earliest_calendar_date),
                        endsOn = LocalDate.parse(latestVersion.latest_calendar_date)
                    ),
                )
            } ?: emptyList()
            logger.debug("Found {} feeds for coordinates ({}  {}, radius {}m)", feeds.size, lat, lon, radius)
            return@handleError Result.success(feeds)
        }
    }

    /**
     * Retrieve all agencies that serve a given [region] or are contained in the feed identified by [feedID].
     */
    @Suppress("ReturnCount")
    override fun agencies(apiKey: String, region: String?, feedID: String?): Result<AgencyResult> {
        return handleError {
            var uri = "/agencies.json"
            if (region != null) {
                uri += "?city_name=$region"
            }
            if (feedID != null) {
                uri += "?feed_onestop_id=$feedID"
            }

            val response = get(uri, apiKey, TransitLandAgencyResponse::class.java)
            val agencies = response?.agencies?.mapNotNull { remote ->
                try {
                    AgencyResultItem(
                        id = remote.onestopID,
                        version = remote.feed.feedVersion,
                        feedID = remote.feed.feed.uid,
                        agencyID = remote.agencyID
                    )
                } catch (e: IllegalArgumentException) {
                    if (e.message?.startsWith("Must be in OneStopID format") == true) {
                        return@mapNotNull null
                    }
                    throw e
                }
            }
            return@handleError Result.success(AgencyResult(agencies.orEmpty()))
        }
    }

    /**
     * Retrieve the routes contained in the feed identified by [feedID].
     */
    override fun routes(apiKey: String, feedID: String, paging: PagingParameters): Result<RouteResult> {
        return handleError {
            val uri = pagedURI("/routes.json?feed_onestop_id=$feedID", paging)
            val response = get(uri, apiKey, TransitLandRouteResponse::class.java)
            val routes = response?.routes?.map { remote ->
                RouteResultItem(
                    id = remote.onestopID, agencyID = remote.agency.agencyID, routeID = remote.routeID
                )
            }
            return@handleError Result.success(RouteResult(routes.orEmpty(), response?.meta?.after))
        }
    }

    /**
     * Retrieve all stops contained in the feed identified by [feedID].
     */
    override fun stop(apiKey: String, feedID: String, stopID: String): Result<StopResultItem> {
        return handleError {
            val uri = "/stops.json?feed_onestop_id=$feedID&stop_id=$stopID"
            val response = get(uri, apiKey, TransitLandStopResponse::class.java)
            val result = response?.stops?.map { remote ->
                StopResultItem(
                    uid = remote.onestopID, stopID = remote.stopID
                )
            } ?: emptyList()

            if (result.isEmpty()) {
                return@handleError Result.failure(Exception("No stops found for $stopID"))
            }
            return@handleError Result.success(result.firstOrNull()!!)
        }
    }

    /**
     * Retrieve all operators with pagination support.
     */
    override fun operators(apiKey: String, paging: PagingParameters): Result<OperatorsResult> {
        return handleError {
            var uri = "/operators.json?limit=${paging.limit}"
            if (paging.after != null) {
                uri += "&after=${paging.after}"
            }

            val response = get(uri, apiKey, TransitLandOperatorResponse::class.java)
            if (response == null) {
                logger.warn("Received null response from Transit.land operators API")
                return@handleError Result.success(OperatorsResult(emptyList(), null))
            }

            logger.debug("Fetched {} operators from Transit.land (hasMore={}, cursor={})",
                response.operators.size,
                response.meta?.after != null,
                response.meta?.after
            )

            return@handleError Result.success(
                OperatorsResult(response.operators.toList(), response.meta?.after)
            )
        }
    }

    override fun metroAreas(apiKey: String): Result<List<MetroAreaResultItem>> = handleError {
        val uri = "/metro_areas.json?limit=200"
        val response = get(uri, apiKey, MetroAreaResult::class.java)
        Result.success(response?.metro_areas ?: emptyList())
    }

    /**
     * Retrieve metadata for a specific feed.
     * Uses concurrency control to prevent overwhelming the API when called in parallel.
     */
    override fun feedMetadata(apiKey: String, feedId: String): Result<FeedMetadataResult> {
        return handleError {
            val uri = "/feeds.json?onestop_id=$feedId&include_alerts=false"
            val response = getConcurrent(uri, apiKey, TransitLandFeedResponse::class.java)

            if (response == null) {
                logger.warn("Received null response for feed: {}", feedId)
                return@handleError Result.failure(Exception("No response for feed: $feedId"))
            }

            val feed = response.feeds.firstOrNull()
            if (feed == null) {
                logger.warn("No feed data found for feed ID: {}", feedId)
                return@handleError Result.failure(Exception("No feed data found for: $feedId"))
            }

            val latestVersion = feed.feed_versions.firstOrNull()
            if (latestVersion == null) {
                logger.warn("No version information available for feed: {}", feedId)
                return@handleError Result.failure(Exception("No version information for: $feedId"))
            }

            val specType = when (feed.spec.lowercase()) {
                "gtfs" -> FeedSpecType.GTFS
                "gtfs-rt" -> FeedSpecType.GTFS_RT
                else -> {
                    logger.warn("Unknown spec type '{}' for feed: {}, defaulting to GTFS", feed.spec, feedId)
                    FeedSpecType.GTFS
                }
            }

            return@handleError Result.success(
                FeedMetadataResult(
                    feedOnestopId = feed.onestop_id ?: feedId,
                    name = feed.name,
                    spec = specType,
                    downloadUrl = latestVersion.url,
                    versionSha1 = latestVersion.sha1,
                    earliestCalendarDate = LocalDate.parse(latestVersion.earliest_calendar_date),
                    latestCalendarDate = LocalDate.parse(latestVersion.latest_calendar_date),
                    staticFeedUrl = feed.urls?.static_current,
                    realtimeFeedUrl = feed.urls?.realtime_trip_updates,
                    authorizationType = feed.authorization?.type,
                    authorizationInfoUrl = feed.authorization?.info_url
                )
            )
        }
    }

    private fun <T> handleError(
        block: () -> Result<T>
    ): Result<T> {
        return try {
            block()
        } catch (e: RequestNotPermitted) {
            logger.warn("Transit.land API rate limit exceeded - request blocked by local rate limiter")
            Result.failure(TooManyRequests)
        } catch (e: WebClientRequestException) {
            Result.failure(NetworkError(e))
        } catch (e: WebClientResponseException) {
            when (e) {
                is WebClientResponseException.Unauthorized -> Result.failure(Unauthorized)
                is WebClientResponseException.TooManyRequests -> {
                    logger.warn("Transit.land API returned 429 Too Many Requests")
                    Result.failure(TooManyRequests)
                }
                else -> Result.failure(GenericError(e.cause.toString()))
            }
        }
    }

    private fun pagedURI(endpoint: String, paging: PagingParameters): String {
        var uri = "$endpoint&limit=${paging.limit}"
        if (paging.after != null) {
            uri += "&after=${paging.after}"
        }
        return uri
    }

    private fun <T : Any> get(
        uri: String, apiKey: String, clazz: Class<T>
    ): T? {
        // Acquire permission from rate limiter before making the request
        // This will block if rate limit is exceeded, up to timeout-duration (30s)
        RateLimiter.waitForPermission(rateLimiter)

        return webClient.get()
            .uri(uri)
            .header("apikey", apiKey)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(clazz)
            .block()
    }

    /**
     * Makes a GET request with concurrency control.
     * Uses both a semaphore to limit concurrent requests and a rate limiter
     * to control request rate. This is suitable for parallel batch operations.
     */
    private fun <T : Any> getConcurrent(
        uri: String, apiKey: String, clazz: Class<T>
    ): T? {
        // Acquire concurrency permit to limit parallel requests
        concurrencyLimit.acquire()
        return try {
            // Acquire permission from rate limiter before making the request
            RateLimiter.waitForPermission(rateLimiter)

            webClient.get()
                .uri(uri)
                .header("apikey", apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(clazz)
                .block()
        } finally {
            concurrencyLimit.release()
        }
    }
}

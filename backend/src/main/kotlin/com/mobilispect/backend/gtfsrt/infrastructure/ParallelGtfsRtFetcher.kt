package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFeedState
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFetchResult
import com.mobilispect.backend.gtfsrt.domain.model.UnchangedReason
import com.mobilispect.backend.gtfsrt.domain.repository.GtfsRtFeedStateRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange

/**
 * Parallel GTFS-RT feed fetcher using Kotlin coroutines.
 *
 * Fetches multiple GTFS-RT feeds concurrently with:
 * - Bounded parallelism via dispatcher and semaphore
 * - Per-feed circuit breakers for fault isolation
 * - HTTP conditional requests for cache efficiency
 * - Content hash deduplication
 *
 * Per ADR 0011.
 */
@Component
class ParallelGtfsRtFetcher(
  private val webClient: WebClient,
  private val feedStateRepository: GtfsRtFeedStateRepository,
  private val circuitBreakerRegistry: FeedCircuitBreakerRegistry,
  private val meterRegistry: MeterRegistry,
  @Value("\${gtfsrt.fetcher.parallelism:50}") private val parallelism: Int,
  @Value("\${gtfsrt.fetcher.max-permits:100}") private val maxPermits: Int,
  @Value("\${gtfsrt.fetcher.timeout-ms:30000}") private val timeoutMs: Long,
) {

  private val logger = LoggerFactory.getLogger(ParallelGtfsRtFetcher::class.java)
  private val dispatcher = Dispatchers.IO.limitedParallelism(parallelism)
  private val semaphore = Semaphore(maxPermits)

  /**
   * Fetch all feeds in parallel.
   *
   * @param feeds List of feeds to fetch (must have realtimeFeedUrl)
   * @return Flow of fetch results
   */
  suspend fun fetchAllFeeds(feeds: List<Feed>): Flow<GtfsRtFetchResult> = channelFlow {
    feeds
      .mapNotNull { feed -> feed.realtimeFeedUrl?.let { url -> feed to url } }
      .map { (feed, url) ->
        async(dispatcher) { semaphore.withPermit { fetchWithResilience(feed, url) } }
      }
      .forEach { deferred -> send(deferred.await()) }
  }

  private suspend fun fetchWithResilience(feed: Feed, url: String): GtfsRtFetchResult {
    val circuitBreaker = circuitBreakerRegistry.getOrCreate(feed.feedId)

    return if (circuitBreaker.tryAcquirePermission()) {
      try {
        val result = withTimeout(timeoutMs) { doFetch(feed.feedId, url) }
        circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        result
      } catch (e: Exception) {
        circuitBreaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, e)
        recordFetchError(feed.feedId, e)
        GtfsRtFetchResult.Failure(feedId = feed.feedId, error = e, failedAt = Instant.now())
      }
    } else {
      logger.debug("Circuit breaker open for feed {}", feed.feedId)
      meterRegistry
        .counter("gtfsrt.fetch.circuit_breaker_open", "feed_id", feed.feedId.value)
        .increment()
      GtfsRtFetchResult.Failure(
        feedId = feed.feedId,
        error = CircuitBreakerOpenException(feed.feedId),
        failedAt = Instant.now(),
      )
    }
  }

  private suspend fun doFetch(feedId: FeedId, url: String): GtfsRtFetchResult {
    val timer = Timer.start(meterRegistry)
    val previousState = feedStateRepository.findByFeedId(feedId)
    val now = Instant.now()

    return try {
      val response =
        webClient
          .get()
          .uri(url)
          .apply { applyConditionalHeaders(previousState) }
          .awaitExchange { clientResponse ->
            when (clientResponse.statusCode()) {
              HttpStatus.NOT_MODIFIED -> {
                recordCacheHit(feedId, UnchangedReason.HTTP_NOT_MODIFIED)
                GtfsRtFetchResult.Unchanged(
                  feedId = feedId,
                  reason = UnchangedReason.HTTP_NOT_MODIFIED,
                  checkedAt = now,
                )
              }
              HttpStatus.OK -> {
                val body = clientResponse.awaitBody<ByteArray>()
                val etag = clientResponse.headers().header("ETag").firstOrNull()
                val lastModified = clientResponse.headers().header("Last-Modified").firstOrNull()

                checkContentAndBuildResult(feedId, body, etag, lastModified, previousState, now)
              }
              else -> {
                val statusCode = clientResponse.statusCode()
                throw HttpStatusException(statusCode.value(), "Unexpected status: $statusCode")
              }
            }
          }

      timer.stop(
        meterRegistry.timer("gtfsrt.fetch.duration", "feed_id", feedId.value, "success", "true")
      )
      response
    } catch (e: Exception) {
      timer.stop(
        meterRegistry.timer("gtfsrt.fetch.duration", "feed_id", feedId.value, "success", "false")
      )
      throw e
    }
  }

  private fun WebClient.RequestHeadersSpec<*>.applyConditionalHeaders(
    state: GtfsRtFeedState?
  ): WebClient.RequestHeadersSpec<*> {
    var request = this
    state?.etag?.let { request = request.header("If-None-Match", it) }
    state?.lastModified?.let { request = request.header("If-Modified-Since", it) }
    return request
  }

  private fun checkContentAndBuildResult(
    feedId: FeedId,
    body: ByteArray,
    etag: String?,
    lastModified: String?,
    previousState: GtfsRtFeedState?,
    now: Instant,
  ): GtfsRtFetchResult {
    val contentHash = ContentHasher.hash(body)

    // Check content hash match
    if (previousState?.contentHash == contentHash) {
      recordCacheHit(feedId, UnchangedReason.CONTENT_HASH_MATCH)
      return GtfsRtFetchResult.Unchanged(
        feedId = feedId,
        reason = UnchangedReason.CONTENT_HASH_MATCH,
        checkedAt = now,
      )
    }

    // Content is different — return for processing
    recordNewData(feedId, body.size)
    return GtfsRtFetchResult.NewData(
      feedId = feedId,
      data = body,
      contentHash = contentHash,
      etag = etag,
      lastModified = lastModified,
      fetchedAt = now,
    )
  }

  private fun recordCacheHit(feedId: FeedId, reason: UnchangedReason) {
    meterRegistry
      .counter("gtfsrt.fetch.cache_hit", "feed_id", feedId.value, "reason", reason.name)
      .increment()
    logger.debug("Feed {} unchanged: {}", feedId, reason)
  }

  private fun recordNewData(feedId: FeedId, bytes: Int) {
    meterRegistry.counter("gtfsrt.fetch.new_data", "feed_id", feedId.value).increment()
    meterRegistry
      .summary("gtfsrt.fetch.response_size", "feed_id", feedId.value)
      .record(bytes.toDouble())
    logger.debug("Feed {} has new data: {} bytes", feedId, bytes)
  }

  private fun recordFetchError(feedId: FeedId, error: Throwable) {
    val errorType = error::class.simpleName ?: "Unknown"
    meterRegistry
      .counter("gtfsrt.fetch.error", "feed_id", feedId.value, "error_type", errorType)
      .increment()
    logger.warn("Feed {} fetch failed: {}", feedId, error.message)
  }
}

/** Exception indicating circuit breaker is open for a feed. */
class CircuitBreakerOpenException(feedId: FeedId) :
  RuntimeException("Circuit breaker open for feed: ${feedId.value}")

/** Exception for unexpected HTTP status codes. */
class HttpStatusException(val statusCode: Int, message: String) : RuntimeException(message)

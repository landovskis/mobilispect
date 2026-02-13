package com.mobilispect.backend.gtfsrt.application

import com.mobilispect.backend.feed.batch.discovery.FeedDiscoveryBatchService
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.repository.FeedRepository
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFetchResult
import com.mobilispect.backend.gtfsrt.infrastructure.ParallelGtfsRtFetcher
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Main GTFS-RT ingestion service.
 *
 * Orchestrates parallel fetching of GTFS-RT feeds with:
 * - On-demand discovery if no feeds exist
 * - Parallel fetching via coroutines
 * - Deduplication to skip unchanged feeds
 * - Processing and persistence of realtime data
 *
 * Per ADR 0011.
 */
@Service
class GtfsRtIngestionService(
  private val feedRepository: FeedRepository,
  private val feedDiscoveryBatchService: FeedDiscoveryBatchService,
  private val fetcher: ParallelGtfsRtFetcher,
  private val processor: GtfsRtProcessingService,
  private val meterRegistry: MeterRegistry,
) {

  private val logger = LoggerFactory.getLogger(GtfsRtIngestionService::class.java)
  private val discoveryInProgress = AtomicBoolean(false)

  /**
   * Main ingestion entry point. Runs every 30 seconds by default.
   *
   * 1. Queries for feeds with realtimeFeedUrl
   * 2. If none found, triggers on-demand discovery
   * 3. Fetches all feeds in parallel
   * 4. Processes results (decode, persist)
   */
  @Scheduled(fixedRateString = "\${gtfsrt.ingestion.interval-ms:30000}")
  fun ingest() = runBlocking {
    val startTime = System.currentTimeMillis()

    try {
      val feeds = getOrDiscoverFeeds()

      if (feeds.isEmpty()) {
        logger.info("No GTFS-RT feeds available after discovery attempt")
        meterRegistry.counter("gtfsrt.ingestion.no_feeds").increment()
        return@runBlocking
      }

      logger.info("Starting GTFS-RT ingestion for {} feeds", feeds.size)
      meterRegistry.gauge("gtfsrt.ingestion.feed_count", feeds.size)

      val stats = IngestionStats()

      fetcher.fetchAllFeeds(feeds).collect { result ->
        handleResult(result, stats)
      }

      val duration = System.currentTimeMillis() - startTime
      recordStats(stats, duration)
    } catch (e: Exception) {
      logger.error("GTFS-RT ingestion cycle failed", e)
      meterRegistry.counter("gtfsrt.ingestion.cycle_error").increment()
    }
  }

  private suspend fun getOrDiscoverFeeds(): List<Feed> {
    val feeds = feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE)

    if (feeds.isNotEmpty()) {
      return feeds
    }

    // No feeds found — trigger discovery if not already running
    if (discoveryInProgress.compareAndSet(false, true)) {
      try {
        logger.info("No GTFS-RT feeds found, triggering feed discovery")
        meterRegistry.counter("gtfsrt.discovery.triggered").increment()

        val result = feedDiscoveryBatchService.discoverAll(FeedSpecType.GTFS)

        logger.info(
          "Feed discovery complete: {} feeds discovered, {} regions found",
          result.feedsFound,
          result.regionsFound,
        )
      } catch (e: Exception) {
        logger.error("On-demand feed discovery failed", e)
      } finally {
        discoveryInProgress.set(false)
      }
    } else {
      logger.info("Discovery already in progress, waiting...")
      // Wait for in-progress discovery to complete
      while (discoveryInProgress.get()) {
        delay(1000)
      }
    }

    // Re-query after discovery
    return feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE)
  }

  private suspend fun handleResult(result: GtfsRtFetchResult, stats: IngestionStats) {
    when (result) {
      is GtfsRtFetchResult.NewData -> {
        val outcome = processor.process(result)
        when (outcome) {
          is ProcessingOutcome.Processed -> {
            stats.processed++
            stats.entities += outcome.entityCount
          }
          is ProcessingOutcome.Skipped -> {
            stats.skipped++
            stats.skipReasons[outcome.reason] = stats.skipReasons.getOrDefault(outcome.reason, 0) + 1
          }
        }
      }
      is GtfsRtFetchResult.Unchanged -> {
        stats.skipped++
        stats.skipReasons[result.reason.name] =
          stats.skipReasons.getOrDefault(result.reason.name, 0) + 1
      }
      is GtfsRtFetchResult.Failure -> {
        stats.failed++
      }
    }
  }

  private fun recordStats(stats: IngestionStats, durationMs: Long) {
    logger.info(
      "GTFS-RT ingestion complete: {} processed, {} skipped, {} failed, {} entities in {}ms",
      stats.processed,
      stats.skipped,
      stats.failed,
      stats.entities,
      durationMs,
    )

    meterRegistry.counter("gtfsrt.ingestion.processed").increment(stats.processed.toDouble())
    meterRegistry.counter("gtfsrt.ingestion.skipped").increment(stats.skipped.toDouble())
    meterRegistry.counter("gtfsrt.ingestion.failed").increment(stats.failed.toDouble())
    meterRegistry.counter("gtfsrt.ingestion.entities").increment(stats.entities.toDouble())
    meterRegistry.timer("gtfsrt.ingestion.duration").record(java.time.Duration.ofMillis(durationMs))

    stats.skipReasons.forEach { (reason, count) ->
      meterRegistry
        .counter("gtfsrt.ingestion.skip_reason", "reason", reason)
        .increment(count.toDouble())
    }
  }
}

private data class IngestionStats(
  var processed: Int = 0,
  var skipped: Int = 0,
  var failed: Int = 0,
  var entities: Int = 0,
  val skipReasons: MutableMap<String, Int> = mutableMapOf(),
)

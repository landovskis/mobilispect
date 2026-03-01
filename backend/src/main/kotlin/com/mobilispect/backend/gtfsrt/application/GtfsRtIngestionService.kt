package com.mobilispect.backend.gtfsrt.application

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.domain.repository.FeedRepository
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFetchResult
import com.mobilispect.backend.gtfsrt.infrastructure.ParallelGtfsRtFetcher
import io.micrometer.core.instrument.MeterRegistry
import java.time.Instant
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Main GTFS-RT ingestion service.
 *
 * Orchestrates parallel fetching of GTFS-RT feeds with:
 * - Parallel fetching via coroutines
 * - Deduplication to skip unchanged feeds
 * - Processing and persistence of realtime data
 *
 * Feed discovery is handled by the Airflow pipeline, which populates feeds into the database.
 * This service reads active feeds with realtime URLs and ingests them.
 *
 * Per ADR 0011.
 */
@Service
class GtfsRtIngestionService(
  private val feedRepository: FeedRepository,
  private val fetcher: ParallelGtfsRtFetcher,
  private val processor: GtfsRtProcessingService,
  private val meterRegistry: MeterRegistry,
) {

  private val logger = LoggerFactory.getLogger(GtfsRtIngestionService::class.java)

  /**
   * Main ingestion entry point. Runs every 30 seconds by default.
   *
   * 1. Queries for active feeds with realtimeFeedUrl
   * 2. Fetches all feeds in parallel
   * 3. Processes results (decode, persist)
   *
   * Feed discovery is handled by the Airflow pipeline; this service only ingests existing feeds.
   */
  @Scheduled(fixedRateString = "\${gtfsrt.ingestion.interval-ms:30000}")
  fun ingest() = runBlocking {
    val startTime = System.currentTimeMillis()

    try {
      val feeds = feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE)

      if (feeds.isEmpty()) {
        logger.info("No GTFS-RT feeds available — waiting for Airflow discovery pipeline")
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

  private suspend fun handleResult(result: GtfsRtFetchResult, stats: IngestionStats) {
    when (result) {
      is GtfsRtFetchResult.NewData -> {
        val outcome = processor.process(result)
        when (outcome) {
          is ProcessingOutcome.Processed -> {
            stats.processed++
            stats.entities += outcome.entityCount
            updateFeedStatus(result.feedId, checkedAt = result.fetchedAt, updatedAt = result.fetchedAt)
          }
          is ProcessingOutcome.Skipped -> {
            stats.skipped++
            stats.skipReasons[outcome.reason] = stats.skipReasons.getOrDefault(outcome.reason, 0) + 1
            updateFeedStatus(result.feedId, checkedAt = result.fetchedAt, updatedAt = null)
          }
        }
      }
      is GtfsRtFetchResult.Unchanged -> {
        stats.skipped++
        stats.skipReasons[result.reason.name] =
          stats.skipReasons.getOrDefault(result.reason.name, 0) + 1
        updateFeedStatus(result.feedId, checkedAt = result.checkedAt, updatedAt = null)
      }
      is GtfsRtFetchResult.Failure -> {
        stats.failed++
      }
    }
  }

  private fun updateFeedStatus(feedId: FeedId, checkedAt: Instant, updatedAt: Instant?) {
    val feed = feedRepository.findById(feedId) ?: return
    feedRepository.save(
      feed.copy(
        lastCheckedAt = checkedAt,
        lastUpdatedAt = updatedAt ?: feed.lastUpdatedAt,
      )
    )
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

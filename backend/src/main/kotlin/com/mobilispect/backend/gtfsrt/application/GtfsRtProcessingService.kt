package com.mobilispect.backend.gtfsrt.application

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFeedState
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFetchResult
import com.mobilispect.backend.gtfsrt.domain.model.UnchangedReason
import com.mobilispect.backend.gtfsrt.domain.repository.GtfsRtFeedStateRepository
import io.micrometer.core.instrument.MeterRegistry
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for processing GTFS-RT feed data.
 *
 * Handles:
 * - Protobuf decoding
 * - Timestamp-based deduplication (final layer)
 * - Persistence of vehicle positions, trip updates, alerts
 * - State updates for deduplication
 */
@Service
class GtfsRtProcessingService(
  private val feedStateRepository: GtfsRtFeedStateRepository,
  private val meterRegistry: MeterRegistry,
) {

  private val logger = LoggerFactory.getLogger(GtfsRtProcessingService::class.java)

  /**
   * Process fetched GTFS-RT data.
   *
   * @param result The fetch result containing raw protobuf data
   * @return Processing outcome (processed or skipped)
   */
  suspend fun process(result: GtfsRtFetchResult.NewData): ProcessingOutcome {
    val feedId = result.feedId
    val previousState = feedStateRepository.findByFeedId(feedId)

    // Parse the GTFS-RT protobuf
    val feedMessage = try {
      parseGtfsRtFeedMessage(result.data)
    } catch (e: Exception) {
      logger.error("Failed to parse GTFS-RT protobuf for feed {}: {}", feedId, e.message)
      meterRegistry.counter("gtfsrt.processing.parse_error", "feed_id", feedId.value).increment()
      return ProcessingOutcome.Skipped(feedId, "PARSE_ERROR")
    }

    // Check GTFS-RT header timestamp (Layer 3 deduplication)
    val headerTimestamp = feedMessage.headerTimestamp
    if (previousState?.gtfsRtTimestamp != null && headerTimestamp <= previousState.gtfsRtTimestamp) {
      logger.debug(
        "Feed {} timestamp {} not newer than previous {}, skipping",
        feedId,
        headerTimestamp,
        previousState.gtfsRtTimestamp,
      )
      meterRegistry
        .counter("gtfsrt.processing.skip_reason", "reason", UnchangedReason.TIMESTAMP_NOT_NEWER.name)
        .increment()
      return ProcessingOutcome.Skipped(feedId, UnchangedReason.TIMESTAMP_NOT_NEWER.name)
    }

    // Process entities
    val entityCount = processEntities(feedId, feedMessage)

    // Update state cache
    val newState =
      GtfsRtFeedState(
        feedId = feedId,
        contentHash = result.contentHash,
        etag = result.etag,
        lastModified = result.lastModified,
        gtfsRtTimestamp = headerTimestamp,
        lastFetchedAt = result.fetchedAt,
        lastProcessedAt = Instant.now(),
      )
    feedStateRepository.save(newState)

    meterRegistry.counter("gtfsrt.processing.success", "feed_id", feedId.value).increment()
    logger.debug("Processed feed {}: {} entities", feedId, entityCount)

    return ProcessingOutcome.Processed(feedId, entityCount)
  }

  private fun parseGtfsRtFeedMessage(data: ByteArray): GtfsRtFeedMessage {
    // TODO: Implement actual GTFS-RT protobuf parsing using gtfs-realtime-bindings
    // For now, return a stub to allow compilation
    // This requires adding gtfs-realtime-bindings dependency

    // Attempt basic parsing to extract header timestamp
    // Real implementation would use:
    // val feedMessage = com.google.transit.realtime.GtfsRealtime.FeedMessage.parseFrom(data)
    // return GtfsRtFeedMessage(
    //   headerTimestamp = feedMessage.header.timestamp,
    //   vehiclePositions = feedMessage.entityList.filter { it.hasVehicle() }.map { ... },
    //   tripUpdates = feedMessage.entityList.filter { it.hasTripUpdate() }.map { ... },
    //   alerts = feedMessage.entityList.filter { it.hasAlert() }.map { ... },
    // )

    return GtfsRtFeedMessage(
      headerTimestamp = System.currentTimeMillis() / 1000,
      vehiclePositionCount = 0,
      tripUpdateCount = 0,
      alertCount = 0,
    )
  }

  private fun processEntities(feedId: FeedId, feedMessage: GtfsRtFeedMessage): Int {
    // TODO: Implement entity processing and persistence
    // - Convert protobuf entities to domain models
    // - Apply map matching for vehicle positions (ADR 0012)
    // - Batch persist to database

    val totalCount =
      feedMessage.vehiclePositionCount + feedMessage.tripUpdateCount + feedMessage.alertCount

    meterRegistry
      .counter("gtfsrt.processing.vehicle_positions", "feed_id", feedId.value)
      .increment(feedMessage.vehiclePositionCount.toDouble())
    meterRegistry
      .counter("gtfsrt.processing.trip_updates", "feed_id", feedId.value)
      .increment(feedMessage.tripUpdateCount.toDouble())
    meterRegistry
      .counter("gtfsrt.processing.alerts", "feed_id", feedId.value)
      .increment(feedMessage.alertCount.toDouble())

    return totalCount
  }
}

/** Parsed GTFS-RT feed message (stub for protobuf parsing). */
data class GtfsRtFeedMessage(
  val headerTimestamp: Long,
  val vehiclePositionCount: Int,
  val tripUpdateCount: Int,
  val alertCount: Int,
)

/** Outcome of processing a GTFS-RT feed. */
sealed class ProcessingOutcome {
  abstract val feedId: FeedId

  data class Processed(
    override val feedId: FeedId,
    val entityCount: Int,
  ) : ProcessingOutcome()

  data class Skipped(
    override val feedId: FeedId,
    val reason: String,
  ) : ProcessingOutcome()
}

package com.mobilispect.backend.vehicle.application

import com.google.transit.realtime.GtfsRealtime
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.vehicle.domain.model.AlertCause
import com.mobilispect.backend.vehicle.domain.model.AlertEffect
import com.mobilispect.backend.vehicle.domain.model.CongestionLevel
import com.mobilispect.backend.vehicle.domain.model.EntitySelector
import com.mobilispect.backend.vehicle.domain.model.GtfsRtFeedState
import com.mobilispect.backend.vehicle.domain.model.GtfsRtFetchResult
import com.mobilispect.backend.vehicle.domain.model.OccupancyStatus
import com.mobilispect.backend.vehicle.domain.model.ServiceAlert
import com.mobilispect.backend.vehicle.domain.model.StopScheduleRelationship
import com.mobilispect.backend.vehicle.domain.model.StopTimeEvent
import com.mobilispect.backend.vehicle.domain.model.StopTimeUpdate
import com.mobilispect.backend.vehicle.domain.model.TimeRange
import com.mobilispect.backend.vehicle.domain.model.TripScheduleRelationship
import com.mobilispect.backend.vehicle.domain.model.TripUpdate
import com.mobilispect.backend.vehicle.domain.model.UnchangedReason
import com.mobilispect.backend.vehicle.domain.model.VehiclePosition
import com.mobilispect.backend.vehicle.domain.model.VehicleStopStatus
import com.mobilispect.backend.vehicle.domain.repository.GtfsRtFeedStateRepository
import io.micrometer.core.instrument.MeterRegistry
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for processing GTFS-RT feed data.
 *
 * Handles:
 * - Protobuf decoding using gtfs-realtime-bindings
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
    val feedMessage =
      try {
        GtfsRealtime.FeedMessage.parseFrom(result.data)
      } catch (e: Exception) {
        logger.error("Failed to parse GTFS-RT protobuf for feed {}: {}", feedId, e.message)
        meterRegistry.counter("gtfsrt.processing.parse_error", "feed_id", feedId.value).increment()
        return ProcessingOutcome.Skipped(feedId, "PARSE_ERROR")
      }

    // Check GTFS-RT header timestamp (Layer 3 deduplication)
    val headerTimestamp = feedMessage.header.timestamp
    if (
      previousState?.gtfsRtTimestamp != null && headerTimestamp <= previousState.gtfsRtTimestamp
    ) {
      logger.debug(
        "Feed {} timestamp {} not newer than previous {}, skipping",
        feedId,
        headerTimestamp,
        previousState.gtfsRtTimestamp,
      )
      meterRegistry
        .counter(
          "gtfsrt.processing.skip_reason",
          "reason",
          UnchangedReason.TIMESTAMP_NOT_NEWER.name,
        )
        .increment()
      return ProcessingOutcome.Skipped(feedId, UnchangedReason.TIMESTAMP_NOT_NEWER.name)
    }

    // Convert protobuf entities to domain models
    val parsedFeed = parseEntities(feedId, feedMessage)

    // Process and persist entities
    val entityCount = processEntities(feedId, parsedFeed)

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

  private fun parseEntities(
    feedId: FeedId,
    feedMessage: GtfsRealtime.FeedMessage,
  ): ParsedGtfsRtFeed {
    val vehiclePositions = mutableListOf<VehiclePosition>()
    val tripUpdates = mutableListOf<TripUpdate>()
    val alerts = mutableListOf<ServiceAlert>()

    for (entity in feedMessage.entityList) {
      if (entity.hasVehicle()) {
        parseVehiclePosition(feedId, entity)?.let { vehiclePositions.add(it) }
      }
      if (entity.hasTripUpdate()) {
        parseTripUpdate(feedId, entity)?.let { tripUpdates.add(it) }
      }
      if (entity.hasAlert()) {
        parseServiceAlert(feedId, entity)?.let { alerts.add(it) }
      }
    }

    return ParsedGtfsRtFeed(vehiclePositions, tripUpdates, alerts)
  }

  private fun parseVehiclePosition(
    feedId: FeedId,
    entity: GtfsRealtime.FeedEntity,
  ): VehiclePosition? {
    val vehicle = entity.vehicle
    if (!vehicle.hasPosition()) return null

    val position = vehicle.position
    val descriptor = if (vehicle.hasVehicle()) vehicle.vehicle else null
    val trip = if (vehicle.hasTrip()) vehicle.trip else null

    return VehiclePosition(
      feedId = feedId,
      vehicleId = descriptor?.id ?: entity.id,
      tripId = trip?.tripId,
      routeId = trip?.routeId,
      latitude = position.latitude.toDouble(),
      longitude = position.longitude.toDouble(),
      bearing = if (position.hasBearing()) position.bearing else null,
      speed = if (position.hasSpeed()) position.speed else null,
      currentStopSequence =
        if (vehicle.hasCurrentStopSequence()) vehicle.currentStopSequence else null,
      currentStatus =
        if (vehicle.hasCurrentStatus()) mapVehicleStopStatus(vehicle.currentStatus) else null,
      timestamp =
        Instant.ofEpochSecond(
          if (vehicle.hasTimestamp()) vehicle.timestamp else entity.id.hashCode().toLong()
        ),
      congestionLevel =
        if (vehicle.hasCongestionLevel()) mapCongestionLevel(vehicle.congestionLevel) else null,
      occupancyStatus =
        if (vehicle.hasOccupancyStatus()) mapOccupancyStatus(vehicle.occupancyStatus) else null,
    )
  }

  private fun parseTripUpdate(feedId: FeedId, entity: GtfsRealtime.FeedEntity): TripUpdate? {
    val tripUpdate = entity.tripUpdate
    if (!tripUpdate.hasTrip()) return null

    val trip = tripUpdate.trip
    val vehicle = if (tripUpdate.hasVehicle()) tripUpdate.vehicle else null

    return TripUpdate(
      feedId = feedId,
      tripId = trip.tripId,
      routeId = if (trip.hasRouteId()) trip.routeId else null,
      vehicleId = vehicle?.id,
      timestamp = Instant.ofEpochSecond(if (tripUpdate.hasTimestamp()) tripUpdate.timestamp else 0),
      delay = if (tripUpdate.hasDelay()) tripUpdate.delay else null,
      scheduleRelationship =
        if (trip.hasScheduleRelationship()) mapTripScheduleRelationship(trip.scheduleRelationship)
        else null,
      stopTimeUpdates = tripUpdate.stopTimeUpdateList.map { parseStopTimeUpdate(it) },
    )
  }

  private fun parseStopTimeUpdate(stu: GtfsRealtime.TripUpdate.StopTimeUpdate): StopTimeUpdate {
    return StopTimeUpdate(
      stopSequence = if (stu.hasStopSequence()) stu.stopSequence else null,
      stopId = if (stu.hasStopId()) stu.stopId else null,
      arrival = if (stu.hasArrival()) parseStopTimeEvent(stu.arrival) else null,
      departure = if (stu.hasDeparture()) parseStopTimeEvent(stu.departure) else null,
      scheduleRelationship =
        if (stu.hasScheduleRelationship()) mapStopScheduleRelationship(stu.scheduleRelationship)
        else null,
    )
  }

  private fun parseStopTimeEvent(event: GtfsRealtime.TripUpdate.StopTimeEvent): StopTimeEvent {
    return StopTimeEvent(
      delay = if (event.hasDelay()) event.delay else null,
      time = if (event.hasTime()) Instant.ofEpochSecond(event.time) else null,
      uncertainty = if (event.hasUncertainty()) event.uncertainty else null,
    )
  }

  private fun parseServiceAlert(feedId: FeedId, entity: GtfsRealtime.FeedEntity): ServiceAlert {
    val alert = entity.alert

    return ServiceAlert(
      feedId = feedId,
      alertId = entity.id,
      cause = if (alert.hasCause()) mapAlertCause(alert.cause) else null,
      effect = if (alert.hasEffect()) mapAlertEffect(alert.effect) else null,
      headerText = if (alert.hasHeaderText()) getTranslatedText(alert.headerText) else null,
      descriptionText =
        if (alert.hasDescriptionText()) getTranslatedText(alert.descriptionText) else null,
      url = if (alert.hasUrl()) getTranslatedText(alert.url) else null,
      activePeriods =
        alert.activePeriodList.map {
          TimeRange(
            start = if (it.hasStart()) Instant.ofEpochSecond(it.start) else null,
            end = if (it.hasEnd()) Instant.ofEpochSecond(it.end) else null,
          )
        },
      informedEntities =
        alert.informedEntityList.map {
          EntitySelector(
            agencyId = if (it.hasAgencyId()) it.agencyId else null,
            routeId = if (it.hasRouteId()) it.routeId else null,
            routeType = if (it.hasRouteType()) it.routeType else null,
            tripId = if (it.hasTrip() && it.trip.hasTripId()) it.trip.tripId else null,
            stopId = if (it.hasStopId()) it.stopId else null,
          )
        },
      timestamp = Instant.now(),
    )
  }

  private fun getTranslatedText(translatedString: GtfsRealtime.TranslatedString): String? {
    return translatedString.translationList
      .firstOrNull { it.language.isNullOrEmpty() || it.language == "en" }
      ?.text ?: translatedString.translationList.firstOrNull()?.text
  }

  private fun mapVehicleStopStatus(
    status: GtfsRealtime.VehiclePosition.VehicleStopStatus
  ): VehicleStopStatus {
    return when (status) {
      GtfsRealtime.VehiclePosition.VehicleStopStatus.INCOMING_AT -> VehicleStopStatus.INCOMING_AT
      GtfsRealtime.VehiclePosition.VehicleStopStatus.STOPPED_AT -> VehicleStopStatus.STOPPED_AT
      GtfsRealtime.VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO ->
        VehicleStopStatus.IN_TRANSIT_TO
    }
  }

  private fun mapCongestionLevel(
    level: GtfsRealtime.VehiclePosition.CongestionLevel
  ): CongestionLevel {
    return when (level) {
      GtfsRealtime.VehiclePosition.CongestionLevel.UNKNOWN_CONGESTION_LEVEL ->
        CongestionLevel.UNKNOWN_CONGESTION_LEVEL
      GtfsRealtime.VehiclePosition.CongestionLevel.RUNNING_SMOOTHLY ->
        CongestionLevel.RUNNING_SMOOTHLY
      GtfsRealtime.VehiclePosition.CongestionLevel.STOP_AND_GO -> CongestionLevel.STOP_AND_GO
      GtfsRealtime.VehiclePosition.CongestionLevel.CONGESTION -> CongestionLevel.CONGESTION
      GtfsRealtime.VehiclePosition.CongestionLevel.SEVERE_CONGESTION ->
        CongestionLevel.SEVERE_CONGESTION
    }
  }

  private fun mapOccupancyStatus(
    status: GtfsRealtime.VehiclePosition.OccupancyStatus
  ): OccupancyStatus {
    return when (status) {
      GtfsRealtime.VehiclePosition.OccupancyStatus.EMPTY -> OccupancyStatus.EMPTY
      GtfsRealtime.VehiclePosition.OccupancyStatus.MANY_SEATS_AVAILABLE ->
        OccupancyStatus.MANY_SEATS_AVAILABLE
      GtfsRealtime.VehiclePosition.OccupancyStatus.FEW_SEATS_AVAILABLE ->
        OccupancyStatus.FEW_SEATS_AVAILABLE
      GtfsRealtime.VehiclePosition.OccupancyStatus.STANDING_ROOM_ONLY ->
        OccupancyStatus.STANDING_ROOM_ONLY
      GtfsRealtime.VehiclePosition.OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY ->
        OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY
      GtfsRealtime.VehiclePosition.OccupancyStatus.FULL -> OccupancyStatus.FULL
      GtfsRealtime.VehiclePosition.OccupancyStatus.NOT_ACCEPTING_PASSENGERS ->
        OccupancyStatus.NOT_ACCEPTING_PASSENGERS
      GtfsRealtime.VehiclePosition.OccupancyStatus.NO_DATA_AVAILABLE ->
        OccupancyStatus.NO_DATA_AVAILABLE
      GtfsRealtime.VehiclePosition.OccupancyStatus.NOT_BOARDABLE -> OccupancyStatus.NOT_BOARDABLE
    }
  }

  private fun mapTripScheduleRelationship(
    rel: GtfsRealtime.TripDescriptor.ScheduleRelationship
  ): TripScheduleRelationship {
    return when (rel) {
      GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED ->
        TripScheduleRelationship.SCHEDULED
      GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED -> TripScheduleRelationship.ADDED
      GtfsRealtime.TripDescriptor.ScheduleRelationship.UNSCHEDULED ->
        TripScheduleRelationship.UNSCHEDULED
      GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED -> TripScheduleRelationship.CANCELED
      GtfsRealtime.TripDescriptor.ScheduleRelationship.REPLACEMENT ->
        TripScheduleRelationship.REPLACEMENT
      GtfsRealtime.TripDescriptor.ScheduleRelationship.DUPLICATED ->
        TripScheduleRelationship.DUPLICATED
    }
  }

  private fun mapStopScheduleRelationship(
    rel: GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship
  ): StopScheduleRelationship {
    return when (rel) {
      GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED ->
        StopScheduleRelationship.SCHEDULED
      GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED ->
        StopScheduleRelationship.SKIPPED
      GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.NO_DATA ->
        StopScheduleRelationship.NO_DATA
      GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.UNSCHEDULED ->
        StopScheduleRelationship.UNSCHEDULED
    }
  }

  private fun mapAlertCause(cause: GtfsRealtime.Alert.Cause): AlertCause {
    return when (cause) {
      GtfsRealtime.Alert.Cause.UNKNOWN_CAUSE -> AlertCause.UNKNOWN_CAUSE
      GtfsRealtime.Alert.Cause.OTHER_CAUSE -> AlertCause.OTHER_CAUSE
      GtfsRealtime.Alert.Cause.TECHNICAL_PROBLEM -> AlertCause.TECHNICAL_PROBLEM
      GtfsRealtime.Alert.Cause.STRIKE -> AlertCause.STRIKE
      GtfsRealtime.Alert.Cause.DEMONSTRATION -> AlertCause.DEMONSTRATION
      GtfsRealtime.Alert.Cause.ACCIDENT -> AlertCause.ACCIDENT
      GtfsRealtime.Alert.Cause.HOLIDAY -> AlertCause.HOLIDAY
      GtfsRealtime.Alert.Cause.WEATHER -> AlertCause.WEATHER
      GtfsRealtime.Alert.Cause.MAINTENANCE -> AlertCause.MAINTENANCE
      GtfsRealtime.Alert.Cause.CONSTRUCTION -> AlertCause.CONSTRUCTION
      GtfsRealtime.Alert.Cause.POLICE_ACTIVITY -> AlertCause.POLICE_ACTIVITY
      GtfsRealtime.Alert.Cause.MEDICAL_EMERGENCY -> AlertCause.MEDICAL_EMERGENCY
    }
  }

  private fun mapAlertEffect(effect: GtfsRealtime.Alert.Effect): AlertEffect {
    return when (effect) {
      GtfsRealtime.Alert.Effect.NO_SERVICE -> AlertEffect.NO_SERVICE
      GtfsRealtime.Alert.Effect.REDUCED_SERVICE -> AlertEffect.REDUCED_SERVICE
      GtfsRealtime.Alert.Effect.SIGNIFICANT_DELAYS -> AlertEffect.SIGNIFICANT_DELAYS
      GtfsRealtime.Alert.Effect.DETOUR -> AlertEffect.DETOUR
      GtfsRealtime.Alert.Effect.ADDITIONAL_SERVICE -> AlertEffect.ADDITIONAL_SERVICE
      GtfsRealtime.Alert.Effect.MODIFIED_SERVICE -> AlertEffect.MODIFIED_SERVICE
      GtfsRealtime.Alert.Effect.OTHER_EFFECT -> AlertEffect.OTHER_EFFECT
      GtfsRealtime.Alert.Effect.UNKNOWN_EFFECT -> AlertEffect.UNKNOWN_EFFECT
      GtfsRealtime.Alert.Effect.STOP_MOVED -> AlertEffect.STOP_MOVED
      GtfsRealtime.Alert.Effect.NO_EFFECT -> AlertEffect.NO_EFFECT
      GtfsRealtime.Alert.Effect.ACCESSIBILITY_ISSUE -> AlertEffect.ACCESSIBILITY_ISSUE
    }
  }

  private fun processEntities(feedId: FeedId, parsedFeed: ParsedGtfsRtFeed): Int {
    // TODO: Implement entity persistence
    // - Batch persist vehicle positions to database
    // - Apply map matching for vehicle positions (ADR 0012)
    // - Batch persist trip updates
    // - Batch persist service alerts

    val totalCount =
      parsedFeed.vehiclePositions.size + parsedFeed.tripUpdates.size + parsedFeed.alerts.size

    meterRegistry
      .counter("gtfsrt.processing.vehicle_positions", "feed_id", feedId.value)
      .increment(parsedFeed.vehiclePositions.size.toDouble())
    meterRegistry
      .counter("gtfsrt.processing.trip_updates", "feed_id", feedId.value)
      .increment(parsedFeed.tripUpdates.size.toDouble())
    meterRegistry
      .counter("gtfsrt.processing.alerts", "feed_id", feedId.value)
      .increment(parsedFeed.alerts.size.toDouble())

    logger.info(
      "Feed {}: parsed {} vehicle positions, {} trip updates, {} alerts",
      feedId,
      parsedFeed.vehiclePositions.size,
      parsedFeed.tripUpdates.size,
      parsedFeed.alerts.size,
    )

    return totalCount
  }
}

/** Parsed GTFS-RT feed with domain models. */
data class ParsedGtfsRtFeed(
  val vehiclePositions: List<VehiclePosition>,
  val tripUpdates: List<TripUpdate>,
  val alerts: List<ServiceAlert>,
)

/** Outcome of processing a GTFS-RT feed. */
sealed class ProcessingOutcome {
  abstract val feedId: FeedId

  data class Processed(override val feedId: FeedId, val entityCount: Int) : ProcessingOutcome()

  data class Skipped(override val feedId: FeedId, val reason: String) : ProcessingOutcome()
}

package com.mobilispect.backend.gtfsrt.application

import com.google.transit.realtime.GtfsRealtime
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFeedState
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFetchResult
import com.mobilispect.backend.gtfsrt.infrastructure.InMemoryGtfsRtFeedStateRepository
import com.mobilispect.backend.gtfsrt.infrastructure.InMemoryServiceAlertRepository
import com.mobilispect.backend.gtfsrt.infrastructure.InMemoryTripUpdateRepository
import com.mobilispect.backend.gtfsrt.infrastructure.InMemoryVehiclePositionRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GtfsRtProcessingServiceTest {

  private lateinit var feedStateRepository: InMemoryGtfsRtFeedStateRepository
  private lateinit var vehiclePositionRepository: InMemoryVehiclePositionRepository
  private lateinit var tripUpdateRepository: InMemoryTripUpdateRepository
  private lateinit var serviceAlertRepository: InMemoryServiceAlertRepository
  private lateinit var meterRegistry: SimpleMeterRegistry
  private lateinit var processingService: GtfsRtProcessingService

  @BeforeEach
  fun setUp() {
    feedStateRepository = InMemoryGtfsRtFeedStateRepository()
    vehiclePositionRepository = InMemoryVehiclePositionRepository()
    tripUpdateRepository = InMemoryTripUpdateRepository()
    serviceAlertRepository = InMemoryServiceAlertRepository()
    meterRegistry = SimpleMeterRegistry()
    processingService =
      GtfsRtProcessingService(
        feedStateRepository,
        vehiclePositionRepository,
        tripUpdateRepository,
        serviceAlertRepository,
        meterRegistry,
      )
  }

  @Test
  fun `process parses vehicle positions from protobuf`() = runTest {
    val feedId = FeedId("test-feed")
    val timestamp = System.currentTimeMillis() / 1000
    val protobufData = createVehiclePositionFeed(timestamp)

    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = protobufData,
        contentHash = "abc123",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    val outcome = processingService.process(result)

    assertIs<ProcessingOutcome.Processed>(outcome)
    assertEquals(feedId, outcome.feedId)
    assertEquals(1, outcome.entityCount)
  }

  @Test
  fun `process parses trip updates from protobuf`() = runTest {
    val feedId = FeedId("test-feed")
    val timestamp = System.currentTimeMillis() / 1000
    val protobufData = createTripUpdateFeed(timestamp)

    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = protobufData,
        contentHash = "abc123",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    val outcome = processingService.process(result)

    assertIs<ProcessingOutcome.Processed>(outcome)
    assertEquals(feedId, outcome.feedId)
    assertEquals(1, outcome.entityCount)
  }

  @Test
  fun `process parses service alerts from protobuf`() = runTest {
    val feedId = FeedId("test-feed")
    val timestamp = System.currentTimeMillis() / 1000
    val protobufData = createServiceAlertFeed(timestamp)

    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = protobufData,
        contentHash = "abc123",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    val outcome = processingService.process(result)

    assertIs<ProcessingOutcome.Processed>(outcome)
    assertEquals(feedId, outcome.feedId)
    assertEquals(1, outcome.entityCount)
  }

  @Test
  fun `process skips when timestamp is not newer`() = runTest {
    val feedId = FeedId("test-feed")
    val oldTimestamp = 1000L
    val protobufData = createVehiclePositionFeed(oldTimestamp)

    // Pre-populate state with a newer timestamp
    feedStateRepository.save(
      GtfsRtFeedState(
        feedId = feedId,
        contentHash = "old-hash",
        etag = null,
        lastModified = null,
        gtfsRtTimestamp = 2000L,
        lastFetchedAt = Instant.now().minusSeconds(60),
        lastProcessedAt = Instant.now().minusSeconds(60),
      )
    )

    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = protobufData,
        contentHash = "abc123",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    val outcome = processingService.process(result)

    assertIs<ProcessingOutcome.Skipped>(outcome)
    assertEquals(feedId, outcome.feedId)
    assertEquals("TIMESTAMP_NOT_NEWER", outcome.reason)
  }

  @Test
  fun `process handles invalid protobuf gracefully`() = runTest {
    val feedId = FeedId("test-feed")
    val invalidData = "not a valid protobuf".toByteArray()

    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = invalidData,
        contentHash = "abc123",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    val outcome = processingService.process(result)

    assertIs<ProcessingOutcome.Skipped>(outcome)
    assertEquals("PARSE_ERROR", outcome.reason)
  }

  @Test
  fun `process updates state after successful processing`() = runTest {
    val feedId = FeedId("test-feed")
    val timestamp = System.currentTimeMillis() / 1000
    val protobufData = createVehiclePositionFeed(timestamp)

    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = protobufData,
        contentHash = "abc123",
        etag = "etag-value",
        lastModified = "Wed, 01 Jan 2025 00:00:00 GMT",
        fetchedAt = Instant.now(),
      )

    processingService.process(result)

    val savedState = feedStateRepository.findByFeedId(feedId)
    assertEquals("abc123", savedState?.contentHash)
    assertEquals("etag-value", savedState?.etag)
    assertEquals(timestamp, savedState?.gtfsRtTimestamp)
  }

  @Test
  fun `process increments success metric`() = runTest {
    val feedId = FeedId("test-feed")
    val timestamp = System.currentTimeMillis() / 1000
    val protobufData = createVehiclePositionFeed(timestamp)

    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = protobufData,
        contentHash = "abc123",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    processingService.process(result)

    val counter = meterRegistry.find("gtfsrt.processing.success").counter()
    assertEquals(1.0, counter?.count())
  }

  @Test
  fun `process counts vehicle positions correctly`() = runTest {
    val feedId = FeedId("test-feed")
    val timestamp = System.currentTimeMillis() / 1000
    val protobufData = createMultipleVehiclePositionsFeed(timestamp, count = 5)

    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = protobufData,
        contentHash = "abc123",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    val outcome = processingService.process(result)

    assertIs<ProcessingOutcome.Processed>(outcome)
    assertEquals(5, outcome.entityCount)
  }

  @Test
  fun `process persists vehicle positions to repository`() = runTest {
    val feedId = FeedId("test-feed")
    val timestamp = System.currentTimeMillis() / 1000
    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = createVehiclePositionFeed(timestamp),
        contentHash = "abc123",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    processingService.process(result)

    val saved = vehiclePositionRepository.findAll()
    assertEquals(1, saved.size)
    assertEquals("vehicle-1", saved[0].vehicleId)
    assertEquals(feedId, saved[0].feedId)
  }

  @Test
  fun `process persists trip updates to repository`() = runTest {
    val feedId = FeedId("test-feed")
    val timestamp = System.currentTimeMillis() / 1000
    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = createTripUpdateFeed(timestamp),
        contentHash = "abc123",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    processingService.process(result)

    val saved = tripUpdateRepository.findAll()
    assertEquals(1, saved.size)
    assertEquals("trip-1", saved[0].tripId)
    assertEquals(feedId, saved[0].feedId)
  }

  @Test
  fun `process persists service alerts to repository`() = runTest {
    val feedId = FeedId("test-feed")
    val timestamp = System.currentTimeMillis() / 1000
    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = createServiceAlertFeed(timestamp),
        contentHash = "abc123",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    processingService.process(result)

    val saved = serviceAlertRepository.findAll()
    assertEquals(1, saved.size)
    assertEquals("alert-1", saved[0].alertId)
    assertEquals(feedId, saved[0].feedId)
  }

  private fun createVehiclePositionFeed(timestamp: Long): ByteArray {
    val header =
      GtfsRealtime.FeedHeader.newBuilder()
        .setGtfsRealtimeVersion("2.0")
        .setTimestamp(timestamp)
        .build()

    val position =
      GtfsRealtime.Position.newBuilder()
        .setLatitude(37.7749f)
        .setLongitude(-122.4194f)
        .setBearing(90f)
        .setSpeed(10f)
        .build()

    val vehicleDescriptor = GtfsRealtime.VehicleDescriptor.newBuilder().setId("vehicle-1").build()

    val tripDescriptor =
      GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip-1").setRouteId("route-1").build()

    val vehiclePosition =
      GtfsRealtime.VehiclePosition.newBuilder()
        .setPosition(position)
        .setVehicle(vehicleDescriptor)
        .setTrip(tripDescriptor)
        .setTimestamp(timestamp)
        .setCurrentStatus(GtfsRealtime.VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO)
        .build()

    val entity =
      GtfsRealtime.FeedEntity.newBuilder().setId("entity-1").setVehicle(vehiclePosition).build()

    return GtfsRealtime.FeedMessage.newBuilder()
      .setHeader(header)
      .addEntity(entity)
      .build()
      .toByteArray()
  }

  private fun createMultipleVehiclePositionsFeed(timestamp: Long, count: Int): ByteArray {
    val header =
      GtfsRealtime.FeedHeader.newBuilder()
        .setGtfsRealtimeVersion("2.0")
        .setTimestamp(timestamp)
        .build()

    val builder = GtfsRealtime.FeedMessage.newBuilder().setHeader(header)

    repeat(count) { i ->
      val position =
        GtfsRealtime.Position.newBuilder()
          .setLatitude(37.7749f + i * 0.001f)
          .setLongitude(-122.4194f + i * 0.001f)
          .build()

      val vehicleDescriptor =
        GtfsRealtime.VehicleDescriptor.newBuilder().setId("vehicle-$i").build()

      val vehiclePosition =
        GtfsRealtime.VehiclePosition.newBuilder()
          .setPosition(position)
          .setVehicle(vehicleDescriptor)
          .setTimestamp(timestamp)
          .build()

      val entity =
        GtfsRealtime.FeedEntity.newBuilder().setId("entity-$i").setVehicle(vehiclePosition).build()

      builder.addEntity(entity)
    }

    return builder.build().toByteArray()
  }

  private fun createTripUpdateFeed(timestamp: Long): ByteArray {
    val header =
      GtfsRealtime.FeedHeader.newBuilder()
        .setGtfsRealtimeVersion("2.0")
        .setTimestamp(timestamp)
        .build()

    val tripDescriptor =
      GtfsRealtime.TripDescriptor.newBuilder()
        .setTripId("trip-1")
        .setRouteId("route-1")
        .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
        .build()

    val stopTimeEvent =
      GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
        .setDelay(120)
        .setTime(timestamp + 120)
        .build()

    val stopTimeUpdate =
      GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
        .setStopSequence(1)
        .setStopId("stop-1")
        .setArrival(stopTimeEvent)
        .build()

    val tripUpdate =
      GtfsRealtime.TripUpdate.newBuilder()
        .setTrip(tripDescriptor)
        .setTimestamp(timestamp)
        .addStopTimeUpdate(stopTimeUpdate)
        .build()

    val entity =
      GtfsRealtime.FeedEntity.newBuilder().setId("entity-1").setTripUpdate(tripUpdate).build()

    return GtfsRealtime.FeedMessage.newBuilder()
      .setHeader(header)
      .addEntity(entity)
      .build()
      .toByteArray()
  }

  private fun createServiceAlertFeed(timestamp: Long): ByteArray {
    val header =
      GtfsRealtime.FeedHeader.newBuilder()
        .setGtfsRealtimeVersion("2.0")
        .setTimestamp(timestamp)
        .build()

    val translation =
      GtfsRealtime.TranslatedString.Translation.newBuilder()
        .setText("Service disruption on Route 1")
        .setLanguage("en")
        .build()

    val headerText = GtfsRealtime.TranslatedString.newBuilder().addTranslation(translation).build()

    val informedEntity = GtfsRealtime.EntitySelector.newBuilder().setRouteId("route-1").build()

    val alert =
      GtfsRealtime.Alert.newBuilder()
        .setCause(GtfsRealtime.Alert.Cause.ACCIDENT)
        .setEffect(GtfsRealtime.Alert.Effect.SIGNIFICANT_DELAYS)
        .setHeaderText(headerText)
        .addInformedEntity(informedEntity)
        .build()

    val entity = GtfsRealtime.FeedEntity.newBuilder().setId("alert-1").setAlert(alert).build()

    return GtfsRealtime.FeedMessage.newBuilder()
      .setHeader(header)
      .addEntity(entity)
      .build()
      .toByteArray()
  }
}

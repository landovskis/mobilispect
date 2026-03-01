package com.mobilispect.backend.gtfsrt

import com.google.transit.realtime.GtfsRealtime
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.gtfsrt.application.GtfsRtProcessingService
import com.mobilispect.backend.gtfsrt.application.ProcessingOutcome
import com.mobilispect.backend.gtfsrt.data.repository.ServiceAlertJpaRepository
import com.mobilispect.backend.gtfsrt.data.repository.TripUpdateJpaRepository
import com.mobilispect.backend.gtfsrt.data.repository.VehiclePositionJpaRepository
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFetchResult
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Transactional
@Testcontainers
class GtfsRtProcessingServiceIntegrationTest {

  companion object {
    @Container
    @ServiceConnection
    @JvmStatic
    val postgres: PostgreSQLContainer<*> =
      PostgreSQLContainer("postgres:18-alpine")
        .withDatabaseName("mobilispect_test")
        .withUsername("test")
        .withPassword("test")
  }

  @Autowired private lateinit var processingService: GtfsRtProcessingService

  @Autowired private lateinit var vehiclePositionJpaRepository: VehiclePositionJpaRepository

  @Autowired private lateinit var tripUpdateJpaRepository: TripUpdateJpaRepository

  @Autowired private lateinit var serviceAlertJpaRepository: ServiceAlertJpaRepository

  @BeforeEach
  fun setUp() {
    vehiclePositionJpaRepository.deleteAll()
    tripUpdateJpaRepository.deleteAll()
    serviceAlertJpaRepository.deleteAll()
  }

  @Test
  fun `process persists vehicle positions to PostgreSQL`() = runBlocking {
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

    val outcome = processingService.process(result)

    assertThat(outcome).isInstanceOf(ProcessingOutcome.Processed::class.java)
    val saved = vehiclePositionJpaRepository.findAll()
    assertThat(saved).hasSize(1)
    assertThat(saved[0].vehicleId).isEqualTo("vehicle-1")
    assertThat(saved[0].feedId).isEqualTo("test-feed")
    assertThat(saved[0].latitude).isEqualTo(37.7749, org.assertj.core.api.Assertions.within(0.001))
    assertThat(saved[0].longitude)
      .isEqualTo(-122.4194, org.assertj.core.api.Assertions.within(0.001))
  }

  @Test
  fun `process persists trip updates to PostgreSQL`() = runBlocking {
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

    val outcome = processingService.process(result)

    assertThat(outcome).isInstanceOf(ProcessingOutcome.Processed::class.java)
    val saved = tripUpdateJpaRepository.findAll()
    assertThat(saved).hasSize(1)
    assertThat(saved[0].tripId).isEqualTo("trip-1")
    assertThat(saved[0].feedId).isEqualTo("test-feed")
    assertThat(saved[0].routeId).isEqualTo("route-1")
    assertThat(saved[0].delay).isNull()
  }

  @Test
  fun `process persists service alerts to PostgreSQL`() = runBlocking {
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

    val outcome = processingService.process(result)

    assertThat(outcome).isInstanceOf(ProcessingOutcome.Processed::class.java)
    val saved = serviceAlertJpaRepository.findAll()
    assertThat(saved).hasSize(1)
    assertThat(saved[0].alertId).isEqualTo("alert-1")
    assertThat(saved[0].feedId).isEqualTo("test-feed")
    assertThat(saved[0].cause).isEqualTo("ACCIDENT")
    assertThat(saved[0].effect).isEqualTo("SIGNIFICANT_DELAYS")
    assertThat(saved[0].headerText).isEqualTo("Service disruption on Route 1")
  }

  @Test
  fun `process succeeds with empty database (bootstrap scenario)`() = runBlocking {
    // This covers the ADR 0011 empty-DB bootstrap scenario:
    // When no feed state exists yet, processing should proceed without errors.
    val feedId = FeedId("new-feed")
    val timestamp = System.currentTimeMillis() / 1000

    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = createVehiclePositionFeed(timestamp),
        contentHash = "bootstrap-hash",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    val outcome = processingService.process(result)

    assertThat(outcome).isInstanceOf(ProcessingOutcome.Processed::class.java)
    assertThat((outcome as ProcessingOutcome.Processed).entityCount).isEqualTo(1)
    assertThat(vehiclePositionJpaRepository.count()).isEqualTo(1)
  }

  @Test
  fun `process persists multiple entity types in a single feed`() = runBlocking {
    val feedId = FeedId("mixed-feed")
    val timestamp = System.currentTimeMillis() / 1000

    val result =
      GtfsRtFetchResult.NewData(
        feedId = feedId,
        data = createMixedFeed(timestamp),
        contentHash = "mixed-hash",
        etag = null,
        lastModified = null,
        fetchedAt = Instant.now(),
      )

    val outcome = processingService.process(result)

    assertThat(outcome).isInstanceOf(ProcessingOutcome.Processed::class.java)
    assertThat((outcome as ProcessingOutcome.Processed).entityCount).isEqualTo(3)
    assertThat(vehiclePositionJpaRepository.count()).isEqualTo(1)
    assertThat(tripUpdateJpaRepository.count()).isEqualTo(1)
    assertThat(serviceAlertJpaRepository.count()).isEqualTo(1)
  }

  // --- Protobuf test data helpers ---

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

    val stopTimeUpdate =
      GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
        .setStopSequence(1)
        .setStopId("stop-1")
        .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(120).build())
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

  private fun createMixedFeed(timestamp: Long): ByteArray {
    val header =
      GtfsRealtime.FeedHeader.newBuilder()
        .setGtfsRealtimeVersion("2.0")
        .setTimestamp(timestamp)
        .build()

    // Vehicle position entity
    val vehiclePosition =
      GtfsRealtime.VehiclePosition.newBuilder()
        .setPosition(
          GtfsRealtime.Position.newBuilder().setLatitude(37.7749f).setLongitude(-122.4194f).build()
        )
        .setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder().setId("vehicle-1").build())
        .setTimestamp(timestamp)
        .build()

    val vehicleEntity =
      GtfsRealtime.FeedEntity.newBuilder()
        .setId("entity-vehicle")
        .setVehicle(vehiclePosition)
        .build()

    // Trip update entity
    val tripUpdate =
      GtfsRealtime.TripUpdate.newBuilder()
        .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip-1").build())
        .setTimestamp(timestamp)
        .build()

    val tripEntity =
      GtfsRealtime.FeedEntity.newBuilder().setId("entity-trip").setTripUpdate(tripUpdate).build()

    // Alert entity
    val alert =
      GtfsRealtime.Alert.newBuilder()
        .setCause(GtfsRealtime.Alert.Cause.TECHNICAL_PROBLEM)
        .setEffect(GtfsRealtime.Alert.Effect.DETOUR)
        .build()

    val alertEntity = GtfsRealtime.FeedEntity.newBuilder().setId("alert-1").setAlert(alert).build()

    return GtfsRealtime.FeedMessage.newBuilder()
      .setHeader(header)
      .addEntity(vehicleEntity)
      .addEntity(tripEntity)
      .addEntity(alertEntity)
      .build()
      .toByteArray()
  }
}

package com.mobilispect.backend.route.handler

import com.mobilispect.backend.feed.api.GTFSStopTime
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.feed.api.ids.GTFSStopId
import com.mobilispect.backend.feed.api.ids.GTFSTripId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.VariantDepartureRepository
import com.mobilispect.backend.route.domain.repository.VariantScheduleRepository
import com.mobilispect.backend.route.domain.service.VariantHashGenerator
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VariantScheduleFeedDataHandlerTest {

  private lateinit var variantScheduleRepository: VariantScheduleRepository
  private lateinit var variantDepartureRepository: VariantDepartureRepository
  private lateinit var variantHashGenerator: VariantHashGenerator
  private lateinit var handler: VariantScheduleFeedDataHandler

  @BeforeEach
  fun setup() {
    variantScheduleRepository = mockk(relaxed = true)
    variantDepartureRepository = mockk(relaxed = true)
    variantHashGenerator = mockk(relaxed = true)
    handler =
      VariantScheduleFeedDataHandler(
        variantScheduleRepository,
        variantDepartureRepository,
        variantHashGenerator,
      )
  }

  @Test
  fun `should calculate schedule for variant with trips`() {
    // Given
    val feedId = FeedId("test-feed")
    val variantId = VariantHash("a".repeat(64))
    val routeId = FeedLocalRouteId("route-1")

    val trip1 =
      GTFSTrip(
        routeId = routeId,
        tripId = GTFSTripId("trip-1"),
        directionId = 0,
        headsign = "Downtown",
        shapeId = null,
        stopTimes =
          listOf(
            GTFSStopTime(
              stopId = GTFSStopId("stop-1"),
              stopSequence = 1,
              departureTime = LocalTime.of(6, 0),
              shapeDistTraveledKm = null,
            ),
            GTFSStopTime(
              stopId = GTFSStopId("stop-2"),
              stopSequence = 2,
              departureTime = LocalTime.of(6, 15),
              shapeDistTraveledKm = null,
            ),
          ),
      )

    val trip2 =
      GTFSTrip(
        routeId = routeId,
        tripId = GTFSTripId("trip-2"),
        directionId = 0,
        headsign = "Downtown",
        shapeId = null,
        stopTimes =
          listOf(
            GTFSStopTime(
              stopId = GTFSStopId("stop-1"),
              stopSequence = 1,
              departureTime = LocalTime.of(22, 0),
              shapeDistTraveledKm = null,
            ),
            GTFSStopTime(
              stopId = GTFSStopId("stop-2"),
              stopSequence = 2,
              departureTime = LocalTime.of(22, 15),
              shapeDistTraveledKm = null,
            ),
          ),
      )

    val trips = listOf(trip1, trip2)
    val bundle = GTFSDataBundle(feedId = feedId, trips = trips)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    every { variantHashGenerator.fromStops(any()) } returns variantId

    // When
    val result = handler.handle(feedId, bundle, context)

    // Then
    val savedSchedule = slot<com.mobilispect.backend.route.domain.model.VariantSchedule>()
    verify { variantScheduleRepository.save(capture(savedSchedule)) }

    assertEquals(variantId.value, savedSchedule.captured.variantId)
    assertEquals(LocalTime.of(6, 0), savedSchedule.captured.firstDepartureTime)
    assertEquals(LocalTime.of(22, 0), savedSchedule.captured.lastDepartureTime)
    assertEquals(2, savedSchedule.captured.tripCount)
    assertNotNull(savedSchedule.captured.calculatedAt)
  }

  @Test
  fun `should skip variants with no departure times`() {
    // Given
    val feedId = FeedId("test-feed")
    val trip =
      GTFSTrip(
        routeId = FeedLocalRouteId("route-1"),
        tripId = GTFSTripId("trip-1"),
        directionId = 0,
        headsign = "Downtown",
        shapeId = null,
        stopTimes =
          listOf(
            GTFSStopTime(
              stopId = GTFSStopId("stop-1"),
              stopSequence = 1,
              departureTime = null, // No departure time
              shapeDistTraveledKm = null,
            )
          ),
      )

    val bundle = GTFSDataBundle(feedId = feedId, trips = listOf(trip))
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    // When
    val result = handler.handle(feedId, bundle, context)

    // Then
    verify(exactly = 0) { variantScheduleRepository.save(any()) }
  }
}

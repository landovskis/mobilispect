package com.mobilispect.backend.feed.api.handler

import com.mobilispect.backend.feed.api.GTFSAgency
import com.mobilispect.backend.feed.api.GTFSRoute
import com.mobilispect.backend.feed.api.GTFSShapePoint
import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.feed.api.ids.GTFSStopId
import com.mobilispect.backend.feed.api.ids.GTFSTripId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GTFSDataBundleTest {

  @Test
  fun `should create bundle with all data types`() {
    val feedId = FeedId("test-feed")
    val agencies =
      listOf(
        GTFSAgency(
          agencyId = FeedLocalAgencyId("agency-1"),
          name = "Test Agency",
          url = null,
          timezone = null,
          phone = null,
        )
      )
    val routes =
      listOf(
        GTFSRoute(
          routeId = FeedLocalRouteId("route-1"),
          agencyId = FeedLocalAgencyId("agency-1"),
          shortName = "R1",
          longName = "Route 1",
          type = 3,
        )
      )
    val trips =
      listOf(
        GTFSTrip(
          routeId = FeedLocalRouteId("route-1"),
          tripId = GTFSTripId("trip-1"),
          directionId = 0,
          headsign = "Downtown",
          shapeId = "shape-1",
          stopTimes = emptyList(),
        )
      )
    val stops =
      listOf(
        GTFSStop(
          stopId = GTFSStopId("stop-1"),
          name = "Stop 1",
          latitude = 37.0,
          longitude = -122.0,
        )
      )
    val shapes =
      mapOf(
        "shape-1" to
          listOf(
            GTFSShapePoint(latitude = 37.0, longitude = -122.0, sequence = 1, distTraveledKm = null)
          )
      )

    val bundle =
      GTFSDataBundle(
        feedId = feedId,
        agencies = agencies,
        routes = routes,
        trips = trips,
        stops = stops,
        shapes = shapes,
      )

    assertThat(bundle.feedId).isEqualTo(feedId)
    assertThat(bundle.agencies).hasSize(1)
    assertThat(bundle.routes).hasSize(1)
    assertThat(bundle.trips).hasSize(1)
    assertThat(bundle.stops).hasSize(1)
    assertThat(bundle.shapes).hasSize(1)
  }

  @Test
  fun `has method should return true when data type is present`() {
    val bundle =
      GTFSDataBundle(
        feedId = FeedId("test-feed"),
        agencies =
          listOf(
            GTFSAgency(
              agencyId = FeedLocalAgencyId("agency-1"),
              name = "Test Agency",
              url = null,
              timezone = null,
              phone = null,
            )
          ),
      )

    assertThat(bundle.has(GTFSDataType.AGENCY)).isTrue()
    assertThat(bundle.has(GTFSDataType.ROUTE)).isFalse()
    assertThat(bundle.has(GTFSDataType.TRIP)).isFalse()
    assertThat(bundle.has(GTFSDataType.STOP)).isFalse()
    assertThat(bundle.has(GTFSDataType.SHAPE)).isFalse()
  }

  @Test
  fun `should create empty bundle with only feedId`() {
    val bundle = GTFSDataBundle(feedId = FeedId("empty-feed"))

    assertThat(bundle.agencies).isEmpty()
    assertThat(bundle.routes).isEmpty()
    assertThat(bundle.trips).isEmpty()
    assertThat(bundle.stops).isEmpty()
    assertThat(bundle.shapes).isEmpty()
    assertThat(bundle.stopTimes).isEmpty()
    assertThat(bundle.frequencies).isEmpty()
    assertThat(bundle.calendars).isEmpty()
  }
}

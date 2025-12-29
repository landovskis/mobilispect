package com.mobilispect.backend.schedule.transit_land

import com.mobilispect.backend.infastructure.transit_land.RouteResult
import com.mobilispect.backend.infastructure.transit_land.RouteResultItem
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandEntityType
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingEntity
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingRepository
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class TransitLandRouteIDDataSourceTest {
  private val transitLandAPI: TransitLandAPI = mock()
  private val credentialsRepository: TransitLandCredentialsRepository = mock()
  private val mappingRepository: TransitLandOnestopIdMappingRepository = mock()
  private val subject =
    TransitLandRouteIDDataSource(
      transitLandAPI,
      credentialsRepository,
      mappingRepository,
      sleepMillisProvider = { 0L },
      sleep = {},
    )

  @Test
  fun `returns cached route ids without calling transit land`() {
    val feedId = "f-test"
    val cached =
      listOf(
        TransitLandOnestopIdMappingEntity(
          feedOnestopId = feedId,
          entityType = TransitLandEntityType.ROUTE,
          gtfsId = "R1",
          onestopId = "r-1",
        )
      )
    whenever(
        mappingRepository.findAllByFeedOnestopIdAndEntityType(feedId, TransitLandEntityType.ROUTE)
      )
      .thenReturn(cached)

    val result = subject.routeIDs(feedId).getOrNull()

    assertThat(result).containsEntry("R1", "r-1")
    verifyNoInteractions(transitLandAPI)
  }

  @Test
  fun `stores route ids on cache miss`() {
    val feedId = "f-test"
    whenever(
        mappingRepository.findAllByFeedOnestopIdAndEntityType(feedId, TransitLandEntityType.ROUTE)
      )
      .thenReturn(emptyList())
    whenever(credentialsRepository.get()).thenReturn("api-key")
    whenever(transitLandAPI.routes(apiKey = eq("api-key"), feedID = eq(feedId), paging = any()))
      .thenReturn(
        Result.success(
          RouteResult(listOf(RouteResultItem(id = "r-1", agencyID = "o-1", routeID = "R1")))
        )
      )

    val result = subject.routeIDs(feedId).getOrNull()

    assertThat(result).containsEntry("R1", "r-1")
    val captor = argumentCaptor<Iterable<TransitLandOnestopIdMappingEntity>>()
    verify(mappingRepository).saveAll(captor.capture())
    val saved = captor.firstValue.toList()
    assertThat(saved).hasSize(1)
    val mapping = saved.first()
    assertThat(mapping.feedOnestopId).isEqualTo(feedId)
    assertThat(mapping.entityType).isEqualTo(TransitLandEntityType.ROUTE)
    assertThat(mapping.gtfsId).isEqualTo("R1")
    assertThat(mapping.onestopId).isEqualTo("r-1")
  }
}

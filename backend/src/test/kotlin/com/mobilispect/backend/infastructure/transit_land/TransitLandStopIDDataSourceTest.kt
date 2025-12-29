package com.mobilispect.backend.infastructure.transit_land

import com.mobilispect.backend.AgencyResult
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandEntityType
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingEntity
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingRepository
import com.mobilispect.backend.schedule.ScheduledFeed
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import com.mobilispect.backend.transit_land.PagingParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class TransitLandStopIDDataSourceTest {
  private val credentialsRepository: TransitLandCredentialsRepository = mock()
  private val mappingRepository: TransitLandOnestopIdMappingRepository = mock()

  @Test
  fun `returns cached stop id without calling transit land`() {
    val feedId = "f-test"
    val stopId = "stop-1"
    val transitLandAPI =
      object : BaseTransitLandAPI() {
        override fun stop(apiKey: String, feedID: String, stopID: String): Result<StopResultItem> {
          throw AssertionError("TransitLandAPI.stop should not be called for cached mappings")
        }
      }
    val subject =
      TransitLandStopIDDataSource(transitLandAPI, credentialsRepository, mappingRepository)
    val cached =
      TransitLandOnestopIdMappingEntity(
        feedOnestopId = feedId,
        entityType = TransitLandEntityType.STOP,
        gtfsId = stopId,
        onestopId = "s-1",
      )
    whenever(
        mappingRepository.findByFeedOnestopIdAndEntityTypeAndGtfsId(
          feedId,
          TransitLandEntityType.STOP,
          stopId,
        )
      )
      .thenReturn(cached)

    val result = subject.stop(feedId, stopId).getOrNull()

    assertThat(result).isEqualTo("s-1")
  }

  @Test
  fun `stores stop id on cache miss`() {
    val feedId = "f-test"
    val stopId = "stop-1"
    val transitLandAPI =
      object : BaseTransitLandAPI() {
        override fun stop(apiKey: String, feedID: String, stopID: String): Result<StopResultItem> =
          Result.success(StopResultItem(uid = "s-1", stopID = stopId))
      }
    val subject =
      TransitLandStopIDDataSource(transitLandAPI, credentialsRepository, mappingRepository)
    whenever(
        mappingRepository.findByFeedOnestopIdAndEntityTypeAndGtfsId(
          feedId,
          TransitLandEntityType.STOP,
          stopId,
        )
      )
      .thenReturn(null)
    whenever(credentialsRepository.get()).thenReturn("api-key")
    whenever(mappingRepository.save(any())).thenAnswer { it.getArgument(0) }

    val result = subject.stop(feedId, stopId).getOrNull()

    assertThat(result).isEqualTo("s-1")
    val captor = argumentCaptor<TransitLandOnestopIdMappingEntity>()
    verify(mappingRepository).save(captor.capture())
    val saved = captor.firstValue
    assertThat(saved.feedOnestopId).isEqualTo(feedId)
    assertThat(saved.entityType).isEqualTo(TransitLandEntityType.STOP)
    assertThat(saved.gtfsId).isEqualTo(stopId)
    assertThat(saved.onestopId).isEqualTo("s-1")
  }
}

private open class BaseTransitLandAPI : TransitLandAPI {
  override fun feed(apiKey: String, feedID: String): Result<ScheduledFeed> =
    error("Not used in TransitLandStopIDDataSourceTest")

  override fun feeds(
    apiKey: String,
    search: String,
    spec: String,
  ): Result<Collection<ScheduledFeed>> = error("Not used in TransitLandStopIDDataSourceTest")

  override fun feedsByCoordinates(
    apiKey: String,
    lat: Double,
    lon: Double,
    radius: Int,
    spec: String,
  ): Result<Collection<ScheduledFeed>> = error("Not used in TransitLandStopIDDataSourceTest")

  override fun agencies(apiKey: String, region: String?, feedID: String?): Result<AgencyResult> =
    error("Not used in TransitLandStopIDDataSourceTest")

  override fun routes(
    apiKey: String,
    feedID: String,
    paging: PagingParameters,
  ): Result<RouteResult> = error("Not used in TransitLandStopIDDataSourceTest")

  override fun stop(apiKey: String, feedID: String, stopID: String): Result<StopResultItem> =
    error("Not used in TransitLandStopIDDataSourceTest")

  override fun operators(apiKey: String, paging: PagingParameters): Result<OperatorsResult> =
    error("Not used in TransitLandStopIDDataSourceTest")

  override fun feedMetadata(apiKey: String, feedId: String): Result<FeedMetadataResult> =
    error("Not used in TransitLandStopIDDataSourceTest")

  override fun metroAreas(apiKey: String): Result<List<MetroAreaResultItem>> =
    error("Not used in TransitLandStopIDDataSourceTest")
}

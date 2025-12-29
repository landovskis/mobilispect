package com.mobilispect.backend

import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandEntityType
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingEntity
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingRepository
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class TransitLandAgencyIDDataSourceTest {
  private val transitLandAPI: TransitLandAPI = mock()
  private val credentialsRepository: TransitLandCredentialsRepository = mock()
  private val mappingRepository: TransitLandOnestopIdMappingRepository = mock()
  private val subject =
    TransitLandAgencyIDDataSource(transitLandAPI, credentialsRepository, mappingRepository)

  @Test
  fun `returns cached agency ids without calling transit land`() {
    val feedId = "f-test"
    val cached =
      listOf(
        TransitLandOnestopIdMappingEntity(
          feedOnestopId = feedId,
          entityType = TransitLandEntityType.AGENCY,
          gtfsId = "A1",
          onestopId = "o-1",
        )
      )
    whenever(
        mappingRepository.findAllByFeedOnestopIdAndEntityType(feedId, TransitLandEntityType.AGENCY)
      )
      .thenReturn(cached)

    val result = subject.agencyIDs(feedId).getOrNull()

    assertThat(result).containsEntry("A1", "o-1")
    verifyNoInteractions(transitLandAPI)
  }

  @Test
  fun `stores agency ids on cache miss`() {
    val feedId = "f-test"
    whenever(
        mappingRepository.findAllByFeedOnestopIdAndEntityType(feedId, TransitLandEntityType.AGENCY)
      )
      .thenReturn(emptyList())
    whenever(credentialsRepository.get()).thenReturn("api-key")
    whenever(transitLandAPI.agencies(apiKey = "api-key", feedID = feedId))
      .thenReturn(
        Result.success(
          AgencyResult(
            listOf(AgencyResultItem(id = "o-1", version = "v1", feedID = feedId, agencyID = "A1"))
          )
        )
      )

    val result = subject.agencyIDs(feedId).getOrNull()

    assertThat(result).containsEntry("A1", "o-1")
    val captor = argumentCaptor<Iterable<TransitLandOnestopIdMappingEntity>>()
    verify(mappingRepository).saveAll(captor.capture())
    val saved = captor.firstValue.toList()
    assertThat(saved).hasSize(1)
    val mapping = saved.first()
    assertThat(mapping.feedOnestopId).isEqualTo(feedId)
    assertThat(mapping.entityType).isEqualTo(TransitLandEntityType.AGENCY)
    assertThat(mapping.gtfsId).isEqualTo("A1")
    assertThat(mapping.onestopId).isEqualTo("o-1")
  }
}

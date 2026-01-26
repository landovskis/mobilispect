package com.mobilispect.backend.agency.handler

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.api.GTFSAgency
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.GTFSDataType
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AgencyFeedDataHandlerTest {

  private lateinit var agencyRepository: AgencyRepository
  private lateinit var handler: AgencyFeedDataHandler

  @BeforeEach
  fun setUp() {
    agencyRepository = mockk()
    handler = AgencyFeedDataHandler(agencyRepository)
  }

  @Test
  fun `dataTypes returns AGENCY`() {
    assertThat(handler.dataTypes()).containsExactly(GTFSDataType.AGENCY)
  }

  @Test
  fun `priority returns 10 for highest priority`() {
    // Agencies should be processed first since routes depend on them
    assertThat(handler.priority()).isEqualTo(10)
  }

  @Test
  fun `handle saves agencies from bundle`() {
    val feedId = FeedId("f-abc-test")
    val gtfsAgency =
      GTFSAgency(
        agencyId = FeedLocalAgencyId("agency-1"),
        name = "Test Transit",
        url = "https://test-transit.com",
        timezone = "America/New_York",
        phone = "555-1234",
      )
    val bundle = GTFSDataBundle(feedId = feedId, agencies = listOf(gtfsAgency))
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val savedAgency = slot<Agency>()
    every { agencyRepository.save(capture(savedAgency)) } answers { savedAgency.captured }

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(1)

    verify(exactly = 1) { agencyRepository.save(any()) }
    assertThat(savedAgency.captured.feedId).isEqualTo(feedId)
    assertThat(savedAgency.captured.name).isEqualTo("Test Transit")
    assertThat(savedAgency.captured.active).isTrue()
  }

  @Test
  fun `handle processes multiple agencies`() {
    val feedId = FeedId("f-abc-test")
    val agencies =
      listOf(
        GTFSAgency(
          agencyId = FeedLocalAgencyId("agency-1"),
          name = "Transit A",
          url = null,
          timezone = null,
          phone = null,
        ),
        GTFSAgency(
          agencyId = FeedLocalAgencyId("agency-2"),
          name = "Transit B",
          url = "https://b.com",
          timezone = "America/Chicago",
          phone = "555-5678",
        ),
      )
    val bundle = GTFSDataBundle(feedId = feedId, agencies = agencies)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    every { agencyRepository.save(any()) } answers { firstArg() }

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(2)
    verify(exactly = 2) { agencyRepository.save(any()) }
  }

  @Test
  fun `handle returns success with zero when bundle has no agencies`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId, agencies = emptyList())
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(0)
    verify(exactly = 0) { agencyRepository.save(any()) }
  }

  @Test
  fun `handle generates correct agency ID from feed and gtfs agency id`() {
    val feedId = FeedId("f-abc-test")
    val gtfsAgency =
      GTFSAgency(
        agencyId = FeedLocalAgencyId("my-agency"),
        name = "My Agency",
        url = null,
        timezone = null,
        phone = null,
      )
    val bundle = GTFSDataBundle(feedId = feedId, agencies = listOf(gtfsAgency))
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val savedAgency = slot<Agency>()
    every { agencyRepository.save(capture(savedAgency)) } answers { savedAgency.captured }

    handler.handle(feedId, bundle, context)

    // Agency ID should be constructed from feed ID and GTFS agency ID
    assertThat(savedAgency.captured.agencyId)
      .isEqualTo(AgencyId(feedId, FeedLocalAgencyId("my-agency")))
  }

  @Test
  fun `handle returns partial success when some agencies fail to save`() {
    val feedId = FeedId("f-abc-test")
    val agencies =
      listOf(
        GTFSAgency(
          agencyId = FeedLocalAgencyId("agency-1"),
          name = "Transit A",
          url = null,
          timezone = null,
          phone = null,
        ),
        GTFSAgency(
          agencyId = FeedLocalAgencyId("agency-2"),
          name = "Transit B",
          url = null,
          timezone = null,
          phone = null,
        ),
      )
    val bundle = GTFSDataBundle(feedId = feedId, agencies = agencies)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    var callCount = 0
    every { agencyRepository.save(any()) } answers
      {
        callCount++
        if (callCount == 2) {
          throw RuntimeException("Database error")
        }
        firstArg()
      }

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.PartialSuccess::class.java)
    val partialSuccess = result as ImportResult.PartialSuccess
    assertThat(partialSuccess.recordsProcessed).isEqualTo(1)
    assertThat(partialSuccess.errors).hasSize(1)
    assertThat(partialSuccess.errors.first().recordId).isEqualTo("agency-2")
    assertThat(partialSuccess.errors.first().message).contains("Database error")
  }
}

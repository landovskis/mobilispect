package com.mobilispect.backend.agency.batch.import

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.events.FeedImportStepCompleted
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class AgencyImportService(
  private val agencyRepository: AgencyRepository,
  private val eventPublisher: ApplicationEventPublisher,
) {
  private val logger = LoggerFactory.getLogger(AgencyImportService::class.java)

  fun execute(stepExecution: StepExecution, feedOnestopId: String) {
    val parsedData =
      stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    val feedId = FeedId(feedOnestopId)
    var created = 0
    var updated = 0

    parsedData.agencies.forEach { agency ->
      val entity =
        Agency(
          agencyId = AgencyId(FeedId(feedOnestopId), agency.agencyId),
          feedId = FeedId(feedOnestopId),
          name = agency.name,
        )
      val existing = agencyRepository.findById(entity.agencyId)
      if (existing == null) {
        agencyRepository.save(entity)
        created++
      } else {
        agencyRepository.save(existing.copy(name = agency.name, active = true))
        updated++
      }
    }

    logger.info(
      "Persisted agencies for feed {} (created={}, updated={})",
      feedOnestopId,
      created,
      updated,
    )

    eventPublisher.publishEvent(FeedImportStepCompleted(feedId, "agency"))
  }
}

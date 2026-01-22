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

  /**
   * Execute agency import from Spring Batch step execution context.
   *
   * Delegates to [processAgencies] after extracting parsed data from the context.
   */
  fun execute(stepExecution: StepExecution, feedOnestopId: String) {
    val parsedData =
      stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    processAgencies(FeedId(feedOnestopId), parsedData)
  }

  /**
   * Process agencies from parsed GTFS data.
   *
   * This method can be called directly for synchronous processing or from Spring Batch.
   *
   * @param feedId The feed being imported
   * @param parsedData Parsed GTFS data containing agencies
   * @return Map of feed-local agency ID to persisted Agency entity
   */
  fun processAgencies(feedId: FeedId, parsedData: GTFSData): Map<String, Agency> {
    val agenciesByFeedLocalId = mutableMapOf<String, Agency>()
    var created = 0
    var updated = 0

    parsedData.agencies.forEach { agency ->
      val entity =
        Agency(agencyId = AgencyId(feedId, agency.agencyId), feedId = feedId, name = agency.name)
      val existing = agencyRepository.findById(entity.agencyId)
      val saved =
        if (existing == null) {
          created++
          agencyRepository.save(entity)
        } else {
          updated++
          agencyRepository.save(existing.copy(name = agency.name, active = true))
        }
      agenciesByFeedLocalId[agency.agencyId.value] = saved
    }

    logger.info(
      "Persisted agencies for feed {} (created={}, updated={})",
      feedId.value,
      created,
      updated,
    )

    eventPublisher.publishEvent(FeedImportStepCompleted(feedId, "agency"))
    return agenciesByFeedLocalId
  }
}

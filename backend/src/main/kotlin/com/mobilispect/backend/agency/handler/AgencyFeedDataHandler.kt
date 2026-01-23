package com.mobilispect.backend.agency.handler

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.api.handler.FeedDataHandler
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.GTFSDataType
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportError
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Handler that processes agency data from GTFS feeds.
 *
 * This handler:
 * 1. Maps GTFS agencies to domain Agency entities
 * 2. Constructs agency IDs from feed ID and GTFS agency ID
 * 3. Persists agencies via the AgencyRepository
 *
 * Priority is set to 10 (highest) because agencies must be processed before routes, which reference
 * agencies.
 *
 * @param agencyRepository Repository for persisting agency entities
 */
@Component
class AgencyFeedDataHandler(private val agencyRepository: AgencyRepository) : FeedDataHandler {

  private val logger = LoggerFactory.getLogger(AgencyFeedDataHandler::class.java)

  override fun dataTypes(): Set<GTFSDataType> = setOf(GTFSDataType.AGENCY)

  override fun priority(): Int = 10

  override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult {
    val agencies = data.agencies
    if (agencies.isEmpty()) {
      logger.debug("No agencies to process for feed {}", feedId.value)
      return ImportResult.Success(0)
    }

    logger.info("Processing {} agencies for feed {}", agencies.size, feedId.value)

    var successCount = 0
    val errors = mutableListOf<ImportError>()

    agencies.forEach { gtfsAgency ->
      try {
        val agency =
          Agency(
            agencyId = AgencyId(feedId, gtfsAgency.agencyId),
            feedId = feedId,
            gtfsAgencyId = gtfsAgency.agencyId.value,
            name = gtfsAgency.name,
            website = gtfsAgency.url,
            phone = gtfsAgency.phone,
            lastFeedImport = context.startedAt,
            active = true,
          )

        agencyRepository.save(agency)
        successCount++

        logger.debug(
          "Saved agency {} ({}) for feed {}",
          gtfsAgency.name,
          gtfsAgency.agencyId.value,
          feedId.value,
        )
      } catch (e: Exception) {
        logger.error(
          "Failed to save agency {} for feed {}: {}",
          gtfsAgency.agencyId.value,
          feedId.value,
          e.message,
        )
        errors.add(
          ImportError(
            recordId = gtfsAgency.agencyId.value,
            message = e.message ?: "Unknown error",
            exception = e,
          )
        )
      }
    }

    return when {
      errors.isEmpty() -> {
        logger.info("Successfully processed {} agencies for feed {}", successCount, feedId.value)
        ImportResult.Success(successCount)
      }
      successCount > 0 -> {
        logger.warn(
          "Partially processed agencies for feed {}: {} succeeded, {} failed",
          feedId.value,
          successCount,
          errors.size,
        )
        ImportResult.PartialSuccess(successCount, errors)
      }
      else -> {
        logger.error("Failed to process any agencies for feed {}", feedId.value)
        ImportResult.Failure(errors.first())
      }
    }
  }
}

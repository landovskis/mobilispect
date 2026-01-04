package com.mobilispect.backend.agency.batch.import

import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.events.FeedImportStepCompleted
import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.AfterStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@StepScope
class AgencyWriter(
  private val agencyRepository: AgencyRepository,
  private val eventPublisher: ApplicationEventPublisher,
) : ItemWriter<Agency> {
  private val logger = LoggerFactory.getLogger(AgencyWriter::class.java)

  private var feedId: FeedId? = null

  @Value("#{jobParameters['feedOnestopId']}") lateinit var feedOnestopId: String

  @AfterStep
  fun afterStep(stepExecution: StepExecution) {
    feedId = FeedId(feedOnestopId)
    eventPublisher.publishEvent(FeedImportStepCompleted(feedId!!, "agency"))
  }

  override fun write(chunk: Chunk<out Agency>) {
    val feedId = FeedId(feedOnestopId)
    var created = 0
    var updated = 0

    chunk.items.forEach { agency ->
      val existing = agencyRepository.findById(agency.agencyId)
      if (existing == null) {
        agencyRepository.save(agency)
        created++
      } else {
        agencyRepository.save(
          existing.copy(
            name = agency.name,
            active = agency.active,
          )
        )
        updated++
      }
    }

    logger.info(
      "Persisted agencies for feed {} (created={}, updated={})",
      feedOnestopId,
      created,
      updated,
    )
  }
}

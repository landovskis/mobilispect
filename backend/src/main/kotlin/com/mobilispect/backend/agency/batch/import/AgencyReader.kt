package com.mobilispect.backend.agency.batch.import

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.gtfs.ParsedAgency
import com.mobilispect.backend.feed.gtfs.ParsedGtfsData
import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.stereotype.Component

@Component
@StepScope
class AgencyReader() : ItemReader<ParsedAgency> {
    private val logger = LoggerFactory.getLogger(AgencyReader::class.java)

    private var feedId: FeedId? = null
    private var agencyIterator: Iterator<ParsedAgency>? = null

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        feedId = FeedId(stepExecution.jobExecution.executionContext.get("feedId") as String)
        val parsedData = stepExecution.jobExecution.executionContext.get("parsedData") as? ParsedGtfsData
            ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

        agencyIterator = parsedData.agencies.iterator()
        logger.info("Initializing AgencyReader with {} agencies", parsedData.agencies.size)
    }

    override fun read(): ParsedAgency? {
        val iterator = agencyIterator ?: return null
        return if (iterator.hasNext()) iterator.next() else null
    }
}

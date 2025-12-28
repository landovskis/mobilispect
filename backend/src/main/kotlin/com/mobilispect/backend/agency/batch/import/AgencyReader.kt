package com.mobilispect.backend.agency.batch.import

import com.mobilispect.backend.feed.api.GTFSAgency
import com.mobilispect.backend.feed.api.GTFSData
import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.stereotype.Component

@Component
@StepScope
class AgencyReader() : ItemReader<GTFSAgency> {
    private val logger = LoggerFactory.getLogger(AgencyReader::class.java)

    private var agencyIterator: Iterator<GTFSAgency>? = null

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        val parsedData = stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
            ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

        agencyIterator = parsedData.agencies.iterator()
        logger.info("Initializing AgencyReader with {} agencies", parsedData.agencies.size)
    }

    override fun read(): GTFSAgency? {
        val iterator = agencyIterator ?: return null
        return if (iterator.hasNext()) iterator.next() else null
    }
}

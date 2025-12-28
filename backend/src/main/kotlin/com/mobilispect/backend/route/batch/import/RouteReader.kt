package com.mobilispect.backend.route.batch.import

import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.GTFSRoute
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemReader that reads parsed GTFS routes and produces RouteInput items.
 *
 * This reader:
 * 1. Retrieves ParsedGtfsData from the job execution context
 * 2. Gets feedOnestopId from job parameters
 * 3. For each route, creates a RouteInput combining:
 *    - ParsedRoute from GTFS data
 *    - Feed onestop ID for route ID generation
 * 4. Returns one RouteInput per route for processing
 *
 * The reader processes routes sequentially, yielding one RouteInput at a time.
 */
@Component
@StepScope
class RouteReader : ItemReader<RouteInput> {

    private val logger = LoggerFactory.getLogger(RouteReader::class.java)

    @Value("#{jobParameters['feedOnestopId']}")
    private lateinit var feedOnestopId: String

    private var parsedData: GTFSData? = null
    private var routeIterator: Iterator<GTFSRoute>? = null

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        // Retrieve parsed data from job execution context
        parsedData = stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
            ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

        val data = parsedData!!

        logger.info(
            "Initializing RouteReader for feed {} with {} routes",
            feedOnestopId,
            data.routes.size
        )

        routeIterator = data.routes.iterator()
    }

    override fun read(): RouteInput? {
        if (routeIterator == null || !routeIterator!!.hasNext()) {
            return null
        }

        val parsedRoute = routeIterator!!.next()

        return RouteInput(
            parsedRoute = parsedRoute,
            feedOnestopId = feedOnestopId
        )
    }
}

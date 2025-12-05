package com.mobilispect.backend.transitanalysis.infrastructure.gtfs

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.nio.file.Path

/**
 * Temporary stub GTFS parser implementation.
 *
 * TODO: Fix OneBusAwayGtfsParser compilation issues and remove this stub.
 *
 * This stub allows the application to compile while the OneBusAwayGtfsParser
 * type inference issues are resolved.
 */
@Component
@Primary
class StubGtfsParser : GtfsParser {
    private val logger = LoggerFactory.getLogger(StubGtfsParser::class.java)

    override fun parse(feedPath: Path): Result<ParsedGtfsData> = runCatching {
        logger.warn("Using stub GTFS parser - no actual parsing will occur for: {}", feedPath)
        logger.warn("TODO: Fix OneBusAwayGtfsParser and remove StubGtfsParser")

        ParsedGtfsData(
            routes = emptyList(),
            trips = emptyList()
        )
    }
}

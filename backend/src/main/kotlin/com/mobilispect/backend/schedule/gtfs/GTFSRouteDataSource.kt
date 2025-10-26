package com.mobilispect.backend.schedule.gtfs

import com.mobilispect.backend.AgencyIDDataSource
import com.mobilispect.backend.schedule.ImportScheduledFeedsService
import com.mobilispect.backend.schedule.Route
import com.mobilispect.backend.schedule.route.RouteDataSource
import com.mobilispect.backend.schedule.route.RouteIDDataSource
import kotlin.time.measureTime
import com.mobilispect.backend.util.readTextAndNormalize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.csv.Csv
import kotlinx.serialization.decodeFromString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path

/**
 * A [RouteDataSource] that uses a GTFS feed as source.
 */
@OptIn(ExperimentalSerializationApi::class)
@Suppress("ReturnCount")
class GTFSRouteDataSource(
    private val agencyIDDataSource: AgencyIDDataSource,
    private val routeIDDataSource: RouteIDDataSource,
) : RouteDataSource {
    private val logger: Logger = LoggerFactory.getLogger(ImportScheduledFeedsService::class.java)

    override fun routes(root: Path, version: String, feedID: String): Result<Collection<Route>> {
        return try {
            val input = root.resolve("routes.txt").toFile().readTextAndNormalize()
            val csv = Csv {
                hasHeaderRecord = true
                ignoreUnknownColumns = true
            }

            val agencyIDRes = agencyIDDataSource.agencyIDs(feedID)
            if (agencyIDRes.isFailure) {
                logger.error("Error loading agency IDs: {}", agencyIDRes.exceptionOrNull()?.message)
                return Result.failure(agencyIDRes.exceptionOrNull()!!)
            }
            val agencyIDs = agencyIDRes.getOrNull()!!

            val routeIDRes = routeIDDataSource.routeIDs(feedID)
            if (routeIDRes.isFailure) {
                logger.error("Error loading route IDs: {}", routeIDRes.exceptionOrNull()?.message)
                return Result.failure(routeIDRes.exceptionOrNull()!!)
            }
            val routeIDs = routeIDRes.getOrNull()!!

            val gtfsRoutes: Collection<GTFSRoute>
            val decodingTime = measureTime {
                gtfsRoutes = csv.decodeFromString<Collection<GTFSRoute>>(input)
            }
            logger.debug("Decoded {} routes in {}", gtfsRoutes.size, decodingTime)

            Result.success(gtfsRoutes.mapNotNull { route ->
                val feedLocalAgencyID = route.agency_id
                val onestopAgencyID = agencyIDs[feedLocalAgencyID] ?: return@mapNotNull null
                val routeID = routeIDs[route.route_id] ?: return@mapNotNull null
                Route(
                    uid = routeID,
                    localID = route.route_id,
                    shortName = route.route_short_name,
                    longName = route.route_long_name,
                    agencyID = onestopAgencyID,
                    versions = listOf(version)
                )
            })
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SerializationException) {
            Result.failure(e)
        }
    }
}

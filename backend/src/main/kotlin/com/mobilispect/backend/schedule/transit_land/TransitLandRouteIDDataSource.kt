package com.mobilispect.backend.schedule.transit_land

import com.mobilispect.backend.infastructure.transit_land.RouteResultItem
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandEntityType
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingEntity
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingRepository
import com.mobilispect.backend.schedule.route.RouteIDDataSource
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import com.mobilispect.backend.transit_land.PagingParameters
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException

/** A [RouteIDDataSource] uses transit land for route IDs. */
internal class TransitLandRouteIDDataSource(
  private val transitLandAPI: TransitLandAPI,
  private val transitLandCredentialsRepository: TransitLandCredentialsRepository,
  private val mappingRepository: TransitLandOnestopIdMappingRepository,
  private val sleepMillisProvider: () -> Long = { 2000L + (Math.random() * 2000).toLong() },
  private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : RouteIDDataSource {
  private val logger = LoggerFactory.getLogger(TransitLandRouteIDDataSource::class.java)

  override fun routeIDs(feedID: String): Result<Map<String, String>> {
    val cachedMappings =
      mappingRepository.findAllByFeedOnestopIdAndEntityType(feedID, TransitLandEntityType.ROUTE)
    if (cachedMappings.isNotEmpty()) {
      return Result.success(cachedMappings.associate { it.gtfsId to it.onestopId })
    }

    return findRouteIDs(feedID).map { routes ->
      val mappings =
        routes.map { item ->
          TransitLandOnestopIdMappingEntity(
            feedOnestopId = feedID,
            entityType = TransitLandEntityType.ROUTE,
            gtfsId = item.routeID,
            onestopId = item.id,
          )
        }
      if (mappings.isNotEmpty()) {
        try {
          mappingRepository.saveAll(mappings)
        } catch (e: DataIntegrityViolationException) {
          logger.warn("Route onestop ID mapping already exists for feed {}", feedID)
        }
      }
      mappings.associate { it.gtfsId to it.onestopId }
    }
  }

  @Suppress("ReturnCount")
  private fun findRouteIDs(feedID: String): Result<Collection<RouteResultItem>> {
    val apiKey =
      transitLandCredentialsRepository.get() ?: return Result.failure(Exception("Missing API key"))
    val allRoutes = mutableListOf<RouteResultItem>()
    var after: Int? = null
    var lastRoutes: Collection<RouteResultItem> = emptyList()
    do {
      val routesRes =
        transitLandAPI.routes(
          apiKey = apiKey,
          feedID = feedID,
          paging = PagingParameters(limit = 100, after = after),
        )
      if (routesRes.isFailure) {
        return Result.failure(routesRes.exceptionOrNull()!!)
      }

      val routeResult = routesRes.getOrNull()!!
      after = routeResult.after
      lastRoutes = routeResult.routes
      allRoutes += lastRoutes
      sleep(sleepMillisProvider())
    } while (lastRoutes.isNotEmpty() && after != null)
    return Result.success(allRoutes)
  }
}

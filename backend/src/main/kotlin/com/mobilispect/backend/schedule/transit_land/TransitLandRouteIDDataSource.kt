package com.mobilispect.backend.schedule.transit_land

import com.mobilispect.backend.infastructure.transit_land.RouteResultItem
import com.mobilispect.backend.schedule.route.RouteIDDataSource
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import com.mobilispect.backend.transit_land.PagingParameters

/** A [RouteIDDataSource] uses transit land for route IDs. */
internal class TransitLandRouteIDDataSource(
  private val transitLandAPI: TransitLandAPI,
  private val transitLandCredentialsRepository: TransitLandCredentialsRepository,
) : RouteIDDataSource {
  override fun routeIDs(feedID: String): Result<Map<String, String>> {
    return findRouteIDs(feedID).map { routes ->
      routes.fold(mutableMapOf()) { acc, item ->
        acc[item.routeID] = item.id
        acc
      }
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
      Thread.sleep(2000 + (Math.random() * 2000).toLong())
    } while (lastRoutes.isNotEmpty() && after != null)
    return Result.success(allRoutes)
  }
}

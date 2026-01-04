package com.mobilispect.backend.agency.application

import com.mobilispect.backend.agency.api.dto.AgencyDTO
import com.mobilispect.backend.agency.api.dto.AgencySummaryDTO
import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.config.RedisConfiguration
import com.mobilispect.backend.feed.api.FeedQueryApi
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.repository.RouteRepository
import kotlin.math.min
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * Query service for agency-related operations with Redis caching (T096).
 *
 * All query methods are cached with 24-hour TTL to improve performance. Cache is automatically
 * invalidated when feed imports complete.
 */
@Service
class AgencyQueryService(
  private val agencyRepository: AgencyRepository,
  private val routeRepository: RouteRepository,
  private val feedQueryApi: FeedQueryApi,
) {
  /** Get all agencies with pagination. Cached with 24-hour TTL (T096). */
  @Cacheable(
    value = [RedisConfiguration.AGENCY_CACHE],
    key = "'all_' + #pageable.pageNumber + '_' + #pageable.pageSize",
  )
  fun getAgencies(pageable: Pageable): Page<AgencyDTO> {
    val agencies = agencyRepository.findAll()
    val mapped = agencies.map { mapAgency(it) }
    return paginate(mapped, pageable)
  }

  /**
   * Get agencies for a specific region with pagination, sorted by route count. Cached with 24-hour
   * TTL (T096).
   */
  @Cacheable(
    value = [RedisConfiguration.AGENCY_CACHE],
    key = "'region_' + #regionId.toString() + '_' + #pageable.pageNumber + '_' + #pageable.pageSize",
  )
  fun getAgenciesByRegion(regionId: RegionId, pageable: Pageable): Page<AgencyDTO> {
    val feeds = feedQueryApi.findFeedsByRegion(regionId)
    val agencies =
      feeds
        .flatMap { feed -> agencyRepository.findByFeedId(feed.feedId, Pageable.unpaged()).content }
        .distinctBy { it.agencyOnestopId }
    val sorted =
      agencies.sortedByDescending { agency ->
        routeRepository.countByAgencyId(agency.agencyOnestopId)
      }
    val mapped = sorted.map { mapAgency(it) }
    return paginate(mapped, pageable)
  }

  /** Get detailed summary for a specific agency. Cached with 24-hour TTL (T096). */
  @Cacheable(value = [RedisConfiguration.AGENCY_CACHE], key = "'summary_' + #agencyId.toString()")
  fun getAgencySummary(agencyId: AgencyId): AgencySummaryDTO? {
    val agency = agencyRepository.findById(agencyId) ?: return null
    val routeCount = routeRepository.countByAgencyId(agency.agencyOnestopId)
    return AgencySummaryDTO(
      id = agency.agencyOnestopId.value,
      name = agency.name,
      routeCount = routeCount.toInt(),
      averageHeadwayMinutes = null,
      minHeadwayMinutes = null,
      maxHeadwayMinutes = null,
    )
  }

  private fun mapAgency(agency: com.mobilispect.backend.agency.domain.model.Agency): AgencyDTO {
    val routes = routeRepository.findByAgencyId(agency.agencyOnestopId, Pageable.unpaged()).content
    val routesByType = routes.groupingBy { it.routeType }.eachCount()
    val feed = feedQueryApi.findFeedById(agency.feedId)
    val regionIds = feed?.regionIds?.map { it.value }?.toSet() ?: emptySet()
    return AgencyDTO(
      id = agency.agencyOnestopId.value,
      name = agency.name,
      feedOnestopId = agency.feedId.value,
      regionIds = regionIds,
      routeCount = routes.size,
      activeRouteCount = routes.count { it.active },
      routesByType = RouteType.values().associateWith { routesByType[it] ?: 0 },
    )
  }

  private fun <T : Any> paginate(items: List<T>, pageable: Pageable): Page<T> {
    if (!pageable.isPaged) {
      return PageImpl(items, pageable, items.size.toLong())
    }
    val start = pageable.offset.toInt()
    if (start >= items.size) {
      return PageImpl(emptyList<T>(), pageable, items.size.toLong())
    }
    val end = min(start + pageable.pageSize, items.size)
    return PageImpl(items.subList(start, end), pageable, items.size.toLong())
  }
}

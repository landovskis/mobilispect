package com.mobilispect.backend.agency.application

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.api.dto.AgencyDTO
import com.mobilispect.backend.agency.api.dto.AgencySummaryDTO
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.repository.RouteRepository
import kotlin.math.min
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/** Query service for agency-related operations. */
@Service
class AgencyQueryService(
  private val agencyRepository: AgencyRepository,
  private val routeRepository: RouteRepository,
  private val feedQueryApi: FeedApi,
) {
  /** Get all agencies with pagination. */
  fun getAgencies(pageable: Pageable): Page<AgencyDTO> {
    val agencies = agencyRepository.findAll()
    val mapped = agencies.map { mapAgency(it) }
    return paginate(mapped, pageable)
  }

  /** Get agencies for a specific region with pagination, sorted by route count. */
  fun getAgenciesByRegion(regionId: RegionId, pageable: Pageable): Page<AgencyDTO> {
    val feeds = feedQueryApi.findFeedsByRegion(regionId)
    val agencies =
      feeds
        .flatMap { feed -> agencyRepository.findByFeedId(feed.feedId, Pageable.unpaged()).content }
        .distinctBy { it.agencyId }
    val sorted =
      agencies.sortedByDescending { agency -> routeRepository.countByAgencyId(agency.agencyId) }
    val mapped = sorted.map { mapAgency(it) }
    return paginate(mapped, pageable)
  }

  /** Get detailed summary for a specific agency. */
  fun getAgencySummary(agencyId: AgencyId): AgencySummaryDTO? {
    val agency = agencyRepository.findById(agencyId) ?: return null
    val routeCount = routeRepository.countByAgencyId(agency.agencyId)
    return AgencySummaryDTO(
      id = agency.agencyId.value,
      name = agency.name,
      routeCount = routeCount.toInt(),
      averageHeadwayMinutes = null,
      minHeadwayMinutes = null,
      maxHeadwayMinutes = null,
    )
  }

  private fun mapAgency(agency: com.mobilispect.backend.agency.domain.model.Agency): AgencyDTO {
    val routes = routeRepository.findByAgencyId(agency.agencyId, Pageable.unpaged()).content
    val routesByType = routes.groupingBy { it.routeType }.eachCount()
    val feed = feedQueryApi.findFeedById(agency.feedId)
    val regionIds = feed?.regionIds?.map { it.value }?.toSet() ?: emptySet()
    return AgencyDTO(
      id = agency.agencyId.value,
      name = agency.name,
      feedOnestopId = agency.feedId.value,
      regionIds = regionIds,
      routeCount = routes.size,
      activeRouteCount = routes.count { it.active },
      routesByType = RouteType.values().associateWith { routesByType[it] ?: 0 },
    )
  }

  /** Helper function to convert a list into a paginated result. */
  private fun <T : Any> paginate(items: List<T>, pageable: Pageable): Page<T> {
    val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(items.size)
    val end = min(start + pageable.pageSize, items.size)
    val pageContent = if (start < items.size) items.subList(start, end) else emptyList()
    return PageImpl(pageContent, pageable, items.size.toLong())
  }
}

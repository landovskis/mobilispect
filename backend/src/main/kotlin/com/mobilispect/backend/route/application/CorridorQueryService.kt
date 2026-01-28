package com.mobilispect.backend.route.application

import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.route.api.dto.CorridorDTO
import com.mobilispect.backend.route.api.dto.CorridorRouteDTO
import com.mobilispect.backend.route.domain.repository.CommonSectionVariantRepository
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * Query service for corridor detection across a region.
 *
 * A corridor is defined as a common section served by two or more distinct routes. This service
 * aggregates data across feeds, agencies, routes, and variants to identify corridors within a
 * metropolitan region.
 */
@Service
class CorridorQueryService(
  private val feedApi: FeedApi,
  private val agencyRepository: AgencyRepository,
  private val routeRepository: RouteRepository,
  private val routeVariantRepository: RouteVariantRepository,
  private val commonSectionVariantRepository: CommonSectionVariantRepository,
) {

  /**
   * Returns all corridors for a region, sorted by number of contributing routes (descending).
   *
   * A corridor is a common section where two or more distinct routes overlap.
   */
  fun getCorridorsForRegion(regionId: RegionId): List<CorridorDTO> {
    val feeds = feedApi.findFeedsByRegion(regionId)
    if (feeds.isEmpty()) return emptyList()

    val agencies =
      feeds
        .flatMap { feed -> agencyRepository.findByFeedId(feed.feedId, Pageable.unpaged()).content }
        .distinctBy { it.agencyId }

    val routes =
      agencies.flatMap { agency ->
        routeRepository.findByAgencyId(agency.agencyId, Pageable.unpaged()).content
      }
    if (routes.isEmpty()) return emptyList()

    val routeById = routes.associateBy { it.id }

    val variants = routes.flatMap { route -> routeVariantRepository.findByRouteId(route.id) }
    if (variants.isEmpty()) return emptyList()

    val variantById = variants.associateBy { it.id.value }

    val allSectionVariants =
      variants.flatMap { variant ->
        commonSectionVariantRepository.findByVariantId(variant.id.value)
      }
    if (allSectionVariants.isEmpty()) return emptyList()

    val sectionGroups = allSectionVariants.groupBy { it.commonSection.id }

    return sectionGroups
      .mapNotNull { (_, sectionVariants) ->
        val section = sectionVariants.first().commonSection

        val distinctRoutes =
          sectionVariants
            .mapNotNull { csv -> variantById[csv.variantId]?.routeId }
            .distinct()
            .mapNotNull { routeId -> routeById[routeId] }

        if (distinctRoutes.size < 2) return@mapNotNull null

        CorridorDTO(
          id = section.id.toString(),
          stopPattern = section.stopPattern,
          stopCount = section.stopCount,
          firstStopId = section.firstStopId,
          lastStopId = section.lastStopId,
          routes =
            distinctRoutes.map { route ->
              CorridorRouteDTO(
                routeId = route.id.value,
                shortName = route.shortName,
                longName = route.longName,
              )
            },
        )
      }
      .sortedByDescending { it.routes.size }
  }
}

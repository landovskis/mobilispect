package com.mobilispect.backend.route.data.repository

import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.data.mapper.RouteCommonSectionMapper
import com.mobilispect.backend.route.domain.model.RouteCommonSection
import com.mobilispect.backend.route.domain.repository.RouteCommonSectionRepository
import org.springframework.stereotype.Repository

/**
 * Implementation of [RouteCommonSectionRepository] using JPA repositories and mappers.
 *
 * Converts between domain models and data entities.
 */
@Repository
class RouteCommonSectionRepositoryImpl(
  private val jpaRepository: RouteCommonSectionJpaRepository,
  private val routeJpaRepository: RouteJpaRepository,
  private val mapper: RouteCommonSectionMapper,
) : RouteCommonSectionRepository {

  override fun findById(id: String): RouteCommonSection? =
    jpaRepository.findById(id).map { mapper.toDomain(it) }.orElse(null)

  override fun findByRouteIdAndDirectionId(
    routeId: RouteId,
    directionId: Int?,
  ): RouteCommonSection? {
    val routeEntity =
      routeJpaRepository.findById(routeId.value).orElseThrow {
        IllegalArgumentException("Route not found: $routeId")
      }
    return jpaRepository.findByRouteAndDirectionId(routeEntity, directionId)?.let {
      mapper.toDomain(it)
    }
  }

  override fun findByRouteId(routeId: RouteId): List<RouteCommonSection> {
    val routeEntity =
      routeJpaRepository.findById(routeId.value).orElseThrow {
        IllegalArgumentException("Route not found: $routeId")
      }
    return jpaRepository.findByRoute(routeEntity).map { mapper.toDomain(it) }
  }

  override fun save(section: RouteCommonSection): RouteCommonSection {
    val routeEntity =
      routeJpaRepository.findById(section.routeId.value).orElseThrow {
        IllegalArgumentException("Route not found: ${section.routeId}")
      }
    val entity = mapper.toEntity(section, routeEntity)
    return mapper.toDomain(jpaRepository.save(entity))
  }

  override fun deleteById(id: String) {
    jpaRepository.deleteById(id)
  }

  override fun deleteByRouteId(routeId: RouteId) {
    val routeEntity =
      routeJpaRepository.findById(routeId.value).orElseThrow {
        IllegalArgumentException("Route not found: $routeId")
      }
    jpaRepository.deleteByRoute(routeEntity)
  }
}

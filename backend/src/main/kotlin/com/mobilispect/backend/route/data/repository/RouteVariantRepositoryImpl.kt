package com.mobilispect.backend.route.data.repository

import com.mobilispect.backend.route.data.mapper.RouteVariantMapper
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import java.time.Instant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/**
 * Implementation of [RouteVariantRepository] using JPA repositories and mappers.
 *
 * Converts between domain models (with @JvmInline value class IDs) and data entities (with plain
 * String IDs).
 */
@Repository
class RouteVariantRepositoryImpl(
  private val jpaRepository: RouteVariantJpaRepository,
  private val routeJpaRepository: RouteJpaRepository,
  private val mapper: RouteVariantMapper,
) : RouteVariantRepository {

  override fun findById(id: VariantHash): RouteVariant? =
    jpaRepository.findById(id.value).map { mapper.toDomain(it) }.orElse(null)

  override fun findByRouteId(routeId: RouteId): List<RouteVariant> =
    jpaRepository.findByRouteId(routeId.value).map { mapper.toDomain(it) }

  override fun findByRouteId(routeId: RouteId, pageable: Pageable): Page<RouteVariant> {
    val routeEntity =
      routeJpaRepository.findById(routeId.value).orElseThrow {
        IllegalArgumentException("Route not found: $routeId")
      }
    return jpaRepository.findByRoute(routeEntity, pageable).map { mapper.toDomain(it) }
  }

  override fun findByRouteIdAndActive(routeId: RouteId, pageable: Pageable): Page<RouteVariant> {
    val routeEntity =
      routeJpaRepository.findById(routeId.value).orElseThrow {
        IllegalArgumentException("Route not found: $routeId")
      }
    return jpaRepository.findByRouteAndActive(routeEntity, pageable).map { mapper.toDomain(it) }
  }

  override fun findByRouteIdAndDirectionId(
    routeId: RouteId,
    directionId: Int,
    pageable: Pageable,
  ): Page<RouteVariant> {
    val routeEntity =
      routeJpaRepository.findById(routeId.value).orElseThrow {
        IllegalArgumentException("Route not found: $routeId")
      }
    return jpaRepository.findByRouteAndDirectionId(routeEntity, directionId, pageable).map {
      mapper.toDomain(it)
    }
  }

  override fun findByRouteIdAndDirectionIdAndActive(
    routeId: RouteId,
    directionId: Int,
    pageable: Pageable,
  ): Page<RouteVariant> {
    val routeEntity =
      routeJpaRepository.findById(routeId.value).orElseThrow {
        IllegalArgumentException("Route not found: $routeId")
      }
    return jpaRepository
      .findByRouteAndDirectionIdAndActive(routeEntity, directionId, pageable)
      .map { mapper.toDomain(it) }
  }

  override fun findByStopCount(stopCount: Int, pageable: Pageable): Page<RouteVariant> =
    jpaRepository.findByStopCount(stopCount, pageable).map { mapper.toDomain(it) }

  override fun findByActive(active: Boolean, pageable: Pageable): Page<RouteVariant> =
    jpaRepository.findByActive(active, pageable).map { mapper.toDomain(it) }

  override fun findByFirstSeenBetween(
    after: Instant,
    before: Instant,
    pageable: Pageable,
  ): Page<RouteVariant> =
    jpaRepository.findByFirstSeenBetween(after, before, pageable).map { mapper.toDomain(it) }

  override fun findByLastSeenAfter(since: Instant, pageable: Pageable): Page<RouteVariant> =
    jpaRepository.findByLastSeenAfter(since, pageable).map { mapper.toDomain(it) }

  override fun findByFirstStopIdAndLastStopId(
    firstStopId: String,
    lastStopId: String,
    pageable: Pageable,
  ): Page<RouteVariant> =
    jpaRepository.findByFirstStopIdAndLastStopId(firstStopId, lastStopId, pageable).map {
      mapper.toDomain(it)
    }

  override fun countByRouteId(routeId: RouteId): Long {
    val routeEntity = routeJpaRepository.findById(routeId.value).orElse(null) ?: return 0
    return jpaRepository.countByRoute(routeEntity)
  }

  override fun countActiveByRouteId(routeId: RouteId): Long {
    val routeEntity = routeJpaRepository.findById(routeId.value).orElse(null) ?: return 0
    return jpaRepository.countActiveByRoute(routeEntity)
  }

  override fun countByRouteIdAndDirectionId(routeId: RouteId, directionId: Int): Long {
    val routeEntity = routeJpaRepository.findById(routeId.value).orElse(null) ?: return 0
    return jpaRepository.countByRouteAndDirectionId(routeEntity, directionId)
  }

  override fun existsByRouteIdAndFirstStopIdAndLastStopId(
    routeId: RouteId,
    firstStopId: String,
    lastStopId: String,
  ): Boolean =
    jpaRepository.existsByRouteIdAndFirstStopIdAndLastStopId(routeId.value, firstStopId, lastStopId)

  override fun save(variant: RouteVariant): RouteVariant {
    val routeEntity =
      routeJpaRepository.findById(variant.routeId.value).orElseThrow {
        IllegalArgumentException("Route not found: ${variant.routeId}")
      }
    val entity = mapper.toEntity(variant, routeEntity)
    val saved = jpaRepository.save(entity)
    return mapper.toDomain(saved)
  }

  override fun deleteById(id: VariantHash) {
    jpaRepository.deleteById(id.value)
  }

  override fun findAll(): List<RouteVariant> = jpaRepository.findAll().map { mapper.toDomain(it) }
}

package com.mobilispect.backend.transitanalysis.data.repository

import com.mobilispect.backend.agency.data.repository.AgencyJpaRepository
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.transitanalysis.data.mapper.RouteMapper
import com.mobilispect.backend.transitanalysis.domain.model.Route
import com.mobilispect.backend.transitanalysis.domain.model.RouteType
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import com.mobilispect.backend.transitanalysis.domain.repository.RouteRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Implementation of [RouteRepository] using JPA repositories and mappers.
 *
 * Converts between domain models (with @JvmInline value class IDs) and data entities (with plain String IDs).
 */
@Repository
class RouteRepositoryImpl(
    private val jpaRepository: RouteJpaRepository,
    private val agencyJpaRepository: AgencyJpaRepository,
    private val mapper: RouteMapper
) : RouteRepository {

    override fun findById(routeId: RouteId): Route? =
        jpaRepository.findById(routeId.value)
            .map { mapper.toDomain(it) }
            .orElse(null)

    override fun findByAgencyId(agencyId: AgencyId, pageable: Pageable): Page<Route> {
        val agencyEntity = agencyJpaRepository.findById(agencyId.value)
            .orElseThrow { IllegalArgumentException("Agency not found: $agencyId") }
        return jpaRepository.findByAgency(agencyEntity, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByAgencyIdAndActive(agencyId: AgencyId, pageable: Pageable): Page<Route> {
        val agencyEntity = agencyJpaRepository.findById(agencyId.value)
            .orElseThrow { IllegalArgumentException("Agency not found: $agencyId") }
        return jpaRepository.findByAgencyAndActive(agencyEntity, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByAgencyIdAndRouteType(agencyId: AgencyId, routeType: RouteType, pageable: Pageable): Page<Route> {
        val agencyEntity = agencyJpaRepository.findById(agencyId.value)
            .orElseThrow { IllegalArgumentException("Agency not found: $agencyId") }
        return jpaRepository.findByAgencyAndRouteType(agencyEntity, routeType.value, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByAgencyIdAndRouteTypeAndActive(agencyId: AgencyId, routeType: RouteType, pageable: Pageable): Page<Route> {
        val agencyEntity = agencyJpaRepository.findById(agencyId.value)
            .orElseThrow { IllegalArgumentException("Agency not found: $agencyId") }
        return jpaRepository.findByAgencyAndRouteTypeAndActive(agencyEntity, routeType.value, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByAgencyIdAndGtfsRouteId(agencyId: AgencyId, gtfsRouteId: String): Route? =
        jpaRepository.findByAgencyIdAndGtfsRouteId(agencyId.value, gtfsRouteId)
            ?.let { mapper.toDomain(it) }

    override fun findByActive(active: Boolean, pageable: Pageable): Page<Route> =
        jpaRepository.findByActive(active, pageable)
            .map { mapper.toDomain(it) }

    override fun findByRouteType(routeType: RouteType, pageable: Pageable): Page<Route> =
        jpaRepository.findByRouteType(routeType.value, pageable)
            .map { mapper.toDomain(it) }

    override fun findByUpdatedAtAfter(since: Instant, pageable: Pageable): Page<Route> =
        jpaRepository.findByUpdatedAtAfter(since, pageable)
            .map { mapper.toDomain(it) }

    override fun countByAgencyId(agencyId: AgencyId): Long {
        val agencyEntity = agencyJpaRepository.findById(agencyId.value)
            .orElse(null) ?: return 0
        return jpaRepository.countByAgency(agencyEntity)
    }

    override fun countActiveByAgencyId(agencyId: AgencyId): Long {
        val agencyEntity = agencyJpaRepository.findById(agencyId.value)
            .orElse(null) ?: return 0
        return jpaRepository.countActiveByAgency(agencyEntity)
    }

    override fun countByAgencyIdAndRouteType(agencyId: AgencyId, routeType: RouteType): Long {
        val agencyEntity = agencyJpaRepository.findById(agencyId.value)
            .orElse(null) ?: return 0
        return jpaRepository.countByAgencyAndRouteType(agencyEntity, routeType.value)
    }

    override fun existsByAgencyIdAndGtfsRouteId(agencyId: AgencyId, gtfsRouteId: String): Boolean =
        jpaRepository.existsByAgencyIdAndGtfsRouteId(agencyId.value, gtfsRouteId)

    override fun save(route: Route): Route {
        val agencyEntity = agencyJpaRepository.findById(route.agencyId.value)
            .orElseThrow { IllegalArgumentException("Agency not found: ${route.agencyId}") }
        val entity = mapper.toEntity(route, agencyEntity)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun deleteById(routeId: RouteId) {
        jpaRepository.deleteById(routeId.value)
    }

    override fun findAll(): List<Route> =
        jpaRepository.findAll().map { mapper.toDomain(it) }
}

package com.mobilispect.backend.route.data.repository

import com.mobilispect.backend.agency.api.AgencyQueryApi
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.route.data.mapper.RouteMapper
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.repository.RouteRepository
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
    private val agencyQueryApi: AgencyQueryApi,
    private val mapper: RouteMapper
) : RouteRepository {

    override fun findById(routeId: RouteId): Route? =
        jpaRepository.findById(routeId.value)
            .map { mapper.toDomain(it) }
            .orElse(null)

    override fun findByAgencyId(agencyId: AgencyId, pageable: Pageable): Page<Route> {
        // Validate agency exists via API
        agencyQueryApi.findAgencyById(agencyId)
            ?: throw IllegalArgumentException("Agency not found: $agencyId")
        return jpaRepository.findByAgencyId(agencyId.value, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByAgencyIdAndActive(agencyId: AgencyId, pageable: Pageable): Page<Route> {
        // Validate agency exists via API
        agencyQueryApi.findAgencyById(agencyId)
            ?: throw IllegalArgumentException("Agency not found: $agencyId")
        return jpaRepository.findByAgencyIdAndActive(agencyId.value, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByAgencyIdAndRouteType(agencyId: AgencyId, routeType: RouteType, pageable: Pageable): Page<Route> {
        // Validate agency exists via API
        agencyQueryApi.findAgencyById(agencyId)
            ?: throw IllegalArgumentException("Agency not found: $agencyId")
        return jpaRepository.findByAgencyIdAndRouteType(agencyId.value, routeType.value, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByAgencyIdAndRouteTypeAndActive(agencyId: AgencyId, routeType: RouteType, pageable: Pageable): Page<Route> {
        // Validate agency exists via API
        agencyQueryApi.findAgencyById(agencyId)
            ?: throw IllegalArgumentException("Agency not found: $agencyId")
        return jpaRepository.findByAgencyIdAndRouteTypeAndActive(agencyId.value, routeType.value, pageable)
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
        // Check if agency exists via API
        agencyQueryApi.findAgencyById(agencyId) ?: return 0
        return jpaRepository.countByAgencyId(agencyId.value)
    }

    override fun countActiveByAgencyId(agencyId: AgencyId): Long {
        // Check if agency exists via API
        agencyQueryApi.findAgencyById(agencyId) ?: return 0
        return jpaRepository.countActiveByAgencyId(agencyId.value)
    }

    override fun countByAgencyIdAndRouteType(agencyId: AgencyId, routeType: RouteType): Long {
        // Check if agency exists via API
        agencyQueryApi.findAgencyById(agencyId) ?: return 0
        return jpaRepository.countByAgencyIdAndRouteType(agencyId.value, routeType.value)
    }

    override fun existsByAgencyIdAndGtfsRouteId(agencyId: AgencyId, gtfsRouteId: String): Boolean =
        jpaRepository.existsByAgencyIdAndGtfsRouteId(agencyId.value, gtfsRouteId)

    override fun save(route: Route): Route {
        // Validate agency exists via API
        agencyQueryApi.findAgencyById(route.agencyId)
            ?: throw IllegalArgumentException("Agency not found: ${route.agencyId}")
        val entity = mapper.toEntity(route)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun deleteById(routeId: RouteId) {
        jpaRepository.deleteById(routeId.value)
    }

    override fun findAll(): List<Route> =
        jpaRepository.findAll().map { mapper.toDomain(it) }
}

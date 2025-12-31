package com.mobilispect.backend.route.domain.repository

import com.mobilispect.backend.route.domain.model.RouteHourlyStat
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/** Repository for [RouteHourlyStat] entities in the transit analysis module. */
@Repository
interface RouteHourlyStatRepository : JpaRepository<RouteHourlyStat, UUID> {

  fun findByRouteIdAndServiceDateOrderByDayTypeAscDirectionIdAscHourOfDayAsc(
    routeId: String,
    serviceDate: LocalDate,
  ): List<RouteHourlyStat>

  @Query("SELECT MAX(s.serviceDate) FROM RouteHourlyStat s WHERE s.routeId = :routeId")
  fun findLatestServiceDate(@Param("routeId") routeId: String): LocalDate?

  @Transactional
  @Modifying
  fun deleteByRouteIdAndServiceDate(routeId: String, serviceDate: LocalDate): Int
}

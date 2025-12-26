package com.mobilispect.backend.route.api

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.route.application.RouteQueryService
import com.mobilispect.backend.route.api.dto.RouteDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class RouteController(
    private val routeQueryService: RouteQueryService
) {

    @GetMapping("/agencies/{agencyId}/routes")
    fun getRoutesByAgency(
        @PathVariable agencyId: String,
        pageable: Pageable
    ): Page<RouteDTO> =
        routeQueryService.getRoutesByAgency(AgencyId(agencyId), pageable)
}

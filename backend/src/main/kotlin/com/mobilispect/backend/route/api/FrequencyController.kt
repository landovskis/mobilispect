package com.mobilispect.backend.route.api

import com.mobilispect.backend.route.api.dto.RouteDTO
import com.mobilispect.backend.route.api.dto.RouteVariantDTO
import com.mobilispect.backend.route.application.FrequencyQueryService
import com.mobilispect.backend.route.domain.model.ids.RouteId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/routes")
class FrequencyController(private val frequencyQueryService: FrequencyQueryService) {
  @GetMapping("/{routeId}")
  fun getRoute(@PathVariable routeId: String): RouteDTO? =
    frequencyQueryService.getRoute(RouteId(routeId))

  @GetMapping("/{routeId}/variants")
  fun getVariants(@PathVariable routeId: String): List<RouteVariantDTO> =
    frequencyQueryService.getVariantsByRoute(RouteId(routeId))
}

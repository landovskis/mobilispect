package com.mobilispect.backend.route.api

import com.mobilispect.backend.route.api.dto.FrequencyDTO
import com.mobilispect.backend.route.api.dto.RouteDTO
import com.mobilispect.backend.route.api.dto.RouteVariantDTO
import com.mobilispect.backend.route.application.FrequencyQueryService
import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import java.time.LocalDate
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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

  @GetMapping("/variants/{variantId}/frequencies")
  fun getFrequencies(
    @PathVariable variantId: String,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
  ): List<FrequencyDTO> =
    frequencyQueryService.getFrequenciesForVariant(VariantHash(variantId), date)
}

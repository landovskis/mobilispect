package com.mobilispect.backend.route.api

import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.api.dto.CombinedFrequencyDTO
import com.mobilispect.backend.route.api.dto.CommonSectionDTO
import com.mobilispect.backend.route.application.CommonSectionService
import com.mobilispect.backend.route.domain.model.TimePeriod
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/common-sections")
class CommonSectionController(private val commonSectionService: CommonSectionService) {
  @GetMapping("/routes/{routeId}")
  fun getCommonSectionsForRoute(@PathVariable routeId: String): List<CommonSectionDTO> =
    commonSectionService.getCommonSectionsForRoute(RouteId(routeId))

  @GetMapping("/{sectionId}/frequency")
  fun getCombinedFrequency(
    @PathVariable sectionId: String,
    @RequestParam timePeriod: TimePeriod,
  ): CombinedFrequencyDTO? =
    commonSectionService.getCombinedFrequency(UUID.fromString(sectionId), timePeriod)

  @GetMapping("/{sectionId}/contributing-routes")
  fun getContributingRoutes(@PathVariable sectionId: String): List<String> =
    commonSectionService.getContributingRoutes(UUID.fromString(sectionId))
}

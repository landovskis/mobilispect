package com.mobilispect.backend.transitanalysis.api

import com.mobilispect.backend.transitanalysis.api.dto.CommonSectionDTO
import com.mobilispect.backend.transitanalysis.api.dto.CombinedFrequencyDTO
import com.mobilispect.backend.transitanalysis.application.CommonSectionService
import com.mobilispect.backend.transitanalysis.domain.model.TimePeriod
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/common-sections")
class CommonSectionController(
    private val commonSectionService: CommonSectionService
) {
    @GetMapping("/routes/{routeId}")
    fun getCommonSectionsForRoute(@PathVariable routeId: String): List<CommonSectionDTO> =
        commonSectionService.getCommonSectionsForRoute(RouteId(routeId))

    @GetMapping("/{sectionId}/frequency")
    fun getCombinedFrequency(
        @PathVariable sectionId: String,
        @RequestParam timePeriod: TimePeriod
    ): CombinedFrequencyDTO? =
        commonSectionService.getCombinedFrequency(UUID.fromString(sectionId), timePeriod)

    @GetMapping("/{sectionId}/contributing-routes")
    fun getContributingRoutes(
        @PathVariable sectionId: String
    ): List<String> = commonSectionService.getContributingRoutes(UUID.fromString(sectionId))
}

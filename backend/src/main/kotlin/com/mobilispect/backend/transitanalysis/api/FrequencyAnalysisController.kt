package com.mobilispect.backend.transitanalysis.api

import com.mobilispect.backend.transitanalysis.api.dto.AgencyDTO
import com.mobilispect.backend.transitanalysis.api.dto.AgencySummaryDTO
import com.mobilispect.backend.transitanalysis.application.AgencyQueryService
import com.mobilispect.backend.transitanalysis.domain.model.ids.AgencyId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/frequency")
class FrequencyAnalysisController(
    private val agencyQueryService: AgencyQueryService
) {

    @GetMapping("/agencies")
    fun listAgencies(pageable: Pageable): Page<AgencyDTO> =
        agencyQueryService.getAgencies(pageable)

    @GetMapping("/agencies/{agencyId}")
    fun getAgency(@PathVariable agencyId: String): AgencySummaryDTO? =
        agencyQueryService.getAgencySummary(AgencyId(agencyId))
}

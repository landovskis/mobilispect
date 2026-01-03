package com.mobilispect.backend.agency.api

import com.mobilispect.backend.agency.api.dto.AgencyDTO
import com.mobilispect.backend.agency.api.dto.AgencySummaryDTO
import com.mobilispect.backend.agency.application.AgencyQueryService
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.model.ids.RegionId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AgencyController(private val agencyQueryService: AgencyQueryService) {

  @GetMapping("/agencies")
  fun listAgencies(pageable: Pageable): Page<AgencyDTO> = agencyQueryService.getAgencies(pageable)

  @GetMapping("/regions/{regionId}/agencies")
  fun listAgenciesByRegion(@PathVariable regionId: String, pageable: Pageable): Page<AgencyDTO> =
    agencyQueryService.getAgenciesByRegion(RegionId(regionId), pageable)

  @GetMapping("/agencies/{agencyId}")
  fun getAgency(@PathVariable agencyId: String): AgencySummaryDTO? =
    agencyQueryService.getAgencySummary(AgencyId(agencyId))
}

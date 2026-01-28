package com.mobilispect.backend.route.api

import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.route.api.dto.CorridorDTO
import com.mobilispect.backend.route.application.CorridorQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/regions")
class CorridorController(private val corridorQueryService: CorridorQueryService) {
  @GetMapping("/{regionId}/corridors")
  fun getCorridorsForRegion(@PathVariable regionId: String): List<CorridorDTO> =
    corridorQueryService.getCorridorsForRegion(RegionId(regionId))
}

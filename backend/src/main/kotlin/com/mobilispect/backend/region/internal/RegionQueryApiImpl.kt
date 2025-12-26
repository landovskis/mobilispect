package com.mobilispect.backend.region.internal

import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import com.mobilispect.backend.region.api.RegionDTO
import com.mobilispect.backend.region.api.RegionQueryApi
import com.mobilispect.backend.region.domain.MetropolitanRegion
import org.springframework.stereotype.Service

/**
 * Implementation of the Region module's public query API.
 *
 * This service provides cross-module access to region data while maintaining
 * proper module boundaries. It converts internal entities to public DTOs.
 */
@Service
internal class RegionQueryApiImpl(
    private val regionRepository: MetropolitanRegionRepository
) : RegionQueryApi {

    override fun findRegionById(regionId: RegionId): RegionDTO? {
        return regionRepository.findByRegionOnestopId(regionId)
            .map { it.toDTO() }
            .orElse(null)
    }

    override fun findAllRegions(): List<RegionDTO> {
        return regionRepository.findAll()
            .map { it.toDTO() }
    }

    /**
     * Converts internal entity to public DTO.
     */
    private fun MetropolitanRegion.toDTO(): RegionDTO {
        return RegionDTO(
            regionId = this.regionOnestopId,
            name = this.name,
            country = this.adm0Name,
            provinceState = this.adm1Name,
            autoUpdateEnabled = this.autoUpdateEnabled,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}

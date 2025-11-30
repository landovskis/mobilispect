package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.transitanalysis.api.dto.CommonSectionDTO
import com.mobilispect.backend.transitanalysis.api.dto.CombinedFrequencyDTO
import com.mobilispect.backend.transitanalysis.domain.model.TimePeriod
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash
import com.mobilispect.backend.transitanalysis.domain.repository.CommonSectionRepository
import com.mobilispect.backend.transitanalysis.domain.repository.CommonSectionVariantRepository
import com.mobilispect.backend.transitanalysis.domain.repository.FrequencyRepository
import com.mobilispect.backend.transitanalysis.domain.repository.RouteVariantRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CommonSectionService(
    private val commonSectionRepository: CommonSectionRepository,
    private val commonSectionVariantRepository: CommonSectionVariantRepository,
    private val routeVariantRepository: RouteVariantRepository,
    private val frequencyRepository: FrequencyRepository
) {
    fun getCommonSectionsForRoute(routeId: RouteId): List<CommonSectionDTO> {
        val variants = routeVariantRepository.findByRouteId(routeId)
        val variantIds = variants.map { it.id }.toSet()

        val sectionVariants = variantIds.flatMap { commonSectionVariantRepository.findByVariantId(it) }
        val sections = sectionVariants.map { it.commonSection }.distinctBy { it.id }

        return sections.map { section ->
            CommonSectionDTO(
                id = section.id.toString(),
                stopPattern = section.stopPattern,
                stopCount = section.stopCount,
                firstStopId = section.firstStopId,
                lastStopId = section.lastStopId,
                variants = sectionVariants.filter { it.commonSection.id == section.id }
                    .map { it.variant.id.value }
            )
        }
    }

    fun getCombinedFrequency(sectionId: UUID, timePeriod: TimePeriod): CombinedFrequencyDTO? {
        val section = commonSectionRepository.findById(sectionId).orElse(null) ?: return null
        val sectionVariants = commonSectionVariantRepository.findAll()
            .filter { it.commonSection.id == sectionId }
        val frequencies = sectionVariants.flatMap { csv ->
            frequencyRepository.findByVariant(csv.variant, org.springframework.data.domain.Pageable.unpaged()).content
                .filter { it.timePeriod == timePeriod }
        }
        if (frequencies.isEmpty()) return CombinedFrequencyDTO(sectionId.toString(), timePeriod.name, null, 0, true)

        val headways = frequencies.mapNotNull { it.averageHeadway }
        val combinedHeadway = if (headways.isNotEmpty()) {
            val combined = headways.sumOf { 1.0 / it }
            if (combined > 0) 1 / combined else null
        } else null
        val isIrregular = frequencies.any { it.isIrregular } || combinedHeadway == null

        return CombinedFrequencyDTO(
            commonSectionId = sectionId.toString(),
            timePeriod = timePeriod.name,
            averageHeadwayMinutes = combinedHeadway,
            tripCount = frequencies.sumOf { it.tripCount },
            isIrregular = isIrregular
        )
    }

    fun getContributingRoutes(sectionId: UUID): List<String> {
        val sectionVariants = commonSectionVariantRepository.findAll()
            .filter { it.commonSection.id == sectionId }
        return sectionVariants.map { it.variant.route.id.value }.distinct()
    }
}

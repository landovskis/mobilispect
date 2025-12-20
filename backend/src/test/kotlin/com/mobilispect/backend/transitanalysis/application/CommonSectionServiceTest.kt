package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.transitanalysis.domain.model.CommonSection
import com.mobilispect.backend.transitanalysis.domain.model.CommonSectionVariant
import com.mobilispect.backend.transitanalysis.domain.model.Route
import com.mobilispect.backend.transitanalysis.domain.model.RouteVariant
import com.mobilispect.backend.transitanalysis.domain.model.TimePeriod
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash
import com.mobilispect.backend.transitanalysis.domain.repository.CommonSectionRepository
import com.mobilispect.backend.transitanalysis.domain.repository.CommonSectionVariantRepository
import com.mobilispect.backend.transitanalysis.domain.repository.FrequencyRepository
import com.mobilispect.backend.transitanalysis.domain.repository.RouteVariantRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class CommonSectionServiceTest {
    private val csRepo: CommonSectionRepository = mock(CommonSectionRepository::class.java)
    private val csvRepo: CommonSectionVariantRepository = mock(CommonSectionVariantRepository::class.java)
    private val variantRepo: RouteVariantRepository = mock(RouteVariantRepository::class.java)
    private val freqRepo: FrequencyRepository = mock(FrequencyRepository::class.java)
    private val service = CommonSectionService(csRepo, csvRepo, variantRepo, freqRepo)
    private val mockAgency = mock(com.mobilispect.backend.agency.domain.model.Agency::class.java)

    @Test
    fun `getCommonSectionsForRoute returns sections linked to route variants`() {
        val route = Route(
            id = RouteId("r-1"),
            agency = mockAgency,
            gtfsRouteId = "R1",
            longName = "Route 1",
            routeType = com.mobilispect.backend.transitanalysis.domain.model.RouteType.BUS,
            active = true
        )
        val variant = RouteVariant(
            id = VariantHash("d".repeat(64)),
            route = route,
            stopPattern = "s1|s2|s3",
            stopCount = 3,
            firstStopId = "s1",
            lastStopId = "s3"
        )
        val section = CommonSection(
            id = UUID.randomUUID(),
            stopPattern = "s1|s2|s3",
            stopCount = 3,
            firstStopId = "s1",
            lastStopId = "s3"
        )
        val csv = CommonSectionVariant(
            id = UUID.randomUUID(),
            commonSection = section,
            variant = variant,
            startSequence = 0,
            endSequence = 2
        )
        `when`(variantRepo.findByRouteId(RouteId("r-1"))).thenReturn(listOf(variant))
        `when`(csvRepo.findByVariantId(variant.id)).thenReturn(listOf(csv))
        val result = service.getCommonSectionsForRoute(RouteId("r-1"))
        assertThat(result).hasSize(1)
        assertThat(result.first().stopCount).isEqualTo(3)
    }

    @Test
    fun `getCombinedFrequency returns null when section missing`() {
        val id = UUID.randomUUID()
        `when`(csRepo.findById(id)).thenReturn(Optional.empty())
        val result = service.getCombinedFrequency(id, TimePeriod.WEEKDAY_AM_PEAK)
        assertThat(result).isNull()
    }

    @Test
    fun `getCombinedFrequency aggregates averages`() {
        val sectionId = UUID.randomUUID()
        val section = CommonSection(
            id = sectionId,
            stopPattern = "s1|s2|s3",
            stopCount = 3,
            firstStopId = "s1",
            lastStopId = "s3"
        )
        val variant = RouteVariant(
            id = VariantHash("e".repeat(64)),
            route = Route(id = RouteId("r-2"), agency = mockAgency, gtfsRouteId = "R2", longName = "Route 2", routeType = com.mobilispect.backend.transitanalysis.domain.model.RouteType.BUS, active = true),
            stopPattern = "s1|s2|s3",
            stopCount = 3,
            firstStopId = "s1",
            lastStopId = "s3"
        )
        val csv = CommonSectionVariant(
            id = UUID.randomUUID(),
            commonSection = section,
            variant = variant,
            startSequence = 0,
            endSequence = 2
        )
        val freq = com.mobilispect.backend.transitanalysis.domain.model.Frequency(
            variant = variant,
            serviceDate = LocalDate.of(2025, 1, 1),
            timePeriod = TimePeriod.WEEKDAY_AM_PEAK,
            averageHeadway = 10.0,
            minHeadway = 10.0,
            maxHeadway = 10.0,
            tripCount = 3,
            isIrregular = false,
            calculatedAt = Instant.now(),
            createdAt = Instant.now()
        )
        `when`(csRepo.findById(sectionId)).thenReturn(Optional.of(section))
        `when`(csvRepo.findBySectionId(sectionId)).thenReturn(listOf(csv))
        `when`(freqRepo.findByVariant(variant, Pageable.unpaged())).thenReturn(PageImpl(listOf(freq)))

        val result = service.getCombinedFrequency(sectionId, TimePeriod.WEEKDAY_AM_PEAK)
        assertThat(result?.tripCount).isEqualTo(3)
    }
}

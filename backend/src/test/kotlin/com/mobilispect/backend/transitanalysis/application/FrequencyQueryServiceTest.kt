package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.transitanalysis.domain.model.Route
import com.mobilispect.backend.transitanalysis.domain.model.RouteType
import com.mobilispect.backend.transitanalysis.domain.model.RouteVariant
import com.mobilispect.backend.transitanalysis.domain.model.TimePeriod
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash
import com.mobilispect.backend.transitanalysis.domain.repository.FrequencyRepository
import com.mobilispect.backend.transitanalysis.domain.repository.RouteRepository
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

class FrequencyQueryServiceTest {
    private val routeRepository: RouteRepository = mock(RouteRepository::class.java)
    private val variantRepository: RouteVariantRepository = mock(RouteVariantRepository::class.java)
    private val frequencyRepository: FrequencyRepository = mock(FrequencyRepository::class.java)
    private val service = FrequencyQueryService(routeRepository, variantRepository, frequencyRepository)
    private val mockAgency = mock(com.mobilispect.backend.agency.domain.model.Agency::class.java)

    @Test
    fun `getVariantsByRoute maps variants`() {
        val route = Route(
            id = RouteId("r-1"),
            agency = mockAgency,
            gtfsRouteId = "R1",
            longName = "Route 1",
            routeType = RouteType.BUS,
            active = true
        )
        val variant = RouteVariant(
            id = VariantHash("a".repeat(64)),
            route = route,
            stopPattern = "s1|s2",
            stopCount = 2,
            firstStopId = "s1",
            lastStopId = "s2"
        )
        `when`(variantRepository.findByRouteId(RouteId("r-1"))).thenReturn(listOf(variant))
        val result = service.getVariantsByRoute(RouteId("r-1"))
        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo("a".repeat(64))
        assertThat(result.first().stopPattern).isEqualTo("s1|s2")
    }

    @Test
    fun `getFrequenciesForVariant returns empty when variant missing`() {
        `when`(variantRepository.findById(VariantHash("b".repeat(64)))).thenReturn(Optional.empty())
        val result = service.getFrequenciesForVariant(VariantHash("b".repeat(64)), null)
        assertThat(result).isEmpty()
    }

    @Test
    fun `getFrequenciesForVariant maps frequencies`() {
        val route = Route(
            id = RouteId("r-1"),
            agency = mockAgency,
            gtfsRouteId = "R1",
            longName = "Route 1",
            routeType = RouteType.BUS,
            active = true
        )
        val variant = RouteVariant(
            id = VariantHash("c".repeat(64)),
            route = route,
            stopPattern = "s1|s2",
            stopCount = 2,
            firstStopId = "s1",
            lastStopId = "s2"
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
        `when`(variantRepository.findById(VariantHash("c".repeat(64)))).thenReturn(Optional.of(variant))
        `when`(frequencyRepository.findByVariant(variant, Pageable.unpaged())).thenReturn(PageImpl(listOf(freq)))
        val result = service.getFrequenciesForVariant(VariantHash("c".repeat(64)), null)
        assertThat(result).hasSize(1)
        assertThat(result.first().timePeriod).isEqualTo(TimePeriod.WEEKDAY_AM_PEAK)
    }
}

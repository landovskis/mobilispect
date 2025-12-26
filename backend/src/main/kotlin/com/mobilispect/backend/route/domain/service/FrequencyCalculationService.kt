package com.mobilispect.backend.route.domain.service

import com.mobilispect.backend.route.domain.model.Frequency
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.TimePeriod
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/**
 * Service for calculating transit frequency (headway) metrics.
 *
 * Calculates average, minimum, and maximum headways for route variants
 * during different time periods (peak, off-peak, weekend).
 */
interface FrequencyCalculationService {
    fun calculateFrequency(
        variant: RouteVariant,
        serviceDate: LocalDate,
        timePeriod: TimePeriod,
        departureTimes: List<LocalTime>
    ): Frequency?
}

@Service
class FrequencyCalculationServiceImpl : FrequencyCalculationService {
    override fun calculateFrequency(
        variant: RouteVariant,
        serviceDate: LocalDate,
        timePeriod: TimePeriod,
        departureTimes: List<LocalTime>
    ): Frequency? {
        if (departureTimes.isEmpty()) return null

        val sorted = departureTimes.sorted()
        val allHeadways = sorted.zipWithNext { a, b -> Duration.between(a, b).toMinutes().toDouble() }

        // Filter out zero or near-zero headways (data anomalies where buses depart simultaneously)
        // Database constraint requires min_headway > 0, so we exclude zeros
        val headways = allHeadways.filter { it > 0.0 }

        if (headways.isEmpty()) {
            return Frequency(
                variantId = variant.id.value,
                serviceDate = serviceDate,
                timePeriod = timePeriod,
                tripCount = departureTimes.size,
                isIrregular = true
            )
        }

        val min = headways.minOrNull() ?: return null
        val max = headways.maxOrNull() ?: return null
        val average = headways.average()

        val irregular = abs(max - min) > average

        return Frequency(
            variantId = variant.id.value,
            serviceDate = serviceDate,
            timePeriod = timePeriod,
            tripCount = departureTimes.size,
            averageHeadway = if (irregular) null else average,
            minHeadway = if (headways.isNotEmpty()) min else null,
            maxHeadway = if (headways.isNotEmpty()) max else null,
            isIrregular = irregular
        )
    }
}

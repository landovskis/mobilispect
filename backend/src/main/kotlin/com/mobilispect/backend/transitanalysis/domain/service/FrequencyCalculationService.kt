package com.mobilispect.backend.transitanalysis.domain.service

import com.mobilispect.backend.transitanalysis.domain.model.Frequency
import com.mobilispect.backend.transitanalysis.domain.model.RouteVariant
import com.mobilispect.backend.transitanalysis.domain.model.TimePeriod
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
        val headways = sorted.zipWithNext { a, b -> Duration.between(a, b).toMinutes().toDouble() }

        if (headways.isEmpty()) {
            return Frequency(
                variant = variant,
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
            variant = variant,
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

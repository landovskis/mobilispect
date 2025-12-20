package com.mobilispect.backend.transitanalysis.events

import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash
import java.time.LocalDate

data class FrequencyCalculationCompleted(
    val variantId: VariantHash,
    val serviceDate: LocalDate
)

package com.mobilispect.backend.route.events

import com.mobilispect.backend.route.domain.model.ids.VariantHash
import java.time.LocalDate

data class FrequencyCalculationCompleted(
    val variantId: VariantHash,
    val serviceDate: LocalDate
)

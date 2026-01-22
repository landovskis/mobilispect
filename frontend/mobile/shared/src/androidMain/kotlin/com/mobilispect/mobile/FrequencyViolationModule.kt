package com.mobilispect.mobile.com.mobilispect.mobile

import com.mobilispect.mobile.android.ui.frequency_violation.FrequencyViolationViewModel
import com.mobilispect.mobile.domain.frequency_violation.FindFrequencyViolationsOnDayAtStopUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.viewmodel.scope.viewModelScope

val frequencyViolationModule = module {
    factoryOf(::FindFrequencyViolationsOnDayAtStopUseCase)
    viewModelOf(::FrequencyViolationViewModel)
}

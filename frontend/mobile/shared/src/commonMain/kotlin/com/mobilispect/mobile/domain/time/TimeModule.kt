package com.mobilispect.mobile.domain.time

import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val timeModule = module {
    factory { FormatTimeUseCase(TimeZone.currentSystemDefault())}
}

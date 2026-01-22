package com.mobilispect.mobile.data.schedule

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.bind

val scheduleModule = module {
    singleOf(::FakeScheduleRepository) bind ScheduleRepository::class
}

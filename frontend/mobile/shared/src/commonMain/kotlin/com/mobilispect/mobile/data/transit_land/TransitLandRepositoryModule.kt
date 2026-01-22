package com.mobilispect.mobile.data.transit_land

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val transitLandModule = module {
    singleOf(::DefaultTransitLandConfigRepository) bind TransitLandConfigRepository::class
}

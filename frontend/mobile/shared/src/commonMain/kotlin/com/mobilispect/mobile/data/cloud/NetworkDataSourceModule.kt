package com.mobilispect.mobile.data.cloud

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val networkModule = module {
    singleOf(::MobilispectAPINetworkDataSource) bind NetworkDataSource::class
}

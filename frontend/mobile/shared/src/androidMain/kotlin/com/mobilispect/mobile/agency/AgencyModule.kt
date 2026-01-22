package com.mobilispect.mobile.com.mobilispect.mobile.agency

import com.mobilispect.mobile.agency.AgencyDAO
import com.mobilispect.mobile.agency.AgencyRepository
import com.mobilispect.mobile.data.AppDatabase
import com.mobilispect.mobile.ui.agencies.AgenciesViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val agencyModule = module {
    single<AgencyDAO> {
        val database = get<AppDatabase>()
        database.agencyDAO()
    }

    singleOf(::OfflineFirstAgencyRepository) bind AgencyRepository::class

    viewModel {
        AgenciesViewModel(
            agencyRepository = get()
        )
    }
}

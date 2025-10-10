package com.mobilispect.mobile.route

import com.mobilispect.mobile.android.ui.routes.RoutesListViewModel
import com.mobilispect.mobile.data.AppDatabase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind

val routeModule = module {
    single<RouteDAO> {
        val database = get<AppDatabase>()
        database.routeDAO()
    }
    singleOf(::OfflineFirstRouteRepository) bind RouteRepository::class

    viewModelOf(::RoutesListViewModel)

}

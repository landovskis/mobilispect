package com.mobilispect.mobile.data

import com.mobilispect.mobile.getDatabaseBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single<AppDatabase> {
        getDatabaseBuilder(androidContext()).build()
    }
}

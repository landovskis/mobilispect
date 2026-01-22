package com.mobilispect.mobile.android

import android.app.Application
import com.mobilispect.mobile.com.mobilispect.mobile.agency.agencyModule
import com.mobilispect.mobile.com.mobilispect.mobile.frequencyViolationModule
import com.mobilispect.mobile.data.cloud.networkModule
import com.mobilispect.mobile.data.databaseModule
import com.mobilispect.mobile.data.schedule.scheduleModule
import com.mobilispect.mobile.data.transit_land.ktorHTTPClientModule
import com.mobilispect.mobile.data.transit_land.transitLandModule
import com.mobilispect.mobile.domain.time.timeModule
import com.mobilispect.mobile.route.routeModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.logger.Level
import org.koin.dsl.KoinConfiguration
import org.koin.dsl.koinConfiguration

@OptIn(KoinExperimentalAPI::class)
class MobilispectApp : Application(), KoinStartup {
    override fun onKoinStartup(): KoinConfiguration =
        koinConfiguration {
            androidContext(this@MobilispectApp)
            androidLogger(Level.INFO)
            modules(
                agencyModule,
                databaseModule,
                frequencyViolationModule,
                ktorHTTPClientModule,
                networkModule,
                routeModule,
                scheduleModule,
                timeModule,
                transitLandModule
            )
    }
}

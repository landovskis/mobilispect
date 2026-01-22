package com.mobilispect.mobile.data.transit_land

import com.mobilispect.mobile.data.transit_land.TransitLandConfig
import com.mobilispect.mobile.data.transit_land.TransitLandConfigRepository

class FakeTransitLandConfigRepository : TransitLandConfigRepository {
    private var config: TransitLandConfig? = null

    override fun config(): TransitLandConfig? = config

    fun insert(config: TransitLandConfig) {
        this.config = config
    }

}

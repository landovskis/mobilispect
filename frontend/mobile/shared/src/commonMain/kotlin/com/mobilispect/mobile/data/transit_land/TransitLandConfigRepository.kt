package com.mobilispect.mobile.data.transit_land

interface TransitLandConfigRepository {
    fun config(): TransitLandConfig?
}

class DefaultTransitLandConfigRepository : TransitLandConfigRepository {
    override fun config(): TransitLandConfig? {
        return TransitLandConfig(
            apiKey = "CvaXEwfuCJ7x1mC169r53ygiVU8N55mj"
        )
    }

    companion object {
        const val FILE_NAME = "transit-land-config.json"
    }
}

package com.mobilispect.backend.infastructure.transit_land

import kotlinx.serialization.Serializable

@Serializable internal class TransitLandStopResponse(val stops: Collection<TransitLandStop>)

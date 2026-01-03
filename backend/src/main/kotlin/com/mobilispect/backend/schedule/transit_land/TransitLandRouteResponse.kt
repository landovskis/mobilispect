package com.mobilispect.backend.schedule.transit_land

import com.mobilispect.backend.transit_land.Meta
import kotlinx.serialization.Serializable

@Serializable
internal class TransitLandRouteResponse(
  val meta: Meta? = null,
  val routes: Collection<TransitLandRoute>,
)

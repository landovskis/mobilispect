package com.mobilispect.backend.infastructure.transit_land

import com.mobilispect.backend.infastructure.StopIDDataSource
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository

/** A [StopIDDataSource] that uses transit.land for stop IDs. */
class TransitLandStopIDDataSource(
  private val transitLandAPI: TransitLandAPI,
  private val transitLandCredentialsRepository: TransitLandCredentialsRepository,
) : StopIDDataSource {
  override fun stop(feedID: String, stopID: String): Result<String> {
    val apiKey =
      transitLandCredentialsRepository.get() ?: return Result.failure(Exception("Missing API key"))
    return transitLandAPI.stop(apiKey = apiKey, feedID = feedID, stopID = stopID).map { it.uid }
  }
}

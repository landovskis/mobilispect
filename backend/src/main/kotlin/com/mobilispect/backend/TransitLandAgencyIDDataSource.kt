package com.mobilispect.backend

import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository

/** A [AgencyIDDataSource] that uses transit.land for agency IDs. */
class TransitLandAgencyIDDataSource(
  private val transitLandAPI: TransitLandAPI,
  private val transitLandCredentialsRepository: TransitLandCredentialsRepository,
) : AgencyIDDataSource {
  override fun agencyIDs(feedID: String): Result<Map<String, String>> {
    val apiKey =
      transitLandCredentialsRepository.get() ?: return Result.failure(Exception("Missing API key"))
    return transitLandAPI.agencies(apiKey = apiKey, feedID = feedID).map { agencies ->
      agencies.agencies
        .filter { it.agencyID != null }
        .associate { item -> item.agencyID!! to item.id }
    }
  }
}

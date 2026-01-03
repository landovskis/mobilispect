package com.mobilispect.backend.schedule.gtfs

import com.mobilispect.backend.AgencyIDDataSource

class StubAgencyIDDataSource(private val pairs: Map<String, String>) : AgencyIDDataSource {
  override fun agencyIDs(feedID: String): Result<Map<String, String>> = Result.success(pairs)
}

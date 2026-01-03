package com.mobilispect.backend

interface AgencyIDDataSource {
  fun agencyIDs(feedID: String): Result<Map<String, String>>
}

package com.mobilispect.backend

import com.mobilispect.backend.infastructure.StopIDDataSource

class StubStopIDDataSource(private val pairs: Map<String, String>) : StopIDDataSource {
  override fun stop(feedID: String, stopID: String): Result<String> {
    val id = pairs[stopID]
    if (id == null) {
      return Result.failure(Exception("No ID for ${stopID}"))
    }
    return Result.success(id)
  }
}

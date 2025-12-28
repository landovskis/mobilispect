package com.mobilispect.backend.infastructure

interface StopIDDataSource {
  fun stop(feedID: String, stopID: String): Result<String>
}

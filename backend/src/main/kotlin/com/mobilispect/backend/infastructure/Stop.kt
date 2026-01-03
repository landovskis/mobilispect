package com.mobilispect.backend.infastructure

data class Stop(
  val uid: String,
  val localID: String,
  val name: String,
  val versions: Collection<String>,
)

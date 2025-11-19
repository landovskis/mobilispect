package com.mobilispect.backend.schedule


import java.time.LocalDate

data class ScheduledTrip(
    val uid: String,
    val routeID: String,
    val dates: Collection<LocalDate>,
    val direction: String,
    val versions: Collection<String>
)

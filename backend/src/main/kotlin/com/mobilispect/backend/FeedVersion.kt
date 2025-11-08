package com.mobilispect.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

@Document(value = "feed_versions")
data class FeedVersion(
    val uid: String, val feedID: String, val startsOn: LocalDate, val endsOn: LocalDate
)

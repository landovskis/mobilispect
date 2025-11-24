package com.mobilispect.backend.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val json = Json { encodeDefaults = true }

@Serializable
private data class Wrapper(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate
)

class LocalDateSerializerTest {
    @Test
    fun `deserializes basic ISO dates`() {
        val decoded = json.decodeFromString<Wrapper>("""{"date":"20240102"}""")

        assertEquals(LocalDate.of(2024, 1, 2), decoded.date)
    }

    @Test
    fun `serializes to ISO-8601 string`() {
        val encoded = json.encodeToString(Wrapper(LocalDate.of(2024, 2, 29)))

        assertEquals("""{"date":"2024-02-29"}""", encoded)
    }
}

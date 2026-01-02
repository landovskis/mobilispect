package com.mobilispect.mobile.data.transit_land

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransitLandClientTest {
    @Test
    fun fromRef_parsesRoutes() = runTest {
        val engine = MockEngine { request ->
            assertEquals("transit.land", request.url.host)
            assertEquals("/api/v2/rest/routes/r-f25ej-19", request.url.encodedPath)
            assertEquals("KEY", request.headers["apikey"])
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "routes": [
                        { "route_long_name": "Beaubien", "route_short_name": "18" }
                      ]
                    }
                    """.trimIndent()
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val subject = TransitLandClient(client)
        val response = subject.fromRef("r-f25ej-19", "KEY")

        assertEquals(1, response.routes.size)
        val route = response.routes.first()
        assertEquals("Beaubien", route.longName)
        assertEquals("18", route.shortName)
        client.close()
    }

    @Test
    fun fromRef_handlesEmptyRoutes() = runTest {
        val engine = MockEngine { request ->
            assertEquals("transit.land", request.url.host)
            assertEquals("/api/v2/rest/routes/r-f25ej-19", request.url.encodedPath)
            respond(
                content = ByteReadChannel("{\"routes\": []}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val subject = TransitLandClient(client)
        val response = subject.fromRef("r-f25ej-19", "KEY")

        assertTrue(response.routes.isEmpty())
        client.close()
    }
}

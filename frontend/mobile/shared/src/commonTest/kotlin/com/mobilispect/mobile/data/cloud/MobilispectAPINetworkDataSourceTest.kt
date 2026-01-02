package com.mobilispect.mobile.data.cloud

import com.mobilispect.mobile.testing.AGENCIES_SUCCESSFUL_FIXTURE
import com.mobilispect.mobile.testing.ROUTES_SUCCESSFUL_FIXTURE
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MobilispectAPINetworkDataSourceTest {
    @Test
    fun agencies_networkError() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/agencies", request.url.encodedPath, null)
            throw IOException("Failed to connect")
        }

        val networkDataSource = MobilispectAPINetworkDataSource(buildClient(mockEngine))

        val actual = networkDataSource.agencies()

        assertIs<NetworkError>(actual.exceptionOrNull())
    }

    @Test
    fun agencies_returnsList() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/agencies", request.url.encodedPath, null)
            respond(
                content = ByteReadChannel(
                    AGENCIES_SUCCESSFUL_FIXTURE.trimIndent()
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val networkDataSource = MobilispectAPINetworkDataSource(buildClient(mockEngine))

        val actual = networkDataSource.agencies().getOrNull()!!

        with(actual.first()) {
            assertEquals("o-abcd-a", id)
            assertEquals("A", name)
        }
        with(actual.last()) {
            assertEquals("o-abcd-b", id)
            assertEquals("B", name)
        }
    }

    @Test
    fun routesOperatedBy_networkError() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/routes/search/findAllByAgencyID", request.url.encodedPath)
            assertEquals("agencyID=o-abcd-a", request.url.encodedQuery)
            throw IOException("Failed to connect")
        }

        val networkDataSource = MobilispectAPINetworkDataSource(buildClient(mockEngine))

        val actual = networkDataSource.routesOperatedBy("o-abcd-a")

        assertIs<NetworkError>(actual.exceptionOrNull())
    }

    @Test
    fun routesOperatedBy_returnsList() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/routes/search/findAllByAgencyID", request.url.encodedPath)
            assertEquals("agencyID=o-abcd-a", request.url.encodedQuery)
            respond(
                content = ByteReadChannel(
                    ROUTES_SUCCESSFUL_FIXTURE.trimIndent()
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val networkDataSource = MobilispectAPINetworkDataSource(buildClient(mockEngine))

        val actual = networkDataSource.routesOperatedBy("o-abcd-a").getOrNull()!!

        with(actual.first()) {
            assertEquals("r-abcd-1", id)
            assertEquals("1", shortName)
            assertEquals("Main Street", longName)
            assertEquals("o-abcd-a", agencyID)
        }
        with(actual.last()) {
            assertEquals("r-abcd-2", id)
            assertEquals("2", shortName)
            assertEquals("Central Avenue", longName)
            assertEquals("o-abcd-a", agencyID)
        }
    }

    private fun buildClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(Resources)
        }
}

package com.mobilispect.backend.feed.integration

import com.mobilispect.backend.feed.model.FeedSpecType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class TransitLandApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: TransitLandApiClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/api/").toString().trimEnd('/')
        client = TransitLandApiClient(
            apiKey = "test-key",
            baseUrl = baseUrl,
            builder = WebClient.builder()
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `discoverRegionalFeeds parses feed metadata`() = runBlocking {
        val responseBody = """
            {
              "feeds": [
                {
                  "onestop_id": "f-test-feed",
                  "name": "Test Feed",
                  "spec": "gtfs",
                  "feed_versions": [
                    {
                      "sha1": "abc123",
                      "fetched_at": "2025-01-10T12:00:00Z",
                      "url": "https://example.com/feeds/test.zip"
                    }
                  ],
                  "urls": {
                    "static_current": "https://example.com/feeds/test.zip",
                    "realtime_trip_updates": "https://example.com/feeds/test.pb"
                  },
                  "authorization": {
                    "type": "api_key",
                    "param_name": "X-API-Key",
                    "info_url": "https://docs.example.com/auth"
                  }
                }
              ]
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody)
        )

        val summaries = client.discoverRegionalFeeds("Test Region", FeedSpecType.GTFS)

        assertThat(summaries).hasSize(1)
        val summary = summaries.first()
        assertThat(summary.feedOnestopId).isEqualTo("f-test-feed")
        assertThat(summary.specType).isEqualTo(FeedSpecType.GTFS)
        assertThat(summary.staticFeedUrl).isEqualTo("https://example.com/feeds/test.zip")
        assertThat(summary.realtimeFeedUrl).isEqualTo("https://example.com/feeds/test.pb")
        assertThat(summary.latestVersionSha1).isEqualTo("abc123")
        assertThat(summary.authorization?.type).isEqualTo("api_key")
        assertThat(summary.authorization?.parameterName).isEqualTo("X-API-Key")
    }

    @Test
    fun `discoverRegionalFeeds retries on rate limiting`() = runBlocking {
        val errorResponse = MockResponse()
            .setResponseCode(429)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"error":"Too Many Requests"}""")
        val successResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "feeds": [
                    {
                      "onestop_id": "f-rate-limit",
                      "spec": "gtfs",
                      "feed_versions": [
                        {
                          "sha1": "def456",
                          "fetched_at": "2025-01-11T12:00:00Z",
                          "url": "https://example.com/rate-limit.zip"
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )

        server.enqueue(errorResponse)
        server.enqueue(successResponse)

        val summaries = client.discoverRegionalFeeds("Rate Limit Region", FeedSpecType.GTFS)

        assertThat(summaries).hasSize(1)
        assertThat(summaries.first().latestVersionSha1).isEqualTo("def456")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `discoverRegionalFeeds throws exception on server error`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"Internal Server Error"}""")
        )

        assertThatThrownBy {
            runBlocking {
                client.discoverRegionalFeeds("Error Region", FeedSpecType.GTFS)
            }
        }
            .isInstanceOf(TransitLandApiException::class.java)
            .hasMessageContaining("500")
    }
}

package com.mobilispect.mobile.data.transit_land

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.path

class TransitLandClient(private val client: HttpClient) {
    private val baseUrl = "https://transit.land" // Define your base URL

    suspend fun fromRef(
        routeRef: String,
        apiKey: String
    ): TransitLandRouteResponse {
        return client.get(baseUrl) {
            url {
                // Construct the path segments
                path("api", "v2", "rest", "routes", routeRef)
            }
            header("apikey", apiKey)
        }.body() // Automatically deserializes the JSON response to TransitLandRouteResponse
    }
}

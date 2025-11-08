package com.mobilispect.mobile.data.transit_land

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val ktorHTTPClientModule = module {
    single<HttpClient> {
        HttpClient(CIO) { // Or HttpClient(Android) for Android
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true // Important for handling evolving APIs
                })
            }
        }
    }
}

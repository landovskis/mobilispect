package com.mobilispect.backend.config

import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(TransitLandProperties::class)
class TransitLandCredentialsConfiguration {
    @Bean
    fun transitLandCredentialsRepository(properties: TransitLandProperties): TransitLandCredentialsRepository {
        return object : TransitLandCredentialsRepository {
            override fun get(): String? = properties.apiKey
        }
    }
}

@ConfigurationProperties(prefix = "app.transit-land")
data class TransitLandProperties(
    val apiKey: String? = null
)

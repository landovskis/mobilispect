package com.mobilispect.backend.feed.integration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@TestConfiguration
@EnableJpaRepositories(basePackages = ["com.mobilispect.backend.feed.repository"])
@EntityScan(basePackages = ["com.mobilispect.backend.feed.model"])
class RegionDiscoveryIntegrationTestConfig {

    @Bean
    fun meterRegistry(): MeterRegistry {
        return SimpleMeterRegistry()
    }

    @Bean
    fun clock(): Clock {
        return Clock.fixed(Instant.parse("2025-01-15T12:00:00Z"), ZoneId.of("UTC"))
    }
}

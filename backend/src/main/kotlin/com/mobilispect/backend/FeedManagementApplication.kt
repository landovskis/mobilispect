package com.mobilispect.backend

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Feed Management System Application
 *
 * Constitutional Compliance:
 * - Performance: Optimized with connection pooling and caching
 * - Security: JWT authentication and role-based access control
 * - Observability: Structured logging and metrics collection
 * - Architecture: Clean DDD architecture with proper separation
 */
@SpringBootApplication(
    scanBasePackages = ["com.mobilispect.backend"]
)

@EnableJpaRepositories(basePackages = [
    "com.mobilispect.backend.agency.domain.repository",
    "com.mobilispect.backend.agency.data.repository",
    "com.mobilispect.backend.feed.repository",
    "com.mobilispect.backend.feed.data.repository",
    "com.mobilispect.backend.route.data.repository",
    "com.mobilispect.backend.route.domain.repository",
    "com.mobilispect.backend.stop.data.repository",
    "com.mobilispect.backend.transitanalysis.domain.repository",
    "com.mobilispect.backend.transitanalysis.data.repository"
])
@EnableScheduling
@EnableAsync
@EnableBatchProcessing
class FeedManagementApplication

fun main(args: Array<String>) {
    runApplication<FeedManagementApplication>(*args)
}

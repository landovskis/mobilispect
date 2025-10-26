package com.mobilispect.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
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
@EnableMongoRepositories(basePackages = ["com.mobilispect.backend"])
@EnableScheduling
@EnableAsync
class FeedManagementApplication

fun main(args: Array<String>) {
    runApplication<FeedManagementApplication>(*args)
}

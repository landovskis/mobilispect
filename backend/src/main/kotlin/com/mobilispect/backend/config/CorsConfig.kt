package com.mobilispect.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

/**
 * CORS Configuration
 *
 * Enables Cross-Origin Resource Sharing (CORS) for the frontend application. Allows requests from
 * localhost development servers.
 */
@Configuration
class CorsConfig {

  @Bean
  fun corsFilter(): CorsFilter {
    val source = UrlBasedCorsConfigurationSource()
    val config = CorsConfiguration()

    // Allow credentials (cookies, authorization headers)
    config.allowCredentials = true

    // Allow frontend origins
    config.allowedOriginPatterns = listOf("http://localhost:*", "http://127.0.0.1:*")

    // Allow all HTTP methods
    config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")

    // Allow all headers
    config.allowedHeaders = listOf("*")

    // Expose headers to frontend
    config.exposedHeaders =
      listOf(
        "Authorization",
        "Content-Type",
        "X-Requested-With",
        "Accept",
        "Origin",
        "Access-Control-Request-Method",
        "Access-Control-Request-Headers",
      )

    // Cache preflight response for 1 hour
    config.maxAge = 3600L

    // Apply CORS configuration to all endpoints
    source.registerCorsConfiguration("/**", config)

    return CorsFilter(source)
  }
}

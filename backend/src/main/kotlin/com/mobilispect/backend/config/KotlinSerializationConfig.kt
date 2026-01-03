package com.mobilispect.backend.config

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

/**
 * Configuration for Kotlin serialization in Spring WebFlux.
 *
 * This configuration creates a custom JSON instance that ignores unknown keys from API responses,
 * preventing deserialization errors when the API schema evolves or contains fields we don't use.
 */
@Configuration
class KotlinSerializationConfig {

  /**
   * Custom JSON configuration with lenient settings.
   *
   * Key features:
   * - ignoreUnknownKeys: Prevents errors when API responses contain unexpected fields
   * - isLenient: Allows quoted boolean literals and unquoted strings
   * - coerceInputValues: Replaces nulls with default values for non-nullable properties
   */
  @Bean
  fun json(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    prettyPrint = false
    encodeDefaults = true
  }

  /**
   * WebClient builder configured with custom Kotlin serialization codecs.
   *
   * This builder is configured to ignore unknown JSON keys, which is necessary for Transit.land API
   * responses that may contain extra fields.
   */
  @Bean
  fun webClientBuilder(json: Json): WebClient.Builder {
    val decoder = KotlinSerializationJsonDecoder(json)
    val encoder = KotlinSerializationJsonEncoder(json)

    val strategies =
      ExchangeStrategies.builder()
        .codecs { configurer: ClientCodecConfigurer ->
          configurer.defaultCodecs().kotlinSerializationJsonDecoder(decoder)
          configurer.defaultCodecs().kotlinSerializationJsonEncoder(encoder)
        }
        .build()

    return WebClient.builder().exchangeStrategies(strategies)
  }
}

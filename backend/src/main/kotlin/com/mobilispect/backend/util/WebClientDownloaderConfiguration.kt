package com.mobilispect.backend.schedule.download

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
internal class WebClientDownloaderConfiguration {
  @Bean
  internal fun downloader(webClientBuilder: WebClient.Builder): Downloader =
    WebClientDownloader(webClientBuilder)
}

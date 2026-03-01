package com.mobilispect.backend.gtfsrt

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/** Spring configuration for the GTFS-RT ingestion module. */
@Configuration
class GtfsRtConfiguration {

  @Bean fun gtfsRtWebClient(builder: WebClient.Builder): WebClient = builder.build()
}

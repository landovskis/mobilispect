package com.mobilispect.backend.schedule.feed

import com.mobilispect.backend.FeedDataSource
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.TransitLandFeedDataSource
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FeedDataSourceConfiguration {
  @Bean
  fun feedDataSource(
    transitLandAPI: TransitLandAPI,
    transitLandCredentialsRepository: TransitLandCredentialsRepository,
  ): FeedDataSource =
    TransitLandFeedDataSource(
      transitLandClient = transitLandAPI,
      transitLandCredentialsRepository = transitLandCredentialsRepository,
    )
}

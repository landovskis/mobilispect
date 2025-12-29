package com.mobilispect.backend.schedule.transit_land

import com.mobilispect.backend.infastructure.StopIDDataSource
import com.mobilispect.backend.infastructure.transit_land.TransitLandStopIDDataSource
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingRepository
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class StopIDDataSourceConfiguration {
  @Bean
  fun oneStopStopIDDataSource(
    transitLandAPI: TransitLandAPI,
    transitLandCredentialsRepository: TransitLandCredentialsRepository,
    mappingRepository: TransitLandOnestopIdMappingRepository,
  ): StopIDDataSource =
    TransitLandStopIDDataSource(transitLandAPI, transitLandCredentialsRepository, mappingRepository)
}

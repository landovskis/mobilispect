package com.mobilispect.backend

import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingRepository
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class AgencyIDDataSourceConfiguration {
  @Bean
  fun oneStopAgencyIDDataSource(
    transitLandAPI: TransitLandAPI,
    credentialRepository: TransitLandCredentialsRepository,
    mappingRepository: TransitLandOnestopIdMappingRepository,
  ): AgencyIDDataSource =
    TransitLandAgencyIDDataSource(transitLandAPI, credentialRepository, mappingRepository)
}

package com.mobilispect.backend.schedule.gtfs

import com.mobilispect.backend.AgencyIDDataSource
import com.mobilispect.backend.schedule.route.RouteDataSource
import com.mobilispect.backend.schedule.route.RouteIDDataSource
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.TransitLandRouteIDDataSource
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class GTFSRouteDataSourceConfiguration {
  @Bean
  fun routeIDDataSource(
    transitLandAPI: TransitLandAPI,
    transitLandCredentialsRepository: TransitLandCredentialsRepository,
  ): RouteIDDataSource =
    TransitLandRouteIDDataSource(transitLandAPI, transitLandCredentialsRepository)

  @Bean
  fun routeDataSource(
    agencyIDDataSource: AgencyIDDataSource,
    routeIDDataSource: RouteIDDataSource,
  ): RouteDataSource = GTFSRouteDataSource(agencyIDDataSource, routeIDDataSource)
}

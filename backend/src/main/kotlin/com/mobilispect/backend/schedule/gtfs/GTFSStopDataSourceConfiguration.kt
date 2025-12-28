package com.mobilispect.backend.schedule.gtfs

import com.mobilispect.backend.infastructure.StopIDDataSource
import com.mobilispect.backend.schedule.stop.StopDataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class GTFSStopDataSourceConfiguration {
  @Bean
  fun stopDataSource(stopIDDataSource: StopIDDataSource): StopDataSource =
    GTFSStopDataSource(stopIDDataSource)
}

package com.mobilispect.backend.route.api.dto

import com.mobilispect.backend.route.domain.model.TimePeriod

data class FrequencyDTO(
  val id: String,
  val variantId: String,
  val serviceDate: String,
  val timePeriod: TimePeriod,
  val averageHeadwayMinutes: Double?,
  val minHeadwayMinutes: Double?,
  val maxHeadwayMinutes: Double?,
  val tripCount: Int,
  val isIrregular: Boolean,
)

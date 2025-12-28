package com.mobilispect.backend.route.batch.spacing

import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.StopSpacing

/**
 * Input for stop spacing calculation in Spring Batch processing.
 *
 * Combines a route variant with stop location data to enable stop spacing calculation between
 * consecutive stops.
 */
data class StopSpacingInput(val variant: RouteVariant, val stopsById: Map<String, GTFSStop>)

/**
 * Output batch for stop spacing calculation in Spring Batch processing.
 *
 * Contains individual StopSpacing records for each consecutive stop pair along the route variant.
 */
data class StopSpacingBatch(val spacings: List<StopSpacing>)

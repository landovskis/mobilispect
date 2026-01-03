package com.mobilispect.backend.route.batch.frequency

import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.route.domain.model.Frequency
import com.mobilispect.backend.route.domain.model.RouteVariant

/**
 * Input for frequency calculation in Spring Batch processing.
 *
 * Combines a route variant with its associated trips from GTFS data to enable frequency
 * calculation.
 */
data class FrequencyInput(val variant: RouteVariant, val trips: List<GTFSTrip>)

/**
 * Output batch for frequency calculation in Spring Batch processing.
 *
 * Contains calculated frequency records for a variant across all applicable time periods.
 */
data class FrequencyBatch(val frequencies: List<Frequency>)

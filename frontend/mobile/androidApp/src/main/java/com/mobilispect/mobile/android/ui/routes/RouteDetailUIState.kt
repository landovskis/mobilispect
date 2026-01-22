package com.mobilispect.mobile.android.ui.routes


class SpanUIState(val start: kotlinx.datetime.LocalTime, val end: kotlinx.datetime.LocalTime)

@Suppress("PropertyName")
class RouteDetailUIState(
    val routeID: String,
    val name: String,
    val stopSpacing: RouteStopSpacingUIState,
    val frequencyViolations: Int? = null,
    val averageFrequency_min: Int? = null,
    val spans: Collection<SpanUIState> = emptyList()
)

class RouteStopSpacingUIState(
    val value: Int,
    val unit: String
)

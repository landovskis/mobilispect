package com.mobilispect.backend.util

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Helper that returns both the elapsed [Duration] and value produced by [block].
 */
fun <T> measureTime(block: () -> T): Pair<Duration, T> {
    val mark = TimeSource.Monotonic.markNow()
    val value = block()
    return mark.elapsedNow() to value
}

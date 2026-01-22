package com.mobilispect.android.ui.frequency

import com.mobilispect.common.util.Ranked

class MostFrequentRoutesUIState(val routes: Collection<Route>) {
    data class Route(
        val shortName: String,
        val longName: String,
        private val minFrequency: Number,
        private val maxFrequency: Number
    ) : Ranked {
        override val range: Collection<Number>
            get() = listOf(minFrequency, maxFrequency)
    }

}

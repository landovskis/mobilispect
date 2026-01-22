@file:Suppress("MagicNumber")

package com.mobilispect.mobile.android.ui.frequency

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mobilispect.android.ui.frequency.MostFrequentRoutesUIState
import com.mobilispect.mobile.android.ui.RankedCard
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.R
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

/**
 * The most frequent routes by 7-day median frequency.
 */
@Composable
fun MostFrequentRoutesCard(uiState: MostFrequentRoutesUIState, modifier: Modifier = Modifier) {
    RankedCard(
        "Most Frequent Routes",
        "by Median Frequency",
        uiState.routes,
        stringResource(id = R.string.minutes),
        modifier
    ) { subModifier, item ->
        RouteName(route = item, modifier = subModifier.fillMaxWidth(0.5f))
    }
}

@Composable
private fun RouteName(route: MostFrequentRoutesUIState.Route, modifier: Modifier = Modifier) {
    Text(
        text = "${route.shortName} ${route.longName}",
        modifier = modifier.padding(horizontal = 5.dp),
        style = MaterialTheme.typography.bodyLarge
    )
}

@ThemePreviews
@Composable
fun PreviewMostFrequentRoutesCard() {
    MobilispectTheme {
        MostFrequentRoutesCard(
            uiState = MostFrequentRoutesUIState(
                routes = listOf(
                    MostFrequentRoutesUIState.Route(
                        shortName = "1",
                        longName = "Main Street",
                        minFrequency = 5,
                        maxFrequency = 10
                    ), MostFrequentRoutesUIState.Route(
                        shortName = "2",
                        longName = "Central Avenue",
                        minFrequency = 10,
                        maxFrequency = 10
                    )
                )
            )
        )
    }
}

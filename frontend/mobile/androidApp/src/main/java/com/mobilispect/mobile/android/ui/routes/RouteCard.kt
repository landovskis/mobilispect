package com.mobilispect.mobile.android.ui.routes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.R
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

@Composable
fun RouteCard(route: RouteUIState, modifier: Modifier = Modifier) {
    RectangularCard {
        Column(modifier = Modifier.Companion.padding(8.dp)) {
            Text(
                "${route.shortName}: ${route.longName}",
                style = MaterialTheme.typography.bodySmall,
                modifier = modifier
            )
            if (route.hasFrequencyCommittment) {
                FilledTonalButton(
                    onClick = {},
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.5f)

                    ),
                ) {
                    Text(
                        stringResource(
                            R.string.frequency_commitment
                        )
                    )
                }
            }
        }
    }
}

@Preview
@ThemePreviews
@Composable
fun PreviewRouteCardWithCommitment() {
    MobilispectTheme {
        RouteCard(
            RouteUIState(
                id = "r-abcd-1",
                shortName = "1",
                longName = "Main Street",
                agencyID = "r-abcd-a",
                hasFrequencyCommittment = true
            ),
        )
    }
}

@Preview
@ThemePreviews
@Composable
fun PreviewRouteCardWithoutCommittment() {
    MobilispectTheme {
        RouteCard(
            RouteUIState(
                id = "r-abce-",
                shortName = "2",
                longName = "1st Avenue",
                agencyID = "r-abcd-a",
                hasFrequencyCommittment = false
            )
        )
    }
}

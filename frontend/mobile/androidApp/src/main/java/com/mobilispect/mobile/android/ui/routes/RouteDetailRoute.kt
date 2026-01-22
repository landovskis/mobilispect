package com.mobilispect.mobile.android.ui.routes

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mobilispect.android.ui.previews.LanguagePreviews
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.R
import com.mobilispect.mobile.android.ui.Card
import com.mobilispect.mobile.android.ui.ScreenFrame
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
class RouteDetail(val routeID: String)

fun NavGraphBuilder.routeDetailGraph(navigateToFrequencyViolations: (String) -> Unit) {
    composable<RouteDetail> {
        RouteDetailRoute(navigateToFrequencyViolations)
    }
}

@Composable
fun RouteDetailRoute(navigateToFrequencyViolations: (String) -> Unit) {
    //val viewModel: RouteDetailViewModel = koinViewModel()
    //val uiState by viewModel.uiState.collectAsState()
    RouteDetailScreen(
        RouteDetailUIState(
            "",
            "",
            RouteStopSpacingUIState(0, ""),
            120
        ),
        navigateToFrequencyViolations = {}
    )
}

class StatsCardUIState(
    @StringRes val title: Int,
    val value: Int,
    val unit: String?,
    val onClick: (() -> Unit)? = null
)

@Composable
fun RouteDetailScreen(
    uiState: RouteDetailUIState,
    navigateToFrequencyViolations: (String) -> Unit,
) {
    ScreenFrame(screenTitle = uiState.name) {
        Column {
            Row {
                Card(title = stringResource(R.string.termini)) {
                    Text(text = "Station Mont-Royal", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatsCard(
                    StatsCardUIState(
                        R.string.stop_spacing,
                        uiState.stopSpacing.value,
                        uiState.stopSpacing.unit
                    )
                )
            }

            if (uiState.frequencyViolations != null) {
                Row {
                    StatsCard(
                        StatsCardUIState(
                            R.string.frequency_violations,
                            uiState.frequencyViolations,
                            unit = null,
                            onClick = { navigateToFrequencyViolations(uiState.routeID) }
                        )
                    )
                }
            }

            if (uiState.averageFrequency_min != null) {
                Row {
                    StatsCard(
                        StatsCardUIState(
                            R.string.frequency,
                            uiState.averageFrequency_min,
                            unit = "min"
                        )
                    )
                }
            }

            if (uiState.spans.isNotEmpty()) {
                Row {
                    Card(
                        title = stringResource(R.string.span)
                    ) {
                        for (span in uiState.spans) {
                            Text(
                                text = "${span.start.hour}:${span.start.minute} - ${span.end.hour}:${span.end.minute}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@ThemePreviews
@LanguagePreviews
@Composable
fun PreviewRouteDetailScreen() {
    MobilispectTheme {
        RouteDetailScreen(
            uiState = RouteDetailUIState(
                "",
                name = "465: Express Côte-des-Neiges",
                stopSpacing = RouteStopSpacingUIState(350, "m"),
                frequencyViolations = 10,
                averageFrequency_min = 12,
                spans = listOf(
                    SpanUIState(
                        LocalTime(5, 30, 0), LocalTime(10, 0)
                    ),
                    SpanUIState(LocalTime(15, 0), LocalTime(19, 30))

                )
            ),
            navigateToFrequencyViolations = {}
        )
    }
}

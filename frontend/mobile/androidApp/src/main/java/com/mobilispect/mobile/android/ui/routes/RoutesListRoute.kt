package com.mobilispect.mobile.android.ui.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mobilispect.android.ui.LoadingCard
import com.mobilispect.mobile.android.ui.ScreenFrame
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.R
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
class RoutesList(val agencyID: String)

fun NavGraphBuilder.routesGraph(navigateToFrequencyViolations: (String) -> Unit) {
    composable<RoutesList> {
        RoutesListRoute(navigateToFrequencyViolations)
    }
}

@Composable
fun RoutesListRoute(navigateToFrequencyViolations: (String) -> Unit) {
    val viewModel: RoutesListViewModel = koinViewModel()
    viewModel.sync()
    val uiState by viewModel.uiState.collectAsState()
    RouteListScreen(uiState, navigateToFrequencyViolations)
}

@Composable
fun RouteListScreen(
    uiState: RouteListUIState,
    navigateToFrequencyViolations: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenFrame(screenTitle = stringResource(R.string.routes)) {
        Column(modifier = modifier.padding(8.dp)) {
            when (uiState) {
                Loading -> LoadingCard()
                is RoutesFound -> {
                    LazyColumn {
                        for (route in uiState.routes)
                            item {
                                Row(modifier = modifier.padding(top = 4.dp, bottom = 4.dp)) {
                                    RouteCard(route)
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
fun RectangularCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.5f)
        ),
        shape = RectangleShape,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        content(modifier.padding(8.dp))
    }
}

@ThemePreviews
@Composable
fun PreviewRoutesListScreen() {
    MobilispectTheme {
        RouteListScreen(
            uiState = RoutesFound(
                routes = listOf(
                    RouteUIState(
                        id = "r-abcd-1",
                        shortName = "1",
                        longName = "Main Street",
                        agencyID = "r-abcd-a",
                        hasFrequencyCommittment = true
                    ),
                    RouteUIState(
                        id = "r-abce-",
                        shortName = "2",
                        longName = "1st Avenue",
                        agencyID = "r-abcd-a",
                        hasFrequencyCommittment = false
                    )
                )
            ), navigateToFrequencyViolations = {}
        )

    }
}

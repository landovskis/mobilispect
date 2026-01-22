@file:Suppress(
    "FunctionNaming",
)

package com.mobilispect.mobile.android.ui.agencies

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mobilispect.android.ui.LoadingCard
import com.mobilispect.android.ui.OutlinedButton
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.R
import com.mobilispect.mobile.android.ui.Card
import com.mobilispect.mobile.android.ui.ScreenFrame
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme
import com.mobilispect.mobile.ui.agencies.AgenciesFound
import com.mobilispect.mobile.ui.agencies.AgenciesUIState
import com.mobilispect.mobile.ui.agencies.AgenciesViewModel
import com.mobilispect.mobile.ui.agencies.AgencyUIState
import com.mobilispect.mobile.ui.agencies.Loading
import com.mobilispect.mobile.ui.agencies.NoAgencyFound
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object AgenciesList

fun NavGraphBuilder.agenciesGraph(navigateToRoutes: (String) -> Unit) {
    composable<AgenciesList> {
        val viewModel: AgenciesViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle(Loading)
        AgenciesScreen(uiState = uiState, navigateToRoutes = navigateToRoutes)
    }
}

@Composable
fun AgenciesScreen(
    uiState: AgenciesUIState,
    modifier: Modifier = Modifier,
    navigateToRoutes: (String) -> Unit
) {
    ScreenFrame(screenTitle = stringResource(id = R.string.agencies), modifier = modifier) {
        when (uiState) {
            Loading -> LoadingCard()
            NoAgencyFound -> {
                Card {
                    Text(stringResource(id = R.string.no_agencies_found))
                }
            }
            is AgenciesFound -> {
                Card {
                    LazyColumn {
                        for (agency in uiState.agencies) {
                            item {
                                OutlinedButton(
                                    onClick = { navigateToRoutes(agency.id) },
                                ) {
                                    Text(
                                        text = agency.name,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
fun PreviewAgenciesScreen() {
    MobilispectTheme {
        AgenciesScreen(uiState = AgenciesFound(
            agencies = listOf(
                AgencyUIState(id = "o-abcd-a", name = "Agency A")
            )
        ), navigateToRoutes = {})
    }
}

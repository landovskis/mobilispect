package com.mobilispect.mobile.android.ui.routes

import androidx.lifecycle.viewModelScope
import com.mobilispect.mobile.route.RouteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RoutesListViewModel(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val routeRepository: RouteRepository
) :
    androidx.lifecycle.ViewModel() {
    private val agencyID: String = savedStateHandle["agencyID"] ?: ""

    val uiState = routeUIState(agencyID)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(), initialValue = Loading
        )

    fun sync() {
        viewModelScope.launch {
            routeRepository.syncRoutesOperatedBy(agencyID)
        }
    }

    private fun routeUIState(agencyID: String) = routeRepository.operatedBy(agencyID)
        .mapLatest { routes ->
            RoutesFound(routes.map { route ->
                RouteUIState(
                    id = route.id,
                    shortName = route.shortName,
                    longName = route.longName,
                    agencyID = route.agencyID,
                    hasFrequencyCommittment = true
                )
            })
        }
}

sealed interface RouteListUIState
data object Loading : RouteListUIState
data class RoutesFound(val routes: Collection<RouteUIState>) : RouteListUIState
data class RouteUIState(
    val id: String,
    val shortName: String,
    val longName: String,
    val agencyID: String,
    val hasFrequencyCommittment: Boolean
)

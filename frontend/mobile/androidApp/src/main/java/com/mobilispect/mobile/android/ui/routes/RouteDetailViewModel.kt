package com.mobilispect.mobile.android.ui.routes

import androidx.lifecycle.SavedStateHandle

class RouteDetailViewModel(private val savedStateHandle: SavedStateHandle) {
    private val routeID: String = savedStateHandle["routeID"] ?: ""

    /*val uiState = RouteDetailUIState(routeID))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(), initialValue = Loading
        )*/
}

package com.mobilispect.mobile.ui.agencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilispect.mobile.agency.AgencyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

class AgencyUIState(val id: String, val name: String)
sealed interface AgenciesUIState
data object Loading : AgenciesUIState
data object NoAgencyFound : AgenciesUIState
class AgenciesFound(val agencies: List<AgencyUIState>) : AgenciesUIState

@OptIn(ExperimentalCoroutinesApi::class)
class AgenciesViewModel @javax.inject.Inject constructor(
    private val agencyRepository: AgencyRepository
) :
    ViewModel() {
    val uiState: Flow<AgenciesUIState> = agencyRepository.all()
        .mapLatest { agencies ->
            agencies.map { agency ->
                AgencyUIState(
                    id = agency.id,
                    name = agency.name
                )
            }.sortedBy { agency -> agency.name.uppercase() }
        }
        .mapLatest { agencies ->
            if (agencies.isNotEmpty()) {
                AgenciesFound(agencies)
            } else {
                NoAgencyFound
            }
        }

    init {
        sync()
    }

    private fun sync() {
        viewModelScope.launch {
            agencyRepository.sync()
        }
    }
}

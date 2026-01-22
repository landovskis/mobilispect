package com.mobilispect.mobile.data

import com.mobilispect.mobile.data.route.Route
import com.mobilispect.mobile.data.route.RouteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class TestRouteRepository : RouteRepository {
    private val routes = mutableListOf<Route>()
    private val routesByAgency = mutableMapOf<String, MutableCollection<Route>>()
    private val operatedByFlow: MutableSharedFlow<Collection<Route>> =
        MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun all(): Flow<Collection<Route>> = flowOf(routes)

    override fun operatedBy(agencyID: String): Flow<Collection<Route>> =
        operatedByFlow.mapLatest { routes ->
            routes.filter { route -> route.agencyID == agencyID }
        }

    override suspend fun syncRoutesOperatedBy(agencyID: String) {}

    fun insert(route: Route) {
        routes.add(route)
        routesByAgency.putIfAbsent(route.agencyID, mutableListOf())
        routesByAgency[route.agencyID]!!.add(route)
        operatedByFlow.tryEmit(routes)
    }
}

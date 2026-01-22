package com.mobilispect.mobile.route

import androidx.room.withTransaction
import com.mobilispect.mobile.data.AppDatabase
import com.mobilispect.mobile.data.cloud.NetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class OfflineFirstRouteRepository(
    private val routeDAO: RouteDAO,
    private val networkDataSource: NetworkDataSource,
    private val appDatabase: AppDatabase
) : RouteRepository {
    override fun all(): Flow<Collection<Route>> = routeDAO.all()

    override fun operatedBy(agencyID: String): Flow<Collection<Route>> =
        routeDAO.operatedBy(agencyID)

    override suspend fun syncRoutesOperatedBy(agencyID: String) {
        val remoteRes = networkDataSource.routesOperatedBy(agencyID)
        remoteRes.onSuccess { remoteRoutes ->
            appDatabase.withTransaction {
                val local = routeDAO.operatedBy(agencyID).first()
                val remote = remoteRoutes.map { route -> route.asEntity() }

                val localIDs = local.map { route -> route.id }
                val remoteIDs = remote.map { route -> route.id }

                val toAdd = remote.filterNot { route -> localIDs.contains(route.id) }
                for (route in toAdd) {
                    routeDAO.insert(route)
                }

                val toRemove = local.filterNot { route -> remoteIDs.contains(route.id) }
                for (route in toRemove) {
                    routeDAO.delete(route)
                }
            }
        }
    }
}

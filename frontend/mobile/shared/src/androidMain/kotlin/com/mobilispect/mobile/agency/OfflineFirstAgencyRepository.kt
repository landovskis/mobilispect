package com.mobilispect.mobile.com.mobilispect.mobile.agency

import androidx.room.withTransaction
import com.mobilispect.mobile.agency.Agency
import com.mobilispect.mobile.agency.AgencyDAO
import com.mobilispect.mobile.agency.AgencyRepository
import com.mobilispect.mobile.agency.asEntity
import com.mobilispect.mobile.data.AppDatabase
import com.mobilispect.mobile.data.cloud.NetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class OfflineFirstAgencyRepository(
    private val agencyDAO: AgencyDAO,
    private val networkDataSource: NetworkDataSource,
    private val appDatabase: AppDatabase,
) : AgencyRepository {
    override fun all(): Flow<List<Agency>> = agencyDAO.all()

    override suspend fun sync() {
        val remoteRes = networkDataSource.agencies()
        remoteRes.onSuccess { remoteAgencies ->
            appDatabase.withTransaction {
                val local = agencyDAO.all().first()
                val remote = remoteAgencies.map { agency -> agency.asEntity() }

                val localIDs = local.map { agency -> agency.id }
                val remoteIDs = remote.map { agency -> agency.id }

                val toAdd = remote.filterNot { agency -> localIDs.contains(agency.id) }
                for (agency in toAdd) {
                    agencyDAO.insert(agency)
                }

                val toRemove = local.filterNot { agency -> remoteIDs.contains(agency.id) }
                for (agency in toRemove) {
                    agencyDAO.delete(agency)
                }
            }
        }
    }
}

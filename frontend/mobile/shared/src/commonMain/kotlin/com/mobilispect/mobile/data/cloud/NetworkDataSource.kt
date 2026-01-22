package com.mobilispect.mobile.data.cloud

interface NetworkDataSource {
    suspend fun agencies(): Result<Collection<NetworkAgency>>
    suspend fun routesOperatedBy(agencyID: String): Result<Collection<NetworkRoute>>
}

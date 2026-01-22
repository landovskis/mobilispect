package com.mobilispect.mobile.data.frequency_commitment

import com.mobilispect.mobile.data.frequency_commitment.FrequencyCommitment
import com.mobilispect.mobile.data.frequency_commitment.FrequencyCommitmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class TestFrequencyCommitmentRepository : FrequencyCommitmentRepository {
    private val frequencyCommitmentsByAgency: MutableMap<String, FrequencyCommitment> =
        mutableMapOf()

    override fun forAgency(agency: String): Flow<FrequencyCommitment?> = flowOf(
        frequencyCommitmentsByAgency[agency]
    )

    fun insert(frequencyCommitment: FrequencyCommitment) {
        frequencyCommitmentsByAgency[frequencyCommitment.agency] = frequencyCommitment
    }
}

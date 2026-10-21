package fr.citoyende2027.feature.candidates

import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.data.repository.UserPreferencesRepository
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CandidateOrder
import fr.citoyende2027.core.model.DataPackInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class TestCandidatesRepository : CandidatesRepository {
    val candidates = MutableStateFlow<List<Candidate>>(emptyList())
    val packInfo = MutableStateFlow<DataPackInfo?>(null)

    override fun observeCandidates(): Flow<List<Candidate>> = candidates

    override fun observeCandidate(id: CandidateId): Flow<Candidate?> =
        candidates.map { list -> list.firstOrNull { it.id == id } }

    override fun observeCorePackInfo(): Flow<DataPackInfo?> = packInfo
}

internal class TestUserPreferencesRepository : UserPreferencesRepository {
    override val candidateOrder = MutableStateFlow<CandidateOrder>(CandidateOrder.Default)

    override suspend fun setCandidateOrder(order: CandidateOrder) {
        candidateOrder.value = order
    }
}

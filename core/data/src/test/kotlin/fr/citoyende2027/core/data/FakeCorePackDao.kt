package fr.citoyende2027.core.data

import fr.citoyende2027.core.database.dao.CorePackDao
import fr.citoyende2027.core.database.model.CandidateCoverageEntity
import fr.citoyende2027.core.database.model.CandidateEntity
import fr.citoyende2027.core.database.model.CandidateWithCoverage
import fr.citoyende2027.core.database.model.DataPackEntity
import fr.citoyende2027.core.database.model.MandateEntity
import fr.citoyende2027.core.database.model.VoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory CorePackDao: the Room implementation itself is covered in :core:database. */
internal class FakeCorePackDao : CorePackDao {
    private val candidates = MutableStateFlow<List<CandidateEntity>>(emptyList())
    private val coverage = MutableStateFlow<List<CandidateCoverageEntity>>(emptyList())
    private val packs = MutableStateFlow<Map<String, DataPackEntity>>(emptyMap())

    override fun observeCandidates(): Flow<List<CandidateWithCoverage>> = candidates.map { list ->
        list.map { c -> CandidateWithCoverage(c, coverage.value.filter { it.candidateId == c.id }) }
    }

    override fun observeCandidate(id: String): Flow<CandidateWithCoverage?> =
        observeCandidates().map { list -> list.firstOrNull { it.candidate.id == id } }

    override fun observePack(id: String): Flow<DataPackEntity?> = packs.map { it[id] }

    override suspend fun countPacks(id: String): Int = if (packs.value.containsKey(id)) 1 else 0

    override suspend fun deleteCandidates() {
        mandates.value = emptyList()
        votes.value = emptyList()
        coverage.value = emptyList()
        candidates.value = emptyList()
    }

    override suspend fun insertCandidates(candidates: List<CandidateEntity>) {
        this.candidates.value += candidates
    }

    override suspend fun insertCoverage(coverage: List<CandidateCoverageEntity>) {
        this.coverage.value += coverage
    }

    val mandates = MutableStateFlow<List<MandateEntity>>(emptyList())
    val votes = MutableStateFlow<List<VoteEntity>>(emptyList())

    override suspend fun insertMandates(mandates: List<MandateEntity>) {
        this.mandates.value += mandates
    }

    override suspend fun insertVotes(votes: List<VoteEntity>) {
        this.votes.value += votes
    }

    override fun observeMandates(candidateId: String): Flow<List<MandateEntity>> =
        mandates.map { list -> list.filter { it.candidateId == candidateId } }

    override fun observeVotes(candidateId: String): Flow<List<VoteEntity>> =
        votes.map { list -> list.filter { it.candidateId == candidateId } }

    override suspend fun upsertPack(pack: DataPackEntity) {
        packs.value += pack.id to pack
    }
}

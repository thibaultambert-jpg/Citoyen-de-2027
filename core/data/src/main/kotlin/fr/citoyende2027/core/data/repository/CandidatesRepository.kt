package fr.citoyende2027.core.data.repository

import fr.citoyende2027.core.database.dao.CorePackDao
import fr.citoyende2027.core.database.model.asModel
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.Vote
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CandidatesRepository {
    /** Candidates in storage order; ordering for display is a UI choice (see CandidateOrder). */
    fun observeCandidates(): Flow<List<Candidate>>

    fun observeCandidate(id: CandidateId): Flow<Candidate?>

    /** Metadata of the active `core` pack, or null when no data is installed. */
    fun observeCorePackInfo(): Flow<DataPackInfo?>

    /** Mandates, most recent start first. */
    fun observeMandates(id: CandidateId): Flow<List<Mandate>>

    /** Recorded votes, most recent first. */
    fun observeVotes(id: CandidateId): Flow<List<Vote>>

    /** Speeches (metadata and links), most recent first. */
    fun observeSpeeches(id: CandidateId): Flow<List<Speech>>
}

internal class OfflineCandidatesRepository @Inject constructor(private val dao: CorePackDao) : CandidatesRepository {
    override fun observeCandidates(): Flow<List<Candidate>> =
        dao.observeCandidates().map { candidates -> candidates.map { it.asModel() } }

    override fun observeCandidate(id: CandidateId): Flow<Candidate?> =
        dao.observeCandidate(id.value).map { it?.asModel() }

    override fun observeCorePackInfo(): Flow<DataPackInfo?> = dao.observePack(DataPackInfo.CORE).map { it?.asModel() }

    override fun observeMandates(id: CandidateId): Flow<List<Mandate>> =
        dao.observeMandates(id.value).map { list -> list.map { it.asModel() } }

    override fun observeVotes(id: CandidateId): Flow<List<Vote>> =
        dao.observeVotes(id.value).map { list -> list.map { it.asModel() } }

    override fun observeSpeeches(id: CandidateId): Flow<List<Speech>> =
        dao.observeSpeeches(id.value).map { list -> list.mapNotNull { it.asModel() } }
}

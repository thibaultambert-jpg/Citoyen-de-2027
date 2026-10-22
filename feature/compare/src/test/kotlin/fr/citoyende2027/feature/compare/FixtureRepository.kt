package fr.citoyende2027.feature.compare

import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CandidateProgram
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.model.Vote
import fr.citoyende2027.core.testing.FictionalCandidates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Repository serving the fictional fixtures, in reverse order to check that screens sort them. */
internal class FixtureRepository : CandidatesRepository {
    override fun observeCandidates(): Flow<List<Candidate>> = flowOf(FictionalCandidates.all.reversed())

    override fun observeCandidate(id: CandidateId): Flow<Candidate?> =
        flowOf(FictionalCandidates.all.firstOrNull { it.id == id })

    override fun observeCorePackInfo(): Flow<DataPackInfo?> = flowOf(FictionalCandidates.packInfo)

    override fun observeMandates(id: CandidateId): Flow<List<Mandate>> = flowOf(emptyList())

    override fun observeVotes(id: CandidateId): Flow<List<Vote>> =
        flowOf(FictionalCandidates.votes.filter { it.candidateId == id })

    override fun observeSpeeches(id: CandidateId): Flow<List<Speech>> = flowOf(emptyList())

    override fun observeThemes(): Flow<List<Theme>> = flowOf(FictionalCandidates.themes.reversed())

    override fun observeProgram(id: CandidateId): Flow<CandidateProgram> =
        flowOf(FictionalCandidates.programsPack.program(id))
}

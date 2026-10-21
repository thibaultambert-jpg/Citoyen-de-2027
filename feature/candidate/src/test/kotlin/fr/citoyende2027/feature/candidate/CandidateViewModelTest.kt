package fr.citoyende2027.feature.candidate

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.model.Body
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.Vote
import fr.citoyende2027.core.model.VoteFilter
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.testing.MainDispatcherExtension
import fr.citoyende2027.feature.candidate.navigation.CandidateRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CandidateViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = object : CandidatesRepository {
        val candidates = MutableStateFlow(FictionalCandidates.all)

        override fun observeCandidates(): Flow<List<Candidate>> = candidates

        override fun observeCandidate(id: CandidateId): Flow<Candidate?> =
            candidates.map { list -> list.firstOrNull { it.id == id } }

        override fun observeCorePackInfo(): Flow<DataPackInfo?> = flowOf(FictionalCandidates.packInfo)

        override fun observeMandates(id: CandidateId): Flow<List<Mandate>> =
            flowOf(FictionalCandidates.mandates.filter { it.candidateId == id })

        override fun observeVotes(id: CandidateId): Flow<List<Vote>> =
            flowOf(FictionalCandidates.votes.filter { it.candidateId == id })
    }

    private fun viewModel(id: String) =
        CandidateViewModel(SavedStateHandle(mapOf(CandidateRoute.ID_ARG to id)), repository)

    @Test
    fun `known candidate is exposed with the data date`() = runTest {
        viewModel(FictionalCandidates.candidateB.id.value).uiState.test {
            val state = awaitSettled() as CandidateUiState.Ready
            assertEquals(FictionalCandidates.candidateB, state.candidate)
            assertEquals(FictionalCandidates.packInfo, state.dataInfo)
            assertEquals(emptyList<Vote>(), state.votes.votes)
        }
    }

    @Test
    fun `unknown candidate is reported as not found`() = runTest {
        viewModel("fictif-inconnu").uiState.test {
            assertEquals(CandidateUiState.NotFound, awaitSettled())
        }
    }

    @Test
    fun `candidate removed by a pack update becomes not found`() = runTest {
        viewModel(FictionalCandidates.candidateA.id.value).uiState.test {
            awaitSettled()
            repository.candidates.value = listOf(FictionalCandidates.candidateB)
            assertEquals(CandidateUiState.NotFound, awaitItem())
        }
    }

    @Test
    fun `votes are filtered without losing the total`() = runTest {
        val viewModel = viewModel(FictionalCandidates.candidateA.id.value)
        viewModel.uiState.test {
            val all = (awaitSettled() as CandidateUiState.Ready).votes
            assertEquals(FictionalCandidates.votes, all.votes)
            assertEquals(listOf(Body.AN), all.options.bodies)

            viewModel.onVoteFilterChange(VoteFilter(body = Body.PE))
            val filtered = (awaitItem() as CandidateUiState.Ready).votes
            assertEquals(emptyList<Vote>(), filtered.votes)
            assertEquals(1, filtered.totalCount)
        }
    }

    @Test
    fun `mandates are exposed`() = runTest {
        viewModel(FictionalCandidates.candidateA.id.value).uiState.test {
            assertEquals(FictionalCandidates.mandates, (awaitSettled() as CandidateUiState.Ready).mandates)
        }
    }

    private suspend fun ReceiveTurbine<CandidateUiState>.awaitSettled(): CandidateUiState {
        while (true) {
            val item = awaitItem()
            if (item != CandidateUiState.Loading) return item
        }
    }
}

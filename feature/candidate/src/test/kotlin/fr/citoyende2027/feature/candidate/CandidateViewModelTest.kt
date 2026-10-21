package fr.citoyende2027.feature.candidate

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.DataPackInfo
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
    }

    private fun viewModel(id: String) =
        CandidateViewModel(SavedStateHandle(mapOf(CandidateRoute.ID_ARG to id)), repository)

    @Test
    fun `known candidate is exposed with the data date`() = runTest {
        viewModel(FictionalCandidates.candidateB.id.value).uiState.test {
            assertEquals(
                CandidateUiState.Ready(FictionalCandidates.candidateB, FictionalCandidates.packInfo),
                awaitSettled(),
            )
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

    private suspend fun ReceiveTurbine<CandidateUiState>.awaitSettled(): CandidateUiState {
        while (true) {
            val item = awaitItem()
            if (item != CandidateUiState.Loading) return item
        }
    }
}

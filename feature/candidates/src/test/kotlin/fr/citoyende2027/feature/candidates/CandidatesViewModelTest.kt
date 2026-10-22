package fr.citoyende2027.feature.candidates

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import fr.citoyende2027.core.model.CandidateOrder
import fr.citoyende2027.core.model.orderedBy
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CandidatesViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val candidatesRepository = TestCandidatesRepository()
    private val preferences = TestUserPreferencesRepository()
    private val viewModel = CandidatesViewModel(candidatesRepository, preferences, SavedStateHandle())

    private fun install() {
        candidatesRepository.candidates.value = FictionalCandidates.all.reversed()
        candidatesRepository.packInfo.value = FictionalCandidates.packInfo
    }

    @Test
    fun `state is loading until data is read`() {
        assertEquals(CandidatesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `no installed data gives an empty ready state`() = runTest {
        viewModel.uiState.test {
            val state = awaitReady()
            assertEquals(0, state.totalCount)
            assertEquals(null, state.dataInfo)
        }
    }

    @Test
    fun `candidates are alphabetical by default`() = runTest {
        install()
        viewModel.uiState.test {
            val state = awaitReady()
            assertEquals(CandidateOrder.Alphabetical, state.order)
            assertEquals(listOf("Candidat A", "Candidat B", "Candidat C"), state.candidates.map { it.displayName })
            assertEquals(FictionalCandidates.packInfo, state.dataInfo)
        }
    }

    @Test
    fun `search filters without changing the order`() = runTest {
        install()
        viewModel.uiState.test {
            awaitReady()
            viewModel.onQueryChange("CANDIDAT b")
            val state = awaitReady()
            assertEquals(listOf("Candidat B"), state.candidates.map { it.displayName })
            assertEquals(3, state.totalCount)
        }
    }

    @Test
    fun `random order is persisted and applied`() = runTest {
        install()
        viewModel.uiState.test {
            awaitReady()
            viewModel.onRandomOrderSelected()
            val state = awaitReady()
            val order = assertInstanceOf(CandidateOrder.Shuffled::class.java, preferences.candidateOrder.value)
            assertEquals(order, state.order)
            assertEquals(FictionalCandidates.all.orderedBy(order), state.candidates)

            viewModel.onAlphabeticalOrderSelected()
            assertEquals(CandidateOrder.Alphabetical, (awaitReady()).order)
        }
    }

    /** Skips the initial Loading value emitted before the repositories are read. */
    private suspend fun ReceiveTurbine<CandidatesUiState>.awaitReady(): CandidatesUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is CandidatesUiState.Ready) return item
        }
    }
}

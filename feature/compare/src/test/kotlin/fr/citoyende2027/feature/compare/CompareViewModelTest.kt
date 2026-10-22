package fr.citoyende2027.feature.compare

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import fr.citoyende2027.core.model.ProgramStatus
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CompareViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val a = FictionalCandidates.candidateA
    private val b = FictionalCandidates.candidateB
    private val c = FictionalCandidates.candidateC

    @Test
    fun `candidates and themes are alphabetical, without the undetermined theme`() = runTest {
        CompareViewModel(FixtureRepository()).uiState.test {
            val state = awaitUntil { it.candidates.isNotEmpty() && it.themes.isNotEmpty() }
            assertEquals(listOf(a, b, c), state.candidates)
            assertEquals(listOf(FictionalCandidates.theme1, FictionalCandidates.theme2), state.themes)
        }
    }

    @Test
    fun `columns appear from two candidates and a theme, alphabetical, whatever the selection order`() = runTest {
        val viewModel = CompareViewModel(FixtureRepository())
        viewModel.uiState.test {
            viewModel.toggleCandidate(b.id)
            viewModel.selectTheme(FictionalCandidates.theme1.id)
            assertEquals(emptyList<CandidateColumn>(), awaitUntil { it.theme != null }.columns)
            viewModel.toggleCandidate(a.id)
            val columns = awaitUntil { it.columns.size == 2 }.columns
            assertEquals(listOf(a, b), columns.map { it.candidate })
            assertEquals(listOf("fictif-a-programme-0001"), columns[0].facts.excerpts.map { it.id })
            assertEquals(FictionalCandidates.votes, columns[0].facts.votes)
            assertEquals(listOf("fictif-b-programme-0001"), columns[1].facts.excerpts.map { it.id })
            assertEquals(ProgramStatus.PARTIEL, columns[1].facts.programStatus)
            assertEquals(emptyList<Any>(), columns[1].facts.votes)
        }
    }

    @Test
    fun `no more than three candidates`() = runTest {
        val viewModel = CompareViewModel(FixtureRepository())
        viewModel.uiState.test {
            listOf(a, b, c).forEach { viewModel.toggleCandidate(it.id) }
            val full = awaitUntil { it.selected.size == 3 }
            assertFalse(full.canSelectMore)
            viewModel.toggleCandidate(c.id)
            assertEquals(setOf(a.id, b.id), awaitUntil { it.selected.size == 2 }.selected)
        }
    }

    private suspend fun ReceiveTurbine<CompareUiState>.awaitUntil(
        predicate: (CompareUiState) -> Boolean,
    ): CompareUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}

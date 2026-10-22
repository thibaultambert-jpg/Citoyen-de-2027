package fr.citoyende2027.feature.ask

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import fr.citoyende2027.core.data.repository.AskAnswer
import fr.citoyende2027.core.data.repository.AskRepository
import fr.citoyende2027.core.data.repository.AskScope
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
import fr.citoyende2027.core.testing.MainDispatcherExtension
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private class Candidates : CandidatesRepository {
    override fun observeCandidates(): Flow<List<Candidate>> = flowOf(FictionalCandidates.all.reversed())

    override fun observeCandidate(id: CandidateId): Flow<Candidate?> = flowOf(null)

    override fun observeCorePackInfo(): Flow<DataPackInfo?> = flowOf(null)

    override fun observeMandates(id: CandidateId): Flow<List<Mandate>> = flowOf(emptyList())

    override fun observeVotes(id: CandidateId): Flow<List<Vote>> = flowOf(emptyList())

    override fun observeSpeeches(id: CandidateId): Flow<List<Speech>> = flowOf(emptyList())

    override fun observeThemes(): Flow<List<Theme>> = flowOf(emptyList())

    override fun observeProgram(id: CandidateId): Flow<CandidateProgram> = flowOf(CandidateProgram.notPublished(id))
}

private class RecordingAsk : AskRepository {
    val asked = mutableListOf<Pair<String, AskScope>>()

    override suspend fun ask(question: String, scope: AskScope): AskAnswer {
        asked += question to scope
        return AskAnswer.Abstained(LocalDate.of(2026, 1, 1))
    }
}

class AskViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `candidates are alphabetical and the question is sent with its scope`() = runTest {
        val ask = RecordingAsk()
        val viewModel = AskViewModel(Candidates(), ask)
        viewModel.uiState.test {
            assertEquals(FictionalCandidates.all, awaitUntil { it.candidates.isNotEmpty() }.candidates)
            viewModel.onScopeChange(ScopeKind.CANDIDATE)
            viewModel.onQuestionChange("Que dit le programme fictif ?")
            assertFalse(awaitUntil { it.question.isNotEmpty() }.canSubmit)
            viewModel.onCandidateChange(FictionalCandidates.candidateB)
            viewModel.submit()
            assertEquals(AskAnswer.Abstained(LocalDate.of(2026, 1, 1)), awaitUntil { it.answer != null }.answer)
            assertEquals(
                listOf("Que dit le programme fictif ?" to AskScope.Candidate(FictionalCandidates.candidateB.id)),
                ask.asked,
            )
        }
    }

    @Test
    fun `programmes scope asks every candidate`() = runTest {
        val ask = RecordingAsk()
        val viewModel = AskViewModel(Candidates(), ask)
        viewModel.uiState.test {
            awaitUntil { it.candidates.isNotEmpty() }
            viewModel.onScopeChange(ScopeKind.PROGRAMS)
            viewModel.submit("Que disent les programmes fictifs ?")
            awaitUntil { it.answer != null }
            assertEquals(AskScope.AllPrograms, ask.asked.single().second)
        }
    }

    private suspend fun ReceiveTurbine<AskUiState>.awaitUntil(predicate: (AskUiState) -> Boolean): AskUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}

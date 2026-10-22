package fr.citoyende2027.feature.ask

import android.net.Uri
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import fr.citoyende2027.core.data.repository.AnswerGroup
import fr.citoyende2027.core.data.repository.AskAnswer
import fr.citoyende2027.core.data.repository.AskRepository
import fr.citoyende2027.core.data.repository.AskScope
import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.data.repository.LegalPassage
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CandidateProgram
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.model.Vote
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.testing.FictionalLegal
import fr.citoyende2027.core.testing.MainDispatcherExtension
import fr.citoyende2027.llm.assistant.Assistant
import fr.citoyende2027.llm.assistant.AssistantStatus
import fr.citoyende2027.rag.answer.GroundedAnswer
import fr.citoyende2027.rag.answer.SourceCandidate
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

private class RecordingAsk(private val answer: AskAnswer = AskAnswer.Abstained(LocalDate.of(2026, 1, 1))) :
    AskRepository {
    val asked = mutableListOf<Pair<String, AskScope>>()

    override suspend fun ask(question: String, scope: AskScope): AskAnswer {
        asked += question to scope
        return answer
    }
}

/** Ready by default; answers when [release] completes, so that "Arrêter" can be tested. */
private class FakeAssistant(status: AssistantStatus = AssistantStatus.Ready(enabled = true)) : Assistant {
    override val status = MutableStateFlow(status)
    val asked = mutableListOf<List<SourceCandidate>>()
    val release = CompletableDeferred<GroundedAnswer>()

    override fun download() = Unit

    override fun cancelDownload() = Unit

    override suspend fun import(uri: Uri) = false

    override fun remove() = Unit

    override fun setEnabled(enabled: Boolean) = Unit

    override suspend fun answer(
        question: String,
        sources: List<SourceCandidate>,
        onText: (String) -> Unit,
    ): GroundedAnswer {
        asked += sources
        onText("{")
        return release.await()
    }
}

private val legalPassages = AskAnswer.Passages(
    listOf(
        AnswerGroup(
            candidate = null,
            legal = listOf(LegalPassage(FictionalLegal.articles[1], FictionalLegal.textA)),
            programs = emptyList(),
        ),
    ),
)

class AskViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `candidates are alphabetical and the question is sent with its scope`() = runTest {
        val ask = RecordingAsk()
        val viewModel = AskViewModel(Candidates(), ask, FakeAssistant())
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
        val viewModel = AskViewModel(Candidates(), ask, FakeAssistant())
        viewModel.uiState.test {
            awaitUntil { it.candidates.isNotEmpty() }
            viewModel.onScopeChange(ScopeKind.PROGRAMS)
            viewModel.submit("Que disent les programmes fictifs ?")
            awaitUntil { it.answer != null }
            assertEquals(AskScope.AllPrograms, ask.asked.single().second)
        }
    }

    @Test
    fun `passages come first, then the verified answer of the local model`() = runTest {
        val assistant = FakeAssistant()
        val viewModel = AskViewModel(Candidates(), RecordingAsk(legalPassages), assistant)
        viewModel.uiState.test {
            awaitUntil { it.assistant != null }
            viewModel.submit("Selon quelle règle a lieu l'élection fictive ?")
            assertEquals(legalPassages, awaitUntil { it.ai is AiAnswer.Generating }.answer)
            assertEquals(
                listOf(
                    SourceCandidate(
                        "fictif-texte-a-art-2",
                        "Texte fictif A, article 1",
                        FictionalLegal.articles[1].text,
                    ),
                ),
                assistant.asked.single(),
            )
            val answered = GroundedAnswer.Answered(emptyList(), emptyList(), emptyList(), null)
            assistant.release.complete(answered)
            assertEquals(AiAnswer.Done(answered), awaitUntil { it.ai is AiAnswer.Done }.ai)
        }
    }

    @Test
    fun `stop keeps the passages and extracts only hides the answer`() = runTest {
        val viewModel = AskViewModel(Candidates(), RecordingAsk(legalPassages), FakeAssistant())
        viewModel.uiState.test {
            awaitUntil { it.assistant != null }
            viewModel.submit("Question fictive ?")
            awaitUntil { it.ai is AiAnswer.Generating }
            viewModel.stopGeneration()
            val stopped = awaitUntil { it.ai is AiAnswer.Done }
            assertEquals(AiAnswer.Done(GroundedAnswer.Stopped), stopped.ai)
            assertEquals(legalPassages, stopped.answer)
            viewModel.showExtractsOnly()
            assertTrue(awaitUntil { it.extractsOnly }.extractsOnly)
        }
    }

    @Test
    fun `no generation without an enabled model, nor for programmes side by side`() = runTest {
        val off = FakeAssistant(AssistantStatus.Ready(enabled = false))
        val viewModel = AskViewModel(Candidates(), RecordingAsk(legalPassages), off)
        viewModel.uiState.test {
            awaitUntil { it.assistant != null }
            viewModel.submit("Question fictive ?")
            assertEquals(AiAnswer.None, awaitUntil { it.answer != null }.ai)
        }
        assertTrue(off.asked.isEmpty())

        val twoGroups = AskAnswer.Passages(legalPassages.groups + legalPassages.groups)
        val sideBySide = AskViewModel(Candidates(), RecordingAsk(twoGroups), FakeAssistant())
        sideBySide.uiState.test {
            awaitUntil { it.assistant != null }
            sideBySide.onScopeChange(ScopeKind.PROGRAMS)
            sideBySide.submit("Question fictive ?")
            assertEquals(AiAnswer.NotForSideBySide, awaitUntil { it.ai != AiAnswer.None }.ai)
        }
    }

    private suspend fun ReceiveTurbine<AskUiState>.awaitUntil(predicate: (AskUiState) -> Boolean): AskUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}

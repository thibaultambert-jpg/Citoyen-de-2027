package fr.citoyende2027.feature.ask

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.citoyende2027.core.data.repository.AskAnswer
import fr.citoyende2027.core.data.repository.AskRepository
import fr.citoyende2027.core.data.repository.AskScope
import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateOrder
import fr.citoyende2027.core.model.orderedBy
import fr.citoyende2027.llm.assistant.Assistant
import fr.citoyende2027.llm.assistant.AssistantStatus
import fr.citoyende2027.rag.answer.GroundedAnswer
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which corpus the question is asked about; a candidate scope needs a selected candidate. */
enum class ScopeKind {
    LEGAL,
    CANDIDATE,
    PROGRAMS,
}

/** The local assistant's part of an answer, shown above the passages (AGENTS.md §8, steps 5–9). */
sealed interface AiAnswer {
    data object None : AiAnswer

    /** Generating; [characters] only feeds the progress indicator, the text is never shown. */
    data class Generating(val characters: Int) : AiAnswer

    /** Programmes side by side: passages only, the same treatment for every candidate. */
    data object NotForSideBySide : AiAnswer

    data class Done(val answer: GroundedAnswer) : AiAnswer
}

data class AskUiState(
    val candidates: List<Candidate> = emptyList(),
    val scope: ScopeKind = ScopeKind.LEGAL,
    val candidate: Candidate? = null,
    val question: String = "",
    val searching: Boolean = false,
    val answer: AskAnswer? = null,
    val assistant: AssistantStatus? = null,
    val ai: AiAnswer = AiAnswer.None,
    /** "Voir uniquement les extraits": the generated answer is hidden for this question. */
    val extractsOnly: Boolean = false,
    /** Result of the last manual import of the model file (null: none). */
    val importAccepted: Boolean? = null,
) {
    val canSubmit: Boolean get() = question.isNotBlank() && !searching &&
        (scope != ScopeKind.CANDIDATE || candidate != null)
}

@HiltViewModel
class AskViewModel @Inject constructor(
    candidatesRepository: CandidatesRepository,
    private val askRepository: AskRepository,
    private val assistant: Assistant,
) : ViewModel() {
    private val form = MutableStateFlow(AskUiState())
    private var search: Job? = null
    private var generation: Job? = null

    val uiState: StateFlow<AskUiState> = combine(
        form,
        candidatesRepository.observeCandidates(),
        assistant.status.onStart<AssistantStatus?> { emit(null) },
    ) { state, all, assistantStatus ->
        val candidates = all.orderedBy(CandidateOrder.Alphabetical)
        state.copy(
            candidates = candidates,
            candidate = state.candidate?.takeIf { it in candidates },
            assistant = assistantStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskUiState())

    fun onQuestionChange(text: String) = form.update { it.copy(question = text) }

    fun onScopeChange(scope: ScopeKind) = reset { it.copy(scope = scope) }

    fun onCandidateChange(candidate: Candidate) = reset { it.copy(candidate = candidate) }

    fun submit(question: String = form.value.question) {
        val state = form.value.copy(question = question)
        val scope = when (state.scope) {
            ScopeKind.LEGAL -> AskScope.Legal
            ScopeKind.PROGRAMS -> AskScope.AllPrograms
            ScopeKind.CANDIDATE -> AskScope.Candidate(state.candidate?.id ?: return)
        }
        if (question.isBlank()) return
        generation?.cancel()
        form.value = state.copy(searching = true, answer = null, ai = AiAnswer.None, extractsOnly = false)
        search?.cancel()
        search = viewModelScope.launch {
            val answer = askRepository.ask(question, scope)
            // Passages are shown at once (< 1 s); the generated answer, if any, comes after.
            form.update { it.copy(searching = false, answer = answer) }
            if (answer is AskAnswer.Passages) generate(question, answer)
        }
    }

    /** "Arrêter": the generation stops in the model process; the passages stay. */
    fun stopGeneration() {
        generation?.cancel()
        form.update { if (it.ai is AiAnswer.Generating) it.copy(ai = AiAnswer.Done(GroundedAnswer.Stopped)) else it }
    }

    fun showExtractsOnly() {
        stopGeneration()
        form.update { it.copy(extractsOnly = true) }
    }

    fun downloadModel() = assistant.download()

    fun cancelDownload() = assistant.cancelDownload()

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            val accepted = assistant.import(uri)
            form.update { it.copy(importAccepted = accepted) }
        }
    }

    fun removeModel() = assistant.remove()

    fun setAssistantEnabled(enabled: Boolean) = assistant.setEnabled(enabled)

    private fun reset(change: (AskUiState) -> AskUiState) {
        generation?.cancel()
        form.update { change(it).copy(answer = null, ai = AiAnswer.None, extractsOnly = false) }
    }

    private suspend fun generate(question: String, answer: AskAnswer.Passages) {
        val status = assistant.status.first()
        if (status != AssistantStatus.Ready(enabled = true)) return
        if (answer.groups.size != 1) {
            form.update { it.copy(ai = AiAnswer.NotForSideBySide) }
            return
        }
        val sources = answer.groups.single().sourceCandidates()
        if (sources.isEmpty()) return
        form.update { it.copy(ai = AiAnswer.Generating(0)) }
        generation = viewModelScope.launch {
            val result = assistant.answer(question, sources) { text ->
                form.update { state ->
                    if (state.ai is AiAnswer.Generating) state.copy(ai = AiAnswer.Generating(text.length)) else state
                }
            }
            form.update { it.copy(ai = AiAnswer.Done(result)) }
        }
    }
}

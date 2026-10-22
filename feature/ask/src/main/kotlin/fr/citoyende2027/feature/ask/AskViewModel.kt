package fr.citoyende2027.feature.ask

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
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which corpus the question is asked about; a candidate scope needs a selected candidate. */
enum class ScopeKind {
    LEGAL,
    CANDIDATE,
    PROGRAMS,
}

data class AskUiState(
    val candidates: List<Candidate> = emptyList(),
    val scope: ScopeKind = ScopeKind.LEGAL,
    val candidate: Candidate? = null,
    val question: String = "",
    val searching: Boolean = false,
    val answer: AskAnswer? = null,
) {
    val canSubmit: Boolean get() = question.isNotBlank() && !searching &&
        (scope != ScopeKind.CANDIDATE || candidate != null)
}

@HiltViewModel
class AskViewModel @Inject constructor(
    candidatesRepository: CandidatesRepository,
    private val askRepository: AskRepository,
) : ViewModel() {
    private val form = MutableStateFlow(AskUiState())
    private var search: Job? = null

    val uiState: StateFlow<AskUiState> = combine(form, candidatesRepository.observeCandidates()) { state, all ->
        val candidates = all.orderedBy(CandidateOrder.Alphabetical)
        state.copy(candidates = candidates, candidate = state.candidate?.takeIf { it in candidates })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskUiState())

    fun onQuestionChange(text: String) = form.update { it.copy(question = text) }

    fun onScopeChange(scope: ScopeKind) = form.update { it.copy(scope = scope, answer = null) }

    fun onCandidateChange(candidate: Candidate) = form.update { it.copy(candidate = candidate, answer = null) }

    fun submit(question: String = form.value.question) {
        val state = form.value.copy(question = question)
        val scope = when (state.scope) {
            ScopeKind.LEGAL -> AskScope.Legal
            ScopeKind.PROGRAMS -> AskScope.AllPrograms
            ScopeKind.CANDIDATE -> AskScope.Candidate(state.candidate?.id ?: return)
        }
        if (question.isBlank()) return
        form.value = state.copy(searching = true, answer = null)
        search?.cancel()
        search = viewModelScope.launch {
            val answer = askRepository.ask(question, scope)
            form.update { it.copy(searching = false, answer = answer) }
        }
    }
}

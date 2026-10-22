package fr.citoyende2027.feature.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CandidateOrder
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.ProgramDocument
import fr.citoyende2027.core.model.ReservePeriod
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.model.ThemeFacts
import fr.citoyende2027.core.model.ThemeId
import fr.citoyende2027.core.model.alphabetical
import fr.citoyende2027.core.model.orderedBy
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CompareUiState(
    /** All candidates, alphabetical. */
    val candidates: List<Candidate> = emptyList(),
    val selected: Set<CandidateId> = emptySet(),
    /** Themes that can be compared, alphabetical (the undetermined theme is left out). */
    val themes: List<Theme> = emptyList(),
    val theme: Theme? = null,
    /** One column per selected candidate, alphabetical, same template; empty until 2 are chosen. */
    val columns: List<CandidateColumn> = emptyList(),
    val dataInfo: DataPackInfo? = null,
    /** Reserve period: programme excerpts are replaced by a notice, votes stay (AGENTS.md §10). */
    val reserve: ReservePeriod = ReservePeriod.NONE,
) {
    val canSelectMore: Boolean get() = selected.size < MAX_CANDIDATES

    companion object {
        const val MIN_CANDIDATES = 2
        const val MAX_CANDIDATES = 3
    }
}

/** [documents] give each excerpt its source document (link, date, reuse terms). */
data class CandidateColumn(val candidate: Candidate, val facts: ThemeFacts, val documents: List<ProgramDocument>)

/** Comparator (AGENTS.md §11): 2 to 3 candidates on one theme, identical template, no verdict. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CompareViewModel @Inject constructor(private val repository: CandidatesRepository) : ViewModel() {
    private val selection = MutableStateFlow(Selection())

    private val candidates = repository.observeCandidates()
    private val themes = repository.observeThemes()

    private val columns: Flow<List<CandidateColumn>> = combine(selection, candidates, themes) { s, all, themes ->
        Triple(s, all, themes)
    }.flatMapLatest { (s, all, themes) ->
        val theme = themes.firstOrNull { it.id == s.theme }
        val chosen = all.filter { it.id in s.candidates }.orderedBy(CandidateOrder.Alphabetical)
        if (theme == null || chosen.size < CompareUiState.MIN_CANDIDATES) {
            flowOf(emptyList())
        } else {
            combine(chosen.map { candidate -> column(candidate, theme) }) { it.toList() }
        }
    }

    val uiState: StateFlow<CompareUiState> = combine(
        candidates,
        themes,
        selection,
        columns,
        combine(repository.observeCorePackInfo(), repository.observeReserve(), ::Pair),
    ) { all, themes, s, columns, (info, reserve) ->
        val comparable = themes.filterNot { it.id.isUnknown }.alphabetical()
        CompareUiState(
            candidates = all.orderedBy(CandidateOrder.Alphabetical),
            selected = s.candidates.filterTo(mutableSetOf()) { id -> all.any { it.id == id } },
            themes = comparable,
            theme = comparable.firstOrNull { it.id == s.theme },
            columns = columns,
            dataInfo = info,
            reserve = reserve,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    fun toggleCandidate(id: CandidateId) = selection.update { s ->
        when {
            id in s.candidates -> s.copy(candidates = s.candidates - id)
            s.candidates.size >= CompareUiState.MAX_CANDIDATES -> s
            else -> s.copy(candidates = s.candidates + id)
        }
    }

    fun selectTheme(id: ThemeId?) = selection.update { it.copy(theme = id) }

    private fun column(candidate: Candidate, theme: Theme): Flow<CandidateColumn> = combine(
        repository.observeProgram(candidate.id),
        repository.observeVotes(candidate.id),
    ) { program, votes -> CandidateColumn(candidate, ThemeFacts.of(theme, program, votes), program.documents) }

    private data class Selection(val candidates: Set<CandidateId> = emptySet(), val theme: ThemeId? = null)
}

package fr.citoyende2027.feature.candidates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.data.repository.UserPreferencesRepository
import fr.citoyende2027.core.model.CandidateOrder
import fr.citoyende2027.core.model.orderedBy
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CandidatesViewModel @Inject constructor(
    candidatesRepository: CandidatesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val query = savedStateHandle.getStateFlow(QUERY_KEY, "")

    val uiState: StateFlow<CandidatesUiState> = combine(
        candidatesRepository.observeCandidates(),
        candidatesRepository.observeCorePackInfo(),
        userPreferencesRepository.candidateOrder,
        query,
    ) { candidates, info, order, query ->
        CandidatesUiState.Ready(
            dataInfo = info,
            order = order,
            query = query,
            candidates = candidates.filter { it.matches(query) }.orderedBy(order),
            totalCount = candidates.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CandidatesUiState.Loading,
    )

    fun onQueryChange(query: String) {
        savedStateHandle[QUERY_KEY] = query
    }

    fun onAlphabeticalOrderSelected() = setOrder(CandidateOrder.Alphabetical)

    /** Each selection draws a new random order. */
    fun onRandomOrderSelected() = setOrder(CandidateOrder.Shuffled(Random.nextLong()))

    private fun setOrder(order: CandidateOrder) {
        viewModelScope.launch { userPreferencesRepository.setCandidateOrder(order) }
    }

    private companion object {
        const val QUERY_KEY = "query"
    }
}

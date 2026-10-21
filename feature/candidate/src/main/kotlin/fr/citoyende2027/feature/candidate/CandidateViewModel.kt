package fr.citoyende2027.feature.candidate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.VoteFilter
import fr.citoyende2027.core.model.VoteFilterOptions
import fr.citoyende2027.feature.candidate.navigation.CandidateRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class CandidateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    candidatesRepository: CandidatesRepository,
) : ViewModel() {
    private val candidateId = CandidateId(checkNotNull(savedStateHandle.get<String>(CandidateRoute.ID_ARG)))
    private val voteFilter = MutableStateFlow(VoteFilter())

    val uiState: StateFlow<CandidateUiState> = combine(
        candidatesRepository.observeCandidate(candidateId),
        candidatesRepository.observeCorePackInfo(),
        candidatesRepository.observeMandates(candidateId),
        candidatesRepository.observeVotes(candidateId),
        voteFilter,
    ) { candidate, info, mandates, votes, filter ->
        if (candidate == null) {
            CandidateUiState.NotFound
        } else {
            CandidateUiState.Ready(
                candidate = candidate,
                dataInfo = info,
                mandates = mandates,
                votes = VotesState(
                    votes = votes.filter(filter::matches),
                    totalCount = votes.size,
                    filter = filter,
                    options = VoteFilterOptions.of(votes),
                    allSources = votes.map { it.source }.distinct(),
                ),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CandidateUiState.Loading,
    )

    fun onVoteFilterChange(filter: VoteFilter) = voteFilter.update { filter }
}

package fr.citoyende2027.feature.candidate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CandidateProgram
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.model.Vote
import fr.citoyende2027.core.model.VoteFilter
import fr.citoyende2027.core.model.VoteFilterOptions
import fr.citoyende2027.core.model.alphabetical
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

    private val facts = combine(
        candidatesRepository.observeMandates(candidateId),
        candidatesRepository.observeVotes(candidateId),
        candidatesRepository.observeSpeeches(candidateId),
        candidatesRepository.observeProgram(candidateId),
        candidatesRepository.observeThemes(),
    ) { mandates, votes, speeches, program, themes ->
        Facts(mandates, votes, speeches, program, themes.alphabetical())
    }

    val uiState: StateFlow<CandidateUiState> = combine(
        candidatesRepository.observeCandidate(candidateId),
        candidatesRepository.observeCorePackInfo(),
        facts,
        voteFilter,
        candidatesRepository.observeReserve(),
    ) { candidate, info, facts, filter, reserve ->
        val votes = facts.votes
        if (candidate == null) {
            CandidateUiState.NotFound
        } else {
            CandidateUiState.Ready(
                candidate = candidate,
                dataInfo = info,
                mandates = facts.mandates,
                votes = VotesState(
                    votes = votes.filter(filter::matches),
                    totalCount = votes.size,
                    filter = filter,
                    options = VoteFilterOptions.of(votes),
                    allSources = votes.map { it.source }.distinct(),
                ),
                speeches = facts.speeches,
                program = facts.program,
                themes = facts.themes,
                allVotes = votes,
                reserve = reserve,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CandidateUiState.Loading,
    )

    fun onVoteFilterChange(filter: VoteFilter) = voteFilter.update { filter }

    private data class Facts(
        val mandates: List<Mandate>,
        val votes: List<Vote>,
        val speeches: List<Speech>,
        val program: CandidateProgram,
        val themes: List<Theme>,
    )
}

package fr.citoyende2027.feature.candidate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.feature.candidate.navigation.CandidateRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CandidateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    candidatesRepository: CandidatesRepository,
) : ViewModel() {
    private val candidateId = CandidateId(checkNotNull(savedStateHandle.get<String>(CandidateRoute.ID_ARG)))

    val uiState: StateFlow<CandidateUiState> = combine(
        candidatesRepository.observeCandidate(candidateId),
        candidatesRepository.observeCorePackInfo(),
    ) { candidate, info ->
        if (candidate == null) CandidateUiState.NotFound else CandidateUiState.Ready(candidate, info)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CandidateUiState.Loading,
    )
}

package fr.citoyende2027.feature.candidate

import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.DataPackInfo

sealed interface CandidateUiState {
    data object Loading : CandidateUiState

    data object NotFound : CandidateUiState

    data class Ready(val candidate: Candidate, val dataInfo: DataPackInfo?) : CandidateUiState
}

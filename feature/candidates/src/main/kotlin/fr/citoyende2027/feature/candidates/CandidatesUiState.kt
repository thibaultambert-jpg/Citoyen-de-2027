package fr.citoyende2027.feature.candidates

import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateOrder
import fr.citoyende2027.core.model.DataPackInfo

sealed interface CandidatesUiState {
    data object Loading : CandidatesUiState

    data class Ready(
        val dataInfo: DataPackInfo?,
        val order: CandidateOrder,
        val query: String,
        /** Candidates matching [query], in [order]. */
        val candidates: List<Candidate>,
        /** Number of candidates installed, before filtering. */
        val totalCount: Int,
    ) : CandidatesUiState
}

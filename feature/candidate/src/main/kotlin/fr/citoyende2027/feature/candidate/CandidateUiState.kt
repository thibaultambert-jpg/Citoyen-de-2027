package fr.citoyende2027.feature.candidate

import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.SourceRef
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.Vote
import fr.citoyende2027.core.model.VoteFilter
import fr.citoyende2027.core.model.VoteFilterOptions

sealed interface CandidateUiState {
    data object Loading : CandidateUiState

    data object NotFound : CandidateUiState

    data class Ready(
        val candidate: Candidate,
        val dataInfo: DataPackInfo?,
        val mandates: List<Mandate> = emptyList(),
        val votes: VotesState = VotesState(),
        val speeches: List<Speech> = emptyList(),
    ) : CandidateUiState
}

data class VotesState(
    /** Votes matching [filter], most recent first. */
    val votes: List<Vote> = emptyList(),
    val totalCount: Int = 0,
    val filter: VoteFilter = VoteFilter(),
    val options: VoteFilterOptions = VoteFilterOptions.of(emptyList()),
    /** Sources of all votes, filtered or not, for the "Sources et méthode" tab. */
    val allSources: List<SourceRef> = emptyList(),
)

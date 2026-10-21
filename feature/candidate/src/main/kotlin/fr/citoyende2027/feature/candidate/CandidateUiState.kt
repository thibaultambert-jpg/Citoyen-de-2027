package fr.citoyende2027.feature.candidate

import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateProgram
import fr.citoyende2027.core.model.DataPackInfo
import fr.citoyende2027.core.model.Mandate
import fr.citoyende2027.core.model.SourceRef
import fr.citoyende2027.core.model.Speech
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.model.ThemeId
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
        val program: CandidateProgram = CandidateProgram.notPublished(candidate.id),
        /** Theme taxonomy, alphabetical, the undetermined theme last. */
        val themes: List<Theme> = emptyList(),
        /** All votes, unfiltered (side by side with the programme). */
        val allVotes: List<Vote> = emptyList(),
    ) : CandidateUiState {
        val themeLabels: Map<ThemeId, String> get() = themes.associate { it.id to it.label }
    }
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

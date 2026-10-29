package fr.citoyende2027.feature.candidate

import androidx.annotation.StringRes

/**
 * Tabs of the candidate sheet. The list is the same for every candidate: a tab without data says so
 * instead of disappearing (template parity, AGENTS.md §10). Mandates, votes and speeches are the
 * three views of the journey tab ([JourneyView]).
 */
enum class CandidateSheetTab(@StringRes val label: Int) {
    OVERVIEW(R.string.feature_candidate_tab_overview),
    PROGRAM(R.string.feature_candidate_tab_program),
    JOURNEY(R.string.feature_candidate_tab_journey),
    PROGRAM_AND_VOTES(R.string.feature_candidate_tab_program_and_votes),
    SOURCES(R.string.feature_candidate_tab_sources),
}

/** Views of [CandidateSheetTab.JOURNEY], in the order of AGENTS.md §11. */
enum class JourneyView(@StringRes val label: Int) {
    MANDATES(R.string.feature_candidate_journey_mandates),
    VOTES(R.string.feature_candidate_journey_votes),
    SPEECHES(R.string.feature_candidate_journey_speeches),
}

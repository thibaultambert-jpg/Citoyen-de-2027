package fr.citoyende2027.feature.candidate

import androidx.annotation.StringRes

/**
 * Tabs of the candidate sheet, in the order of AGENTS.md §11. The list is the same for every
 * candidate: a tab without data says so instead of disappearing (template parity, §10).
 */
enum class CandidateSheetTab(@StringRes val label: Int) {
    OVERVIEW(R.string.feature_candidate_tab_overview),
    PROGRAM(R.string.feature_candidate_tab_program),
    MANDATES(R.string.feature_candidate_tab_mandates),
    VOTES(R.string.feature_candidate_tab_votes),
    SPEECHES(R.string.feature_candidate_tab_speeches),
    PROGRAM_AND_VOTES(R.string.feature_candidate_tab_program_and_votes),
    SOURCES(R.string.feature_candidate_tab_sources),
}

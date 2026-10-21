package fr.citoyende2027.core.ui

import androidx.annotation.StringRes
import fr.citoyende2027.core.model.CandidacyStatus
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel

@get:StringRes
val CoverageFacet.labelRes: Int
    get() = when (this) {
        CoverageFacet.PROGRAM -> R.string.core_ui_facet_program
        CoverageFacet.MANDATES -> R.string.core_ui_facet_mandates
        CoverageFacet.VOTES -> R.string.core_ui_facet_votes
        CoverageFacet.SPEECHES -> R.string.core_ui_facet_speeches
    }

@get:StringRes
val CoverageLevel.labelRes: Int
    get() = when (this) {
        CoverageLevel.AVAILABLE -> R.string.core_ui_level_available
        CoverageLevel.PARTIAL -> R.string.core_ui_level_partial
        CoverageLevel.NOT_APPLICABLE -> R.string.core_ui_level_not_applicable
        CoverageLevel.UNAVAILABLE -> R.string.core_ui_level_unavailable
    }

/** Format string taking the status date (JJ/MM/AAAA) as its only argument. */
@get:StringRes
val CandidacyStatus.labelRes: Int
    get() = when (this) {
        CandidacyStatus.DECLARE -> R.string.core_ui_status_declared
        CandidacyStatus.RETIRE -> R.string.core_ui_status_withdrawn
        CandidacyStatus.OFFICIEL -> R.string.core_ui_status_official
    }

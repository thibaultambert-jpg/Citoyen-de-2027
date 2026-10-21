package fr.citoyende2027.core.ui

import androidx.annotation.StringRes
import fr.citoyende2027.core.model.BallotSubject
import fr.citoyende2027.core.model.Body
import fr.citoyende2027.core.model.CandidacyStatus
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.CoverageLevel
import fr.citoyende2027.core.model.ProgramStatus
import fr.citoyende2027.core.model.TagMethod
import fr.citoyende2027.core.model.VotePosition

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

@get:StringRes
val Body.labelRes: Int
    get() = when (this) {
        Body.AN -> R.string.core_ui_body_an
        Body.SENAT -> R.string.core_ui_body_senat
        Body.PE -> R.string.core_ui_body_pe
        Body.GOVERNMENT -> R.string.core_ui_body_government
        Body.LOCAL -> R.string.core_ui_body_local
    }

/** Null for [VotePosition.OTHER]: the published value is then shown as is. */
@get:StringRes
val VotePosition.labelRes: Int?
    get() = when (this) {
        VotePosition.FOR -> R.string.core_ui_position_for
        VotePosition.AGAINST -> R.string.core_ui_position_against
        VotePosition.ABSTENTION -> R.string.core_ui_position_abstention
        VotePosition.NON_VOTING -> R.string.core_ui_position_non_voting
        VotePosition.OTHER -> null
    }

@get:StringRes
val BallotSubject.labelRes: Int
    get() = when (this) {
        BallotSubject.AMENDMENT -> R.string.core_ui_subject_amendment
        BallotSubject.ARTICLE -> R.string.core_ui_subject_article
        BallotSubject.WHOLE_TEXT -> R.string.core_ui_subject_whole_text
        BallotSubject.MOTION -> R.string.core_ui_subject_motion
        BallotSubject.OTHER -> R.string.core_ui_subject_other
    }

@get:StringRes
val ProgramStatus.labelRes: Int
    get() = when (this) {
        ProgramStatus.COMPLET -> R.string.core_ui_program_status_complete
        ProgramStatus.PARTIEL -> R.string.core_ui_program_status_partial
        ProgramStatus.NON_PUBLIE -> R.string.core_ui_program_status_none
    }

@get:StringRes
val TagMethod.labelRes: Int
    get() = when (this) {
        TagMethod.RULE -> R.string.core_ui_tag_method_rule
        TagMethod.MANUAL -> R.string.core_ui_tag_method_manual
    }

package fr.citoyende2027.core.model

import java.time.LocalDate

@JvmInline
value class CandidateId(val value: String)

/**
 * A candidate. Every candidate exposes exactly the same fields (AGENTS.md §2.1):
 * no photo, logo or party colour in V1.
 */
data class Candidate(
    val id: CandidateId,
    /** Name as displayed, e.g. "Candidat A" in fixtures. */
    val displayName: String,
    /** Key used for the alphabetical order (family name first in real data). */
    val sortName: String,
    val status: CandidacyStatus,
    /** Date the status was established (announcement, withdrawal or official list). */
    val statusDate: LocalDate,
    /** Where the status comes from, behind the "Source" button. */
    val statusSource: SourceRef,
    val coverage: DataCoverage,
) {
    /** Accent- and case-insensitive match on the displayed and sort names. */
    fun matches(query: String): Boolean {
        val needle = SearchText.normalize(query)
        return needle.isEmpty() ||
            SearchText.normalize(displayName).contains(needle) ||
            SearchText.normalize(sortName).contains(needle)
    }

    /** Initials used by the uniform avatar, at most two letters. */
    val initials: String
        get() = displayName
            .split(' ', '-', '\'')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(separator = "") { it.first().uppercase() }
}

/** Inclusion status (AGENTS.md §10). */
enum class CandidacyStatus {
    /** Publicly announced, before the Conseil constitutionnel list. */
    DECLARE,

    /** Withdrawn; kept with its date. */
    RETIRE,

    /** On the official list published by the Conseil constitutionnel. */
    OFFICIEL,
}

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
    val coverage: DataCoverage,
) {
    /** Initials used by the uniform avatar, at most two letters. */
    val initials: String
        get() = displayName
            .split(' ', '-', '\'')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(separator = "") { it.first().uppercase() }
}

/** Inclusion status, see docs/inclusion-policy.md (AGENTS.md §10). */
enum class CandidacyStatus {
    /** Publicly announced, before the Conseil constitutionnel list. */
    DECLARE,

    /** Withdrawn; kept with its date. */
    RETIRE,

    /** On the official list published by the Conseil constitutionnel. */
    OFFICIEL,
}

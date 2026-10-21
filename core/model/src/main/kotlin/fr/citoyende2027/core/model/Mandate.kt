package fr.citoyende2027.core.model

import java.time.LocalDate

/** Where a mandate is held or a vote cast. Recorded votes exist for AN, SENAT and PE only. */
enum class Body {
    AN,
    SENAT,
    PE,
    GOVERNMENT,
    LOCAL,
}

/** Subject of a ballot (AGENTS.md §10), from explicit rules on its official title in the pipeline. */
enum class BallotSubject {
    AMENDMENT,
    ARTICLE,
    WHOLE_TEXT,
    MOTION,
    OTHER,
}

data class Mandate(
    val id: String,
    val candidateId: CandidateId,
    val body: Body,
    /** Label as built by the pipeline from the official record (role and place). */
    val label: String,
    val startDate: LocalDate,
    /** Null while the mandate is ongoing according to the source. */
    val endDate: LocalDate?,
    val source: SourceRef,
)

/** A recorded vote. [position] is kept exactly as published by the assembly (AGENTS.md §7.3). */
data class Vote(
    val id: String,
    val candidateId: CandidateId,
    val body: Body,
    val ballotId: String,
    val ballotDate: LocalDate,
    val ballotTitle: String,
    val ballotType: String?,
    /** Null when the source gives no way to determine it (e.g. European Parliament). */
    val ballotSubject: BallotSubject?,
    val position: String,
    val ballotUrl: String,
    val source: SourceRef,
)

package fr.citoyende2027.core.model

import java.time.LocalDate

/** Where a mandate is held or a vote cast. Recorded votes exist for AN, SENAT and PE only. */
enum class Body {
    AN,
    SENAT,
    PE,
    LOCAL,
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
    val position: String,
    val ballotUrl: String,
    val source: SourceRef,
)

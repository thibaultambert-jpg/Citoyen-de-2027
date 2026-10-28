package fr.citoyende2027.core.model

import java.time.LocalDate

/** Sponsorships (parrainages) validated and published by the Conseil constitutionnel (AGENTS.md §6). */
data class Sponsorships(
    val candidateId: CandidateId,
    val total: Int,
    /** Distinct values of the published "Département" column (departments and other collectivities). */
    val departments: Int,
    /** Published mandate label and its number of sponsorships, sorted by label. */
    val byMandate: List<MandateCount>,
    /** Latest publication included in the figures: their date. */
    val publishedOn: LocalDate,
    val source: SourceRef,
) {
    data class MandateCount(val mandate: String, val count: Int)
}

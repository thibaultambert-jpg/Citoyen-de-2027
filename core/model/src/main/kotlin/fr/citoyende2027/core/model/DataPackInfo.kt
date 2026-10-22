package fr.citoyende2027.core.model

import java.time.LocalDate

/** Metadata of an activated data pack, shown as "Données du JJ/MM/AAAA" (AGENTS.md §5). */
data class DataPackInfo(
    val id: String,
    val version: String,
    val generatedOn: LocalDate,
    /** True only for the fictional demo data of debug builds; never in a published pack. */
    val isFictional: Boolean,
) {
    companion object {
        const val CORE = "core"
        const val PROGRAMS = "programs"
        const val LEGAL = "legal"
    }
}

/** Content of the `core` pack: candidates, mandates, recorded votes, speeches and the theme list. */
data class CorePackContent(
    val info: DataPackInfo,
    val candidates: List<Candidate>,
    val mandates: List<Mandate> = emptyList(),
    val votes: List<Vote> = emptyList(),
    val speeches: List<Speech> = emptyList(),
    val themes: List<Theme> = emptyList(),
    /** Retrieval thresholds (JSON), calibrated by the pipeline on its golden set (AGENTS.md §8). */
    val ragConfig: String? = null,
    /** Electoral reserve period flag of the published pack (AGENTS.md §10). */
    val reserve: ReservePeriod = ReservePeriod.NONE,
    /** Version date of the candidate inclusion rule used for this pack (AGENTS.md §10). */
    val inclusionPolicyDate: LocalDate? = null,
) {
    init {
        require(info.id == DataPackInfo.CORE) { "Not a core pack: ${info.id}" }
        val ids = candidates.map { it.id }.toSet()
        require(ids.size == candidates.size) { "Duplicate candidate ids" }
        require(mandates.all { it.candidateId in ids }) { "Mandate of an unknown candidate" }
        require(votes.all { it.candidateId in ids }) { "Vote of an unknown candidate" }
        require(speeches.all { it.candidateId in ids }) { "Speech of an unknown candidate" }
        val themeIds = themes.map { it.id }.toSet()
        require(votes.all { vote -> vote.themes.all { it.themeId in themeIds } }) { "Vote with an unknown theme" }
    }

    /** Checks that a `programs` pack of the same manifest matches these candidates and themes. */
    fun requireConsistent(programs: ProgramsPackContent) {
        val ids = candidates.map { it.id }.toSet()
        require(programs.statuses.keys.all { it in ids }) { "Programme status of an unknown candidate" }
        require(programs.documents.all { it.candidateId in ids }) { "Programme of an unknown candidate" }
        val themeIds = themes.map { it.id }.toSet()
        require(programs.chunks.all { chunk -> chunk.themes.all { it.themeId in themeIds } }) {
            "Programme excerpt with an unknown theme"
        }
    }
}

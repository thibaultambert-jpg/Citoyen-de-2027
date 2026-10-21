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
    }
}

/** Content of the `core` pack: candidates, their mandates and their recorded votes. */
data class CorePackContent(
    val info: DataPackInfo,
    val candidates: List<Candidate>,
    val mandates: List<Mandate> = emptyList(),
    val votes: List<Vote> = emptyList(),
) {
    init {
        require(info.id == DataPackInfo.CORE) { "Not a core pack: ${info.id}" }
        val ids = candidates.map { it.id }.toSet()
        require(ids.size == candidates.size) { "Duplicate candidate ids" }
        require(mandates.all { it.candidateId in ids }) { "Mandate of an unknown candidate" }
        require(votes.all { it.candidateId in ids }) { "Vote of an unknown candidate" }
    }
}
